package aggregation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utilize.LamportClock;
import utilize.JsonUtil;
import utilize.ReqResParser;
import utilize.ReqResParser.Header;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


public class AggregationServer {
    private record Request(String type, JsonObject header, JsonObject body, long lamport) {}
    private record WeatherRecord(JsonObject data, long lamport) {}
    private record WeatherSnapshot(WeatherRecord record, long counter) {}

    private int port;
    private LamportClock clock;
    private final Path snapshotPath;
    private PriorityBlockingQueue<Request> requestQueue;
    private Map<String, WeatherRecord> storage = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Map<String, Long> lastUpdate = new ConcurrentHashMap<>();



    public static void main(String[] args) throws Exception {
        int port = 4567;
        Path snapshotPath = Path.of("data.json");
        AggregationServer server = new AggregationServer(port, snapshotPath);
        server.start();

    }

    public AggregationServer(int port, Path snapshotPath){
        this.port = port;
        this.snapshotPath = snapshotPath;
        this.clock = new LamportClock();
        this.requestQueue = new PriorityBlockingQueue<>(1024,
                                Comparator.comparingLong((Request a) -> a.lamport)
                                .thenComparing(a -> a.body.get("id").toString()));
        restoreSnapshotIfPresent();
    }

    public long getLocalLamport() {
        return clock.get();
    }

    private boolean hasStationId(String stationId) {
        return storage.containsKey(stationId);
    }

    /**
     * Start server
     * @throws IOException
     */
    public void start() throws IOException {
        startConsumer();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::removeExpired, 10, 10, TimeUnit.SECONDS);


        ExecutorService pool = Executors.newFixedThreadPool(4);
        this.serverSocket = new ServerSocket(this.port);
        try {
            System.out.println("Server listening on port " + this.port);

            while (running) {
                try {
                    Socket clientSocket = this.serverSocket.accept(); // waits for client
                    pool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Stop server
     * @throws IOException
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close(); // will break out of accept()
        }
    }

    /**
     * Start Consumer
     */
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Request request = this.requestQueue.take(); // block until an update is available
                    String stationId = request.body.get("id").toString();

                    // Update storage if there is any change and
                    // the task counter to mark down current timestamp of processing task
                    storage.put(stationId, new WeatherRecord(request.body, request.lamport));
                    lastUpdate.put(stationId, System.currentTimeMillis());

                    System.out.println("[Consumer] Applied PUT update to stationId: " + stationId + " (Lamport = " + request.lamport + ")");
                    persistSnapshot();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Consumer");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Remove expired data
     */
    public void removeExpired() {
        long cutoff = System.currentTimeMillis() - 30_000;
        int before = storage.size();

        for (Map.Entry<String, Long> entry : lastUpdate.entrySet()) {
            if (entry.getValue() < cutoff) {
                String key = entry.getKey();
                storage.entrySet().removeIf(e -> e.getKey().equals(key));
                lastUpdate.entrySet().removeIf(e -> e.getKey().equals(key));
                System.out.println("Expired: " + key);
            }
        }

        int after = storage.size();
        if (before != after) {
            System.out.println("[Cleanup] Removed " + (before - after) + " expired stations");
            System.out.println();
            persistSnapshot();
        }
    }

    /**
     * Restore weather data when crashing
     */
    private void restoreSnapshotIfPresent() {
        if (!Files.exists(snapshotPath)) return;
        try (BufferedReader br = Files.newBufferedReader(snapshotPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(br);
            if (root != null && root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                long snapLamport = obj.has("Lamport") ? obj.get("Lamport").getAsLong() : 0L;
                JsonArray arr = obj.getAsJsonArray("stations");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        JsonObject data = el.getAsJsonObject();
                        String stationId = data.get("id").toString();
                        storage.put(stationId, new WeatherRecord(data, snapLamport));
                        lastUpdate.put(stationId, System.currentTimeMillis());
                    }
                }
                long restoredLamport = Math.max(clock.get(), snapLamport);
                clock.set(restoredLamport);
                System.out.println("[Restore] Restore the server to Lamport = " + clock.get());
            }
        } catch (Exception e) {
            System.err.println("[Error] Restore server failed: " + e.getMessage());
        }
    }

    /**
     * Store weather data for recovery purpose
     */
    private void persistSnapshot() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("Lamport", clock.get());
            JsonArray arr = new JsonArray();
            for (WeatherRecord record : storage.values()) {
                arr.add(record.data);
            }
            root.add("stations", arr);

            // Write down in tmp file
            byte[] bytes = JsonUtil.serialize(root).getBytes(StandardCharsets.UTF_8);
            Path tmp = snapshotPath.resolveSibling(snapshotPath.getFileName().toString() + ".tmp");
            Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // log failed message
            System.out.println("Persist snapshot failed: " + e.getMessage());
        }
    }

    /**
     * Handle PUT requests
     * @param out
     * @param jsonHeader
     * @param jsonBody
     */
    private void handlePut(PrintWriter out, JsonObject jsonHeader, JsonObject jsonBody) {
        long timestamp = jsonHeader.get("LamportClock").getAsLong();
        long lamportValue = clock.onReceive(timestamp);
        //System.out.println("[Lamport] Update local Lamport = " + lamportValue);

        requestQueue.put(new Request("PUT", jsonHeader, jsonBody, lamportValue));
        System.out.println("[Producer] Receive and enqueue PUT with stationId: " + jsonBody.get("id") + " (Lamport = " + lamportValue + ")");
    }

    /**
     * Handle GET requests
     * @param jsonHeader
     * @return
     */
    private WeatherSnapshot handleGet(JsonObject jsonHeader) {
        String stationId = jsonHeader.get("StationId").toString();
        long timestamp = jsonHeader.get("LamportClock").getAsLong();
        long lamportValue = clock.onReceive(timestamp);
        //System.out.println("[Lamport] Update local Lamport = " + lamportValue);

        if (storage.isEmpty()) {
            return null;
        }
        WeatherRecord record = storage.get(stationId);
        if (record == null) {
            System.out.println("[Error] No data with stationId: " + jsonHeader.get("StationId") + " (Lamport = " + lamportValue + ")");
            return null;
        }
        //log
        System.out.println("[Producer] Receive and process GET with stationId: " + jsonHeader.get("StationId") + " (Lamport = " + lamportValue + ")");
        return new WeatherSnapshot(record, timestamp);
    }

    /**
     * Send response back to clients
     * @param out
     * @param statusCode
     * @param status
     * @param lamport
     * @param body
     */
    private void sendResponse(PrintWriter out, int statusCode, String status, long lamport, String body) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String response =   "HTTP/1.1 " + statusCode + " " + status + "\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: " + bytes.length + "\r\n" +
                            "LamportClock: " + lamport + "\r\n" +
                            "\r\n" +
                            (bytes.length > 0 ? body : "");
        out.println(response);
        out.flush();
    }

    /**
     * Classify request type and handle it properly
     * @param socket
     */
    private void handleClient(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String[] request = ReqResParser.splitHeaderAndBodyFromRequest(in);

            // Read request header
            Header header = ReqResParser.readHeader(request[0]);
            String requestType = header.requestType();
            Map<String, String> metadata = header.metadata();
            JsonObject jsonHeader = JsonUtil.toJsonObj(metadata);
            System.out.println(requestType);
            System.out.println("[Info] Current local Lamport = " + clock.get());
            // send simple HTTP response
            if (requestType == null) return;

            if (requestType.equals("PUT")) {
                System.out.println("[Info] Request from: " + header.metadata().get("ServerId") + " with external Lamport = " + header.metadata().get("LamportClock"));
                JsonObject jsonBody = ReqResParser.readBody(request[1]);
                String stationId = jsonBody.get("id").toString();
                boolean isExist = hasStationId(stationId);

                this.handlePut(out, jsonHeader, jsonBody);

                int statusCode = isExist ? 200 : 201;
                String status = isExist ? "OK" : "Created";
                sendResponse(out, statusCode, status, clock.onSend(), "");
                //System.out.println("[Lamport] Increase Lamport to " + clock.get());
                System.out.println("[Respond] Send response (Lamport = " + clock.get() + ")");

            } else if (requestType.equals("GET")) {
                System.out.println("[Info] From: " + header.metadata().get("ClientId") + " with external Lamport = " + header.metadata().get("LamportClock"));
                WeatherSnapshot snap = this.handleGet(jsonHeader);
                //System.out.println("[Lamport] Increase Lamport to " + clock.get());
                if (snap != null){
                    sendResponse(out, 200, "OK", clock.onSend(), snap.record.data.toString());
                } else {
                    sendResponse(out, 500, "Internal Server Error", clock.onSend(), "");
                }
                System.out.println("[Respond] Send response (Lamport = " + clock.get() + ")");

            } else {
                out.println("HTTP/1.1 400 Bad Request");
                out.flush();
            }
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

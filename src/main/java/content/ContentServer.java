package content;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import utilize.JsonUtil;
import utilize.LamportClock;

public class ContentServer {

    private final LamportClock clock;
    private final JsonObject jsonWeatherData;
    private final String serverId;

    public ContentServer(String filePath) throws Exception {
        Random rand = new Random();
        int num = rand.nextInt(100);
        this.serverId = "cs" + num;
        this.clock = new LamportClock();
        this.jsonWeatherData = JsonUtil.fromText(filePath);
    }

    public JsonObject getWeatherData() {
        return this.jsonWeatherData;
    }

    public void sendPUT(String serverAddress) throws IOException {
        String[] parts = serverAddress.split(":");
        String host = parts[0];
        int port;

        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port");
            return;
        }

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String content = this.jsonWeatherData.toString();
            long lamportValue = clock.onSend();


            // Format of request
            String putRequest = "PUT /weather.json HTTP/1.1\r\n" +
                                "User-Agent: ATOMClient/1/0\r\n" +
                                "ServerId: " + this.serverId + "\r\n" +
                                "LamportClock: " + lamportValue + "\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: " + content.length() + "\r\n" +
                                "\r\n" +
                                content;

            // Send request
            out.println(putRequest);
            System.out.println("[ContentServer] Request sent (Lamport = " + clock.get() + ")");

            // Read the response
            String line;
            long lamport = 0;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("LamportClock: ")) {
                    lamport = Integer.parseInt(line.split(":")[1].trim());
                    clock.onReceive(lamport);
                }
                System.out.println(line);
            }

        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java ContentServer <serverHost:port> <filePath>");
            return;
        }

        String filePath = args[1];
        ContentServer contentServer = new ContentServer(filePath);
        String serverAddress = args[0];


        // Set up scheduler to send PUT request every 10 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Send PUT request
                contentServer.sendPUT(serverAddress);
            } catch (Exception e) {
                System.out.println("[Error] " + e.getMessage());
            }
        }, 0, 20, TimeUnit.SECONDS);
    }
}


package client;

import com.google.gson.JsonObject;
import utilize.LamportClock;
import utilize.ReqResParser;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Random;

public class GETClient {
    private final LamportClock clock;
    private final String clientId; // You can make this configurable

    public GETClient() {
        Random rand = new Random();
        int num = rand.nextInt(100);
        this.clock = new LamportClock();
        this.clientId = "client" + num;
    }

    public long getLocalLamport() {
        return clock.get();
    }

    public String sendGET(String serverAddress, String stationId) throws IOException, InterruptedException {
        String[] parts = serverAddress.split(":");
        String host = parts[0];
        int port;
        int maxRetries = 3;
        int retries = 0;

        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "Invalid port";
        }

        while (retries < maxRetries) {
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                long lamportValue = clock.onSend();

                String getRequest = "GET /weather.json HTTP/1.1\r\n" +
                        "User-Agent: ATOMClient/1/0\r\n" +
                        "ClientId: " + this.clientId + "\r\n" +
                        "LamportClock: " + lamportValue + "\r\n" +
                        "StationId: " + stationId + "\r\n" +
                        "\r\n";
                out.println(getRequest);

                String[] response = ReqResParser.splitHeaderAndBodyFromRequest(in);
                ReqResParser.Header header = ReqResParser.readHeader(response[0]);
                long lamport = Long.parseLong(header.metadata().get("LamportClock"));
                clock.onReceive(lamport);

                String[] sHeader = response[0].split("\r\n");
                for (String info : sHeader) {
                    System.out.println(info);
                }
                System.out.println();

                if (sHeader[0].equals("HTTP/1.1 500 Internal Server Error")) {
                    return null;
                }
                return response[1];
            } catch (Exception e) {
                System.out.println("[Error] " + e.getMessage());
                retries++;
                if (retries < maxRetries) {
                    System.out.println("[Retry] Retry on sending request");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        System.out.println("[Error] Retry fail: " + ie.getMessage());
                    }
                }
            }
        }
        return "[Error] Could not connect to the Aggregation Server.";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java GETClient <serverHost:port> <stationId>");
            return;
        }

        GETClient client = new GETClient();
        String serverAddress = args[0];
        String stationId = args[1];

        String response = client.sendGET(serverAddress, stationId);
        if (response != null) {
            JsonObject jsonResponse = ReqResParser.readBody(response);
            System.out.println(jsonResponse);
        }
    }
}

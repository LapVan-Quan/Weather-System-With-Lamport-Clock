import aggregation.AggregationServer;
import client.GETClient;
import com.google.gson.JsonObject;
import content.ContentServer;
import org.junit.jupiter.api.*;
import utilize.JsonUtil;
import utilize.ReqResParser;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {
    private AggregationServer aggServer;
    private ContentServer content1;
    private ContentServer content2;
    private GETClient client1;
    private GETClient client2;
    private final String serverAddress = "localhost:4567";
    private final String filePath1 = "src/main/java/content/weather_1.txt";
    private final String filePath2 = "src/main/java/content/weather_2.txt";

    /**
     * Set up before every single case
     * @throws Exception
     */
    @BeforeEach
    public void setUp() throws Exception {
        int port = 4567;
        Path snapshotPath = Path.of("test_data.json");
        aggServer = new AggregationServer(port, snapshotPath);

        new Thread(() -> {
            try {
                aggServer.start();
            } catch (Exception e) {
                fail(e);
            }
        }).start();

        // Create Content Servers
        content1 = new ContentServer(this.filePath1);
        content2 = new ContentServer(this.filePath2);

        // Create Clients
        client1 = new GETClient();
        client2 = new GETClient();
    }

    /**
     * Shut down server after every single test case
     * @throws Exception
     */
    @AfterEach
    void tearDown() throws Exception {
        if (aggServer != null) {
            aggServer.stop();
        }
    }

    @Test
    @Order(1)
    void testPutAndGetData() throws Exception {
        // Start Content Server 1 in background
        new Thread(() -> {
            try {
                content1.sendPUT(this.serverAddress);
            } catch (Exception e) {
                fail(e);
            }
        }).start();
        Thread.sleep(500);

        new Thread(() -> {
            try {
                content2.sendPUT(this.serverAddress);
            } catch (Exception e) {
                fail(e);
            }
        }).start();
        Thread.sleep(500);

        String response1 = client1.sendGET(serverAddress, "IDS60901");
        JsonObject jsonResponse1 = ReqResParser.readBody(response1);

        String response2 = client2.sendGET(serverAddress, "IDS96012");
        JsonObject jsonResponse2 = ReqResParser.readBody(response2);

        assertEquals(content1.getWeatherData().get("wind_spd_kmh"), jsonResponse1.get("wind_spd_kmh"));
        assertEquals(content1.getWeatherData().get("id"), jsonResponse1.get("id"));
    }

    @Test
    @Order(2)
    void testLamportSync() throws Exception {
        String response = client1.sendGET(serverAddress, "IDS60901");
        System.out.println(response);
        JsonObject jsonResponse = ReqResParser.readBody(response);

        assertEquals(client1.getLocalLamport(), aggServer.getLocalLamport() + 1);
    }

    @Test
    @Order(3)
    void testServerRecovery() throws Exception {
        aggServer.stop();

        new Thread(() -> {
            try {
                int port = 4567;
                Path snapshotPath = Path.of("test_data.json");
                aggServer = new AggregationServer(port, snapshotPath);
                aggServer.start();
            } catch (Exception e) {
                fail(e);
            }
        }).start();

        String response = client1.sendGET(serverAddress, "IDS60901");
        JsonObject jsonResponse = ReqResParser.readBody(response);

        assertEquals(content1.getWeatherData().get("wind_spd_kmh"), jsonResponse.get("wind_spd_kmh"));
        assertEquals(content1.getWeatherData().get("id"), jsonResponse.get("id"));
    }

    @Test
    @Order(4)
    void testConsistencyOfGet() throws Exception {
        String response1 = client1.sendGET(serverAddress, "IDS60901");
        String response2 = client1.sendGET(serverAddress, "IDS60901");

        JsonObject jsonResponse1 = ReqResParser.readBody(response1);
        JsonObject jsonResponse2 = ReqResParser.readBody(response2);

        assertEquals(jsonResponse1, jsonResponse2);
    }

    @Test
    @Order(4)
    void testGetWithInvalidStationId() throws Exception {
        String response1 = client1.sendGET(serverAddress, "IDS60902");

        assertNull(response1);
    }
}

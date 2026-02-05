import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import utilize.JsonUtil;

import java.nio.file.Files;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonUtilTest {
    @Test
    void testParsingFromText() throws Exception {
        String filePath = "src/main/java/content/weather_1.txt";
        JsonObject obj = JsonUtil.fromText(filePath);

        assertEquals("IDS60901", obj.get("id").getAsString());
        assertEquals("Adelaide (West Terrace /  ngayirdapira)", obj.get("name").getAsString());
        assertEquals(-34.9, obj.get("lat").getAsDouble());
        assertEquals(138.6, obj.get("lon").getAsDouble());
        assertEquals("20230715160000", obj.get("local_date_time_full").getAsString());
    }

    @Test
    void testToJsonObjectFromMap() throws Exception {
        var map = new HashMap<String, String>();
        map.put("id", "ST02");
        map.put("state", "NSW");

        JsonObject obj = JsonUtil.toJsonObj(map);

        assertEquals("ST02", obj.get("id").getAsString());
        assertEquals("NSW", obj.get("state").getAsString());
    }

    @Test
    void testSerializeAndDeserialize() throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "ST03");
        obj.addProperty("temp", 25.5);

        String json = JsonUtil.serialize(obj);
        JsonObject parsed = JsonUtil.deserialize(json, JsonObject.class);

        assertEquals("ST03", parsed.get("id").getAsString());
        assertEquals(25.5, parsed.get("temp").getAsDouble());
    }
}

package utilize;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JsonUtil {
    private static final Gson gson = new Gson();

    /**
     * Read data from .txt file as described in Assignment 2 to JsonObject
     * @param path (String)
     * @return JsonObject
     */
    public static JsonObject fromText(String path) throws Exception{
        if (path == null) {
            throw new Exception("Invalid path.");
        }

        Map<String, Object> map = new HashMap<>();

        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String line : lines) {
            String[] item = line.split(":", 2);
            String key = item[0].trim();
            String value = item[1].trim();

            map.put(key, value);
        }

        return gson.toJsonTree(map).getAsJsonObject();
    }
    /**
     * Convert Map object to JsonObject
     * @param data (Map)
     * @return JsonObject
     */
    public static JsonObject toJsonObj(Map<String, String> data) throws Exception {
        if (data == null) {
            throw new Exception("Input is null.");
        }

        return gson.toJsonTree(data).getAsJsonObject();
    }

    /**
     * Convert String to JsonObject. The String object need to be in the JSON format
     * @param data (String)
     * @return JsonObject
     */
    public static JsonObject toJsonObj(String data) throws Exception {
        if (data == null) {
            throw new Exception("Input is null.");
        }

        return JsonParser.parseString(data).getAsJsonObject();
    }

    /**
     * Serialize an Object to String that in JSON format
     * @param jsonObj (T)
     * @return String
     */
    public static <T> String serialize(T jsonObj){
        return gson.toJson(jsonObj);
    }

    /**
     * Deserialize a String to a desire type (object)
     * @param data (String)
     * @param type (Type)
     * @return T (Type.class())
     */
    public static <T> T deserialize(String data, Type type){
        return gson.fromJson(data, type);
    }
}

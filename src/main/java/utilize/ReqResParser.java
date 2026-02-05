package utilize;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

public class ReqResParser {
    public record Header(String requestType, Map<String, String> metadata) {}

    public static String[] splitHeaderAndBodyFromRequest(BufferedReader in) throws Exception {
        String[] result = new String[2];
        String line;
        StringBuilder header = new StringBuilder();
        StringBuilder body = new StringBuilder();
        int contentLength = 0;

        // Read Header
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length: ")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }

            header.append(line).append("\r\n");
        }

        // Read Body
        int off = 0;
        char[] buf = new char[contentLength];
        while (off < contentLength) {
            int r = in.read(buf, off, contentLength - off);
            if (r == -1) {
                break;
            }
            off += r;
        }
        String content = new String(buf);
        body.append(content);

        // Add Header and Body to result
        result[0] = header.toString();
        result[1] = body.toString();
        return result;
    }

    public static Header readHeader(String header) {
        String[] rows = header.split("\r\n");
        String requestType = rows[0].split(" ")[0].trim();
        Map<String, String> metadata = new HashMap<>();

        for (int i = 1; i < rows.length; i++) {
            String[] pair = rows[i].split(": ", 2);
            String key = pair[0];
            String value = pair[1];

            metadata.put(key, value);
        }

        return new Header(requestType, metadata);
    }

    public static JsonObject readBody(String body) throws Exception {
        return JsonUtil.toJsonObj(body);
    }
}

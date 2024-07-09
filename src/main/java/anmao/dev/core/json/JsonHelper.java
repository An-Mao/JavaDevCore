package anmao.dev.core.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class JsonHelper {
    public static void CheckData(String sourceJson,String targetJsonFilePath) {
        try {
            JsonObject sourceJsonObj = JsonParser.parseString(sourceJson).getAsJsonObject();
            JsonObject targetJsonObj = readJsonFromFile(targetJsonFilePath);
            mergeJsonObjects(sourceJsonObj, targetJsonObj);
            writeJsonToFile(targetJsonObj, targetJsonFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
            //LOGGER.error(e.getMessage());
        }
    }

    private static JsonObject readJsonFromFile(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void writeJsonToFile(JsonObject jsonObj, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(jsonObj.toString());
        }
    }
    private static void mergeJsonObjects(JsonObject sourceObj, JsonObject targetObj) {
        sourceObj.entrySet().forEach(entry -> {
            String key = entry.getKey();
            JsonElement valueSource = entry.getValue();
            if (targetObj.has(key)) {
                JsonElement valueTarget = targetObj.get(key);
                if (valueSource.isJsonObject() && valueTarget.isJsonObject()) {
                    mergeJsonObjects(valueSource.getAsJsonObject(), valueTarget.getAsJsonObject());
                }
            } else {
                targetObj.add(key, valueSource);
            }
        });
    }
}

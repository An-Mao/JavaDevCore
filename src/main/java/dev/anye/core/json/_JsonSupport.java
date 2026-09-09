package dev.anye.core.json;

import com.google.gson.*;
import dev.anye.core.cdt._SuffixCDT;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

public class _JsonSupport {
	public static final Gson GSON = new Gson();

	@Deprecated(since = "2.0.6")
	public static void checkData(String sourceJson, String targetJsonFilePath) {
		try {
			JsonElement sourceJsonElement = JsonParser.parseString(sourceJson);
			JsonElement targetJsonElement = readJsonFromFile(targetJsonFilePath);
			JsonElement mergedJsonElement = mergeJsonElements(sourceJsonElement, targetJsonElement);
			writeJsonToFile(mergedJsonElement, targetJsonFilePath);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	public static void mergeDefaultData(
			JsonElement defaultElement,
			String targetFilePath) {
		try {
			JsonElement targetElement = readJsonFromFile(targetFilePath);
			JsonElement mergedElement = mergeJsonElements(defaultElement, targetElement);
			writeJsonToFile(mergedElement, targetFilePath);
		} catch (IOException | JsonParseException e) {
			throw new _IOException(e);
		}
	}

	private static JsonElement readJsonFromFile(String filePath)
			throws IOException {

		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			return JsonParser.parseReader(reader);
		}
	}
	protected static void writeJsonToFile(
			JsonElement jsonElement,
			String filePath
	) throws IOException {

		Path target = Paths.get(filePath);
		Path temp = Paths.get(filePath + "." + UUID.randomUUID() + _SuffixCDT.TMP_SUFFIX);

		try {
			try (Writer writer = _File.startWriterWithUtf8(temp.toString())) {
				//writer.write(jsonElement.toString());
				GSON.toJson(jsonElement, writer);
			}

			try {
				Files.move(
						temp,
						target,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE
				);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(
						temp,
						target,
						StandardCopyOption.REPLACE_EXISTING
				);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	private static JsonElement mergeJsonElements(
			JsonElement source,
			JsonElement target
	) {
		if (source.isJsonObject() && target.isJsonObject()) {
			JsonObject sourceObj = source.getAsJsonObject();
			JsonObject targetObj = target.getAsJsonObject();

			for (Map.Entry<String, JsonElement> entry :
					sourceObj.entrySet()) {

				String key = entry.getKey();
				JsonElement sourceValue = entry.getValue();

				if (targetObj.has(key)) {
					JsonElement targetValue = targetObj.get(key);

					targetObj.add(
							key,
							mergeJsonElements(sourceValue, targetValue)
					);
				} else {
					targetObj.add(key, sourceValue.deepCopy());
				}
			}

			return targetObj;
		}

		if (source.isJsonArray() && target.isJsonArray()) {
			JsonArray sourceArray = source.getAsJsonArray();
			JsonArray targetArray = target.getAsJsonArray();

			if (targetArray.isEmpty()) {
				for (JsonElement element : sourceArray) {
					targetArray.add(element.deepCopy());
				}
			}

			return targetArray;
		}

		if (target.isJsonNull()) {
			return source.deepCopy();
		}

		return target;
	}
}
/*

public class _JsonSupport {
	public static final Gson GSON = new Gson();

	public static void checkData(String sourceJson, String targetJsonFilePath) {
		try {
			JsonElement sourceJsonElement = JsonParser.parseString(sourceJson);
			JsonElement targetJsonElement = readJsonFromFile(targetJsonFilePath);
			JsonElement mergedJsonElement = mergeJsonElements(sourceJsonElement, targetJsonElement);
			writeJsonToFile(mergedJsonElement, targetJsonFilePath);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	private static JsonElement readJsonFromFile(String filePath) throws IOException {
		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			return JsonParser.parseReader(reader);
		}
	}

	private static void writeJsonToFile(JsonElement jsonElement, String filePath) throws IOException {
		try (Writer writer = _File.startWriterWithUtf8(filePath)) {
			writer.write(jsonElement.toString());
		}
	}

	private static JsonElement mergeJsonElements(JsonElement sourceElement, JsonElement targetElement) {
		if (sourceElement.isJsonObject() && targetElement.isJsonObject()) {
			JsonObject sourceObj = sourceElement.getAsJsonObject();
			JsonObject targetObj = targetElement.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : sourceObj.entrySet()) {
				String key = entry.getKey();
				JsonElement valueSource = entry.getValue();
				if (targetObj.has(key)) {
					JsonElement valueTarget = targetObj.get(key);
					targetObj.add(key, mergeJsonElements(valueSource, valueTarget));
				} else {
					targetObj.add(key, valueSource);
				}
			}
			return targetObj;
		} else if (sourceElement.isJsonArray() && targetElement.isJsonArray()) {
			JsonArray sourceArray = sourceElement.getAsJsonArray();
			JsonArray targetArray = targetElement.getAsJsonArray();
			if (targetArray.isEmpty()) {
				for (JsonElement element : sourceArray) {
					targetArray.add(element);
				}
			}
			return targetArray;
		} else if (targetElement.isJsonNull() || (targetElement.isJsonArray() && targetElement.getAsJsonArray().isEmpty())) {
			return sourceElement;
		} else {
			return targetElement;
		}
	}
}
*/

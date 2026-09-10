package dev.anye.core.json;

import com.google.gson.*;
import dev.anye.core.cdt._SuffixCDT;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

public class _JsonSupport {

	private _JsonSupport(){}

	public static final Gson GSON = new Gson();

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
			return mergeJsonObject(source.getAsJsonObject(),target.getAsJsonObject());
		}
		if (source.isJsonArray() && target.isJsonArray()) {
			return mergeJsonArray(source.getAsJsonArray(),target.getAsJsonArray());
		}
		if (target.isJsonNull()) {
			return source.deepCopy();
		}

		return target;
	}

	public static JsonElement mergeJsonObject(JsonObject sourceObj,JsonObject targetObj){
		for (Map.Entry<String, JsonElement> entry : sourceObj.entrySet()) {

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

	public static JsonElement mergeJsonArray(JsonArray sourceArray,JsonArray targetArray){
		if (targetArray.isEmpty()) {
			for (JsonElement element : sourceArray) {
				targetArray.add(element.deepCopy());
			}
		}
		return targetArray;
	}
}

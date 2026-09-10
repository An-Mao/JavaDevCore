package dev.anye.core.data;

import java.util.HashMap;
import java.util.Map;

public record _DataMap(
		Map<String, Boolean> booleanMap,
		Map<String, Integer> integerMap,
		Map<String, String> stringMap,
		Map<String, Float> floatMap,
		Map<String, Double> doubleMap,
		Map<String, Byte> byteMap,
		Map<String, Long> longMap,
		Map<String, Short> shortMap,
		Map<String, Character> characterMap

) {


	public static Builder builder() {
		return new Builder();
	}


	public static class Builder {
		Map<String, Boolean> booleanMap = new HashMap<>();
		Map<String, Integer> integerMap = new HashMap<>();
		Map<String, String> stringMap = new HashMap<>();
		Map<String, Float> floatMap = new HashMap<>();
		Map<String, Double> doubleMap = new HashMap<>();
		Map<String, Byte> byteMap = new HashMap<>();
		Map<String, Long> longMap = new HashMap<>();
		Map<String, Short> shortMap = new HashMap<>();
		Map<String, Character> characterMap = new HashMap<>();

		public Builder addBoolean(String key, boolean value) {
			booleanMap.put(key, value);
			return this;
		}

		public Builder addInt(String key, int value) {
			integerMap.put(key, value);
			return this;
		}

		public Builder addString(String key, String value) {
			stringMap.put(key, value);
			return this;
		}

		public Builder addFloat(String key, float value) {
			floatMap.put(key, value);
			return this;
		}

		public Builder addDouble(String key, double value) {
			doubleMap.put(key, value);
			return this;
		}

		public Builder addByte(String key, byte value) {
			byteMap.put(key, value);
			return this;
		}

		public Builder addLong(String key, long value) {
			longMap.put(key, value);
			return this;
		}

		public Builder addShort(String key, short value) {
			shortMap.put(key, value);
			return this;
		}

		public Builder addChar(String key, char value) {
			characterMap.put(key, value);
			return this;
		}


		public _DataMap build() {
			return new _DataMap(booleanMap, integerMap, stringMap, floatMap, doubleMap, byteMap, longMap, shortMap, characterMap);
		}
	}
}

package dev.anye.core.data;

import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class _Data {
	private static final Gson GSON = new Gson();
	private final Map<String, Object> map;

	public _Data() {
		this(new HashMap<>());
	}

	public _Data(Map<String, Object> map) {
		this.map = map;
	}

	public Map<String, Object> map() {
		return map;
	}

	public void remove(String key) {
		map.remove(key);
	}

	public _Data add(String key, Object value) {
		map.put(key, value);
		return this;
	}

	public Object get(String key) {
		return map.get(key);
	}

	public <T> T get(String key, Class<T> type) {
		return type.cast(get(key));
	}

	public <T> T as(String key, Class<T> type) {
		return convert(get(key), type);
	}

	public <T> T as(String key, Type type) {
		return convert(get(key), type);
	}

	public static <T> T convert(Object value, Class<T> type) {
		return convert(value, (Type) type);
	}

	@SuppressWarnings("unchecked")
	public static <T> T convert(Object value, Type type) {
		if (value == null) {
			return null;
		}
		if (type instanceof Class<?> clazz && clazz.isInstance(value)) {
			//T result = (T) value;
			return (T) value;
		}
		return GSON.fromJson(
				GSON.toJsonTree(value),
				type
		);
	}

	public Number asNumber(String key) {
		return as(key, Number.class);
	}

	public int asInt(String key) {
		return asNumber(key).intValue();
	}

	public double asDouble(String key) {
		return asNumber(key).doubleValue();
	}

	public float asFloat(String key) {
		return asNumber(key).floatValue();
	}

	public long asLong(String key) {
		return asNumber(key).longValue();
	}

	public byte asByte(String key) {
		return asNumber(key).byteValue();
	}

	public short asShort(String key) {
		return asNumber(key).shortValue();
	}

	public boolean asBool(String key) {
		return as(key, Boolean.class);
	}

	public String asString(String key) {
		return as(key, String.class);
	}

	public int getInt(String key) {
		return get(key, Integer.class);
	}

	public boolean getBool(String key) {
		return get(key, Boolean.class);
	}

	public double getDouble(String key) {
		return get(key, Double.class);
	}

	public float getFloat(String key) {
		return get(key, Float.class);
	}

	public long getLong(String key) {
		return get(key, Long.class);
	}

	public byte getByte(String key) {
		return get(key, Byte.class);
	}

	public short getShort(String key) {
		return get(key, Short.class);
	}

	public char getChar(String key) {
		return get(key, Character.class);
	}

	public String getString(String key) {
		return get(key, String.class);
	}

	public static _Data empty() {
		return new _Data();
	}

	public static _Data create(Consumer<_Data> consumer) {
		_Data data = new _Data();
		consumer.accept(data);
		return data;
	}
}

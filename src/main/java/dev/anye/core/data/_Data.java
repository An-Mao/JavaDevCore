package dev.anye.core.data;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class _Data {
	private final Map<String,Object> map;

	public _Data(){
		this(new HashMap<>());
	}
	public _Data(Map<String,Object> map){
		this.map = map;
	}

	public Map<String,Object> map(){
		return map;
	}

	public void remove(String key){
		map.remove(key);
	}

	public _Data add(String key,Object value){
		map.put(key,value);
		return this;
	}
	public Object get(String key){
		return map.get(key);
	}

	public <T> T get(String key,Class<T> type){
		try {
			return type.cast(map.get(key));
		}catch (ClassCastException e){
			throw new ClassCastException(e.getMessage());
		}
	}
	public int asInt(String key){
		return get(key,Integer.class);
	}
	public boolean asBool(String key){
		return get(key, Boolean.class);
	}
	public double asDouble(String key){
		return get(key,Double.class);
	}
	public float asFloat(String key){
		return get(key, Float.class);
	}
	public long asLong(String key){
		return get(key, Long.class);
	}
	public byte asByte(String key){
		return get(key,Byte.class);
	}
	public short asShort(String key){
		return get(key, Short.class);
	}
	public char asChar(String key){
		return get(key, Character.class);
	}
	public String asString(String key){
		return get(key, String.class);
	}

	public static _Data empty(){
		return new _Data();
	}
	public static _Data create(Consumer<_Data> consumer){
		_Data data = new _Data();
		consumer.accept(data);
		return data;
	}

}

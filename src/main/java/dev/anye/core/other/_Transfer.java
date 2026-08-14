package dev.anye.core.other;

import java.util.function.Consumer;

public class _Transfer<T>{
	private T value;
	public _Transfer(T value){
		this.value = value;
	}

	public void set(T value){
		this.value = value;
	}

	public T get(){
		return value;
	}
	public T orElse(T other){
		if (value == null) return other;
		return value;
	}
	public boolean isPresent(){
		return value != null;
	}
	public T orElseThrow(){
		if (value == null) throw new NullPointerException();
		return value;
	}
	public void ifPresent(Consumer<T> consumer){
		if (value != null){
			consumer.accept(value);
		}
	}
}

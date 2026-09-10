package dev.anye.core.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class _JsonCore<T> {
	protected final String filePath;
	protected final Type type;
	public static final Gson GSON = new Gson();
	protected _JsonCore(String filePath,TypeToken<T> typeToken){
		this(filePath, typeToken.getType());
	}
	protected _JsonCore(String filePath,Type type){
		this.filePath = filePath;
		this.type = type;
	}


	public abstract void read(Consumer<? super T> action);

	public abstract <R> R read(Function<? super T, ? extends R> function);
}

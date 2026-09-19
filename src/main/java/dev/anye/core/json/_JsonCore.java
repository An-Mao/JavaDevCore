package dev.anye.core.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class _JsonCore<T> {
	public static final Gson GSON = new Gson();

	protected final String filePath;
	protected final Type type;
	protected _JsonCore(String filePath,TypeToken<T> typeToken){
		this(filePath, typeToken.getType());
	}
	protected _JsonCore(String filePath,Type type){
		this.filePath = filePath;
		this.type = type;
	}




	/**
	 * 读取数据，并执行操作，当内部数据为null时，若备用数据不为null则使用备用数据进行操作，否则不进行任何操作。
	 * 具体使用需要参考子类的实现
	 * @param action 要执行的操作
	 * @param spare 备用数据
	 */
	public abstract void read(Consumer<? super T> action,T spare);

	public void read(Consumer<? super T> action){
		read(action,null);
	}

	/**
	 * 从数据中取回指定内容
	 * @param function 要执行的取回操作
	 * @return 数据为null时返回null
	 * @param <R> 数据类型
	 */
	public <R> R fetch(Function<? super T, ? extends R> function){
		return fetch(function,null);
	}

	/**
	 * 从数据中取回指定内容
	 * @param function 要执行的取回操作
	 * @param defaultValue 默认值
	 * @return 当总结果为null时，返回默认值
	 * @param <R> 数据类型
	 */
	public abstract <R> R fetch(Function<? super T, ? extends R> function,R defaultValue);

	public <R> Optional<R> fetchOpt(Function<? super T, ? extends R> function){
		return Optional.ofNullable(fetch(function));
	}


}

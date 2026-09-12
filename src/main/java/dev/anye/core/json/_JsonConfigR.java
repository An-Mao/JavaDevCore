package dev.anye.core.json;

import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 只读型，仅在初始化时写入，后续不进行写入。
 *
 * <p>data的内部数据通常不应修改。
 *
 * <p>由于data变量暴露，可能会有线程安全的问题。若要完善，则可能损失一些性能。
 *
 * <p>如果确实需要线程安全，可以考虑使用{@link _JsonConfigS}
 *
 * @param <T> 目标数据
 */
public abstract class _JsonConfigR<T extends Record> extends _JsonCore<T> {
	protected final boolean checkData;
	private final T defaultRawData;
	protected T data;

	protected _JsonConfigR(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		super(filePath, typeToken);
		this.defaultRawData = GSON.fromJson(GSON.toJson(defaultRawData), type);
		this.checkData = checkData;
		this.data = null;
		init();
	}

	protected _JsonConfigR(String filePath, T defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	protected final void init() {
		File file = new File(filePath);
		if (!file.exists()) {
			reset();
		} else {
			if (checkData) {
				_JsonSupport.mergeDefaultData(GSON.toJsonTree(defaultRawData, type), filePath);
			}
			load();
		}
	}

	public final void reload() {
		load();
	}

	protected final void reset() {
		try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
			writer.write(GSON.toJson(defaultRawData, this.type));
			T newData = GSON.fromJson(GSON.toJson(defaultRawData), this.type);
			if (newData != null) this.data = newData;
		} catch (IOException e) {
			throw new _IOException(e);
		}

	}

	private void load() {
			try (Reader reader = _File.loadFileWithUtf8(filePath)) {
				T newData = GSON.fromJson(reader, this.type);
				if (newData != null) this.data = newData;
			} catch (IOException e) {
				throw new _IOException(e);
			} catch (Exception e) {
				throw new RuntimeException("Failed to parse JSON config: " + filePath, e);
			}

	}

	public final void read(Consumer<? super T> action) {
		read(action, null);
	}
	public final void read(Consumer<? super T> action,T defaultValue) {
		if (data != null) {
			action.accept(data);
		}else if (defaultValue != null){
			action.accept(defaultValue);
		}
	}
	public final <R> R read(Function<? super T, ? extends R> function) {
		return read(function,null);
	}
	public final <R> R read(Function<? super T, ? extends R> function,R defaultValue) {
		return data == null ? defaultValue : function.apply(data);
	}
	public final void setData(T newData) {
		if (newData == null)
			return;
		this.data = newData;
	}
	public T data(){
		return data;
	}
}
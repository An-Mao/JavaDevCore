package dev.anye.core.json;

import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class _JsonConfig<T> extends _JsonCore<T> {
	protected final boolean checkData;
	private final T defaultRawData;

	// Expose visibility to avoid the need for subclasses to customize.
	protected final AtomicReference<T> data;

	// 新增：用于保证磁盘文件读写并发安全的锁对象
	private final Object fileLock = new Object();

	protected _JsonConfig(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		super(filePath, typeToken);
		this.defaultRawData = GSON.fromJson(GSON.toJson(defaultRawData), type);
		this.checkData = checkData;
		this.data = new AtomicReference<>();
		init();
	}

	protected _JsonConfig(String filePath, T defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	/**
	 * @param filePath    filePath
	 * @param defaultData defaultData
	 * @param typeToken   typeToken
	 * @deprecated Using this method is no longer recommended, as it may entail
	 *             numerous issues;
	 */
	@Deprecated(since = "2.0.5")
	@SuppressWarnings("unchecked")
	protected _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken, boolean checkData) {
		this(filePath, (T) GSON.fromJson(defaultData, typeToken.getType()), typeToken, checkData);
	}

	/**
	 * @param filePath    filePath
	 * @param defaultData defaultData
	 * @param typeToken   typeToken
	 * @deprecated Using this method is no longer recommended, as it may entail
	 *             numerous issues;
	 */
	@Deprecated(since = "2.0.5")
	protected _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	/**
	 * Initial loading
	 * <li>When {@link _JsonConfig#checkData} is enabled, parts that do not conform
	 * to the default data format will be replaced.
	 * 声明为 final 防止子类重写导致构造期逸出
	 */
	public final void init() {
		synchronized (fileLock) {
			File file = new File(filePath);
			if (!file.exists()) {
				// reset 内部已经同步了内存和磁盘，这里不需要再 load 造成二次 I/O
				reset();
			} else {
				if (checkData) {
					_JsonSupport.mergeDefaultData(GSON.toJsonTree(defaultRawData, type), filePath);
				}
				load();
			}
		}
	}

	// 声明为 final
	public final void reload() {
		load();
	}

	/**
	 * Overwrite the original content with default data.
	 * Directory existence is not checked; please ensure the provided path already
	 * exists.
	 * 声明为 final
	 */
	public final void reset() {
		synchronized (fileLock) {
			try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
				writer.write(GSON.toJson(defaultRawData, this.type));
				// 修复 Bug：reset 之后，同步更新内存状态，避免数据不一致
				this.data.set(GSON.fromJson(GSON.toJson(defaultRawData), this.type));
			} catch (IOException e) {
				throw new _IOException(e);
			}
		}
	}

	/**
	 * Loads file data and throws an exception if the data is null; please verify
	 * that the structure matches the expected type.
	 * Before loading completes, the data remains the old data (or
	 * {@link Optional#empty} if it is the initial load).
	 */
	private void load() {
		synchronized (fileLock) {
			try (Reader reader = _File.loadFileWithUtf8(filePath)) {
				data.set(GSON.fromJson(reader, this.type));
			} catch (IOException e) {
				// 统一抛出 _IOException
				throw new _IOException(e);
			} catch (Exception e) {
				throw new RuntimeException("Failed to parse JSON config: " + filePath, e);
			}
		}
	}

	/**
	 * Save new data to file and update memory.
	 * 声明为 final
	 *
	 * @param data new data
	 */
	public final void save(T data) {
		if (data == null)
			return;
		setSaveFile(data);
		this.data.set(data);
	}

	/**
	 * Save existing data to a file and load it.
	 * 声明为 final
	 */
	public final void save() {
		save(this.data.get());
	}

	/**
	 * Reads the current data while holding the data lock.
	 * The action should execute quickly and should not perform blocking operations.
	 * 声明为 final
	 */
	public final void read(Consumer<? super T> action) {
		read(action,null);
	}

	public final void read(Consumer<? super T> action,T defaultValue) {
		T currentData = data.get();
		if (currentData != null) {
			action.accept(currentData);
		}else if (defaultValue != null){
			action.accept(defaultValue);
		}
	}

	// 修复 Bug：将原 data == null 改为 currentData == null 的正确判断
	public final <R> R read(Function<? super T, ? extends R> function) {
		return read(function,null);
	}
	public final <R> R read(Function<? super T, ? extends R> function,R defaultValue) {
		T currentData = data.get();
		return currentData == null ? defaultValue : function.apply(currentData);
	}

	/**
	 * Modify the existing data only, without saving it.
	 * Must not be null.
	 * 声明为 final
	 *
	 * @param data new data
	 */
	public final void setData(T data) {
		if (data == null)
			return;
		this.data.set(data);
	}

	/**
	 * Save without altering the existing data.
	 * 声明为 final
	 */
	public final void setSaveFile(T data) {
		synchronized (fileLock) {
			try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
				GSON.toJson(data, this.type, writer);
			} catch (IOException e) {
				throw new _IOException(e);
			}
		}
	}

}
package dev.anye.core.json;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class _JsonConfigX<T> extends _JsonSupport {
	protected final boolean checkData;
	protected final String filePath;
	private final String defaultRawDataBase;
	private final T defaultRawData;
	protected final Type type;

	protected final AtomicReference<T> data = new AtomicReference<>();

	private final Object dataLock = new Object();
	private final Object fileLock = new Object();

	protected _JsonConfigX(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		this.filePath = filePath;
		this.type = typeToken.getType();

		defaultRawDataBase = GSON.toJson(defaultRawData);
		this.defaultRawData = GSON.fromJson(defaultRawDataBase, type);
		this.checkData = checkData;
		init();
	}

	protected _JsonConfigX(String filePath, T defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	/**
	 * @param filePath    filePath
	 * @param defaultData defaultData
	 * @param typeToken   typeToken
	 * @deprecated Using this method is no longer recommended, as it may entail numerous issues;
	 */
	@Deprecated(since = "2.0.5")
	@SuppressWarnings("unchecked")
	protected _JsonConfigX(String filePath, String defaultData, TypeToken<T> typeToken, boolean checkData) {

		this(filePath, (T) GSON.fromJson(defaultData, typeToken.getType()), typeToken, checkData);
	}

	/**
	 * @param filePath    filePath
	 * @param defaultData defaultData
	 * @param typeToken   typeToken
	 * @deprecated Using this method is no longer recommended, as it may entail numerous issues;
	 */
	@Deprecated(since = "2.0.5")
	protected _JsonConfigX(String filePath, String defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}


	/**
	 * Initial loading
	 * <li>When {@link _JsonConfigX#mergeDefaultData} is enabled, parts that do not conform to the default data format will be replaced.
	 */
	public void init() {
		synchronized (fileLock) {
			File file = new File(filePath);
			if (!file.exists()) {
				reset();
			} else {
				if (defaultRawData != null && checkData)
					mergeDefaultData(GSON.toJsonTree(defaultRawData, type), filePath);
			}
			load();
		}
	}



	/*public void reload() {
		load();

	}*/
	public void reload() {
		synchronized (fileLock) {
			load();
		}
	}
	/**
	 * Overwrite the original content with default data.
	 * Directory existence is not checked; please ensure the provided path already exists.
	 */
	public void reset() {
		if (defaultRawData == null) return;
		save(GSON.fromJson(defaultRawDataBase, this.type));
			/*try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
				writer.write(GSON.toJson(defaultRawData, this.type));
			} catch (IOException e) {
				throw new _IOException(e);
			}*/

	}

	/**
	 * Loads file data and throws an exception if the data is null; please verify that the structure matches the expected type.
	 * Before loading completes, the data remains the old data (or {@link Optional#empty} if it is the initial load).
	 */
	/*private void load() {
		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			T newData = GSON.fromJson(reader, type);
			if (newData != null){
				data.set(newData);
			}
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}*/
	private void load() {
		final T newData;

		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			newData = GSON.fromJson(reader, type);
		} catch (IOException e) {
			throw new _IOException(e);
		}

		if (newData != null) {
			data.set(newData);
		}
	}
	/**
	 * Save new data to file and update memory.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 *
	 * @param newData new data
	 */
	public void save(T newData) {
		if (newData == null) {
			return;
		}

		final JsonElement json = GSON.toJsonTree(newData, type);

		synchronized (fileLock) {
			saveJsonToFile(json);

			synchronized (dataLock) {
				data.set(newData);
			}
		}
	}
	/*public void save(T newData) {
		if (newData == null) {
			return;
		}

		final JsonElement json;

		synchronized (dataLock) {
			T value = data.get();

			if (value == null) {
				return;
			}

			json = GSON.toJsonTree(value, type);
		}

		synchronized (fileLock) {
			saveJsonToFile(json);
			data.set(newData);
		}
	}*/
	/*public void save(T data) {
		if (data == null) return;
		setSaveFile(data);
		this.data.set(data);
	}*/

	/**
	 * Save existing data to a file and load it.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 */
	public void save() {
		final JsonElement json;

		synchronized (dataLock) {
			T value = data.get();

			if (value == null) {
				return;
			}

			json = GSON.toJsonTree(value, type);
		}

		synchronized (fileLock) {
			saveJsonToFile(json);
		}
	}

	public Optional<T> data() {
		return Optional.ofNullable(data.get());
	}

	public T getDataOrDefault(T other) {
		if (this.data.get() == null) return other;
		return this.data.get();
	}

	public T getDataOrDefault() {
		return getDataOrDefault(defaultRawData);
	}

	protected T copyData(T source) {
		if (source == null) {
			return null;
		}

		return GSON.fromJson(
				GSON.toJsonTree(source, type),
				type
		);
	}

	public void update(Consumer<T> action) {
		synchronized (dataLock) {
			T value = data.get();

			if (value != null) {
				action.accept(value);
			}
		}
	}

	public void updateCopy(Consumer<T> action) {
		synchronized (dataLock) {
			T oldData = data.get();

			if (oldData == null) {
				return;
			}

			T newData = copyData(oldData);

			action.accept(newData);

			data.set(newData);
		}
	}


	/**
	 * Modify the existing data only, without saving it.
	 * Must not be null.
	 *
	 * @param data new data
	 */
	public void setData(T data) {
		synchronized (dataLock) {
			this.data.set(data);
		}
	}

	/**
	 * Save without altering the existing data.
	 */
	protected void saveJsonToFile(JsonElement json) {
		try {
			writeJsonToFile(json, filePath);
		} catch (IOException e) {
			throw new _IOException(e);
		}

	}


	public boolean isPresent() {
		return data.get() != null;
	}

	public void ifPresent(Consumer<? super T> action) {
		read(action);
	}

	public void read(Consumer<? super T> action) {
		synchronized (dataLock) {
			T value = data.get();

			if (value != null) {
				action.accept(value);
			}
		}
	}

	public <R> R read(Function<? super T, ? extends R> function) {
		synchronized (dataLock) {
			T value = data.get();

			return value == null ? null : function.apply(value);
		}
	}
}

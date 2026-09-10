package dev.anye.core.json;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class _JsonConfigS<T> extends _JsonCore<T> {
	private static final ExecutorService SAVE_EXECUTOR =
			Executors.newSingleThreadExecutor(r -> {
				Thread thread = new Thread(r, "JsonConfig-Save");
				thread.setDaemon(true);
				return thread;
			});

	protected final boolean checkData;
	private final T defaultRawData;

	protected T data;

	private final Object dataLock = new Object();
	private final Object fileLock = new Object();

	private boolean dirty;
	private long dataVersion;
	private boolean savePending;


	protected _JsonConfigS(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		super(filePath, typeToken.getType());
		this.defaultRawData = GSON.fromJson(GSON.toJson(defaultRawData, type), type);
		this.checkData = checkData;
		this.data = copyData(defaultRawData);
		init();
	}

	protected _JsonConfigS(String filePath, T defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	/**
	 * Initial loading
	 * <li> When {@link _JsonConfigS#checkData} is enabled, parts that do not conform to the default data format will be replaced.
	 * </li>
	 * <p>
	 * 初始化加载，子类通常不应该调用此方法，如果想重新加载，可以使用{@link _JsonConfigS#reload()}
	 * 当{@link _JsonConfigS#checkData}为true时，使用默认数据（不为null时）替换不合规的数据
	 */
	protected void init() {
		synchronized (fileLock) {
			File file = new File(filePath);

			if (!file.exists()) {
				reset();
			} else {
				if (defaultRawData != null && checkData) {
					_JsonSupport.mergeDefaultData(
							GSON.toJsonTree(defaultRawData, type),
							filePath
					);
				}

				load();
			}
		}
	}

	/**
	 * 重新加载
	 */
	public void reload() {
		synchronized (fileLock) {
			load();
		}
	}

	/**
	 * Overwrite the original content with default data.
	 * <p>Directory existence is not checked; please ensure the provided path already exists.</p>
	 * <p>如果默认数据不为NULL，则将当前数据替换为默认数据并写入文件。</p>
	 * <p>不会检查路径的合法性，以及目录是否存在。</p>
	 */
	public void reset() {
		if (defaultRawData == null) {
			return;
		}
		save(copyData(defaultRawData));
	}

	/**
	 * Loads file data and throws an exception if the data is null; please verify that the structure matches the expected type.
	 * Before loading completes, the data remains the old data (if it is the initial load).
	 * 加载文件数据，当文件内容无法与类型匹配时抛出异常。
	 */
	private void load() {
		final T newData;

		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			newData = GSON.fromJson(reader, type);
		} catch (IOException e) {
			throw new _IOException(e);
		}

		if (newData != null) {
			synchronized (dataLock) {
				data = newData;
				dirty = false;
			}
		}
	}

	public void saveIfDirtyAsync() {
		synchronized (dataLock) {
			if (data == null || !dirty || savePending) {
				return;
			}

			savePending = true;
		}

		try {
			SAVE_EXECUTOR.execute(this::processSave);
		} catch (RuntimeException e) {
			synchronized (dataLock) {
				savePending = false;
			}

			throw e;
		}
	}

	private void processSave() {
		try {
			while (true) {
				final JsonElement json;
				final long version;

				synchronized (dataLock) {
					if (data == null || !dirty) {
						savePending = false;
						return;
					}

					json = GSON.toJsonTree(data, type);
					version = dataVersion;
				}

				synchronized (fileLock) {
					saveJsonToFile(json);
				}

				synchronized (dataLock) {
					if (dataVersion == version) {
						dirty = false;
						savePending = false;
						return;
					}
				}
			}
		} catch (Throwable e) {
			synchronized (dataLock) {
				savePending = false;
			}

			e.printStackTrace();
		}
	}

	/**
	 * Saves the specified data to the file and replaces the current in-memory data.
	 * <p>
	 * Frequent calls are not recommended because they may cause unnecessary
	 * serialization and file I/O.
	 * </p>
	 *
	 * @param newData new data, ignored if null
	 */
	public void save(T newData) {
		if (newData == null) {
			return;
		}

		final JsonElement json = GSON.toJsonTree(newData, type);

		synchronized (fileLock) {
			saveJsonToFile(json);
		}

		synchronized (dataLock) {
			data = newData;
			dirty = false;
			dataVersion++;
		}
	}


	/**
	 * Saves the current in-memory data to the file.
	 * <p>
	 * Frequent calls are not recommended because they may cause unnecessary
	 * serialization and file I/O.
	 * </p>
	 */
	public void save() {
		saveInternal(false);
	}

	private void saveInternal(boolean onlyIfDirty) {
		final JsonElement json;
		final long version;

		synchronized (dataLock) {
			if (data == null || (onlyIfDirty && !dirty)) {
				return;
			}

			json = GSON.toJsonTree(data, type);
			version = dataVersion;
		}

		synchronized (fileLock) {
			saveJsonToFile(json);
		}

		synchronized (dataLock) {
			if (dataVersion == version) {
				dirty = false;
			}
		}
	}


	/**
	 * Creates a deep copy of the specified data using Gson.
	 *
	 * @param source source data
	 * @return deep copy, or null if source is null
	 */
	protected T copyData(T source) {
		if (source == null) {
			return null;
		}

		return GSON.fromJson(
				GSON.toJsonTree(source, type),
				type
		);
	}

	public boolean isDirty() {
		synchronized (dataLock) {
			return dirty;
		}
	}

	public void saveIfDirty() {
		saveInternal(true);
	}

	public void update(Consumer<T> action) {
		synchronized (dataLock) {
			if (data == null) {
				return;
			}

			try {
				action.accept(data);
			} finally {
				dirty = true;
				dataVersion++;
			}
		}
	}

	/**
	 * 修改data数据，先复制一份data，修改完成后替换现有data
	 *
	 * @param action action
	 */
	public void updateCopy(Consumer<T> action) {
		synchronized (dataLock) {
			if (data == null) {
				return;
			}

			T newData = copyData(data);
			action.accept(newData);

			data = newData;
			dirty = true;
			dataVersion++;
		}
	}


	/**
	 * Replaces the current in-memory data without saving it to the file.
	 * 仅修改现有data而不进行保存，如果为null则不进行操作。
	 *
	 * @param newData new data, ignored if null
	 */
	public void setData(T newData) {
		if (newData == null) {
			return;
		}

		synchronized (dataLock) {
			this.data = newData;
			dirty = true;
			dataVersion++;
		}
	}

	/**
	 * Save without altering the existing data.
	 */
	protected void saveJsonToFile(JsonElement json) {
		try {
			_JsonSupport.writeJsonToFile(json, filePath);
		} catch (IOException e) {
			throw new _IOException(e);
		}

	}

	public boolean isPresent() {
		synchronized (dataLock) {
			return data != null;
		}
	}

	/**
	 * Reads the current data while holding the data lock.
	 * The action should execute quickly and should not perform blocking operations.
	 */
	public void read(Consumer<? super T> action) {
		synchronized (dataLock) {
			if (data != null) {
				action.accept(data);
			}
		}
	}

	public <R> R read(Function<? super T, ? extends R> function) {
		synchronized (dataLock) {
			return data == null ? null : function.apply(data);
		}
	}
}

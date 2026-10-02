package dev.anye.core.json;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;
import dev.anye.core.system.log._Log;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 线程安全的 JSON 配置类。
 *
 * <p>V2 版本主要解决以下问题：</p>
 * <ul>
 *     <li>避免外部对象引用直接进入内部 data</li>
 *     <li>避免内部 data 引用通过 API 逃逸</li>
 *     <li>使用 dataVersion 检测保存快照是否过期</li>
 *     <li>异步保存请求自动合并</li>
 *     <li>reload / save / async save 之间进行版本协调</li>
 *     <li>统一多锁获取顺序：fileLock -> dataLock</li>
 * </ul>
 *
 * <p>
 * 如果只读不写，可以考虑使用 {@link _JsonConfigR}。
 * </p>
 *
 * @param <T> 配置数据类型
 */
public abstract class _JsonConfigS<T> extends _JsonCore<T> {

	private static final _Log LOG = new _Log(_JsonConfigS.class);

	/**
	 * 所有配置实例共用一个保存线程。
	 *
	 * <p>
	 * 单线程的目的不是保证数据线程安全，而是避免多个配置同时
	 * 进行磁盘写入造成额外竞争。
	 * </p>
	 */
	private static final ExecutorService SAVE_EXECUTOR =
			Executors.newSingleThreadExecutor(r -> {
				Thread thread = new Thread(r, "JsonConfig-Save");
				thread.setDaemon(true);
				return thread;
			});

	/**
	 * 是否检查数据结构。
	 */
	protected final boolean checkData;

	/**
	 * 默认数据。
	 *
	 * <p>
	 * 构造时已经进行了深复制，因此外部无法通过原始 defaultRawData
	 * 修改这里保存的默认值。
	 * </p>
	 */
	private final T defaultRawData;

	/**
	 * 当前内存数据。
	 *
	 * <p>
	 * 访问必须受到 dataLock 保护。
	 * </p>
	 */
	protected T data;

	/**
	 * 保护内存数据及其状态。
	 *
	 * <p>
	 * 保护：
	 * <ul>
	 *     <li>data</li>
	 *     <li>dirty</li>
	 *     <li>dataVersion</li>
	 *     <li>savePending</li>
	 * </ul>
	 * </p>
	 */
	private final Object dataLock = new Object();

	/**
	 * 保护文件操作。
	 *
	 * <p>
	 * 所有磁盘读取、写入必须通过该锁。
	 * </p>
	 */
	private final Object fileLock = new Object();

	/**
	 * 当前数据是否有尚未写入磁盘的修改。
	 */
	private boolean dirty;

	/**
	 * 内存数据版本。
	 *
	 * <p>
	 * 每次 data 发生逻辑变化都会递增。
	 * 保存时会记录快照版本，写入完成后再次检查版本。
	 * </p>
	 */
	private long dataVersion;

	/**
	 * 是否已经有异步保存任务提交到 SAVE_EXECUTOR。
	 *
	 * <p>
	 * 用于合并重复的异步保存请求。
	 * </p>
	 */
	private boolean savePending;

	protected _JsonConfigS(
			String filePath,
			T defaultRawData,
			TypeToken<T> typeToken,
			boolean checkData
	) {
		super(filePath, typeToken.getType());

		this.defaultRawData = copyData(defaultRawData);
		this.checkData = checkData;
		this.data = copyData(defaultRawData);

		init();
	}

	protected _JsonConfigS(
			String filePath,
			T defaultData,
			TypeToken<T> typeToken
	) {
		this(filePath, defaultData, typeToken, true);
	}

	/**
	 * 初始化。
	 *
	 * <p>
	 * 锁顺序：
	 * </p>
	 *
	 * <pre>
	 * fileLock -> dataLock
	 * </pre>
	 */
	protected void init() {
		synchronized (fileLock) {
			File file = new File(filePath);

			if (!file.exists()) {
				resetInternal();
				return;
			}

			if (defaultRawData != null && checkData) {
				_JsonSupport.mergeDefaultData(
						GSON.toJsonTree(defaultRawData, type),
						filePath
				);
			}

			loadInternal();
		}
	}

	/**
	 * 重新加载磁盘数据。
	 *
	 * <p>
	 * reload 会使当前内存数据失效，并增加 dataVersion。
	 * 因此已经产生但尚未写入的旧保存快照会被识别为过期。
	 * </p>
	 */
	public void reload() {
		synchronized (fileLock) {
			loadInternal();
		}
	}

	/**
	 * 实际执行加载。
	 *
	 * <p>
	 * 调用者必须已经持有 fileLock。
	 * </p>
	 */
	private void loadInternal() {
		final T newData;

		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			newData = GSON.fromJson(reader, type);
		} catch (IOException e) {
			throw new _IOException(e);
		}

		if (newData == null) {
			return;
		}

		synchronized (dataLock) {
			data = newData;
			dirty = false;
			dataVersion++;
		}
	}

	/**
	 * 将当前数据恢复为默认数据并立即写入文件。
	 */
	public void reset() {
		if (defaultRawData == null) {
			return;
		}

		T newData = copyData(defaultRawData);
		save(newData);
	}

	/**
	 * 初始化时使用的 reset。
	 *
	 * <p>
	 * 当前方法由 init() 调用。
	 * </p>
	 */
	private void resetInternal() {
		if (defaultRawData == null) {
			return;
		}

		T newData = copyData(defaultRawData);
		JsonElement json = GSON.toJsonTree(newData, type);

		saveJsonToFile(json);

		synchronized (dataLock) {
			data = newData;
			dirty = false;
			dataVersion++;
		}
	}

	/**
	 * 异步保存当前数据。
	 *
	 * <p>
	 * 多次连续调用只会产生一个实际的保存任务。
	 * 如果任务执行过程中数据继续变化，任务会继续保存最新版本。
	 * </p>
	 */
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

	/**
	 * 异步保存任务。
	 *
	 * <p>
	 * 核心流程：
	 * </p>
	 *
	 * <pre>
	 * 1. dataLock 内生成 JSON 快照
	 * 2. 记录快照版本
	 * 3. 获取 fileLock
	 * 4. 再次确认快照没有过期
	 * 5. 写入文件
	 * 6. 检查当前版本
	 * 7. 如果版本变化，则继续保存
	 * </pre>
	 */
	private void processSave() {
		try {
			while (true) {
				final JsonElement json;
				final long version;

				/*
				 * 只在生成快照时持有 dataLock。
				 *
				 * 不让磁盘 IO 阻塞 update()。
				 */
				synchronized (dataLock) {
					if (data == null || !dirty) {
						savePending = false;
						return;
					}

					json = GSON.toJsonTree(data, type);
					version = dataVersion;
				}

				/*
				 * 所有文件操作统一通过 fileLock。
				 */
				synchronized (fileLock) {

					/*
					 * 在真正写文件前再次确认快照仍然有效。
					 *
					 * 锁顺序：
					 *
					 * fileLock -> dataLock
					 */
					synchronized (dataLock) {
						if (data == null || !dirty || dataVersion != version) {
							continue;
						}
					}

					/*
					 * 注意：
					 *
					 * 此处 dataLock 已经释放。
					 * update() 可以继续修改内存数据。
					 *
					 * 如果发生修改，dataVersion 会发生变化，
					 * 最后的版本检查会发现快照过期。
					 */
					saveJsonToFile(json);
				}

				/*
				 * 判断刚才保存的版本是否仍然是最新版本。
				 */
				synchronized (dataLock) {
					if (dataVersion == version) {
						dirty = false;
						savePending = false;
						return;
					}

					/*
					 * 数据已经发生变化。
					 *
					 * dirty 保持 true。
					 *
					 * 下一轮循环会生成新的 JSON 快照。
					 */
				}
			}
		} catch (Throwable e) {
			/*
			 * 保存失败不能清除 dirty。
			 *
			 * 否则可能导致：
			 *
			 * 内存数据 != 磁盘数据
			 * dirty == false
			 *
			 * 最终无法再次触发保存。
			 */
			synchronized (dataLock) {
				savePending = false;
			}

			LOG.error("Failed to save JSON config: " + e);
		}
	}

	/**
	 * 将指定数据写入文件，并替换当前内存数据。
	 *
	 * <p>
	 * newData 会首先进行深复制，因此调用者之后修改原对象
	 * 不会影响当前配置。
	 * </p>
	 *
	 * <p>
	 * 这是一个强一致操作，因此会同时持有：
	 * </p>
	 *
	 * <pre>
	 * fileLock -> dataLock
	 * </pre>
	 *
	 * <p>
	 * 文件 IO 会在两个锁都持有的情况下进行。
	 * 这是故意的，因为 save(T) 的语义是：
	 * </p>
	 *
	 * <pre>
	 * 文件写入成功 + 内存替换
	 * </pre>
	 *
	 * 必须作为一个不可被 update()/reload() 插入的操作。
	 * </p>
	 *
	 * @param newData 新数据，null 时忽略
	 */
	public void save(T newData) {
		if (newData == null) {
			return;
		}

		/*
		 * 防止外部对象引用进入内部 data。
		 */
		final T newCopy = copyData(newData);
		final JsonElement json = GSON.toJsonTree(newCopy, type);

		synchronized (fileLock) {
			/*
			 * 先写文件。
			 */
			saveJsonToFile(json);

			/*
			 * 文件成功写入后再替换内存。
			 *
			 * 锁顺序：
			 * fileLock -> dataLock
			 */
			synchronized (dataLock) {
				data = newCopy;
				dirty = false;
				dataVersion++;
			}
		}
	}

	/**
	 * 保存当前内存数据。
	 *
	 * <p>
	 * 使用快照 + version 检查，不会长时间占用 dataLock。
	 * </p>
	 */
	public void save() {
		saveInternal(false);
	}

	/**
	 * 保存当前内存数据。
	 *
	 * @param onlyIfDirty 是否只有 dirty 时才保存
	 */
	private void saveInternal(boolean onlyIfDirty) {
		final JsonElement json;
		final long version;

		/*
		 * 创建内存快照。
		 */
		synchronized (dataLock) {
			if (data == null || (onlyIfDirty && !dirty)) {
				return;
			}

			json = GSON.toJsonTree(data, type);
			version = dataVersion;
		}

		/*
		 * 磁盘 IO。
		 */
		synchronized (fileLock) {
			saveJsonToFile(json);
		}

		/*
		 * 写入完成后检查快照是否已经过期。
		 */
		synchronized (dataLock) {
			if (dataVersion == version) {
				dirty = false;
			}
		}
	}
	/**
	 * 获取当前数据的独立副本。
	 *
	 * <p>
	 * 返回对象与内部 data 没有引用关系。
	 * </p>
	 *
	 * @return 当前数据副本，data 为 null 时返回 null
	 */
	public T copyData() {
		synchronized (dataLock) {
			return copyData(data);
		}
	}

	/**
	 * 判断当前数据是否需要保存。
	 */
	public boolean isDirty() {
		synchronized (dataLock) {
			return dirty;
		}
	}

	/**
	 * 保存当前数据（仅 dirty 时）。
	 */
	public void saveIfDirty() {
		saveInternal(true);
	}

	/**
	 * 直接修改当前内部数据。
	 *
	 * <p>
	 * action 整个执行过程都在 dataLock 内。
	 * </p>
	 *
	 * <p>
	 * action 如果抛出异常，内部数据可能已经发生部分修改，
	 * 因此仍然会将 dirty 设置为 true。
	 * </p>
	 *
	 * <p>
	 * action 不应该：
	 * </p>
	 *
	 * <ul>
	 *     <li>执行长时间阻塞操作</li>
	 *     <li>执行网络 IO</li>
	 *     <li>执行磁盘 IO</li>
	 *     <li>调用当前对象的 reload()</li>
	 *     <li>调用当前对象的 save()</li>
	 *     <li>调用当前对象的 update()</li>
	 * </ul>
	 *
	 * @param action 修改操作
	 */
	public void update(Consumer<T> action) {
		if (action == null) {
			return;
		}

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
	 * 修改 data。
	 *
	 * <p>
	 * 会先创建 data 的深复制。
	 * action 修改的是副本。
	 * action 成功完成后才替换内部 data。
	 * </p>
	 *
	 * <p>
	 * 如果 action 抛出异常，则原始 data 不会改变。
	 * </p>
	 *
	 * @param action 修改操作
	 */
	public void updateCopy(Consumer<T> action) {
		if (action == null) {
			return;
		}

		synchronized (dataLock) {
			if (data == null) {
				return;
			}

			T newData = copyData(data);

			/*
			 * action 失败时，不替换 data。
			 */
			action.accept(newData);

			data = newData;
			dirty = true;
			dataVersion++;
		}
	}

	/**
	 * 替换当前内存数据，但不立即保存。
	 *
	 * <p>
	 * newData 会进行深复制，避免外部引用直接进入内部 data。
	 * </p>
	 *
	 * @param newData 新数据，null 时忽略
	 */
	public void setData(T newData) {
		if (newData == null) {
			return;
		}

		T copy = copyData(newData);

		synchronized (dataLock) {
			data = copy;
			dirty = true;
			dataVersion++;
		}
	}

	/**
	 * 将 JSON 写入文件。
	 *
	 * <p>
	 * 调用者负责 fileLock。
	 * </p>
	 */
	protected void saveJsonToFile(JsonElement json) {
		try {
			_JsonSupport.writeJsonToFile(json, filePath);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	/**
	 * 判断当前是否存在数据。
	 */
	public boolean isPresent() {
		synchronized (dataLock) {
			return data != null;
		}
	}

	/**
	 * 在 dataLock 内读取当前数据。
	 *
	 * <p>
	 * action 执行期间内部 data 不会被其他 update/setData/reload 替换。
	 * </p>
	 *
	 * <p>
	 * action 不应该执行阻塞操作。
	 * </p>
	 *
	 * @param action 读取操作
	 * @param spare data 为 null 时使用的备用值
	 */
	@Override
	public void read(Consumer<? super T> action, T spare) {
		if (action == null) {
			return;
		}

		synchronized (dataLock) {
			if (data != null) {
				action.accept(data);
			} else if (spare != null) {
				action.accept(spare);
			}
		}
	}

	/**
	 * 在 dataLock 内计算结果。
	 *
	 * <p>
	 * 注意：
	 * 返回值离开此方法之后不再受到 dataLock 保护。
	 * </p>
	 *
	 * <p>
	 * 因此推荐返回：
	 * </p>
	 *
	 * <ul>
	 *     <li>基本类型</li>
	 *     <li>String</li>
	 *     <li>不可变对象</li>
	 *     <li>基于 data 计算出来的值</li>
	 * </ul>
	 *
	 * <p>
	 * 不推荐：
	 * </p>
	 *
	 * <pre>
	 * fetch(data -> data, null)
	 * fetch(Data::getList, null)
	 * </pre>
	 *
	 * @param function 计算函数
	 * @param defaultValue data 为 null 时返回的默认值
	 */
	@Override
	public <R> R fetch(
			Function<? super T, ? extends R> function,
			R defaultValue
	) {
		if (function == null) {
			return defaultValue;
		}

		synchronized (dataLock) {
			return data == null
					? defaultValue
					: function.apply(data);
		}
	}
}

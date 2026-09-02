package dev.anye.core.json;

import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class _JsonConfig<T> extends _JsonSupport {
	protected final boolean checkData;
	protected final String filePath;
	private final T defaultRawData;
	protected final Type type;
	//Expose visibility to avoid the need for subclasses to customize.
	protected volatile T data;

	private final Object lock = new Object();
	public _JsonConfig(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		this.filePath = filePath;
		this.type = typeToken.getType();
		this.defaultRawData = GSON.fromJson(GSON.toJson(defaultRawData), type);
		this.checkData = checkData;
		this.data = null;
		init();
	}

	public _JsonConfig(String filePath, T defaultData, TypeToken<T> typeToken) {
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
	public _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken, boolean checkData) {

		this(filePath, (T) GSON.fromJson(defaultData, typeToken.getType()), typeToken, checkData);
	}

	/**
	 * @param filePath    filePath
	 * @param defaultData defaultData
	 * @param typeToken   typeToken
	 * @deprecated Using this method is no longer recommended, as it may entail numerous issues;
	 */
	@Deprecated(since = "2.0.5")
	public _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}


	/**
	 * Initial loading
	 * <li>When {@link _JsonConfig#checkData} is enabled, parts that do not conform to the default data format will be replaced.
	 */
	public void init() {
		File file = new File(filePath);
		if (!file.exists()) {
			reset();
		} else {
			if (defaultRawData != null && checkData) checkData(GSON.toJson(defaultRawData, type), filePath);
		}
		load();
	}

	public void reload() {
		load();
	}

	/**
	 * Overwrite the original content with default data.
	 * Directory existence is not checked; please ensure the provided path already exists.
	 */
	public void reset() {
		if (defaultRawData == null) return;
		try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
			writer.write(GSON.toJson(defaultRawData, this.type));
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	/**
	 * Loads file data and throws an exception if the data is null; please verify that the structure matches the expected type.
	 * Before loading completes, the data remains the old data (or {@link Optional#empty} if it is the initial load).
	 */
	private void load() {
		try (Reader reader = _File.loadFileWithUtf8(filePath)) {
			data = GSON.fromJson(reader, this.type);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Save new data to file and update memory.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 *
	 * @param data new data
	 */
	public void save(T data) {
		if (data == null) return;
		setSaveFile(data);
		this.data = data;
	}

	/**
	 * Save existing data to a file and load it.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 */
	public void save() {
		save(this.data);
	}

	public Optional<T> data() {
		return Optional.ofNullable(this.data);
	}
	public T getDataOrDefault(T other) {
		if (this.data == null) return other;
		return this.data;
	}

	public T getDataOrDefault() {
		return getDataOrDefault(defaultRawData);
	}

	/**
	 * @return (nullable) T
	 */
	public T getData() {
		return data;
	}
	/**
	 * Modify the existing data only, without saving it.
	 * Must not be null.
	 *
	 * @param data new data
	 */
	public void setData(T data) {
		if (data == null) return;
		this.data = data;
	}

	/**
	 * Save without altering the existing data.
	 */
	public void setSaveFile(T data) {
		try (OutputStreamWriter writer = _File.startWriterWithUtf8(filePath)) {
			GSON.toJson(data, this.type, writer);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}


	public boolean isPresent() {
		return data != null;
	}

	public void ifPresent(Consumer<? super T> action) {
		if (data != null) {
			action.accept(data);
		}
	}

	@Deprecated(since = "2.0.6")
	public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
		return Optional.ofNullable(mapper.apply(data));
	}

	@Deprecated(since = "2.0.6")
	public Optional<T> or(Supplier<Optional<? extends T>> supplier) {
		if (isPresent()) return Optional.of(data);
		return (Optional<T>) supplier.get();
	}

	@Deprecated(since = "2.0.6")
	public T orElse(T other) {
		return getDataOrDefault(other);
	}

	@Deprecated(since = "2.0.6")
	public Optional<T> filter(Predicate<? super T> predicate) {
		if (this.data == null) return Optional.empty();
		return predicate.test(data) ? Optional.of(data) : Optional.empty();
	}

	@Deprecated(since = "2.0.6")
	public <U> Optional<U> flatMap(Function<? super T, Optional<? extends U>> mapper) {
		if (this.data == null) return Optional.empty();
		return (Optional<U>) mapper.apply(data);
	}

	@Deprecated(since = "2.0.6")
	public boolean isEmpty() {
		return data == null;
	}

	@Deprecated(since = "2.0.6")
	public T orElseGet(Supplier<? extends T> supplier) {
		return data != null ? data : supplier.get();
	}

	@Deprecated(since = "2.0.6")
	public T orElseThrow() {
		if (data == null) {
			throw new NoSuchElementException("No value present");
		}
		return data;
	}

	@Deprecated(since = "2.0.6")
	public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
		if (data != null) {
			return data;
		} else {
			throw exceptionSupplier.get();
		}
	}

	@Deprecated(since = "2.0.6")
	public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
		if (data != null) {
			action.accept(data);
		} else {
			emptyAction.run();
		}
	}


}

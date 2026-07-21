package dev.anye.core.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Optional;

public class _JsonConfig<T> extends _JsonSupport {
	private final boolean checkData;
	protected final String filePath;
	protected final String defaultData;
	protected final Type type;
	protected Optional<T> data = Optional.empty();

	public _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}

	public _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken, boolean checkData) {
		this.filePath = filePath;
		this.defaultData = defaultData;
		this.type = typeToken.getType();
		this.checkData = checkData;
		init();
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
			if (checkData) CheckData(defaultData, filePath);
		}
		load();
	}

	/**
	 * Overwrite the original content with default data.
	 */
	public void reset() {
		try (FileWriter writer = new FileWriter(filePath)) {
			writer.write(defaultData);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Loading the file content temporarily sets the `data` variable to `null`; accessing the `data` variable during the loading process will result in an error. It is recommended to minimize the use of methods that rely on this method.
	 */
	private void load() {
		data = Optional.empty();
		Gson gson = new Gson();
		try (Reader reader = new FileReader(filePath)) {
			data = Optional.of(gson.fromJson(reader, this.type));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Save new data to a file and load it.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 * @param data new data
	 */
	public void save(T data) {
		if (data == null) return;
		setSaveFile(data);
		init();
	}
	public void save(Optional<T> data){
		save(data.orElse(null));
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

	public Optional<T> data(){
		return this.data;
	}


	public T getData() {
		return data.orElse(null);
	}


	/**
	 * Modify the existing data only, without saving it.
	 * @param data new data
	 */
	public void setData(T data){
		this.data = Optional.of(data);
	}
	/**
	 * Save without altering the existing data.
	 */
	public void setSaveFile(T data){
		Gson gson = new Gson();
		try (Writer writer = new FileWriter(filePath)) {
			gson.toJson(data, writer);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}
}

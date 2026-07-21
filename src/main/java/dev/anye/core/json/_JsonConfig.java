package dev.anye.core.json;

import com.google.gson.reflect.TypeToken;
import dev.anye.core.exception._IOException;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class _JsonConfig<T> extends _JsonSupport {
	protected final boolean checkData;
	protected final String filePath;
	private final T defaultRawData;
	protected final Type type;
	//Expose visibility to avoid the need for subclasses to customize.
	protected Optional<T> data;

	public _JsonConfig(String filePath,T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		this.filePath = filePath;
		this.type = typeToken.getType();
		this.defaultRawData = GSON.fromJson(GSON.toJson(defaultRawData),type);
		this.checkData = checkData;
		this.data = Optional.empty();
		init();
	}
	public _JsonConfig(String filePath, T defaultData, TypeToken<T> typeToken){
		this(filePath,defaultData,typeToken,true);
	}
	/**
	 * @deprecated 
	 * Using this method is no longer recommended, as it may entail numerous issues;
	 * @param filePath filePath
	 * @param defaultData defaultData
	 * @param typeToken typeToken
	 */
	@Deprecated(since = "2.0.5")
	@SuppressWarnings("unchecked")
	public _JsonConfig(String filePath, String defaultData, TypeToken<T> typeToken, boolean checkData) {
		
		this(filePath, (T) GSON.fromJson(defaultData,typeToken.getType()),typeToken,checkData);
	}

	/**
	 * @deprecated 
	 * Using this method is no longer recommended, as it may entail numerous issues;
	 * @param filePath filePath
	 * @param defaultData defaultData
	 * @param typeToken typeToken
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
			if (checkData) checkData(GSON.toJson(defaultRawData,type), filePath);
		}
		load();
	}

	public void reload(){
		load();
	}

	/**
	 * Overwrite the original content with default data.
	 * Directory existence is not checked; please ensure the provided path already exists.
	 */
	public void reset() {
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
			writer.write(GSON.toJson(defaultRawData,this.type));
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	/**
	 * Loads file data and throws an exception if the data is null; please verify that the structure matches the expected type.
	 * Before loading completes, the data remains the old data (or {@link Optional#empty} if it is the initial load).
	 */
	private void load() {
		try (Reader reader = new InputStreamReader(new FileInputStream(filePath),StandardCharsets.UTF_8)) {
			data = Optional.of(GSON.fromJson(reader, this.type));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Save new data to file and update memory.
	 * <ul>
	 *     Frequent use of this method is not recommended, as it can lead to unnecessary runtime errors.
	 * </ul>
	 * @param data new data
	 */
	public void save(T data) {
		if (data == null) return;
		setSaveFile(data);
		this.data = Optional.of(data);
	}
	public void save(Optional<T> data){
		if (data == null) return;
		save(data.orElse(defaultRawData));
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

	/**
	 * @deprecated
	 * Retrieve data, returning the default raw data if it is unavailable.
	 * It is not recommended to use this method anymore.
	 * @return T
	 */
	@Deprecated(since = "2.0.5")
	public T getData() {
		return data.orElse(defaultRawData);
	}


	/**
	 * Modify the existing data only, without saving it.
	 * Must not be null.
	 * @param data new data
	 */
	public void setData(T data){
		if (data == null) return;
		this.data = Optional.of(data);
	}
	/**
	 * Save without altering the existing data.
	 */
	public void setSaveFile(T data){
		try (OutputStreamWriter writer =  new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
			GSON.toJson(data,this.type, writer);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}
}

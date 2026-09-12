package dev.anye.core.json;

import com.google.gson.reflect.TypeToken;

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
public abstract class _JsonConfigR<T extends Record> extends _JsonConfigX<T> {
	protected _JsonConfigR(String filePath, T defaultRawData, TypeToken<T> typeToken, boolean checkData) {
		super(filePath,defaultRawData, typeToken,checkData);
	}

	protected _JsonConfigR(String filePath, T defaultData, TypeToken<T> typeToken) {
		this(filePath, defaultData, typeToken, true);
	}
}
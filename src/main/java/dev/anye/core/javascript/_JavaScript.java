package dev.anye.core.javascript;

import dev.anye.core.bytes._Byte;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class _JavaScript<T extends _JavaScript<T, S>, S> {
	public static final String FileEncoding = StandardCharsets.UTF_8.name();

	private final boolean cache;
	private final HashMap<String, S> temp = new HashMap<>();

	protected _JavaScript(boolean cache) {
		this.cache = cache;
	}

	public abstract T addParameter(String name, Object value);

	public abstract T setParameter(Map<String, Object> map);

	public abstract S getJsData(Reader reader);

	public abstract S getJsData(String code);

	public abstract Object runCode(S code);


	public void clearCache() {
		temp.clear();
	}


	@Deprecated(since = "2.0.5")
	public Object runCode(String code) {
		return runCode(_Byte.getMd5(code), code);
	}

	public Object runCode(String key, String code) {
		if (cache) {
			return this.runCode(temp.computeIfAbsent(key, s -> getJsData(code)));
		} else {
			return this.runCode(getJsData(code));
		}
	}

	@Deprecated(since = "2.0.5")
	public Object runFile(String file) {
		return runFile(file, file);
	}

	public Object runFile(String key, String file) {
		if (cache) {
			if (!temp.containsKey(key)) {
				try {
					temp.put(key, getJsData(_File.loadFileWithUtf8(file)));
				} catch (FileNotFoundException e) {
					throw new _IOException(file);
				}
			}
			return this.runCode(temp.get(file));
		} else {
			try {
				return this.runCode(getJsData(_File.loadFileWithUtf8(file)));
			} catch (FileNotFoundException e) {
				throw new _IOException(e);
			}
		}
	}
}

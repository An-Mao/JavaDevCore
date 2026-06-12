package dev.anye.core.javascript;

import dev.anye.core.bytes._Byte;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class _JavaScript<T extends _JavaScript<T, S>, S> {
	public static final String FileEncoding = StandardCharsets.UTF_8.name();

	private final boolean cache;
	private final HashMap<String, S> temp = new HashMap<>();

	public _JavaScript(boolean cache) {
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


	@Deprecated
	public Object runCode(String code) {
		return runCode(_Byte.getMd5(code), code);
	}

	public Object runCode(String key, String code) {
		if (cache) {
			if (!temp.containsKey(key)) {
				temp.put(key, getJsData(code));
			}
			return this.runCode(temp.get(key));
		} else {
			return this.runCode(getJsData(code));
		}
	}

	@Deprecated
	public Object runFile(String file) {
		return runFile(file, file);
	}

	public Object runFile(String key, String file) {
		if (cache) {
			if (!temp.containsKey(key)) {
				try {
					temp.put(key, getJsData(new FileReader(file)));
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
			return this.runCode(temp.get(file));
		} else {
			try {
				return this.runCode(getJsData(new FileReader(file)));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}
}

package dev.anye.core.pack;

import dev.anye.core.cdt._SuffixCDT;
import dev.anye.core.exception._IOException;
import dev.anye.core.system._File;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Function;

public final class _Pack {
	private _Pack(){}
	public static void writeFiles(String packPath, String putPath, String suffix, String... names) {
		writeFiles(packPath, putPath, suffix, false, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, boolean replace, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, replace, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, names);
	}
	public static void writeFiles(String packPath, String putPath, String suffix, boolean replace, String... names) {
		ClassLoader classLoader = _Class.getClassLoader();
		for (String name : names) {
			if (replace && new File(_File.getFilePath(putPath, name + suffix)).exists()) continue;
			try (InputStream inputStream = classLoader.getResourceAsStream(_File.getFilePath(packPath, name + suffix))) {
				if (inputStream == null)
					throw new FileNotFoundException("Resource not found: " + _File.getFilePath(packPath, name + suffix));
				Path outputPath = Paths.get(_File.getFilePath(putPath, name + suffix));
				Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new _IOException(e);
			}
		}
	}

	public static byte[] readFileByte(String filePath) {
		ClassLoader classLoader = _Class.getClassLoader();
		try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
			if (inputStream == null)
				throw new FileNotFoundException("Resource not found: " + filePath);
			return inputStream.readAllBytes();
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	/**
	 * 读取包内文件并进行自定义处理，此方法含有返回
	 * @param filePath 文件类型
	 * @param function 自定义行为
	 * @return R
	 * @param <R> 自定义返回类型
	 */
	public static <R> R readFile(String filePath,Function<? super InputStream, ? extends R> function){
		ClassLoader classLoader = _Class.getClassLoader();
		try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
			if (inputStream == null)
				throw new FileNotFoundException("Resource not found: " + filePath);
			return function.apply(inputStream);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}

	/**
	 * 读取包内文件并进行自定义处理，此方法不含返回
	 * @param filePath 文件类型
	 * @param action 自定义行为
	 */
	public static void readFileX(String filePath, Consumer<? super InputStream> action){
		ClassLoader classLoader = _Class.getClassLoader();
		try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
			if (inputStream == null)
				throw new FileNotFoundException("Resource not found: " + filePath);
			action.accept(inputStream);
		} catch (IOException e) {
			throw new _IOException(e);
		}
	}
}

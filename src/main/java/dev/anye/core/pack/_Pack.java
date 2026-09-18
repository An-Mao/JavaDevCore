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
	/**
	 * 将包内文件写出到系统，跳过已存在的文件
	 * @param packPath 包内路径
	 * @param putPath 输出路径
	 * @param suffix 文件后缀
	 * @param names 要输出的文件名
	 */
	public static void writeFiles(String packPath, String putPath, String suffix, String... names) {
		writeFiles(packPath, putPath, suffix, true, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, boolean skip, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, skip, names);
	}

	public static void writeJsonFiles(String packPath, String putPath, String... names) {
		writeFiles(packPath, putPath, _SuffixCDT.JSON_SUFFIX, names);
	}

	/**
	 * 将包内文件写出到系统
	 * @param packPath 包内路径
	 * @param putPath 输出路径
	 * @param suffix 文件后缀
	 * @param skip 文件存在时是否跳过
	 * @param names 要输出的文件名
	 */
	public static void writeFiles(String packPath, String putPath, String suffix, boolean skip, String... names) {
		ClassLoader classLoader = _Class.getClassLoader();
		for (String name : names) {
			String output = _File.getFilePath(putPath, name + suffix);
			if (new File(output).exists() && skip) continue;

			String input = _File.getFilePath(packPath, name + suffix);
			try (InputStream inputStream = classLoader.getResourceAsStream(input)) {
				if (inputStream == null) throw new FileNotFoundException("Resource not found: " + input);

				Path outputPath = Paths.get(output);
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
	 * @param filePath 文件路径
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
	 * @param filePath 文件路径
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

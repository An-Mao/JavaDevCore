package dev.anye.core.bytes;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public class _Byte {
	public static UUID getUUID(String md5Hash) {
		byte[] bytes = hexStringToByteArray(getMd5(md5Hash));
		return UUID.nameUUIDFromBytes(bytes);
	}

	public static byte[] hexStringToByteArray(String hexString) {
		int len = hexString.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
					+ Character.digit(hexString.charAt(i + 1), 16));
		}
		return data;
	}

	public static String getMd5(String input) {
		return getHash(input, "MD5");
	}

	public static String getSha1(String input) {
		return getHash(input, "SHA-1");
	}

	public static String getHash(String input, String type) {
		try {
			MessageDigest digest = MessageDigest.getInstance(type);
			byte[] hash = digest.digest(input.getBytes());
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Serialize an object to a byte array
	 * 将对象序列化成byte数组
	 **/
	public static byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
			objectOutputStream.writeObject(obj);
		}
		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * Deserialize a byte array to an object
	 * 将byte数组反序列化成对象
	 **/
	public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
		try (ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
			return objectInputStream.readObject();
		}
	}

	/**
	 * Deflater
	 * Compress a byte array
	 * 压缩byte数组
	 **/
	public static byte[] compress(byte[] data) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream)) {
			deflaterOutputStream.write(data);
		}
		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * Inflater
	 * Decompress a byte array
	 * 解压byte数组
	 **/
	public static byte[] decompress(byte[] compressedData) throws IOException {
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
		try (InflaterInputStream inflaterInputStream = new InflaterInputStream(byteArrayInputStream);
		     ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = inflaterInputStream.read(buffer)) > 0) {
				byteArrayOutputStream.write(buffer, 0, len);
			}
			return byteArrayOutputStream.toByteArray();
		}
	}

	/**
	 * GZIP
	 * Compress a byte array
	 * 压缩byte数组
	 **/
	public static byte[] gzCompress(byte[] data) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
			gzipOutputStream.write(data);
		}
		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * GZIP
	 * Decompress a byte array
	 * 解压byte数组
	 **/
	public static byte[] gzDecompress(byte[] compressedData) throws IOException {
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
		try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
		     ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = gzipInputStream.read(buffer)) > 0) {
				byteArrayOutputStream.write(buffer, 0, len);
			}
			return byteArrayOutputStream.toByteArray();
		}
	}

}

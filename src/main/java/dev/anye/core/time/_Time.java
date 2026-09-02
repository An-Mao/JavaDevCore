package dev.anye.core.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class _Time {
	private _Time(){}
	public static long getSysSec() {
		return System.currentTimeMillis() / 1000L;
	}

	public static String getDay() {
		return getDay("yyyyMMdd");
	}

	public static String getDay(String format) {
		return LocalDateTime
				.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()),
						ZoneId.systemDefault())
				.format(DateTimeFormatter.ofPattern(format));
	}


}

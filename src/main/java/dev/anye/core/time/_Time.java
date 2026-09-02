package dev.anye.core.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public final class _Time {
	private static final long TIMEZONE_OFFSET_MS = OffsetDateTime.now(ZoneId.systemDefault()).getOffset().getTotalSeconds() * 1000L;

	private static final TimeZone SYSTEM_TIMEZONE = TimeZone.getDefault();

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

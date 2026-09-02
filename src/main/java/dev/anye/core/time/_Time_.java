package dev.anye.core.time;
/*
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class _Time_ {

	private _Time_() {}

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

	public static final class FastTimeExtractor {

		private static final long DAY_MILLIS = 86_400_000L;
		private static final long DAY_SECONDS = 86_400L;

		private static final long DEFAULT_OFFSET_MS = 8 * 3600_000L;

		private FastTimeExtractor() {}

		public static long getPackedTime(long epochMillis) {
			return getPackedTime(epochMillis, DEFAULT_OFFSET_MS);
		}

		public static long getPackedTime(long epochMillis, long offsetMillis) {

			long localMillis = epochMillis + offsetMillis;

			long epochDay = Math.floorDiv(localMillis, DAY_MILLIS);
			long millisOfDay = Math.floorMod(localMillis, DAY_MILLIS);

			int hour = (int) (millisOfDay / 3_600_000L);
			millisOfDay %= 3_600_000L;

			int minute = (int) (millisOfDay / 60_000L);
			millisOfDay %= 60_000L;

			int second = (int) (millisOfDay / 1000L);

			// Gregorian calendar
			long z = epochDay + 719468;

			long era = Math.floorDiv(z, 146097);
			long doe = z - era * 146097;

			long yoe = (doe
					- doe / 1460
					+ doe / 36524
					- doe / 146096) / 365;

			long year = yoe + era * 400;

			long doy = doe
					- (365 * yoe
					+ yoe / 4
					- yoe / 100);

			long mp = (5 * doy + 2) / 153;

			int day = (int) (doy - (153 * mp + 2) / 5 + 1);
			int month = (int) (mp + (mp < 10 ? 3 : -9));

			year += month <= 2 ? 1 : 0;

			return pack(
					(int) year,
					month,
					day,
					hour,
					minute,
					second
			);
		}


		private static long pack(
				int year,
				int month,
				int day,
				int hour,
				int minute,
				int second
		) {
			return ((long) year << 26)
					| ((long) month << 22)
					| ((long) day << 17)
					| ((long) hour << 12)
					| ((long) minute << 6)
					| second;
		}


		public static int getYear(long packed) {
			return (int) ((packed >>> 26) & 0x3FFF);
		}

		public static int getMonth(long packed) {
			return (int) ((packed >>> 22) & 0x0F);
		}

		public static int getDay(long packed) {
			return (int) ((packed >>> 17) & 0x1F);
		}

		public static int getHour(long packed) {
			return (int) ((packed >>> 12) & 0x1F);
		}

		public static int getMinute(long packed) {
			return (int) ((packed >>> 6) & 0x3F);
		}

		public static int getSecond(long packed) {
			return (int) (packed & 0x3F);
		}


		public static String getFormatDay(String format) {
			long t = getPackedTime(System.currentTimeMillis());

			return format
					.replace("Y", String.valueOf(getYear(t)))
					.replace("M", String.valueOf(getMonth(t)))
					.replace("D", String.valueOf(getDay(t)));
		}
	}
}

 */
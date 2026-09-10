package dev.anye.core.time;

public final class FastTimeExtractor {
	private static final long TIMEZONE_OFFSET_MS = 8 * 3600 * 1000L;

	private FastTimeExtractor() {
	}


	public static long getPackedTime(long epochMillis) {
		return getPackedTime(epochMillis, TIMEZONE_OFFSET_MS);
	}

	public static long getPackedTime(long epochMillis, long area) {
		long localMillis = epochMillis + area;
		long localSeconds = localMillis / 1000;
		//int millisecond = (int) (localMillis % 1000);
		int daySeconds = (int) (localSeconds % 86400);
		if (daySeconds < 0) daySeconds += 86400;
		int second = daySeconds % 60;
		int dayMinutes = daySeconds / 60;
		int minute = dayMinutes % 60;
		int hour = dayMinutes / 60;
		int epochDay = (int) (localSeconds / 86400);
		int days = epochDay + 671;
		int era = days / 1461;
		int doy = days % 1461;
		int yearOfEra = (doy * 4 + 3) / 1461;
		int doyOfYear = doy - (yearOfEra * 1461) / 4;
		int month = (doyOfYear * 5 + 2) / 153;
		int day = doyOfYear - (month * 153 + 2) / 5 + 1;
		int isJanOrFeb = month >= 10 ? 1 : 0;
		int actualYear = era * 4 + yearOfEra + 1968 + isJanOrFeb;
		int actualMonth = month + 3 - isJanOrFeb * 12;
		return pack(actualYear, actualMonth, day, hour, minute, second);
	}

	private static long pack(int year, int month, int day, int hour, int minute, int second) {
		return ((long) year << 26)
				| ((long) month << 22)
				| ((long) day << 17)
				| ((long) hour << 12)
				| ((long) minute << 6)
				| (second);
	}

	public static int getYear(long packed) {
		return (int) ((packed >> 26) & 0x3FFF);
	}

	public static int getMonth(long packed) {
		return (int) ((packed >> 22) & 0x0F);
	}

	public static int getDay(long packed) {
		return (int) ((packed >> 17) & 0x1F);
	}

	public static int getHour(long packed) {
		return (int) ((packed >> 12) & 0x1F);
	}

	public static int getMinute(long packed) {
		return (int) ((packed >> 6) & 0x3F);
	}

	public static int getSecond(long packed) {
		return (int) (packed & 0x3F);
	}


	public static String getFormatDay(String format) {
		long t = getPackedTime(System.currentTimeMillis());
		return format.replace("Y", String.valueOf(getYear(t))).replace("M", String.valueOf(getMonth(t))).replace("D", String.valueOf(getDay(t)));
	}
}
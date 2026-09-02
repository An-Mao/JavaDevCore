package dev.anye.core.time;

import java.util.TimeZone;

public final class FastDateTime {
	private static final long DAY_MILLIS = 86_400_000L;
	//private static final long DAY_SECONDS = 86_400L;
	private static final long DEFAULT_OFFSET_MS = 8 * 3600_000L;
	//private static final ConcurrentHashMap<String, FastPattern> FORMAT_CACHE = new ConcurrentHashMap<>();

	public long epochMillis;
	public final long offsetMillis;

	public int hour;
	public int minute;
	public int second;
	public long year;
	public int month;
	public int day;
	public int millisecond;

	public FastDateTime(){
		this(System.currentTimeMillis());
	}
	public FastDateTime(long epochMillis){
		this(epochMillis,DEFAULT_OFFSET_MS);
	}
	public FastDateTime(long epochMillis, long offsetMillis) {
		this.offsetMillis = offsetMillis;
		setEpochMillis(epochMillis);
	}
	public FastDateTime(TimeZone timeZone) {
		this(System.currentTimeMillis(),timeZone);
	}
	public FastDateTime(long epochMillis, TimeZone timeZone) {
		this.offsetMillis = timeZone.getOffset(epochMillis);
		setEpochMillis(epochMillis);
	}

	public void update(){
		setEpochMillis(System.currentTimeMillis());
	}
	public void setEpochMillis(long epochMillis){
		this.epochMillis = epochMillis;
		refresh();
	}


	public void refresh(){
		long localMillis = epochMillis + offsetMillis;

		long epochDay = Math.floorDiv(localMillis, DAY_MILLIS);
		long millisOfDay = Math.floorMod(localMillis, DAY_MILLIS);
		hour = (int) (millisOfDay / 3_600_000L);
		millisOfDay %= 3_600_000L;

		minute = (int) (millisOfDay / 60_000L);
		millisOfDay %= 60_000L;

		second = (int) (millisOfDay / 1000L);
		millisecond = (int) (millisOfDay % 1_000L);

		long z = epochDay + 719468;
		long era = Math.floorDiv(z, 146097);
		long doe = z - era * 146097;
		long yoe = (doe
				- doe / 1460
				+ doe / 36524
				- doe / 146096) / 365;

		year = yoe + era * 400;

		long doy = doe
				- (365 * yoe
				+ yoe / 4
				- yoe / 100);

		long mp = (5 * doy + 2) / 153;

		day = (int) (doy - (153 * mp + 2) / 5 + 1);
		month = (int) (mp + (mp < 10 ? 3 : -9));

		//year += month <= 2 ? 1 : 0;
		if (month <= 2) year++;

	}

	public long pack() {
		return (year << 26)
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


/*

	public String format(String format) {
		return format
				.replace("Y", String.valueOf(year))
				.replace("M", String.valueOf(month))
				.replace("D", String.valueOf(day));
	}
*/

	public String toDateString(String s) {
		/*
		_String.appendNumber(out, (int) year, 4);
		out.append('-');
		_String.appendNumber(out, month, 2);
		out.append('-');
		_String.appendNumber(out, day, 2);
		*/
		return year + s + month + s + day;
	}

	public String toTimeString(String s) {
		/*StringBuilder out = new StringBuilder(12);

		_String.appendNumber(out, hour, 2);
		out.append(':');
		_String.appendNumber(out, minute, 2);
		out.append(':');
		_String.appendNumber(out, second, 2);

		return out.toString();
		*/
		return year + s + month + s + day;
	}

	public String toDateTimeString(String s) {
		/*StringBuilder out = new StringBuilder(23);

		_String.appendNumber(out, (int) year, 4);
		out.append('-');
		_String.appendNumber(out, month, 2);
		out.append('-');
		_String.appendNumber(out, day, 2);
		out.append(' ');
		_String.appendNumber(out, hour, 2);
		out.append(':');
		_String.appendNumber(out, minute, 2);
		out.append(':');
		_String.appendNumber(out, second, 2);

		return out.toString();*/

		return year + s + month + s + day + s + hour + s + minute + s + second;
	}



	/*
	public String format(String pattern) {
		return FORMAT_CACHE
				.computeIfAbsent(pattern, FastPattern::parse)
				.format(this);
	}

	private interface FormatPart {
		void append(StringBuilder out, FastDateTime time);
	}
	private static final class TextPart implements FormatPart {

		private final String text;

		private TextPart(String text) {
			this.text = text;
		}

		@Override
		public void append(StringBuilder out, FastDateTime time) {
			out.append(text);
		}
	}
	private static final class FieldPart implements FormatPart {

		private final char type;
		private final int width;

		private FieldPart(char type, int width) {
			this.type = type;
			this.width = width;
		}

		@Override
		public void append(StringBuilder out, FastDateTime time) {
			int value = switch (type) {
				case 'y' -> (int) time.year;
				case 'M' -> time.month;
				case 'd' -> time.day;
				case 'H' -> time.hour;
				case 'm' -> time.minute;
				case 's' -> time.second;
				case 'S' -> time.millisecond;
				default -> throw new IllegalStateException();
			};

			_String.appendNumber(out, value, width);
		}
	}
	private static final class FastPattern {

		private final FormatPart[] parts;

		private FastPattern(FormatPart[] parts) {
			this.parts = parts;
		}

		static FastPattern parse(String pattern) {

			List<FormatPart> parts = new ArrayList<>();

			for (int i = 0; i < pattern.length();) {

				char c = pattern.charAt(i);

				// 字面量
				if (c == '\'') {
					int start = ++i;

					while (i < pattern.length()
							&& pattern.charAt(i) != '\'') {
						i++;
					}

					parts.add(new TextPart(
							pattern.substring(start, i)
					));

					if (i < pattern.length()) {
						i++;
					}

					continue;
				}

				// 时间字段
				if (isField(c)) {

					int start = i++;

					while (i < pattern.length()
							&& pattern.charAt(i) == c) {
						i++;
					}

					parts.add(
							new FieldPart(c, i - start)
					);

					continue;
				}

				// 普通字符
				int start = i++;

				while (i < pattern.length()) {

					char next = pattern.charAt(i);

					if (next == '\''
							|| isField(next)) {
						break;
					}

					i++;
				}

				parts.add(
						new TextPart(pattern.substring(start, i))
				);
			}

			return new FastPattern(
					parts.toArray(new FormatPart[0])
			);
		}

		private static boolean isField(char c) {
			return switch (c) {
				case 'y', 'M', 'd',
					 'H', 'm', 's', 'S' -> true;
				default -> false;
			};
		}

		String format(FastDateTime time) {

			StringBuilder out = new StringBuilder(32);

			for (FormatPart part : parts) {
				part.append(out, time);
			}

			return out.toString();
		}
	}

	 */
}
package dev.anye.core.system;

public class _String {
	public static void appendNumber(
			StringBuilder out,
			int value,
			int width
	) {
		if (width == 1) {
			out.append(value);
			return;
		}

		if (width == 2) {
			if (value < 10) {
				out.append('0');
			}
			out.append(value);
			return;
		}

		if (width == 3) {
			if (value < 10) {
				out.append("00");
			} else if (value < 100) {
				out.append('0');
			}
			out.append(value);
			return;
		}

		if (width == 4) {
			if (value < 10) {
				out.append("000");
			} else if (value < 100) {
				out.append("00");
			} else if (value < 1000) {
				out.append('0');
			}
			out.append(value);
			return;
		}

		out.append(value);
	}
}

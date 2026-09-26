package dev.anye.core.math;

public final class _Trig {

	private _Trig() {}

	/**
	 * LUT 大小 = 16384。
	 * 一个 LUT 单元约：
	 * 2PI / 16384 = 0.02197265625°
	 */
	public static final int TABLE_BITS = 14;
	public static final int TABLE_SIZE = 1 << TABLE_BITS;
	public static final int TABLE_MASK = TABLE_SIZE - 1;
	public static final int QUARTER = TABLE_SIZE >> 2;
	public static final int HALF = TABLE_SIZE >> 1;
	public static final int THREE_QUARTER = QUARTER * 3;

	public static final float INV_TWO_PI = (float) (1.0f / _MathCDT.TWICE_PI);
	public static final float TABLE_SCALE = TABLE_SIZE * INV_TWO_PI;

	/**
	 * double 版本常量。
	 * 可以减少连续增量计算产生的误差。
	 */
	public static final double TABLE_SCALE_D = TABLE_SIZE / (Math.PI * 2.0);

	/**
	 * 多一个元素用于 TABLE_SIZE + 1。
	 * 当 index == TABLE_SIZE - 1 时可以直接访问 index + 1。
	 */
	static final float[] SIN_TABLE = new float[TABLE_SIZE + 1];

	static {
		final double step = Math.PI * 2.0 / TABLE_SIZE;

		for (int i = 0; i < TABLE_SIZE; i++) {
			SIN_TABLE[i] = (float) Math.sin(i * step);
		}

		SIN_TABLE[TABLE_SIZE] = SIN_TABLE[0];

		// 强制保证四个基准点准确。
		SIN_TABLE[0] = 0.0f;
		SIN_TABLE[QUARTER] = 1.0f;
		SIN_TABLE[HALF] = 0.0f;
		SIN_TABLE[THREE_QUARTER] = -1.0f;
		SIN_TABLE[TABLE_SIZE] = 0.0f;
	}

	/**
	 * 标准 sin。
	 * 支持任意角度，包括负角度和超过 2PI 的角度。
	 */
	public static float sin(double radians) {
		return sinAt(radians * TABLE_SCALE_D);
	}

	/**
	 * 标准 cos。
	 * cos(x) = sin(x + PI / 2)。
	 */
	public static float cos(double radians) {
		return cosAt(radians * TABLE_SCALE_D);
	}

	/**
	 * 根据 LUT position 获取 sin。
	 * position 可以超过一个周期，也可以为负数。
	 * 这里使用 floor 语义处理负数，保证 fraction 始终位于 [0, 1)。
	 */
	public static float sinAt(double position) {
		int index = (int) position;
		if (position < index) {
			index--;
		}

		float fraction = (float) (position - index);
		index &= TABLE_MASK;

		float a = SIN_TABLE[index];
		return a + (SIN_TABLE[index + 1] - a) * fraction;
	}

	/**
	 * 根据 LUT position 获取 cos。
	 */
	public static float cosAt(double position) {
		int index = (int) position;
		if (position < index) {
			index--;
		}

		float fraction = (float) (position - index);
		index &= TABLE_MASK;

		int cosIndex = (index + QUARTER) & TABLE_MASK;
		float a = SIN_TABLE[cosIndex];
		return a + (SIN_TABLE[cosIndex + 1] - a) * fraction;
	}
}

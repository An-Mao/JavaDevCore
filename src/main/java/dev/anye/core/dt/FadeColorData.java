package dev.anye.core.dt;

import dev.anye.core.color._ColorSupport;

/**
 * ↑ ↓ ← → ↖ ↗ ↘ ↙
 * 假设默认颜色是左向右( → )
 * @param leftTopColor
 * @param leftBottomColor
 * @param rightBottomColor
 * @param rightTopColor
 */
public record FadeColorData(
		int leftTopColor,
		int leftBottomColor,

		int rightBottomColor,
		int rightTopColor
){
	public static final FadeColorData EMPTY = new FadeColorData(0x00000000,0x00000000,0xffffffff, 0xffffffff);
	/**
	 * ( ← )
	 * 将颜色左右镜像
	 * <pre>
	 *     1 - 4     4 - 1
	 *     |   | --> |   |
	 *     2 - 3     3 - 2
	 * </pre>
	 * @return FadeColorData
	 */
	public FadeColorData right(){
		return new FadeColorData(rightTopColor,rightBottomColor,
				leftBottomColor,leftTopColor);
	}

	/**
	 * ( ↓ )
	 * 将颜色镜像
	 * <pre>
	 *     1 - 4     1 - 2
	 *     |   | --> |   |
	 *     2 - 3     4 - 3
	 * </pre>
	 * @return FadeColorData
	 */
	public FadeColorData down(){
		return new FadeColorData(leftTopColor,rightTopColor,
				rightBottomColor,leftBottomColor);
	}

	/**
	 * ( ↑ )
	 * 将颜色镜像
	 * <pre>
	 *     1 - 4     4 - 3
	 *     |   | --> |   |
	 *     2 - 3     1 - 2
	 * </pre>
	 * @return FadeColorData
	 */
	public FadeColorData up(){
		return new FadeColorData(rightTopColor,leftTopColor,
				leftBottomColor,rightBottomColor);
	}
	/**
	 * 将颜色上下镜像
	 * <pre>
	 *     1 - 4     2 - 3
	 *     |   | --> |   |
	 *     2 - 3     1 - 4
	 * </pre>
	 * @return FadeColorData
	 */
	public FadeColorData tb(){
		return new FadeColorData(leftBottomColor,leftTopColor,
				rightTopColor,rightBottomColor);
	}
	/**
	 * 将颜色对角镜像
	 * <pre>
	 *     1 - 4     3 - 2
	 *     |   | --> |   |
	 *     2 - 3     4 - 1
	 * </pre>
	 * @return FadeColorData
	 */
	public FadeColorData dm(){
		return new FadeColorData(rightBottomColor,rightTopColor,
				leftTopColor,leftBottomColor);
	}


	public static FadeColorData withColor(int color, float intensity, boolean alpha) {
		int c = _ColorSupport.fade(color, intensity, alpha);
		int e = c & 0x00FFFFFF;
		return new FadeColorData(c,c,e,e);
	}

	public static FadeColorData withColor(int color, float intensity) {
		return withColor(color, intensity,false);
	}

	public static FadeColorData withColor(int color) {
		return withColor(color, 2F);
	}

	public static FadeColorData create(int color){
		return new FadeColorData(color,color,color,color);
	}


	@Override
	public String toString() {
		return "FadeColorData{" +
				"leftTopColor=" + _ColorSupport.intToHexColor(leftTopColor) +
				", leftBottomColor=" + _ColorSupport.intToHexColor(leftBottomColor) +
				", rightBottomColor=" + _ColorSupport.intToHexColor(rightBottomColor) +
				", rightTopColor=" + _ColorSupport.intToHexColor(rightTopColor) +
				'}';
	}
}

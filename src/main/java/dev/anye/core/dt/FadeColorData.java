package dev.anye.core.dt;

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
	public FadeColorData lr(){
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
}

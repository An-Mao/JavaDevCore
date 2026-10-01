package dev.anye.core.math;

public final class _Arc {
	public static final double ARC = Math.PI / 180;

	/**
	 * (Counter-clockwise)逆时针
	 * <pre>
	 *                   90°
	 *                   ↑
	 *                   |
	 *                   |
	 * 180° ←------------+------------→ 0°
	 *                   |
	 *                   |
	 *                   ↓
	 *                  270°
	 * </pre>
	 */
	private static final double[] ARC_CC = new double[361];
	/**
	 * Negative 逆时针 负值
	 */
	private static final double[] ARC_CC_N = new double[361];

	/**
	 * Clockwise 顺时针
	 * <pre>
	 *                  270°
	 *                   ↑
	 *                   |
	 *                   |
	 * 180° ←------------+------------→ 0°
	 *                   |
	 *                   |
	 *                   ↓
	 *                   90°
	 * </pre>
	 */
	private static final double[] ARC_C = new double[361];
	/**
	 *  顺时针 负值
	 */
	private static final double[] ARC_C_N = new double[361];
	static {
		for (int i = 0;i < 361; i++){
			double a = i * ARC;
			ARC_CC[i] = a;
			ARC_CC_N[i] = -a;

			ARC_C[360 - i] = a;
			ARC_C_N[360 - i] = -a;
		}
	}
	public _Arc(){}

	/**
	 * 获取逆时针数组元素
	 * @return 弧度
	 */
	public static double cc(int i){
		return ARC_CC[i];
	}
	/**
	 * 获取逆时针负数数组元素
	 * @return 弧度
	 */
	public static double ccn(int i){
		return ARC_CC_N[i];
	}

	/**
	 * 获取顺时针数组元素
	 * @return 弧度
	 */
	public static double c(int i){
		return ARC_C[i];
	}
	/**
	 * 获取顺时针负数数组元素
	 * @return 弧度
	 */
	public static double cn(int i){
		return ARC_C_N[i];
	}


	public static double[] getArcCenter(double xc, double yc, double r, double theta1, double theta2) {
		theta1 = Math.toRadians(theta1); // 起始角度（弧度）
		theta2 = Math.toRadians(theta2); // 终止角度（弧度）

		// 计算扇形的中心角度
		// double thetaMid = (theta1 + theta2) / 2;

		// 扇形边缘上的两点坐标
		double x1 = xc + r * Math.cos(theta1);
		double y1 = yc + r * Math.sin(theta1);
		double x2 = xc + r * Math.cos(theta2);
		double y2 = yc + r * Math.sin(theta2);

		// 计算中心点在扇形中心线上的位置的坐标
		double centerX = (x1 + x2) / 2;
		double centerY = (y1 + y2) / 2;

		return new double[]{centerX, centerY};
	}

	public static double getArc(double angleDegrees, double radius) {
		return (angleDegrees / 360.0) * (2 * _MathCDT.PI * radius);
	}

	public static double[] getArcCenter(double radius, double theta, double alpha) {
		double x1 = radius * Math.cos(theta);
		double y1 = radius * Math.sin(theta);
		double x2 = radius * Math.cos(theta + alpha);
		double y2 = radius * Math.sin(theta + alpha);
		double xm = (x1 + x2) / 2;
		double ym = (y1 + y2) / 2;
		double xc = xm + radius * Math.sin(alpha / 2) * Math.cos(theta + alpha / 2);
		double yc = ym + radius * Math.sin(alpha / 2) * Math.sin(theta + alpha / 2);
        /*
        System.out.println("弧的起点坐标：(" + x1 + ", " + y1 + ")");
        System.out.println("弧的中点坐标：(" + xm + ", " + ym + ")");
        System.out.println("弧的终点坐标：(" + x2 + ", " + y2 + ")");
        System.out.println("弧的中心点坐标：(" + xc + ", " + yc + ")");
         */
		return new double[]{xc, yc};
	}

	public static double getTextAngle(double b, double h) {
		double tanTheta = (b / 2) / h;
		double theta = Math.atan(tanTheta);
		double degreesTheta = Math.toDegrees(theta);
		return 180 - 2 * degreesTheta;
	}
}

package dev.anye.core.math;

import dev.anye.core.exception._TargetException;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class _Math extends _MathCDT {
	public static int half1(int i, int j) {
		if (i % 2 > j) return i >> 1;
		else return (i >> 1) + 1;
	}

	public static int half1(int i) {
		if (i % 2 == 0) return i >> 1;
		else return (i >> 1) + 1;
	}

	public static int half(int value) {
		return value >> 1;
	}

	/**
	 * Clamp the value of v to the range [-PI, PI].
	 *
	 * @param v value
	 * @return [-PI, PI]
	 */
	public static double pullBackWithPI(double v) {
		while (v > PI) v = v - TWICE_PI;
		while (v < -PI) v = v + TWICE_PI;
		/*
		if (v > PI) {
			v = v - TWICE_PI;
		}
		if (v < -PI) {
			v = v + TWICE_PI;
		}

		 */
		return v;
	}

	/**
	 * Returns 0 when greater than or equal to the given value.
	 *
	 * @param value test value
	 * @param max   max value
	 * @return returns value if less than max; otherwise, returns 0.
	 */
	public static int maxToZero(int value, int max) {
		return value < max ? value : 0;
	}

	/**
	 * Returns 0 when greater than or equal to the given value.
	 *
	 * @param value test value
	 * @param max   max value
	 * @return returns value if less than max; otherwise, returns 0.
	 */
	public static float maxToZero(float value, float max) {
		return value < max ? value : 0f;
	}

	/**
	 * Returns 0 when greater than or equal to the given value.
	 *
	 * @param value test value
	 * @param max   max value
	 * @return returns value if less than max; otherwise, returns 0.
	 */
	public static long maxToZero(long value, long max) {
		return value < max ? value : 0L;
	}

	/**
	 * Returns 0 when greater than or equal to the given value.
	 *
	 * @param value test value
	 * @param max   max value
	 * @return returns value if less than max; otherwise, returns 0.
	 */
	public static double maxToZero(double value, double max) {
		return value < max ? value : 0d;
	}

	public static int log(float number, int base) {
		return log((double) number, base);
	}

	public static int log(double number, int base) {
		int customBaseLog = 0;
		double powerOfBase = 1.0;

		while (powerOfBase < number) {
			powerOfBase *= base;
			customBaseLog++;
		}

		if (powerOfBase > number) {
			customBaseLog--;
		}
		return customBaseLog;
	}

	/**
	 * 位运算计算log
	 *
	 * @param n >= 1
	 * @return log
	 */
	public static int log2Floor(int n) {
		if (n < 1) throw new _TargetException("Number must >= 1 At => " + n);
		int log = 0;
		if (n > 0xffff) {
			n >>>= 16;
			log = 16;
		}
		if (n > 0xff) {
			n >>>= 8;
			log |= 8;
		}
		if (n > 0xf) {
			n >>>= 4;
			log |= 4;
		}
		if (n > 0b11) {
			n >>>= 2;
			log |= 2;
		}
		return log + (n >>> 1);
	}

	public static List<Point2D.Double> getPosWithCircle(double circleRadius, int pointNumber) {
		ArrayList<Point2D.Double> points = new ArrayList<>();
		for (int i = 0; i < pointNumber; i++) {
			double angle = TWICE_PI * i / pointNumber;
			double x = circleRadius * Math.cos(angle);
			double y = circleRadius * Math.sin(angle);
			points.add(new Point2D.Double(x, y));
		}
		return points;
	}

	/**
	 * 解析表达式并计算结果, 递归解析
	 *
	 * @param expression 表达式
	 * @return 计算结果
	 */
	public static double evaluate(String expression) {
		char[] tokens = expression.toCharArray();

		// Stack for numbers
		Stack<Double> values = new Stack<>();

		// Stack for operators
		Stack<Character> operators = new Stack<>();

		for (int i = 0; i < tokens.length; i++) {
			// Skip whitespace
			if (tokens[i] == ' ')
				continue;

			// If current token is a number, push it to stack for numbers
			if (Character.isDigit(tokens[i]) || tokens[i] == '.') {
				StringBuilder sb = new StringBuilder();
				// There may be more than one digits in the number
				while (i < tokens.length && (Character.isDigit(tokens[i]) || tokens[i] == '.')) {
					sb.append(tokens[i++]);
				}
				values.push(Double.parseDouble(sb.toString()));
				// Since the index is incremented one extra in loop
				// for(i++)
				i--;
			}
			// If current token is an opening brace, push it to operators stack
			else if (tokens[i] == '(')
				operators.push(tokens[i]);

				// If current token is a closing brace, solve entire brace
			else if (tokens[i] == ')') {
				while (operators.peek() != '(')
					values.push(applyOp(operators.pop(), values.pop(), values.pop()));
				operators.pop();
			}

			// If current token is an operator
			else if (tokens[i] == '+' || tokens[i] == '-' ||
					tokens[i] == '*' || tokens[i] == '/') {
				// While top of 'operators' has same or greater precedence to current
				// token, which is an operator. Apply operator on top of 'operators'
				// to top two elements in values stack
				while (!operators.empty() && hasPrecedence(tokens[i], operators.peek()))
					values.push(applyOp(operators.pop(), values.pop(), values.pop()));

				// Push current token to 'operators'.
				operators.push(tokens[i]);
			}
		}

		// Entire expression has been parsed at this point, apply remaining
		// operators to remaining values
		while (!operators.empty())
			values.push(applyOp(operators.pop(), values.pop(), values.pop()));

		// Top of 'values' contains result, return it
		return values.pop();
	}

	// Returns true if 'op2' has higher or same precedence as 'op1',
	// otherwise returns false.
	public static boolean hasPrecedence(char op1, char op2) {
		if (op2 == '(' || op2 == ')')
			return false;
		return (op1 != '*' && op1 != '/') || (op2 != '+' && op2 != '-');
	}

	/**
	 * 算式运算
	 *
	 * @param op 运算符 + - * /
	 * @param b  第二个操作数
	 * @param a  第一个操作数
	 * @return 运算结果
	 */
	// A utility method to apply an operator 'op' on operands 'a'
	// and 'b'. Return the result.
	public static double applyOp(char op, double b, double a) {
		return switch (op) {
			case '+' -> a + b;
			case '-' -> a - b;
			case '*' -> a * b;
			case '/' -> {
				if (b == 0)
					throw new
							UnsupportedOperationException("Cannot divide by zero");
				yield a / b;
			}
			default -> 0;
		};
	}


	public static class Arc {
		public double[] getArcCenter(double xc, double yc, double r, double theta1, double theta2) {
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

		protected double getArc(double angleDegrees, double radius) {
			return (angleDegrees / 360.0) * (2 * PI * radius);
		}

		protected double[] getArcCenter(double radius, double theta, double alpha) {
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

		protected double getTextAngle(double b, double h) {
			double tanTheta = (b / 2) / h;
			double theta = Math.atan(tanTheta);
			double degreesTheta = Math.toDegrees(theta);
			return 180 - 2 * degreesTheta;
		}
	}

	public static class RD {
		private RD() {
		}

		public static Random random = new Random();

		public static boolean isHit(float p) {
			return !(getRandomFloat() >= p);
		}

		public static int getRandomNumberH(int min, int max) {
			return getRandomNumber(min, max + 1);
		}

		public static int getRandomNumber(int min, int max) {
			//return RandomSource.createNewThreadLocalInstance().nextInt(min,max);
			return random.nextInt(max - min) + min;
		}

		public static float getRandomFloat() {
			return random.nextFloat();
		}

		public static double getRandomDouble() {
			return random.nextDouble();
		}

		public static int getIntRandomNumber(int min, int max) {
			return random.nextInt(min, max + 1);
		}
	}


	public static List<Point2D.Double> distributePoints(double circleRadius, int pointNumber) {
		return distributePoints(circleRadius, pointNumber, 0);
	}

	public static List<Point2D.Double> distributePoints(double circleRadius, int pointNumber, double rotationAngle) {
		ArrayList<Point2D.Double> points = new ArrayList<>();
		for (int i = 0; i < pointNumber; i++) {
			double angle = rotationAngle + TWICE_PI * i / pointNumber;
			double x = circleRadius * Math.cos(angle);
			double y = circleRadius * Math.sin(angle);
			points.add(new Point2D.Double(x, y));
		}
		return points;
	}
}

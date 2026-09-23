package dev.anye.core.color;

import dev.anye.core.math._Math;

public final class _ColorSupport {
	private _ColorSupport(){}
	public static int HexToColor(String s) {
		if (s == null) {
			return 0x00000000;
		}
		if (s.startsWith("0x")) {
			s = s.substring(2);
		} else if (s.startsWith("#")) {
			s = s.substring(1);
		}
		return (int) Long.parseLong(s, 16);
	}

	public static int getR(int color) {
		return (color >> 16) & 0xFF;
	}

	public static int getG(int color) {
		return (color >> 8) & 0xFF;
	}

	public static int getB(int color) {
		return color & 0xFF;
	}

	public static int getAlpha(int color) {
		if ((color & 0xFF000000) != 0) {
			return (color >> 24) & 0xFF;
		} else {
			return 255;
		}
	}

	/**
	 * 将颜色等比淡化
	 * @param color 待淡化的颜色
	 * @param ratio 比例
	 * @param alpha 是否包含透明通道
	 * @return new color
	 */
	public static int fade(int color,float ratio,boolean alpha){
		int[] colors = extractRGBA(color);
		return rgbaToInt((int) (colors[0] * ratio), (int) (colors[1] * ratio), (int) (colors[2] * ratio), alpha ? (int) (colors[3] * ratio) : colors[3]);
	}

	/**
	 * 将颜色的值限制到0 - 255
	 * @param v 值
	 * @return 0 - v - 255
	 */
	public static int format(int v){
		return _Math.clamp(v,0,255);
	}

	/**
	 * 将rgba转为int型颜色，值会被限制在0-255
	 * @param r red
	 * @param g green
	 * @param b blue
	 * @param a alpha
	 * @return 0xFF FF FF FF
	 */
	public static int rgbaToInt(int r,int g,int b,int a) {
		return ((format(a) & 0xFF) << 24) |
				((format(r) & 0xFF) << 16) |
				((format(g) & 0xFF) << 8)  |
				(format(b) & 0xFF);
	}

	/**
	 * 提取颜色到数组，附加透明通道
	 * @param color 待提取的颜色
	 * @return {r, g, b, a}
	 */
	public static int[] extractRGBA(int color) {
		int r;
		int g;
		int b;
		int a;
		if ((color & 0xFF000000) != 0) {
			// ARGB format
			a = (color >> 24) & 0xFF;
		} else {
			// RGB format, default to opaque
			a = 255;
		}
		r = (color >> 16) & 0xFF;
		g = (color >> 8) & 0xFF;
		b = color & 0xFF;

		return new int[]{r, g, b, a};
	}

	public static String intToHexColor(int color) {
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		int a = (color >> 24) & 0xFF;
		if ((color & 0xFF000000) == 0) {
			a = 255;
		}
		return String.format("0x%02X%02X%02X%02X", a, r, g, b);
	}
}

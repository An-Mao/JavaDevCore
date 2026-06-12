package dev.anye.core.draw;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class _Image {
	private final int width;
	private final int height;
	private final int scaleFactor; // 添加缩放因子
	private final BufferedImage image;
	private final Graphics2D graphics;
	private Font font = new Font("Arial", Font.PLAIN, 15);

	public _Image(int width, int height, int scaleFactor) {
		this.width = width;
		this.height = height;
		this.scaleFactor = scaleFactor; // 设置缩放因子
		image = new BufferedImage(this.width * scaleFactor, this.height * scaleFactor, BufferedImage.TYPE_INT_ARGB); // 创建更高分辨率的图像
		graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		//graphics.setComposite(AlphaComposite.SrcOver);
		graphics.scale(scaleFactor, scaleFactor);  // 缩放 Graphics 对象
	}

	public _Image(int width, int height, int scaleFactor, Font font) {
		this(width, height, scaleFactor);
		this.font = font;
	}

	public void setFont(Font font) {
		this.font = font;
	}

	public void drawRect(int x, int y, int width, int height, Color color) {
		graphics.setColor(color);
		graphics.fillRect(x, y, width, height);
	}

	public void drawText(String text, int x, int y, Color defaultColor) {
		if (text == null || text.isEmpty()) {
			return;
		}

		graphics.setFont(font); // 确保每次绘制文本时都设置字体

		int currentX = x;
		Color currentColor = defaultColor;
		int startIndex = 0;

		while (startIndex < text.length()) {
			int colorCodeStart = text.indexOf("[", startIndex);

			if (colorCodeStart == -1) {
				// 没有找到颜色代码，绘制剩余的文本
				graphics.setColor(currentColor);
				graphics.drawString(text.substring(startIndex), currentX, y);
				break;
			}

			// 绘制颜色代码之前的文本
			if (colorCodeStart > startIndex) {
				graphics.setColor(currentColor);
				graphics.drawString(text.substring(startIndex, colorCodeStart), currentX, y);
				currentX += graphics.getFontMetrics().stringWidth(text.substring(startIndex, colorCodeStart));
			}

			// 提取颜色代码
			int colorCodeEnd = text.indexOf("]", colorCodeStart);
			if (colorCodeEnd == -1 || colorCodeEnd - colorCodeStart != 7) {
				// 颜色代码不完整或格式错误，忽略它
				graphics.setColor(currentColor);
				graphics.drawString(text.substring(colorCodeStart, colorCodeStart + 1), currentX, y);
				currentX += graphics.getFontMetrics().stringWidth(text.substring(colorCodeStart, colorCodeStart + 1));
				startIndex = colorCodeStart + 1;
				continue;
			}

			String colorCode = text.substring(colorCodeStart + 1, colorCodeEnd);

			try {
				currentColor = Color.decode("#" + colorCode); // 尝试解析颜色代码
			} catch (NumberFormatException e) {
				// 颜色代码无效，忽略它
				graphics.setColor(defaultColor); // 恢复默认颜色
				graphics.drawString(text.substring(colorCodeStart, colorCodeEnd + 1), currentX, y);
				currentX += graphics.getFontMetrics().stringWidth(text.substring(colorCodeStart, colorCodeEnd + 1));
				startIndex = colorCodeEnd + 1;
				continue;
			}

			// 更新 startIndex，跳过颜色代码
			startIndex = colorCodeEnd + 1;
		}
	}

	public void drawImage(BufferedImage image, int x, int y) {
		graphics.drawImage(image, x, y, null);
	}

	public void oldsave(String path) {
		graphics.dispose();
		try {
			ImageIO.write(image, "png", new File(path));
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	public void save(String path) {
		graphics.dispose();
		BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaledImage.createGraphics();
		g.drawImage(image, 0, 0, width, height, null); // 将高分辨率图像缩放到目标大小
		g.dispose();
		try {
			ImageIO.write(scaledImage, "png", new File(path));
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	public Graphics2D getGraphics() {
		return graphics;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	/**
	 * 获取指定字符串在当前字体下的宽度.
	 *
	 * @param text 要测量的字符串
	 * @return 字符串的宽度 (以像素为单位)
	 */
	public int getStringWidth(String text) {
		graphics.setFont(font); // Ensure font is set on the graphics context
		return graphics.getFontMetrics().stringWidth(text);
	}
}

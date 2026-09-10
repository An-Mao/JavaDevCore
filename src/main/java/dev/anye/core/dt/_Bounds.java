package dev.anye.core.dt;

import dev.anye.core.math._Math;

public class _Bounds {
	private int minX;
	private int minY;
	private int maxX;
	private int maxY;

	private int centerX;
	private int centerY;

	private int width;
	private int height;

	public _Bounds(_Bounds bounds) {
		this(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY);
	}

	public _Bounds() {
		this(0, 0, 0, 0);
	}

	public _Bounds(int minX, int minY, int maxX, int maxY) {
		set(minX, minY, maxX, maxY);
	}


	public boolean include(int x, int y) {
		return _Math.abs(centerX - x) <= width && _Math.abs(centerY - y) <= height;
	}

	public boolean include(int x, int y, int offset) {
		return _Math.abs(centerX - x) <= width + offset && _Math.abs(centerY - y) <= height + offset;
	}


	public void upX() {
		width = maxX - minX;
		centerX = minX + (width >> 1);
	}

	public void upY() {
		height = maxY - minY;
		centerY = minY + (height >> 1);
	}

	public void up() {
		upX();
		upY();
	}

	public void setMinX(int minX) {
		this.minX = minX;
		upX();
	}

	public void setMinY(int minY) {
		this.minY = minY;
		upY();
	}

	public void setMaxX(int maxX) {
		this.maxX = maxX;
		upX();
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
		upY();
	}


	public void setY(int minY, int maxY) {
		this.minY = minY;
		this.maxY = maxY;
		upY();
	}

	public void setX(int minX, int maxX) {
		this.minX = minX;
		this.maxX = maxX;
		upX();
	}

	public void setMax(int maxX, int maxY) {
		this.maxX = maxX;
		this.maxY = maxY;
		up();
	}

	public void setMin(int minX, int minY) {
		this.minX = minX;
		this.minY = minY;
		up();
	}

	public void set(int minX, int minY, int maxX, int maxY) {
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		up();
	}

	public void replace(_Bounds bounds) {
		set(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY);
	}

	public _Bounds copy() {
		return new _Bounds(this);
	}

	public int minX() {
		return minX;
	}

	public int minY() {
		return minY;
	}

	public int maxX() {
		return maxX;
	}

	public int maxY() {
		return maxY;
	}

	public int centerX() {
		return centerX;
	}

	public int centerY() {
		return centerY;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	@Override
	public String toString() {
		return "_Bounds{" +
				"minX=" + minX +
				", minY=" + minY +
				", maxX=" + maxX +
				", maxY=" + maxY +
				", centerX=" + centerX +
				", centerY=" + centerY +
				", width=" + width +
				", height=" + height +
				'}';
	}
}

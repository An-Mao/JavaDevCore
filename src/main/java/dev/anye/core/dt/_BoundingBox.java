package dev.anye.core.dt;

public class _BoundingBox {
	private int x, y, w, h;

	public _BoundingBox(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public _BoundingBox(int w, int h) {
		this(0, 0, w, h);
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getX() {
		return x;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getY() {
		return y;
	}

	public void setH(int h) {
		this.h = h;
	}

	public int getH() {
		return h;
	}

	public void setW(int w) {
		this.w = w;
	}

	public int getW() {
		return w;
	}

	public int getMaxX() {
		return x + w;
	}

	public int getMaxY() {
		return y + h;
	}

	public void addX(int x) {
		this.x += x;
	}

	public void addY(int y) {
		this.y += y;
	}

	public void addW(int w) {
		this.w += w;
	}

	public void addH(int h) {
		this.h += h;
	}

	public void expansion(int s) {
		this.x -= s;
		this.y -= s;
		this.w += s + s;
		this.h += s + s;
	}

	public void retraction(int s) {
		this.x += s;
		this.y += s;
		this.w -= s + s;
		this.h -= s + s;
	}

	public _BoundingBox copy() {
		return new _BoundingBox(this.x, this.y, this.w, this.h);
	}

	public boolean isInBox(int x, int y) {
		return x > this.x && x < getMaxX() && y > this.y && y < getMaxY();
	}

	public boolean isInBox(_BoundingBox box) {
		return isInBox(box.x, box.y, box.w, box.h);
	}

	public boolean isInBox(int x, int y, int w, int h) {
		return x > this.x && x + w < getMaxX() && y > this.y && y + h < getMaxY();
	}

	@Override
	public String toString() {
		return "BoundingBox{" +
				"x=" + x +
				", y=" + y +
				", w=" + w +
				", h=" + h +
				'}';
	}
}

package dev.anye.core.dt;

import dev.anye.core.math._Math;

public class _BoundsR extends _Bounds{
	private int radius;
	public _BoundsR(){

	}
	public _BoundsR(int minX, int minY, int maxX, int maxY,int radius){
		super(minX,minY,maxX,maxY);
		this.radius = radius;
	}

	public boolean includeWithRange(int x, int y){
		return _Math.abs(centerX() - x) <= radius && _Math.abs(centerY() - y) <= radius;
	}

	public boolean includeWithRange(int x, int y, int offset){
		return _Math.abs(centerX() - x) <= radius + offset && _Math.abs(centerY() - y) <= radius + offset;
	}


	public void setRadius(int radius){
		this.radius = radius;
	}

	public int range(){
		return radius;
	}

	@Override
	public String toString() {
		return super.toString()+"{radius="+ radius +"}";
	}
}

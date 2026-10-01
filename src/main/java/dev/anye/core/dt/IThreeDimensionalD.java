package dev.anye.core.dt;

/**
 * 三维结构
 * x,y,z
 * w,h,l
 *
 */
public interface IThreeDimensionalD extends ITwoDimensionalD{
	double z();
	double l();
	default double length(){return l();}
}

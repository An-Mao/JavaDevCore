package dev.anye.core.dt;

/**
 * 三维结构
 * x,y,z
 * w,h,l
 *
 */
public interface IThreeDimensionalI extends ITwoDimensional{
	int z();
	int l();
	default int length(){return l();}
}

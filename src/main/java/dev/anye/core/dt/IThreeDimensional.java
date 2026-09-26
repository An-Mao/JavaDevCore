package dev.anye.core.dt;

/**
 * 三维结构
 * x,y,z
 * w,h,l
 *
 */
public interface IThreeDimensional extends ITwoDimensional{
	float z();
	float l();
	default float length(){return l();}
}

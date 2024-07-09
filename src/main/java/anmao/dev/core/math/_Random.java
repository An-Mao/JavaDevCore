package anmao.dev.core.math;

import java.util.Random;

public class _Random {
    public static Random random = new Random();
    public static int getRandomNumberH(int min ,int max){
        return getRandomNumber(min , max + 1);
    }
    public static int getRandomNumber(int min ,int max){
        //return RandomSource.createNewThreadLocalInstance().nextInt(min,max);
        return random.nextInt(max - min) + min;
    }
    public static float getRandomFloat(){
        return random.nextFloat();
    }
    public static double getRandomDouble(){
        return random.nextDouble();
    }
    public static int getIntRandomNumber(int min,int max){
        return random.nextInt(min,max + 1);
    }
}

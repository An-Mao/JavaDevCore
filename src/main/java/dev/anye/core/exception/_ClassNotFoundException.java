package dev.anye.core.exception;

public class _ClassNotFoundException extends RuntimeException{
	public _ClassNotFoundException(){
		super();
	}
	public _ClassNotFoundException(String className){
		super("Failed to load or process class ==> "+className);
	}
	public _ClassNotFoundException(String className,Throwable e){
		super("Failed to load or process class ==> "+className,e);
	}
	public _ClassNotFoundException(Throwable e){
		super(e);
	}

}

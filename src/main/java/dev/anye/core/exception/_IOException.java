package dev.anye.core.exception;

public class _IOException extends RuntimeException{
	public _IOException(){
		super();
	}
	public _IOException(String file){
		super("IO error ==> "+file);
	}
	public _IOException(String file,Throwable e){
		super("IO error ==> "+file,e);
	}
	public _IOException(Throwable e){
		super(e);
	}

}

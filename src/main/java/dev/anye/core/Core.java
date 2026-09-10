package dev.anye.core;


import dev.anye.core.system._Log;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Core {
	public static final String VERSION = "2.0.6";
	private static final Logger LOGGER = Logger.getLogger(Core.class.getName());
	private static final _Log log = new _Log();
	public static void main(String[] args) {
		log.setDebug(true);
		log.info("Core Version : {}=>{} A",VERSION,true);
		log.debug("Core Version : {}",VERSION);

		//run();
	}

	public static void run() {
	}
}
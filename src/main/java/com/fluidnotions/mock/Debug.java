package com.fluidnotions.mock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Debug {
	
	public static void logInfo(String message, String module){
		Log log = LogFactory.getLog(module);
		log.info(message);
	}
	
	public static void logError(String message, String module){
		Log log = LogFactory.getLog(module);
		log.error(message);
	}

}

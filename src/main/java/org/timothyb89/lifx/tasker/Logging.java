package org.timothyb89.lifx.tasker;

import android.os.Environment;
import de.mindpipe.android.logging.log4j.LogConfigurator;
import java.io.File;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tim
 */
public class Logging {
	
	private static volatile boolean initialized = false;
	
	private static void configure() {
		LogConfigurator conf = new LogConfigurator();
                
        conf.setFileName(Environment.getExternalStorageDirectory()
				+ File.separator + "lifx-tasker.log");
        conf.setRootLevel(Level.DEBUG);
		
        // Set log level of a specific logger
        conf.setLevel("org.apache", Level.ERROR);
		
        conf.configure();
		
	}
	
	public static Logger init(Class c) {
		if (!initialized) {
			configure();
			
			initialized = true;
		}
		
		return LoggerFactory.getLogger(c);
	}
	
}

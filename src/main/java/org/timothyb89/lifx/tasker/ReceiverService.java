package org.timothyb89.lifx.tasker;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.lifx.tasker.editor.Action;
import org.timothyb89.lifx.tasker.editor.BulbListEditor;
import org.timothyb89.lifx.tasker.editor.ColorEditor;

/**
 * A wrapper service to process events from FireReceiver.
 * ... a service to start a service? :(
 * @author tim
 */
@EService
public class ReceiverService extends IntentService {

	private static Logger log = Logging.init(ReceiverService.class);
	
	public static final int PULSE_DEFAULT_LENGTH = 500;
	public static final int CONNECTION_MAX_ATTEMPTS = 1;
	
	private Bundle bundle;
	
	private volatile int connectionAttempts;
	private volatile boolean connectionMade;
	
	public ReceiverService() {
		super("ReceiverService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		bundle = intent.getBundleExtra(FireReceiver.KEY_BUNDLE);
		
		log.info("Attempting to start LIFX service...");
		
		startService(new Intent(this, LIFXService_.class));

		connectionMade = false;
		connectionAttempts = 0;
		
		Intent service = new Intent(this, LIFXService_.class);
		
		// an awful hack
		// for some reason bindService() occassionally fails to ever connect
		// the service starts in the background, but we're never notified
		while (!connectionMade) {
			if (connectionAttempts > 0) {
				// old instances will still be floating around
				// we need to kill them to make sure we can actually bind the
				// port
				stopService(service);
			}
			
			boolean ret = bindService(
					new Intent(this, LIFXService_.class),
					connection,
					Context.BIND_AUTO_CREATE);

			log.info("Service bound, returned {}", ret);
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				break;
			}
			
			connectionAttempts++;
			if (connectionAttempts > CONNECTION_MAX_ATTEMPTS) {
				break;
			}
			
			log.warn("Service connection failed, retrying...");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (connection != null) {
			unbindService(connection);
		}
	}
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	private String[] getBulbs() {
		return bundle.getStringArray(BulbListEditor.KEY_BULBS);
	}
	
	private int getColor() {
		return bundle.getInt(ColorEditor.KEY_COLOR);
	}
	
	@Background
	protected void process(LIFXService lifx) {
		// crappy action handling for now - will be improved later
		String actionId = bundle.getString(FireReceiver.KEY_ACTION);
		Action action = Action.getAction(actionId);
		
		log.debug("Processing action: {}", action);
		
		// force a refresh
		//lifx.purgeBulbs();
		
		switch (action) {
			case POWER_ON:     lifx.turnOn(  getBulbs());             break;
			case POWER_OFF:    lifx.turnOff( getBulbs());             break;
			case POWER_TOGGLE: lifx.toggle(  getBulbs());             break;
			case COLOR_SET:    lifx.setColor(getBulbs(), getColor()); break;
			case COLOR_PULSE:  lifx.pulse(   getBulbs(), getColor()); break;
			default:
				log.error("Unknown action: {}", actionId);
				showToast("LIFX Tasker: Unknown action " + actionId);
				
				break;
		}
		
		// wait a bit and do a status update
		//try {
		//	Thread.sleep(750);
		//} catch (InterruptedException ex) { } // ignore
		
		lifx.refreshCycle();
		
		lifx.closeSocket();
		//lifx.stop();
		
		stopSelf();
	}
	
	private ServiceConnection connection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			connectionMade = true;
			
			log.info("LIFX service connected.");
			
			LIFXService lifx = ((LIFXService.LIFXBinder) service).getService();
			
			process(lifx);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// ignore
			log.debug("LIFX service disconnected.");
		}
		
	};
	
}

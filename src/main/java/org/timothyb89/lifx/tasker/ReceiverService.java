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

	private static Logger log = LoggerFactory.getLogger(ReceiverService.class);
	
	public static final int PULSE_DEFAULT_LENGTH = 500;
	
	private Bundle bundle;
	
	public ReceiverService() {
		super("ReceiverService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		bundle = intent.getBundleExtra(FireReceiver.KEY_BUNDLE);
		
		startService(new Intent(this, LIFXService_.class));
		bindService(
				new Intent(this, LIFXService_.class),
				connection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		unbindService(connection);
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
		
		switch (action) {
			case POWER_ON:
				for (String bulbName : getBulbs()) {
					lifx.turnOn(bulbName);
				}

				break;

			case POWER_OFF:
				for (String bulbName : getBulbs()) {
					lifx.turnOff(bulbName);
				}

				break;

			case POWER_TOGGLE:
				for (String bulbName : getBulbs()) {
					lifx.toggle(bulbName);
				}

				break;
				
			case COLOR_SET:
				int color = getColor();
				
				for (String bulbName : getBulbs()) {
					lifx.setColor(bulbName, color);
				}
				break;
				
			case COLOR_PULSE:
				lifx.pulse(getBulbs(), getColor());
				
				break;
			
			default:
				log.error("Unknown action: {}", actionId);
				showToast("LIFX-Tasker: Uknown action " + actionId);
				
				break;
		}
	}
	
	private ServiceConnection connection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LIFXService lifx = ((LIFXService.LIFXBinder) service).getService();
			
			process(lifx);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// ignore
		}
		
	};
	
}

package org.timothyb89.lifx.tasker;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EService;

/**
 * A wrapper service to process events from FireReceiver.
 * ... a service to start a service? :(
 * @author tim
 */
@EService
public class ReceiverService extends IntentService {

	private String action;
	private String[] bulbs;
	
	public ReceiverService() {
		super("ReceiverService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		action = intent.getStringExtra(FireReceiver.KEY_ACTION);
		bulbs = intent.getStringArrayExtra(FireReceiver.KEY_BULBS);
		
		startService(new Intent(this, LIFXService.class));
		bindService(
				new Intent(this, LIFXService.class),
				connection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		unbindService(connection);
	}
	
	@Background
	protected void process(LIFXService lifx) {
		// crappy action handling for now - will be improved later
		switch (action) {
			case "Power On":
				for (String bulbName : bulbs) {
					lifx.turnOn(bulbName);
				}

				break;

			case "Power Off":
				for (String bulbName : bulbs) {
					lifx.turnOff(bulbName);
				}

				break;

			case "Power Toggle":
				for (String bulbName : bulbs) {
					lifx.toggle(bulbName);
				}

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

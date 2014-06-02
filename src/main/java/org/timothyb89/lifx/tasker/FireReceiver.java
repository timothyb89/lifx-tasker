package org.timothyb89.lifx.tasker;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.lifx.bulb.Bulb;

/**
 *
 * @author tim
 */
public class FireReceiver extends BroadcastReceiver {

	private static Logger log = Logging.init(FireReceiver.class);
	
	public static final String KEY_ACTION = "action";
	public static final String KEY_BUNDLE = "bundle";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String setting = com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING;
		if (!setting.equals(intent.getAction())) {
			log.warn("Unexpected intent action: " + intent.getAction());
			return;
		}
		
		log.info("Received Tasker event!");
		
		Bundle bundle = intent.getBundleExtra(
				com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
		
		Intent wrapped = new Intent(context, ReceiverService_.class);
		wrapped.putExtra(KEY_BUNDLE, bundle);
		context.startService(wrapped);
	}
	
}

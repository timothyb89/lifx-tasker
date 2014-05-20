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

	private static Logger log = LoggerFactory.getLogger(FireReceiver.class);
	
	public static final String KEY_BULBS = "bulbs";
	public static final String KEY_ACTION = "action";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction())) {
			log.warn("Unexpected intent action: " + intent.getAction());
			return;
		}
		
		Bundle bundle = intent.getBundleExtra(
				com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
		
		String[] bulbs = bundle.getStringArray(KEY_BULBS);
		String action = bundle.getString(KEY_ACTION);
		
		log.info("Tasker event; action: {}, bulbs: {}", action, bulbs);
		
		Intent wrapped = new Intent(context, ReceiverService_.class);
		wrapped.putExtra(KEY_ACTION, action);
		wrapped.putExtra(KEY_BULBS, bulbs);
		
		context.startService(wrapped);
	}
	
	
	
}

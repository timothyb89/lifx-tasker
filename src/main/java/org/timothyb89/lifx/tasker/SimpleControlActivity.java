package org.timothyb89.lifx.tasker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import lombok.extern.slf4j.Slf4j;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.timothyb89.lifx.gateway.Gateway;
import org.timothyb89.lifx.tasker.LIFXService.LIFXBinder;

@Slf4j
@EActivity(R.layout.activity_main)
public class SimpleControlActivity extends Activity {
	
	@ViewById(R.id.gateway_field)
	protected TextView gatewayField;
	
	@ViewById(R.id.gateway_bulbs_off)
	protected Button bulbsOffButton;
	
	@ViewById(R.id.gateway_bulbs_on)
	protected Button bulbsOnButton;
	
	private LIFXService lifx;
	
	/**
	 * Called when the activity is first created.
	 *
	 * @param savedInstanceState If the activity is being re-initialized after
	 * previously being shut down then this Bundle contains the data it most
	 * recently supplied in onSaveInstanceState(Bundle). <b>Note: Otherwise it
	 * is null.</b>
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//listener = new BroadcastListener(this);
		//listener.bus().register(this);
		
		//try {
		//	listener.startListen();
		//} catch (Exception ex) {
		//	log.error("couldn't listen", ex);
		//}
		initService();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		unbindService(connection);
	}
	
	@Background
	protected void initService() {
		startService(new Intent(this, LIFXService.class));
		
		bindService(
				new Intent(this, LIFXService.class),
				connection,
				Context.BIND_AUTO_CREATE);
	}
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	@Click(R.id.gateway_bulbs_off)
	@Background
	protected void bulbsOffButtonClicked() {
		if (lifx == null) {
			showToast("Error: service not connected");
			return;
		}
		
		lifx.turnOff();
		
		//try {
		//	gateway.turnOff();
		//} catch (IOException ex) {
		//	showToast("Error: " + ex.getMessage());
		//	log.error("Error turning off bulbs", ex);
		//}
	}
	
	@Click(R.id.gateway_bulbs_on)
	@Background
	protected void bulbsOnButtonClicked() {
		if (lifx == null) {
			showToast("Error: service not connected");
			return;
		}
		
		lifx.turnOff();
		
		//try {
		//	gateway.turnOn();
		//} catch (IOException ex) {
		//	showToast("Error: " + ex.getMessage());
		//	log.error("Error turning on bulbs", ex);
		//}
	}
	
	@UiThread
	protected void updateGateway(Gateway gateway)  {
		//this.gateway = gateway;
		
		gatewayField.setText(gateway.toString());
		showToast("Found gateway " + gateway);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			lifx = ((LIFXBinder) service).getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			lifx = null;
		}
		
	};
	
}

package org.timothyb89.lifx.tasker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.timothyb89.R;
import org.timothyb89.eventbus.EventHandler;
import org.timothyb89.lifx.gateway.Gateway;
import org.timothyb89.lifx.net.BroadcastListener;
import org.timothyb89.lifx.net.GatewayDiscoveredEvent;

@Slf4j
@EActivity(R.layout.activity_main)
public class SimpleControlActivity extends Activity {
	
	@ViewById(R.id.gateway_field)
	protected TextView gatewayField;
	
	@ViewById(R.id.gateway_bulbs_off)
	protected Button bulbsOffButton;
	
	@ViewById(R.id.gateway_bulbs_on)
	protected Button bulbsOnButton;
	
	private BroadcastListener listener;
	private Gateway gateway;
	
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
		
		listener = new BroadcastListener(this);
		listener.bus().register(this);
		
		try {
			listener.startListen();
		} catch (Exception ex) {
			log.error("couldn't listen", ex);
		}
	}
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	@Click(R.id.gateway_bulbs_off)
	@Background
	protected void bulbsOffButtonClicked() {
		if (gateway == null || !gateway.isConnected()) {
			showToast("Error: not connected");
			return;
		}
		
		try {
			gateway.turnOff();
		} catch (IOException ex) {
			showToast("Error: " + ex.getMessage());
			log.error("Error turning off bulbs", ex);
		}
	}
	
	@Click(R.id.gateway_bulbs_on)
	@Background
	protected void bulbsOnButtonClicked() {
		if (gateway == null || !gateway.isConnected()) {
			showToast("Error: not connected");
			return;
		}
		
		try {
			gateway.turnOn();
		} catch (IOException ex) {
			showToast("Error: " + ex.getMessage());
			log.error("Error turning on bulbs", ex);
		}
	}
	
	@UiThread
	protected void updateGateway(Gateway gateway)  {
		this.gateway = gateway;
		
		gatewayField.setText(gateway.toString());
		showToast("Found gateway " + gateway);
	}
	
	@EventHandler
	public void gatewayDiscovered(GatewayDiscoveredEvent ev) {
		try {
			listener.stopListen();
		} catch (IOException ex) {
			log.error("Couldn't stop listener", ex);
		}
		
		try {
			ev.getGateway().connect();

			updateGateway(ev.getGateway());
		} catch (IOException ex) {
			showToast("Couldn't connect to gateway: " + ex.getMessage());
			log.error("error connecting to gateway", ex);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(org.timothyb89.R.menu.main, menu);
		return true;
	}

}

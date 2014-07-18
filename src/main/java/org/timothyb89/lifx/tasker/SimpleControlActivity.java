package org.timothyb89.lifx.tasker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.apmem.tools.layouts.FlowLayout;
import org.slf4j.Logger;
import org.timothyb89.eventbus.EventHandler;
import org.timothyb89.lifx.bulb.Bulb;
import org.timothyb89.lifx.bulb.BulbPowerStateUpdatedEvent;
import org.timothyb89.lifx.bulb.PowerState;
import org.timothyb89.lifx.tasker.LIFXService.LIFXBinder;

@EActivity(R.layout.activity_main)
public class SimpleControlActivity extends Activity {
	
	private static Logger log = Logging.init(SimpleControlActivity.class);
	
	@ViewById(R.id.gateway_bulbs_off)
	protected Button bulbsOffButton;
	
	@ViewById(R.id.gateway_bulbs_on)
	protected Button bulbsOnButton;

	@ViewById(R.id.bulb_list)
	protected FlowLayout bulbContainer;
	
	private Map<Bulb, Button> bulbMap;
	
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
		
		bulbMap = new HashMap<>();
		
		initService();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		unbindService(connection);
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		if (lifx != null) {
			lifx.closeSocket();
			
			lifx.bus().deregister(this);
		}
	}
	
	@Background
	protected void initService() {
		log.info("Starting service");
		
		startService(new Intent(this, LIFXService_.class));
		
		bindService(
				new Intent(this, LIFXService_.class),
				connection,
				Context.BIND_AUTO_CREATE);
	}
	
	protected void serviceConnected() {
		bulbsUpdated();
		
		lifx.refreshAll();
		
		log.info("LIFX: {}", lifx);
		if (lifx != null) {
			lifx.bus().register(this);
		}
	}
	
	@EventHandler
	public void bulbsUpdatedHandler(BulbListUpdatedEvent event) {
		bulbsUpdated();
	}
	
	@UiThread
	protected void bulbsUpdated() {
		if (lifx == null) {
			return; // wat?
		}
		
		log.info("Bulbs updated.");
		
		bulbMap.clear();
		bulbContainer.removeAllViews();
		
		for (Bulb bulb : lifx.getBulbs()) {
			Button b = new Button(this);
			b.setOnClickListener(new BulbButtonController(bulb, b));
			b.setText(bulb.getLabel());
			bulbMap.put(bulb, b);
			
			bulbContainer.addView(b);
		}
	}
	
	@Background
	protected void toggle(Bulb bulb) {
		lifx.toggle(bulb.getLabel());
		
		try {
			Thread.sleep(750);
		} catch (InterruptedException ex) {}
		
		lifx.refreshAll();
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
		
		log.info("Attempted turn-off");
	}
	
	@Click(R.id.gateway_bulbs_on)
	@Background
	protected void bulbsOnButtonClicked() {
		if (lifx == null) {
			showToast("Error: service not connected");
			return;
		}
		
		lifx.turnOn();
		
		log.info("Attempted turn-on.");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@UiThread
	protected void updateButtonIcon(Button b, int resId) {
		b.setCompoundDrawablesWithIntrinsicBounds(0, resId, 0, 0);
	}

	private ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			lifx = ((LIFXBinder) service).getService();
			
			serviceConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			lifx = null;
		}
		
	};
	
	protected class BulbButtonController implements View.OnClickListener {

		private Bulb bulb;
		private Button button;

		public BulbButtonController(Bulb bulb, Button button) {
			this.bulb = bulb;
			this.button = button;
			
			bulb.bus().register(this);
			
			// fake an event to update the icon initially
			handleStateUpdated(new BulbPowerStateUpdatedEvent(
					bulb, bulb.getPowerState()));
		}
		
		@Override
		public void onClick(View v) {
			toggle(bulb);
		}
		
		@EventHandler
		public void handleStateUpdated(BulbPowerStateUpdatedEvent event) {
			if (event.getPowerState() == PowerState.OFF) {
				updateButtonIcon(button, R.drawable.bulb_off);
			} else {
				updateButtonIcon(button, R.drawable.bulb_on);
			}
		}
		
	}
	
}

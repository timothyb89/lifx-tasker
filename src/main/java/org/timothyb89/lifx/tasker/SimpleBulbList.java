package org.timothyb89.lifx.tasker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.apmem.tools.layouts.FlowLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.lifx.bulb.Bulb;
import org.timothyb89.lifx.bulb.PowerState;
import org.timothyb89.lifx.gateway.Gateway;

/**
 * An activity to select bulbs from a list.
 * @author tim
 */
@EActivity(R.layout.activity_bulb_list)
public class SimpleBulbList extends Activity {

	public static final String KEY_INTENT_BULBS = "bulb_names_pre";
	public static final String KEY_RESULT_BULBS = "bulb_names";
	
	private static Logger log = LoggerFactory.getLogger(SimpleBulbList.class);
	
	@ViewById(R.id.select_all)
	protected Button selectAllButton;
	
	@ViewById(R.id.select_none)
	protected Button selectNoneButton; 
	
	@ViewById(R.id.select_bulb_list)
	protected FlowLayout bulbContainer;
	
	private LIFXService lifx;
	
	private List<String> selectedBulbs;
	private Map<CheckBox, Bulb> bulbMap;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		selectedBulbs = new ArrayList<>();
		if (getIntent().hasExtra(KEY_INTENT_BULBS)) {
			for (String s : getIntent().getStringArrayExtra(KEY_INTENT_BULBS)) {
				selectedBulbs.add(s);
			}
		}
		
		bulbMap = new HashMap<>();
		
		initService();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		unbindService(connection);
	}
	
	@Background
	protected void initService() {
		log.info("Starting service");
		
		startService(new Intent(this, LIFXService.class));
		
		bindService(
				new Intent(this, LIFXService.class),
				connection,
				Context.BIND_AUTO_CREATE);
	}

	protected void serviceConnected() {
		bulbsUpdated();
		
		log.info("LIFX: {}", lifx);
		if (lifx != null) {
			lifx.bus().register(this);
		}
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
			CheckBox b = new CheckBox(this);
			b.setText(bulb.getLabel());
			
			if (selectedBulbs.contains(bulb.getLabel())) {
				b.setChecked(true);
			}
			
			int icon = bulb.getPowerState() == PowerState.ON ?
					R.drawable.bulb_on :
					R.drawable.bulb_off;
			
			b.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
			bulbMap.put(b, bulb);
			
			bulbContainer.addView(b);
		}
	}
	
	@Click(R.id.select_all)
	protected void doSelectAll() {
		for (CheckBox b : bulbMap.keySet()) {
			b.setChecked(true);
		}
	}
	
	@Click(R.id.select_none)
	protected void doSelectNone() {
		for (CheckBox b : bulbMap.keySet()) {
			b.setChecked(false);
		}
	}
	
	private void accept() {
		Intent result = new Intent();
		
		List<String> values = new ArrayList<>();
		for (Entry<CheckBox, Bulb> e : bulbMap.entrySet()) {
			if (e.getKey().isChecked()) {
				values.add(e.getValue().getLabel());
			}
		}
		
		result.putExtra(KEY_RESULT_BULBS, values.toArray(new String[0]));
		
		setResult(RESULT_OK, result);
		finish();
	}
	
	private void cancel() {
		setResult(RESULT_CANCELED);
		finish();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.select, menu);
		
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.select_accept:
				accept();
				return true;
			case R.id.select_cancel:
				cancel();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			lifx = ((LIFXService.LIFXBinder) service).getService();
			
			serviceConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			lifx = null;
		}
		
	};
	
}

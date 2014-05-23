package org.timothyb89.lifx.tasker.editor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.timothyb89.lifx.tasker.EditActivity;
import org.timothyb89.lifx.tasker.LIFXService;
import org.timothyb89.lifx.tasker.R;
import static org.timothyb89.lifx.tasker.editor.ColorEditor.KEY_COLOR;

/**
 * An activity to select bulbs from a list.
 * @author tim
 */
@EActivity(R.layout.activity_edit_bulb_list)
public class BulbListEditor extends Activity {

	public static final String KEY_BULBS = "bulbs";
	
	private static Logger log = LoggerFactory.getLogger(BulbListEditor.class);
	
	@ViewById(R.id.select_all)
	protected Button selectAllButton;
	
	@ViewById(R.id.select_none)
	protected Button selectNoneButton; 
	
	@ViewById(R.id.select_bulb_list)
	protected FlowLayout bulbContainer;
	
	private LIFXService lifx;
	
	private List<String> selectedBulbs;
	private Map<CheckBox, Bulb> bulbMap;
	
	private String paramName;
	private Bundle parameters;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		paramName = getIntent().getStringExtra(EditActivity.KEY_PARAM_NAME);
		parameters = getIntent().getBundleExtra(EditActivity.KEY_PARAMS);
		
		selectedBulbs = new ArrayList<>();
		if (parameters.containsKey(KEY_BULBS)) {
			for (String s : parameters.getStringArray(KEY_BULBS)) {
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
			b.setBackgroundColor(Color.LTGRAY);
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
		result.putExtra(EditActivity.KEY_PARAM_NAME, paramName);
		
		List<String> values = new ArrayList<>();
		for (Entry<CheckBox, Bulb> e : bulbMap.entrySet()) {
			if (e.getKey().isChecked()) {
				values.add(e.getValue().getLabel());
			}
		}
		
		parameters.putStringArray(KEY_BULBS, values.toArray(new String[0]));
		
		result.putExtra(EditActivity.KEY_PARAMS, parameters);
		
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

	@Override
	public void onBackPressed() {
		accept();
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

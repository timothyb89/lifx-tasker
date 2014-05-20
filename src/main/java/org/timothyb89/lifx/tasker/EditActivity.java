package org.timothyb89.lifx.tasker;

import android.app.Activity;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.timothyb89.lifx.bulb.Bulb;
import static org.timothyb89.lifx.tasker.SimpleBulbList.KEY_RESULT_BULBS;

/**
 *
 * @author tim
 */
@EActivity(R.layout.activity_task_edit)
public class EditActivity extends Activity {
	
	public static final int REQUEST_BULBS_SELECT = 1000;
	
	@ViewById(R.id.edit_bulbs_select)
	protected Button bulbsEditButton;
	
	@ViewById(R.id.edit_bulbs_count)
	protected TextView bulbsCountLabel;
	
	@ViewById(R.id.edit_action_spinner)
	protected Spinner actionSpinner;
	
	private String[] bulbs;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	@Click(R.id.edit_bulbs_select)
	protected void bulbSelectButtonClicked() {
		Intent i = new Intent(this, SimpleBulbList_.class);
		
		if (bulbs != null) {
			i.putExtra(SimpleBulbList.KEY_INTENT_BULBS, bulbs);
		}
		
		startActivityForResult(i, REQUEST_BULBS_SELECT);
	}

	private void bulbSelectReturned(int resultCode, Intent data) {
		if (resultCode != RESULT_OK) {
			return;
		}
		
		bulbs = data.getStringArrayExtra(SimpleBulbList.KEY_RESULT_BULBS);
		
		bulbsCountLabel.setText(String.format("%d", bulbs.length));
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_BULBS_SELECT) {
			bulbSelectReturned(resultCode, data);
		}
	}
	
	private void accept() {
		if (bulbs == null || bulbs.length == 0) {
			showToast("No bulbs selected!");
			return;
		}
		
		String action = (String) actionSpinner.getSelectedItem();
		if (action == null) {
			showToast("No action selected!");
			return;
		}
		
		Intent result = new Intent();
		
		Bundle bundle = new Bundle();
		bundle.putString(FireReceiver.KEY_ACTION, action);
		bundle.putStringArray(FireReceiver.KEY_BULBS, bulbs);
		
		result.putExtra(
				com.twofortyfouram.locale.Intent.EXTRA_BUNDLE,
				bundle);
		
		result.putExtra(
				com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB,
				String.format("Action: %s - %d bulbs", action, bulbs.length));
		
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
	
}

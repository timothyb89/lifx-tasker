package org.timothyb89.lifx.tasker;

import android.app.Activity;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ItemSelect;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.timothyb89.lifx.tasker.editor.Action;
import org.timothyb89.lifx.tasker.editor.Parameter;

/**
 *
 * @author tim
 */
@EActivity(R.layout.activity_edit)
public class EditActivity extends Activity {
	
	private static Logger log = Logging.init(EditActivity.class);
	
	public static final int REQUEST_SHOW_EDITOR = 1000;
	
	public static final String KEY_PARAMS = "params";
	public static final String KEY_PARAM_NAME = "param-name";
	
	@ViewById(R.id.edit_action_spinner)
	protected Spinner actionSpinner;
	
	@ViewById(R.id.edit_param_container)
	protected GridLayout paramContainer;
	
	private Bundle parameters;
	
	private Action currentAction;
	
	private Map<Parameter, TextView> labelMap;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		labelMap = new HashMap<>();
	}
	
	@AfterViews
	protected void init() {
		// find defined actions
		List<String> actions = new ArrayList<>();
		for (Action a : Action.values()) {
			actions.add(getString(a.getResourceId()));
		}
		
		actionSpinner.setAdapter(new ArrayAdapter(
				this,
				android.R.layout.simple_list_item_1,
				actions));
		
		
		String key = com.twofortyfouram.locale.Intent.EXTRA_BUNDLE;
		if (getIntent().hasExtra(key)) {
			parameters = getIntent().getBundleExtra(key);
			
			log.info("Got initial parameters...");
			for (String k : parameters.keySet()) {
				log.info("{} = {}", k, parameters.get(k));
			}

		} else {
			parameters = new Bundle();
		}
		
		if (parameters.containsKey(FireReceiver.KEY_ACTION)) {
			Action action = Action.getAction(
					parameters.getString(FireReceiver.KEY_ACTION));
			actionSpinner.setSelection(action.ordinal());
		} else {
			// fake an action selected for the first item
			actionSpinner.setSelection(0);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@UiThread
	protected void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}
	
	private void showEditor(Parameter param) {
		Intent intent = new Intent(this, param.getEditorClass());
		intent.putExtra(KEY_PARAMS, parameters);
		intent.putExtra(KEY_PARAM_NAME, param.name());
		
		startActivityForResult(intent, REQUEST_SHOW_EDITOR);
	}
	
	@ItemSelect(R.id.edit_action_spinner)
	protected void actionSelected(boolean selected, String item) {
		log.info("actionSelected({}, {})", selected, item);
		
		if (!selected) {
			parameters = null;
			return;
		}
		
		Action a = Action.getAction(this, item);
		if (a == null) {
			log.error("Unknown action: " + item);
			showToast("Unknown action: " + item);
			return;
		}
		
		currentAction = a;
		
		parameters.putString(FireReceiver.KEY_ACTION, a.getId());
		
		paramContainer.removeAllViews();
		for (final Parameter p : a.getParameters()) {
			log.info("Creating editor for " + p.name());
			
			TextView label = new TextView(this);
			label.setText(p.getResourceId());
			paramContainer.addView(label);
			
			Button button = new Button(this);
			button.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					showEditor(p);
				}
				
			});
			button.setText(R.string.edit_button);
			paramContainer.addView(button);
			
			TextView statusLabel = new TextView(this);
			if (p.validate(this, parameters)) {
				statusLabel.setText(p.describe(this, parameters));
			}
			
			paramContainer.addView(statusLabel);
			labelMap.put(p, statusLabel);
		}
	}
	
	private void editorReturned(int code, Intent data) {
		// if the editor returned successfully, overwrite our params with its
		if (code != RESULT_OK) {
			return;
		}
		
		parameters = data.getBundleExtra(KEY_PARAMS);
		
		Parameter p = Parameter.valueOf(data.getStringExtra(KEY_PARAM_NAME));
		if (p.validate(this, parameters)) {
			labelMap.get(p).setText(p.describe(this, parameters));
		} else {
			showToast(getString(R.string.edit_invalid_accept));
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_SHOW_EDITOR) {
			editorReturned(resultCode, data);
		}
	}
	
	private void accept() {
		if (currentAction == null) {
			showToast("No action selected!");
			return;
		}
		
		if (parameters == null) {
			showToast("Invalid parameters selected!");
			return;
		}
		
		// TODO: validate action / params
		
		Intent result = new Intent();
		
		result.putExtra(
				com.twofortyfouram.locale.Intent.EXTRA_BUNDLE,
				parameters);
		
		List<String> descriptions = new LinkedList<>();
		descriptions.add(getString(currentAction.getResourceId()));
		for (Parameter p : labelMap.keySet()) {
			descriptions.add(p.describe(this, parameters));
		}
		
		result.putExtra(
				com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB,
				StringUtils.join(descriptions, " - "));
		
		for (String key : parameters.keySet()) {
			log.info("{} = {}", key, parameters.get(key));
		}
		
		setResult(RESULT_OK, result);
		finish();
	}
	
	private void cancel() {
		setResult(RESULT_CANCELED);
		finish();
	}
	
	private boolean validate() {
		if (currentAction == null || parameters == null) {
			return false;
		}
		
		for (Parameter p : labelMap.keySet()) {
			if (!p.validate(this, parameters)) {
				log.info("Validation of {} failed", p.name());
				return false;
			} else {
				log.info("Validation of {} passed", p.name());
			}
		}
		
		return true;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.edit, menu);
		
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.edit_accept:
				if (validate()) {
					accept();
				} else {
					showToast(getString(R.string.edit_invalid_accept));
				}
				return true;
			case R.id.edit_cancel:
				cancel();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if (!validate()) {
			showToast(getString(R.string.edit_invalid_back));
			cancel();
			return;
		}
		
		accept();
	}
	
}

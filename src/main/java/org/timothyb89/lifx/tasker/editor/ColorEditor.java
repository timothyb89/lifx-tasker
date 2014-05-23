package org.timothyb89.lifx.tasker.editor;

import android.app.Activity;
import static android.app.Activity.RESULT_OK;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.timothyb89.lifx.tasker.EditActivity;
import org.timothyb89.lifx.tasker.R;

/**
 *
 * @author tim
 */
@EActivity(R.layout.activity_edit_color)
public class ColorEditor extends Activity {
	
	public static final String KEY_COLOR = "color";
	
	@ViewById(R.id.edit_color_picker)
	protected ColorPicker picker;
	
	@ViewById(R.id.edit_color_svbar)
	protected SVBar svBar;
	
	private String paramName;
	private Bundle parameters;
	
	@AfterViews
	protected void init() {
		picker.addSVBar(svBar);
		
		paramName = getIntent().getStringExtra(EditActivity.KEY_PARAM_NAME);
		parameters = getIntent().getBundleExtra(EditActivity.KEY_PARAMS);
		
		if (parameters.containsKey(KEY_COLOR)) {
			picker.setColor(parameters.getInt(KEY_COLOR));
		}
	}
	
	private void accept() {
		Intent result = new Intent();
		
		result.putExtra(EditActivity.KEY_PARAM_NAME, paramName);
		
		parameters.putInt(KEY_COLOR, picker.getColor());
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
		getMenuInflater().inflate(R.menu.edit_color, menu);
		
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.edit_color_accept:
				accept();
				return true;
			case R.id.edit_color_cancel:
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
	
}

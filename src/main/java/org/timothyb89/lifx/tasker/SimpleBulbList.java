package org.timothyb89.lifx.tasker;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.androidannotations.annotations.EActivity;
import org.timothyb89.lifx.gateway.Gateway;

/**
 *
 * @author tim
 */
@Slf4j
@EActivity(R.layout.activity_bulb_list)
public class SimpleBulbList extends Activity {
	
	private Map<Gateway, ListView> viewMap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		viewMap = new HashMap<>();
	}
	
}

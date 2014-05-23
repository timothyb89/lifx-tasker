package org.timothyb89.lifx.tasker.editor;

import android.content.Context;
import org.timothyb89.lifx.tasker.R;

/**
 *
 * @author tim
 */
public enum Action {
	
	POWER_ON(
			"Power On",
			R.string.action_power_on,
			Parameter.BULB_NAMES),
	
	POWER_OFF(
			"Power Off",
			R.string.action_power_off,
			Parameter.BULB_NAMES),
	
	POWER_TOGGLE(
			"Power Toggle",
			R.string.action_power_toggle,
			Parameter.BULB_NAMES),
	
	COLOR_SET(
			"Color Set",
			R.string.action_color_set,
			Parameter.BULB_NAMES, Parameter.COLOR),
	
	COLOR_PULSE(
			"Color Pulse",
			R.string.action_color_pulse,
			Parameter.BULB_NAMES, Parameter.COLOR);
	
	private String id;
	private int resourceId;
	private Parameter[] parameters;

	private Action(String id, int resourceId, Parameter... parameters) {
		this.id = id;
		this.resourceId = resourceId;
		this.parameters = parameters;
	}

	public String getId() {
		return id;
	}

	public int getResourceId() {
		return resourceId;
	}

	public Parameter[] getParameters() {
		return parameters;
	}
	
	public static Action getAction(String id) {
		for (Action a : values()) {
			if (id.equals(a.getId())) {
				return a;
			}
		}
		
		return null;
	}
	
	public static Action getAction(Context ctx, String realName) {
		for (Action a : values()) {
			if (realName.equals(ctx.getString(a.getResourceId()))) {
				return a;
			}
		}
		
		return null;
	}
	
}

package org.timothyb89.lifx.tasker.editor;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import org.timothyb89.lifx.tasker.R;

/**
 *
 * @author tim
 */
public enum Parameter {
	
	BULB_NAMES(R.string.edit_param_bulbs, BulbListEditor_.class) {

		@Override
		public boolean validate(Context ctx, Bundle params) {
			String key = BulbListEditor.KEY_BULBS;
			if (!params.containsKey(key)) {
				return false;
			}
			
			if (params.getStringArray(key).length == 0) {
				return false;
			}
			
			return true;
		}
		
		@Override
		public String describe(Context ctx, Bundle params) {
			return ctx.getString(
					R.string.edit_param_bulbs_desc,
					params.getStringArray(BulbListEditor.KEY_BULBS).length);
		}
		
	},
	
	COLOR(R.string.edit_param_color, ColorEditor_.class) {

		@Override
		public boolean validate(Context ctx, Bundle params) {
			return true;
		}
		
		@Override
		public String describe(Context ctx, Bundle params) {
			int color = params.getInt(ColorEditor.KEY_COLOR);
			
			return String.format("#%02X%02X%02X",
					Color.red(color),
					Color.green(color),
					Color.blue(color));
		}
		
	};
	
	private int resourceId;
	private Class editorClass;

	private Parameter(int resourceId, Class editorClass) {
		this.resourceId = resourceId;
		this.editorClass = editorClass;
	}

	public int getResourceId() {
		return resourceId;
	}

	public Class getEditorClass() {
		return editorClass;
	}
	
	public abstract boolean validate(Context ctx, Bundle params);
	
	public abstract String describe(Context ctx, Bundle params);
	
}

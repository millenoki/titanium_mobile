package org.appcelerator.titanium.animation;

import org.appcelerator.titanium.util.TiViewHelper;

import android.view.View;

import android.util.Property;

public class ScaleProperty extends Property<View, Float> {

	public ScaleProperty() {
		super(Float.class, "scale");
	}

	@Override
	public void set(View view, Float value) {
		TiViewHelper.setScale(view, value);
	}

	@Override
	public Float get(View view) {
		return view.getScaleX();
	}
	
}
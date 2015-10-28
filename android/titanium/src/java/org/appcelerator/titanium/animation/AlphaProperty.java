package org.appcelerator.titanium.animation;

import android.view.View;

import android.util.Property;


public class AlphaProperty extends Property<View, Float> {

	public AlphaProperty() {
		super(Float.class, "alpha");
	}

	@Override
	public void set(View view, Float value) {
	    view.setAlpha(value);
	}

	@Override
	public Float get(View view) {
		return view.getAlpha();
	}
}

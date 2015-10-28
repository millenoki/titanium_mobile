package org.appcelerator.titanium.animation;

import android.view.View;

import android.util.Property;


public class RotationProperty extends Property<View, Float> {

	public RotationProperty(String name) {
		super(Float.class, name);
	}

	@Override
	public void set(View view, Float value) {
		if (getName().equals("y"))
		    view.setRotationY(value);
		else
		    view.setRotationX(value);
	}

	@Override
	public Float get(View view) {
		if (getName().equals("y"))
			return view.getRotationY();
		else
			return view.getRotationX();
	}
	
}
package org.appcelerator.titanium.animation;

import org.appcelerator.titanium.util.TiViewHelper;

import android.view.View;

import android.util.Property;

public class PivotProperty extends Property<View, Float> {

	public PivotProperty(String name) {
		super(Float.class, name);
	}

	@Override
	public void set(View view, Float value) {
		if (getName().equals("y"))
			TiViewHelper.setPivotFloatY(view, value);
		else
			TiViewHelper.setPivotFloatX(view, value);
	}

	@Override
	public Float get(View view) {
		if (getName().equals("y"))
			return TiViewHelper.getPivotFloatY(view);
		else
			return TiViewHelper.getPivotFloatX(view);
	}
	
}
package org.appcelerator.titanium.animation;

import android.view.View;

import android.util.Property;

public class TranslationProperty extends Property<View, Float> {

	public TranslationProperty(String name) {
		super(Float.class, name);
	}

	@Override
	public void set(View view, Float value) {
		if (getName().equals("y"))
		    view.setTranslationY(value);
		else
		    view.setTranslationX(value);
	}

	@Override
	public Float get(View view) {
		if (getName().equals("y"))
			return view.getTranslationY();
		else
			return view.getTranslationX();
	}
}
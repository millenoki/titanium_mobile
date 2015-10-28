package org.appcelerator.titanium.animation;

import org.appcelerator.titanium.util.TiViewHelper;

import android.view.View;

import android.util.Property;

public class TranslationRelativeProperty extends Property<View, Float> {

	public TranslationRelativeProperty(String name) {
		super(Float.class, name);
	}

	@Override
	public void set(View view, Float value) {
		if (getName().equals("y"))
			TiViewHelper.setTranslationRelativeY(view, value);
		else
			TiViewHelper.setTranslationRelativeX(view, value);
	}

	@Override
	public Float get(View view) {
		if (getName().equals("y"))
			return TiViewHelper.getTranslationRelativeY(view);
		else
			return TiViewHelper.getTranslationRelativeX(view);
	}
	
}
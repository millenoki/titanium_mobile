package org.appcelerator.titanium.view;

import org.appcelerator.titanium.view.TiCompositeLayout.AnimationLayoutParams;

import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup.LayoutParams;

import android.util.Property;

public class AnimatedRectFractionProperty extends Property<View, Float> {

	public AnimatedRectFractionProperty() {
		super(Float.class, "AnimatedRectFraction");
	}

	@Override
	public void set(View view, Float value) {
		LayoutParams params = view.getLayoutParams();
		if (params instanceof AnimationLayoutParams) {
			((AnimationLayoutParams)params).animationFraction = value;
			view.setLayoutParams(params);
			ViewParent viewParent = view.getParent();
			if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
				((View) viewParent).postInvalidate();
			}
		}
	}

	@Override
	public Float get(View view) {
		LayoutParams params = view.getLayoutParams();
		if (params instanceof AnimationLayoutParams)
			return ((AnimationLayoutParams)params).animationFraction;
		return 0.0f;
	}
	
}

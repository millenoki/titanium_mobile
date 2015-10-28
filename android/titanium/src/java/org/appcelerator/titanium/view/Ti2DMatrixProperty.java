package org.appcelerator.titanium.view;

import org.appcelerator.titanium.util.AffineTransform;
import org.appcelerator.titanium.util.AffineTransform.DecomposedType;

import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup.LayoutParams;

import android.util.Property;

public class Ti2DMatrixProperty extends Property<View, Ti2DMatrix> {

	public Ti2DMatrixProperty() {
		super(Ti2DMatrix.class, "matrix");
	}

	@Override
    final public void set(View view, Ti2DMatrix value) {
		LayoutParams params = view.getLayoutParams();
		
		if (params instanceof FreeLayout.LayoutParams) {
			((FreeLayout.LayoutParams)params).matrix = value;
			view.setLayoutParams(params);
			ViewParent viewParent = view.getParent();
			if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
				((View) viewParent).postInvalidate();
			}
		}
		else {
			DecomposedType decompose = value.getAffineTransform(view).decompose();
			view.setTranslationX((float) decompose.translateX);
			view.setTranslationY((float) decompose.translateY);
			view.setRotation((float) (decompose.angle*180/Math.PI));
			view.setScaleX((float) decompose.scaleX);
			view.setScaleY((float) decompose.scaleY);
		}
    }

	@Override
	public Ti2DMatrix get(View view) {
		LayoutParams params = view.getLayoutParams();
		if (params instanceof FreeLayout.LayoutParams) {
			return  ((FreeLayout.LayoutParams)params).matrix;
		}
		else {
			DecomposedType decompose =new DecomposedType();
			decompose.translateX = view.getTranslationX();
			decompose.translateY = view.getTranslationY();
			decompose.angle = view.getRotation()*Math.PI / 180;
			decompose.scaleX = view.getScaleX();
			decompose.scaleY = view.getScaleY();
			return new Ti2DMatrix(new AffineTransform(decompose));
		}
	}
	
}
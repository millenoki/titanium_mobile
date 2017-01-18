package org.appcelerator.titanium.util;

import org.appcelerator.titanium.TiApplication;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

public class TiTypefaceSpan extends TypefaceSpan {
    private final Typeface newType;
    private final String fontFamily;

    public TiTypefaceSpan(String family, Typeface type) {
        super(family);
    	this.fontFamily = family;
        newType = type;
    }
    
    public TiTypefaceSpan(String family) {
        this(family, TiUIHelper.toTypeface(TiApplication.getInstance().getApplicationContext(), family));
    }

    @Override
    public void updateDrawState(TextPaint ds) {
    	if (newType != null) {
    		applyCustomTypeFace(ds, newType);
    	}
        super.updateDrawState(ds);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (newType != null) {
    		applyCustomTypeFace(paint, newType);
    	}
        super.updateMeasureState(paint);
    }

    private void applyCustomTypeFace(Paint paint, Typeface tf) {
//        int oldStyle;
        Typeface old = paint.getTypeface();
        if (tf == old) {
            return;
        }
//        if (old == null) {
//            oldStyle = 0;
//        } else {
//            oldStyle = old.getStyle();
//        }
//
//        int fake = oldStyle & ~tf.getStyle();
//        if ((fake & Typeface.BOLD) != 0) {
//            paint.setFakeBoldText(true);
//        }
//
//        if ((fake & Typeface.ITALIC) != 0) {
//            paint.setTextSkewX(-0.25f);
//        }

        paint.setTypeface(tf);
    }
}
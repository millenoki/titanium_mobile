/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiHtml;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TiUIProgressBar extends TiUIView {

	private TextView label;
	private ProgressBar progress;
	private LinearLayout view;
	
    private float value = 0;
    private float min = 0;
    private float max = 0;
    
    private float secondaryValue = 0;
    private float secondaryMin = 0;
    private float secondaryMax = 0;
	
	public TiUIProgressBar(final TiViewProxy proxy)
	{
		super(proxy);
		
		view = new LinearLayout(proxy.getActivity())
		{
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom)
			{
				super.onLayout(changed, left, top, right, bottom);
				TiUIHelper.firePostLayoutEvent(TiUIProgressBar.this);
			}
			
			@Override
			public boolean dispatchTouchEvent(MotionEvent event) {
				if (touchPassThrough == true)
					return false;
				return super.dispatchTouchEvent(event);
			}
		};
		view.setOrientation(LinearLayout.VERTICAL);
		label = new TextView(proxy.getActivity());
		label.setGravity(Gravity.TOP | Gravity.LEFT);
		label.setPadding(0, 0, 0, 0);
		label.setSingleLine(false);

		progress = new ProgressBar(proxy.getActivity(), null, android.R.attr.progressBarStyleHorizontal);
		progress.setIndeterminate(false);
		progress.setMax(1000);
		
		view.addView(label);
		view.addView(progress);
		
		setNativeView(view);
	}
	
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {

        switch (key) {
        case TiC.PROPERTY_MESSAGE:
            handleSetMessage(TiConvert.toString(newValue));
            break;
        case TiC.PROPERTY_VALUE:
            value = TiConvert.toFloat(newValue, 0);
            updateProgress();
            break;
        case TiC.PROPERTY_MIN:
            min = TiConvert.toFloat(newValue, 0);
            updateProgress();
            break;
        case TiC.PROPERTY_MAX:
            max = TiConvert.toFloat(newValue, 0);
            updateProgress();
            break;
        case "secondaryValue":
            secondaryValue = TiConvert.toFloat(newValue, 0);
            updateSecondaryProgress();
            break;
        case "secondaryMin":
            secondaryMin = TiConvert.toFloat(newValue, 0);
            updateSecondaryProgress();
            break;
        case "secondaryMax":
            secondaryMax = TiConvert.toFloat(newValue, 0);
            updateSecondaryProgress();
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
	
	private int convertRange(double min, double max, double value, int base)
	{
		return (int)Math.floor((value/(max - min))*base);
	}
	
	public void updateProgress()
	{
		progress.setProgress(convertRange(min, max, value, 1000));
	}
	public void updateSecondaryProgress()
    {
        progress.setSecondaryProgress(convertRange(secondaryMin, secondaryMax, secondaryValue, 1000));
    }
    
	
	public void handleSetMessage(String message)
	{
		label.setText(TiHtml.fromHtml(message));
		label.requestLayout();
	}
}

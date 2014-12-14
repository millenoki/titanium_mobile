/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.picker;

import java.util.Calendar;
import java.util.Date;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

public class TiUITimePicker extends TiUIView
	implements OnTimeChangedListener
{
	private static final String TAG = "TiUITimePicker";
	private boolean suppressChangeEvent = false;
	
	protected Date minDate, maxDate;
	protected int minuteInterval;
	
	public TiUITimePicker(TiViewProxy proxy)
	{
		super(proxy);
	}
	public TiUITimePicker(final TiViewProxy proxy, Activity activity)
	{
		this(proxy);
		Log.d(TAG, "Creating a time picker", Log.DEBUG_MODE);
		
		TimePicker picker = new TimePicker(activity)
		{
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom)
			{
				super.onLayout(changed, left, top, right, bottom);
				TiUIHelper.firePostLayoutEvent(TiUITimePicker.this);
			}
		};
		picker.setIs24HourView(false);
		picker.setOnTimeChangedListener(this);
		setNativeView(picker);
	}
	
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_VALUE:
            setValue(newValue);
            break;
        case TiC.PROPERTY_MIN_DATE:
        {
            this.minDate = TiConvert.toDate(newValue);
            break;
        }
        case TiC.PROPERTY_MAX_DATE:
        {
            this.maxDate = TiConvert.toDate(newValue);
            break;
        }
        case "minuteInterval":
            int mi = TiConvert.toInt(newValue, 0);
            if (mi >= 1 && mi <= 30 && mi % 60 == 0) {
                this.minuteInterval = mi; 
            }
            break;
        case "format24":
            ((TimePicker) getNativeView()).setIs24HourView(TiConvert.toBoolean(newValue, false));
            break;

        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
	
	@Override
	public void processProperties(KrollDict d) {
        ((TimePicker) getNativeView()).setIs24HourView(false);
		super.processProperties(d);
        
        if (!d.containsKey(TiC.PROPERTY_VALUE)) {
            Calendar calendar = Calendar.getInstance();
            proxy.setProperty(TiC.PROPERTY_VALUE, calendar.getTime());
        }
        
        //iPhone ignores both values if max <= min
        if (minDate != null && maxDate != null) {
            if (maxDate.compareTo(minDate) <= 0) {
                Log.w(TAG, "maxDate is less or equal minDate, ignoring both settings.");
                minDate = null;
                maxDate = null;
            }   
        }
	}
	
	public void setValue(long value)
	{
		setValue(value, false);
	}
	
	public void setValue(long value, boolean suppressEvent)
	{
	    
		TimePicker picker = (TimePicker) getNativeView();
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(value);
		
		// This causes two events to fire.
		suppressChangeEvent = true;
		picker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
		suppressChangeEvent = suppressEvent || !((TiViewProxy) proxy).viewRealised();
		picker.setCurrentMinute(calendar.get(Calendar.MINUTE));
		suppressChangeEvent = false;
	}
	
	public void setValue(Date value, boolean suppressEvent) {
        long millis = (value != null)?value.getTime():Calendar.getInstance().getTimeInMillis();
        setValue(millis, suppressEvent);
    }
    
    public void setValue(Object value)
    {
        setValue(TiConvert.toDate(value), false);
    }

	@Override
	public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
		calendar.set(Calendar.MINUTE, minute);
		if (!suppressChangeEvent && hasListeners(TiC.EVENT_CHANGE)) {
			KrollDict data = new KrollDict();
			data.put("value", calendar.getTime());
			fireEvent(TiC.EVENT_CHANGE, data, false, false);		
		}
		// Make sure .value is readable by user
		proxy.setProperty("value", calendar.getTime());
	}
}

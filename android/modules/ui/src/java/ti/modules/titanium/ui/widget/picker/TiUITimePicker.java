/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.picker;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.view.View;
import android.widget.TimePicker;
import android.widget.TimePicker.OnTimeChangedListener;

public class TiUITimePicker extends TiUIView
	implements OnTimeChangedListener, TiUIPickerInterface
{
	private static final String TAG = "TiUITimePicker";
	private boolean suppressChangeEvent = false;
	
	protected Date minDate, maxDate;
	protected int minuteInterval;

	private static int id_am = 0;
	private static int id_pm = 0;
	
	public TiUITimePicker(TiViewProxy proxy)
	{
		super(proxy);
	}
	public TiUITimePicker(final TiViewProxy proxy, Activity activity)
	{
		this(proxy);
		Log.d(TAG, "Creating a time picker", Log.DEBUG_MODE);
		
		final TimePicker picker;
		// If it is not API Level 21 (Android 5.0), create picker normally.
		// If not, it will inflate a spinner picker to address a bug.
		if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP) {
			picker = new TimePicker(activity)
			{
				@Override
				protected void onLayout(boolean changed, int left, int top, int right, int bottom)
				{
					super.onLayout(changed, left, top, right, bottom);
                    if (changed) {
                        TiUIHelper.firePostLayoutEvent(TiUITimePicker.this);
                    }
				}
			};

			// TIMOB-8430: https://issuetracker.google.com/issues/36931448
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
				Resources resources = TiApplication.getInstance().getResources();
				if (id_am == 0) {
					id_am = resources.getIdentifier("android:id/am_label", "drawable", "android.widget.TimePicker");
				}
				if (id_pm == 0) {
					id_pm = resources.getIdentifier("android:id/pm_label", "drawable", "android.widget.TimePicker");
				}
				View am = (View) picker.findViewById(id_am);
				View pm = (View) picker.findViewById(id_pm);
				View.OnClickListener listener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if (Build.VERSION.SDK_INT >= 23) {
							picker.setHour((picker.getHour() + 12) % 24);
						} else {
							picker.setCurrentHour((picker.getCurrentHour() + 12) % 24);
						}
					}
				};
				if (am != null) {
					am.setOnClickListener(listener);
				}
				if (pm != null) {
					pm.setOnClickListener(listener);
				}
			}

		} else {
			// A bug where PickerCalendarDelegate does not send events to the
			// listener on API Level 21 (Android 5.0) for TIMOB-19192
			// https://code.google.com/p/android/issues/detail?id=147657
			// Work around is to use spinner view instead of calendar view in
			// in Android 5.0
			int timePickerSpinner;
			try {
				timePickerSpinner = TiRHelper.getResource("layout.titanium_ui_time_picker_spinner");
			} catch (ResourceNotFoundException e) {
				if (Log.isDebugModeEnabled()) {
					Log.e(TAG, "XML resources could not be found!!!");
				}
				return;
			}
            picker = (TimePicker) activity.getLayoutInflater().inflate(timePickerSpinner, null);
		};
		picker.setIs24HourView(false);
		picker.setOnTimeChangedListener(this);
		
		setNativeView(picker);
	}
	
	@Override
	public void release() {
	    updateValue();
	    super.release();
	}
	
	private TimePicker getPicker() {
	    return (TimePicker) nativeView;
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
	public void processProperties(HashMap d) {
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
	    updateValue();
	}
	
	private void updateValue() {
        int hours = getPicker().getCurrentHour();
        int minutes = getPicker().getCurrentMinute();
        Date currentDate = (Date) proxy.getProperty(TiC.PROPERTY_VALUE);
        boolean needsUpdate = currentDate == null;
        Calendar calendar = Calendar.getInstance();
        if (currentDate != null) {
            calendar.setTime(currentDate);
            needsUpdate = calendar.get(Calendar.MINUTE) != minutes ||
                    calendar.get(Calendar.HOUR_OF_DAY) != hours;
        }
        if (needsUpdate) {
            calendar.set(Calendar.HOUR_OF_DAY, hours);
            calendar.set(Calendar.MINUTE, minutes);
            if (!suppressChangeEvent && hasListeners(TiC.EVENT_CHANGE)) {
                KrollDict data = new KrollDict();
                data.put(TiC.PROPERTY_VALUE, calendar.getTime());
                fireEvent(TiC.EVENT_CHANGE, data, false, false);        
            }
            // Make sure .value is readable by user
            proxy.setProperty(TiC.PROPERTY_VALUE, calendar.getTime());
        }
	}
	
    @Override
    public Object getValue() {
        updateValue();
        return proxy.getProperty(TiC.PROPERTY_VALUE);
    }
}

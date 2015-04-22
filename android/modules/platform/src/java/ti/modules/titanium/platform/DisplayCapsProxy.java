/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.platform;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;

import android.util.DisplayMetrics;
import android.view.Display;

@Kroll.proxy(parentModule=PlatformModule.class)
public class DisplayCapsProxy extends KrollProxy
{
	private DisplayMetrics dm = null;
	private boolean sizeSet = false;
	private SoftReference<Display> softDisplay;
	int realWidth;
    int realHeight;

	public DisplayCapsProxy()
	{
		super();
	}

	public DisplayCapsProxy(TiContext tiContext)
	{
		this();
	}
	
	@SuppressWarnings("deprecation")
    private void querySize() {
	    if (sizeSet == false) {
	        if (TiC.JELLY_BEAN_MR1_OR_GREATER){
	            //new pleasant way to get real metrics
	            DisplayMetrics realMetrics = new DisplayMetrics();
	            getDisplay().getRealMetrics(realMetrics);
	            realWidth = realMetrics.widthPixels;
	            realHeight = realMetrics.heightPixels;

	        } else if (TiC.ICS_OR_GREATER) {
	            //reflection for this weird in-between time
                Display display = getDisplay();
	            try {
	                Method mGetRawH = Display.class.getMethod("getRawHeight");
	                Method mGetRawW = Display.class.getMethod("getRawWidth");
	                realWidth = (Integer) mGetRawW.invoke(display);
	                realHeight = (Integer) mGetRawH.invoke(display);
	            } catch (Exception e) {
	                //this may not be 100% accurate, but it's all we've got
	                realWidth = display.getWidth();
	                realHeight = display.getHeight();
	            }

	        } else {
                Display display = getDisplay();
	            //This should be close, as lower API devices should not have window navigation bars
	            realWidth = display.getWidth();
	            realHeight = display.getHeight();
	        }
	        sizeSet = true;
	    }
	}

	private Display getDisplay() {
		if (softDisplay == null || softDisplay.get() == null) {
			// we only need the window manager so it doesn't matter if the root or current activity is used
			// for accessing it
			softDisplay = new SoftReference<Display>(TiApplication.getAppRootOrCurrentActivity().getWindowManager().getDefaultDisplay());
		}
		return softDisplay.get();
	}
	
	private DisplayMetrics getDisplayMetrics() {
	    if (dm == null) {
	        dm = new DisplayMetrics();
	        getDisplay().getMetrics(dm);
	    }
	    return dm;
	}

	@Kroll.getProperty @Kroll.method
	public int getPlatformWidth() {
	    querySize();
	    return realWidth;
	}

	@Kroll.getProperty @Kroll.method
	public int getPlatformHeight() {
	    querySize();
        return realHeight;
	}

	@Kroll.getProperty @Kroll.method
	public String getDensity() {
		return TiApplication.getAppDensityString();
	}

	@Kroll.getProperty @Kroll.method
	public float getDpi() {
		return getDisplayMetrics().densityDpi;
	}

	@Kroll.getProperty @Kroll.method
	public float getXdpi() {
		return getDisplayMetrics().xdpi;
	}

	@Kroll.getProperty @Kroll.method
	public float getYdpi() {
		return getDisplayMetrics().ydpi;
	}

	@Kroll.getProperty @Kroll.method
	public float getLogicalDensityFactor() {
        return TiApplication.getAppDensity();
	}

	@Override
	public String getApiName()
	{
		return "Ti.Platform.DisplayCaps";
	}
}

/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.util.ArrayList;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.util.TiActivityHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.util.TiUIHelper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

@SuppressLint("InlinedApi")
@Kroll.proxy(propertyAccessors = {
		TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED,
        TiC.PROPERTY_DISPLAY_HOME_AS_UP,
        TiC.PROPERTY_BACKGROUND_COLOR,
        TiC.PROPERTY_BACKGROUND_IMAGE,
        TiC.PROPERTY_BACKGROUND_GRADIENT,
        TiC.PROPERTY_BACKGROUND_OPACITY,
        TiC.PROPERTY_LOGO,
        TiC.PROPERTY_UP_INDICATOR,
        TiC.PROPERTY_HOME_AS_UP_INDICATOR,
		TiC.PROPERTY_ICON
})

public class ActionBarProxy extends AnimatableReusableProxy
{
    private static final boolean JELLY_BEAN_MR1_OR_GREATER = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);
	private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;


	private static final String TAG = "ActionBarProxy";

	private ActionBar actionBar;
	private Drawable themeBackgroundDrawable;
	private Drawable themeIconDrawable = null;
    private boolean showTitleEnabled = true;
	private int defaultColor = 0;
	private boolean customBackgroundSet = false;
    private Drawable mActionBarBackgroundDrawable;
    private int backgroundAlpha = 255;
    
    private Drawable.Callback mDrawableCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            actionBar.setBackgroundDrawable(who);
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
        }
    };
    
    private void setActionBarDrawable(final Drawable drawable) {
        if (drawable == mActionBarBackgroundDrawable ) {
            return;
        }
        if (mActionBarBackgroundDrawable != null) {
            if (!JELLY_BEAN_MR1_OR_GREATER) {
                mActionBarBackgroundDrawable.setCallback(null);
            }
            mActionBarBackgroundDrawable = null;
        }
        if (drawable == themeBackgroundDrawable) {
            actionBar.setBackgroundDrawable(themeBackgroundDrawable);
            return;
        }
        mActionBarBackgroundDrawable = drawable;
        if (mActionBarBackgroundDrawable != null) {
            if (!JELLY_BEAN_MR1_OR_GREATER) {
                mActionBarBackgroundDrawable.setCallback(mDrawableCallback);
            }
            mActionBarBackgroundDrawable.setAlpha(backgroundAlpha);
        }
        actionBar.setBackgroundDrawable(mActionBarBackgroundDrawable);
    }

	public ActionBarProxy(TiBaseActivity activity)
	{
		super();
		setActivity(activity);
        actionBar = TiActivityHelper.getActionBar(activity);

//		try {
//		    actionBar = activity.getSupportActionBar();
//		    //trick to actually know if the internal action bar exists
//	        actionBar.isShowing();
//        } catch (NullPointerException e) {
//            //no internal action bar
//            actionBar = null;
//        }
		int resourceId = 0;
		try {
		    TypedValue typedValue = new TypedValue(); 
            int id = TiRHelper.getResource("android.support.v7.appcompat.R$", "attr.colorPrimary");
		    activity.getTheme().resolveAttribute(id, typedValue, true);
		    defaultColor = typedValue.data;
		    if (defaultColor == 0) {
		        //non material
	            resourceId = TiRHelper.getResource("android.support.v7.appcompat.R$", "id.action_context_bar");
	            if (resourceId > 0) {
	                View view = activity.getWindow().getDecorView().findViewById(resourceId);
	                if (view != null) {
	                    themeBackgroundDrawable = view.getBackground();
	                }
	            }
	            themeIconDrawable = getActionBarIcon(activity);
		    }
        } catch (ResourceNotFoundException e) {
        }
	}
	@Override
    public void release() {
	    actionBar = null;
        super.release();
    }
	   
	protected static TypedArray obtainStyledAttrsFromThemeAttr(Context context,
            int[] styleAttrs) throws ResourceNotFoundException {
        // Need to get resource id of style pointed to from the theme attr
        TypedValue outValue = new TypedValue();
    	int resourceId = TiRHelper.getResource("android.support.v7.appcompat.R$", "attr.actionBarStyle");
       context.getTheme().resolveAttribute(resourceId, outValue, true);
        final int styleResId =  outValue.resourceId;

        return context.obtainStyledAttributes(styleResId, styleAttrs);
    }
	
	protected Drawable getActionBarBackground(Context context) {
        TypedArray values = null;
        try {
            int resourceId = TiRHelper.getResource("android.support.v7.appcompat.R$", "attr.background");
            int[] attrs = {resourceId};
            values = context.getTheme().obtainStyledAttributes(attrs);
            return values.getDrawable(0);
        } catch (ResourceNotFoundException e) {
            return null;
        } finally {
            if (values != null) {
                values.recycle();
            }
        }
    }

    public static int getActionBarSize(Context context) {
        TypedArray values = null;
        try {
            int resourceId = TiRHelper.getResource("android.support.v7.appcompat.R$", "attr.actionBarSize");
            int[] attrs = {resourceId};
            values = context.getTheme().obtainStyledAttributes(attrs);
            return values.getDimensionPixelSize(0, 0);
        } catch (ResourceNotFoundException e) {
            return 0;
        } finally {
            if (values != null) {
                values.recycle();
            }
        }
    }
	

	protected Drawable getActionBarIcon(Context context) {
        int[] android_styleable_ActionBar = {android.R.attr.icon};

        // Now get the action bar style values...
        TypedArray abStyle = null;
        try {
        	abStyle = obtainStyledAttrsFromThemeAttr(context, android_styleable_ActionBar);
       	int count = abStyle.getIndexCount();
        	if (count > 0) {
	            return abStyle.getDrawable(0);
        	}
        	return context.getApplicationInfo().loadIcon(context.getPackageManager()); 
        } catch (ResourceNotFoundException e) {
			return null;
		} finally {
            if (abStyle != null) abStyle.recycle();
        }
    }

	private void setDisplayHomeAsUp(final boolean showHomeAsUp)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setDisplayHomeAsUp(showHomeAsUp);
                }
            });
            return;
        }
        actionBar.setDisplayHomeAsUpEnabled(showHomeAsUp);
	}

	public void setNavigationMode(final int navigationMode)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setNavigationMode(navigationMode);
                }
            });
            return;
        }
        actionBar.setNavigationMode(navigationMode);

	}

	public void setBackgroundImage(final Object value)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBackgroundImage(value);
                }
            });
            return;
        }
        actionBar.setDisplayShowTitleEnabled(!showTitleEnabled);
        actionBar.setDisplayShowTitleEnabled(showTitleEnabled);
        
        if (value instanceof Drawable) {
            
        } else {
            setActionBarDrawable((Drawable) value);
        }
        setActionBarDrawable(TiUIHelper.getResourceDrawable(value));
        customBackgroundSet = (mActionBarBackgroundDrawable != null);
	}
	
	public void setBackgroundColor(final int color)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
	    if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBackgroundColor(color);
                }
            });
            return;
        }
        
        resetTitleEnabled();
        setActionBarDrawable(new ColorDrawable(color));
        customBackgroundSet = (mActionBarBackgroundDrawable != null) && color != defaultColor;
	}
	
	public void setBackgroundGradient(final KrollDict gradDict)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBackgroundGradient(gradDict);
                }
            });
            return;
        }
        resetTitleEnabled();
        setActionBarDrawable(TiUIHelper.buildGradientDrawable(gradDict));
        customBackgroundSet = (mActionBarBackgroundDrawable != null);
	}
	
	public void setBackgroundOpacity(final float alpha)
    {
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBackgroundOpacity(alpha);
                }
            });
            return;
        }
        backgroundAlpha = (int) (alpha*255.0f);
        if (mActionBarBackgroundDrawable == null) {
            setBackgroundColor(defaultColor);
        } else {
            mActionBarBackgroundDrawable.setAlpha(backgroundAlpha);
        }
    }

	public void setTitle(final String title)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setTitle(title);
                }
            });
            return;
        }
        actionBar.setTitle(title);
	}
	
	public void setCustomView(final Object view, final boolean shouldHold)
    {
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setCustomView(view, shouldHold);
                }
            });
            return;
        }
        KrollProxy viewProxy = null;
        //the customView can come from the window titleView. In that case don't hold it
        // as the window already did it.
        if (shouldHold) {
            viewProxy = addProxyToHold(view, "customView");
        } else if (view instanceof KrollProxy) {
            viewProxy = (KrollProxy) view;
            viewProxy.setActivity(getActivity());
        }
        if (viewProxy instanceof TiViewProxy) {
            viewProxy.setActivity(getActivity());
            View viewToAdd = ((TiViewProxy) viewProxy).getOrCreateView().getOuterView();
            if (actionBar.getCustomView() != viewToAdd) {
                TiUIHelper.removeViewFromSuperView((TiViewProxy) viewProxy);
                actionBar.setCustomView(viewToAdd);
                showTitleEnabled = false;
                actionBar.setDisplayShowCustomEnabled(true);
            }
        } else {
            actionBar.setCustomView(null);
            showTitleEnabled = true;
            actionBar.setDisplayShowCustomEnabled(false);
        }
        actionBar.setDisplayShowTitleEnabled(showTitleEnabled);
    }

	public void setSubtitle(final String subTitle)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setSubtitle(subTitle);
                }
            });
            return;
        }
        showTitleEnabled = true;
        actionBar.setDisplayShowTitleEnabled(showTitleEnabled);
        actionBar.setSubtitle(subTitle);
	}
	
	public void setDisplayShowHomeEnabled(final boolean show) {
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setDisplayShowHomeEnabled(show);
                }
            });
            return;
        }
        actionBar.setDisplayShowHomeEnabled(show);
	}
	
	public void setDisplayShowTitleEnabled(final boolean show) {
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setDisplayShowTitleEnabled(show);
                }
            });
            return;
        }
		actionBar.setDisplayShowTitleEnabled(show);
		showTitleEnabled = show;
	}
	

    public void setLogo(final Object value)
    {
        if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setLogo(value);
                }
            });
            return;
        }
        Drawable logo = TiUIHelper.getResourceDrawable(value);
        actionBar.setLogo(logo);
    }

    public void setIcon(final Object value)
    {
        if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setIcon(value);
                }
            });
            return;
        }
        Drawable icon = TiUIHelper.getResourceDrawable(value);
        if (icon == null) {
            actionBar.setIcon(themeIconDrawable);
        } else {
            actionBar.setIcon(icon);
        } 
    }
    
    
    private void setUpIndicator(final Object value) {
        if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setUpIndicator(value);
                }
            });
            return;
        }
        Drawable drawable = TiUIHelper.getResourceDrawable(value);
        ImageView view = getUpIndicatorView();
        if (view != null) {
            if (drawable != null) {
                view.setImageDrawable(drawable);
            }
            else {
                view.setImageDrawable(view.getDrawable());
            }
        }
    }
    
    private void setHomeAsUpIndicator(final Object value) {
        if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setUpIndicator(value);
                }
            });
            return;
        }
        Drawable drawable = TiUIHelper.getResourceDrawable(value);
        actionBar.setHomeAsUpIndicator(drawable);
    }


    @Kroll.method
    @Kroll.getProperty
    public double getHeight() {
        if (actionBar == null) {
            return 0;
        }
        TiDimension nativeHeight = new TiDimension(actionBar.getHeight(), TiDimension.TYPE_HEIGHT);
        return nativeHeight.getAsDefault();
    }
    
	@SuppressWarnings("deprecation")
    public int getNavigationMode()
	{
		if (actionBar == null) {
			return 0;
		}
		return (int) actionBar.getNavigationMode();
	}

	@Kroll.method
	public void show()
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    show();
                }
            });
            return;
        }
        actionBar.show();
	}

	@Kroll.method
	public void hide()
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            });
            return;
        }
        actionBar.hide();
	}

	
	private void resetTitleEnabled() {
        actionBar.setDisplayShowTitleEnabled(!showTitleEnabled);
        actionBar.setDisplayShowTitleEnabled(showTitleEnabled);
	}
	


	private void activateHomeButton(final boolean value)
	{
	    if (actionBar == null) {
            Log.w(TAG, "ActionBar is not enabled");
            return;
        }
        if (!TiApplication.isUIThread()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activateHomeButton(value);
                }
            });
            return;
        }

        actionBar.setHomeButtonEnabled(value);
	}
	

	private Drawable getDrawable(Object value)
	{
	    return TiUIHelper.getResourceDrawable(value);
	}

	private static final ArrayList<String> KEY_SEQUENCE;
    static{
      ArrayList<String> tmp = new ArrayList<String>();
      tmp.add(TiC.PROPERTY_DISPLAY_HOME_TITLE_ENABLED);
      tmp.add(TiC.PROPERTY_DISPLAY_SHOW_HOME_ENABLED);
      tmp.add(TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED);
      tmp.add(TiC.PROPERTY_DISPLAY_HOME_AS_UP);
      KEY_SEQUENCE = tmp;
    }
    @Override
    protected ArrayList<String> keySequence() {
        return KEY_SEQUENCE;
    }
    
    private static final HashMap<String, String> BAR_PROPERTIES_MAP;
    static{
        HashMap<String, String> tmp = new HashMap<String, String>();
        tmp.put(TiC.PROPERTY_BAR_COLOR, TiC.PROPERTY_BACKGROUND_COLOR);
        tmp.put(TiC.PROPERTY_BAR_IMAGE, TiC.PROPERTY_BACKGROUND_IMAGE);
        tmp.put(TiC.PROPERTY_BAR_OPACITY, TiC.PROPERTY_BACKGROUND_OPACITY);
        tmp.put(TiC.PROPERTY_BAR_GRADIENT, TiC.PROPERTY_BACKGROUND_GRADIENT);
        BAR_PROPERTIES_MAP = tmp;
    }
    public static HashMap<String, String> propsToReplace() {
        return BAR_PROPERTIES_MAP;
    }
    
    private static final ArrayList<String> BAR_PROPERTIES;
    static{
      ArrayList<String> tmp = new ArrayList<String>();
      tmp.add(TiC.PROPERTY_BAR_COLOR);
      tmp.add(TiC.PROPERTY_BAR_IMAGE);
      tmp.add(TiC.PROPERTY_BAR_OPACITY);
      tmp.add(TiC.PROPERTY_BAR_GRADIENT);
      tmp.add(TiC.PROPERTY_LOGO);
      tmp.add(TiC.PROPERTY_ICON);
      tmp.add(TiC.PROPERTY_UP_INDICATOR);
      tmp.add(TiC.PROPERTY_HOME_AS_UP_INDICATOR);
      tmp.add(TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED);
      tmp.add(TiC.PROPERTY_DISPLAY_HOME_AS_UP);
      tmp.add(TiC.PROPERTY_DISPLAY_HOME_TITLE_ENABLED);
      tmp.add(TiC.PROPERTY_DISPLAY_SHOW_HOME_ENABLED);
      tmp.add(TiC.PROPERTY_TITLE);
      tmp.add(TiC.PROPERTY_SUBTITLE);
      tmp.add(TiC.PROPERTY_TITLE_VIEW);
      tmp.add(TiC.PROPERTY_CUSTOM_VIEW);
      BAR_PROPERTIES = tmp;
    }
    public static ArrayList<String> windowProps() {
        return BAR_PROPERTIES;
    }

	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED:
            activateHomeButton(newValue instanceof KrollFunction);
            break;
        case TiC.PROPERTY_DISPLAY_HOME_AS_UP:
            setDisplayHomeAsUp(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_DISPLAY_HOME_TITLE_ENABLED:
            setDisplayShowTitleEnabled(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_DISPLAY_SHOW_HOME_ENABLED:
            setDisplayShowHomeEnabled(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_BACKGROUND_IMAGE:
            setBackgroundImage(TiConvert.toString(newValue));
            break;
        case TiC.PROPERTY_BACKGROUND_COLOR:
            setBackgroundColor(TiConvert.toColor(newValue));
            break;
        case TiC.PROPERTY_BACKGROUND_GRADIENT:
            setBackgroundGradient(TiConvert.toKrollDict(newValue));
            break;
        case TiC.PROPERTY_BACKGROUND_OPACITY:
            setBackgroundOpacity(TiConvert.toFloat(newValue, 1.0f));
            break;
        case TiC.PROPERTY_CUSTOM_VIEW:
            setCustomView(newValue, true);
            break;
        case TiC.PROPERTY_TITLE_VIEW:
            setCustomView(newValue, false);
            break;
        case TiC.PROPERTY_LOGO:
            setLogo(newValue);
            break;
        case TiC.PROPERTY_TITLE:
            setTitle(TiConvert.toString(newValue));
            break;
        case TiC.PROPERTY_SUBTITLE:
            setSubtitle(TiConvert.toString(newValue));
            break;
        case TiC.PROPERTY_UP_INDICATOR:
            setUpIndicator(newValue);
            break;
        case TiC.PROPERTY_ICON:
            setIcon(newValue);
            break;
        case TiC.PROPERTY_HOME_AS_UP_INDICATOR:
            setHomeAsUpIndicator(newValue);
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
    
    @Override
    protected void didProcessProperties() {
        super.didProcessProperties();
        if (customBackgroundSet && properties.get(TiC.PROPERTY_BACKGROUND_COLOR) == null && 
                properties.get(TiC.PROPERTY_BACKGROUND_IMAGE) == null &&  
                properties.get(TiC.PROPERTY_BACKGROUND_GRADIENT) == null )
        {
            if (defaultColor != 0) {
                setBackgroundColor(defaultColor);
            } else {
                setBackgroundImage(themeBackgroundDrawable);
            }
            
            customBackgroundSet = false;
        }
    }

	@Override
	public String getApiName()
	{
		return "Ti.Android.ActionBar";
	}
	
	private ImageView getUpIndicatorView() {
	    String appPackage = getActivity().getPackageName();

        // Attempt to find AppCompat up indicator
        final int homeId = getActivity().getResources().getIdentifier("home", "id", appPackage);
        View v = getActivity().findViewById(homeId);
        if (v != null) {
            ViewGroup parent = (ViewGroup) v.getParent();
            final int upId = getActivity().getResources().getIdentifier("up", "id", appPackage);
            if (parent != null) {
                return (ImageView) parent.findViewById(upId);
            }
        }
        return null;
	}

}

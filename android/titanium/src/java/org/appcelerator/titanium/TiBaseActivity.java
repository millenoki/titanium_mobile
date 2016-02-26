/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;
import org.appcelerator.titanium.TiLifecycle.OnWindowFocusChangedEvent;
import org.appcelerator.titanium.TiLifecycle.interceptOnBackPressedEvent;
import org.appcelerator.titanium.TiLifecycle.interceptOnHomePressedEvent;
import org.appcelerator.titanium.TiLifecycle.OnActivityResultEvent;
import org.appcelerator.titanium.TiLifecycle.OnInstanceStateEvent;
import org.appcelerator.titanium.TiLifecycle.OnCreateOptionsMenuEvent;
import org.appcelerator.titanium.TiLifecycle.OnPrepareOptionsMenuEvent;
import org.appcelerator.titanium.proxy.ActionBarProxy;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.IntentProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.AndroidBug5497Workaround;
import org.appcelerator.titanium.util.TiActivityHelper;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiActivitySupportHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiMenuSupport;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiWeakList;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import com.appcelerator.analytics.APSAnalytics;

/**
 * The base class for all non tab Titanium activities. To learn more about Activities, see the
 * <a href="http://developer.android.com/reference/android/app/Activity.html">Android Activity documentation</a>.
 */
public abstract class TiBaseActivity extends AppCompatActivity
	implements TiActivitySupport/*, ITiWindowHandler*/
{
    
    public static interface PermissionCallback {
        public void onRequestPermissionsResult(String permissions[], int[] grantResults);
    }
    
    public static  class KrollPermissionCallback {
        public final KrollObject krollContext;
        public final KrollFunction krollCallback;
        public KrollPermissionCallback(KrollObject krollContext, KrollFunction krollCallback) {
            this.krollContext = krollContext;
            this.krollCallback = krollCallback;
        }
        public void onRequestPermissionsResult(String permissions[], int[] grantResults) {
            if (krollCallback == null) {
                return;
            }
            boolean granted = true;
            for (int i = 0; i < grantResults.length; ++i) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                KrollDict response = new KrollDict();
                response.putCodeAndMessage(0, null);
                krollCallback.callAsync(krollContext, response);
            } else {
                KrollDict response = new KrollDict();
                response.putCodeAndMessage(-1, "One or more permission(s) were denied");
                krollCallback.callAsync(krollContext, response);
            }
        }
    }
    
    
	private static final String TAG = "TiBaseActivity";

	private static OrientationChangedListener orientationChangedListener = null;
	private static OrientationEventListener orientationListener;

	private boolean onDestroyFired = false;
	private int originalOrientationMode = -1;
	private boolean inForeground = false; // Indicates whether this activity is in foreground or not.
	private TiWeakList<OnLifecycleEvent> lifecycleListeners = new TiWeakList<OnLifecycleEvent>();
	private TiWeakList<OnWindowFocusChangedEvent> windowFocusChangedListeners = new TiWeakList<OnWindowFocusChangedEvent>();
	protected TiWeakList<interceptOnBackPressedEvent> interceptOnBackPressedListeners = new TiWeakList<interceptOnBackPressedEvent>();
	protected TiWeakList<interceptOnHomePressedEvent> interceptOnHomePressedListeners = new TiWeakList<interceptOnHomePressedEvent>();
	private TiWeakList<OnInstanceStateEvent> instanceStateListeners = new TiWeakList<OnInstanceStateEvent>();
	private TiWeakList<OnActivityResultEvent> onActivityResultListeners = new TiWeakList<OnActivityResultEvent>();
	private TiWeakList<OnCreateOptionsMenuEvent>  onCreateOptionsMenuListeners = new TiWeakList<OnCreateOptionsMenuEvent>();
	private TiWeakList<OnPrepareOptionsMenuEvent> onPrepareOptionsMenuListeners = new TiWeakList<OnPrepareOptionsMenuEvent>();
    protected TiWeakList<OrientationChangedListener> orientationChangedListeners = null;
//	private APSAnalytics analytics = APSAnalytics.getInstance();

	private static HashMap<Integer, ArrayList<Object>> sPermissionCallback = new HashMap();
    protected ViewGroup layout;
    protected TiCompositeLayout contentView;
	protected TiActivitySupportHelper supportHelper;
	protected int supportHelperId = -1;
	protected TiWindowProxy window;
	protected TiViewProxy view;
	protected ActivityProxy activityProxy;
	protected TiWeakList<ConfigurationChangedListener> configChangedListeners = new TiWeakList<ConfigurationChangedListener>();
	protected int orientationDegrees;
	protected TiMenuSupport menuHelper;
	protected Messenger messenger;
	protected int msgActivityCreatedId = -1;
	protected int msgId = -1;
	protected static int previousOrientation = -1;
	//Storing the activity's dialogs and their persistence
	private CopyOnWriteArrayList<DialogWrapper> dialogs = new CopyOnWriteArrayList<DialogWrapper>();
	private Stack<TiWindowProxy> windowStack = new Stack<TiWindowProxy>();

	public boolean isResumed = false;
	
	private boolean isPaused = false;
	
	private boolean fullscreen = false;
	private boolean defaultFullscreen = false;
	private boolean navBarHidden = false;
//	private boolean defaultNavBarHidden = false;
	private int defaultSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN |  WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
	private int softInputMode = defaultSoftInputMode;
	private boolean mReadyToQueryActionBar = false;

	private boolean overridenLayout;	

	public class DialogWrapper {
		boolean isPersistent;
		Dialog dialog;

		WeakReference<TiBaseActivity> dialogActivity;

		public DialogWrapper(Dialog d, boolean persistent, WeakReference<TiBaseActivity> activity) {
			isPersistent = persistent;
			dialog = d;
			dialogActivity = activity;
		}

		public TiBaseActivity getActivity()
		{
			if (dialogActivity == null) {
				return null;
			} else {
				return dialogActivity.get();
			}
		}

		public void setActivity(WeakReference<TiBaseActivity> da)
		{
			dialogActivity = da;
		}

		public Dialog getDialog() {
			return dialog;
		}

		public void setDialog(Dialog d) {
			dialog = d;
		}

		public void release()
		{
			dialog = null;
			dialogActivity = null;
		}

		public boolean getPersistent()
		{
			return isPersistent;
		}

		public void setPersistent(boolean p)
		{
			isPersistent = p;
		}
	}

	public void addWindowToStack(TiWindowProxy proxy)
	{
		if (windowStack.contains(proxy)) {
			Log.e(TAG, "Window already exists in stack", Log.DEBUG_MODE);
			return;
		}
		boolean isEmpty = windowStack.empty();
		if (!isEmpty) {
			windowStack.peek().onWindowFocusChange(false);
		}
		windowStack.add(proxy);
		if (!isEmpty) {
			proxy.onWindowFocusChange(true);
		}
	    updateForWindow(proxy);
	}
	
	public boolean isCurrentWindow(final TiWindowProxy proxy) {
	    return this.window == proxy;
	}

	public void removeWindowFromStack(final TiWindowProxy proxy)
	{
	    boolean wasCurrentWindow = this.window == proxy;
		proxy.onWindowFocusChange(false);

		boolean isTopWindow = ( (!windowStack.isEmpty()) && (windowStack.peek() == proxy) ) ? true : false;
		windowStack.remove(proxy);
		if (!wasCurrentWindow) {
		    return;
		}
		
		if (!windowStack.empty()) {
			TiWindowProxy nextWindow = windowStack.peek();
            updateForWindow(nextWindow);
			//Fire focus only if activity is not paused and the removed window was topWindow
			if (isResumed && isTopWindow) {
				nextWindow.onWindowFocusChange(true);
			}
		}
		else
		{
	        updateForWindow(this.window);
		}
		
	}

	/**
	 * Returns the window at the top of the stack.
	 * @return the top window or null if the stack is empty.
	 */
	public TiWindowProxy topWindowOnStack()
	{
		return (windowStack.isEmpty()) ? null : windowStack.peek();
	}
	
	private void clearWindowStack(final boolean isFinishing) {
	    if (!windowStack.isEmpty()) {
            Iterator itr = windowStack.iterator();
            while( itr.hasNext() ) {
                TiWindowProxy window = (TiWindowProxy)itr.next();
                if (window != this.window) {
                    window.closeFromActivity(isFinishing);
                }
            }
            windowStack.clear();
        }
	}
	
    public void closeStackWindowsInFrontOf(TiWindowProxy proxy) {
//        int index = windowStack.indexOf(proxy);
//        if (index != -1) {
//            List<TiWindowProxy> toRemove = windowStack.subList(0, index);
//            for (int i = toRemove.size() - 1; i >= 0; i--) {
//                window = toRemove.get(i);
//                if (window != this.window) {
//                    window.closeFromActivity(true);
//                }
//            }
//            toRemove.clear();
//        } else if(isCurrentWindow(proxy)) {
//            clearWindowStack(true);
//        }
        TiWindowProxy window;
        for (int i = windowStack.size() - 1; i >= 0; i--) {
            window = windowStack.get(i);
            if (window == proxy || (
                    window instanceof TiWindowManager && 
                    ((TiWindowManager)window).getTopWindow() == proxy)) {
                return;
            }
            window.closeFromActivity(true);
            windowStack.remove(i);
        }
    }

	// could use a normal ConfigurationChangedListener but since only orientation changes are
	// forwarded, create a separate interface in order to limit scope and maintain clarity
	public static interface OrientationChangedListener
	{
		public void onOrientationChanged (int configOrientationMode, int width, int height);
	}
	
	private void setupOrientationListener() {
	    if (orientationListener == null) {
	        orientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
	            @Override
	            public void onOrientationChanged(int orientation) {
	                DisplayMetrics dm = new DisplayMetrics();
	                getWindowManager().getDefaultDisplay().getMetrics(dm);
	                int width = dm.widthPixels;
	                int height = dm.heightPixels;
	                int rotation = getWindowManager().getDefaultDisplay().getRotation();

	                if (((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
	                        && rotation != previousOrientation) ||
	                        ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
	                        && rotation != previousOrientation)) {
	                    callOrientationChangedListener(TiApplication.getAppRootOrCurrentActivity(), width, height, rotation);
	                    if (orientationChangedListeners != null) {
	                        synchronized (orientationChangedListeners.synchronizedList()) {
	                            int currentOrientation = getWindowManager().getDefaultDisplay().getRotation();
	                            for (OrientationChangedListener listener : orientationChangedListeners.nonNull()) {
	                                listener.onOrientationChanged(currentOrientation, width, height);;
	                            }
	                        }
	                    }
	                    
	                }
	            }
	        };
	        if (orientationListener.canDetectOrientation() == true) {
	            orientationListener.enable();
	        } else {
	            Log.w(TAG, "Cannot detect orientation");
	            orientationListener.disable();
	        }
	    }
	}
	
   private void unsetupOrientationListener() {
       if (orientationListener != null) {
           orientationListener.disable();
           orientationListener = null;
       }
   }


	public static void registerOrientationListener (OrientationChangedListener listener)
	{
		orientationChangedListener = listener;
		SparseArray<TiActivityWindow> windows = TiActivityWindows.getWindows();
		for(int i = 0, arraySize= windows.size(); i < arraySize; i++) {
		    TiActivityWindow window = windows.get(windows.keyAt(i));
		    Activity activity = window.getActivity();
		    if (activity instanceof TiBaseActivity) {
		        ((TiBaseActivity) activity).setupOrientationListener();
		    }
		 }
	}

	public static void deregisterOrientationListener()
	{
		orientationChangedListener = null;
		SparseArray<TiActivityWindow> windows = TiActivityWindows.getWindows();
        for(int i = 0, arraySize= windows.size(); i < arraySize; i++) {
            TiActivityWindow window = windows.get(windows.keyAt(i));
            Activity activity = window.getActivity();
            if (activity instanceof TiBaseActivity) {
                ((TiBaseActivity) activity).unsetupOrientationListener();
            }
         }
	}

	public static interface ConfigurationChangedListener
	{
		public void onConfigurationChanged(TiBaseActivity activity, Configuration newConfig);
	}
		
	public static void addPermissionListener(int code, KrollObject krollContext, KrollFunction krollCallback) {
	    ArrayList<Object> currents = sPermissionCallback.get(code);
	    if (currents == null) {
	        currents = new ArrayList();
            sPermissionCallback.put(code, currents);
	    }
	    currents.add(new KrollPermissionCallback(krollContext, krollCallback));
	}
	
	public static void addPermissionListener(int code, PermissionCallback command) {
        ArrayList<Object> currents = sPermissionCallback.get(code);
        if (currents == null) {
            currents = new ArrayList();
            sPermissionCallback.put(code, currents);
        }
        currents.add(command);
    }
	
	public static void removePermissionListener(int code, PermissionCallback command) {
        ArrayList<Object> currents = sPermissionCallback.get(code);
        if (currents == null) {
            return;
        }
        currents.remove(command);
    }
	public static void removePermissionListener(int code, KrollObject krollContext, KrollFunction krollCallback) {
        ArrayList<Object> currents = sPermissionCallback.get(code);
        if (currents == null) {
            return;
        }
        for(Object callback:currents) {
            if (callback instanceof KrollPermissionCallback &&
                    ((KrollPermissionCallback) callback).krollCallback == krollCallback) {
                currents.remove(callback);
                break;
            }
        }
    }


	/**
	 * @return the instance of TiApplication.
	 */
	public TiApplication getTiApp()
	{
		return (TiApplication) getApplication();
	}

	/**
	 * @return the window proxy associated with this activity.
	 */
	public TiWindowProxy getWindowProxy()
	{
		return this.window;
	}



	/**
	 * Sets the window proxy.
	 * @param proxy
	 */
	@SuppressLint("NewApi")
	public void setWindowProxy(TiWindowProxy proxy)
	{
//	    proxy = proxy.getTopWindow();
	    if(this.window == proxy) return;
		this.window = proxy;
		if (this.window == null) {
		    return;
		}
		
		updateForWindow(this.window);
	}
	
	public void updateForWindow(TiWindowProxy window) {
	       KrollDict props = window.getProperties();
	        boolean fullscreen = props.optBoolean(TiC.PROPERTY_FULLSCREEN, this.defaultFullscreen);
	        boolean newNavBarHidden = props.optBoolean(TiC.PROPERTY_NAV_BAR_HIDDEN, this.defaultFullscreen);
	        int softInputMode = props.optInt(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE, this.defaultSoftInputMode);
	        boolean hasSoftInputMode = softInputMode != -1;
	        
	        if (fullscreen != this.fullscreen) {
	            this.fullscreen = fullscreen;
	            setFullscreen(fullscreen);
	        }
	        if (newNavBarHidden != this.navBarHidden) {
	            this.navBarHidden = newNavBarHidden;
	            TiActivityHelper.setActionBarHidden(this, this.navBarHidden);
	        }
	        
	        if (TiC.HONEYCOMB_OR_GREATER) {
	            int uiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
	            if(props.containsKey("uiVisibilityFlags")) {
	                uiVisibility |= TiConvert.toInt(props, "uiVisibilityFlags", uiVisibility);
	            }
	            if (TiC.LOLLIPOP_OR_GREATER) {
	                if(props.containsKey("statusBarColor")) {
	                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
	                    getWindow().setStatusBarColor(TiConvert.toColor(props, "statusBarColor"));
	                }
	                if(props.containsKey("navigationBarColor")) {
	                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
	                    getWindow().setNavigationBarColor(TiConvert.toColor(props, "navigationBarColor"));
	                }
	                
	                if(props.containsKey("immersive")) {
	                    int immersive = TiConvert.toInt(props, "immersive");
	                    switch (immersive) {
	                    case 1:
	                        uiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
	                        break;
	                    case 2:
	                        uiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
	                        break;
	                    case 0:
	                    default:
	                        break;
	                    }
	                }
	                if (fullscreen) {
	                    uiVisibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
	                }
	                if(props.containsKey("navigationHidden")) {
	                    boolean value =  TiConvert.toBoolean(props, "navigationHidden");
	                    if (value) {
	                        uiVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
	                    }
	                }
	            }
	            getWindow().getDecorView().setSystemUiVisibility(uiVisibility);
	        }
	        
	        

	        
	        if (hasSoftInputMode && softInputMode != this.softInputMode) {
	            setSoftInputMode(softInputMode);  
	        }
	        
	        updateActivityProxy(window, null);
	}
	
	public void updateActivityProxy(TiWindowProxy window, HashMap supplActionBar) {
        HashMap props = window.getProperties();
	    HashMap activityDict = window.getActivityProperties(TiConvert.toHashMap(props.get(TiC.PROPERTY_ACTIVITY)), supplActionBar);
        if (window.getWindowManager() instanceof TiWindowProxy) {
            activityDict = ((TiWindowProxy) window.getWindowManager()).getActivityProperties(activityDict, supplActionBar);
        }
        getActivityProxy().setProperties(activityDict);
	}
	
	private void setSoftInputMode(int softInputMode) {
	    getWindow().setSoftInputMode(softInputMode);  
        if(!TiC.KIT_KAT_OR_GREATER) {
            return;
        }
        //on > 19 if a window has a transculent flag, the adjustResize wont work correctly
        //anymore. This fixes it by changing the height of the layout
        int flags = getWindow().getAttributes().flags;
        if ((softInputMode & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) != 0
             && (flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0) {
            AndroidBug5497Workaround.assistActivity(this);
        } else {
            AndroidBug5497Workaround.unassistActivity(this);
        }
	}

	/**
	 * Sets the view proxy.
	 * @param proxy
	 */
	public void setViewProxy(TiViewProxy proxy)
	{
		this.view = proxy;
	}

	/**
	 * @return activity proxy associated with this activity.
	 */
	public ActivityProxy getActivityProxy()
	{
		return activityProxy;
	}

	public void addDialog(DialogWrapper d)
	{
		if (!dialogs.contains(d)) {
			dialogs.add(d);
		}
	}

	public void removeDialog(Dialog d)
	{
		for (int i = 0; i < dialogs.size(); i++) {
			DialogWrapper p = dialogs.get(i);
			if (p.getDialog().equals(d)) {
				p.release();
				dialogs.remove(i);
				return;
			}
		}
	}
	public void setActivityProxy(ActivityProxy proxy)
	{
		this.activityProxy = proxy;
	}

	/**
	 * @return the activity's current layout.
	 */
	public View getLayout()
	{
		return layout;
	}
	

    public View getTiLayout() {
        if (contentView != null) {
            return contentView;
        }
        return getLayout();
    }

	public void setLayout(ViewGroup layout)
	{
		this.layout = layout;
	}

	public void addConfigurationChangedListener(ConfigurationChangedListener listener)
	{
		configChangedListeners.add(new WeakReference<ConfigurationChangedListener>(listener));
	}

	public void removeConfigurationChangedListener(ConfigurationChangedListener listener)
	{
		configChangedListeners.remove(listener);
	}

	public void registerOrientationChangedListener (OrientationChangedListener listener)
	{
		orientationChangedListener = listener;
	}

	public void deregisterOrientationChangedListener()
	{
		orientationChangedListener = null;
	}

	protected boolean getIntentBoolean(String property, boolean defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getBooleanExtra(property, defaultValue);
			}
		}

		return defaultValue;
	}
	
	protected boolean intentHasProperty(String property)
    {
        Intent intent = getIntent();
        if (intent != null) {
            return intent.hasExtra(property);
        }

        return false;
    }

	protected int getIntentInt(String property, int defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getIntExtra(property, defaultValue);
			}
		}

		return defaultValue;
	}

	protected String getIntentString(String property, String defaultValue)
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(property)) {
				return intent.getStringExtra(property);
			}
		}

		return defaultValue;
	}


//	protected void updateTitle(TiWindowProxy proxy)
//	{
//		if (proxy == null) return;
//
//		if (proxy.hasProperty(TiC.PROPERTY_TITLE)) {
//			String oldTitle = (String) getTitle();
//			String newTitle = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_TITLE));
//
//			if (oldTitle == null) {
//				oldTitle = "";
//			}
//
//			if (newTitle == null) {
//				newTitle = "";
//			}
//
//			if (!newTitle.equals(oldTitle)) {
//				final String fnewTitle = newTitle;
//				
//				    runOnUiThread(new Runnable(){
//                        public void run() {
//                            if (!TiActivityHelper.setActionBarTitle(TiBaseActivity.this, fnewTitle)) {
//                                setTitle(fnewTitle);
//                            }
//                        }
//                    });
//			}
//		}
//	}

//	private Toolbar toolbar = null;
	// Subclasses can override to provide a custom layout
	protected ViewGroup createLayout(int layoutId)
	{
       LayoutArrangement arrangement = LayoutArrangement.DEFAULT;

        String layoutFromIntent = getIntentString(TiC.INTENT_PROPERTY_LAYOUT, "");
        if (layoutFromIntent.equals(TiC.LAYOUT_HORIZONTAL)) {
            arrangement = LayoutArrangement.HORIZONTAL;

        } else if (layoutFromIntent.equals(TiC.LAYOUT_VERTICAL)) {
            arrangement = LayoutArrangement.VERTICAL;
        }
        contentView = new TiCompositeLayout(this, arrangement, null) {
            private boolean firstFocusRequest = true;
            
            @Override
            public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
                if (firstFocusRequest) {
                    firstFocusRequest = false;
                    return false;
                }
                return super.requestFocus(direction, previouslyFocusedRect);
            }
            
            //make sure no TiUIView is attached
            @Override
            public void setView(TiUIView view) {
            }
            
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                final int action = event.getAction();
                if (action == MotionEvent.ACTION_UP || 
                        action == MotionEvent.ACTION_CANCEL) {
                    final MotionEvent copy = MotionEvent.obtain(event);
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                      @Override
                      public void run() {
                          if (!windowStack.isEmpty()) {
                              TiWindowProxy win = (TiWindowProxy)windowStack.lastElement();
                              if (win != null) {
                                  window.checkUpEventSent(copy);
                              }
                          }
                          else if (window != null) {
                               window.checkUpEventSent(copy);
                          }
                      }
                    }, 0);
                }
                return super.onInterceptTouchEvent(event);
            }
        };
	    if (layoutId == -1) {
            return contentView;
	        
	    }
	    setContentView(layoutId);
        layout = (ViewGroup) findViewById(android.R.id.content);
	     
        ViewGroup contentHolder;
        try {
            contentHolder = (ViewGroup) findViewById(TiRHelper.getApplicationResource("id.content_holder"));
            contentHolder.addView(contentView, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
          Toolbar toolbar = (Toolbar) findViewById(TiRHelper.getApplicationResource("id.toolbar"));
//            Toolbar toolbar = new Toolbar(this);
//            toolbar.setVisibility(View.VISIBLE);
//            ViewGroup toolbarHolder = (ViewGroup) findViewById(TiRHelper.getApplicationResource("id.toolbar_holder"));
//          toolbar.setVisibility(View.VISIBLE);
//            ViewGroup.LayoutParams toolbarParams = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//          toolbarParams.setCollapseMode(CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN);
//            toolbarHolder.addView(toolbar, toolbarParams);
//          setSupportActionBar(mToolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                getSupportActionBar().setDisplayShowTitleEnabled(true);

                getSupportActionBar().setTitle("My custom toolbar!");
                getSupportActionBar().setHomeButtonEnabled(true);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        } catch (ResourceNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        

//        
//	    layout = new CoordinatorLayout(this);
//	    
//	    AppBarLayout barLayout = new AppBarLayout(this);
//	    CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//        params.setBehavior(new AppBarLayout.ScrollingViewBehavior());
//	    layout.addView(barLayout, params);
//	    
//        CollapsingToolbarLayout collLayout = new CollapsingToolbarLayout(this);
//        AppBarLayout.LayoutParams barParams = new AppBarLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
//        barParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
//                | AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
//	    barLayout.addView(collLayout, params);
//	    
//	    TiCompositeLayout testView = new TiCompositeLayout(this);
//	    testView.setBackgroundColor(Color.RED);
//	    CollapsingToolbarLayout.LayoutParams toolbarParams = new CollapsingToolbarLayout.LayoutParams(LayoutParams.MATCH_PARENT, android.R.attr.actionBarSize);
//        toolbarParams.setCollapseMode(CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX);
//        collLayout.addView(testView, toolbarParams);
//	    
//	    mToolbar = new Toolbar(this);
//        mToolbar.setVisibility(View.VISIBLE);
//        toolbarParams = new CollapsingToolbarLayout.LayoutParams(LayoutParams.MATCH_PARENT, android.R.attr.actionBarSize);
//        toolbarParams.setCollapseMode(CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN);
//        collLayout.addView(mToolbar, toolbarParams);
//        setSupportActionBar(mToolbar);
//        
//        params = new CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
//        params.setBehavior(new AppBarLayout.ScrollingViewBehavior());
//        layout.addView(contentView, params);
	    return layout;
		
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
		String permissions[], int[] grantResults) {
	    if (sPermissionCallback.containsKey(requestCode)) {
	        ArrayList<Object> callbacks = sPermissionCallback.get(requestCode);
	        for (Object command : callbacks) {
	            if (command instanceof PermissionCallback) {
	                ((PermissionCallback) command).onRequestPermissionsResult(permissions, grantResults);
	            } else if (command instanceof  KrollPermissionCallback) {
                    ((KrollPermissionCallback) command).onRequestPermissionsResult(permissions, grantResults);
	            }
	        }
	        sPermissionCallback.remove(requestCode);
	    }
	}

	protected void setFullscreen(boolean fullscreen)
	{
		if (fullscreen) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			View decorView = getWindow().getDecorView();
			// Hide both the navigation bar and the status bar.
			// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
			// a general rule, you should design your app to hide the status bar whenever you
			// hide the navigation bar.
			int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			              | View.SYSTEM_UI_FLAG_FULLSCREEN;
			decorView.setSystemUiVisibility(uiOptions);
		}
		else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}
	
	public final boolean isReadyToQueryActionBar() {
	    return mReadyToQueryActionBar;
	}
	
	// Subclasses can override to handle post-creation (but pre-message fire) logic
	@SuppressWarnings("deprecation")
	protected void windowCreated(Bundle savedInstanceState)
	{
		defaultFullscreen = fullscreen = getIntentBoolean(TiC.PROPERTY_FULLSCREEN, false);
//		defaultNavBarHidden = navBarHidden = getIntentBoolean(TiC.PROPERTY_NAV_BAR_HIDDEN, false);
		navBarHidden = getIntentBoolean(TiC.PROPERTY_NAV_BAR_HIDDEN, false);
		boolean modal = getIntentBoolean(TiC.PROPERTY_MODAL, false);
		softInputMode = getIntentInt(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE, defaultSoftInputMode);
		boolean hasSoftInputMode = softInputMode != -1;
		int windowFlags = getIntentInt(TiC.PROPERTY_WINDOW_FLAGS, 0);
		final Window window = getWindow();
		
		setFullscreen(fullscreen);
		TiActivityHelper.setActionBarHidden(this, navBarHidden);
		
		if (intentHasProperty(TiC.PROPERTY_WINDOW_TYPE)) {
            window.setType(getIntentInt(TiC.PROPERTY_WINDOW_TYPE, WindowManager.LayoutParams.TYPE_APPLICATION));
        }
		
		if (windowFlags > 0) {
			window.addFlags(windowFlags);
		}
        
		
		if (modal) {
			if (TiC.ICS_OR_GREATER) {
				// This flag is deprecated in API 14. On ICS, the background is not blurred but straight black.
				window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
			}
		}

		if (hasSoftInputMode) {
			Log.d(TAG, "windowSoftInputMode: " + softInputMode, Log.DEBUG_MODE);
			setSoftInputMode(softInputMode);
		}

		boolean useActivityWindow = getIntentBoolean(TiC.INTENT_PROPERTY_USE_ACTIVITY_WINDOW, false);
		if (useActivityWindow) {
			int windowId = getIntentInt(TiC.INTENT_PROPERTY_WINDOW_ID, -1);
			TiActivityWindows.windowCreated(this, windowId, savedInstanceState);
		}
	}

	// Record if user has set a content view manually from hyperloop code during require of app.js!
	@Override
	public void setContentView(View view) {
		overridenLayout = true;
		super.setContentView(view);
	}

	@Override
	public void setContentView(int layoutResID) {
		overridenLayout = true;
		super.setContentView(layoutResID);
	}

	@Override
	public void setContentView(View view, LayoutParams params) {
		overridenLayout = true;
		super.setContentView(view, params);
	}


	@Override
	/**
	 * When the activity is created, this method adds it to the activity stack and
	 * fires a javascript 'create' event.
	 * @param savedInstanceState Bundle of saved data.
	 */
	protected void onCreate(Bundle savedInstanceState)
	{
		TiApplication.getInstance().activityStarted(this);
//		Log.d(TAG, "Activity " + this + " onCreate", Log.DEBUG_MODE);

		inForeground = true;
		TiApplication tiApp = getTiApp();

		if (tiApp.isRestartPending()) {
			super.onCreate(savedInstanceState);
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		// If all the activities has been killed and the runtime has been disposed or the app's hosting process has
		// been killed, we cannot recover one specific activity because the info of the top-most view proxy has been
		// lost (TiActivityWindows.dispose()). In this case, we have to restart the app.
		if (TiBaseActivity.isUnsupportedReLaunch(this, savedInstanceState)) {
			Log.w(TAG, "Runtime has been disposed or app has been killed. Finishing.");
			super.onCreate(savedInstanceState);
			tiApp.scheduleRestart(250);
			finish();
			return;
		}

		TiApplication.addToActivityStack(this);

		// create the activity proxy here so that it is accessible from the activity in all cases
		activityProxy = new ActivityProxy(this);

		// Increment the reference count so we correctly clean up when all of our activities have been destroyed
		KrollRuntime.incrementActivityRefCount();

		Intent intent = getIntent();
		if (intent != null) {
			if (intent.hasExtra(TiC.INTENT_PROPERTY_MESSENGER)) {
				messenger = (Messenger) intent.getParcelableExtra(TiC.INTENT_PROPERTY_MESSENGER);
				msgActivityCreatedId = intent.getIntExtra(TiC.INTENT_PROPERTY_MSG_ACTIVITY_CREATED_ID, -1);
				msgId = intent.getIntExtra(TiC.INTENT_PROPERTY_MSG_ID, -1);
			}

			if (intent.hasExtra(TiC.PROPERTY_WINDOW_PIXEL_FORMAT)) {
				getWindow().setFormat(intent.getIntExtra(TiC.PROPERTY_WINDOW_PIXEL_FORMAT, PixelFormat.UNKNOWN));
			}
		}

		// Doing this on every create in case the activity is externally created.
		TiPlatformHelper.getInstance().intializeDisplayMetrics(this);

		// Set the theme of the activity before calling super.onCreate().
		// On 2.3 devices, it does not work if the theme is set after super.onCreate.
		int theme = getIntentInt(TiC.PROPERTY_THEME, -1);
		if (theme != -1) {
			this.setTheme(theme);
		}

		// Set ActionBar into split mode must be done before the decor view has been created
		// we need to do this before calling super.onCreate()
		if (intent != null && intent.hasExtra(TiC.PROPERTY_SPLIT_ACTIONBAR)) {
			getWindow().setUiOptions(ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);
		}
		
        if (intent != null && intent.hasExtra(TiC.PROPERTY_ACTIONBAR_OVERLAY)) {
            supportRequestWindowFeature(AppCompatDelegate.FEATURE_SUPPORT_ACTION_BAR_OVERLAY);
        }

		// we only want to set the current activity for good in the resume state but we need it right now.
		// save off the existing current activity, set ourselves to be the new current activity temporarily
		// so we don't run into problems when we give the proxy the event
		Activity tempCurrentActivity = tiApp.getCurrentActivity();
		tiApp.setCurrentActivity(this, this);

		// we need to set window features before calling onCreate
		this.requestWindowFeature(Window.FEATURE_PROGRESS);
		this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		if (TiC.LOLLIPOP_OR_GREATER) {
			this.requestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
		}
		

		if (layout == null) {
		    layout = createLayout(getIntentInt(TiC.PROPERTY_LAYOUT_ID, -1));
        }
        if (intent != null && intent.hasExtra(TiC.PROPERTY_KEEP_SCREEN_ON)) {
            layout.setKeepScreenOn(intent.getBooleanExtra(TiC.PROPERTY_KEEP_SCREEN_ON, layout.getKeepScreenOn()));
        }
        
		super.onCreate(savedInstanceState);
		
		// set the current activity back to what it was originally
        tiApp.setCurrentActivity(this, tempCurrentActivity);

        // If user changed the layout during app.js load, keep that
		if (!overridenLayout) {
			setContentView(layout);
		}
        //sometimes we get a weird right and bottom padding :s
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        
        mReadyToQueryActionBar = true;
        
        //make sure we call windowCreated after setContentView because
        //it's a bad idea to query the actionBar before.
		windowCreated(savedInstanceState);

		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_CREATE, null);
			activityProxy.fireEvent(TiC.EVENT_CREATE);
		}
		
//		if (toolbar != null) {
//            setSupportActionBar(toolbar);
//        }
		
		sendMessage(msgActivityCreatedId);
		// for backwards compatibility
		sendMessage(msgId);

		// store off the original orientation for the activity set in the AndroidManifest.xml
		// for later use
		originalOrientationMode = getRequestedOrientation();

		
		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, savedInstanceState, TiLifecycle.LIFECYCLE_ON_CREATE);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
	}
	
	@Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (window != null) {
            if (window.getWindowManager() != null)
                window.getWindowManager().onWindowActivityCreated();
            else {
                window.onWindowActivityCreated();
            }
        }
    }


	public int getOriginalOrientationMode()
	{
		return originalOrientationMode;
	}

	public boolean isInForeground()
	{
		return inForeground;
	}
	
	public boolean isActivityPaused()
	{
		return isPaused;
	}

	protected void sendMessage(final int msgId)
	{
		if (messenger == null || msgId == -1) {
			return;
		}

		// fire an async message on this thread's queue
		// so we don't block onCreate() from returning
		TiMessenger.postOnMain(new Runnable() {
			public void run()
			{
				handleSendMessage(msgId);
			}
		});
	}

	protected void handleSendMessage(int messageId)
	{
		try {
			Message message = TiMessenger.getMainMessenger().getHandler().obtainMessage(messageId, this);
			messenger.send(message);

		} catch (RemoteException e) {
			Log.e(TAG, "Unable to message creator. finishing.", e);
			finish();

		} catch (RuntimeException e) {
			Log.e(TAG, "Unable to message creator. finishing.", e);
			finish();
		}
	}

	protected TiActivitySupportHelper getSupportHelper()
	{
		if (supportHelper == null) {
			this.supportHelper = new TiActivitySupportHelper(this);
			// Register the supportHelper so we can get it back when the activity is recovered from force-quitting.
			supportHelperId = TiActivitySupportHelpers.addSupportHelper(supportHelper);
		}

		return supportHelper;
	}

	// Activity Support
	public int getUniqueResultCode()
	{
		return getSupportHelper().getUniqueResultCode();
	}

	/**
	 * See TiActivitySupport.launchActivityForResult for more details.
	 */
	public void launchActivityForResult(Intent intent, int code, TiActivityResultHandler resultHandler)
	{
		getSupportHelper().launchActivityForResult(intent, code, resultHandler);
	}

	/**
	 * See TiActivitySupport.launchIntentSenderForResult for more details.
	 */
	public void launchIntentSenderForResult(IntentSender intent, int requestCode, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options, TiActivityResultHandler resultHandler)
	{
		getSupportHelper().launchIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options, resultHandler);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		synchronized (onActivityResultListeners.synchronizedList()) {
			for (OnActivityResultEvent listener : onActivityResultListeners.nonNull()) {
				try {
					TiLifecycle.fireOnActivityResultEvent(this, listener, requestCode, resultCode, data);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching onActivityResult event: " + t.getMessage(), t);
				}
			}
		}
		getSupportHelper().onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed()
	{
		if (!handleBackKeyPressed()){
			// If event is not handled by any listeners allow default behavior.
			super.onBackPressed();
		}
	}
	
	public boolean handleAndroidBackEvent() {
		KrollProxy proxy = null;
		if (activityProxy.hasListeners(TiC.EVENT_ANDROID_BACK)) {
			proxy = activityProxy;
		} else{
			TiWindowProxy topWindow = topWindowOnStack();
			if (topWindow != null) {
			    if (topWindow instanceof TiWindowManager) {
			        TiWindowProxy newWindow = ((TiWindowManager) topWindow).getTopWindow();
	                while(newWindow != topWindow && newWindow instanceof TiWindowManager) {
	                    newWindow = ((TiWindowManager) newWindow).getTopWindow();
	                }
	                topWindow = newWindow;
			    }
			    proxy = topWindow.firstHierarchyListener(TiC.EVENT_ANDROID_BACK);
			} else if(window != null) {
				proxy = window.firstHierarchyListener(TiC.EVENT_ANDROID_BACK);
			}
		}
		
		// Prevent default Android behavior for "back" press
		// if the top window has a listener to handle the event.
		if (proxy != null) {
			proxy.fireEvent(TiC.EVENT_ANDROID_BACK, null, false, false);
			return true;
		}
		return false;
	}
	
	private boolean handleBackKeyPressed(){
//	    boolean hasLightWeightWindow = windowStack.size() > 1;
	    if (handleAndroidBackEvent()) {
	        return true;
	    }
		synchronized (interceptOnBackPressedListeners.synchronizedList()) {
			for (interceptOnBackPressedEvent listener : interceptOnBackPressedListeners.nonNull()) {
				try {
					if (listener.interceptOnBackPressed()) {
						return true;
					}

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching interceptOnBackPressed event: " + t.getMessage(), t);
				}
			}
		}
//		if (hasLightWeightWindow) {
	        return false;
//		} else {
//		    return handleAndroidBackEvent();
//		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		boolean handled = false;

		TiViewProxy window;
		if (this.window != null) {
			window = this.window;
		} else {
			window = this.view;
		}

		if (window == null) {
			return super.dispatchKeyEvent(event);
		}

		switch(event.getKeyCode()) {
			case KeyEvent.KEYCODE_BACK : {

				if (event.getAction() == KeyEvent.ACTION_UP) {
					handled = handleBackKeyPressed();					
				}
				break;
			}
			case KeyEvent.KEYCODE_CAMERA : {
				if (window.hasListeners(TiC.EVENT_ANDROID_CAMERA)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_CAMERA);
					}
					handled = true;
				}
				break;
			}
			case KeyEvent.KEYCODE_FOCUS : {
				if (window.hasListeners(TiC.EVENT_ANDROID_FOCUS)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_FOCUS);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_SEARCH : {
				if (window.hasListeners(TiC.EVENT_ANDROID_SEARCH)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_SEARCH);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_VOLUME_UP : {
				if (window.hasListeners(TiC.EVENT_ANDROID_VOLUP)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_VOLUP);
					}
					handled = true;
				}

				break;
			}
			case KeyEvent.KEYCODE_VOLUME_DOWN : {
				if (window.hasListeners(TiC.EVENT_ANDROID_VOLDOWN)) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						window.fireEvent(TiC.EVENT_ANDROID_VOLDOWN);
					}
					handled = true;
				}

				break;
			}
		}

		if (!handled) {
			handled = super.dispatchKeyEvent(event);
		}

		return handled;
	}
	
	private ActionBarProxy getActionBarProxy(){
		if (activityProxy != null) {
			return activityProxy.getActionBar();
		}
		return null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// If targetSdkVersion is set to 11+, Android will invoke this function
		// to initialize the menu (since it's part of the action bar). Due
		// to the fix for Android bug 2373, activityProxy won't be initialized b/c the
		// activity is expected to restart, so we will ignore it.
		if (activityProxy == null) {
			return false;
		}

		boolean listenerExists = false;
		synchronized (onCreateOptionsMenuListeners.synchronizedList()) {
			for (OnCreateOptionsMenuEvent listener : onCreateOptionsMenuListeners.nonNull()) {
				try {
					listenerExists = true;
					TiLifecycle.fireOnCreateOptionsMenuEvent(this, listener, menu);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnCreateOptionsMenuEvent: " + t.getMessage(), t);
				}
			}
		}

		if (menuHelper == null) {
			menuHelper = new TiMenuSupport(activityProxy);
		}

		return menuHelper.onCreateOptionsMenu(super.onCreateOptionsMenu(menu) || listenerExists, menu);
	}
	
	public Menu getMenu() {
	    if (menuHelper != null) {
	        return menuHelper.getMenu();
	    }
	    return null;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case android.R.id.home:
				synchronized (interceptOnHomePressedListeners.synchronizedList()) {
					for (interceptOnHomePressedEvent listener : interceptOnHomePressedListeners.nonNull()) {
						try {
							if (listener.interceptOnHomePressed()) {
								return true;
							}

						} catch (Throwable t) {
							Log.e(TAG, "Error dispatching interceptOnHomePressed event: " + t.getMessage(), t);
						}
					}
				}
				
				ActionBarProxy actionBarProxy = getActionBarProxy();
				if (actionBarProxy != null) {
					KrollFunction onHomeIconItemSelected = (KrollFunction) actionBarProxy
						.getProperty(TiC.PROPERTY_ON_HOME_ICON_ITEM_SELECTED);
					if (onHomeIconItemSelected != null) {
						KrollDict event = new KrollDict();
						event.put(TiC.EVENT_PROPERTY_SOURCE, actionBarProxy);
						event.put(TiC.EVENT_PROPERTY_WINDOW, window);
						onHomeIconItemSelected.call(activityProxy.getKrollObject(), new Object[] { event });
						return true;
					}
				}
				
				
				return true;
			default:
				return menuHelper.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		boolean listenerExists = false;
		synchronized (onPrepareOptionsMenuListeners.synchronizedList()) {
			for (OnPrepareOptionsMenuEvent listener : onPrepareOptionsMenuListeners.nonNull()) {
				try {
					listenerExists = true;
					TiLifecycle.fireOnPrepareOptionsMenuEvent(this, listener, menu);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnPrepareOptionsMenuEvent: " + t.getMessage(), t);
				}
			}
		}
		return menuHelper.onPrepareOptionsMenu(super.onPrepareOptionsMenu(menu) || listenerExists, menu);
	}

	public static void callOrientationChangedListener(Activity activity, int width, int height, int rotation)
	{
		if (activity != null) {
			int currentOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
			if (orientationChangedListener != null && previousOrientation != currentOrientation) {
				previousOrientation = currentOrientation;
				orientationChangedListener.onOrientationChanged (currentOrientation, width, height);
			}	
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		for (WeakReference<ConfigurationChangedListener> listener : configChangedListeners) {
			if (listener.get() != null) {
				listener.get().onConfigurationChanged(this, newConfig);
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);

		Log.d(TAG, "Activity " + this + " onNewIntent", Log.DEBUG_MODE);

		if (activityProxy != null) {
			IntentProxy ip = new IntentProxy(intent);
			KrollDict data = new KrollDict();
			data.put(TiC.PROPERTY_INTENT, ip);
			activityProxy.fireSyncEvent(TiC.EVENT_NEW_INTENT, data);
			// TODO: Deprecate old event
			activityProxy.fireSyncEvent("newIntent", data);
		}
	}

	public void addOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		lifecycleListeners.add(new WeakReference<OnLifecycleEvent>(listener));
	}

	public void addOnInstanceStateEventListener(OnInstanceStateEvent listener)
	{
		instanceStateListeners.add(new WeakReference<OnInstanceStateEvent>(listener));
	}

    public void removeOnInstanceStateEventListener(OnInstanceStateEvent listener)
    {
        for (int i = 0; i < instanceStateListeners.size(); i++) {
            OnInstanceStateEvent iListener = instanceStateListeners.get(i).get();
            if (listener == iListener) {
                instanceStateListeners.remove(i);
                return;
            }
        }
    }

	public void addOnWindowFocusChangedEventListener(OnWindowFocusChangedEvent listener)
	{
		windowFocusChangedListeners.add(new WeakReference<OnWindowFocusChangedEvent>(listener));
	}
	
	public void removeOnWindowFocusChangedEventListener(OnWindowFocusChangedEvent listener)
    {
        for (int i = 0; i < windowFocusChangedListeners.size(); i++) {
            OnWindowFocusChangedEvent iListener = windowFocusChangedListeners.get(i).get();
            if (listener == iListener) {
                windowFocusChangedListeners.remove(i);
                return;
            }
        }
    }

	public void addInterceptOnBackPressedEventListener(interceptOnBackPressedEvent listener)
	{
		interceptOnBackPressedListeners.add(new WeakReference<interceptOnBackPressedEvent>(listener));
	}
	
	public void removeInterceptOnBackPressedEventListener(interceptOnBackPressedEvent listener)
	{
		for (int i = 0; i < interceptOnBackPressedListeners.size(); i++) {
			interceptOnBackPressedEvent iListener = interceptOnBackPressedListeners.get(i).get();
			if (listener == iListener) {
				interceptOnBackPressedListeners.remove(i);
				return;
			}
		}
	}
	
	public void addInterceptOnHomePressedEventListener(interceptOnHomePressedEvent listener)
	{
		interceptOnHomePressedListeners.add(new WeakReference<interceptOnHomePressedEvent>(listener));
	}
	
	public void removeInterceptOnHomePressedEventListener(interceptOnHomePressedEvent listener)
	{
		for (int i = 0; i < interceptOnHomePressedListeners.size(); i++) {
			interceptOnHomePressedEvent iListener = interceptOnHomePressedListeners.get(i).get();
			if (listener == iListener) {
				interceptOnHomePressedListeners.remove(i);
				return;
			}
		}
	}

	public void addOnActivityResultListener(OnActivityResultEvent listener)
	{
		onActivityResultListeners.add(new WeakReference<OnActivityResultEvent>(listener));
	}

	public void addOnCreateOptionsMenuEventListener(OnCreateOptionsMenuEvent listener)
	{
		onCreateOptionsMenuListeners.add(new WeakReference<OnCreateOptionsMenuEvent>(listener));
	}

	public void addOnPrepareOptionsMenuEventListener(OnPrepareOptionsMenuEvent listener)
	{
		onPrepareOptionsMenuListeners.add(new WeakReference<OnPrepareOptionsMenuEvent>(listener));
	}

	public void removeOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		for (int i = 0; i < lifecycleListeners.size(); i++) {
			OnLifecycleEvent iListener = lifecycleListeners.get(i).get();
			if (listener == iListener) {
				lifecycleListeners.remove(i);
				return;
			}
		}
	}
	
	public void addOrientationChangedListener (OrientationChangedListener listener)
    {
	    if (orientationChangedListeners == null) {
	        orientationChangedListeners = new TiWeakList<OrientationChangedListener>();
	    }
        orientationChangedListeners.add(new WeakReference<OrientationChangedListener>(listener));
    }

    public void removeOrientationChangedListener(OrientationChangedListener listener)
    {
        if (orientationChangedListeners == null) {
            return;
        }
        for (int i = 0; i < orientationChangedListeners.size(); i++) {
            OrientationChangedListener iListener = orientationChangedListeners.get(i).get();
            if (listener == iListener) {
                orientationChangedListeners.remove(i);
                return;
            }
        }
    }
    

	private void dispatchCallback(String name, KrollDict data) {
		if (data == null) {
			data = new KrollDict();
		}

		data.put("source", activityProxy);

		// TIMOB-19903
		if (TiApplication.getInstance().runOnMainThread()) {
			// We must call this synchornously to ensure it happens before we release the Activity reference on the V8/Native side!
			activityProxy.callPropertySync(name, new Object[] { data });
		} else {
			// This hopefully finishes before we release the reference on the native side?! I have seen it crash because it didn't before though...
			// Not sure it's safe to keep this behavior...
			activityProxy.callPropertyAsync(name, new Object[] { data });
		}
	}

	private void releaseDialogs(boolean finish)
	{
		//clean up dialogs when activity is pausing or finishing
		for (Iterator<DialogWrapper> iter = dialogs.iterator(); iter.hasNext(); ) {
			DialogWrapper p = iter.next();
			Dialog dialog = p.getDialog();
			boolean persistent = p.getPersistent();
			//if the activity is pausing but not finishing, clean up dialogs only if
			//they are non-persistent
			
			if (finish || !persistent) {
				if (dialog != null && dialog.isShowing()) {
					if (dialog.getCurrentFocus() != null) {
						TiUIHelper.hideSoftKeyboard(dialog.getCurrentFocus());
					}
					dialog.dismiss();
				}
				dialogs.remove(p);
			}
			else {
				if (dialog != null && dialog.getCurrentFocus() != null) {
					TiUIHelper.hideSoftKeyboard(dialog.getCurrentFocus());
				}
			}
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		synchronized (windowFocusChangedListeners.synchronizedList()) {
			for (OnWindowFocusChangedEvent listener : windowFocusChangedListeners.nonNull()) {
				try {
					listener.onWindowFocusChanged(hasFocus);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching onWindowFocusChanged event: " + t.getMessage(), t);
				}
			}
		}
		super.onWindowFocusChanged(hasFocus);
	}
	
	@Override
    public void startActivity(Intent intent) {
		//this activity onPause is called before the new activity onCreate :s
		//this prevent unwanted pause events to be sent
		super.startActivity(intent);
    }
	
	@Override
	/**
	 * When this activity pauses, this method sets the current activity to null, fires a javascript 'pause' event,
	 * and if the activity is finishing, remove all dialogs associated with it.
	 */
	protected void onPause()
	{
		TiApplication.getInstance().activityPaused(this); //call before setting inForeground
		inForeground = false;
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_PAUSE, null);
		}
		super.onPause();
		isResumed = false;
		isPaused = true;

//		Log.d(TAG, "Activity " + this + " onPause", Log.DEBUG_MODE);

		TiApplication tiApp = getTiApp();
		if (tiApp.isRestartPending()) {
			releaseDialogs(true);
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		if (!windowStack.empty()) {
			windowStack.peek().onWindowFocusChange(false);
		}

		TiApplication.updateActivityTransitionState(true);
		tiApp.setCurrentActivity(this, null);
		TiUIHelper.hideSoftKeyboard(getWindow().getDecorView());

		//release non-persistent dialogs
		releaseDialogs(this.isFinishing());

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_PAUSE);
		}

		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_PAUSE);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}

		// Checkpoint for ti.background event
		if (tiApp != null && TiApplication.getInstance().isAnalyticsEnabled()) {
			APSAnalytics.getInstance().sendAppBackgroundEvent();
		}
	}

	@Override
	/**
	 * When the activity resumes, this method updates the current activity to this and fires a javascript
	 * 'resume' event.
	 */
	protected void onResume()
	{
		TiApplication.getInstance().activityResumed(this);
		inForeground = true;
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_RESUME, null);
		}
		super.onResume();
		if (isFinishing()) {
			return;
		}

//		Log.d(TAG, "Activity " + this + " onResume", Log.DEBUG_MODE);

		TiApplication tiApp = getTiApp();
		if (tiApp.isRestartPending()) {
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		if (!windowStack.empty()) {
			windowStack.peek().onWindowFocusChange(true);
		}

		tiApp.setCurrentActivity(this, this);
		TiApplication.updateActivityTransitionState(false);

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_RESUME);
		}
		
		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_RESUME);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}

		isResumed = true;
		isPaused = false;

		// Checkpoint for ti.foreground event
		//String deployType = tiApp.getAppProperties().getString("ti.deploytype", "unknown");
		if(TiApplication.getInstance().isAnalyticsEnabled()){
		    APSAnalytics.getInstance().sendAppForegroundEvent();
		}
	}
	
//	@Override
//	public void startActivity(Intent intent)	{
//		TiApplication.getInstance().setStartingActivity(true);
//		super.startActivity(intent);
//	}
	
	@Override
	/**
	 * When this activity starts, this method updates the current activity to this if necessary and
	 * fire javascript 'start' and 'focus' events. Focus events will only fire if
	 * the activity is not a tab activity.
	 */
	protected void onStart()
	{
		inForeground = true;
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_START, null);
		}
		super.onStart();
		if (isFinishing()) {
			return;
		}

		// Newer versions of Android appear to turn this on by default.
		// Turn if off until an activity indicator is shown.
		setProgressBarIndeterminateVisibility(false);

//		Log.d(TAG, "Activity " + this + " onStart", Log.DEBUG_MODE);

		TiApplication tiApp = getTiApp();

		if (tiApp.isRestartPending()) {
			if (!isFinishing()) {
				finish();
			}
			return;
		}


		if (activityProxy != null) {
			// we only want to set the current activity for good in the resume state but we need it right now.
			// save off the existing current activity, set ourselves to be the new current activity temporarily
			// so we don't run into problems when we give the proxy the event
			Activity tempCurrentActivity = tiApp.getCurrentActivity();
			tiApp.setCurrentActivity(this, this);

			activityProxy.fireEvent(TiC.EVENT_START);

			// set the current activity back to what it was originally
			tiApp.setCurrentActivity(this, tempCurrentActivity);
		}

		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_START);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
		// store current configuration orientation
		// This fixed bug with double orientation chnage firing when activity starts in landscape
		previousOrientation = getWindowManager().getDefaultDisplay().getRotation();
	}

	@Override
	/**
	 * When this activity stops, this method fires the javascript 'blur' and 'stop' events. Blur events will only fire
	 * if the activity is not a tab activity.
	 */
	protected void onStop()
	{
		TiApplication.getInstance().activityStopped(this);
		inForeground = false;
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_STOP, null);
		}
		synchronized (lifecycleListeners.synchronizedList()) {
            for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
                try {
                    TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_STOP);

                } catch (Throwable t) {
                    Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
                }
            }
        }
		
		super.onStop();

//		Log.d(TAG, "Activity " + this + " onStop", Log.DEBUG_MODE);

		if (getTiApp().isRestartPending()) {
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_STOP);
		}

		
		KrollRuntime.suggestGC();
	}

	@Override
	/**
	 * When this activity restarts, this method updates the current activity to this and fires javascript 'restart'
	 * event.
	 */
	protected void onRestart()
	{
		inForeground = true;
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_RESTART, null);
		}
		super.onRestart();

//		Log.d(TAG, "Activity " + this + " onRestart", Log.DEBUG_MODE);

		TiApplication tiApp = getTiApp();
		if (tiApp.isRestartPending()) {
			if (!isFinishing()) {
				finish();
			}

			return;
		}

		if (activityProxy != null) {
			// we only want to set the current activity for good in the resume state but we need it right now.
			// save off the existing current activity, set ourselves to be the new current activity temporarily
			// so we don't run into problems when we give the proxy the event
			Activity tempCurrentActivity = tiApp.getCurrentActivity();
			tiApp.setCurrentActivity(this, this);

			activityProxy.fireEvent(TiC.EVENT_RESTART);

			// set the current activity back to what it was originally
			tiApp.setCurrentActivity(this, tempCurrentActivity);
		}
	}

	@Override
	/**
	 * When the activity is about to go into the background as a result of user choice, this method fires the
	 * javascript 'userleavehint' event.
	 */
	protected void onUserLeaveHint()
	{
		Log.d(TAG, "Activity " + this + " onUserLeaveHint", Log.DEBUG_MODE);

		if (getTiApp().isRestartPending()) {
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		if (activityProxy != null) {
			activityProxy.fireEvent(TiC.EVENT_USER_LEAVE_HINT);
		}

		super.onUserLeaveHint();
	}

	@Override
    public void onLowMemory() {
        super.onLowMemory();
        synchronized (lifecycleListeners.synchronizedList()) {
            for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
                try {
                    TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_LOWMEMORY);

                } catch (Throwable t) {
                    Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
                }
            }
        }

    }

	@Override
	/**
	 * When this activity is destroyed, this method removes it from the activity stack, performs
	 * clean up, and fires javascript 'destroy' event.
	 */
	protected void onDestroy()
	{
//		Log.d(TAG, "Activity " + this + " onDestroy", Log.DEBUG_MODE);
		if (activityProxy != null) {
			dispatchCallback(TiC.PROPERTY_ON_DESTROY, null);
		}

		inForeground = false;
		TiApplication tiApp = getTiApp();
		//Clean up dialogs when activity is destroyed.
		releaseDialogs(true);

		if (tiApp.isRestartPending()) {
			super.onDestroy();
			if (!isFinishing()) {
				finish();
			}
			return;
		}

		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					TiLifecycle.fireLifecycleEvent(this, listener, TiLifecycle.LIFECYCLE_ON_DESTROY);

				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
		lifecycleListeners.clear();
		
		unsetupOrientationListener();

		super.onDestroy();

		boolean isFinishing = isFinishing();

		// If the activity is finishing, remove the windowId and supportHelperId so the window and supportHelper can be released.
		// If the activity is forced to destroy by Android OS, keep the windowId and supportHelperId so the activity can be recovered.
		if (isFinishing) {
			int windowId = getIntentInt(TiC.INTENT_PROPERTY_WINDOW_ID, -1);
			TiActivityWindows.removeWindow(windowId);
			TiActivitySupportHelpers.removeSupportHelper(supportHelperId);
		}

		fireOnDestroy();

		if (layout instanceof TiCompositeLayout) {
			Log.d(TAG, "Layout cleanup.", Log.DEBUG_MODE);
			((TiCompositeLayout) layout).removeAllViews();
		}
		layout = null;

		//LW windows
		if (view != null) {
			view.releaseViews(isFinishing);
			view = null;
		}
		
		clearWindowStack(true);

		if (window != null) {
			window.closeFromActivity(isFinishing);
			window = null;
		}

		if (menuHelper != null) {
			menuHelper.destroy();
			menuHelper = null;
		}

		if (activityProxy != null) {
			activityProxy.release();
			activityProxy = null;
		}

		// Don't dispose the runtime if the activity is forced to destroy by Android,
		// so we can recover the activity later.
		KrollRuntime.decrementActivityRefCount(isFinishing);
		KrollRuntime.suggestGC();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);

		// If the activity is forced to destroy by Android, save the supportHelperId so
		// we can get it back when the activity is recovered.
		if (!isFinishing() && supportHelper != null) {
			outState.putInt("supportHelperId", supportHelperId);
		}

		synchronized (instanceStateListeners.synchronizedList()) {
			for (OnInstanceStateEvent listener : instanceStateListeners.nonNull()) {
				try {
					TiLifecycle.fireInstanceStateEvent(outState, listener, TiLifecycle.ON_SAVE_INSTANCE_STATE);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnInstanceStateEvent: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);

		if (savedInstanceState.containsKey("supportHelperId")) {
			supportHelperId = savedInstanceState.getInt("supportHelperId");
			supportHelper = TiActivitySupportHelpers.retrieveSupportHelper(this, supportHelperId);
			if (supportHelper == null) {
				Log.e(TAG, "Unable to retrieve the activity support helper.");
			}
		}
		synchronized (instanceStateListeners.synchronizedList()) {
			for (OnInstanceStateEvent listener : instanceStateListeners.nonNull()) {
				try {
					TiLifecycle.fireInstanceStateEvent(savedInstanceState, listener, TiLifecycle.ON_RESTORE_INSTANCE_STATE);
				} catch (Throwable t) {
					Log.e(TAG, "Error dispatching OnInstanceStateEvent: " + t.getMessage(), t);
				}
			}
		}
	}

	// called in order to ensure that the onDestroy call is only acted upon once.
	// should be called by any subclass
	protected void fireOnDestroy()
	{
		if (!onDestroyFired) {
			if (activityProxy != null) {
				activityProxy.fireEvent(TiC.EVENT_DESTROY);
			}
			onDestroyFired = true;
		}
	}

	protected boolean shouldFinishRootActivity()
	{
		if (window != null)
			return window.shouldExitOnClose();
		return false;
	}

	@Override
	public void finish()
	{
		finish(false);
	}

	public void finish(boolean force)
	{
		super.finish();
		
		if (shouldFinishRootActivity() || force == true) {
			TiApplication app = getTiApp();
			if (app != null) {
				TiRootActivity rootActivity = app.getRootActivity();
				if (rootActivity != null && !(rootActivity.equals(this)) && !rootActivity.isFinishing()) {
					rootActivity.finish();
				} else if (rootActivity == null && !app.isRestartPending()) {
					// When the root activity has been killed and garbage collected and the app is not scheduled to restart,
					// we need to force finish the root activity while this activity has an intent to finish root.
					// This happens when the "Don't keep activities" option is enabled and the user stays in some activity
					// (eg. heavyweight window, tabgroup) other than the root activity for a while and then he wants to back
					// out the app.
					app.setForceFinishRootActivity(true);
				}
			}
		}
	}

	// These activityOnXxxx are all used by TiLaunchActivity when
	// the android bug 2373 is detected and the app is being re-started.
	// By calling these from inside its on onXxxx handlers, TiLaunchActivity
	// can avoid calling super.onXxxx (super being TiBaseActivity), which would
	// result in a bunch of Titanium-specific code running when we don't need it
	// since we are restarting the app as fast as possible. Calling these methods
	// allows TiLaunchActivity to fulfill the requirement that the Android built-in
	// Activity's onXxxx must be called. (Think of these as something like super.super.onXxxx
	// from inside TiLaunchActivity.)
	protected void activityOnPause()
	{
		super.onPause();
	}
	protected void activityOnRestart()
	{
		super.onRestart();
	}
	protected void activityOnResume()
	{
		super.onResume();
	}
	protected void activityOnStop()
	{
		super.onStop();
	}
	protected void activityOnStart()
	{
		super.onStart();
	}
	protected void activityOnDestroy()
	{
		super.onDestroy();
	}

	public void activityOnCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}

	/**
	 * Called by the onCreate methods of TiBaseActivity to determine if an unsupported application
	 * re-launch appears to be occurring.
	 * @param activity The Activity getting the onCreate
	 * @param savedInstanceState The argument passed to the onCreate. A non-null value is a "tell"
	 * that the system is re-starting a killed application.
	 */
	public static boolean isUnsupportedReLaunch(Activity activity, Bundle savedInstanceState)
	{
		// We have to relaunch the app if
		// 1. all the activities have been killed and the runtime has been disposed or
		// 2. the app's hosting process has been killed. In this case, onDestroy or any other method
		// is not called. We can check the status of the root activity to detect this situation.
		if (savedInstanceState != null && !(activity instanceof TiLaunchActivity) &&
				(KrollRuntime.isDisposed() || TiApplication.getInstance().rootActivityLatch.getCount() != 0)) {
			return true;
		}
		return false;
	}
	
	@Override
	protected Dialog onCreateDialog (int id, Bundle args) {
		TiApplication.getInstance().cancelPauseEvent();
		return super.onCreateDialog(id, args);
	}
	
	public boolean getDefaultFullscreen() {
	    return defaultFullscreen;
	}

    public void closeFrontActivities() {
        TiApplication.closeActivitiesInFrontOf(this);
    }
}

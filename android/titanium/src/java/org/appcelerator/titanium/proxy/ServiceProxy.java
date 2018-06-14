/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiServiceBinder;
import org.appcelerator.titanium.TiServiceInterface;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import ti.modules.titanium.TitaniumModule;

import java.lang.reflect.Method;

@Kroll.proxy(creatableInModule=TitaniumModule.class)
/**
 * This is a proxy representation of the Android Service type.
 * Refer to <a href="http://developer.android.com/reference/android/app/Service.html" >Android Service</a> for more details.
 */
public class ServiceProxy extends KrollProxy {

    @Kroll.constant
    public static final String EVENT_STARTED = "started";
    protected Service service = null;
    protected TiServiceInterface tiService = null;
//    private boolean forBoundServices;

    // private Service service;
	private int serviceInstanceId;
	private IntentProxy intentProxy;
    private int notificationId;
    private KrollProxy notificationProxy;
    
    private final String TAG = "TiServiceProxy";

    public static final String NEEDS_STARTING = "needsStarting";
    private boolean stopOnDestroy = false;

    public ServiceProxy() {
        super();
        initLifeCycle(); // created from the app, we can get the root activity
        Intent intent = new Intent(TiApplication.getInstance()
                .getApplicationContext(), serviceClass());
        intent.putExtra(NEEDS_STARTING, false);
        setIntent(intent);
	}

	/**
     * For when creating a service proxy directly, for later binding using
     * bindService()
	 */
    public ServiceProxy(IntentProxy intentProxy) {
		setIntent(intentProxy);
//        forBoundServices = true;
	}

	/**
     * For when a service started via startService() creates a proxy when it
     * starts running
	 */
    public ServiceProxy(Service service, Intent intent,
            Integer serviceInstanceId) {
        super();
        if (service instanceof TiServiceInterface) {
		this.service = service;
            this.tiService = (TiServiceInterface) service;
		setIntent(intent);
		this.serviceInstanceId = serviceInstanceId;
	}

    }

    private void initLifeCycle() {
        if (TiApplication.getInstance().getRootActivity() == null) return;
       TiApplication.getInstance().getRootActivity()
                .addOnLifecycleEventListener(this);
    }
    
    @Override
    public void onDestroy(Activity activity) {
        realUnbind();
    }

    private void realUnbind() {
        if (service != null) {
            if (stopOnDestroy == true)
                tiService.stop();
            unbindService();
        }
        TiApplication.getInstance().getRootActivity()
                .removeOnLifecycleEventListener(this);
    }

    @Kroll.getProperty
	@Kroll.method
    public int getServiceInstanceId() {
		return serviceInstanceId;
	}

	@Kroll.getProperty
    @Kroll.method
    public IntentProxy getIntent() {
		return intentProxy;
	}

    public void setIntent(Intent intent) {
		setIntent(new IntentProxy(intent));
	}

	/**
	 * Sets the IntentProxy.
     * 
     * @param intentProxy
     *            the proxy to set.
	 */
    public void setIntent(IntentProxy intentProxy) {
		this.intentProxy = intentProxy;
	}

	@Kroll.method
    public void start() {
//        if (!forBoundServices) {
//            Log.w(TAG, "Only services created via Ti.Android.createService can be started via the start() command. Ignoring start() request.");
//            return;
//        }
		bindAndInvokeService();
	}

	@Kroll.method
    public void stop() {
        if (service != null) {
//            if (!forBoundServices) {
//                Log.d(TAG, "stop via stopService", Log.DEBUG_MODE);
//                service.stopSelf();
//            } else {
//                unbindService();
//            }
            this.tiService.stop();
        }
    }

    @Kroll.getProperty
    @Kroll.method
    public boolean getRunning() {
        return this.service != null && isServiceRunning();
    }
    
    @Kroll.method
    public void foregroundNotify(int notificationId, KrollProxy notificationProxy)
    {
        // Validate arguments.
        if (notificationId == 0) {
            throw new RuntimeException("Notification ID argument cannot be set to zero.");
        }
        if (notificationProxy == null) {
            throw new RuntimeException("Notification object argument cannot be null.");
        }
        
        // Update service's foreground state.
        synchronized (this)
        {
            this.notificationId = notificationId;
            this.notificationProxy = notificationProxy;
        }
        updateForegroundState();
    }
    
    @Kroll.method
    public void foregroundCancel()
    {
        // Update service's foreground state.
        synchronized (this)
        {
            this.notificationId = 0;
            this.notificationProxy = null;
        }
        updateForegroundState();
    }

    @SuppressWarnings("rawtypes")
    protected Class serviceClass() {
        return Service.class;
    }

    protected boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) TiApplication.getInstance()
                .getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass().getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void bindService() {
        Context context = TiApplication.getInstance();
        boolean result = context.bindService(getIntent().getIntent(),
                this.serviceConnection, Context.BIND_AUTO_CREATE);
        if (!result) {
            Log.e(TAG, "Could not bind", Log.DEBUG_MODE);
        }
    }

    protected void bindAndInvokeService() {
        Context context = TiApplication.getInstance();
//        String className = serviceClass().toString();
        boolean alreadStarted = isServiceRunning();
        Intent intent = getIntent().getIntent();
        if (!alreadStarted) {
            context.startService(intent);
        }
        bindService();

    }

    protected void handleBinder(IBinder binder) {
        TiServiceBinder akbinder = (TiServiceBinder) binder;
        Service localservice = (Service) akbinder.getService();
        if (localservice instanceof TiServiceInterface) {
					ServiceProxy proxy = ServiceProxy.this;
            this.service = localservice;
            this.tiService = (TiServiceInterface) localservice;
            proxy.invokeBoundService();
					proxy.serviceInstanceId = tiService.nextServiceInstanceId();
        }

    }

    protected final ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            unbindService();
        }

        public void onServiceConnected(ComponentName name, IBinder binder) {
            handleBinder(binder);
			}
		};

    public void unbindService() {
		Context context = TiApplication.getInstance();
		if (context == null) {
            Log.w(TAG,
                    "Cannot unbind service.  tiContext.getTiApp() returned null");
			return;
		}

        if (this.tiService != null) {
            this.tiService.unbindProxy(this);
        }

        try {
            context.unbindService(this.serviceConnection);
        } catch (Exception e) {

        }
		this.service = null;
        this.tiService = null;
    }

    protected void invokeBoundService() {
        this.tiService.start(this);
        // Enable the foreground state if configured.
        if ((this.notificationId != 0) && (this.notificationProxy != null)) {
            updateForegroundState();
        }
    }

    @Override
    public boolean fireEvent(String event, Object data) {
        return super.fireEvent(event, data);
    }

    @Override
    public void release() {
        realUnbind();
        super.release();
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android.Service";
	}

	private void updateForegroundState()
	{
		// Do not continue if we don't have access to the service yet.
		if (this.service == null) {
			return;
		}

		// Update the service on the main UI thread.
		runOnMainThread(new Runnable() {
			@Override
			public void run()
			{
				// Fetch the service. (Make sure it hasn't been released.)
				Service service = ServiceProxy.this.service;
				if (service == null) {
					return;
				}

				// Fetch the proxy's notification and ID.
				int notificationId = 0;
				Notification notificationObject = null;
				try {
					// Fetch notification settings from proxy.
					synchronized (ServiceProxy.this)
					{
						notificationId = ServiceProxy.this.notificationId;
						if (notificationId != 0) {
							final String CLASS_NAME =
								"ti.modules.titanium.android.notificationmanager.NotificationProxy";
							Class proxyClass = Class.forName(CLASS_NAME);
							Object object = proxyClass.cast(ServiceProxy.this.notificationProxy);
							if (object != null) {
								Method method = proxyClass.getMethod("buildNotification");
								notificationObject = (Notification) method.invoke(object);
							}
						}
					}

					// If given notification was assigned Titanium's default channel, then make sure it's set up.
					// Note: Notification channels are only supported on Android 8.0 and higher.
					if ((notificationObject != null) && (Build.VERSION.SDK_INT >= 26)) {
						final String CLASS_NAME =
							"ti.modules.titanium.android.notificationmanager.NotificationManagerModule";
						Class managerClass = Class.forName(CLASS_NAME);
						String defaultChannelId = (String) managerClass.getField("DEFAULT_CHANNEL_ID").get(null);
						if (defaultChannelId.equals(notificationObject.getChannelId())) {
							Method method = managerClass.getMethod("useDefaultChannel");
							method.invoke(null);
						}
					}
				} catch (Exception ex) {
					// We want reflection exceptions to cause a crash so that our unit tests will catch it.
					throw new RuntimeException(ex);
				}

				// Enable/Disable the service's foreground state.
				// Note: A notification will be shown in the status bar while enabled.
				if ((notificationId != 0) && (notificationObject != null)) {
					service.startForeground(notificationId, notificationObject);
				} else {
					service.stopForeground(true);
				}
			}
		});
	}
}

/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
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
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

@Kroll.proxy
/**
 * This is a proxy representation of the Android Service type.
 * Refer to <a href="http://developer.android.com/reference/android/app/Service.html" >Android Service</a> for more details.
 */
public class ServiceProxy extends KrollProxy {

    @Kroll.constant
    public static final String EVENT_STARTED = "started";
    protected Service service = null;
    protected TiServiceInterface tiService = null;
    private boolean forBoundServices;

    // private Service service;
    private int serviceInstanceId;
    private IntentProxy intentProxy;
    private final String TAG = "AkServiceProxy";

    public static final String NEEDS_STARTING = "needsStarting";

    private OnLifecycleEvent lifecycleListener = null;

    private boolean stopOnDestroy = false;

    protected String logTAG() {
        return TAG;
    }

    public ServiceProxy() {
        super();
        Log.d(logTAG(), "AkServiceProxy " + this, Log.DEBUG_MODE);
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
        forBoundServices = true;
    }

    /**
     * For when a service started via startService() creates a proxy when it
     * starts running
     */
    public ServiceProxy(Service service, Intent intent,
            Integer serviceInstanceId) {
        super();
        if (service instanceof TiServiceInterface) {
            Log.d(logTAG(), "AkServiceProxy via start Service " + this + "/"
                    + service, Log.DEBUG_MODE);
            this.service = service;
            this.tiService = (TiServiceInterface) service;
            setIntent(intent);
            this.serviceInstanceId = serviceInstanceId;
        }

    }

    private void initLifeCycle() {
        if (lifecycleListener != null
                || TiApplication.getInstance().getRootActivity() == null)
            return;
        lifecycleListener = new OnLifecycleEvent() {
            @Override
            public void onStop(Activity activity) {
            }

            @Override
            public void onStart(Activity activity) {
            }

            @Override
            public void onResume(Activity activity) {
            }

            @Override
            public void onPause(Activity activity) {
            }

            @Override
            public void onDestroy(Activity activity) {
                Log.d(logTAG(), "onDestroy", Log.DEBUG_MODE);
                realUnbind();
            }
        };
        Log.d(logTAG(), "initLifeCycle", Log.DEBUG_MODE);
        TiApplication.getInstance().getRootActivity()
                .addOnLifecycleEventListener(lifecycleListener);
    }

    private void realUnbind() {
        Log.d(logTAG(), "realUnbind", Log.DEBUG_MODE);
        if (service != null) {
            if (stopOnDestroy == true)
                tiService.stop();
            unbindService();
        } else {
            Log.d(logTAG(), "onDestroy: service is null", Log.DEBUG_MODE);
        }
        TiApplication.getInstance().getRootActivity()
                .removeOnLifecycleEventListener(lifecycleListener);
        lifecycleListener = null;
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
        Log.d(logTAG(), "Starting service " + this, Log.DEBUG_MODE);
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
            Log.d(logTAG(), "Stopping service", Log.DEBUG_MODE);
            this.tiService.stop();
        }
    }

    @Kroll.getProperty
    @Kroll.method
    public boolean getRunning() {
        Log.d(logTAG(), "getStarted " + this, Log.DEBUG_MODE);
        return this.service != null && isServiceRunning();
    }

    @SuppressWarnings("rawtypes")
    protected Class serviceClass() {
        return Service.class;
    }

    protected boolean isServiceRunning() {
        Log.d(logTAG(), "isServiceRunning " + serviceClass().toString(),
                Log.DEBUG_MODE);
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
        Log.d(logTAG(), "bindService " + this.serviceConnection, Log.DEBUG_MODE);
        boolean result = context.bindService(getIntent().getIntent(),
                this.serviceConnection, Context.BIND_AUTO_CREATE);
        if (!result) {
            Log.e(logTAG(), "Could not bind", Log.DEBUG_MODE);
        }
    }

    protected void bindAndInvokeService() {
        Context context = TiApplication.getInstance();
        String className = serviceClass().toString();
        Log.d(logTAG(), "bindAndInvokeService " + className, Log.DEBUG_MODE);
        boolean alreadStarted = isServiceRunning();
        Intent intent = getIntent().getIntent();
        if (!alreadStarted) {
            Log.d(logTAG(), "bindAndInvokeService: service not running",
                    Log.DEBUG_MODE);
            context.startService(intent);
        }
        bindService();

    }

    protected void handleBinder(IBinder binder) {
        Log.d(logTAG(), "handleBinder", Log.DEBUG_MODE);
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
            Log.d(logTAG(), "Service disconnected", Log.DEBUG_MODE);
            unbindService();
        }

        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(logTAG(), "Service connected", Log.DEBUG_MODE);
            handleBinder(binder);
        }
    };

    public void unbindService() {
        Context context = TiApplication.getInstance();
        if (context == null) {
            Log.w(logTAG(),
                    "Cannot unbind service.  tiContext.getTiApp() returned null");
            return;
        }

        if (this.tiService != null) {
            this.tiService.unbindProxy(this);
        }

        Log.d(logTAG(), "Unbinding service " + this.serviceConnection,
                Log.DEBUG_MODE);
        try {
            context.unbindService(this.serviceConnection);
        } catch (Exception e) {

        }
        this.service = null;
        this.tiService = null;
    }

    protected void invokeBoundService() {
        Log.d(logTAG(), "invokeBoundService: "
                + this.tiService.getClass().toString() + ":" + this + "/"
                + tiService, Log.DEBUG_MODE);
        this.tiService.start(this);
    }

    @Override
    public boolean fireEvent(String event, Object data) {
        Log.d(logTAG(), "fireEvent " + event, Log.DEBUG_MODE);
        return super.fireEvent(event, data);
    }

    @Override
    public void release() {
        Log.d(logTAG(), "release", Log.DEBUG_MODE);
        realUnbind();
        super.release();
    }

    @Override
    public String getApiName()
    {
        return "Ti.Android.Service";
    }
}

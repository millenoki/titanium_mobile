/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.titanium.proxy.ServiceProxy;
import org.appcelerator.kroll.common.Log;

import com.trevorpage.tpsvg.internal.Gradient.StopColor;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * The base class for Titanium services. To learn more about Services, see the
 * <a href="http://developer.android.com/reference/android/app/Service.html">Android Service documentation</a>.
 */
public class TiBaseService extends Service implements TiServiceInterface{

    static final String TAG = "AkService";
    public static final String TI_SERVICE_INTENT_ID_KEY = "$__TITANIUM_SERVICE_INTENT_ID__$";
    protected AtomicInteger proxyCounter = new AtomicInteger();

    protected ServiceProxy proxy = null;

    private boolean started = false;
    
    protected String logTAG() {
        return TAG;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        KrollRuntime.incrementServiceReceiverRefCount();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        KrollRuntime.decrementServiceReceiverRefCount();
    }
    

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean needsStart = intent == null || intent.getBooleanExtra(ServiceProxy.NEEDS_STARTING, true);
        Log.d(logTAG(), "onStartCommand: " + needsStart, Log.DEBUG_MODE);
        if (this.proxy == null) {
            Log.d(logTAG(), "onStartCommand: first start no proxy", Log.DEBUG_MODE);
        } else {
            Log.d(logTAG(), "onStartCommand: already started", Log.DEBUG_MODE);
        }
        if (needsStart){
            start(this.proxy);
        }

        int result = START_STICKY;
        if (intent != null) result = intent.getIntExtra(TiC.INTENT_PROPERTY_START_MODE,
                result);
        return result;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(logTAG(), "onBind", Log.DEBUG_MODE);
        return new TiServiceBinder(this);
    }

    protected void executeServiceCode() {
        Log.d(logTAG(), "executeServiceCode " + proxy.getProperties(),
                Log.DEBUG_MODE);
        //meant to be overriden
    }
    
    /**
    * Creates and returns a service proxy, also increments the instance id.
    * Each service proxy has a unique instance id.
    * @param intent the intent used to create the proxy.
    * @return service proxy
    */
   protected ServiceProxy createProxy(Intent intent)
   {
       this.proxy = new ServiceProxy(this, intent, proxyCounter.incrementAndGet());
       return this.proxy;
   }

    

    public void start(ServiceProxy proxy) {
        Log.d(logTAG(), "start", Log.DEBUG_MODE);
        if (this.proxy != null) {
            Log.d(logTAG(),
                    "we were already associated to a proxy, must have started on boot",
                    Log.DEBUG_MODE);
            Log.d(logTAG(),
                    "old service instance id"
                            + this.proxy.getServiceInstanceId(), Log.DEBUG_MODE);
            Log.d(logTAG(),
                    "new service instance id" + proxy.getServiceInstanceId(),
                    Log.DEBUG_MODE);
        }

        this.proxy = proxy;
        
        
        if (started == false) {
            started = true;
            executeServiceCode();
        }
        else {
            Log.d(logTAG(), "start: already started", Log.DEBUG_MODE);
        }
        
        if (this.proxy != null) {
            this.proxy.fireEvent(TiC.EVENT_START, new KrollDict());
        }
    }
    
    public void stop(){
        if (this.proxy != null) {
            this.proxy.fireEvent(TiC.EVENT_STOP, new KrollDict());
        }
        if (this.proxy != null) {
            this.proxy.unbindService();
        }
        stopSelf();
    }

    /**
     * Implementing subclasses should use this method to release the proxy.
     * 
     * @param proxy
     *            the proxy to release.
     */
    public void unbindProxy(ServiceProxy proxy) {
        this.proxy = null;
    }

    /**
     * @return next service instance id.
     */
    public int nextServiceInstanceId() {
        return proxyCounter.incrementAndGet();
    }
    

    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "The task that comes from the service's application has been removed.");
        }
        if (this.proxy != null) {
            this.proxy.fireSyncEvent(TiC.EVENT_TASK_REMOVED, null);
        }
    }
}
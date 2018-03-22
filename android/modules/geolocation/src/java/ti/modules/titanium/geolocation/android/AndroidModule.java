/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.geolocation.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiLifecycle.OnActivityResultEvent;
import org.appcelerator.titanium.util.TiConvert;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import ti.modules.titanium.geolocation.GeolocationModule;
import ti.modules.titanium.geolocation.TiLocation;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;


/**
 * AndroidModule exposes all Android specific methods and properties relating to geolocation behavior 
 * associated with Ti.Geolocation.Android to the Titanium developer.  Cross platform API points should 
 * be exposed through GeolocationModule (Ti.Geolocation).
 * 
 * <p>
 * The main purpose of this class beyond providing a Android specific namespace under Ti.Geolocation is 
 * to support managing manual location providers and location rules.
 */
@SuppressWarnings({"rawtypes"})
@Kroll.module(parentModule=GeolocationModule.class)
public class AndroidModule extends KrollModule
	implements Handler.Callback, ConnectionCallbacks, OnConnectionFailedListener
{
	@Kroll.constant public static final String PROVIDER_PASSIVE = LocationManager.PASSIVE_PROVIDER;
	@Kroll.constant public static final String PROVIDER_NETWORK = LocationManager.NETWORK_PROVIDER;
	@Kroll.constant public static final String PROVIDER_GPS = LocationManager.GPS_PROVIDER;

	public HashMap<String, LocationProviderProxy> manualLocationProviders = new HashMap<String, LocationProviderProxy>();
	public ArrayList<LocationRuleProxy> manualLocationRules = new ArrayList<LocationRuleProxy>();
	public boolean manualMode = false;

	private static final String TAG = "AndroidModule";

	private GeolocationModule geolocationModule;
	private TiLocation tiLocation;

	/**
	 * Constructor
	 */
	public AndroidModule()
	{
		super("geolocation.android");

		geolocationModule = (GeolocationModule) TiApplication.getInstance().getModuleByName("geolocation");
		geolocationModule.androidModule = this;
		tiLocation = geolocationModule.tiLocation;
		
		
	}


	/**
	 * Checks if the manual location providers are being used instead of the simple 
	 * or legacy providers
	 * 
	 * @return			<code>true</code> if the manual location providers are being 
	 * 					used, <code>false</code> if not
	 */
	@Kroll.getProperty
	public boolean getManualMode()
	{
		return manualMode;
	}

	/**
	 * Sets whether the manual location providers should be used in place of the 
	 * simple or legacy providers
	 * 
	 * @param manualMode			<code>boolean</code> value to indicate whether 
	 * 								the manual providers should be used
	 */
	@SuppressWarnings("deprecation")
	@Kroll.setProperty
	public void setManualMode(boolean manualMode)
	{
		if (this.manualMode != manualMode) {
			this.manualMode = manualMode;
			if (manualMode) {
				geolocationModule.enableLocationProviders(manualLocationProviders);

			} else {
				if (geolocationModule.legacyModeActive) {
					geolocationModule.enableLocationProviders(geolocationModule.legacyLocationProviders);

				} else {
					geolocationModule.enableLocationProviders(geolocationModule.simpleLocationProviders);
				}
			}
		}
	}

	/**
	 * Creates a new instance of a location provider.  Mimics that normal proxy mechanism 
	 * provided via annotations.  This is needed due to a flaw in how the annotation driven 
	 * proxy creation works in the sub module.
	 * 
	 * @param creationArgs		creation arguments for the proxy that are passed in from Javascript
	 * @return					new instance of a location provider
	 */
	@Kroll.method
	public LocationProviderProxy createLocationProvider(Object creationArgs[])
	{
		String name = null;

		if ((creationArgs.length > 0) && (creationArgs[0] instanceof HashMap)) {
		    HashMap args = (HashMap) creationArgs[0];
		    name = TiConvert.toString(args, TiC.PROPERTY_NAME);
		    if (!args.containsKey(TiC.PROPERTY_MIN_UPDATE_DISTANCE)) {
		        args.put(TiC.PROPERTY_MIN_UPDATE_DISTANCE, geolocationModule.legacyLocationAccuracyLookupResult);
		    }
	          if (!args.containsKey(TiC.PROPERTY_MIN_UPDATE_TIME)) {
	                args.put(TiC.PROPERTY_MIN_UPDATE_TIME, geolocationModule.legacyLocationFrequency);
	            }
//			Object nameProperty = ((HashMap) creationArgs[0]).get(TiC.PROPERTY_NAME);
//			if (nameProperty instanceof String) {
//				if (tiLocation.isProvider((String) nameProperty)) {
//					name = (String) nameProperty;
//				}
//			}
		}

		if (name != null) {
			return new LocationProviderProxy(creationArgs, geolocationModule);

		} else {
			throw new IllegalArgumentException("Invalid provider name, unable to create location provider");
		}
	}

	/**
	 * Creates a new instance of a location rule.  Mimics that normal proxy mechanism 
	 * provided via annotations.  This is needed due to a flaw in how the annotation driven 
	 * proxy creation works in the sub module.
	 * 
	 * @param creationArgs		creation arguments for the proxy that are passed in from Javascript
	 * @return					new instance of a location rule
	 */
	@Kroll.method
	public LocationRuleProxy createLocationRule(Object creationArgs[])
	{
		return new LocationRuleProxy(creationArgs);
	}

	/**
	 * Adds the specified location provider to the list of manual location providers.  If a location 
	 * provider with the same "name" property already exists in the list of manual location 
	 * providers then the existing provider will be removed and the specified one will be added in it's
	 * place.
	 * 
	 * @param locationProvider		the location provider to add
	 */
	private void doAddLocationProvider(LocationProviderProxy locationProvider)
	{
		String providerName = TiConvert.toString(locationProvider.getProperty(TiC.PROPERTY_NAME));
		if (!(tiLocation.isProvider(providerName))) {
			Log.e(TAG, "Unable to add location provider [" + providerName + "], does not exist");

			return;
		}

		// if doesn't exist, add new - otherwise update properties
		LocationProviderProxy existingLocationProvider = manualLocationProviders.get(providerName);
		if (existingLocationProvider == null) {
			manualLocationProviders.put(providerName, locationProvider);

		} else {
			manualLocationProviders.remove(providerName);

			if (manualMode && (geolocationModule.numLocationListeners > 0)) {
				geolocationModule.unregisterLocationProvider(existingLocationProvider);
			}

			manualLocationProviders.put(providerName, locationProvider);
		}

		if (manualMode && (geolocationModule.numLocationListeners > 0)) {
			geolocationModule.registerLocationProvider(locationProvider);

		}
	}

	/**
	 * Removed the specified location provider from the list of manual location providers.
	 * 
	 * @param locationProvider		the location provider to remove
	 */
	private void doRemoveLocationProvider(LocationProviderProxy locationProvider)
	{
		manualLocationProviders.remove(locationProvider.getName());
		if (manualMode && (geolocationModule.numLocationListeners > 0)) {
			geolocationModule.unregisterLocationProvider(locationProvider);
		}
	}
	
	@Kroll.method
    public void addLocationProvider(final Object value)
    {
	    if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    addLocationProvider(value);
                }
            }, true);
            return;
        }
        Object[] array;
        if (value instanceof Object[]) {
            array  = (Object[]) value;
        }else {
            array  = new Object[]{value};
        }
        for (int i = 0; i < array.length; i++) {
            LocationProviderProxy providerProxy  = providerFromObject(array[i]);
            if (providerProxy != null) {
                doAddLocationProvider(providerProxy);
            }
        }
    }
    
    @Kroll.method
    public void setLocationProviders(final Object value)
    {
        if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    setLocationProviders(value);
                }
            }, true);
            return;
        }
        this.removeAllLocationProviders();
        this.addLocationProvider(value);
    }

    /**
     * Removed the specified location rule from the list of manual location rules.
     * 
     * @param locationRule      the location rule to remove
     */
    @Kroll.method
    public void removeLocationProvider(final Object value)
    {
        if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    removeLocationProvider(value);
                }
            }, true);
            return;
        }
        if (value == null) return;
        Object[] array;
        if (value instanceof Object[]) {
            array  = (Object[]) value;
        }else {
            array  = new Object[]{value};
        }
        for (int i = 0; i < array.length; i++) {
            Object provider = array[i] ;
            if (provider instanceof LocationProviderProxy) {
                doRemoveLocationProvider((LocationProviderProxy)provider);
            }
        }
    }

    @Kroll.method
    public void removeAllLocationProviders()
    {
        removeLocationProvider(manualLocationProviders.values());
    }

	private LocationRuleProxy ruleFromObject(Object object) {
        if (object instanceof HashMap) {
            LocationRuleProxy result =(LocationRuleProxy)  KrollProxy.createProxy(LocationRuleProxy.class, null,
                    new Object[] { object }, null);
            result.updateKrollObjectProperties();
            return result;
        }
        if (object instanceof LocationRuleProxy) {
            return (LocationRuleProxy) object;
        }
        return null;
	}
	
	private LocationProviderProxy providerFromObject(Object object) {
	    if (object instanceof String) {
            LocationProviderProxy result = new LocationProviderProxy((String)object);
            result.setProviderListener(geolocationModule);
            return result;
        }
        if (object instanceof HashMap) {
            LocationProviderProxy result =(LocationProviderProxy)  KrollProxy.createProxy(LocationProviderProxy.class, null,
                    new Object[] { object }, null);
            result.setProviderListener(geolocationModule);
            return result;
        }
        if (object instanceof LocationProviderProxy) {
            return (LocationProviderProxy) object;
        }
        return null;
    }
	/**
	 * Adds the specified location rule to the list of manual location rules.
	 * 
	 * @param locationRule		the location rule to add
	 */
	@Kroll.method
	public void addLocationRule(Object value)
	{
		Object[] array;
        if (value instanceof Object[]) {
            array  = (Object[]) value;
        }else {
            array  = new Object[]{value};
        }
        List<LocationRuleProxy> toAdd = new ArrayList<LocationRuleProxy>();
        for (int i = 0; i < array.length; i++) {
            LocationRuleProxy ruleProxy  = ruleFromObject(array[i]);
            if (ruleProxy != null && !manualLocationRules.contains(ruleProxy)) {
                toAdd.add(ruleProxy);
            }
        }
        int addCount = toAdd.size();
        if (addCount > 0) {
            manualLocationRules.addAll(toAdd);
        }
	}
	
	@Kroll.method
    public void setLocationRules(Object value)
    {
        this.removeAllLocationRules();
        this.addLocationRule(value);
    }

	/**
	 * Removed the specified location rule from the list of manual location rules.
	 * 
	 * @param locationRule		the location rule to remove
	 */
	@Kroll.method
	public void removeLocationRule(Object value)
	{
	    if (value == null) return;
        Object[] array;
        if (value instanceof Object[]) {
            array  = (Object[]) value;
        }else {
            array  = new Object[]{value};
        }
        List<LocationRuleProxy> toRemove = new ArrayList<LocationRuleProxy>();
        for (int i = 0; i < array.length; i++) {
            Object rule = array[i] ;
            if (rule instanceof LocationRuleProxy) {
                toRemove.add((LocationRuleProxy) rule);
            }
        }
        if (toRemove.size() > 0) {
            manualLocationRules.removeAll(toRemove);
        }
	}

	@Kroll.method
	public void removeAllLocationRules()
	{
		manualLocationRules.clear();
	}

	@Override
	public String getApiName()
	{
		return "Ti.Geolocation.Android";
	}
	
	@Kroll.method
    public int getProviderState(String provider)
    {
        return tiLocation.getProviderState(provider);
    }
	
	   
    @Kroll.method
    public boolean isProviderEnabled(String name)
    {
        return tiLocation.isProviderEnabled(name);
    }

    @Override
    public void onConnected(Bundle arg0) {
        if (hasListeners("api.connected", false)) {
            fireEvent("api.connected", null, false, false);
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        if (hasListeners("api.suspended", false)) {
            KrollDict data = new KrollDict();
            data.putCodeAndMessage(arg0, "The Google Api connection was suspended");
            fireEvent("api.suspended", null, false, false);
        }
    }
    
    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        if (hasListeners("api.failed", false)) {
            KrollDict data = new KrollDict();
            data.putCodeAndMessage(arg0.getErrorCode(), arg0.getErrorMessage());
            fireEvent("api.failed", null, false, false);
        }
    }
    
    private LocationRequest mLocationRequest;
    PendingResult<LocationSettingsResult> result;
    private GoogleApiClient mGoogleApiClient;
   @Kroll.method
    public void checkLocationSettings(@Kroll.argument(optional=true) final Object options, @Kroll.argument(optional=true) final KrollFunction callback) {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(new ConnectionCallbacks() {
                        
                        @Override
                        public void onConnectionSuspended(int arg0) {
                            // TODO Auto-generated method stub
                            
                        }
                        
                        @Override
                        public void onConnected(Bundle arg0) {
                            checkLocationSettings(options, callback);
                        }
                    })
                    .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                        
                        @Override
                        public void onConnectionFailed(ConnectionResult arg0) {
                            if (callback != null) {
                                KrollDict response = new KrollDict();
                                response.putCodeAndMessage(arg0.getErrorCode(), arg0.getErrorMessage());
                                callback.callAsync(getKrollObject(), response);
                            }
                        }
                    }).build();
            mGoogleApiClient.connect();
            return;
        }
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(30 * 1000);
        mLocationRequest.setFastestInterval(5 * 1000);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);

        result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                //final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        if (callback != null) {
                            KrollDict response = new KrollDict();
                            response.putCodeAndMessage(0, null);
                            callback.callAsync(getKrollObject(), response);
                        }
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            TiBaseActivity activity = (TiBaseActivity) TiApplication.getAppCurrentActivity();
                            activity.addOnActivityResultListener(new OnActivityResultEvent() {
                                
                                @Override
                                public void onActivityResult(Activity activity, int requestCode,
                                        int resultCode, Intent data) {
                                    if (callback != null) {
                                        KrollDict response = new KrollDict();
                                        response.putCodeAndMessage(-1-resultCode, null);
                                        callback.callAsync(getKrollObject(), response);
                                    }
                                }
                            });
                            status.startResolutionForResult(
                                    activity,
                                    TiC.PERMISSION_CODE_GOOGLE_API_LOCATION);
                        } catch (IntentSender.SendIntentException e) {
                            Log.d(TAG, "", e);
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        //...
                        KrollDict response = new KrollDict();
                        response.putCodeAndMessage(status.getStatusCode(), "Location settings are not satisfied");
                        callback.callAsync(getKrollObject(), response);
                        break;
                }
            }
        });
    }
}


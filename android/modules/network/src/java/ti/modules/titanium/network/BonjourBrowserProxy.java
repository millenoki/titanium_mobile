package ti.modules.titanium.network;

import java.util.HashMap;
import java.util.regex.Pattern;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

@Kroll.proxy(creatableInModule = NetworkModule.class, propertyAccessors = {
        "domain", "serviceType" })
public class BonjourBrowserProxy extends KrollProxy {
    protected static final String TAG = "BonjourServiceProxy";
    static NsdManager mNsdManager = null;
    private String mServiceType = null;
    private String mDomain = null;
    private boolean mDiscovering = false;
    private boolean mResolveOnDiscover = false;
    private Pattern mNamePattern = null;

    public BonjourBrowserProxy() {
        super();
        if (mNsdManager == null) {
            mNsdManager = (NsdManager) TiApplication.getInstance()
                    .getSystemService(Context.NSD_SERVICE);
        }
    }

    public BonjourBrowserProxy(TiContext tiContext) {
        this();
    }

    @Override
    public void handleCreationDict(HashMap dict) {
        super.handleCreationDict(dict);

        if (dict.containsKey("serviceType")) {
            mServiceType = TiConvert.toString(dict, "serviceType");
        }

        if (dict.containsKey("domain")) {
            mDomain = TiConvert.toString(dict, "domain");
        }
        if (dict.containsKey("resolveOnDiscover")) {
            mResolveOnDiscover = TiConvert.toBoolean(dict, "resolveOnDiscover");
        }
        if (dict.containsKey("nameRegex")) {
            mNamePattern = Pattern.compile(TiConvert.toString(dict, "nameRegex"));
        }
    }

    private void fireEventForService(String type, NsdServiceInfo serviceInfo) {

        if (!hasListeners(type)) {
            return;
        }
        KrollDict data = new KrollDict();
        data.put("name", serviceInfo.getServiceName());
        data.put("type", serviceInfo.getServiceType());
        data.put("host", serviceInfo.getHost());
        data.put("port", serviceInfo.getPort());
        if (serviceInfo.getPort() != -1) {
            data.put("addresses",
                    new String[] { serviceInfo.getHost().toString() + ":"
                            + serviceInfo.getPort() });
        } else {
            data.put("addresses",
                    new String[] { serviceInfo.getHost().toString() });
        }
        fireEvent(type, data, false, false);
    }

    final private NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed" + errorCode);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            fireEventForService("resolve", serviceInfo);
        }
    };

    // Instantiate a new DiscoveryListener
    final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        // Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.d(TAG, "Service discovery started");
            mDiscovering = true;
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            // A service was found! Do something with it.
            Log.d(TAG, "Service discovery success" + serviceInfo);
            if (serviceInfo.getServiceType().indexOf(mServiceType) == 0
                    && (mNamePattern == null || mNamePattern
                            .matcher(serviceInfo.getServiceName()).matches())) {
                if (mResolveOnDiscover) {
                    mNsdManager.resolveService(serviceInfo, mResolveListener);
                } else {
                    fireEventForService("discover", serviceInfo);
                }
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "service lost" + serviceInfo);
            fireEventForService("lost", serviceInfo);

        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.i(TAG, "Discovery stopped: " + serviceType);
            mDiscovering = false;
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }
    };

    @Kroll.setProperty
    @Kroll.method
    public void setServiceType(String value) {
        if (mServiceType != value) {
            mServiceType = value;
            if (mDiscovering) {
                stopSearch();
                mDiscovering = false; // to force search
                search();
            }
        }
    }

    @Kroll.method
    public void stopSearch() {
        if (mDiscovering) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
    }

    @Kroll.method
    public void search() {
        if (!mDiscovering) {
            mNsdManager.discoverServices(mServiceType,
                    NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }

    }
}

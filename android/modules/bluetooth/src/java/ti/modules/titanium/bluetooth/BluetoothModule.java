/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.bluetooth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConvert;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

@Kroll.module
public class BluetoothModule extends KrollModule {
    private static final String TAG = "BluetoothModule";

    // Constants that indicate the current connection state
    @Kroll.constant
    public static final int STATE_NONE = 0; // we're doing nothing
    @Kroll.constant
    public static final int STATE_LISTEN = 1; // now listening for incoming
                                              // connections
    @Kroll.constant
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing
                                                  // connection
    @Kroll.constant
    public static final int STATE_CONNECTED = 3; // now connected to a remote
                                                 // device

    @Kroll.constant
    public static final int ERROR_BT_NOT_SUPPORTED = -2;
    @Kroll.constant
    public static final int ERROR_BT_NOT_ENABLED = -3;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Local Bluetooth adapter
    private static BluetoothAdapter sBluetoothAdapter = null;
    // private List<BluetoothDevice> mPairedDevicesArrayAdapter;
    private List<BluetoothDevice> mNewDevicesArrayAdapter;
    

    private boolean registeredForConnect = false;
    private boolean registeredForDisconnect = false;
    private boolean discovering = false;

    KrollFunction fDiscoveryCallback = null;

    public static BluetoothAdapter getBTAdapter() {
        if (sBluetoothAdapter == null) {
            sBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        return sBluetoothAdapter;
    }

    public BluetoothModule() {
        super();
        // mPairedDevicesArrayAdapter = new ArrayList<BluetoothDevice>();
        mNewDevicesArrayAdapter = new ArrayList<BluetoothDevice>();

        // Get a set of currently paired devices
        // Set<BluetoothDevice> pairedDevices =
        // mBluetoothAdapter.getBondedDevices();
        // If there are paired devices, add each one to the ArrayAdapter
        // if (pairedDevices.size() > 0) {
        // for (BluetoothDevice device : pairedDevices) {
        // mPairedDevicesArrayAdapter.add(device);
        // }
        // }
    }

    public BluetoothModule(TiContext tiContext) {
        this();
    }

    @Kroll.onAppCreate
    public static void onAppCreate(TiApplication app) {
    }

    @Override
    protected void initActivity(Activity activity) {
        super.initActivity(activity);
        if (getBTAdapter() == null) {
            if (hasListeners(TiC.EVENT_ERROR)) {
                KrollDict data = new KrollDict();
                data.putCodeAndMessage(ERROR_BT_NOT_SUPPORTED,
                        "Bluetooth not supported");
                fireEvent(TiC.EVENT_ERROR, data, false, false);
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction("android.bluetooth.device.action.DISAPPEARED");
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(mReceiver, filter);
    }

    @Override
    public void eventListenerAdded(String type, int count,
            final KrollProxy proxy) {
        super.eventListenerAdded(type, count, proxy);
        if (type == "connected" && !registeredForConnect) {
            registeredForConnect = true;
        } else if (type == "disconnected" && !registeredForDisconnect) {
            registeredForDisconnect = true;
        }
    }

    @Override
    public void eventListenerRemoved(String type, int count, KrollProxy proxy) {
        super.eventListenerRemoved(type, count, proxy);
        if (type == "connected" && count == 1 && registeredForConnect) {
            registeredForConnect = false;
        } else if (type == "disconnected" && count == 1
                && registeredForDisconnect) {
            registeredForDisconnect = false;
        }
    }

    @Override
    public void onResume(Activity activity) {
        super.onResume(activity);
    }

    @Override
    public void onPause(Activity activity) {
        super.onPause(activity);
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return;
        }
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
    }

    @Override
    public void onStop(Activity activity) {
        super.onStop(activity);
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return;
        }
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
    }

    @Override
    public void onDestroy(Activity activity) {
        activity.unregisterReceiver(mReceiver);
        super.onDestroy(activity);
    }

    @Override
    public String getApiName() {
        return "Ti.Bluetooth";
    }

    public static String fromHexString(String hex) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            str.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return str.toString();
    }

    private static final String HEX_CHARSET = "0123456789ABCDEF";

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        int datalength = (int) Math.ceil((double) len / 2);
        byte[] data = new byte[datalength];
        for (int i = 0; i < len; i++) {
            if (HEX_CHARSET.indexOf(s.charAt(i)) != -1) {
                data[i / 2] = (byte) (Character.digit(s.charAt(i), 16) << 4);
                if (i < len - 1) {
                    i++;
                    if (HEX_CHARSET.indexOf(s.charAt(i)) != -1)
                        data[(i - 1) / 2] += (byte) (Character.digit(
                                s.charAt(i), 16));
                }
            }
        }
        return data;
    }

    private Activity getCurrentOrRootActivity() {
        Activity activity = TiApplication.getAppCurrentActivity();
        if (activity == null) {
            return TiApplication.getAppRootOrCurrentActivity();
        }
        return activity;
    }

    private List<CommandNoReturn> onConnectCommand = new ArrayList<CommandNoReturn>();

    private void enableBT() {
        if (!getBTAdapter().isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            Activity activity = getCurrentOrRootActivity();
            if (activity != null) {
                TiActivitySupport activitySupport = (TiActivitySupport) activity;

                activitySupport.launchActivityForResult(enableIntent,
                        REQUEST_ENABLE_BT, new TiActivityResultHandler() {

                            @Override
                            public void onResult(Activity activity,
                                    int requestCode, int resultCode, Intent data) {
                                Log.d(TAG, "onActivityResult " + resultCode);
                                switch (requestCode) {
                                case REQUEST_CONNECT_DEVICE_SECURE:
                                    break;
                                case REQUEST_CONNECT_DEVICE_INSECURE:
                                    break;
                                case REQUEST_ENABLE_BT:
                                    break;
                                }
                            }

                            @Override
                            public void onError(Activity activity,
                                    int requestCode, Exception e) {
                                if (hasListeners(TiC.EVENT_ERROR)) {
                                    KrollDict data = new KrollDict();
                                    data.putCodeAndMessage(
                                            ERROR_BT_NOT_ENABLED,
                                            e.getMessage());
                                    fireEvent(TiC.EVENT_ERROR, data, false,
                                            false);
                                }
                            }
                        });

            }
        }
    }

    private void ensureDiscoverable() {
        Log.d(TAG, "ensure discoverable");
        if (getBTAdapter().getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            Activity activity = getCurrentOrRootActivity();
            if (activity != null) {
                activity.startActivity(discoverableIntent);
            }
        }
    }

    private void startDiscovery() {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            onConnectCommand.add(new CommandNoReturn() {
                @Override
                public void execute() {
                    startDiscovery();
                }
            });
            enableBT();
            return;
        }
        // If we're already discovering, stop it
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        adapter.startDiscovery();
    }
    
    @Kroll.method
    public void discover(KrollFunction onDone) {
        fDiscoveryCallback = onDone;
        startDiscovery();
    }

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed
                // already
                if (discovering) {
                    mNewDevicesArrayAdapter.add(device);
                } else {
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        
                    } else {
                        
                    }
                }
                if (hasListeners("found")) {
                    KrollDict data = new KrollDict();
                    data.put("device", dictFromDevice(device));
                    data.put("discovering", discovering);
                    fireEvent("found", data, false, false);
                }

                // When discovery is finished, change the Activity title
            } else if ("android.bluetooth.device.action.DISAPPEARED"
                    .equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed
                // already

                if (discovering) {
                    mNewDevicesArrayAdapter.remove(device);
                }
                if (hasListeners("disconnected")) {
                    KrollDict data = new KrollDict();
                    data.put("device", dictFromDevice(device));
                    fireEvent("disconnected", data, false, false);
                }

                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mNewDevicesArrayAdapter.clear();
                discovering = true;
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                discovering = false;
                if (fDiscoveryCallback != null) {
                    List<KrollDict> result = new ArrayList<KrollDict>();
                    for (BluetoothDevice device : mNewDevicesArrayAdapter) {
                        result.add(dictFromDevice(device));
                    }
                    fDiscoveryCallback.callAsync(getKrollObject(), new Object[] {result.toArray()});
                    fDiscoveryCallback = null;
                }
                if (hasListeners("discovery")) {
                    KrollDict data = new KrollDict();
                    data.put("devices", mNewDevicesArrayAdapter.toArray());
                    fireEvent("discovery", data, false, false);
                }
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if (hasListeners(TiC.EVENT_CHANGE)) {
                    KrollDict data = new KrollDict();
                    data.put(TiC.PROPERTY_STATE, state);
                    data.put(TiC.PROPERTY_ENABLED, getBTAdapter().isEnabled());
                    fireEvent(TiC.EVENT_CHANGE, data, false, false);
                }
                if (state == BluetoothAdapter.STATE_ON
                        && onConnectCommand.size() > 0) {
                    for (CommandNoReturn command : onConnectCommand) {
                        command.execute();
                    }
                }
                onConnectCommand.clear();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {              
                final int state        = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState    = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                
                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    if (hasListeners("pairing")) {
                        KrollDict data = new KrollDict();
                        data.put("device", dictFromDevice(device));
                        data.put("paired", true);
                        fireEvent("pairing", data, false, false);
                    }
                }
                else if(state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    
                    if (hasListeners("pairing")) {
                        KrollDict data = new KrollDict();
                        data.put("device", dictFromDevice(device));
                        data.put("paired", false);
                        fireEvent("pairing", data, false, false);
                    }
                }
           }
        }
    };

    public static KrollDict dictFromDevice(BluetoothDevice device) {
        KrollDict data = new KrollDict();
        data.put("paired",
                device.getBondState() == BluetoothDevice.BOND_BONDED);
        data.put("name", device.getName());
        data.put("address", device.getAddress());
        return data;
    }

    @Kroll.method
    @Kroll.getProperty
    public Object getConnectedDevices() {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return null;
        }
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        List<KrollDict> result = new ArrayList<KrollDict>();
        for (BluetoothDevice device : pairedDevices) {
            result.add(dictFromDevice(device));
        }
        return result.toArray();
    }

    @Kroll.method
    public void pairDevice(final HashMap options) {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null || options == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            onConnectCommand.add(new CommandNoReturn() {
                @Override
                public void execute() {
                    pairDevice(options);
                }
            });
            enableBT();
            return;
        }
        String address = TiConvert.toString(options, "address", null);
        if (address != null) {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            if (device != null) {
                pairDevice(device);
            }
        }
    }
    
    @Kroll.method
    public void unpairDevice(final HashMap options) {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null || options == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            onConnectCommand.add(new CommandNoReturn() {
                @Override
                public void execute() {
                    unpairDevice(options);
                }
            });
            enableBT();
            return;
        }
        String address = TiConvert.toString(options, "address", null);
        if (address != null) {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            if (device != null ) {
                unpairDevice(device);
            }
        }
    }

    @Kroll.method
    public void enableBluetooth() {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return;
        }
        enableBT();
    }

    @Kroll.method
    public void disableBluetooth() {
        BluetoothAdapter adapter = getBTAdapter();
        if (adapter == null) {
            return;
        }
        if (adapter.isEnabled()) {
            adapter.disable();
        }
    }

    public static boolean pairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("createBond",
                    (Class[]) null);
            return (Boolean) method.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean unpairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("removeBond",
                    (Class[]) null);
            return (Boolean) method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Kroll.method
    @Kroll.getProperty
    public boolean getSupported() {
        BluetoothAdapter adapter = getBTAdapter();
        return adapter != null;
    }
    
    @Kroll.method
    @Kroll.getProperty
    public boolean getDiscovering() {
        BluetoothAdapter adapter = getBTAdapter();
        return adapter != null && adapter.isDiscovering();
    }
    
    @Kroll.method
    @Kroll.getProperty
    public boolean getEnabled() {
        BluetoothAdapter adapter = getBTAdapter();
        return adapter != null && adapter.isEnabled();
    }
}

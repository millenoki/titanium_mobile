/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.UUID;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;

@Kroll.proxy(creatableInModule=BluetoothModule.class, propertyAccessors = {
	 TiC.PROPERTY_ADDRESS
})
public class DeviceProxy extends KrollProxy
{
	private static final String TAG = "BluetoothDeviceProxy";
	
	// Member fields
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
  
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    
    private BluetoothDevice mDevice = null;
    private String mMacAdress = null;
    
    private UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String mSocketServiceName = "TiBluetoothDevice";
    
    public BluetoothDevice getDevice() {
        if (mDevice == null) {
            if (mMacAdress != null) {
                mDevice = BluetoothModule.getBTAdapter().getRemoteDevice(mMacAdress);
            } else if  (mUUID != null) {
                
            }
        }
        return mDevice;
    }
    
	public DeviceProxy()
	{
		super();
	}

	public DeviceProxy(TiContext tiContext)
	{
		this();
	}
	
	public void handleCreationDict(KrollDict dict)
    {
        super.handleCreationDict(dict);
        if (hasProperty(TiC.PROPERTY_ADDRESS)) {
            mMacAdress = TiConvert.toString(dict, TiC.PROPERTY_ADDRESS, "");
        }
        if (hasProperty("UUID")) {
            mUUID = UUID.fromString(TiConvert.toString(dict, "UUID", "00001101-0000-1000-8000-00805F9B34FB"));
         }
    }
	
	// The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {              
                final int state        = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState    = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != getDevice()) {
                    return;
                }
                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    if (hasListeners("pairing")) {
                        KrollDict data = new KrollDict();
                        data.put("device", BluetoothModule.dictFromDevice(getDevice()));
                        data.put("paired", true);
                        fireEvent("pairing", data, false, false);
                    }
                }
                else if(state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    
                    if (hasListeners("pairing")) {
                        KrollDict data = new KrollDict();
                        data.put("device", BluetoothModule.dictFromDevice(getDevice()));
                        data.put("paired", false);
                        fireEvent("pairing", data, false, false);
                    }
                }
           }
        }
    };

	@Override
	protected void initActivity(Activity activity) {
		super.initActivity(activity);
		((TiBaseActivity) activity).addOnLifecycleEventListener(this);
		IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        activity.registerReceiver(mReceiver, filter);
	}
	
//	@Override
//    public void onResume(Activity activity) {
//        super.onResume(activity);
//    }

//    @Override
//    public void onPause(Activity activity) {
//        super.onPause(activity);
//    }

    @Override
    public void onStop(Activity activity) {
        super.onStop(activity);
        stop();
    }

    @Override
    public void onDestroy(Activity activity) {
        activity.unregisterReceiver(mReceiver);
        super.onDestroy(activity);
    }
	
	private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            StringBuffer sb = new StringBuffer();
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothModule.STATE_CONNECTED:
                    fireEvent("connected");
                    break;
                case BluetoothModule.STATE_CONNECTING:
                    break;
                case BluetoothModule.STATE_LISTEN:
                case BluetoothModule.STATE_NONE:
                    fireEvent("disconnected");
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                
                for (int i = 0; i < writeBuf.length; i ++) {
                    sb.append(String.format("%02X", writeBuf[i]));
                }

                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                
                for (int i = 0; i < msg.arg1; i ++) {
                    sb.append(String.format("%02X", readBuf[i]));
                }
                break;
//            case MESSAGE_DEVICE_NAME:
//                // save the connected device's name
//                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
//                Toast.makeText(getApplicationContext(), "Connected to "
//                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
//                break;
            }
        }
    };
    
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = BluetoothModule.getBTAdapter().listenUsingRfcommWithServiceRecord(mSocketServiceName,
                        mUUID);
                } else {
                    tmp = BluetoothModule.getBTAdapter().listenUsingInsecureRfcommWithServiceRecord(
                            mSocketServiceName, mUUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != BluetoothModule.STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (DeviceProxy.this) {
                        switch (mState) {
                        case BluetoothModule.STATE_LISTEN:
                        case BluetoothModule.STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice(),
                                    mSocketType);
                            break;
                        case BluetoothModule.STATE_NONE:
                        case BluetoothModule.STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private String mSocketType;

        public ConnectThread(boolean secure) {
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = mDevice.createRfcommSocketToServiceRecord(mUUID);
                } else {
                    tmp = mDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            BluetoothModule.getBTAdapter().cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (DeviceProxy.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    long timestamp = (new Date()).getTime();
                    bytes = mmInStream.read(buffer);
                    if (bytes >= 0 && hasListeners("read")) {
                        KrollDict data = new KrollDict();
                        data.put(TiC.PROPERTY_TIMESTAMP, timestamp);
                        data.put(TiC.PROPERTY_LENGTH, bytes);
                        data.put(TiC.PROPERTY_DATA, TiBlob.blobFromObject(buffer));
                    }
                    // Send the obtained bytes to the UI Activity
//                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
//                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
//                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
//                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(BluetoothModule.STATE_NONE);
        // Start the service over to restart listening mode
//        start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {

        setState(BluetoothModule.STATE_NONE);
        // Start the service over to restart listening mode
//        start();
    }
    
    private synchronized void setState(int state) {
        if (mState == state) {
            return;
        }
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        boolean connected = getConnected();
        if (hasListeners(TiC.EVENT_CHANGE)) {
            KrollDict data = new KrollDict();
            data.put(TiC.PROPERTY_STATE, mState);
            data.put("connected", connected);
            data.put("paired", getPaired());
            fireEvent(TiC.EVENT_CHANGE, data, false, false);
        }
        if (connected && hasListeners("connected")) {
            fireEvent("connected", null, false, false);
        } else if (mState == BluetoothModule.STATE_NONE && hasListeners("disconnected")) {
            fireEvent("disconnected", null, false, false);
        }
        // Give the new state to the Handler so the UI Activity can update
//        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }
    
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(BluetoothModule.STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(boolean secure) {
        Log.d(TAG, "connect to: " + mDevice);

        // Cancel any thread attempting to make a connection
        if (mState == BluetoothModule.STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        
        BluetoothDevice device = getDevice();
        if (device == null) {
            return;
        }
        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(secure);
        mConnectThread.start();
        setState(BluetoothModule.STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
//        mDevice = device;
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        setState(BluetoothModule.STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setState(BluetoothModule.STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != BluetoothModule.STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    
    

	@Override
	public String getApiName()
	{
		return "Ti.Bluetooth.Device";
	}
	
	@Kroll.method @Kroll.getProperty
    public boolean getConnected() {
        return getState() == BluetoothModule.STATE_CONNECTED;
    }
	
	@Kroll.method @Kroll.getProperty
    public boolean getPaired() {
        return mDevice != null && mDevice.getBondState() == BluetoothDevice.BOND_BONDED;
    }
	
	@Kroll.method
    public void pair() {
	    if (!getPaired()) {
	        BluetoothModule.pairDevice(mDevice);
	    }
    }
	
	@Kroll.method
    public void connect() {
	    connect(false);
    }
	
	@Kroll.method
    public void disconnect() {
	    stop();
    }
	
	@Kroll.method
    public void send(Object args)
	{
	    synchronized (this) {
            if (mState != BluetoothModule.STATE_CONNECTED) return;
        }
	    if (args instanceof String) {
	        try {
                write((TiConvert.toString(args)).getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
	    } else if (args instanceof Object[]) {
	        //supposed to be a byte array
//	        [self sendData: [NSKeyedArchiver archivedDataWithRootObject:args]];
	    } else if (args instanceof TiBlob) {
            write(((TiBlob)args).getBytes());
	    }
	}
}

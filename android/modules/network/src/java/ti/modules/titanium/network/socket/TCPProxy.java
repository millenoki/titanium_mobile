/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.network.socket;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.io.TiStream;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiStreamHelper;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import ti.modules.titanium.BufferProxy;

@SuppressLint("NewApi")
@Kroll.proxy(creatableInModule=SocketModule.class)
public class TCPProxy extends KrollProxy implements TiStream
{
	private static final String TAG = "TCPProxy";

	//private boolean initialized = false;
	private Socket clientSocket = null;
	private ServerSocket serverSocket = null;
	private boolean accepting = false;
	private KrollDict acceptOptions = null;
	private int state = 0;
	private InputStream inputStream = null;


	public TCPProxy()
	{
		super();
		state = SocketModule.INITIALIZED;
	}

	@Kroll.method
	public void connect() throws Exception
	{
		if ((state != SocketModule.LISTENING) && (state != SocketModule.CONNECTED)) {
			Object host = getProperty("host");
			Object port = getProperty("port");
			if((host != null) && (port != null) && (TiConvert.toInt(port) > 0)) {
				new ConnectedSocketThread().start();

			} else {
				throw new IllegalArgumentException("Unable to call connect, socket must have a valid host and port");
			}

		} else {
			throw new Exception("Unable to call connect on socket in <" + state + "> state");
		}
	}

	@Kroll.method
	public void listen() throws Exception
	{
		if ((state != SocketModule.LISTENING) && (state != SocketModule.CONNECTED)) {
			Object port = getProperty("port");
			Object listenQueueSize = getProperty("listenQueueSize");

			try {
				if ((port != null) && (listenQueueSize != null)) {
					serverSocket = new ServerSocket(TiConvert.toInt(port), TiConvert.toInt(listenQueueSize));

				} else if (port != null) {
					serverSocket = new ServerSocket(TiConvert.toInt(port));

				} else {
					serverSocket = new ServerSocket();
				}

				new ListeningSocketThread().start();
				state = SocketModule.LISTENING;

			} catch (IOException e) {
				e.printStackTrace();
				state = SocketModule.ERROR;
				throw new Exception("Unable to listen, IO error");
			}

		} else {
			throw new Exception("Unable to call listen on socket in <" + state + "> state");
		}
	}

	@Kroll.method
	public void accept(KrollDict acceptOptions) throws Exception
	{
		if (state != SocketModule.LISTENING) {
			throw new Exception("Socket is not listening, unable to call accept");
		}

		this.acceptOptions = acceptOptions;
		accepting = true;
	}

	private void closeSocket() throws IOException {
        state = SocketModule.CLOSING; // set socket state to uninitialized to prevent use while closing
		if (clientSocket != null) {
			clientSocket.close();
			clientSocket = null;
		}

		if (serverSocket != null) {
			serverSocket.close();
			serverSocket = null;
		}
		updateState(SocketModule.CLOSED, "closed", null);
        fireEvent("close");
	}

	@Kroll.setProperty @Kroll.method
	public void setHost(String host)
	{
		setSocketProperty("host", host);
	}

	@Kroll.setProperty @Kroll.method
	public void setPort(int port)
	{
		setSocketProperty("port", port);
	}

	@Kroll.setProperty @Kroll.method
	public void setTimeout(int timeout)
	{
		setSocketProperty("timeout", timeout);
	}

	@Kroll.setProperty @Kroll.method
	public void setOptions(KrollDict options)
	{
		// not implemented yet - reserved for future use
		Log.i(TAG, "setting options on socket is not supported yet");
	}

	@Kroll.setProperty @Kroll.method
	public void setListenQueueSize(int listenQueueSize)
	{
		setSocketProperty("listenQueueSize", listenQueueSize);
	}

	@Kroll.setProperty @Kroll.method
	public void setConnected(KrollFunction connected)
	{
		setSocketProperty("connected", connected);
	}

	@Kroll.setProperty @Kroll.method
	public void setError(KrollFunction error)
	{
		setSocketProperty("error", error);
	}

	@Kroll.setProperty @Kroll.method
	public void setAccepted(KrollFunction accepted)
	{
		setSocketProperty("accepted", accepted);
	}

	private void setSocketProperty(String propertyName, Object propertyValue)
	{
		if ((state != SocketModule.LISTENING) && (state != SocketModule.CONNECTED)) {
			setProperty(propertyName, propertyValue);

		} else {
			Log.e(TAG, "Unable to set property <" + propertyName + "> on socket in <" + state + "> state");
		}
	}

	@Kroll.getProperty @Kroll.method
	public int getState()
	{
		return state;
	}
	
	private ConnectivityManager getConnectivityManager()
    {
        ConnectivityManager cm = null;

        Context a = TiApplication.getInstance();
        if (a != null) {
            cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
        } else {
            Log.w(TAG, "Activity is null when trying to retrieve the connectivity service", Log.DEBUG_MODE);
        }

        return cm;
    }

	private class ConnectedSocketThread extends Thread
	{
		public ConnectedSocketThread()
		{
			super("ConnectedSocketThread");
		}

		public void run()
		{
			final String host = TiConvert.toString(getProperty("host"));
            int transportType = TiConvert.toInt(getProperty("transportType"), -1);
            final int timeout = TiConvert.toInt(getProperty("timeout"), -1);
            final int port = TiConvert.toInt(getProperty("port"));

			try {
                clientSocket = new Socket();
				if (timeout >= 0) {
				    clientSocket.setSoTimeout(timeout);
				}
				if (TiC.LOLLIPOP_OR_GREATER && transportType > 0) {
				    final ConnectivityManager cm = getConnectivityManager();
				    NetworkRequest.Builder req = new NetworkRequest.Builder();
				    req.addTransportType(transportType);
				    cm.requestNetwork(req.build(), new ConnectivityManager.NetworkCallback() {
				        @Override
					    public void onAvailable(Network network) {
					            cm.unregisterNetworkCallback(this);
					            try {
                                    network.bindSocket(clientSocket);
//                                    if (timeout >= 0) {
//                                        clientSocket.connect(new InetSocketAddress(host, port), timeout);
//
//                                    } else {
                                        clientSocket.connect(new InetSocketAddress(host, port));
//                                    }
                                    updateState(SocketModule.CONNECTED, "connected", buildConnectedCallbackArgs());
                              } catch (IOException e) {
                                  updateState(SocketModule.ERROR, "error", buildErrorCallbackArgs(e.getLocalizedMessage(), -1));
                              }
					    }
				    });
				} else {
				    if (timeout >= 0) {
                        clientSocket.connect(new InetSocketAddress(host, port), timeout);

                    } else {
                        clientSocket.connect(new InetSocketAddress(host, port));
                    }
                    updateState(SocketModule.CONNECTED, "connected", buildConnectedCallbackArgs());
				}
			} catch (IOException e) {
				e.printStackTrace();
				updateState(SocketModule.ERROR, "error", buildErrorCallbackArgs(e.getLocalizedMessage(), -1));
			}
		}
	}

	private class ListeningSocketThread extends Thread
	{
		public ListeningSocketThread()
		{
			super("ListeningSocketThread");
		}

		public void run()
		{
			while(true) {
				if(accepting) {
					try {
						// Check if serverSocket is valid, if not exit
						if (serverSocket == null) {
							break;
						}
						Socket acceptedSocket = serverSocket.accept();

						TCPProxy acceptedTcpProxy = new TCPProxy();
						acceptedTcpProxy.clientSocket = acceptedSocket;
						acceptedTcpProxy.setProperty("host", acceptedTcpProxy.clientSocket.getInetAddress().getHostAddress());
						acceptedTcpProxy.setProperty("port", acceptedTcpProxy.clientSocket.getPort());

						Object optionValue;
						if((optionValue = acceptOptions.get("timeout")) != null) {
							acceptedTcpProxy.setProperty("timeout", TiConvert.toInt(optionValue, 0));
						}
						if((optionValue = acceptOptions.get("error")) != null) {
							if(optionValue instanceof KrollFunction) {
								acceptedTcpProxy.setProperty("error", (KrollFunction) optionValue);
							}
						}

						acceptedTcpProxy.state = SocketModule.CONNECTED;

						Object callback = getProperty("accepted");
						if (callback instanceof KrollFunction) {
							((KrollFunction) callback).callAsync(getKrollObject(), buildAcceptedCallbackArgs(acceptedTcpProxy));
						}

						accepting = false;

					} catch (IOException e) {
						if (state == SocketModule.LISTENING) {
							e.printStackTrace();
							updateState(SocketModule.ERROR, "error", buildErrorCallbackArgs(e.getLocalizedMessage(), 0));
						}

						break;
					}

				} else {
					try {
						sleep(500);

					} catch (InterruptedException e) {
						e.printStackTrace();
						Log.e(TAG, "Listening thread interrupted");
					}
				}
			}
		}
	}

	private KrollDict buildConnectedCallbackArgs()
	{
		KrollDict callbackArgs = new KrollDict();
		callbackArgs.put("socket", this);

		return callbackArgs;
	}

	private KrollDict buildErrorCallbackArgs(String error, int errorCode)
	{
		KrollDict callbackArgs = new KrollDict();
		callbackArgs.put("socket", this);
		callbackArgs.putCodeAndMessage(errorCode, error);
		callbackArgs.put("errorCode", errorCode);

		return callbackArgs;
	}

	private KrollDict buildAcceptedCallbackArgs(TCPProxy acceptedTcpProxy)
	{
		KrollDict callbackArgs = new KrollDict();
		callbackArgs.put("socket", this);
		callbackArgs.put("inbound", acceptedTcpProxy);

		return callbackArgs;
	}

	public void updateState(int state, String callbackName, KrollDict callbackArgs)
	{
		this.state = state;

		if (state == SocketModule.ERROR) {
			try {
				if (clientSocket != null) {
					clientSocket.close();
				}

				if (serverSocket != null) {
					serverSocket.close();
				}

			} catch (IOException e) {
				Log.w(TAG, "Unable to close socket in error state", Log.DEBUG_MODE);
			}
		}

		Object callback = getProperty(callbackName);
		if (callback instanceof KrollFunction) {
			((KrollFunction) callback).callAsync(getKrollObject(), callbackArgs);
		}
	}

	@Kroll.method
	public boolean isConnected()
	{
		if (state == SocketModule.CONNECTED) {
			return true;
		}
		return false;
	}


	// TiStream interface methods
	@Kroll.method
	public int read(Object args[]) throws IOException
	{
		if (!isConnected()) {
			throw new IOException("Unable to read from socket, not connected");
		}

		BufferProxy bufferProxy = null;
		int offset = 0;
		int length = 0;
		Number position = null;
        if(args.length > 0) {
            if(args[0] instanceof BufferProxy) {
                bufferProxy = (BufferProxy) args[0];
                length = bufferProxy.getLength();

            } else {
                throw new IllegalArgumentException("Invalid buffer argument");
            }
            if(args.length > 1) {
                if(args[1] instanceof Number) {
                    offset = ((Number)args[1]).intValue();
                } else {
                    throw new IllegalArgumentException("Invalid offset argument");
                }
            }
            if(args.length > 2) {
                if(args[2] instanceof Number) {
                    length = ((Number)args[2]).intValue();
                } else {
                    throw new IllegalArgumentException("Invalid length argument");
                }
            }
            if(args.length > 3) {
                if(args[3] instanceof Number) {
                    position = ((Number)args[3]);
                } else {
                    throw new IllegalArgumentException("Invalid position argument");
                }
            }
        }

		if (inputStream == null) {
			inputStream = clientSocket.getInputStream();
		}

		try {
			return TiStreamHelper.read(inputStream, bufferProxy, offset, length, position);

		} catch (IOException e) {
			e.printStackTrace();
			if (state != SocketModule.CLOSED) {
				closeSocket();
			}
			throw e;
		}
//		return -1;
	}

	@Kroll.method
	public int write(Object args[]) throws IOException
	{
		if(!isConnected())
		{
			throw new IOException("Unable to write to socket, not connected");
		}
		byte[] bytes = null;
		int offset = 0;
		int length = 0;

		if(args.length == 1 || args.length == 3) {
			if(args.length > 0) {
			    bytes = TiConvert.toBytes(args[0]);
			}

			if(args.length == 3) {
				if(args[1] instanceof Integer) {
					offset = ((Integer)args[1]).intValue();

				} else if(args[1] instanceof Double) {
					offset = ((Double)args[1]).intValue();

				} else {
					throw new IllegalArgumentException("Invalid offset argument");
				}

				if(args[2] instanceof Integer) {
					length = ((Integer)args[2]).intValue();

				} else if(args[2] instanceof Double) {
					length = ((Double)args[2]).intValue();

				} else {
					throw new IllegalArgumentException("Invalid length argument");
				}
			}

		} else {
			throw new IllegalArgumentException("Invalid number of arguments");
		}
		
		if(bytes == null) {
            throw new IllegalArgumentException("Invalid buffer argument");
        }
		length = bytes.length;

		try {
			return TiStreamHelper.write(clientSocket.getOutputStream(), bytes, offset, length);

		} catch (IOException e) {
			e.printStackTrace();
			closeSocket();
			updateState(SocketModule.ERROR, "error", buildErrorCallbackArgs(e.getLocalizedMessage(), 0));
			throw new IOException("Unable to write to socket, IO error");
		}
	}

	@Kroll.method
	public boolean isWritable()
	{
		return isConnected();
	}

	@Kroll.method
	public boolean isReadable()
	{
		return isConnected();
	}

	@Kroll.method
	public void close() throws IOException
	{
		if (state == SocketModule.CLOSED) {
			return;
		}

		// if((state != SocketModule.CONNECTED) && (state != SocketModule.LISTENING)) {
		// 	throw new IOException("Socket is not connected or listening, unable to call close on socket in <" + state + "> state");
		// }

		try {
			closeSocket();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IOException("Error occured when closing socket");
		}
	}

	@Override
	public String getApiName()
	{
		return "Ti.Network.Socket.TCP";
	}
}

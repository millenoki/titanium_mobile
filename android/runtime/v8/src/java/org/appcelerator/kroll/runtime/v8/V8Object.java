/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.runtime.v8;

import java.util.HashMap;

import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;

public class V8Object extends KrollObject
{
	private static final String TAG = "V8Object";

	private volatile long ptr;

	public V8Object(long ptr)
	{
		this.ptr = ptr;
	}

	public long getPointer()
	{
		return ptr;
	}

	public void setPointer(long ptr)
	{
		this.ptr = ptr;
	}

	@Override
	public Object getNativeObject()
	{
		return this;
	}

	@Override
	public void setProperty(String name, Object value)
	{
	    if (ptr == 0) {
            return;
        }
		if (!KrollRuntime.isInitialized()) {
			Log.w(TAG, "Runtime disposed, cannot set property '" + name + "'");
			return;
		}
		nativeSetProperty(ptr, name, value);
	}

	@Override
	public Object getProperty(String name)
	{
	    if (ptr == 0) {
            return null;
        }
		if (!KrollRuntime.isInitialized()) {
			Log.w(TAG, "Runtime disposed, cannot get property '" + name + "'");
			return null;
		}
		return nativeGetProperty(ptr, name);
	}

	@Override
	public boolean fireEvent(Object source, String type, Object data, boolean bubbles, boolean reportSuccess, int code, String message)
	{
	    if (ptr == 0) {
            return false;
        }
		if (!KrollRuntime.isInitialized()) {
			Log.w(TAG, "Runtime disposed, cannot fire event '" + type + "'");
			return false;
		}

		long sourceptr = 0;
		if (source instanceof V8Object) {
			sourceptr = ((V8Object) source).getPointer();
		}
		return nativeFireEvent(ptr, source, sourceptr, type, data, bubbles, reportSuccess, code, message);
	}

	@Override
	public Object callProperty(String propertyName, Object[] args) {
	    if (ptr == 0) {
            return null;
        }
        if (!KrollRuntime.isInitialized()) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Runtime disposed, cannot call property '" + propertyName + "'");
			}
			return null;
		}
		return nativeCallProperty(ptr, propertyName, args);
	}

	@Override
	public void doRelease()
	{
		if (ptr == 0) {
			return;
		}

		if (!KrollRuntime.isDisposed() && nativeRelease(ptr)) {
			ptr = 0;
			KrollRuntime.suggestGC();
		}
	}

	@Override
	public void doSetWindow(Object windowProxyObject)
	{
	    if (ptr == 0) {
            return;
        }
	    if (!KrollRuntime.isInitialized()) {
            Log.w(TAG, "Runtime disposed, cannot doSetWindow");
            return;
        }
		nativeSetWindow(ptr, windowProxyObject);
	}

	@Override
	protected void finalize() throws Throwable
	{
		super.finalize();

		if (ptr != 0) {
			release();
		}
	}

	@Override
	public void updateNativeProperties(HashMap<String, Object> properties)
	{
	    if (ptr == 0) {
            return;
        }
		if (!KrollRuntime.isInitialized()) {
			Log.w(TAG, "Runtime disposed, cannot updateNativeProperties");
			return;
		}
		nativeUpdateProperties(ptr, properties);
	}

	// JNI method prototypes
	protected static native void nativeInitObject(Class<?> proxyClass, Object proxyObject);

	private static native boolean nativeRelease(long ptr);

	private native Object nativeGetProperty(long ptr, String name);
	private native Object nativeCallProperty(long ptr, String propertyName, Object[] args);

	private native void nativeSetProperty(long ptr, String name, Object value);

	private native boolean nativeFireEvent(long ptr, Object source, long sourcePtr, String event, Object data,
										   boolean bubble, boolean reportSuccess, int code, String errorMessage);

	private native void nativeSetWindow(long ptr, Object windowProxyObject);
	private native void nativeUpdateProperties(long ptr, Object properties);
}

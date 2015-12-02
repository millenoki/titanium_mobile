/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.util.ArrayList;
import java.util.Iterator;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.util.TiConvert;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * API for accessing, storing, and modifying application properties that are 
 * exposed via Ti.App.Properties.
 */
public class TiProperties
{
	private static final String TAG = "TiProperties";
	private static JSONObject systemProperties;

	SharedPreferences preferences;

	/**
	 * Instantiates the private SharedPreferences collection with the given name and context.
	 * This means no other Android application will have access to they keys and values.
	 * @param context the context used to create/retrieve preferences.
	 * @param name the name used to create/retrieve preferences.
	 * @param clear whether to clear all keys and values in the instantiated SharedPreferences collection.
	 */
	public TiProperties(Context context, String name, boolean clear) {
		preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE);
		if (clear) {
			preferences.edit().clear().commit();
		}
	}

	/**
	 * Returns the mapping of a specified key, in String format. If key does not exist, returns the default value.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return mapping of key, or default value.
	 * @module.api
	 */
	public String getString(String key, String def)
	{
		
		Object value = getPreference(key);
		
		if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "getString called with key:" + key + ", def:" + def + ", value:" + value, Log.DEBUG_MODE);
        }

		if (value != null) {
			return value.toString();
		} else {
			return def;
		}
	}

	/**
	 * @deprecated
	 */
	public SharedPreferences getPreference()
	{
		return preferences;
	}

	public Object getPreference(String key)
	{
		Object value = null;
		if (systemProperties != null) {
			try {
				value = systemProperties.get(key);
			} catch (JSONException e) {
				value = preferences.getAll().get(key);
			}
		}
		if (value == null) {
			value = preferences.getAll().get(key);
		}
		return value;
	}

	/**
	 * Maps the specified key with a String value. If value is null, existing key will be removed from preferences.
	 * Otherwise, its value will be overwritten.
	 * @param key the key to set.
	 * @param value the value to set.
	 * @module.api
	 */
	public void setString(String key, String value)
	{
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG,"setString called with key:"+key+", value:"+value);
        }

		if (systemProperties != null && systemProperties.has(key)) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Cannot overwrite/delete read-only property: " + key);
			}
			return;
		}

		SharedPreferences.Editor editor = preferences.edit();
		if (value == null) {
			editor.remove(key);
		} else {
			editor.putString(key,value);
		}
		editor.commit();
	}

	/**
	 * Returns the mapping of a specified key as an Integer. If key does not exist, returns the default value.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return mapping of key, or default value.
	 * @module.api
	 */
	public int getInt(String key, int def)
	{
		Object value = getPreference(key);
        
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "getInt called with key:" + key + ", def:" + def + ", value:" + value, Log.DEBUG_MODE);
        }
        return TiConvert.toInt(value, def);
	}
	
	/**
	 * Maps the specified key with an int value. If key exists, its value will be overwritten.
	 * @param key the key to set.
	 * @param value the value to set.
	 * @module.api
	 */
	public void setInt(String key, int value)
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "setInt called with key:" + key + ", value:" + value);
		}

		if (systemProperties != null && systemProperties.has(key)) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Cannot overwrite read-only property: " + key);
			}
			return;
		}

		SharedPreferences.Editor editor = preferences.edit();
		editor.putInt(key,value);
		editor.commit();
	}
	
	/**
	 * Returns the mapping of a specified key as a Double. If key does not exist, returns the default value.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return mapping of key, or default value.
	 * @module.api
	 */
	public double getDouble(String key, double def)
	{
		Object value = getPreference(key);
        
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "getDouble called with key:" + key + ", def:" + def + ", value:" + value, Log.DEBUG_MODE);
        }
        return TiConvert.toDouble(value, def);
	}

	/**
	 * Maps the specified key with a double value. If key exists, its value will be
	 * overwritten.
	 * @param key the key to set.
	 * @param value the value to set.
	 * @module.api
	 */
	public void setDouble(String key, double value)
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "setDouble called with key:" + key + ", value:" + value);
		}

		if (systemProperties != null && systemProperties.has(key)) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Cannot overwrite read-only property: " + key);
			}
			return;
		}

		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(key,value + "");
		editor.commit();
	}
	
	/**
	 * Returns the mapping of a specified key, as a Boolean. If key does not exist, returns the default value.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return mapping of key, or default value.
	 * @module.api
	 */
	public boolean getBool(String key, boolean def)
	{
		Object value = getPreference(key);
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "getBool called with key:" + key + ", def:" + def + ", value:" + value, Log.DEBUG_MODE);
        }
        return TiConvert.toBoolean(value, def);
	}

	/**
	 * Maps the specified key with a boolean value. If key exists, its value will be
	 * overwritten.
	 * @param key the key to set.
	 * @param value the value to set.
	 * @module.api
	 */
	public void setBool(String key, boolean value)
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "setBool called with key:" + key + ", value:" + value);
		}

		if (systemProperties != null && systemProperties.has(key)) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Cannot overwrite read-only property: " + key);
			}
			return;
		}

		SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean(key,value);
		editor.commit();
	}

	/**
	 * Returns the mapping of a specified key as a String array. If key does not exist, returns the default value.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return mapping of key, or default value.
	 * @module.api
	 */
	public String[] getList(String key, String def[])
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "getList called with key:" + key + ", def:" + def);
		}

		int length = preferences.getInt(key+".length", -1);
		if (length == -1) {
			return def;
		}

		String list[] = new String[length];
		for (int i = 0; i < length; i++) {
			list[i] = preferences.getString(key+"."+i, "");
		}
		return list;
	}

	/**
	 * Maps the specified key with String[] value. Also maps 'key.length' to 'value.length'.
	 * If key exists, its value will be overwritten.
	 * @param key the key to set.
	 * @param value the value to set.
	 * @module.api
	 */
	public void setList(String key, String[] value)
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "setList called with key:" + key + ", value:" + value);
		}

		SharedPreferences.Editor editor = preferences.edit();
		for (int i = 0; i < value.length; i++) {
			editor.putString(key + "." + i, value[i]);
		}
		editor.putInt(key + ".length", value.length);

		editor.commit();

	}

	/**
	 * @param key the lookup list key.
	 * @return true if the list property exists in preferences
	 * @module.api
	 */
	public boolean hasListProperty(String key) {
		return hasProperty(key+".0");
	}

	/**
	 * Returns whether key exists in preferences.
	 * @param key the lookup key.
	 * @return true if key exists in preferences.
	 * @module.api
	 */
	public boolean hasProperty(String key)
	{
		return systemProperties != null ? systemProperties.has(key) || preferences.contains(key) : preferences.contains(key);
	}

	/**
	 * Returns an array of keys whose values are lists.
	 * @return an array of keys.
	 * @module.api
	 */
	public String[] listProperties()
	{
		ArrayList<String> properties = new ArrayList<String>();
		if (systemProperties != null) {
			Iterator<?> keys = systemProperties.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				properties.add(key);
			}
		}
		for (String key : preferences.getAll().keySet()) {
			if (key.endsWith(".length")) {
				properties.add(key.substring(0, key.length() - 7));
			} else if (key.matches(".+\\.\\d+$")) {

			} else if (!properties.contains(key)) {
				properties.add(key);
			}
		}

		return properties.toArray(new String[properties.size()]);
	}

	/**
	 * Removes the key from preferences if it exists.
	 * @param key the key to remove.
	 * @module.api
	 */
	public void removeProperty(String key)
	{
		if (systemProperties != null && systemProperties.has(key)) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Cannot remove a read-only property: " + key);
			}
			return;
		}

		if (preferences.contains(key)) {
			SharedPreferences.Editor editor = preferences.edit();
			editor.remove(key);
			editor.commit();
		}
	}
	
	public static boolean systemPropertiesLoaded( ) {
	    return systemProperties != null;
	}

	public static void setSystemProperties(JSONObject prop) {
		systemProperties = prop;
	}
}

/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiFileProxy;
import org.appcelerator.titanium.TiPoint;
import org.appcelerator.titanium.util.TiUIHelper.Shadow;
import org.appcelerator.titanium.view.Ti2DMatrix;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.appcelerator.titanium.view.TiUIView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import ti.modules.titanium.BufferProxy;

/**
 * Utility class for type conversions.
 */
public class TiConvert
{
	private static final String TAG = "TiConvert";

	public static final String ASSET_URL = "file:///android_asset/"; // class scope on URLUtil
	public static final String JSON_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";


	// Bundle
	public static Object putInKrollDict(KrollDict d, String key, Object value)
	{
		if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Date) {
			d.put(key, value);

		} else if (value instanceof KrollDict) {
			KrollDict nd = new KrollDict();
			KrollDict dict = (KrollDict) value;
			for (String k : dict.keySet()) {
				putInKrollDict(nd, k, dict.get(k));
			}
			d.put(key, nd);
			value = nd;
		} else if (value instanceof Object[]) {
			Object[] a = (Object[]) value;
			int len = a.length;
			if (len > 0) {
				Object v = a[0];
				if (v != null) {
					Log.w(TAG, "Array member is type: " + v.getClass().getSimpleName(), Log.DEBUG_MODE);

				} else {
					Log.w(TAG, "First member of array is null", Log.DEBUG_MODE);
				}

				if (v != null && v instanceof String) {
					String[] sa = new String[len];
					for (int i = 0; i < len; i++) {
						sa[i] = (String) a[i];
					}
					d.put(key, sa);

				} else if (v != null && v instanceof Double) {
					double[] da = new double[len];
					for (int i = 0; i < len; i++) {
						da[i] = (Double) a[i];
					}
					d.put(key, da);

				} /*else if (v != null && v instanceof KrollObject) {
					KrollProxy[] pa = new KrollProxy[len];
					for(int i = 0; i < len; i++) {
						KrollObject ko = (KrollObject) a[i];
						pa[i] = (KrollProxy) ko.getProxy();
					}
					d.put(key, pa);

				} */else {

					Object[] oa = new Object[len];
					for (int i = 0; i < len; i++) {
						oa[i] = a[i];
					}
					d.put(key, oa);
					//throw new IllegalArgumentException("Unsupported array property type " + v.getClass().getSimpleName());
				}

			} else {
				d.put(key, (Object[]) value);
			}

		} else if (value == null) {
			d.put(key, null);

		} else if (value instanceof KrollProxy) {
			d.put(key, value);

		} else if (value instanceof Map) {
			KrollDict dict = new KrollDict();
			Map<?, ?> map = (Map<?, ?>) value;
			Iterator<?> iter = map.keySet().iterator();
			while(iter.hasNext())
			{
				String k = (String) iter.next();
				putInKrollDict(dict, k, map.get(k));
			}
			d.put(key, dict);

		} else {
			throw new IllegalArgumentException("Unsupported property type "
											   + (value == null ? "null" : value.getClass().getName()));
		}

		return value;
	}

	/**
	 * This is a wrapper method. 
	 * Refer to {@link TiColorHelper#parseColor(String)} for more details.
	 * @param value  color value to convert.
	 * @return an int representation of the color.
	 * @module.api
	 */
	public static int toColor(String value)
	{
		return TiColorHelper.parseColor(value);
	}
	public static int toColor(Object value)
	{
		if (value instanceof String) {
			return TiColorHelper.parseColor((String)value);
		}
		return TiColorHelper.parseColor(toString(value));
	}
    public static int toColor(Object value, int def)
    {
        String result = toString(value);
        if (result == null) {
            return def;
        }
        return TiColorHelper.parseColor(result);
    }

	/**
	 * This is a wrapper method. 
	 * Refer to {@link TiColorHelper#parseColor(String)} for more details.
	 * @param hashMap the HashMap contains the String representation of the color.
	 * @param key the color lookup key.
	 * @return an int representation of the color.
	 * @module.api
	 */
	public static int toColor(HashMap<String, Object> hashMap, String key)
	{
		return toColor(TiConvert.toString(hashMap.get(key)));
	}

	public static int toColor(HashMap<String, Object> hashMap, String key, int def)
	{
		if (hashMap.containsKey(key))
			return toColor(TiConvert.toString(hashMap.get(key)));
		return def;
	}

	public static ColorDrawable toColorDrawable(String value)
	{
		return new ColorDrawable(toColor(value));
	}
	public static ColorDrawable toColorDrawable(Object value)
    {
        return toColorDrawable(TiConvert.toString(value));
    }

	public static ColorDrawable toColorDrawable(HashMap<String, Object> hashMap, String key)
	{
		return toColorDrawable(TiConvert.toString(hashMap.get(key)));
	}

	private static final int DEFAULT_FLAG_RETURN =  TiUIView.TIFLAG_NEEDS_LAYOUT;
	
	public static int fillLayout(String key, Object value, LayoutParams layoutParams, boolean withMatrix)
	{
        switch (key) {
        case TiC.PROPERTY_TRANSFORM:
            if (withMatrix) {
                layoutParams.matrix = TiConvert.toMatrixWithReuse(value, layoutParams.matrix);
                return TiUIView.TIFLAG_NEEDS_INVALIDATE;
			}
            break;
        case TiC.PROPERTY_LEFT:
            layoutParams.optionLeft = toTiDimension(value, TiDimension.TYPE_LEFT);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_TOP:
            layoutParams.optionTop = toTiDimension(value, TiDimension.TYPE_TOP);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_CENTER:
            updateLayoutCenter(value, layoutParams);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_RIGHT:
            layoutParams.optionRight = toTiDimension(value, TiDimension.TYPE_RIGHT);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_BOTTOM:
            layoutParams.optionBottom = toTiDimension(value, TiDimension.TYPE_BOTTOM);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_MIN_WIDTH:
            layoutParams.minWidth = toTiDimension(value, TiDimension.TYPE_WIDTH);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_MIN_HEIGHT:
            layoutParams.minHeight = toTiDimension(value, TiDimension.TYPE_HEIGHT);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_MAX_WIDTH:
            layoutParams.maxWidth = toTiDimension(value, TiDimension.TYPE_WIDTH);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_MAX_HEIGHT:
            layoutParams.maxHeight = toTiDimension(value, TiDimension.TYPE_HEIGHT);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_LAYOUT_FULLSCREEN:
            layoutParams.fullscreen = toBoolean(value, false);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_SIZE_RATIO:
            layoutParams.sizeRatio = toFloat(value, 1.0f);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_WEIGHT:
            layoutParams.weight = toFloat(value, -1.0f);
            return DEFAULT_FLAG_RETURN;
        case TiC.PROPERTY_WIDTH:
        {
            String sValue = TiConvert.toString(value);
            layoutParams.widthDefined = true;
            if (value == null) {
				layoutParams.optionWidth = null;
				layoutParams.sizeOrFillWidthEnabled = false;
                layoutParams.widthDefined = false;

            }else if (TiC.LAYOUT_FILL.equalsIgnoreCase(sValue)) {
				// fill
				layoutParams.optionWidth = null;
				layoutParams.sizeOrFillWidthEnabled = true;
				layoutParams.autoFillsWidth = true;
                layoutParams.width = LayoutParams.MATCH_PARENT;

            } else if (TiC.LAYOUT_SIZE.equalsIgnoreCase(sValue)) {
				// size
				layoutParams.optionWidth = null;
				layoutParams.sizeOrFillWidthEnabled = true;
				layoutParams.autoFillsWidth = false;
                layoutParams.width = LayoutParams.WRAP_CONTENT;
            } else if (TiC.LAYOUT_MATCH_PARENT.equalsIgnoreCase(sValue)) {
                // parent
                layoutParams.optionWidth = null;
                layoutParams.sizeOrFillWidthEnabled = false;
                layoutParams.autoFillsWidth = false;
                layoutParams.widthMatchParent = true;
            } else if (TiC.LAYOUT_MATCH.equalsIgnoreCase(sValue)) {
                // match
                layoutParams.optionWidth = null;
                layoutParams.sizeOrFillWidthEnabled = false;
                layoutParams.autoFillsWidth = false;
                layoutParams.widthMatchHeight = true;
            } else if (TiC.SIZE_AUTO.equalsIgnoreCase(sValue)) {
                layoutParams.optionWidth = null;
                layoutParams.sizeOrFillWidthEnabled = true;

			} else {
                layoutParams.optionWidth = toTiDimension(value, TiDimension.TYPE_WIDTH);
				layoutParams.sizeOrFillWidthEnabled = false;
			}
            return DEFAULT_FLAG_RETURN;
		}
        case TiC.PROPERTY_HEIGHT: {
            String sValue = TiConvert.toString(value);
            layoutParams.heightDefined = true;
            if (value == null) {
				layoutParams.optionHeight = null;
				layoutParams.sizeOrFillHeightEnabled = false;
                layoutParams.heightDefined = false;
            }else if (TiC.LAYOUT_FILL.equalsIgnoreCase(sValue)) {
				// fill
				layoutParams.optionHeight = null;
				layoutParams.sizeOrFillHeightEnabled = true;
				layoutParams.autoFillsHeight = true;
                layoutParams.height = LayoutParams.MATCH_PARENT;

            } else if (TiC.LAYOUT_SIZE.equalsIgnoreCase(sValue)) {
				// size
				layoutParams.optionHeight = null;
				layoutParams.sizeOrFillHeightEnabled = true;
				layoutParams.autoFillsHeight = false;
                layoutParams.height = LayoutParams.WRAP_CONTENT;
            } else if (TiC.LAYOUT_MATCH_PARENT.equalsIgnoreCase(sValue)) {
                // parent
                layoutParams.optionHeight = null;
                layoutParams.sizeOrFillHeightEnabled = false;
                layoutParams.autoFillsHeight = false;
                layoutParams.heightMatchParent = true;
            } else if (TiC.LAYOUT_MATCH.equalsIgnoreCase(sValue)) {
                // size
                layoutParams.optionHeight = null;
                layoutParams.sizeOrFillHeightEnabled = false;
                layoutParams.autoFillsHeight = false;
                layoutParams.heightMatchWidth = true;
            } else if (TiC.SIZE_AUTO.equalsIgnoreCase(sValue)) {
                layoutParams.optionHeight = null;
                layoutParams.sizeOrFillHeightEnabled = true;

			} else {
                layoutParams.optionHeight = toTiDimension(value, TiDimension.TYPE_HEIGHT);
				layoutParams.sizeOrFillHeightEnabled = false;
			}
            return DEFAULT_FLAG_RETURN;
		}
        case TiC.PROPERTY_ZINDEX:
            layoutParams.optionZIndex = toInt(value, 0);
            return DEFAULT_FLAG_RETURN | TiUIView.TIFLAG_NEEDS_LAYOUT_INFORMPARENT;
        case TiC.PROPERTY_ANCHOR_POINT:
            if (value instanceof HashMap) {
                HashMap point = (HashMap) value;
                layoutParams.anchorX = TiConvert.toFloat(point, TiC.PROPERTY_X);
                layoutParams.anchorY = TiConvert.toFloat(point, TiC.PROPERTY_Y);
                return DEFAULT_FLAG_RETURN;
			}
            break;
        default:
            break;
		}
        return 0;
    }

	
	private static Set<String> sLayoutKeys;
	// Layout
    public static int fillLayout(HashMap hashMap, LayoutParams layoutParams,
            boolean withMatrix) {
        if (sLayoutKeys == null) {
            sLayoutKeys = new HashSet<String>();
            sLayoutKeys.add(TiC.PROPERTY_WIDTH);
            sLayoutKeys.add(TiC.PROPERTY_HEIGHT);
            sLayoutKeys.add(TiC.PROPERTY_LEFT);
            sLayoutKeys.add(TiC.PROPERTY_RIGHT);
            sLayoutKeys.add(TiC.PROPERTY_BOTTOM);
            sLayoutKeys.add(TiC.PROPERTY_TOP);
            sLayoutKeys.add(TiC.PROPERTY_MIN_WIDTH);
            sLayoutKeys.add(TiC.PROPERTY_MAX_WIDTH);
            sLayoutKeys.add(TiC.PROPERTY_MIN_HEIGHT);
            sLayoutKeys.add(TiC.PROPERTY_MAX_HEIGHT);
            sLayoutKeys.add(TiC.PROPERTY_LAYOUT_FULLSCREEN);
            sLayoutKeys.add(TiC.PROPERTY_WEIGHT);
            sLayoutKeys.add(TiC.PROPERTY_SIZE_RATIO);
            sLayoutKeys.add(TiC.PROPERTY_ZINDEX);
            sLayoutKeys.add(TiC.PROPERTY_ANCHOR_POINT);
            sLayoutKeys.add(TiC.PROPERTY_CENTER);
            sLayoutKeys.add(TiC.PROPERTY_TRANSFORM);
		}
//        Set<String> intersection = new HashSet(hashMap.keySet());
//        intersection.retainAll(sLayoutKeys);
        int updateFlags = 0;
        for (String key : sLayoutKeys) {
            if (hashMap.containsKey(key) && (key != TiC.PROPERTY_TRANSFORM || withMatrix) ) {

                int result = fillLayout(key, hashMap.get(key), layoutParams, withMatrix);
                if (result != 0) {
                    updateFlags |= result;
	}
                hashMap.remove(key);
            }
        }
//        Iterator it = intersection.iterator();
//        while (it.hasNext()) {
//            Map.Entry entry = (Map.Entry) it.next();
//            Object key = entry.getKey();
//            if (key instanceof String) {
//                int result = fillLayout((String) key, entry.getValue(), layoutParams, withMatrix);
//                if (result != 0) {
//                    updateFlags |= result;
//                    it.remove();
//                }
//            } else {
//                Log.e(TAG, "can't parse object key \"" + key + "\", should be a string");
//            }
//            
//        }
        return updateFlags;
    }
	public static int fillLayout(KrollDict hashMap, LayoutParams layoutParams)
	{
		return fillLayout(hashMap, layoutParams, true);
	}

	public static void updateLayoutCenter(Object value, LayoutParams layoutParams)
	{
		if (value instanceof HashMap) {
			HashMap center = (HashMap) value;
			Object x = center.get(TiC.PROPERTY_X);
			Object y = center.get(TiC.PROPERTY_Y);

			if (x != null) {
				layoutParams.optionCenterX = toTiDimension(x, TiDimension.TYPE_CENTER_X);

			} else {
				layoutParams.optionCenterX = null;
			}

			if (y != null) {
				layoutParams.optionCenterY = toTiDimension(y, TiDimension.TYPE_CENTER_Y);

			} else {
				layoutParams.optionCenterY = null;
			}

		} else if (value != null) {
			layoutParams.optionCenterX = toTiDimension(value, TiDimension.TYPE_CENTER_X);
			layoutParams.optionCenterY = null;

		} else {
			layoutParams.optionCenterX = null;
			layoutParams.optionCenterY = null;
		}
	}

	/**
	 * Attempts to convert a value into a boolean, if value is a Boolean or String. Otherwise,
	 * default value is returned
	 * @param value the value to convert.
	 * @param def  the default value.
	 * @return a boolean value.
	 * @module.api
	 */
	public static boolean toBoolean(Object value, boolean def)
	{
		boolean result = def;
		if (value != null) {
			try {
				result = toBoolean(value);
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * Attempts to convert a value into a boolean, if value is a Boolean or String. Otherwise,
	 * an exception is thrown.
	 * @param value the value to convert.
	 * @return a boolean value.
	 * @module.api
	 */
	public static boolean toBoolean(Object value)
	{
		if (value instanceof Boolean) {
			return (Boolean) value;

		} else if (value instanceof String) {
			return Boolean.parseBoolean(((String) value));

		} else if (value instanceof Number) {
			// in Javascript anything other than zero is 'true'
			return ((Number) value).intValue() != 0;

		} else {
			throw new IllegalArgumentException("Unable to convert " + (value == null ? "null" : value.getClass().getName()) + " to boolean.");
		}
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toBoolean(Object)}.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @param def the default value.
	 * @return a boolean value.
	 * @module.api
	 */
	public static boolean toBoolean(HashMap<String, Object> hashMap, String key, boolean def)
	{
		if (hashMap != null && key != null) {
			return toBoolean(hashMap.get(key), def);
		}

		return def;
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toBoolean(Object)}.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return a boolean value.
	 * @module.api
	 */
	public static boolean toBoolean(HashMap<String, Object> hashMap, String key)
	{
		return toBoolean(hashMap.get(key));
	}

	/**
	 * If value is a Double, Integer, Long or String, converts it to Integer. Otherwise
	 * an exception is thrown.
	 * @param value the value to convert.
	 * @return an int value.
	 * @module.api
	 */
	public static int toInt(Object value)
	{
		if (value instanceof Double) {
			return ((Double) value).intValue();

		} else if (value instanceof Integer) {
			return ((Integer) value);

		} else if (value instanceof Long) {
			return ((Long) value).intValue();

		} else if (value instanceof String) {
			return Integer.parseInt((String) value);

		} else {
			throw new NumberFormatException("Unable to convert " + (value == null ? "null" : value));
		}
	}

	/**
	 * If value is a Double, Integer, Long or String, converts it to Integer. Otherwise
	 * returns default value.
	 * @param value the value to convert.
	 * @param def the default value to return
	 * @return an int value.
	 * @module.api
	 */
	public static int toInt(Object value, int def)
	{
		int result = def;
		if (value != null) {
			try {
				result = toInt(value);
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toInt(Object)}.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return an int value.
	 * @module.api
	 */
	public static int toInt(HashMap<String, Object> hashMap, String key)
	{
		return toInt(hashMap.get(key));
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toInt(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @param def the default value to return.
	 * @return an int value.
	 * @module.api
	 */
	public static int toInt(HashMap<String, Object> hashMap, String key, int def)
	{
		if (hashMap != null)
			return toInt(hashMap.get(key), def);
		return def;
    }

    /**
     * If value is a Double, Integer, Long or String, converts it to Long. Otherwise
     * an exception is thrown.
     * @param value the value to convert.
     * @return a long value.
     * @module.api
     */
    public static long toLong(Object value)
    {
        if (value instanceof Double) {
            return ((Double) value).longValue();

        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();

        } else if (value instanceof Long) {
            return ((Long) value);

        } else if (value instanceof String) {
            return Long.parseLong((String) value);

        } else {
            throw new NumberFormatException("Unable to convert " + (value == null ? "null" : value));
        }
    }

    /**
     * If value is a Double, Integer, Long or String, converts it to Long. Otherwise
     * returns default value.
     * @param value the value to convert.
     * @param def the default value to return
     * @return a long value.
     * @module.api
     */
    public static long toLong(Object value, long def)
    {
        try {
            return toLong(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Takes a value out of a hash table then attempts to convert it using {@link #toLong(Object)}.
     * @param hashMap the hash map to search.
     * @param key the lookup key.
     * @return a long value.
     * @module.api
     */
    public static long toLong(HashMap<String, Object> hashMap, String key)
    {
        return toLong(hashMap.get(key));
    }

    /**
     * Takes a value out of a hash table then attempts to convert it using {@link #toLong(Object)} for more details.
     * @param hashMap the hash map to search.
     * @param key the lookup key.
     * @param def the default value to return.
     * @return a long value.
     * @module.api
     */
    public static long toLong(HashMap<String, Object> hashMap, String key, long def)
    {
        if (hashMap != null)
            return toLong(hashMap.get(key), def);
        return def;
    }


	/**
	 * If value is a Double, Integer or String, converts it to Float. Otherwise,
	 * an exception is thrown.
	 * @param value the value to convert.
	 * @return a float value.
	 * @module.api
	 */
	public static float toFloat(Object value)
	{
		if (value instanceof Float) {
			return (Float) value;

		} else if (value instanceof Double) {
			return ((Double) value).floatValue();

		} else if (value instanceof Integer) {
			return ((Integer) value).floatValue();

		} else if (value instanceof String) {
			return Float.parseFloat((String) value);

		} else {
			throw new NumberFormatException("Unable to convert value to float.");
		}
	}

	/**
	 * If value is a Double, Integer, Long or String, converts it to Float. Otherwise
	 * returns default value.
	 * @param value the value to convert.
	 * @param def the default value to return
	 * @return an float value.
	 * @module.api
	 */
	public static float toFloat(Object value, float def)
	{
		float result = def;
		if (value != null) {
			try {
				result = toFloat(value);
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toFloat(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return a float value.
	 * @module.api
	 */
	public static float toFloat(HashMap<String, Object> hashMap, String key)
	{
		return toFloat(hashMap.get(key));
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toFloat(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @param def the default value to return.
	 * @return a float value.
	 * @module.api
	 */
	public static float toFloat(HashMap<String, Object> hashMap, String key, float def)
	{
		if (hashMap != null)
		return toFloat(hashMap.get(key), def);
		return def;
	}

	/**
	 * If value is a Double, Integer, or String, converts it to Double. Otherwise,
	 * an exception is thrown.
	 * @param value the value to convert.
	 * @return a double value.
	 * @module.api
	 */
	public static double toDouble(Object value)
	{
		if (value instanceof Double) {
			return ((Double) value);

		} else if (value instanceof Integer) {
			return ((Integer) value).doubleValue();

		} else if (value instanceof String) {
			return Double.parseDouble((String) value);

		} else {
			throw new NumberFormatException("Unable to convert " + (value == null ? "null" : value.getClass().getName()));
		}
	}

	/**
	 * If value is a Float, Integer, Long or String, converts it to Double. Otherwise
	 * returns default value.
	 * @param value the value to convert.
	 * @param def the default value to return
	 * @return an double value.
	 * @module.api
	 */
	public static double toDouble(Object value, double def)
	{
		try {
			return toDouble(value);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toDouble(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return a double.
	 * @module.api
	 */
	public static double toDouble(HashMap<String, Object> hashMap, String key)
	{
		return toDouble(hashMap.get(key));
	}


	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toDouble(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @param def the default value to return.
	 * @return a double value.
	 * @module.api
	 */
	public static double toDouble(HashMap<String, Object> hashMap, String key, double def)
	{
		if (hashMap != null)
			return toDouble(hashMap.get(key), def);
		return def;
	}

	/**
	 * Converts a vlaue into a String. If value is null, a default value is returned.
	 * @param value the value to convert.
	 * @param defaultString the default value.
	 * @return a String.
	 * @module.api
	 */
	public static String toString(Object value, String defaultString)
	{
		String result = toString(value);
		if (result == null) {
			result = defaultString;
		}

		return result;
	}

	/**
	 * Converts a value into a String. If value is null, returns null.
	 * @param value the value to convert.
	 * @return String or null.
	 * @module.api
	 */
	public static String toString(Object value)
	{
	    if (value instanceof TiBlob) {
	        return ((TiBlob) value).getText();
	    }
		return value == null ? null : value.toString();
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toString(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return String or null.
	 * @module.api
	 */
	public static String toString(HashMap<String, Object> hashMap, String key)
	{
		return toString(hashMap.get(key));
	}

	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toString(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @return String or null.
	 * @module.api
	 */
	public static String toString(HashMap<String, Object> hashMap, String key, String def)
	{
		if (hashMap != null)
			return toString(hashMap.get(key), def);
		return def;
	}


	/**
	 * Converts an Object array into a String array.
	 * @param parts the object array to convert
	 * @return a String array.
	 * @module.api
	 */
	public static String[] toStringArray(Object[] parts)
	{
		String[] sparts = (parts != null ? new String[parts.length] : new String[0]);
		if (parts != null) {
			for (int i = 0; i < parts.length; i++) {
				sparts[i] = parts[i] == null ? null : parts[i].toString();
			}
		}

		return sparts;
	}

	public static String[] toStringArray(Object value)
    {
        if (value instanceof Object[]) {
            return toStringArray((Object[])value);
        }
        else if (value != null) {
            return new String[] {value.toString()};
        }
        return null;
    }

	/**
	 * Converts an array of boxed objects into a primitive int array.
	 * @param inArray array that contains Number objects
	 * @return a primitive int array
	 * @throws ClassCastException if a non-Integer object is found in the array.
	 */
	public static int[] toIntArray(Object[] inArray) {
		int[] outArray = new int[inArray.length];
		for (int i = 0; i < inArray.length; i++) {
			outArray[i] = TiConvert.toInt(inArray[i]);
		}
		return outArray;
	}

	/**
	 * Converts an array of boxed objects into a primitive float array.
	 * @param inArray array that contains Number objects
	 * @return a primitive float array
	 * @throws ClassCastException if a non-Integer object is found in the array.
	 */
	public static float[] toFloatArray(Object[] inArray) {
		float[] outArray = new float[inArray.length];
		for (int i = 0; i < inArray.length; i++) {
			outArray[i] = ((Number) inArray[i]).floatValue();
		}
		return outArray;
	}
	
	public static int[] toIntArray(HashMap<String, Object> hashMap, String key, int[] defaultValue) {
        int[] result = defaultValue;

        if (hashMap.containsKey(key)) {
            result = toIntArray((Object[])hashMap.get(key));
        }
        return result;
    }
    
    public static float[] toFloatArray(HashMap<String, Object> hashMap, String key, float[] defaultValue) {
        float[] result = defaultValue;

        if (hashMap.containsKey(key)) {
            result = toFloatArray((Object[])hashMap.get(key));
        }
        return result;
    }

	/**
	 * Converts an array of boxed objects into a primitive double array.
	 * @param inArray array that contains Number objects
	 * @return a primitive double array
	 * @throws ClassCastException if a non-Integer object is found in the array.
	 */
	public static double[] toDoubleArray(Object[] inArray) {
		double[] outArray = new double[inArray.length];
		for (int i = 0; i < inArray.length; i++) {
			outArray[i] = ((Number) inArray[i]).doubleValue();
		}
		return outArray;
	}

	/**
	 * Converts an array of boxed objects into a primitive Number array.
	 * @param inArray array that contains Number objects
	 * @return a primitive Number array
	 * @throws ClassCastException if a non-Integer object is found in the array.
	 */
	public static Number[] toNumberArray(Object[] inArray) {
		Number[] outArray = new Number[inArray.length];
		for (int i = 0; i < inArray.length; i++) {
			outArray[i] = (Number) inArray[i];
		}
		return outArray;
	}
	/**
	 * Returns a new TiDimension object given a String value and type.
	 * Refer to {@link TiDimension#TiDimension(String, int)} for more details.
	 * @param value the dimension value.
	 * @param valueType the dimension type.
	 * @return a TiDimension instance.
	 */
	public static TiDimension toTiDimension(String value, int valueType)
	{
		if (value == null) return null;
		return new TiDimension(value, valueType);
	}

	/**
	 * Converts value to String, and if value is a Number, appends "px" to value, 
	 * then creates and returns a new TiDimension object with the new value and valueType.
	 * Refer to {@link TiDimension#TiDimension(String, int)} for more details.
	 * @param value the dimension value.
	 * @param valueType the dimension type.
	 * @return a TiDimension instance.
	 */
	public static TiDimension toTiDimension(Object value, int valueType)
	{
		if (value instanceof Number) {
			value = value.toString() + TiApplication.getInstance().getDefaultUnit();
		}
		if (value instanceof String) {
			return toTiDimension((String) value, valueType);
		}
		return null;
	}
	/**
	 * Takes a value out of a hash table then attempts to convert it using {@link #toTiDimension(Object, int)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key.
	 * @param valueType the dimension type.
	 * @return a TiDimension instance.
	 */
	public static TiDimension toTiDimension(HashMap<String, Object> hashMap, String key, int valueType)
	{
		return toTiDimension(hashMap.get(key), valueType);
	}

	/**
	 * Returns a url string by appending the 
	 * String representation of 'uri' to file:///android_asset/Resources/
	 * @param uri the uri, cannot be null.
	 * @return url string.
	 */
	public static String toURL(Uri uri)
	{
		String url = null;
		if (uri.isRelative()) {
			url = uri.toString();
			if (url.startsWith("/")) {
				url = ASSET_URL + "Resources" + url.substring(1);

			} else {
				url = ASSET_URL + "Resources/" + url;
			}

		} else {
			url = uri.toString();
		}

		return url;
	}

	/**
	 * Casts and returns value as TiBlob.
	 * @param value must be of type TiBlob.
	 * @return a TiBlob instance.
	 * @module.api
	 */
	public static TiBlob toBlob(Object value)
	{
		return (TiBlob) value;
	}

	/**
	 * A wrapper function.
	 * Refer to {@link #toBlob(Object)} for more details.
	 * @param object the hashmap.
	 * @param property the lookup key.
	 * @return a TiBlob instance.
	 * @module.api
	 */
	public static TiBlob toBlob(HashMap<String, Object> object, String property)
	{
		return toBlob(object.get(property));
	}

	public static Object fromJSON(Object value) {
        try {
            if (value instanceof JSONObject) {
                return new KrollDict((JSONObject)value);

            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray)value;
                Object[] values = new Object[array.length()];
                for (int i = 0; i < array.length(); i++) {
                    values[i] = fromJSON(array.get(i));
                }
                return values;

            } else if (value == JSONObject.NULL) {
                return null;

            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }

        return value;
    }
	
	public static Object parseJSON(String str) {
	   try {
           JSONTokener tokener = new JSONTokener(str);
           return fromJSON(tokener.nextValue());
        } catch (JSONException e) {
            return null;
        }
	}

	/**
	 * Converts a HashMap into a JSONObject and returns it. If data is null, null is returned.
	 * @param data the HashMap used for conversion.
	 * @return a JSONObject instance.
	 */
	public static JSONObject toJSON(HashMap<String, Object> data)
	{
		if (data == null) {
			return null;
		}
		JSONObject json = new JSONObject();

		for (String key : data.keySet()) {
			try {
				Object o = data.get(key);
				if (o == null) {
					json.put(key, JSONObject.NULL);

				} else if (o instanceof Number) {
					json.put(key, (Number) o);

				} else if (o instanceof String) {
					json.put(key, (String) o);

				} else if (o instanceof Boolean) {
					json.put(key, (Boolean) o);

				} else if (o instanceof Date) {
					json.put(key, toJSONString((Date) o));

				} else if (o instanceof HashMap) {
					json.put(key, toJSON((HashMap) o));

				} else if (o.getClass().isArray()) {
					json.put(key, toJSONArray((Object[]) o));

				} else {
					Log.w(TAG, "Unsupported type " + o.getClass());
				}

			} catch (JSONException e) {
				Log.w(TAG, "Unable to JSON encode key: " + key);
			}
		}

		return json;
	}

	/**
	 * Converts an object array into JSONArray and returns it.
	 * @param a  the object array to be converted.
	 * @return a JSONArray instance.
	 */
	public static JSONArray toJSONArray(Object[] a)
	{
		JSONArray ja = new JSONArray();
		for (Object o : a) {
			if (o == null) {
				Log.w(TAG, "Skipping null value in array", Log.DEBUG_MODE);
				continue;
			}

			// dead code, for now leave in place for debugging
			/*if (o == null) {
				ja.put(JSONObject.NULL);
			} else */
			if (o instanceof Number) {
				ja.put((Number) o);

			} else if (o instanceof String) {
				ja.put((String) o);

			} else if (o instanceof Boolean) {
				ja.put((Boolean) o);

			} else if (o instanceof Date) {
				ja.put(toJSONString((Date) o));

			} else if (o instanceof HashMap) {
				ja.put(toJSON((HashMap) o));

			} else if (o.getClass().isArray()) {
				ja.put(toJSONArray((Object[]) o));

			} else {
				Log.w(TAG, "Unsupported type " + o.getClass());
			}
		}

		return ja;
	}

	/**
	 * If value is a  Date, formats and returns it. Otherwise,
	 * return a String representation of value.
	 * @param value the value to convert.
	 * @return a String.
	 * @module.api
	 */
	public static String toJSONString(Object value)
	{
		if (value instanceof Date) {
			DateFormat df = new SimpleDateFormat(JSON_DATE_FORMAT);
			df.setTimeZone(TimeZone.getTimeZone("GMT"));

			return df.format((Date) value);

		} else if (value instanceof JSONObject) {
		    return value.toString();
		} else if (value instanceof HashMap){
            JSONObject  object = toJSON((HashMap<String, Object>) value);
            if (object != null) {
                return object.toString();
		} else {
                return null;
            }
        } else if (value instanceof Object[]){
            JSONArray  object = toJSONArray((Object[]) value);
            if (object != null) {
                return object.toString();
            } else {
                return null;
            }
		} else {
			return toString(value);
		}
	}

	/**
	 * Converts value into Date object and returns it.
	 * @param value the value to convert.
	 * @return a Date instance.
	 * @module.api
	 */
	public static Date toDate(Object value)
	{
		if (value instanceof Date) {
			return (Date) value;

		} else if (value instanceof Number) {
			long millis = ((Number) value).longValue();

			return new Date(millis);
		}

		return null;
	}

	/**
	 * A wrapper function.
	 * Refer to {@link #toDate(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key
	 * @return a Date instance.
	 * @module.api
	 */
	public static Date toDate(HashMap<String, Object> hashMap, String key)
	{
		return toDate(hashMap.get(key));
	}

	
	public static KrollDict toRectDict(int x, int y, int width, int height)
    {
        KrollDict dict = new KrollDict();
        TiDimension dim = new TiDimension(width, TiDimension.TYPE_WIDTH);
        dict.put(TiC.PROPERTY_WIDTH, (int)Math.round(dim.getAsDefault()));
        dim.setValue(height);
        dim.setValueType(TiDimension.TYPE_HEIGHT);
        dict.put(TiC.PROPERTY_HEIGHT, (int)Math.round(dim.getAsDefault()));
        dim.setValue(x);
        dim.setValueType(TiDimension.TYPE_LEFT);
        dict.put(TiC.PROPERTY_X, (int)Math.round(dim.getAsDefault()));
        dim.setValue(y);
        dim.setValueType(TiDimension.TYPE_TOP);
        dict.put(TiC.PROPERTY_Y, (int)Math.round(dim.getAsDefault()));
        return dict;
}

	public static KrollDict toPointDict(int x, int y)
    {
        KrollDict dict = new KrollDict();
        TiDimension dim = new TiDimension(x, TiDimension.TYPE_LEFT);
        dict.put(TiC.PROPERTY_X, (int)Math.round(dim.getAsDefault()));
        dim.setValue(y);
        dim.setValueType(TiDimension.TYPE_TOP);
        dict.put(TiC.PROPERTY_Y, (int)Math.round(dim.getAsDefault()));
        return dict;
    }

	/**
	 * Converts HashMap into Rect object and returns it.
	 * @param value the HashMap to convert.
	 * @return a RectF instance.
	 * @module.api
	 */
	public static RectF toRect(HashMap<String, Object>  map)
	{
		float left = TiUIHelper.getInPixels(map, TiC.PROPERTY_X);
		float top = TiUIHelper.getInPixels(map, TiC.PROPERTY_Y);
		float width = TiUIHelper.getInPixels(map, TiC.PROPERTY_WIDTH);
		float height = TiUIHelper.getInPixels(map, TiC.PROPERTY_HEIGHT);
		return new RectF(left, top, left + width, top + height);
	}
	/**
	 * Converts value into Rect object and returns it.
	 * @param value the value to convert.
	 * @return a RectF instance.
	 * @module.api
	 */
	public static RectF toRect(Object value)
	{
		if (value instanceof RectF) {
			return (RectF)value;

		} else if (value instanceof HashMap<?,?>) {
			return toRect((HashMap<String, Object>)value);
		}

		return null;
	}
	
	/**
	 * A wrapper function.
	 * Refer to {@link #toRect(Object)} for more details.
	 * @param hashMap the hash map to search.
	 * @param key the lookup key
	 * @return a RectF instance.
	 * @module.api
	 */
	public static RectF toRect(HashMap<String, Object> hashMap, String key)
	{
		return toRect(hashMap.get(key));
	}
	
	
	public static RectF toPaddingRect(Object value, RectF reuse)
    {
	    if (reuse == null) {
	        reuse = new RectF();
	    }
        if (value instanceof RectF) {
            reuse.set((RectF)value);
        } else if (value instanceof HashMap<?,?>) {
            KrollDict dict = new KrollDict((HashMap<String, Object>)value);
            reuse.left = TiUIHelper.getInPixels(dict,
                        TiC.PROPERTY_LEFT);
            reuse.right = TiUIHelper.getInPixels(dict,
                        TiC.PROPERTY_RIGHT);
            reuse.top = TiUIHelper.getInPixels(dict,
                        TiC.PROPERTY_TOP);
            reuse.bottom = TiUIHelper.getInPixels(dict,
                        TiC.PROPERTY_BOTTOM);
        } else if (value instanceof Number) {
            float padding = TiUIHelper.getRawSize(TiConvert.toString(value), null);
            reuse.set(padding, padding, padding, padding);
        } else if (value instanceof Object[] && ((Object[])value).length == 4) {
            float[] array = TiConvert.toFloatArray((Object[]) value);
            //top left bottom right
            reuse.set(TiUIHelper.getInPixels(array[1]), 
                    TiUIHelper.getInPixels(array[0]), 
                    TiUIHelper.getInPixels(array[3]), 
                    TiUIHelper.getInPixels(array[2]));
        } else {
            reuse.set(0, 0, 0, 0);
        }
        return reuse;
    }

	public static RectF toPaddingRect(HashMap<String, Object> hashMap, String key, RectF reuse)
    {
        return toPaddingRect(hashMap.get(key), reuse);
    }
	
	/**
	 * Converts value into Rect object and returns it.
	 * @param value the value to convert.
	 * @return a RectF instance.
	 * @module.api
	 */
	public static TiPoint toPoint(Object value)
	{
		if (value instanceof TiPoint) {
			return (TiPoint)value;

		} else if (value instanceof HashMap || value instanceof KrollDict) {
            return new TiPoint((HashMap)value);
        } else if (value instanceof Object[]) {
            Object[] array = (Object[])value;
            if (array.length >= 2) {
                return new TiPoint(array[0], array[1]);
            }
        }

		return null;
	}
	
    public static PointF toPointF(Object value)
    {
        if (value instanceof PointF) {
            return (PointF)value;
    
        } else if (value instanceof HashMap || value instanceof KrollDict) {
            HashMap hashmap = (HashMap)value;
            
            return new PointF(TiConvert.toFloat(hashmap, TiC.PROPERTY_X, 0.0f),
                    TiConvert.toFloat(hashmap, TiC.PROPERTY_Y, 0.0f));
        } else if (value instanceof Object[]) {
            Object[] array = (Object[])value;
            if (array.length >= 2) {
                return new PointF(TiConvert.toFloat(array[0], 0.0f),
                        TiConvert.toFloat(array[1], 0.0f));
            }
        }
    
        return null;
    }

	
    public static Ti2DMatrix IDENTITY_MATRIX = new Ti2DMatrix();
	/**
     * Converts value into A matrix object and returns it
     * @param value the value to convert.
     * @return a Ti2DMatrix instance.
     * @module.api
     */
    public static Ti2DMatrix toMatrix(Object value)
    {
        if (value instanceof Ti2DMatrix) {
            if (value.getClass() != Ti2DMatrix.class) {
                return new Ti2DMatrix((Ti2DMatrix)value); // case of _2DMatrixProxy
            }
            return (Ti2DMatrix)value;
        } else if (value instanceof HashMap) {
            return new Ti2DMatrix((HashMap)value);
        } else if (value instanceof String) {
            return new Ti2DMatrix((String)value);
        }
        return IDENTITY_MATRIX;
    }
    
    public static Ti2DMatrix toMatrixWithReuse(Object value, Ti2DMatrix reuse)
    {
        Ti2DMatrix result = reuse != IDENTITY_MATRIX ? reuse : null;
        if (value instanceof Ti2DMatrix) {
            return (Ti2DMatrix) value;

        } else if (value instanceof HashMap) {
            if (result != null) {
                result.reuseForNewMatrix((HashMap)value);
            }
            else {
                result = new Ti2DMatrix((HashMap)value);
            }
        } else if (value instanceof String) {
            if (result != null) {
                result.reuseForNewMatrix((String)value);
            }
            else {
                result = new Ti2DMatrix((String)value);
            }
        }
        else if (value != null && value.getClass().getSuperclass().equals(Ti2DMatrix.class)) {
            return new Ti2DMatrix((Ti2DMatrix)value); // case of _2DMatrixProxy
        } else {
            result = IDENTITY_MATRIX;
        }
        return result;
    }
    
    /**
     * convert value to a Ti2DMatrix, if can't return def
     * @param value the value to convert.
     * @param def the default value to return
     * @return an Ti2DMatrix value.
     * @module.api
     */
    public static Ti2DMatrix toMatrix(Object value, Ti2DMatrix def)
    {
        Ti2DMatrix result = toMatrix(value);
        if (result == null) {
            result = def;
        }
        return result;
    }
    
    /**
     * Takes a value out of a hash table then attempts to convert it using {@link #toMatrix(Object)}.
     * @param hashMap the hash map to search.
     * @param key the lookup key.
     * @return an Ti2DMatrix value.
     * @module.api
     */
    public static Ti2DMatrix toMatrix(HashMap<String, Object> hashMap, String key)
    {
        return toMatrix(hashMap.get(key));
    }

    /**
     * Takes a value out of a hash table then attempts to convert it using {@link #toMatrix(Object)} for more details.
     * @param hashMap the hash map to search.
     * @param key the lookup key.
     * @param def the default value to return.
     * @return an Ti2DMatrix value.
     * @module.api
     */
    public static Ti2DMatrix toMatrix(HashMap<String, Object> hashMap, String key, Ti2DMatrix def)
    {
        if (hashMap != null)
            return toMatrix(hashMap.get(key), def);
        return def;
    }
	
	/**
	 * Converts an array of boxed objects into a primitive Shadow array.
	 * @param inArray array that contains HashMap objects
	 * @return a primitive Shadow array
	 * @throws ClassCastException if a non-Hashmap object is found in the array.
	 */
	public static Shadow[] toShadowArray(Object[] inArray) {
		Shadow[] outArray = new Shadow[inArray.length];
		for (int i = 0; i < inArray.length; i++) {
			outArray[i] = TiUIHelper.getShadow(new KrollDict((HashMap) inArray[i]));
		}
		return outArray;
	}
	
	public static KrollDict toKrollDict(Object value)
	{
		if (value instanceof KrollDict) {
			return (KrollDict)value;
		} else if (value instanceof HashMap) {
			return new KrollDict((HashMap)value);
		}

		return null;
	}
	
	public static KrollDict toKrollDict(Object value, KrollDict def)
    {
	    KrollDict result = toKrollDict(value);
        if (result == null) {
            result = def;
        }
        return result;
    }
	
	public static KrollDict toKrollDict(HashMap<String, Object> hashMap, String key, KrollDict def)
    {
        if (hashMap != null)
            return toKrollDict(hashMap.get(key), def);
        return def;
    }


    public static HashMap toHashMap(Object value)
    {
        if (value instanceof KrollDict) {
            return (KrollDict)value;
        } else if (value instanceof HashMap) {
            return (HashMap)value;
        }

        return null;
    }
    
    public static byte[] toBytes(Object value) {
        byte[] bytes = null;
        if (value instanceof String) {
            try {
                bytes = TiConvert.toString(value).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else if (value instanceof Object[]) {
            final Object[] array =  ((Object[])value);
            final int length = array.length;
            bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) TiConvert.toInt(array[i]);
            }
        } else if (value instanceof TiBlob) {
            bytes = ((TiBlob) value).getBytes();
        } else if (value instanceof BufferProxy) {
            bytes = ((BufferProxy) value).getBuffer();
        } else if (value instanceof TiFileProxy) {
            try {
                bytes = ((TiFileProxy) value).read().getBytes();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bytes;
    }
    
    
    public static int[] bytesToIntArray(byte[] bytes) {
        if (bytes != null) {
            int[] outArray = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                outArray[i] = bytes[i] & 0xFF;
            }
            return outArray;
        }
        return null;
    }
}



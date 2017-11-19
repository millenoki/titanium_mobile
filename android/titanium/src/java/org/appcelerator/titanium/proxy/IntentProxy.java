/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.io.TiFileProvider;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContentProvider;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiIntentHelper;

import android.content.ClipData;
import android.graphics.Bitmap;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.net.Uri;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

@SuppressLint("NewApi")
@Kroll.proxy(propertyAccessors = {
	TiC.PROPERTY_URL
})
/**
 * This is a proxy representation of the Android Intent type.
 * Refer to <a href="http://developer.android.com/reference/android/content/Intent.html">Android Intent</a>
 * for more details.
 */
public class IntentProxy extends KrollProxy 
{
	private static final String TAG = "TiIntent";

	public static final int TYPE_ACTIVITY = 0;
	public static final int TYPE_SERVICE = 1;
	public static final int TYPE_BROADCAST = 2;

	protected Intent intent;
	protected int type = TYPE_ACTIVITY;

	public IntentProxy()
	{	
	}

	public IntentProxy(Intent intent)
	{
		this.intent = intent;

	}

	@Kroll.getProperty @Kroll.method
	public String getPackageName()
	{
		if (intent == null) {
			return null;
		}
		ComponentName componentName = intent.getComponent();
		if (componentName != null) {
			return componentName.getPackageName();
		}
		return null;
	}

	@Kroll.getProperty @Kroll.method
	public String getClassName()
	{
		if (intent == null) {
			return null;
		}
		ComponentName componentName = intent.getComponent();
		if (componentName != null) {
			return componentName.getClassName();
		}
		return null;
	}
    public IntentProxy(HashMap dict)
    {
        KrollDict params;
        if (dict instanceof KrollDict) {
            params = (KrollDict) dict;
        }
        else {
            params = new KrollDict(dict);
        }
        handleCreationDict(params);
    }

    public static IntentProxy fromObject(Object obj) {
        if (obj instanceof IntentProxy) {
            return (IntentProxy) obj;
        } else if (obj instanceof Intent) {
            return new IntentProxy((Intent) obj);
        } else if (obj instanceof HashMap) {
            return new IntentProxy((HashMap)obj);
        }
        return null;
    }
    
    public static Intent intentFromObject(Object obj) {
        if (obj instanceof IntentProxy) {
            return ((IntentProxy) obj).intent;
        } else if (obj instanceof Intent) {
            return (Intent) obj;
        } else if (obj instanceof HashMap) {
            return (new IntentProxy((HashMap)obj)).intent;
        }
        return null;
    }

	protected static char[] escapeChars = new char[] {
		'\\', '/', ' ', '.', '$', '&', '@'
	};

	protected static String getURLClassName(String url, int type)
	{
		switch (type) {
			case TYPE_ACTIVITY: return getURLClassName(url, "Activity");
			case TYPE_SERVICE: return getURLClassName(url, "Service");
			case TYPE_BROADCAST: return getURLClassName(url, "Broadcast");
		}
		return null;
	}

	protected static String getURLClassName(String url, String appendage)
	{
		List<String> parts = Arrays.asList(url.split("/"));
		if (parts.size() == 0) return null;
		
		int start = 0;
		if (parts.get(0).equals("app:") && parts.size() >= 3) {
			start = 2;
		}
		
		String className = TextUtils.join("_", parts.subList(start, parts.size()));
		if (className.endsWith(".js")) {
			className = className.substring(0, className.length()-3);
		}
		
		if (className.length() > 1) {
			className = className.substring(0, 1).toUpperCase() + className.substring(1);
		} else {
			className = className.toUpperCase();
		}
		
		for (char escapeChar : escapeChars) {
			className = className.replace(escapeChar, '_');
		}
		
		return className+appendage;
	}

	@Override
	public void handleCreationDict(HashMap dict)
	{
		super.handleCreationDict(dict);
		intent = new Intent();
		
		// See which set of options we have to work with.
		String action = TiConvert.toString(dict, TiC.PROPERTY_ACTION);
		String url = TiConvert.toString(dict, TiC.PROPERTY_URL);
		String data = TiConvert.toString(dict, TiC.PROPERTY_DATA);
		String className = TiConvert.toString(dict, TiC.PROPERTY_CLASS_NAME);
        String packageName = TiConvert.toString(dict, TiC.PROPERTY_PACKAGE_NAME);
		String type = TiConvert.toString(dict, TiC.PROPERTY_TYPE);
		int flags = 0;

		if (dict.containsKey(TiC.PROPERTY_FLAGS)) {
			flags = TiConvert.toInt(dict, TiC.PROPERTY_FLAGS);
			Log.d(TAG, "Setting flags: " + Integer.toString(flags), Log.DEBUG_MODE);
			intent.setFlags(flags);
		} else {
			setProperty(TiC.PROPERTY_FLAGS, intent.getFlags());
		}

		if (action != null) {
			Log.d(TAG, "Setting action: " + action, Log.DEBUG_MODE);
			intent.setAction(action);
		}

		if (packageName != null) {
			Log.d(TAG, "Setting package: " + packageName, Log.DEBUG_MODE);
			intent.setPackage(packageName);
		}

		if (url != null) {
			Log.d(TAG, "Creating intent for JS Activity/Service @ " + url, Log.DEBUG_MODE);
			packageName = TiApplication.getInstance().getPackageName();
			className = packageName + "." + getURLClassName(url, this.type);
		}

		if (className != null) {
			if (packageName != null) {
				Log.d(TAG, "Both className and packageName set, using intent.setClassName(packageName, className",
					Log.DEBUG_MODE);
				intent.setClassName(packageName, className);
			} else {
				try {
					Class<?> c = getClass().getClassLoader().loadClass(className);
					intent.setClass(TiApplication.getInstance().getApplicationContext(), c);
				} catch (ClassNotFoundException e) {
					Log.e(TAG, "Unable to locate class for name: " + className);
					throw new IllegalStateException("Missing class for name: " + className, e);
				}
			}
		}

		if (type == null) {
			if (action != null && action.equals(Intent.ACTION_SEND)) {
				type = "text/plain";
			}
		}

		// setType and setData are inexplicably intertwined
		// calling setType by itself clears the type and vice-versa
		// if you have both you _must_ call setDataAndType
		if (data != null) {
			Uri dataUri = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && data.startsWith("file://")) {
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				dataUri = TiFileProvider.createUriFrom(data);
			} else {
				dataUri = Uri.parse(data);
			}
			if (type != null) {
				Log.d(TAG, "setting type: " + type, Log.DEBUG_MODE);
				intent.setDataAndType(dataUri, type);
			} else {
				intent.setData(dataUri);
			}
		} else {
			intent.setType(type);
		}
		if (dict.get("categories") != null) {
		    Object obj = dict.get("categories");
		    if (obj instanceof Object[]) {
		        Object[] array = (Object[])obj;
		        for (int i = 0; i < array.length; i++) {
                    intent.addCategory(TiConvert.toString(array[i]));
                }
		    }
        }
		if (dict.get(TiC.PROPERTY_HTML) != null) {
		    putExtraHTML(Intent.EXTRA_TEXT, dict.get(TiC.PROPERTY_HTML));
		}
		else if (dict.get(TiC.PROPERTY_TEXT) != null) {
            putExtra(Intent.EXTRA_TEXT, dict.get(TiC.PROPERTY_TEXT));
        }
		if (dict.get("stream") != null) {
            putExtraStreamUri(dict.get("stream"));
        }
		if (dict.get("subject") != null) {
            putExtra(Intent.EXTRA_SUBJECT, dict.get("subject"));
		}
		if (dict.get("initialIntents") != null) {
            putExtraInitialIntents(dict.get("initialIntents"));
        }
	}

	@Kroll.method
	public IntentProxy putExtraUri(String key, Object value) {
        if (value == null) {
            return;
        }
        
        if (value instanceof String) {
            String extraString = (String) value;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && extraString.startsWith("file://")) {
                Uri contentUri = TiFileProvider.createUriFrom(extraString);
                ClipData clipData = ClipData.newRawUri("FILE", contentUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(clipData);
                intent.putExtra(key, contentUri);
            } else {
                intent.putExtra(key, Uri.parse(extraString));
            }
        } else if (value instanceof Object[]) {
            try {
                Object[] objVal = (Object[]) value;
                String[] stringArray = Arrays.copyOf(objVal, objVal.length, String[].class);
                ArrayList<Uri> imageUris = new ArrayList<Uri>();
                ClipData clipData = null;
                for(String s : stringArray) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && s.startsWith("file://")) {
                        Uri contentUri = TiFileProvider.createUriFrom(s);
                        imageUris.add(contentUri);
                        if (clipData == null) {
                            clipData = ClipData.newRawUri("FILES", contentUri);
                        } else {
                            clipData.addItem(new ClipData.Item(contentUri));
                        }
                    } else {
                        imageUris.add(Uri.parse(s));
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(clipData);
                }
                intent.putParcelableArrayListExtra(key, imageUris);
            } catch (Exception ex) {
                Log.e(TAG, "Error unimplemented put conversion ", ex.getMessage());
            }
        }
        return this;
	}

    private void putExtraStreamUri(Object value) {
        if (value instanceof Object[]) {
            Object[] values = (Object[]) value;
            ArrayList<Uri> uris = new ArrayList<Uri>();
            for (int i = 0; i < values.length; i++) {
                Uri uri = Uri.withAppendedPath(TiContentProvider.getContentUri(), resolveUrl(null, TiConvert.toString(values[i])));
                uris.add(uri);
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        else {
            Uri uri = Uri.withAppendedPath(TiContentProvider.getContentUri(), resolveUrl(null, TiConvert.toString(value)));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
        }
    }
    
	@Kroll.method
    public IntentProxy putExtraHTML(String key, Object value)
	{
        intent.putExtra(key, Html.fromHtml((String) value));
        if (value instanceof Object[]) {
            Object[] values = (Object[]) value;
            ArrayList<Spanned> htmls = new ArrayList<Spanned>();
            for (int i = 0; i < values.length; i++) {
                htmls.add(Html.fromHtml(TiConvert.toString(values[i])));
            }
            intent.putExtra(key, htmls);
        }
        else {
            intent.putExtra(key, Html.fromHtml(TiConvert.toString(value)));
        }
        return this;
    }        

	@Kroll.method
	public IntentProxy putExtra(String key, Object value)
	{
		if (value == null) {
			return this;
		}
		if (value instanceof String) {
			intent.putExtra(key, (String) value);
		} else if (value instanceof Boolean) {
			intent.putExtra(key, (Boolean) value);
		} else if (value instanceof Double) {
			intent.putExtra(key, (Double) value);
		} else if (value instanceof Integer) {
			intent.putExtra(key, (Integer) value);
		} else if (value instanceof Long) {
			intent.putExtra(key, (Long) value);
		} else if (value instanceof IntentProxy) {
			intent.putExtra(key, (Intent) ((IntentProxy) value).getIntent());
		} else if (value instanceof TiBlob) {
			intent.putExtra(key, ((TiBlob) value).getImage());
		} else if (value instanceof Object[]) {
			try {
				Object[] objVal = (Object[]) value;
				String[] stringArray = Arrays.copyOf(objVal, objVal.length, String[].class);
				intent.putExtra(key, stringArray);
			} catch (Exception ex) {
				Log.e(TAG, "Error unimplemented put conversion ", ex.getMessage());
			}
		} else {
			intent.putExtra(key, TiConvert.toString(value));
		}
		return this;
	}

	@Kroll.method
	public IntentProxy addFlags(int flags)
	{
		intent.addFlags(flags);
        return this;
	}

	@Kroll.setProperty @Kroll.method
	public void setFlags(int flags)
	{
		intent.setFlags(flags);
	}

	@Kroll.getProperty @Kroll.method
	public int getFlags()
	{
		return intent.getFlags();
	}
	@Kroll.method
	public IntentProxy addCategory(String category)
	{
		if (category != null) {
			Log.d(TAG, "Adding category: " + category, Log.DEBUG_MODE);
			intent.addCategory(category);
		}
        return this;
	}

	@Kroll.method
	public String getStringExtra(String name)
	{
		if (!intent.hasExtra(name)) {
			return null;
		}

		String result = intent.getStringExtra(name);
		if (result == null) {
			// One more try as parcelable extra, such as when it's a Uri.
			// We can't really support getParcelableExtra(name) by itself,
			// since the type of object coming out of it is unknown and
			// might not make its way successfully over to Javascript.
			// By getting it as a string, we at least allow people to grab
			// Uris (Intent.STREAM) stored as parcelable extras, which is a
			// very common use case.
			Object parcelable = intent.getParcelableExtra(name);
			if (parcelable != null) {
				result = parcelable.toString();
			}
		}

		return result;
	}

	@Kroll.method
	public boolean getBooleanExtra(String name, boolean defaultValue)
	{
		return intent.getBooleanExtra(name, defaultValue);
	}

	@Kroll.method
	public int getIntExtra(String name, int defaultValue)
	{
		return intent.getIntExtra(name, defaultValue);
	}

	@Kroll.method
	public long getLongExtra(String name, long defaultValue)
	{
		return intent.getLongExtra(name, defaultValue);
	}

	@Kroll.method
	public double getDoubleExtra(String name, double defaultValue)
	{
		return intent.getDoubleExtra(name, defaultValue);
	}

	@Kroll.method
	public TiBlob getBlobExtra(String name)
	{
		InputStream is = null;
		ByteArrayOutputStream bos = null;
		try {

			Object returnData = intent.getExtras().getParcelable(name);
			if (returnData instanceof Uri) {

				Uri uri = (Uri) returnData;
				is = TiApplication.getInstance().getContentResolver().openInputStream(uri);

				bos = new ByteArrayOutputStream();

				int len;
				int size = 4096;
				byte[] buf = new byte[size];
				while ((len = is.read(buf, 0, size)) != -1) {
					bos.write(buf, 0, len);
				}
				buf = bos.toByteArray();

				return TiBlob.blobFromObject(buf);
			} else if (returnData instanceof Bitmap) {

				Bitmap returnBitmapData = (Bitmap) returnData;
				return TiBlob.blobFromObject(returnBitmapData);
			}

		} catch (Exception e) {
			Log.e(TAG, "Error getting blob extra: " + e.getMessage(), e);
			return null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), Log.DEBUG_MODE);
				}
			}
			if (bos != null) {
				try {
					bos.close();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), Log.DEBUG_MODE);
				}
			}
		}
		return null;
	}

	@Kroll.method @Kroll.getProperty
	public String getData()
	{
		return intent.getDataString();
	}

	/**
	 * @return the associated intent.
	 */
	public Intent getIntent()
	{ 
		return intent;
	}

	@Kroll.method @Kroll.getProperty
	public String getType()
	{
		return intent.getType();
	}

	@Kroll.method @Kroll.setProperty
	public void setType(String type)
	{
		intent.setType(type);
	}

	@Kroll.method @Kroll.getProperty
	public String getAction()
	{
		return intent.getAction();
	}

	@Kroll.method @Kroll.setProperty
	public void setAction(String action)
	{
		intent.setAction(action);
	}

	/**
	 * @return intent type for internal purposes (TYPE_ACTIVITY, etc.)
	 */
	public int getInternalType()
	{
		return type;
	}

	/**
	 * Sets the intent type.
	 * @param type the intent type for internal purposes (TYPE_ACTIVITY etc.)
	 */
	public void setInternalType(int type)
	{
		this.type = type;
	}

	@Kroll.method
	public boolean hasExtra(String name)
	{
		if (intent != null) {
			return intent.hasExtra(name);
		}
		return false;
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android.Intent";
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
    @Kroll.method
    public IntentProxy putExtraInitialIntents(Object arg)
    {
	    if (!(arg instanceof Object[])) return this;
	    Object[] values = (Object[]) arg;
        List<Intent> extraIntents = new ArrayList<Intent>();
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Intent) {
                extraIntents.add((Intent) value);
            } else if (value instanceof IntentProxy) {
                extraIntents.add(((IntentProxy) value).intent);
            } else if (value instanceof HashMap) {
                if (((HashMap)value).containsKey("intent")) {
                    Object intentProp = ((HashMap)value).get("intent");
                    Intent intent = intentFromObject(intentProp);
                    if (intent != null) {
                        LabeledIntent labelIntent = new LabeledIntent(
                                intent,
                                intent.getPackage(),
                                TiConvert.toString((HashMap)value, "label", ""),
                                TiConvert.toInt((HashMap)value, "icon", 0));
                        if (labelIntent != null) {
                            extraIntents.add(labelIntent);
}
                    }
                }
                else {
                    IntentProxy intentProxy = new IntentProxy((HashMap)value);
                    extraIntents.add(intentProxy.intent);
                }
            }
        }
        intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Parcelable[]{}));
        return this;
    }
	
	@Kroll.method
    public Object[] queryIntentActivities()
    {
	    return TiIntentHelper.queryIntentActivities(intent);
    }

}

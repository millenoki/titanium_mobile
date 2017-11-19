/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2017 by Axway, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.android.notificationmanager;

import java.util.HashMap;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.util.TiConvert;


import android.annotation.TargetApi;
import android.app.NotificationChannel;

@TargetApi(26)
@Kroll.proxy(propertyAccessors = {
		TiC.PROPERTY_BYPASS_DND,
		TiC.PROPERTY_DESCRIPTION,
		TiC.PROPERTY_ENABLE_LIGHTS,
		TiC.PROPERTY_ENABLE_VIBRATION,
		TiC.PROPERTY_GROUP_ID,
		TiC.PROPERTY_IMPORTANCE,
		TiC.PROPERTY_LIGHT_COLOR,
		TiC.PROPERTY_LOCKSCREEN_VISIBILITY,
		TiC.PROPERTY_NAME,
		TiC.PROPERTY_SHOW_BADGE,
		TiC.PROPERTY_VIBRATE_PATTERN
})
public class NotificationChannelProxy extends KrollProxy
{
	private static final String TAG = "TiNotificationChannel";

	private NotificationChannel channel = null;

	public NotificationChannelProxy()
	{
		super();
	}

	@Override
	public void handleCreationDict(HashMap d)
	{
		super.handleCreationDict(d);
		if (TiC.O_OR_GREATER && d != null &&
				d.containsKey(TiC.PROPERTY_ID) && d.containsKey(TiC.PROPERTY_NAME) && d.containsKey(TiC.PROPERTY_IMPORTANCE)) {
			
			channel = new NotificationChannel(TiConvert.toString(d, TiC.PROPERTY_ID), TiConvert.toString(d, TiC.PROPERTY_NAME), TiConvert.toInt(d, TiC.PROPERTY_IMPORTANCE));

			if (d.containsKey(TiC.PROPERTY_BYPASS_DND)) {
				setBypassDnd(TiConvert.toBoolean(d, TiC.PROPERTY_BYPASS_DND));
			}
			if (d.containsKey(TiC.PROPERTY_DESCRIPTION)) {
				setDescription(TiConvert.toString(d, TiC.PROPERTY_DESCRIPTION));
			}
			if (d.containsKey(TiC.PROPERTY_ENABLE_LIGHTS)) {
				setEnableLights(TiConvert.toBoolean(TiC.PROPERTY_ENABLE_LIGHTS));
			}
			if (d.containsKey(TiC.PROPERTY_ENABLE_VIBRATION)) {
				setEnableVibration(TiConvert.toBoolean(d, TiC.PROPERTY_ENABLE_VIBRATION));
			}
			if (d.containsKey(TiC.PROPERTY_GROUP_ID)) {
				setGroupId(TiConvert.toString(d, TiC.PROPERTY_GROUP_ID));
			}
			if (d.containsKey(TiC.PROPERTY_LIGHT_COLOR)) {
				setLightColor(TiConvert.toInt(d, TiC.PROPERTY_LIGHT_COLOR));
			}
			if (d.containsKey(TiC.PROPERTY_LOCKSCREEN_VISIBILITY)) {
				setLockscreenVisibility(TiConvert.toInt(d, TiC.PROPERTY_LOCKSCREEN_VISIBILITY));
			}
			if (d.containsKey(TiC.PROPERTY_SHOW_BADGE)) {
				setShowBadge(TiConvert.toBoolean(d, TiC.PROPERTY_SHOW_BADGE));
			}
			if (d.containsKey(TiC.PROPERTY_VIBRATE_PATTERN)) {
				setVibrationPattern(d.get(TiC.PROPERTY_VIBRATE_PATTERN));
			}
		} else {
			Log.e(TAG, "could not create notification channel");
		}
	}

	@Kroll.method @Kroll.getProperty
	public String getId()
	{
		return channel.getId();
	}

	@Kroll.method @Kroll.setProperty
	public void setEnableLights(boolean lights)
	{
		channel.enableLights(lights);
		setProperty(TiC.PROPERTY_ENABLE_LIGHTS, lights);
	}

	@Kroll.method @Kroll.setProperty
	public void setEnableVibration(boolean vibration)
	{
		channel.enableVibration(vibration);
		setProperty(TiC.PROPERTY_ENABLE_VIBRATION, vibration);
	}

	@Kroll.method @Kroll.setProperty
	public void	setBypassDnd(boolean bypassDnd)
	{
		channel.setBypassDnd(bypassDnd);
		setProperty(TiC.PROPERTY_BYPASS_DND, bypassDnd);
	}

	@Kroll.method @Kroll.setProperty
	public void	setDescription(String description)
	{
		channel.setDescription(description);
		setProperty(TiC.PROPERTY_DESCRIPTION, description);
	}

	@Kroll.method @Kroll.setProperty
	public void	setGroupId(String groupId)
	{
		channel.setGroup(groupId);
		setProperty(TiC.PROPERTY_GROUP_ID, groupId);
	}

	@Kroll.method @Kroll.setProperty
	public void setImportance(int importance)
	{
		channel.setImportance(importance);
		setProperty(TiC.PROPERTY_IMPORTANCE, importance);
	}

	@Kroll.method @Kroll.setProperty
	public void setLightColor(int argb)
	{
		channel.setLightColor(argb);
		setProperty(TiC.PROPERTY_LIGHT_COLOR, argb);
	}

	@Kroll.method @Kroll.setProperty
	public void setLockscreenVisibility(int lockscreenVisibility)
	{
		channel.setLockscreenVisibility(lockscreenVisibility);
		setProperty(TiC.PROPERTY_LOCKSCREEN_VISIBILITY, lockscreenVisibility);
	}

	@Kroll.method @Kroll.setProperty
	public void setName(String name)
	{
		channel.setName(name);
		setProperty(TiC.PROPERTY_NAME, name);
	}

	@Kroll.method @Kroll.setProperty
	public void setShowBadge(boolean showBadge)
	{
		channel.setShowBadge(showBadge);
		setProperty(TiC.PROPERTY_SHOW_BADGE, showBadge);
	}

	@Kroll.method @Kroll.setProperty
	public void setVibrationPattern(Object patternObj)
	{
		if (patternObj instanceof Object[]) {
			Object[] patternArray = (Object[]) patternObj;
			long[] pattern = new long[patternArray.length];

			for (int i = 0; i < patternArray.length; i++) {
				if (!(patternArray[i] instanceof Integer)) {
					Log.e(TAG, "invalid vibratePattern array element");
					return;
				}
				pattern[i] = ((Integer) patternArray[i]).intValue();
			}
			channel.setVibrationPattern(pattern);
			setProperty(TiC.PROPERTY_VIBRATE_PATTERN, patternArray);
		}
	}

	public NotificationChannel getNotificationChannel()
	{
		return channel;
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android.NotificationChannel";
	}
}

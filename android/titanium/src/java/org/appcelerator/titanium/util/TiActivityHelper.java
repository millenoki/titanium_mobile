package org.appcelerator.titanium.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.appcelerator.titanium.ITiAppInfo;
import org.appcelerator.titanium.TiApplication;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;

public class TiActivityHelper {
    /**
     * Used to know if Apollo was sent into the background
     * 
     * @param context The {@link Context} to use
     */
    public static final boolean isApplicationSentToBackground(final Context context) {
        final ActivityManager activityManager = (ActivityManager)context
                .getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            final ComponentName topActivity = tasks.get(0).topActivity;
            if (!topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }
    
    private static String capitalize(String line)
    {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1).toLowerCase();
    }
    
    public static String getMainActivityName(){
        Pattern pattern = Pattern.compile("[^A-Za-z0-9_]");
        ITiAppInfo appInfo = TiApplication.getInstance().getAppInfo();
        String str = appInfo.getName();
        String className = "";
        String[] splitStr = pattern.split(str);
        for (int i = 0; i < splitStr.length; i++) {
            className = className + capitalize(splitStr[i]);
        }
        Pattern pattern2 = Pattern.compile("^[0-9]");
        Matcher matcher = pattern2.matcher(className);
        if (matcher.matches()) {
            className = "_" + className;
        }
        return appInfo.getId() + "." + className + "Activity";
    }
}

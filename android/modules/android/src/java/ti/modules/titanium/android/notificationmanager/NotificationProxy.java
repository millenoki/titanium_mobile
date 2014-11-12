/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.android.notificationmanager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiDrawableReference;

import com.squareup.picasso.Cache;
import com.squareup.picasso.OkHttpDownloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.squareup.picasso.Picasso.LoadedFrom;

import ti.modules.titanium.android.AndroidModule;
import ti.modules.titanium.android.PendingIntentProxy;
import ti.modules.titanium.android.RemoteViewsProxy;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Message;
import android.view.View;

@Kroll.proxy(creatableInModule = AndroidModule.class, propertyAccessors = {
        TiC.PROPERTY_CONTENT_TEXT, TiC.PROPERTY_CONTENT_TITLE })
public class NotificationProxy extends KrollProxy implements Target {
    private static final String TAG = "TiNotification";
    private static final boolean JELLY_BEAN_OR_GREATER = (Build.VERSION.SDK_INT >= 16);

    protected Notification notification;

    private int currentId = -1;

    private static final int MSG_FIRST_ID = 100;
    private static final int MSG_SET_LARGE_ICON = MSG_FIRST_ID + 1;
    private RemoteViewsProxy contentView = null;
    private RemoteViewsProxy bigContentView = null;

    // private static final String NOTIFICATION_DELETED_ACTION =
    // "NOTIFICATION_DELETED";
    //
    // private final BroadcastReceiver deleteReceiver = new BroadcastReceiver()
    // {
    // @Override
    // public void onReceive(Context context, Intent intent) {
    // if (intent.getIntExtra("notif.id", 0) ==
    // NotificationProxy.this.currentId) {
    // NotificationProxy.this.didHide();
    // }
    // }
    // };

    private class LoadLocalCoverArtTask extends
            AsyncTask<TiDrawableReference, Void, Drawable> {
        private Cache cache;
        private TiDrawableReference imageref;

        LoadLocalCoverArtTask(Cache cache) {
            this.cache = cache;
        }

        @Override
        protected Drawable doInBackground(TiDrawableReference... params) {
            imageref = params[0];
            return imageref.getDrawable();

        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            Bitmap bitmap = null;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
                cache.set(imageref.getUrl(), bitmap);
            }
            notification.largeIcon = bitmap;
            update();
        }
    }

    public NotificationProxy() {
        super();
        notification = new Notification(android.R.drawable.stat_sys_warning,
                null, System.currentTimeMillis());
        notification.flags = Notification.FLAG_AUTO_CANCEL;
    }

    public NotificationProxy(TiContext tiContext) {
        this();
    }

    public void setCurrentId(final int currentId) {
        if (this.currentId != currentId) {
            willShow();
            // about to be shown, let register for delete

            this.currentId = currentId;
        }

    }

    public void willShow() {
        // if (notification.deleteIntent == null) {
        // Intent intent = new Intent(NOTIFICATION_DELETED_ACTION);
        // intent.putExtra("notif.id", currentId);
        // notification.deleteIntent = PendingIntent.getBroadcast(getActivity(),
        // 0, intent, 0);
        // TiApplication.getAppContext().registerReceiver(deleteReceiver, new
        // IntentFilter(NOTIFICATION_DELETED_ACTION));
        // }
        if (contentView != null) {
            contentView.willShow();
        }
        if (bigContentView != null) {
            bigContentView.willShow();
        }
    }

    public void didHide() {
        // TiApplication.getAppContext().unregisterReceiver(deleteReceiver);
        if (contentView != null) {
            contentView.didHide();
        }
        if (bigContentView != null) {
            bigContentView.didHide();
        }
        currentId = 0;
    }

    private NotificationManager getManager() {
        return (NotificationManager) TiApplication.getInstance()
                .getSystemService(Activity.NOTIFICATION_SERVICE);
    }

    @Kroll.method
    public boolean update(@Kroll.argument(optional = true) HashMap args) {
        if (args != null) {
            applyProperties(args);
        }
        if (this.currentId >= 0) {
            NotificationManager manager = getManager();
            Log.d(TAG, "updating notification " + this.currentId, Log.DEBUG_MODE);
            manager.notify(this.currentId, notification);
            return true;
        }
        return false;
    }

    public void update() {
        update(null);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_SET_LARGE_ICON: {
            handleSetLargeIcon(msg.obj);
            return true;
        }
        }
        return super.handleMessage(msg);
    }

    @Override
    public void release() {
        super.release();
    }

    @Override
    public void handleCreationDict(KrollDict d) {
        super.handleCreationDict(d);
        if (d == null) {
            return;
        }
        for (String key : d.keySet()) {
            propertySet(key, d.get(key), null, false);
        }
        checkLatestEventInfoProperties(d);
    }

    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        if (key.equals(TiC.PROPERTY_ICON)) {
            setIcon(newValue);
        }
        else if (key.equals(TiC.PROPERTY_TICKER_TEXT)) {
            setTickerText(TiConvert.toString(newValue));
        }
        else if (key.equals(TiC.PROPERTY_WHEN)) {
            setWhen(newValue);
        }
        else if (key.equals(TiC.PROPERTY_AUDIO_STREAM_TYPE)) {
            setAudioStreamType(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_CONTENT_VIEW)) {
            setContentView(newValue);
        }
        else if (key.equals(TiC.PROPERTY_BIG_CONTENT_VIEW)) {
            setBigContentView(newValue);
        }
        else if (key.equals(TiC.PROPERTY_CONTENT_INTENT)) {
            setContentIntent(newValue);
        }
        else if (key.equals(TiC.PROPERTY_DEFAULTS)) {
            setDefaults(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_DELETE_INTENT)) {
            setDeleteIntent(newValue);
        }
        else if (key.equals(TiC.PROPERTY_FLAGS)) {
            setFlags(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_ICON_LEVEL)) {
            setIconLevel(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_LED_ARGB)) {
            setLedARGB(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_LED_OFF_MS)) {
            setLedOffMS(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_LED_ON_MS)) {
            setLedOnMS(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_NUMBER)) {
            setNumber(TiConvert.toInt(newValue));
        }
        else if (key.equals(TiC.PROPERTY_SOUND)) {
            setSound(TiConvert.toString(newValue));
        }
        else if (key.equals(TiC.PROPERTY_VIBRATE_PATTERN)) {
            setVibratePattern((Object[]) newValue);
        }
    }
    
    @Override
    public void onPropertyChanged(String name, Object value, Object oldValue) {
        propertySet(name, value, oldValue, true);
    }


    public void setIcon(Object icon) {
        if (icon instanceof Number) {
            notification.icon = ((Number) icon).intValue();
        } else {
            String iconUrl = TiConvert.toString(icon);
            // TiContext context = invocation == null ? getTiContext() :
            // invocation.getTiContext();
            String iconFullUrl = resolveUrl(null, iconUrl);
            notification.icon = TiUIHelper.getResourceId(iconFullUrl);
            if (notification.icon == 0) {
                Log.w(TAG, "No image found for " + iconUrl);
            }
        }
    }

    public void setLargeIcon(Object icon) {
        if (TiApplication.isUIThread()) {
            handleSetLargeIcon(icon);
        } else {
            getMainHandler().obtainMessage(MSG_SET_LARGE_ICON, icon)
                    .sendToTarget();
        }
    }

    public void setTickerText(String tickerText) {
        notification.tickerText = tickerText;
    }
    
    public void setWhen(Object when) {
        if (when instanceof Date) {
            notification.when = ((Date) when).getTime();
        } else {
            notification.when = ((Double) TiConvert.toDouble(when)).longValue();
        }
    }

    public void setAudioStreamType(int type) {
        notification.audioStreamType = type;
    }

    public void setContentView(Object obj) {
        if (contentView != null) {
            contentView.didHide();
            contentView.setNotification(null);
            contentView.setParentForBubbling(null);
        }
        contentView = RemoteViewsProxy.fromObject(obj);
        if (contentView != null) {
            contentView.setNotification(this);
            contentView.setParentForBubbling(this);
            setProperty(TiC.PROPERTY_CONTENT_VIEW, contentView);
            notification.contentView = contentView.getRemoteViews();
        } else {
            notification.contentView = null;
        }
    }

    public void setBigContentView(Object obj) {
        if (JELLY_BEAN_OR_GREATER) {
            if (bigContentView != null) {
                bigContentView.didHide();
                bigContentView.setNotification(null);
                bigContentView.setParentForBubbling(null);
            }
            bigContentView = RemoteViewsProxy.fromObject(obj);
            if (bigContentView != null) {
                bigContentView.setNotification(this);
                bigContentView.setParentForBubbling(this);
                setProperty(TiC.PROPERTY_BIG_CONTENT_VIEW, bigContentView);
                notification.bigContentView = bigContentView.getRemoteViews();
            } else {
                notification.bigContentView = null;
            }
        }
    }

    public void setContentIntent(Object obj) {
        PendingIntentProxy proxy = PendingIntentProxy.fromObject(obj);
        notification.contentIntent = proxy.getPendingIntent();
    }

    public void setDefaults(int defaults) {
        notification.defaults = defaults;
    }

    public void setDeleteIntent(Object obj) {
        PendingIntentProxy proxy = PendingIntentProxy.fromObject(obj);
        notification.deleteIntent = proxy.getPendingIntent();
    }

    public void setFlags(int flags) {
        notification.flags = flags;
    }

    public void setIconLevel(int iconLevel) {
        notification.iconLevel = iconLevel;
    }

    public void setLedARGB(int ledARGB) {
        notification.ledARGB = ledARGB;
    }

    public void setLedOffMS(int ledOffMS) {
        notification.ledOffMS = ledOffMS;
    }

    public void setLedOnMS(int ledOnMS) {
        notification.ledOnMS = ledOnMS;
    }

    public void setNumber(int number) {
        notification.number = number;
    }

    public void setSound(String url) {
        notification.sound = Uri.parse(resolveUrl(null, url));

    }
    
    public void setVibratePattern(Object[] pattern) {
        if (pattern != null) {
            notification.vibrate = new long[pattern.length];
            for (int i = 0; i < pattern.length; i++) {
                notification.vibrate[i] = ((Double) TiConvert
                        .toDouble(pattern[i])).longValue();
            }
        }
    }

    @Kroll.method
    public void cancel() {
        if (this.currentId >= 0) {
            NotificationManager manager = getManager();
            manager.cancel(this.currentId);
        }
    }

    protected void checkLatestEventInfoProperties(KrollDict d) {
        if (d.containsKeyAndNotNull(TiC.PROPERTY_CONTENT_TITLE)
                || d.containsKeyAndNotNull(TiC.PROPERTY_CONTENT_TEXT)) {
            String contentTitle = "";
            String contentText = "";
            PendingIntent contentIntent = null;
            View contentView = null;
            if (d.containsKeyAndNotNull(TiC.PROPERTY_CONTENT_TITLE)) {
                contentTitle = TiConvert
                        .toString(d, TiC.PROPERTY_CONTENT_TITLE);
            }
            if (d.containsKeyAndNotNull(TiC.PROPERTY_CONTENT_TEXT)) {
                contentText = TiConvert.toString(d, TiC.PROPERTY_CONTENT_TEXT);
            }
            if (d.containsKey(TiC.PROPERTY_CONTENT_INTENT)) {
                PendingIntentProxy intentProxy = (PendingIntentProxy) d
                        .get(TiC.PROPERTY_CONTENT_INTENT);
                contentIntent = intentProxy.getPendingIntent();
            }

            Context c = getActivity();
            if (c == null) {
                c = TiApplication.getInstance().getApplicationContext();
            }
            notification.setLatestEventInfo(c, contentTitle, contentText,
                    contentIntent);
        }
    }

    @Kroll.method
    public void setLatestEventInfo(String contentTitle, String contentText,
            PendingIntentProxy contentIntent) {
        Context c = getActivity();
        if (c == null) {
            c = TiApplication.getInstance().getApplicationContext();
        }
        notification.setLatestEventInfo(c, contentTitle, contentText,
                contentIntent.getPendingIntent());
    }

    public Notification getNotification() {
        return notification;
    }

    @Override
    public String getApiName() {
        return "Ti.Android.Notification";
    }

    private void handleSetLargeIcon(final Object obj) {
        TiDrawableReference imageref = TiDrawableReference
                .fromObject(this, obj);
        if (imageref.isNetworkUrl()) {
            Picasso picasso = TiApplication.getPicassoInstance();

            if (hasProperty(TiC.PROPERTY_HTTP_OPTIONS)) {
                // Prepare OkHttp
                final Context context = TiApplication.getAppContext();
                picasso = new Picasso.Builder(context).downloader(
                        new OkHttpDownloader(context) {
                            @Override
                            protected HttpURLConnection openConnection(Uri uri)
                                    throws IOException {
                                HttpURLConnection connection = super
                                        .openConnection(uri);
                                TiApplication
                                        .prepareURLConnection(
                                                connection,
                                                (HashMap) getProperty(TiC.PROPERTY_HTTP_OPTIONS));
                                return connection;
                            }
                        }).build();
            }
            // picasso will cancel running request if reusing
            picasso.cancelRequest(this);
            picasso.load(imageref.getUrl()).into(this);
        } else {
            String cacheKey = imageref.getCacheKey();
            Cache cache = TiApplication.getImageMemoryCache();
            Bitmap bitmap = (cacheKey != null) ? cache.get(cacheKey) : null;
            if (bitmap == null) {
                (new LoadLocalCoverArtTask(cache)).execute(imageref);
                return;
            }
            notification.largeIcon = bitmap;
            update();
        }
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
        notification.largeIcon = bitmap;
        update();
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        notification.largeIcon = null;
        update();
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
        // TODO Auto-generated method stub

    }
}

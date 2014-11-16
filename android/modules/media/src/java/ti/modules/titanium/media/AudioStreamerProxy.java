/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiEnhancedServiceProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.util.TiUIHelper;

import ti.modules.titanium.media.streamer.AudioStreamerService;
import android.app.Service;
import android.content.Intent;

@Kroll.proxy(creatableInModule = MediaModule.class, propertyAccessors = {
        TiC.PROPERTY_VOLUME, TiC.PROPERTY_METADATA })
public class AudioStreamerProxy extends TiEnhancedServiceProxy {
    private static final String TAG = "AudioStreamerProxy";

    AudioStreamerService tiService = null;
    List<Object> mPlaylist;

    public AudioStreamerProxy() {
        super(null, null, null);
        if (isMyServiceRunning()) {
            startService();
        }
    }

    /**
     * For when a service started via startService() creates a proxy when it
     * starts running
     */
    public AudioStreamerProxy(Service service, Intent intent,
            Integer serviceInstanceId) {
        super(service, intent, serviceInstanceId);
        defaultValues.put(TiC.PROPERTY_VOLUME, 1.0f);
        defaultValues.put(TiC.PROPERTY_TIME, 0);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Class serviceClass() {
        return AudioStreamerService.class;
    }

    @Override
    public void unbindService() {
        super.unbindService();
        this.tiService = null;
    }

    protected void invokeBoundService() {
        this.tiService = (AudioStreamerService) this.service;
        super.invokeBoundService();
    }

    private void runAction(String cmd) {
        Intent intent = new Intent(getIntent().getIntent());
        // intent.setAction(action);
        intent.putExtra("command", cmd);
        startService(intent);
        // else if (target == mPauseButton)
        // startService(new Intent(AudioStreamerService.ACTION_PAUSE));
        // else if (target == mSkipButton)
        // startService(new Intent(AudioStreamerService.ACTION_SKIP));
        // else if (target == mRewindButton)
        // startService(new Intent(AudioStreamerService.ACTION_REWIND));
        // else if (target == mStopButton)
        // startService(new Intent(AudioStreamerService.ACTION_STOP));
    }

    // @Override
    // public void initActivity(Activity activity)
    // {
    // //In our case the service must always be started, so start it even if not
    // started already!
    // Log.d(TAG, "initActivity",Log.DEBUG_MODE);
    // super.initActivity(activity);
    // startService();
    // }

    // we override ServiceProxy methods to make sure we call our static methods
    // in all cases.

    private boolean mEnableLockscreenControls = true;

    private List<Object> addItemToPlaylist(Object item) {
        if (mPlaylist == null) {
            mPlaylist = new ArrayList<Object>();
        }
        if (item instanceof Object[]) {
            List<Object> result = new ArrayList<Object>();
            Object[] array = (Object[]) item;
            for (int i = 0; i < array.length; i++) {
                result.addAll(addItemToPlaylist(array[i]));
            }
            return result;
        } else {
            mPlaylist.add(item);
            return Arrays.asList(new Object[] { item });
        }
    }

    private List<Object> removeItemsFromPlaylist(Object item) {
        if (mPlaylist != null) {
            if (item instanceof Object[]) {
                List<Object> result = new ArrayList<Object>();
                Object[] array = (Object[]) item;
                for (int i = 0; i < array.length; i++) {
                    result.addAll(removeItemsFromPlaylist(array[i]));
                }
                return result;
            } else {
                if (mPlaylist.remove(item)) {
                    return Arrays.asList(new Object[] { item });
                }
            }
        }

        return null;
    }

    public List<Object> getPlayList() {
        return mPlaylist;
    }

    @Override
    public void handleCreationDict(KrollDict options) {
        super.handleCreationDict(options);
        if (options.containsKey(TiC.PROPERTY_PLAYLIST)) {
            addItemToPlaylist(options.get(TiC.PROPERTY_PLAYLIST));
        }
        // } else if (options.containsKey(TiC.PROPERTY_SOUND)) {
        // FileProxy fp = (FileProxy) options.get(TiC.PROPERTY_SOUND);
        // if (fp != null) {
        // String url = fp.getNativePath();
        // setProperty(TiC.PROPERTY_URL, url);
        // }
        // }
        // if (options.containsKey(TiC.PROPERTY_ALLOW_BACKGROUND)) {
        // allowBackground = options.optBoolean(TiC.PROPERTY_ALLOW_BACKGROUND,
        // allowBackground);
        // }
        // Log.i(TAG,
        // "Creating audio player proxy for url: "
        // + TiConvert.toString(getProperty(TiC.PROPERTY_URL)),
        // Log.DEBUG_MODE);
    }

    @Override
    public void onPropertyChanged(String name, Object value, Object oldValue) {
        if (tiService == null)
            return;
        if (TiC.PROPERTY_VOLUME.equals(name)) {
            tiService.setVolume(TiConvert.toFloat(value, 1.0f));
        } else if (TiC.PROPERTY_TIME.equals(name)) {
            tiService.seek(TiConvert.toLong(value));
        }
        if (name.equals(TiC.PROPERTY_METADATA)) {
            tiService.updateMetadata();
        }
    }

    // @Kroll.getProperty
    // @Kroll.method
    // public String getUrl() {
    // return TiConvert.toString(getProperty(TiC.PROPERTY_URL));
    // }
    //
    // @Kroll.setProperty
    // @Kroll.method
    // public void setUrl(String url) {
    // if (url != null) {
    // setProperty(TiC.PROPERTY_URL,
    // resolveUrl(null, TiConvert.toString(url)));
    // }
    // }

    @Kroll.getProperty
    @Kroll.method
    public long getDuration() {
        if (tiService != null) {
            return tiService.duration();
        }
        return 0;
    }

    @Kroll.getProperty
    @Kroll.method
    public boolean isPlaying() {
        if (tiService != null) {
            return tiService.isPlaying();
        }
        return false;
    }

    @Kroll.getProperty
    @Kroll.method
    public boolean isPaused() {
        if (tiService != null) {
            return tiService.isPaused();
        }
        return false;
    }

    @Kroll.getProperty
    @Kroll.method
    public boolean isStopped() {
        if (tiService != null) {
            return tiService.isStopped();
        }
        return true;
    }

    // An alias for play so that
    @Kroll.method
    public void start() {
        runAction(AudioStreamerService.CMDPLAY);
    }

    @Kroll.method
    public void play() {
        runAction(AudioStreamerService.CMDPLAY);
    }

    @Kroll.method
    public void pause() {
        runAction(AudioStreamerService.CMDPAUSE);
    }

    @Kroll.method
    public void stop() {
        runAction(AudioStreamerService.CMDSTOP);
    }

    @Kroll.method
    public void playPause() {
        runAction(AudioStreamerService.CMDTOGGLEPAUSE);
    }

    @Kroll.method
    public void next() {
        runAction(AudioStreamerService.CMDNEXT);
    }

    @Kroll.method
    public void previous() {
        runAction(AudioStreamerService.CMDPREVIOUS);
    }

    @Kroll.method
    public void close() {
        stopService();
    }

    @Kroll.method
    @Kroll.getProperty
    public double getTime() {
        if (tiService != null) {
            long time = tiService.position();
            setProperty(TiC.PROPERTY_TIME, time);
        }
        return TiConvert.toLong(getProperty(TiC.PROPERTY_TIME));
    }

    public int getNotificationIcon() {
        return TiRHelper.getResource(getProperty("notifIcon"), "drawable", false);
    }

    public int getNotificationViewId() {
        return TiRHelper.getResource(getProperty("notifViewId"), "layout", false);
    }

    public int getNotificationExtandedViewId() {
        return TiRHelper.getResource(getProperty("notifExpandedViewId"), "layout", false);
    }

    public boolean getEnableLockscreenControls() {
        return TiConvert.toBoolean(getProperty("enableLockscreenControls"),
                true);
    }

    @Kroll.method
    public void addToPlayList(Object item) {
        List<Object> result = addItemToPlaylist(item);
        if (tiService != null) {
            tiService.addToPlayList(result, Integer.MAX_VALUE);
        }
    }

    @Kroll.method
    public void removeFromPlayList(Object item) {
        List<Object> result = removeItemsFromPlaylist(item);
        if (tiService != null) {
            tiService.removeFromPlayList(result);
        }
    }

    @Override
    public String getApiName() {
        return "Ti.Media.AudioStreamer";
    }
}

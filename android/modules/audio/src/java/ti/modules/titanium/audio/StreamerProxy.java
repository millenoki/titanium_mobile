/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */


package ti.modules.titanium.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiEnhancedServiceProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;

import ti.modules.titanium.audio.streamer.AudioStreamerExoService;
import android.app.Service;
import android.content.Intent;

@Kroll.proxy(creatableInModule = AudioModule.class, propertyAccessors = {
        TiC.PROPERTY_VOLUME, 
        TiC.PROPERTY_METADATA, 
        TiC.PROPERTY_PLAYLIST,
        TiC.PROPERTY_REPEAT_MODE,
        TiC.PROPERTY_SHUFFLE_MODE,
        })
public class StreamerProxy extends TiEnhancedServiceProxy {
    private static final String TAG = "StreamerProxy";

    AudioStreamerExoService tiService = null;
    List<Object> mPlaylist;
    private boolean needsStopOnBind = false;

    public StreamerProxy() {
        super(null, null, null);
        if (isMyServiceRunning()) {
            startService();
        }
    }

    /**
     * For when a service started via startService() creates a proxy when it
     * starts running
     */
    public StreamerProxy(Service service, Intent intent,
            Integer serviceInstanceId) {
        super(service, intent, serviceInstanceId);
        defaultValues.put(TiC.PROPERTY_VOLUME, 1.0f);
        defaultValues.put(TiC.PROPERTY_TIME, 0);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Class serviceClass() {
        return AudioStreamerExoService.class;
    }

    @Override
    public void unbindService() {
        super.unbindService();
        this.tiService = null;
    }

    protected void invokeBoundService() {
        this.tiService = (AudioStreamerExoService) this.service;
        if (needsStopOnBind) {
            needsStopOnBind = false;
            this.tiService.reset();
        }
        super.invokeBoundService();
    }

    private void runAction(String cmd) {
        Intent intent = new Intent(getIntent().getIntent());
        // intent.setAction(action);
        intent.putExtra(AudioStreamerExoService.CMDNAME, cmd);
        startService(intent);
        // else if (target == mPauseButton)
        // startService(new Intent(AudioStreamerExoService.ACTION_PAUSE));
        // else if (target == mSkipButton)
        // startService(new Intent(AudioStreamerExoService.ACTION_SKIP));
        // else if (target == mRewindButton)
        // startService(new Intent(AudioStreamerExoService.ACTION_REWIND));
        // else if (target == mStopButton)
        // startService(new Intent(AudioStreamerExoService.ACTION_STOP));
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
        if (name.equals(TiC.PROPERTY_PLAYLIST)) {
            mPlaylist = null;
            addItemToPlaylist(value);
            if (tiService != null) {
                tiService.setPlaylist(mPlaylist);
            }
            else {
                needsStopOnBind = true;
            }
        } else if (TiC.PROPERTY_VOLUME.equals(name)) {
            if (tiService != null) {
                tiService.setVolume(TiConvert.toFloat(value, 1.0f));
            }
        } else if (TiC.PROPERTY_TIME.equals(name)) {
            if (tiService != null) {
                tiService.seek(TiConvert.toLong(value));
            }
        } else if (name.equals(TiC.PROPERTY_METADATA)) {
            if (tiService != null) {
                tiService.updateMetadata();
            }
        } else if (TiC.PROPERTY_REPEAT_MODE.equals(name)) {
            if (tiService != null) {
                tiService.setRepeatMode(TiConvert.toInt(value, tiService.getRepeatMode()));
            }
        } else if (TiC.PROPERTY_SHUFFLE_MODE.equals(name)) {
            if (tiService != null) {
                tiService.setShuffleMode(TiConvert.toInt(value, tiService.getShuffleMode()));
            }
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
        runAction(AudioStreamerExoService.CMDPLAY);
    }

    @Kroll.method
    public void play() {
        runAction(AudioStreamerExoService.CMDPLAY);
    }

    @Kroll.method
    public void pause() {
        runAction(AudioStreamerExoService.CMDPAUSE);
    }

    @Kroll.method
    public void stop() {
        runAction(AudioStreamerExoService.CMDSTOP);
    }

    @Kroll.method
    public void playPause() {
        runAction(AudioStreamerExoService.CMDTOGGLEPAUSE);
    }

    @Kroll.method
    public void next() {
        runAction(AudioStreamerExoService.CMDNEXT);
    }

    @Kroll.method
    public void previous() {
        runAction(AudioStreamerExoService.CMDPREVIOUS);
    }
    
    @Kroll.method
    public void seek(long time) {
        if (tiService != null) {
            tiService.seek(time);
        }
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
            return time;
        }
        return TiConvert.toLong(getProperty(TiC.PROPERTY_TIME));
    }
    
    @Kroll.method
    @Kroll.getProperty
    public int getState() {
        if (tiService != null) {
            int state = tiService.state();
            setProperty(TiC.PROPERTY_STATE, state);
            return state;
        }
        return TiConvert.toInt(getProperty(TiC.PROPERTY_TIME));
    }
    
    @Kroll.method
    @Kroll.getProperty
    public String getStateDescription() {
        if (tiService != null) {
            String description = tiService.stateDescription();
            return description;
        }
        return AudioStreamerExoService.STATE_STOPPED_DESC;
    }
    
    @Kroll.method
    @Kroll.getProperty
    public Object getCurrentItem() {
        if (tiService != null) {
            return tiService.getCurrent();
        }
        return null;
    }
    
    @Kroll.method
    @Kroll.getProperty
    public Object[] getPlaylist() {
        if (tiService != null) {
            return tiService.getPlaylist();
        }
        return mPlaylist.toArray();
    }
    
    @Kroll.method
    @Kroll.getProperty
    public Object[] getQueue() {
        if (tiService != null) {
            return tiService.getQueue();
        }
        return null;
    }
    
    
    @Kroll.method
    @Kroll.getProperty
    public int getIndex() {
        if (tiService != null) {
            return tiService.getQueuePosition();
        }
        return -1;
    }


    public int getNotificationIcon() {
        return TiRHelper.getResource(getProperty("notifIcon"), "drawable",
                false);
    }

    public int getNotificationViewId() {
        return TiRHelper.getResource(getProperty("notifViewId"), "layout",
                false);
    }

    public int getNotificationExtandedViewId() {
        return TiRHelper.getResource(getProperty("notifExpandedViewId"),
                "layout", false);
    }

    public boolean getEnableLockscreenControls() {
        return TiConvert.toBoolean(getProperty("enableLockscreenControls"),
                true);
    }

    @Kroll.method
    public void addToPlaylist(Object item) {
        List<Object> result = addItemToPlaylist(item);
        if (tiService != null) {
            tiService.addToPlayList(result, Integer.MAX_VALUE);
        }
    }

    @Kroll.method
    public void removeFromPlaylist(Object item) {
        List<Object> result = removeItemsFromPlaylist(item);
        if (tiService != null) {
            tiService.removeFromPlayList(result);
        }
    }
    
    public List<Object> getInternalPlaylist() {
        return mPlaylist;
    }

    @Override
    public String getApiName() {
        return "Ti.Audio.Streamer";
    }
}

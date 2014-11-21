/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.lang.ref.SoftReference;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import android.content.Context;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.SeekBar;

public class TiUIVolumeView extends TiUIView
    implements SeekBar.OnSeekBarChangeListener
{
    private static final String TAG = "TiUIVolumeView";

    private int min;
    private int max;
    private int volumeStream = AudioManager.STREAM_MUSIC;
    private boolean initialized = false;
    
    private AudioManager mAudioManager = null; 

    private SoftReference<Drawable> thumbDrawable;

    public TiUIVolumeView(final TiViewProxy proxy)
    {
        super(proxy);
        Log.d(TAG, "Creating a seekBar", Log.DEBUG_MODE);

        layoutParams.autoFillsWidth = true;
        
        mAudioManager = (AudioManager) TiApplication.getAppSystemService(Context.AUDIO_SERVICE);

        SeekBar seekBar = new SeekBar(proxy.getActivity())
        {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom)
            {
                super.onLayout(changed, left, top, right, bottom);
                TiUIHelper.firePostLayoutEvent(TiUIVolumeView.this);
            }
            
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (touchPassThrough == true)
                    return false;
                return super.dispatchTouchEvent(event);
            }
        };
        

        seekBar.setOnSeekBarChangeListener(this);
        setNativeView(seekBar);
    }

    @Override
    public void processProperties(KrollDict d)
    {
        super.processProperties(d);

        SeekBar seekBar = (SeekBar) getNativeView();
        if (d.containsKey("stream")) {
            volumeStream = TiConvert.toInt(d, "stream", volumeStream);
        }
        if (d.containsKey("thumbImage")) {
            updateThumb(seekBar, d);
        }
        
        if (d.containsKey("leftTrackImage") && d.containsKey("rightTrackImage")) {
            updateTrackingImages(seekBar, d);
        }
        updateControl();
        initialized = true;
    }
    
    private void updateControl() {
        SeekBar seekBar = (SeekBar) getNativeView();
        this.max = mAudioManager.getStreamMaxVolume(volumeStream);
        int current = mAudioManager
                .getStreamVolume(volumeStream);
        seekBar.setMax(this.max);
        seekBar.setProgress(current);
    }

    private void updateThumb(SeekBar seekBar, KrollDict d) 
    {
        TiFileHelper tfh = null;
        String thumbImage = TiConvert.toString(d, "thumbImage");
        if (thumbImage != null) {
            if (tfh == null) {
                tfh = new TiFileHelper(seekBar.getContext());
            }
            String url = proxy.resolveUrl(null, thumbImage);
            Drawable thumb = tfh.loadDrawable(url, false);
            if (thumb != null) {
                thumbDrawable = new SoftReference<Drawable>(thumb);
                seekBar.setThumb(thumb);
            } else {
                Log.e(TAG, "Unable to locate thumb image for progress bar: " + url);
            }
        } else {
            seekBar.setThumb(null);
        }
    }
    
    private void updateTrackingImages(SeekBar seekBar, KrollDict d) 
    {
        TiFileHelper tfh = null;
        String leftImage =  TiConvert.toString(d, "leftTrackImage");
        String rightImage = TiConvert.toString(d, "rightTrackImage");
        if (leftImage != null && rightImage != null) {
            if (tfh == null) {
                tfh = new TiFileHelper(seekBar.getContext());
            }
            String leftUrl = proxy.resolveUrl(null, leftImage);
            String rightUrl = proxy.resolveUrl(null, rightImage);

            Drawable rightDrawable = tfh.loadDrawable(rightUrl, false, true);
            Drawable leftDrawable = tfh.loadDrawable(leftUrl, false, true);
            if (rightDrawable != null && leftDrawable != null) {
                Drawable[] lda = {
                    rightDrawable,
                    new ClipDrawable(leftDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
                };
                LayerDrawable ld = new LayerDrawable(lda);
                ld.setId(0, android.R.id.background);
                ld.setId(1, android.R.id.progress);
                seekBar.setProgressDrawable(ld);
            } else {
                if (leftDrawable == null) {
                    Log.e(TAG, "Unable to locate left image for progress bar: " + leftUrl);
                }
                if (rightDrawable == null) {
                    Log.e(TAG, "Unable to locate right image for progress bar: " + rightUrl);
                }
                // release
                leftDrawable = null;
                rightDrawable = null;
            }
        } else if (leftImage == null && rightImage == null) {
            seekBar.setProgressDrawable(null);
        } else {
            Log.w(TAG, "Custom tracking images must both be set before they will be drawn.");
        }
    }
    
    @Override
    public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
    {
        if (Log.isDebugModeEnabled()) {
            Log.d(TAG, "Property: " + key + " old: " + oldValue + " new: " + newValue, Log.DEBUG_MODE);
        }
        SeekBar seekBar = (SeekBar) getNativeView();
        if (key.equals("stream")) {
            volumeStream = TiConvert.toInt(newValue, volumeStream);
            updateControl();
        } else if (key.equals("thumbImage")) {
            //updateThumb(seekBar, proxy.getDynamicProperties());
            //seekBar.invalidate();
            Log.i(TAG, "Dynamically changing thumbImage is not yet supported. Native control doesn't draw");
        } else if (key.equals("leftTrackImage") || key.equals("rightTrackImage")) {
            //updateTrackingImages(seekBar, proxy.getDynamicProperties());
            //seekBar.invalidate();
            Log.i(TAG, "Dynamically changing leftTrackImage or rightTrackImage is not yet supported. Native control doesn't draw");
        } else {
            super.propertyChanged(key, oldValue, newValue, proxy);
        }
    }
    
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!initialized) return;
        mAudioManager.setStreamVolume(volumeStream, progress, 0);
        if (!hasListeners(TiC.EVENT_CHANGE, false)) {
            return;
        }
        KrollDict data = new KrollDict();
        data.put(TiC.PROPERTY_VALUE, progress);
        data.put(TiC.PROPERTY_MAX, this.max);
        fireEvent(TiC.EVENT_CHANGE, data, false, false);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (hasListeners(TiC.EVENT_START, false)){
            KrollDict data = new KrollDict();
            data.put(TiC.PROPERTY_VALUE, value());
            data.put(TiC.PROPERTY_MAX, this.max);
            fireEvent(TiC.EVENT_START, data, false, false);
        }
    }
    
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (hasListeners(TiC.EVENT_STOP, false)){
            KrollDict data = new KrollDict();
            data.put(TiC.PROPERTY_VALUE, value());
            data.put(TiC.PROPERTY_MAX, this.max);
            fireEvent(TiC.EVENT_STOP, data, false, false);
        }
    }

    private float value() {
        return ((SeekBar) getNativeView()).getProgress();
    }

}

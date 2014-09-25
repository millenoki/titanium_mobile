/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import android.annotation.SuppressLint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;

@SuppressLint("NewApi")
public class TiImageLruCache
{
    static final String TAG = "TiImageLruCache";
    private LruCache<Integer, RecyclingBitmapDrawable> mMemoryCache;

    private static TiImageLruCache instance;
    
    private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory());

    // Use 1/8th of the available memory for this memory cache.
    private static final int cacheSize = maxMemory / 8;

    public static TiImageLruCache getInstance() {
        if(instance == null) {
            instance = new TiImageLruCache();
            instance.init();
        } 

        return instance;
    }

    private void init() {

        // We are declaring a cache of 6Mb for our use.
        // You need to calculate this on the basis of your need 
        mMemoryCache = new LruCache<Integer, RecyclingBitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, RecyclingBitmapDrawable bitmapDrawable) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    return bitmapDrawable.getBitmap().getByteCount() ;
                } else {
                    return bitmapDrawable.getBitmap().getRowBytes() * bitmapDrawable.getBitmap().getHeight();
                }
            }

            @Override
            protected void entryRemoved(boolean evicted, Integer key, RecyclingBitmapDrawable oldValue, RecyclingBitmapDrawable newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                oldValue.setIsCached(false);
            }
        };

    }

    public void addBitmapToMemoryCache(Integer key, RecyclingBitmapDrawable bitmapDrawable) {
        if (getBitmapFromMemCache(key) == null) {
            // The removed entry is a recycling drawable, so notify it
            // that it has been added into the memory cache
            bitmapDrawable.setIsCached(true);
            mMemoryCache.put(key, bitmapDrawable);
        }
    }
    
    public Drawable addDrawableToMemoryCache(Integer key, Drawable bitmapDrawable) {
        if (!(bitmapDrawable instanceof BitmapDrawable)) return bitmapDrawable;
        RecyclingBitmapDrawable result = getBitmapFromMemCache(key);
        if (result == null) {
            if (bitmapDrawable instanceof RecyclingBitmapDrawable) {
                result = (RecyclingBitmapDrawable) bitmapDrawable;
            }
            else {
                result = new RecyclingBitmapDrawable((BitmapDrawable) bitmapDrawable);
            }
            // The removed entry is a recycling drawable, so notify it
            // that it has been added into the memory cache
            result.setIsCached(true);
            mMemoryCache.put(key, result);
        }
        return result;
    }

    public RecyclingBitmapDrawable getBitmapFromMemCache(Integer key) {
        return mMemoryCache.get(key);
    }
    
    public void removeBitmapFromMemCache(Integer key) {
        mMemoryCache.remove(key);
    }

    public void clear() {
        Log.d(TAG, "clear");
        mMemoryCache.evictAll();
    }
}

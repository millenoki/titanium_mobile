package org.appcelerator.titanium;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.appcelerator.kroll.common.Log;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;

public class TiBitmapRecycleHandler {
    private static final String TAG = "TiBitmapRecycleHandler";
    protected static HashMap<Bitmap, Integer> bitmapUsers = null;
    protected static Set<SoftReference<Bitmap>> mReusableBitmaps = Collections
            .synchronizedSet(new HashSet<SoftReference<Bitmap>>());;

    public static void addBitmapUser(final Bitmap bitmap) {
        if (bitmapUsers == null) {
            bitmapUsers = new HashMap<Bitmap, Integer>();
        }
        Integer current = bitmapUsers.get(bitmap);
        if (current == null) {
            current = 1;
        } else {
            current += 1;
        }
        bitmapUsers.put(bitmap, current);
    }

    public static void removeBitmapUser(final Bitmap bitmap) {
        Integer current = bitmapUsers.get(bitmap);
        if (current != null) {
            current -= 1;
            if (current == 0) {
                bitmapUsers.remove(bitmap);
//                bitmap.recycle();
//                java.lang.System.gc();

                // We're running on Honeycomb or later, so add the bitmap
                // to a SoftReference set for possible use with inBitmap later.
                Log.d(TAG, "setting bitmap to reuse for size " + bitmap.getWidth() + "x" + bitmap.getHeight());
                mReusableBitmaps.add(new SoftReference<Bitmap>(bitmap));
            } else {
                bitmapUsers.put(bitmap, current);
            }
        }
    }

    // This method iterates through the reusable bitmaps, looking for one
    // to use for inBitmap:
    protected static Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
        Bitmap bitmap = null;

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized (mReusableBitmaps) {
                final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps
                        .iterator();
                Bitmap item;

                while (iterator.hasNext()) {
                    item = iterator.next().get();

                    if (null != item && item.isMutable()) {
                        // Check to see it the item can be used for inBitmap.
                        if (canUseForInBitmap(item, options)) {
                            bitmap = item;
                            Log.d(TAG, "reusing bitmap of size " + bitmap.getWidth() + "x" + bitmap.getHeight());

                            // Remove from reusable set so it can't be used
                            // again.
                            iterator.remove();
                            break;
                        }
                    } else {
                        // Remove from the set if the reference has been
                        // cleared.
                        iterator.remove();
                    }
                }
            }
        }
        return bitmap;
    }
    
    public static void addInBitmapOptions(BitmapFactory.Options options) {
        // inBitmap only works with mutable bitmaps, so force the decoder to
        // return mutable bitmaps.
        options.inMutable = true;

        // Try to find a bitmap to use for inBitmap.
        Bitmap inBitmap = getBitmapFromReusableSet(options);

        if (inBitmap != null) {
            // If a suitable bitmap has been found, set it as the value of
            // inBitmap.
            options.inBitmap = inBitmap;
        }
    }

    static boolean canUseForInBitmap(Bitmap candidate,
            BitmapFactory.Options targetOptions) {

        if (TiC.KIT_KAT_OR_GREATER) {
            // From Android 4.4 (KitKat) onward we can re-use if the byte size
            // of
            // the new bitmap is smaller than the reusable bitmap candidate
            // allocation byte count.
            int width = targetOptions.outWidth / targetOptions.inSampleSize;
            int height = targetOptions.outHeight / targetOptions.inSampleSize;
            int byteCount = width * height
                    * getBytesPerPixel(candidate.getConfig());
            return byteCount <= candidate.getAllocationByteCount();
        }

        // On earlier versions, the dimensions must match exactly and the
        // inSampleSize must be 1
        return candidate.getWidth() == targetOptions.outWidth
                && candidate.getHeight() == targetOptions.outHeight
                && targetOptions.inSampleSize == 1;
    }

    /**
     * A helper function to return the byte usage per pixel of a bitmap based on
     * its configuration.
     */
    static int getBytesPerPixel(Config config) {
        if (config == Config.ARGB_8888) {
            return 4;
        } else if (config == Config.RGB_565) {
            return 2;
        } else if (config == Config.ARGB_4444) {
            return 2;
        } else if (config == Config.ALPHA_8) {
            return 1;
        }
        return 1;
    }
}

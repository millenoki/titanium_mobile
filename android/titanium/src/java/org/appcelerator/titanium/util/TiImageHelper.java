/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.util;

import java.util.Arrays;
import java.util.HashMap;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImageBoxBlurFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.GPUImageGaussianBlurFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageiOSBlurFilter;
import jp.co.cyberagent.android.gpuimage.GPUImage.ScaleType;
import jp.co.cyberagent.android.gpuimage.Rotation;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.michaelevans.colorart.library.ColorArt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PorterDuff.Mode;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.util.Pair;

/**
 * Utility class for image manipulations.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class TiImageHelper
{
	private static final String TAG = "TiImageHelper";
	private static GPUImage mGPUImage;
	
	public enum FilterType {
		kFilterBoxBlur, kFilterGaussianBlur, kFilteriOSBlur
	}
	private static GPUImage getGPUImage()
	{
		if (mGPUImage == null) {
			mGPUImage = new GPUImage(TiApplication.getInstance().getBaseContext());
		}
		return mGPUImage;
	}
	/**
	 * Add an alpha channel to the given image if it does not already have one.
	 * 
	 * @param image
	 *            the image to add an alpha channel to.
	 * @return a copy of the given image with an alpha channel. If the image already have the alpha channel, return the
	 *         image itself.
	 */
	public static Bitmap imageWithAlpha(Bitmap image)
	{
		if (image == null) {
			return null;
		}
		if (image.hasAlpha()) {
			return image;
		}
		return image.copy(Bitmap.Config.ARGB_8888, true);
	}

	/**
	 * Create a copy of the given image with rounded corners and a transparent border around its edges.
	 * 
	 * @param image
	 *            the image to add rounded corners to.
	 * @param cornerRadius
	 *            the radius of the rounded corners.
	 * @param borderSize
	 *            the size of the border to be added.
	 * @return a copy of the given image with rounded corners and a transparent border. If the cornerRadius <= 0 or
	 *         borderSize < 0, return the image itself.
	 */
	public static Bitmap imageWithRoundedCorner(Bitmap image, float cornerRadius, float borderSize)
	{
		if (image == null) {
			return null;
		}
		if (cornerRadius <= 0 || borderSize < 0) {
			Log.w(TAG, "Unable to add rounded corners. Invalid corner radius or borderSize for imageWithRoundedCorner");
			return image;
		}

		int width = image.getWidth();
		int height = image.getHeight();
		Bitmap imageRoundedCorner = Bitmap.createBitmap(width + (int) (borderSize * 2), height + (int) (borderSize * 2),
			Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(imageRoundedCorner);

		Path clipPath = new Path();
		RectF imgRect = new RectF(borderSize, borderSize, width + borderSize, height + borderSize);

		float radii[] = new float[8];
		Arrays.fill(radii, cornerRadius);
		clipPath.addRoundRect(imgRect, radii, Direction.CW);

		// This still happens sometimes when hw accelerated so, catch and warn
		try {
			canvas.clipPath(clipPath);
		} catch (Exception e) {
			Log.e(TAG, "Unable to create the image with rounded corners. clipPath failed on canvas: " + e.getMessage());
			canvas.clipRect(imgRect);
		}

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		paint.setDither(true);
		canvas.drawBitmap(imageWithAlpha(image), borderSize, borderSize, paint);
		return imageRoundedCorner;
	}

	/**
	 * Add a transparent border to the given image around its edges.
	 * 
	 * @param image
	 *            the image to add a transparent border to.
	 * @param borderSize
	 *            the size of the border to be added.
	 * @return a copy of the given image with a transparent border. If the borderSize <= 0, return the image itself.
	 */
	public static Bitmap imageWithTransparentBorder(Bitmap image, int borderSize)
	{
		if (image == null) {
			return null;
		}
		if (borderSize <= 0) {
			Log.w(TAG, "Unable to add a transparent border. Invalid border size for imageWithTransparentBorder.");
			return image;
		}

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		paint.setDither(true);

		int width = image.getWidth();
		int height = image.getHeight();
		Bitmap imageBorder = Bitmap.createBitmap(width + borderSize * 2, height + borderSize * 2, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(imageBorder);
		canvas.drawBitmap(imageWithAlpha(image), borderSize, borderSize, paint);
		return imageBorder;
	}
	
	private static Bitmap getFilteredBitmap(Bitmap bitmap, FilterType filterType, HashMap options) {
	    GPUImageFilter filter = getFilter(filterType, options);
	    if (filter != null) {
            return getGPUImage().getBitmapWithFilterApplied(bitmap, 
                    filter, 
                    ScaleType.CENTER_CROP, 
                    Rotation.NORMAL);
	    }
	    else {
	        return bitmap;
	    }
	}
	
	private static GPUImageFilter getFilter(FilterType filterType, HashMap options) {
        GPUImageFilter filter = null;
        switch (filterType) {
            case kFilterBoxBlur:
            {
                float radius = 1.0f;
                if (options != null) {
                    radius = TiConvert.toFloat(options.get("radius"), radius);
                }
                filter = new GPUImageBoxBlurFilter(radius);
                break;
            }
            case kFilterGaussianBlur:
            {
                float radius = 1.0f;
                if (options != null) {
                    radius = TiConvert.toFloat(options.get("radius"), radius);
                }
                filter = new GPUImageGaussianBlurFilter(radius);
                break;
            }
            case kFilteriOSBlur:
            {
                filter = new GPUImageiOSBlurFilter();
                if (options != null) {
                    if (options.containsKey("radius")) {
                        ((GPUImageiOSBlurFilter)filter).setBlurRadiusInPixels(TiConvert.toFloat(options, "radius"));
                    }
                    if (options.containsKey("saturation")) {
                        ((GPUImageiOSBlurFilter)filter).setSaturation(TiConvert.toFloat(options, "saturation"));
                    }
                    if (options.containsKey("downsampling")) {
                        ((GPUImageiOSBlurFilter)filter).setDownSampling(TiConvert.toFloat(options, "downsampling"));
                    }
                }
                break;
            }
        }
        return filter;
    }
	
	public static Bitmap imageTinted(Bitmap bitmap, int tint, Mode mode) {
		if (tint != 0) {
			Canvas canvas = new Canvas(bitmap);
			canvas.drawColor(tint, mode);
		}
		return bitmap;
	}
	
	public static Bitmap imageCropped(Bitmap bitmap, TiRect rect) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		RectF realRect = rect.getAsPixels(width, height);
		try {
			bitmap = Bitmap.createBitmap(bitmap, (int)realRect.left, (int)realRect.top, (int)realRect.width(), (int)realRect.height());
		} catch (Exception e) {
			Log.e(TAG, "Unable to crop the image. Not enough memory: " + e.getMessage(), e);
			return bitmap;
		}
		return bitmap;
	}
	
	public static Bitmap imageScaled(Bitmap bitmap, float scale) {
		if (scale != 1.0f) {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int dstWidth = (int) (width * scale);
			int dstHeight = (int) (height * scale);
			try {
			    
				bitmap = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true);
				
			} catch (OutOfMemoryError e) {
				Log.e(TAG, "Unable to resize the image. Not enough memory: " + e.getMessage(), e);
			}
		}
		return bitmap;
	}
	
	public static Bitmap imageFiltered(Bitmap bitmap, FilterType filterType, @Kroll.argument(optional=true) HashMap options) {	
		if (options.containsKey("scale")) {
			float scale = TiConvert.toFloat(options, "scale", 1.0f);
			bitmap = TiImageHelper.imageScaled(bitmap, scale);
		}
		
		return getFilteredBitmap(bitmap, filterType, options);
	}
	
	public static Pair<Bitmap, KrollDict> imageFiltered(Bitmap bitmap, HashMap options, final boolean shouldCopySource) {
	    if (bitmap == null) {
            return null;
        }
	    if (shouldCopySource) {
	        bitmap = bitmap.copy (Bitmap.Config.ARGB_8888,true);
	    }
	    KrollDict infoData = new KrollDict();
		if (options.containsKey("crop")) {
			TiRect rect = new TiRect(options.get("crop"));
            Bitmap oldBitmap  = bitmap;
			bitmap = TiImageHelper.imageCropped(bitmap, rect);
            bitmap = bitmap.copy (Bitmap.Config.ARGB_8888,true);
            oldBitmap.recycle();
            oldBitmap = null;
		}

        if (options.containsKey("scale")) {
            float scale = TiConvert.toFloat(options, "scale", 1.0f);
            Bitmap oldBitmap  = bitmap;
            bitmap = TiImageHelper.imageScaled(bitmap, scale);
            bitmap = bitmap.copy (Bitmap.Config.ARGB_8888,true);
            oldBitmap.recycle();
            oldBitmap = null;
        }

		if (options.containsKey("filters")) {
		    GPUImageFilterGroup group = new GPUImageFilterGroup();
			Object[] filters = (Object[]) options.get("filters");
			for (int i = 0; i < filters.length; i++) {
			    if (filters[i] instanceof HashMap) {
			        HashMap<String, Object> filterOptions = (HashMap<String, Object>) filters[i];
			        GPUImageFilter filter =  getFilter(FilterType.values()[TiConvert.toInt(filterOptions, "type")], filterOptions);
			        if (filter != null) {
			            group.addFilter(filter);
			        }
			    }
			}
            Bitmap oldBitmap  = bitmap;
			bitmap = getGPUImage().getBitmapWithFilterApplied(bitmap, 
			        group, 
                    ScaleType.CENTER_CROP, 
                    Rotation.NORMAL);
            bitmap = bitmap.copy (Bitmap.Config.ARGB_8888,true);
            oldBitmap.recycle();
            oldBitmap = null;
		}
		
		if (options.containsKey("tint")) {
			int tint = TiConvert.toColor(options, "tint", 0);
			Mode mode = Mode.values()[TiConvert.toInt(options, "blend", Mode.LIGHTEN.ordinal())];
            Bitmap oldBitmap  = bitmap;
			bitmap = TiImageHelper.imageTinted(bitmap, tint, mode);
			bitmap = bitmap.copy (Bitmap.Config.ARGB_8888,true);
            oldBitmap.recycle();
            oldBitmap = null;
		}
		
		if (options.containsKey("colorArt")) {
            int width = 120;
            int height = 120;
            Object colorArtOptions = options.get("colorArt");
            if (colorArtOptions instanceof HashMap) {
                width = TiConvert.toInt((HashMap)colorArtOptions, "width", width);
                height = TiConvert.toInt((HashMap)colorArtOptions, "height", height);
            }
            ColorArt art = new ColorArt(bitmap, width, height);
            KrollDict colorArtData = new KrollDict();
            colorArtData.put("backgroundColor", TiColorHelper.toHexString(art.getBackgroundColor()));
            colorArtData.put("primaryColor", TiColorHelper.toHexString(art.getPrimaryColor()));
            colorArtData.put("secondaryColor", TiColorHelper.toHexString(art.getSecondaryColor()));
            colorArtData.put("detailColor", TiColorHelper.toHexString(art.getDetailColor()));
            infoData.put("colorArt", colorArtData);
        }
		
		return new Pair<Bitmap, KrollDict>(bitmap, infoData);
	}
	
	private static final String FILE_PREFIX = "file://";
	
	/**
	 * Find the orientation of the image.
	 * @param file image file
	 * @return return the orientation in degrees, -1 for error
	 */
	public static int getOrientation(String path) {
		int orientation = 0;
		try {
			if (path == null) {
				Log.e(TAG,
					"Path of image file could not determined. Could not create an exifInterface from an invalid path.");
				return 0;
			}
			// Remove path prefix
			if (path.startsWith(FILE_PREFIX)) {
				path = path.replaceFirst(FILE_PREFIX, "");
			}
			
			ExifInterface ei = new ExifInterface(path);
			int orientationConst = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
			switch (orientationConst) {
				case ExifInterface.ORIENTATION_ROTATE_270:
					orientation = 270;
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					orientation = 180;
					break;
				case ExifInterface.ORIENTATION_ROTATE_90:
					orientation = 90;
					break;
			}
		} catch (Exception e) {
			Log.e(TAG, "Unable to find orientation " + e.getMessage());
		}
		return orientation;
	}
	
	/**
	 * Rotate the image
	 * @param bm source bitmap
	 * @param rotation degree of rotation
	 * @return return the rotated bitmap
	 */
	public static Bitmap rotateImage(Bitmap bm, int rotation) {
		Matrix matrix = new Matrix();
	    matrix.postRotate(rotation);
	    return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
	}
}

package org.appcelerator.titanium.util;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;

import android.view.MotionEvent;
import android.view.View;


public class TiViewHelper {

    private static int getWidthForView(View view) {
        int width = view.getWidth();
        if (width == 0) { // a cheat for NavigationWindowProxy where animation
                          // will start before layout
            View parent = (View) view.getParent();
            if (parent != null) {
                width = parent.getWidth();
            }
        }
        return width;
    }

    private static int getHeightForView(View view) {
        int height = view.getHeight();
        if (height == 0) { // a cheat for NavigationWindowProxy where animation
                           // will start before layout
            View parent = (View) view.getParent();
            if (parent != null) {
                height = parent.getHeight();
            }
        }
        return height;
    }

    public static void setTranslationRelativeX(View view, float val) {
        view.setTranslationX(getWidthForView(view) * val);

    }

    public static float getTranslationRelativeX(View view) {
        return (view.getTranslationX() / getWidthForView(view));
    }

    public static void setTranslationRelativeY(View view, float val) {
        view.setTranslationY(getHeightForView(view) * val);
    }

    public static float getTranslationRelativeY(View view) {
        return (view.getTranslationY() / getHeightForView(view));
    }

    public static void setPivotFloatX(View view, float val) {
        view.setPivotX(getWidthForView(view) * val);
    }

    public static void setPivotFloat(View view, float valx, float valy) {
        view.setPivotX(getWidthForView(view) * valx);
        view.setPivotY(getHeightForView(view) * valy);
    }

    public static float getPivotFloatX(View view) {
        return (view.getPivotX() / getWidthForView(view));
    }

    public static void setPivotFloatY(View view, float val) {
        view.setPivotX(getHeightForView(view) * val);
    }

    public static float getPivotFloatY(View view) {
        return (view.getPivotY() / getHeightForView(view));
    }

    public static void setScale(View view, float valx, float valy) {
        view.setScaleX(valx);
        view.setScaleY(valy);
    }

    public static void setScale(View view, float val) {
        view.setScaleX(val);
        view.setScaleY(val);
    }

    public static void resetValues(View view) {

        setPivotFloat(view, 0.5f, 0.5f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setRotation(0.0f);
        view.setRotationX(0.0f);
        view.setRotationY(0.0f);
        view.setAlpha(1.0f);
    }

    public static KrollDict dictFromMotionEvent(final View view, final MotionEvent e) {
        KrollDict data = new KrollDict();
        if (e != null) {
            double density = TiApplication.getAppDensity();
            int[] coords = new int[2];
            view.getLocationInWindow(coords);

            final double rawx = e.getRawX() / density;
            final double rawy = e.getRawY() / density;
            final double x = (double) rawx - coords[0] / density;
            final double y = (double) rawy - coords[1] / density;
            data.put(TiC.EVENT_PROPERTY_X, x);
            data.put(TiC.EVENT_PROPERTY_Y, y);
            KrollDict globalPoint = new KrollDict();
            globalPoint.put(TiC.EVENT_PROPERTY_X, rawx);
            globalPoint.put(TiC.EVENT_PROPERTY_Y, rawy);
            data.put(TiC.EVENT_PROPERTY_GLOBALPOINT, globalPoint);
            data.put(TiC.EVENT_PROPERTY_FORCE, (double)e.getPressure());
            data.put(TiC.EVENT_PROPERTY_SIZE, (double)e.getSize());
        }
//        data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
        return data;
    }
    
    public static KrollDict dictFromMotionEventCoords(final View view, final float focusX, final float focusY) {
        KrollDict data = new KrollDict();
//        if (e != null) {
            double density = TiApplication.getAppDensity();
            int[] coords = new int[2];
            view.getLocationInWindow(coords);

            
            final double x = (double) focusX / density;
            final double y = (double) focusY / density;
            final double rawx = x  + coords[0] / density;
            final double rawy = y  + coords[1] / density;
            data.put(TiC.EVENT_PROPERTY_X, x);
            data.put(TiC.EVENT_PROPERTY_Y, y);
            KrollDict globalPoint = new KrollDict();
            globalPoint.put(TiC.EVENT_PROPERTY_X, rawx);
            globalPoint.put(TiC.EVENT_PROPERTY_Y, rawy);
            data.put(TiC.EVENT_PROPERTY_GLOBALPOINT, globalPoint);
//        }
//        data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
        return data;
    }
}

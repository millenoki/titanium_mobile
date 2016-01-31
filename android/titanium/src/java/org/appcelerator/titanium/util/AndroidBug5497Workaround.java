package org.appcelerator.titanium.util;

import java.util.HashMap;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

public class AndroidBug5497Workaround {

    // For more information, see https://code.google.com/p/android/issues/detail?id=5497
    // To use this class, simply invoke assistActivity() on an Activity that already has its content view set.
    private static HashMap sAssisted;
    public static void assistActivity (Activity activity) {
        if (sAssisted == null) {
            sAssisted = new HashMap();
        }
        if (!sAssisted.containsKey(activity)) {
            sAssisted.put(activity, new AndroidBug5497Workaround(activity));
        }
    }
    
    public static void unassistActivity (Activity activity) {
        if (sAssisted == null) {
            return;
        }
        if (sAssisted.containsKey(activity)) {
            ((AndroidBug5497Workaround) sAssisted.remove(activity)).release();
        }
    }

    private View mChildOfContent;
    private int usableHeightPrevious;
    private FrameLayout.LayoutParams frameLayoutParams;
    ViewTreeObserver.OnGlobalLayoutListener listener;

    private AndroidBug5497Workaround(Activity activity) {
        FrameLayout content = (FrameLayout) activity.findViewById(android.R.id.content);
        mChildOfContent = content.getChildAt(0);
        listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                possiblyResizeChildOfContent();
            }
        };
        mChildOfContent.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        frameLayoutParams = (FrameLayout.LayoutParams) mChildOfContent.getLayoutParams();
    }
    
    private void release() {
        if (mChildOfContent != null && listener!= null) {
            mChildOfContent.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private void possiblyResizeChildOfContent() {
        int usableHeightNow = computeUsableHeight();
        if (usableHeightNow != usableHeightPrevious) {
            int usableHeightSansKeyboard = mChildOfContent.getRootView().getHeight();
            int heightDifference = usableHeightSansKeyboard - usableHeightNow;
            if (heightDifference > (usableHeightSansKeyboard/4)) {
                // keyboard probably just became visible
                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference;
            } else {
                // keyboard probably just became hidden
                frameLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            mChildOfContent.requestLayout();
            usableHeightPrevious = usableHeightNow;
        }
    }

    private int computeUsableHeight() {
        Rect r = new Rect();
        mChildOfContent.getWindowVisibleDisplayFrame(r);
        return r.bottom ;
    }

}
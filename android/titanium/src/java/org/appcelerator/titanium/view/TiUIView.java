/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollPropertyChange;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.animation.Ti2DMatrixEvaluator;
import org.appcelerator.titanium.animation.TiAnimatorSet;
import org.appcelerator.titanium.animation.TiViewAnimator;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.AffineTransform.DecomposedType;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiImageHelper;
import org.appcelerator.titanium.util.TiRect;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiUIHelper.Shadow;
import org.appcelerator.titanium.view.TiCompositeLayout.AnimationLayoutParams;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.appcelerator.titanium.TiBlob;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ArgbEvaluator;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.PropertyValuesHolder;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.animation.AnimatorProxy;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;

/**
 * This class is for Titanium View implementations, that correspond with TiViewProxy. 
 * A TiUIView is responsible for creating and maintaining a native Android View instance.
 */
public abstract class TiUIView
	implements KrollProxyReusableListener, OnFocusChangeListener, Handler.Callback
{

	private static final String TAG = "TiUIView";
	

	private static AtomicInteger idGenerator;

	// When distinguishing twofingertap and pinch events, minimum motion (in pixels) 
	// to qualify as a scale event. 
	private static final float SCALE_THRESHOLD = 6.0f;

	public static final int SOFT_KEYBOARD_DEFAULT_ON_FOCUS = 0;
	public static final int SOFT_KEYBOARD_HIDE_ON_FOCUS = 1;
	public static final int SOFT_KEYBOARD_SHOW_ON_FOCUS = 2;

	private static final int MSG_FIRST_ID = 100;
	private static final int MSG_SET_BACKGROUND = MSG_FIRST_ID + 1;
    private static final int MSG_CLEAR_FOCUS = MSG_FIRST_ID + 2;
    private static final int MSG_FOCUS = MSG_FIRST_ID + 3;
    
    // flags for updates requests during properties process
    protected int mProcessUpdateFlags = 0;
    public static final int TIFLAG_NEEDS_LAYOUT               = 0x10000000;
    public static final int TIFLAG_NEEDS_LAYOUT_INFORMPARENT  = 0x20000000;
    public static final int TIFLAG_NEEDS_INVALIDATE           = 0x40000000;
  

	protected View nativeView; // Native View object

	protected TiViewProxy proxy;
	protected TiViewProxy parent;
    protected ArrayList<TiUIView> children = new ArrayList<TiUIView>();

	protected LayoutParams layoutParams;
    protected TiBackgroundDrawable background;
    protected Drawable mForegroundDrawable;
	
    protected boolean preventListViewSelection = false;
    protected boolean touchPassThrough = false;
	protected boolean dispatchPressed = false;
	protected boolean reusing = false;
	
	protected boolean isEnabled = true;
    protected boolean isFocusable = false;
    protected boolean isTouchEnabled = true;

	private boolean clipChildren = true;

	protected MotionEvent lastUpEvent = null;
	protected MotionEvent lastDownEvent = null;

	// In the case of heavy-weight windows, the "nativeView" is null,
	// so this holds a reference to the view which is used for touching,
	// i.e., the view passed to registerForTouch.
	private WeakReference<View> touchView = null;


	private boolean zIndexChanged = false;
	protected TiBorderWrapperView borderView;
	// For twofingertap detection
	private boolean didScale = false;

	//to maintain sync visibility between borderview and view. Default is visible
	private int visibility = View.VISIBLE;
	
	protected GestureDetector detector = null;
	protected ScaleGestureDetector scaleDetector = null;

	protected Handler handler;

	protected boolean exclusiveTouch = false;
	public boolean hardwareAccEnabled = true;
	protected TiTouchDelegate mTouchDelegate;
	private RectF mBorderPadding;
	protected boolean useCustomLayoutParams = false;
	
	protected int focusKeyboardState = TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS;
	/**
	 * Constructs a TiUIView object with the associated proxy.
	 * @param proxy the associated proxy.
	 * @module.api
	 */
	public TiUIView(TiViewProxy proxy)
	{
		if (idGenerator == null) {
			idGenerator = new AtomicInteger(0);
		}
		this.proxy = proxy;
		this.layoutParams = new TiCompositeLayout.LayoutParams();
		handler = new Handler(Looper.getMainLooper(), this);
	}

	/**
	 * Adds a child view into the ViewGroup.
	 * @param child the view to be added.
	 */
	public void add(TiUIView child)
	{
		add(child, -1);
	}
	
	/**
	 * Adds a child view into the ViewGroup in specific position.
	 * @param child the view to be added.
	 * @param position position the view to be added.
	 */
	public void insertAt(TiUIView child, int position)
	{
		add(child, position);
	}

	protected void add(TiUIView child, int childIndex)
	{
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
			    TiUIHelper.removeViewFromSuperView(cv);
			    if (!isEnabled) {
                    child.setEnabled(isEnabled, true);
                }
				View nv = getParentViewForChild();
				if (nv instanceof ViewGroup) {
					if (cv.getParent() == null) {
						if (childIndex != -1) {
							((ViewGroup) nv).addView(cv, childIndex, child.getLayoutParams());
						} else {
							((ViewGroup) nv).addView(cv, child.getLayoutParams());
						}
					}
					if(children.contains(child)) {
						children.remove(child);
					}
					if(childIndex == -1) {
						children.add(child);
					} else {
						children.add(childIndex, child);
					}
					child.parent = proxy;
					TiViewProxy childProxy = child.proxy;
					if (childProxy.hasProperty(TiC.PROPERTY_CLIP_CHILDREN)) {
						boolean value = TiConvert.toBoolean(childProxy.getProperty(TiC.PROPERTY_CLIP_CHILDREN));
						if (value == false) {
							((ViewGroup) nv).setClipChildren(false);
						}
					}
				}
			}
		}
	}

//	private int findChildIndex(TiUIView child)
//	{
//		int idxChild = -1;
//		if (child != null) {
//			View cv = child.getOuterView();
//			if (cv != null) {
//				View nv = getNativeView();
//				if (nv instanceof ViewGroup) {
//					idxChild = ((ViewGroup) nv).indexOfChild(cv);
//
//				}
//			}
//		}
//		return idxChild;
//	}

	/**
	 * Removes the child view from the ViewGroup, if child exists.
	 * @param child the view to be removed.
	 */
	public void remove(TiUIView child)
	{
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
				View nv = getParentViewForChild();
				if (nv instanceof ViewGroup) {
					((ViewGroup) nv).removeView(cv);
					children.remove(child);
					child.parent = null;
				}
			}
		}
	}

	/**
	 * @return list of views added.
	 */
	public List<TiUIView> getChildren()
	{
		return children;
	}

	/**
	 * @return the view proxy.
	 * @module.api
	 */
	public TiViewProxy getProxy()
	{
		return proxy;
	}

	/**
	 * Sets the view proxy.
	 * @param proxy the proxy to set.
	 * @module.api
	 */
	public void setProxy(TiViewProxy proxy)
	{
		this.proxy = proxy;
	}

	public TiViewProxy getParent()
	{
		return parent;
	}

	public void setParent(TiViewProxy parent)
	{
		this.parent = parent;
	}
	
	public void setTouchDelegate(TiTouchDelegate delegate) {
        mTouchDelegate = delegate;
    }

	/**
	 * @return the view's layout params.
	 * @module.api
	 */
	public LayoutParams getLayoutParams()
	{
		return layoutParams;
	}
	
	
	public Context getContext()
	{
		if (nativeView != null)
		{
			return nativeView.getContext();
		}
		return null;
	}
	
	
	//This handler callback is tied to the UI thread.
	public boolean handleMessage(Message msg)
	{
		switch(msg.what) {
			case MSG_SET_BACKGROUND : {
				applyCustomBackground();
				return true;
			}
			case MSG_FOCUS : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleFocus((View) result.getArg());
				result.setResult(null); //Signal added.
				return true;
			}
			case MSG_CLEAR_FOCUS : {
                AsyncResult result = (AsyncResult) msg.obj;
                handleClearFocus((View) result.getArg());
                result.setResult(null); //Signal added.
                return true;
            }
		}
		return false;
	}

	/**
	 * @return the Android native view.
	 * @module.api
	 */
	public View getNativeView()
	{
		return nativeView;
	}

	public View getParentViewForChild()
	{
		return nativeView;
	}
	
	
	protected boolean isClickable(){
		return isTouchEnabled;
	}
	/**
	 * Sets the nativeView to view.
	 * @param view the view to set
	 * @module.api
	 */
	protected void setNativeView(View view)
	{
		if (view != null && view.getId() == View.NO_ID) {
			view.setId(idGenerator.incrementAndGet());
		}
		
		if (this.nativeView == view) {
		    return;
		}
		if (this.nativeView != null) {
		    //if the nativeView already has a parent make sure to add the newOne to it
		    ViewGroup savedParent = null;
	        int savedIndex = 0;
	        if (view != null && this.nativeView.getParent() != null) {
	            ViewParent nativeParent = this.nativeView.getParent();
	            if (nativeParent instanceof ViewGroup && nativeParent != view) {
	                savedParent = (ViewGroup) nativeParent;
	                savedIndex = savedParent.indexOfChild(this.nativeView);
	                savedParent.removeView(this.nativeView);
	            }
	        }
	        if (savedParent != null) {
	            savedParent.addView(view, savedIndex,getLayoutParams());
	        }
		}
		this.nativeView = view;

		doSetClickable();
		getFocusView().setOnFocusChangeListener(this);
		
		
		if (background != null)
		{
//			background.setNativeView(nativeView);
			if (TiApplication.isUIThread()) {
				applyCustomBackground();
			} else {
				handler.sendEmptyMessage(MSG_SET_BACKGROUND);
			}
		}
		nativeView.setTag(this);
		if (borderView != null)
		{
			addBorderView();
		}
		
		if (TiC.HONEYCOMB_OR_GREATER && hardwareAccEnabled == false) {
			disableHWAcceleration(getOuterView());
		}
		applyAccessibilityProperties();
	}

	protected void setLayoutParams(LayoutParams layoutParams)
	{
		this.layoutParams = layoutParams;
	}
	
	public void cleanAnimatedParams(boolean autoreverse)
	{
		if (layoutParams instanceof AnimationLayoutParams) {
			//we remove any animated params...
			layoutParams = new TiCompositeLayout.LayoutParams(autoreverse?((AnimationLayoutParams) layoutParams).oldParams:layoutParams);
			if (getOuterView() != null)
				getOuterView().setLayoutParams(layoutParams);
		}
	}
	
	public void resetAnimatedParams()
	{
		if (layoutParams instanceof AnimationLayoutParams) {
			((AnimationLayoutParams)layoutParams).animationFraction = 0.0f;
		}
	}

	public void listenerAdded(String type, int count, KrollProxy proxy) {
	}

	public void listenerRemoved(String type, int count, KrollProxy proxy){
	}

	public float[] getPreTranslationValue(float[] points)
	{
		View view = getOuterView();
		if (view != null && layoutParams.matrix != null) {
			Matrix m = layoutParams.matrix.getMatrix(view);
			// Get the translation values
			float[] values = new float[9];
			m.getValues(values);
			points[0] = points[0] - values[2];
			points[1] = points[1] - values[5];
		}
		return points;
	}

	public void applyTransform(Object timatrix)
	{
		View view = getOuterView();
		if (view != null && !useCustomLayoutParams) {
			layoutParams.matrix = TiConvert.toMatrix(timatrix);
			view.setLayoutParams(layoutParams);
			ViewParent viewParent = view.getParent();
			if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
				((View) viewParent).postInvalidate();
			}
		}
	}
	
	protected void invalidate() {
	    View view = getOuterView();
        if (view != null) {
            if (!useCustomLayoutParams) {
                view.setLayoutParams(layoutParams);
            }
            ViewParent viewParent = view.getParent();
            if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
                ((View) viewParent).postInvalidate();
            }
        }
	}
	
	public void applyAnchorPoint(Object anchorPoint)
	{
		View view = getOuterView();
		if (view != null && !useCustomLayoutParams) {
			if (anchorPoint instanceof HashMap) {
				HashMap point = (HashMap) anchorPoint;
				layoutParams.anchorX = TiConvert.toFloat(point, TiC.PROPERTY_X);
				layoutParams.anchorY = TiConvert.toFloat(point, TiC.PROPERTY_Y);
			}
			else {
				layoutParams.anchorX = layoutParams.anchorY = 0.5f;
			}
			view.setLayoutParams(layoutParams);
			ViewParent viewParent = view.getParent();
			if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
				((View) viewParent).postInvalidate();
			}
		}
	}

	public void forceLayoutNativeView(boolean informParent)
	{
		layoutNativeView(informParent);
	}

	protected void layoutNativeView()
	{
		layoutNativeView(false);
	}
	
	protected void redrawNativeView() {
		if (nativeView != null)
			nativeView.postInvalidate();
	}
	
	//for listview 
	public void setReusing(boolean value)
	{
		reusing = value;
	}

	protected void layoutNativeView(boolean informParent)
	{
		if (parent != null) {
			View outerView = getOuterView();
			ViewParent nativeParent = null;
			if (outerView != null) {
				nativeParent = outerView.getParent();
			}
			if (nativeParent != null) {
				if (((View) nativeParent).getVisibility() == View.INVISIBLE || ((View) nativeParent).getVisibility() == View.GONE) {
					//if we have a parent which is hidden, we are hidden, so no need to layout
					return;
				}
				if (informParent && nativeParent instanceof TiCompositeLayout) {
					((TiCompositeLayout) nativeParent).resort();
				}
			}
		}
		
		View childHolder = getParentViewForChild();
		if (childHolder != null) {
			childHolder.requestLayout();
		}
	}

	public void resort()
	{
		View v = getNativeView();
		if (v instanceof TiCompositeLayout) {
			((TiCompositeLayout) v).resort();
		}
	}
	public boolean iszIndexChanged()
	{
		return zIndexChanged;
	}
	protected void setNeedsLayout() {
	    setNeedsLayout(false);
	}
	protected void setNeedsLayout(final boolean informParent) {
	    mProcessUpdateFlags |= TIFLAG_NEEDS_LAYOUT;
        if (informParent) 
        {
            mProcessUpdateFlags |= TIFLAG_NEEDS_LAYOUT_INFORMPARENT;
        }

	}
	public void setzIndexChanged(boolean zIndexChanged)
	{
		this.zIndexChanged = zIndexChanged;
	}

    protected int fillLayout(String key, Object value, boolean withMatrix) {
        return TiConvert.fillLayout(key, value, layoutParams, true);
    }

    protected int fillLayout(KrollDict d) {
        return TiConvert.fillLayout(d, layoutParams, true);
    }

	public void propertySet(String key, Object newValue, Object oldValue,
			boolean changedProperty) {
	    if (changedProperty) {
            int layoutPropsFlags = fillLayout(key, newValue, true);
            if (layoutPropsFlags != 0) {
                //it means it is a layout property so already handled
                mProcessUpdateFlags |= layoutPropsFlags;
                return;
            }
	    }
	    if (key.startsWith(TiC.PROPERTY_BACKGROUND_PREFIX)) {
            TiBackgroundDrawable bgdDrawable = getOrCreateBackground();
	        switch (key) {
            case TiC.PROPERTY_BACKGROUND_COLOR:
                int color = TiConvert.toColor(newValue);
                bgdDrawable.setDefaultColor(color);
                bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_1,
                        color);
                bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_2,
                        color);
                break;
            case TiC.PROPERTY_BACKGROUND_SELECTED_COLOR:
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE,
                        TiConvert.toColor(newValue));
                break;
            case TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR:
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE,
                        TiConvert.toColor(newValue));
                break;
            case TiC.PROPERTY_BACKGROUND_DISABLED_COLOR:
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE,
                        TiConvert.toColor(newValue));
                break;
            case TiC.PROPERTY_BACKGROUND_SELECTED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_FOCUSED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_DISABLED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1, drawable);
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2, drawable);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_IMAGE:
            {
                boolean repeat = proxy.getProperties().optBoolean(
                        TiC.PROPERTY_BACKGROUND_REPEAT, false);
                setBackgroundImageDrawable(newValue, repeat, new int[][] {
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1,
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2 });
                break;
            }
            case TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE:
            {
                boolean repeat = proxy.getProperties().optBoolean(
                        TiC.PROPERTY_BACKGROUND_REPEAT, false);
                setBackgroundImageDrawable(newValue, repeat,
                        new int[][] { TiUIHelper.BACKGROUND_SELECTED_STATE });
                break;
            }
            case TiC.PROPERTY_BACKGROUND_FOCUSED_IMAGE:
            {
                boolean repeat = proxy.getProperties().optBoolean(
                        TiC.PROPERTY_BACKGROUND_REPEAT, false);
                setBackgroundImageDrawable(newValue, repeat,
                        new int[][] { TiUIHelper.BACKGROUND_FOCUSED_STATE });
                break;
            }
            case TiC.PROPERTY_BACKGROUND_DISABLED_IMAGE:
            {
                boolean repeat = proxy.getProperties().optBoolean(
                        TiC.PROPERTY_BACKGROUND_REPEAT, false);
                setBackgroundImageDrawable(newValue, repeat,
                        new int[][] { TiUIHelper.BACKGROUND_DISABLED_STATE });
                break;
            }
            case TiC.PROPERTY_BACKGROUND_SELECTED_INNERSHADOWS:
            {
                Shadow[] shadows = TiConvert.toShadowArray((Object[]) newValue);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE, shadows);
//                bgdDrawable.setInnerShadowsForState(
//                        TiUIHelper.BACKGROUND_FOCUSED_STATE, shadows);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_FOCUSED_INNERSHADOWS:
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE,
                        TiConvert.toShadowArray((Object[]) newValue));
                break;
            case TiC.PROPERTY_BACKGROUND_DISABLED_INNERSHADOWS:
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE,
                        TiConvert.toShadowArray((Object[]) newValue));
                break;
            case TiC.PROPERTY_BACKGROUND_INNERSHADOWS:
            {
                Shadow[] shadows = TiConvert.toShadowArray((Object[]) newValue);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1, shadows);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2, shadows);
                break;
            }
            case TiC.PROPERTY_BACKGROUND_OPACITY:
                if (background != null)
                    TiUIHelper.setDrawableOpacity(
                            background,
                            ViewHelper.getAlpha(getNativeView())
                                    * TiConvert.toFloat(newValue, 1f));
                break;
            case TiC.PROPERTY_BACKGROUND_PADDING:
                Log.i(TAG, key + " not yet implemented.");
                break;
            default:
                break;
            }
            if (changedProperty)
                bgdDrawable.invalidateSelf();
            return;
        } else if (key.startsWith(TiC.PROPERTY_BORDER_PREFIX)) {
            TiBorderWrapperView view = getOrCreateBorderView();
            TiBackgroundDrawable borderDrawable = view.getBorderDrawable();
            switch (key) {
            case TiC.PROPERTY_BORDER_COLOR:
            {
                int color = TiConvert.toColor(newValue);
                borderDrawable.setDefaultColor(color);
                borderDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_1,
                        color);
                borderDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_2,
                        color);
                break;
            }
            case TiC.PROPERTY_BORDER_SELECTED_COLOR:
            {
                borderDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE,
                        TiConvert.toColor(newValue));
                break;
            }
            case TiC.PROPERTY_BORDER_FOCUSED_COLOR:
            {
                borderDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE,
                        TiConvert.toColor(newValue));
                break;
            }
            case TiC.PROPERTY_BORDER_DISABLED_COLOR:
            {
                borderDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE,
                        TiConvert.toColor(newValue));
                break;
            }
            case TiC.PROPERTY_BORDER_SELECTED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                borderDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BORDER_FOCUSED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                borderDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BORDER_DISABLED_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                borderDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE, drawable);
                break;
            }
            case TiC.PROPERTY_BORDER_GRADIENT:
            {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                borderDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1, drawable);
                borderDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2, drawable);
                break;
            }
            case TiC.PROPERTY_BORDER_RADIUS:
                setBorderRadius(newValue);
                break;
            case TiC.PROPERTY_BORDER_WIDTH:
                setBorderWidth(TiUIHelper.getInPixels(newValue));
                break;
            case TiC.PROPERTY_BORDER_PADDING:
                mBorderPadding = TiConvert.toPaddingRect(newValue);
                if (borderView != null) {
                    borderView.setBorderPadding(mBorderPadding);
                }
                break;
            }
            if (changedProperty)
                view.postInvalidate();
            return;
        } else if (key.startsWith(TiC.PROPERTY_ACCESSIBILITY_PREFIX)) {
            switch (key) {
            case TiC.PROPERTY_ACCESSIBILITY_HIDDEN:
                applyAccessibilityHidden(newValue);
                break;
            default:
                applyContentDescription();
                break;
            }
            return;
        }
	    
	    switch (key) {
        case TiC.PROPERTY_LAYOUT:
        {
            View parentViewForChild = getParentViewForChild();
            if (parentViewForChild instanceof TiCompositeLayout) {
                ((TiCompositeLayout) parentViewForChild)
                        .setLayoutArrangement(TiConvert.toString(newValue));
            }
            setNeedsLayout();
            break;
        }
        case TiC.PROPERTY_HORIZONTAL_WRAP:
            if (nativeView instanceof TiCompositeLayout) {
                ((TiCompositeLayout) getParentViewForChild())
                        .setEnableHorizontalWrap(TiConvert.toBoolean(newValue,
                                false));
            }
            setNeedsLayout(changedProperty);
            break;
        case TiC.PROPERTY_FOCUSABLE:
            isFocusable = TiConvert.toBoolean(newValue, false);
            if (changedProperty)
                registerForKeyPress(nativeView, isFocusable);
            break;
        case TiC.PROPERTY_TOUCH_ENABLED:
            isTouchEnabled = TiConvert.toBoolean(newValue, true);
//          nativeView.setEnabled(value);
            doSetClickable(isTouchEnabled);
            break;
        case TiC.PROPERTY_VISIBLE:
            this.setVisibility(TiConvert.toBoolean(newValue, true) ? View.VISIBLE
                    : View.GONE);
            break;
        case TiC.PROPERTY_ENABLED:
            boolean oldEnabled = isEnabled;
            isEnabled = TiConvert.toBoolean(newValue, true);
            if (oldEnabled != isEnabled) {
                setEnabled(isEnabled, true);
            }
            break;
        case TiC.PROPERTY_EXCLUSIVE_TOUCH:
            exclusiveTouch = TiConvert.toBoolean(newValue);
            break;
        
        case TiC.PROPERTY_VIEW_MASK:
            setViewMask(newValue);
            break;
        case TiC.PROPERTY_OPACITY:
            setOpacity(TiConvert.toFloat(newValue, 1f));
            break;
        case TiC.PROPERTY_TRANSFORM:
            applyTransform(newValue);
            break;
        case TiC.PROPERTY_ANCHOR_POINT:
            applyAnchorPoint(newValue);
            break;
        case TiC.PROPERTY_KEEP_SCREEN_ON:
            if (nativeView != null) {
                nativeView.setKeepScreenOn(TiConvert.toBoolean(newValue, false));
            }
            break;
        case TiC.PROPERTY_TOUCH_PASSTHROUGH:
            touchPassThrough = TiConvert.toBoolean(newValue, false);
            break;
        case TiC.PROPERTY_DISPATCH_PRESSED:
            dispatchPressed = TiConvert.toBoolean(newValue, false);
            break;
        case TiC.PROPERTY_SOFT_KEYBOARD_ON_FOCUS:
            focusKeyboardState = TiConvert.toInt(newValue);
            break;
        case TiC.PROPERTY_PREVENT_LISTVIEW_SELECTION:
            preventListViewSelection = TiConvert.toBoolean(newValue, false);
            break;
        case TiC.PROPERTY_CLIP_CHILDREN:
        {
            clipChildren = TiConvert.toBoolean(newValue, false);
            View parentViewForChild = getParentViewForChild();
            if (parentViewForChild instanceof ViewGroup) {
                ((ViewGroup) parentViewForChild).setClipChildren(clipChildren);
            }
            if (borderView != null) {
                borderView.setClipChildren(clipChildren);
            }
            if (!clipChildren) {
                ViewGroup parent = (ViewGroup) getOuterView().getParent();
                if (parent != null)
                    parent.setClipChildren(clipChildren);
            }
            break;
        }
        case TiC.PROPERTY_DISABLE_HW:
            boolean value = TiConvert.toBoolean(newValue);
            if (value)
                disableHWAcceleration();
            else
                enableHWAcceleration();
            break;
	        case TiC.PROPERTY_MASK_FROM_VIEW:
	            KrollProxy maskProxy = proxy.addProxyToHold(newValue, "maskView");
                if (maskProxy != null && maskProxy instanceof TiViewProxy) {
                    TiUIView tiView = ((TiViewProxy)maskProxy).getOrCreateView();
                    View view = tiView.getOuterView();
                    if (tiView.getParent() == null) {
                        view.setVisibility(View.GONE);
                        add(tiView);
                    }
                    getOrCreateBorderView().setMaskView(view);
                }
                else if (borderView != null) {
                    getOrCreateBorderView().setMaskView(null);
                }
	            break;
        default:
            break;
        }
	}


	protected void setBackgroundImageDrawable(Object object, boolean backgroundRepeat, int[][] states) {
		TiBackgroundDrawable bgdDrawable = getOrCreateBackground();
		Drawable drawable = TiUIHelper.buildImageDrawable(nativeView.getContext(), object, backgroundRepeat, proxy);
		
		for (int i = 0; i < states.length; i++) {
			bgdDrawable.setImageDrawableForState(states[i], drawable);
		}
	}
	
	public static void setFocusable(View view, boolean focusable) 
	{
	    view.setFocusable(focusable);
        //so dumb setFocusable to false set  setFocusableInTouchMode
        // but not when using true :s so we have to do it
        view.setFocusableInTouchMode(focusable);
	}
		
	protected void setEnabled(View view, boolean enabled, boolean focusable, boolean setChildren) {
		view.setEnabled(enabled);
		setFocusable(view, focusable);
		if (setChildren && view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++) {
				View child = group.getChildAt(i);
				Object tag = child.getTag();
				if (tag != null && tag instanceof TiUIView) {
					((TiUIView) tag).setEnabled(enabled, setChildren);
    			} else {
    					setEnabled(child, enabled, focusable, setChildren);
    			}
    		}
		}
	}

	protected void setEnabled(boolean enabled, boolean setChildren){
        setEnabled(getOuterView(), enabled && isEnabled, enabled && isFocusable, setChildren);
    }
	
	protected ArrayList<String> keySequence() {
	    return null;
	}
	
	protected void handleProperties(KrollDict d, final boolean changed) {
	    final KrollProxy proxy = this.proxy;
	    if (keySequence() != null) {
	        for (final String key : keySequence()) {
	            if (d.containsKey(key)) {
	                propertySet(key, d.get(key), proxy.getProperty(key), changed);
	                d.remove(key);
	            }
            }
	    }
	    for (Map.Entry<String, Object> entry : d.entrySet()) {
	        final String key = entry.getKey();
            propertySet(key, entry.getValue(), proxy.getProperty(key), changed);
        }
	}
	
	@Override
    public void processApplyProperties(KrollDict d) {
        aboutToProcessProperties(d);
        handleProperties(d, true);
        didProcessProperties();
    }

    @Override
	public void processProperties(KrollDict d)
	{
        aboutToProcessProperties(d);
        handleProperties(d, false);
        didProcessProperties();
        
        if (!(layoutParams instanceof AnimationLayoutParams) && getOuterView() != null && !useCustomLayoutParams) {
            getOuterView().setLayoutParams(layoutParams);
        }

		if (touchView == null || touchView.get() != getTouchView()) {
			registerForTouch();
			registerForKeyPress();
		}
	}
    
    protected void aboutToProcessProperties(KrollDict d) {
        mProcessUpdateFlags = 0; // just to make sure we start from scratch
        
        //we fill the layout props here not to have to do it in the processProperties
        if (!(layoutParams instanceof AnimationLayoutParams)) {
            mProcessUpdateFlags |= fillLayout(d);
        }
    }
    protected void didProcessProperties() {
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_LAYOUT) != 0) {
            layoutNativeView(((mProcessUpdateFlags & TIFLAG_NEEDS_LAYOUT_INFORMPARENT) != 0));
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_LAYOUT;
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_LAYOUT_INFORMPARENT;
        }
        
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_INVALIDATE) != 0) {
            View view = getOuterView();
            if (view != null) {
                if (!useCustomLayoutParams) {
                    view.setLayoutParams(layoutParams);
                }
                ViewParent viewParent = view.getParent();
                if (view.getVisibility() == View.VISIBLE && viewParent instanceof View) {
                    ((View) viewParent).postInvalidate();
                }
            }
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_INVALIDATE;
        }
    }
    
    @Override
    public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
    {
        propertySet(key, newValue, oldValue, true);
        didProcessProperties();
    }

    @Override
    public void propertiesChanged(List<KrollPropertyChange> changes, KrollProxy proxy)
    {
        for (KrollPropertyChange change : changes) {
            propertySet(change.getName(), change.getNewValue(), change.getOldValue(), true);
        }
        didProcessProperties();
    }
    
	
	public void onFocusChange(final View v, boolean hasFocus)
	{
		if (hasFocus) {
			TiMessenger.postOnMain(new Runnable() {
				public void run() {
					TiUIHelper.requestSoftInputChange(TiUIView.this, v);
				}
			});
			fireEvent(TiC.EVENT_FOCUS, getFocusEventObject(hasFocus), false, true);
		} else {
			fireEvent(TiC.EVENT_BLUR, getFocusEventObject(hasFocus), false, true);
		}
	}

	protected KrollDict getFocusEventObject(boolean hasFocus)
	{
		return null;
	}

	protected InputMethodManager getIMM()
	{
		InputMethodManager imm = null;
		imm = (InputMethodManager) TiApplication.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
		return imm;
	}
	
	public static void handleFocus(View view)
    {
        int oldDesc = ViewGroup.FOCUS_AFTER_DESCENDANTS;
        if (view instanceof ViewGroup){
            oldDesc = ((ViewGroup) view).getDescendantFocusability();
            ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        view.requestFocus();
        if (view instanceof ViewGroup){
            ((ViewGroup) view).setDescendantFocusability(oldDesc);
        }
    }

	/**
	 * Focuses the view.
	 */
	public boolean focus()
	{
		View view = getFocusView();
		if (view != null && !hasFocus()) {
			if (TiApplication.isUIThread()) {
	            handleFocus(view);
	        }
	        else {
	            TiMessenger.sendBlockingMainMessage(proxy.getMainHandler().obtainMessage(MSG_FOCUS), view);
	        }
	        return true;
		}
		return false;
	}

	public boolean hasFocus()
	{
		View view = getFocusView();
		if (view != null) {
			return view.hasFocus();
		}
		return false;
	}
		
	public static void handleClearFocus(View view)
	{
        View root = view.getRootView();
        boolean oldValue = true;
        int oldDesc = ViewGroup.FOCUS_BEFORE_DESCENDANTS;
        
        if (root != null) {
        	if (root instanceof ViewGroup){
        		oldDesc = ((ViewGroup) root).getDescendantFocusability();
            	((ViewGroup) root).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
        	oldValue = root.isFocusable();
            setFocusable(root, false);
        }
		view.clearFocus();
		if (root != null) {
	        setFocusable(root, oldValue);
        	if (root instanceof ViewGroup){
            	((ViewGroup) root).setDescendantFocusability(oldDesc);
            }
        }
		
		TiUIHelper.hideSoftKeyboard(view);
	}
	
	protected void clearFocus(View view)
	{
		if (TiApplication.isUIThread()) {
			handleClearFocus(view);
		}
		else {
			TiMessenger.sendBlockingMainMessage(proxy.getMainHandler().obtainMessage(MSG_CLEAR_FOCUS), view);
		}
	}

	/**
	 * Blurs the view.
	 */
	public boolean blur()
	{
		View view = getFocusView();
		if (view != null && hasFocus()) {
			clearFocus(view);
			return true;
		}
		return false;
	}

	public void release()
	{
		if (Log.isDebugModeEnabled()) {
			Log.d(TAG, "Releasing: " + this, Log.DEBUG_MODE);
		}
		proxy.cancelAllAnimations();
		View nv = getRootView();
		if (nv != null) {
			if (nv instanceof ViewGroup) {
				ViewGroup vg = (ViewGroup) nv;
				if (Log.isDebugModeEnabled()) {
					Log.d(TAG, "Group has: " + vg.getChildCount(), Log.DEBUG_MODE);
				}
				if (!(vg instanceof AdapterView<?>)) {
					vg.removeAllViews();
				}
			}
			Drawable d = nv.getBackground();
			if (d != null) {
				setBackgroundDrawable(nv, null);
				d.setCallback(null);
				if (d instanceof TiBackgroundDrawable) {
					((TiBackgroundDrawable)d).releaseDelegate();
				}
				d = null;
			}
			TiUIHelper.removeViewFromSuperView(getOuterView());
			nativeView = null;
			borderView = null;
			if (proxy != null) {
				proxy.setModelListener(null);
			}
		}
	}

	public void setVisibility(int visibility)
	{
		if (this.visibility == visibility) return;
			forceLayoutNativeView(true);
		this.visibility = visibility;
		proxy.setProperty(TiC.PROPERTY_VISIBLE, (visibility == View.VISIBLE));
		
		View view = getRootView();
		if (view != null) {
			view.clearAnimation();
			view.setVisibility(this.visibility);
		}
		view = getOuterView();
		if (view != null) {
			view.clearAnimation();
			view.setVisibility(this.visibility);
		}
		
	}

	/**
	 * Shows the view, changing the view's visibility to View.VISIBLE.
	 */
	public void show()
	{
		this.setVisibility(View.VISIBLE);
		if (getOuterView() == null) {
			Log.w(TAG, "Attempt to show null native control", Log.DEBUG_MODE);
		}
	}

	/**
	 * Hides the view, changing the view's visibility to View.INVISIBLE.
	 */
	public void hide()
	{
		this.setVisibility(View.GONE);
		if (getOuterView() == null) {
			Log.w(TAG, "Attempt to hide null native control", Log.DEBUG_MODE);
		}
	}
	
	public void propagateChildDrawableState(View child)
	{
		propagateDrawableState(child.getDrawableState());
	}
	
	public void propagateDrawableState(int[] state)
	{
		if (background != null)
		{
			background.setState(state);
		}
		if (borderView != null) {
			borderView.setDrawableState(state);
		}
	}

	protected TiBackgroundDrawable getOrCreateBackground()
	{
		if (background == null)
		{
			applyCustomBackground();
		}
		return background;
	}
	
	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public static void setBackgroundDrawable(View view, Drawable drawable) {
		if(TiC.JELLY_BEAN_OR_GREATER) {
			view.setBackground(drawable);
		} else {
			view.setBackgroundDrawable(drawable);
		}
	}

	protected void applyCustomBackground()
	{
		if (background == null) {
			background = new TiBackgroundDrawable();
				float alpha = 1.0f;
			if (!TiC.HONEYCOMB_OR_GREATER) {
				if (proxy.hasProperty(TiC.PROPERTY_OPACITY))
					alpha *= TiConvert.toFloat(proxy.getProperty(TiC.PROPERTY_OPACITY));
			}
			if (proxy.hasProperty(TiC.PROPERTY_BACKGROUND_OPACITY))
				alpha *= TiConvert.toFloat(proxy.getProperty(TiC.PROPERTY_BACKGROUND_OPACITY));
			
			if (alpha < 1.0)
				TiUIHelper.setDrawableOpacity(background, alpha);
			if (proxy.hasProperty(TiC.PROPERTY_BACKGROUND_REPEAT))
				background.setImageRepeat(TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_BACKGROUND_REPEAT)));
			if (borderView != null) {
				background.setRadius(borderView.getRadius());
			}
		}
		View view = getNativeView();
		if (view != null) {
			Drawable currentDrawable = view.getBackground();
			if (currentDrawable != null) {
				currentDrawable.setCallback(null);
				if (currentDrawable instanceof TiBackgroundDrawable) {
					((TiBackgroundDrawable) currentDrawable).releaseDelegate();
				}
				setBackgroundDrawable(view, null);
			}
			setBackgroundDrawable(view, background);
		}
	}
	
    protected void applyCustomForeground() {
        if (mForegroundDrawable == null) {
            TypedArray a = getContext().obtainStyledAttributes(
                    new int[] { android.R.attr.selectableItemBackground });
            mForegroundDrawable = a.getDrawable(0);
            a.recycle();
        }
        if (mForegroundDrawable != null) {
            TiCompositeLayout view = (TiCompositeLayout) ((nativeView instanceof TiCompositeLayout) ? nativeView
                    : getOrCreateBorderView());
            if (view != null) {
                if (mForegroundDrawable != null) {
                    mForegroundDrawable.setCallback(view);
                }
                view.setForeground(mForegroundDrawable);
            }
        } else {
            TiCompositeLayout view = (TiCompositeLayout) ((nativeView instanceof TiCompositeLayout) ? nativeView
                    : borderView);
            if (view != null) {
                borderView.setForeground(mForegroundDrawable);
            }
        }
    }
	
	private void addBorderView(){
		View rootView = getRootView();
		// Create new layout params for the child view since we just want the
		// wrapper to control the layout
		LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		// If the view already has a parent, we need to detach it from the parent
		// and add the borderView to the parent as the child
		ViewGroup savedParent = null;
		int savedIndex = 0;
		if (rootView.getParent() != null) {
			ViewParent nativeParent = rootView.getParent();
			if (nativeParent instanceof ViewGroup) {
				savedParent = (ViewGroup) nativeParent;
				savedIndex = savedParent.indexOfChild(rootView);
				savedParent.removeView(rootView);
			}
		}
		nativeView.setTag(null);
		borderView.setTag(this);
		borderView.setLayoutParams(getLayoutParams());
		borderView.addView(rootView, params);
		if (savedParent != null) {
			savedParent.addView(borderView, savedIndex);
		}
		
//		if ((borderView.getRadius() != null || hardwareAccEnabled == false) && HONEYCOMB_OR_GREATER) {
//			disableHWAcceleration(borderView);
//		}
	}
	
	protected TiBorderWrapperView getOrCreateBorderView()
	{
		if (borderView == null) {
			Activity currentActivity = proxy.getActivity();
			if (currentActivity == null) {
				currentActivity = TiApplication.getAppCurrentActivity();
			}
			float oldAlpha = getOpacity();
			borderView = new TiBorderWrapperView(currentActivity, proxy);
			ViewHelper.setAlpha(borderView, oldAlpha);
			borderView.setVisibility(this.visibility);

			if (proxy.hasProperty(TiC.PROPERTY_CLIP_CHILDREN)) {
				boolean value = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_CLIP_CHILDREN));
				borderView.setClipChildren(value);
			}
			
			if(mBorderPadding != null) borderView.setBorderPadding(mBorderPadding);
			if (hardwareAccEnabled == false) {
				disableHWAcceleration(borderView);
			}
			addBorderView();
		}
		return borderView;
	}
	
	private void setBorderRadius(Object value){
		float[] result = null;
		if (value instanceof Object[]) {
			result = getBorderRadius(TiConvert.toFloatArray((Object[]) value));
		}
		else if (value instanceof Number) {
			result = getBorderRadius(TiConvert.toFloat(value, 0f));
		}
		if (background != null) {
			background.setRadius(result);
		}
		getOrCreateBorderView().setRadius(result);
	}
	private float[] getBorderRadius(float radius){
		float realRadius =  radius * TiApplication.getAppDensity();
		float[] result = new float[8];
		Arrays.fill(result, realRadius);
		return result;
	}
	private float[] getBorderRadius(float[] radius){
		float factor = TiApplication.getAppDensity();
		float[] result = null;
		if (radius.length == 4) {
			result = new float[8];
			for (int i = 0; i < radius.length; i++) {
				result[i*2] = result[i*2+1] = radius[i] * factor;
			}
		}
		else if (radius.length == 8) 
		{
			result = new float[8];
			if (radius.length == 4) {
				for (int i = 0; i < radius.length; i++) {
					result[i] = radius[i] * factor;
				}
			}
		}
		return result;
	}
	
	private void setBorderWidth(float width){
//		float realWidth = (new TiDimension(Float.toString(width), TiDimension.TYPE_WIDTH)).getAsPixels(nativeView);
		getOrCreateBorderView().setBorderWidth(width);
	}
	
	private void setViewMask(Object mask){
		boolean tileImage = proxy.getProperties().optBoolean(
                TiC.PROPERTY_BACKGROUND_REPEAT, false);
		getOrCreateBorderView().setMask(TiUIHelper.buildImageDrawable(nativeView.getContext(), mask, tileImage, proxy));
//		disableHWAcceleration();
	}

	protected static SparseArray<String> motionEvents = new SparseArray<String>();
	static
	{
		motionEvents.put(MotionEvent.ACTION_DOWN, TiC.EVENT_TOUCH_START);
		motionEvents.put(MotionEvent.ACTION_UP, TiC.EVENT_TOUCH_END);
		motionEvents.put(MotionEvent.ACTION_MOVE, TiC.EVENT_TOUCH_MOVE);
		motionEvents.put(MotionEvent.ACTION_CANCEL, TiC.EVENT_TOUCH_CANCEL);
	}

	protected KrollDict dictFromEvent(MotionEvent e)
	{
		KrollDict data = new KrollDict();
		if (e != null) 
		{
			double density = TiApplication.getAppDensity();
			int[] coords = new int[2];
			getTouchView().getLocationInWindow(coords);

			final double rawx = e.getRawX();
			final double rawy = e.getRawY();
			final double x = (double) rawx - coords[0];
			final double y = (double) rawy - coords[1];
			data.put(TiC.EVENT_PROPERTY_X, x / density);
			data.put(TiC.EVENT_PROPERTY_Y, y / density);
			KrollDict globalPoint = new KrollDict();
			globalPoint.put(TiC.EVENT_PROPERTY_X, rawx / density);
			globalPoint.put(TiC.EVENT_PROPERTY_Y, rawy / density);
			data.put(TiC.EVENT_PROPERTY_GLOBALPOINT, globalPoint);
		}
		data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
		return data;
	}

//	protected KrollDict dictFromEvent(KrollDict dictToCopy){
//		KrollDict data = new KrollDict();
//		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_X)){
//			data.put(TiC.EVENT_PROPERTY_X, dictToCopy.get(TiC.EVENT_PROPERTY_X));
//		} else {
//			data.put(TiC.EVENT_PROPERTY_X, (double)0);
//		}
//		if (dictToCopy.containsKey(TiC.EVENT_PROPERTY_Y)){
//			data.put(TiC.EVENT_PROPERTY_Y, dictToCopy.get(TiC.EVENT_PROPERTY_Y));
//		} else {
//			data.put(TiC.EVENT_PROPERTY_Y, (double)0);
//		}
//		data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
//		return data;
//	}

	protected boolean allowRegisterForTouch()
	{
		return true;
	}

	/**
	 * @module.api
	 */
	protected boolean allowRegisterForKeyPress()
	{
		return true;
	}

	public View getOuterView()
	{
		return borderView == null ? nativeView : borderView;
	}

	public View getRootView()
	{
		return nativeView;
	}
	
	public View getFocusView()
	{
		return nativeView;
	}
	
	public int getFocusState()
    {
        return focusKeyboardState;
    }

	public void registerForTouch()
	{
		if (allowRegisterForTouch()) {
			registerForTouch(getTouchView());
		}
	}
	
	public void checkUpEventSent(MotionEvent event){
		if (pointerDown) {
			customInterceptTouchEvent(event);
		}
		else {
			for (TiUIView child : children) {
				child.checkUpEventSent(event);
			}
		}
	}
	
	protected void setPointerDown(final boolean value) {
	    pointerDown = value;
	}
	
	private int pointersDown = 0;
	private boolean pointerDown = false;
	public boolean customInterceptTouchEvent(MotionEvent event) {
		if (mTouchDelegate != null) {
			mTouchDelegate.onTouchEvent(event, TiUIView.this);
		}
		int action = event.getAction();
		if (exclusiveTouch) {
			ViewGroup parent =  (ViewGroup)getTouchView().getParent();
			if(parent != null) {
				switch (action) {
			    case MotionEvent.ACTION_MOVE: 
			        parent.requestDisallowInterceptTouchEvent(true);
			        break;
			    case MotionEvent.ACTION_UP:
			    case MotionEvent.ACTION_OUTSIDE:
			    case MotionEvent.ACTION_CANCEL:
			    	parent.requestDisallowInterceptTouchEvent(false);
			        break;
			    }
			}
			
		}
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_OUTSIDE) {
			lastUpEvent = event;
			setPointerDown(false);
		}

		if (action == MotionEvent.ACTION_DOWN ) {
			lastDownEvent = event;
            setPointerDown(true);
		}

		scaleDetector.onTouchEvent(event);
		if (scaleDetector.isInProgress()) {
			pointersDown = 0;
			return true;
		}

		boolean handled = detector.onTouchEvent(event);
		if (handled) {
			pointersDown = 0;
			return true;
		}

		if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
			if (didScale) {
				didScale = false;
				pointersDown = 0;
			} else {
				pointersDown++;
			}
		} else if (action == MotionEvent.ACTION_UP) {
			if (pointersDown == 1) {
				if (hierarchyHasListener(TiC.EVENT_TWOFINGERTAP)) {
					fireEventNoCheck(TiC.EVENT_TWOFINGERTAP, dictFromEvent(event));
				}
				pointersDown = 0;
				return true;
			}
			pointersDown = 0;
		}

		handleTouchEvent(event);
		return !isTouchEnabled;
	}

	protected void registerTouchEvents(final View touchable)
	{
		touchView = new WeakReference<View>(touchable);

		scaleDetector = new ScaleGestureDetector(touchable.getContext(),
			new SimpleOnScaleGestureListener()
			{
				// protect from divide by zero errors
				long minTimeDelta = 1;
				float minStartSpan = 1.0f;
				float startSpan;

				@Override
				public boolean onScale(ScaleGestureDetector sgd)
				{
					if (hierarchyHasListener(TiC.EVENT_PINCH)) {
						float timeDelta = sgd.getTimeDelta() == 0 ? minTimeDelta : sgd.getTimeDelta();

					// Suppress scale events (and allow for possible two-finger tap events)
					// until we've moved at least a few pixels. Without this check, two-finger 
					// taps are very hard to register on some older devices.
					if (!didScale) {
						if (Math.abs(sgd.getCurrentSpan() - startSpan) > SCALE_THRESHOLD) {
							didScale = true;
						} 
					}

						if (didScale) {
							KrollDict data = new KrollDict();
							data.put(TiC.EVENT_PROPERTY_SCALE, sgd.getCurrentSpan() / startSpan);
							data.put(TiC.EVENT_PROPERTY_VELOCITY, (sgd.getScaleFactor() - 1.0f) / timeDelta * 1000);
							data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
	
							return fireEventNoCheck(TiC.EVENT_PINCH, data);
						}
					}
					return false;
				}

				@Override
				public boolean onScaleBegin(ScaleGestureDetector sgd)
				{
					startSpan = sgd.getCurrentSpan() == 0 ? minStartSpan : sgd.getCurrentSpan();
					return true;
				}
			});

		detector = new GestureDetector(touchable.getContext(), new SimpleOnGestureListener()
		{
			@Override
			public boolean onDoubleTap(MotionEvent e)
			{
				boolean hasDoubleTap = hierarchyHasListener(TiC.EVENT_DOUBLE_TAP);
				boolean hasDoubleClick = hierarchyHasListener(TiC.EVENT_DOUBLE_CLICK);
				
				if (hasDoubleTap || hasDoubleClick) {
					KrollDict event = dictFromEvent(e);
					if (hasDoubleTap) fireEventNoCheck(TiC.EVENT_DOUBLE_TAP, event);
					if (hasDoubleClick) fireEventNoCheck(TiC.EVENT_DOUBLE_CLICK, event);
					return true;
				}
				return false;
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e)
			{
				Log.d(TAG, "TAP, TAP, TAP on " + proxy, Log.DEBUG_MODE);
				if (hierarchyHasListener(TiC.EVENT_SINGLE_TAP)) {
					return fireEventNoCheck(TiC.EVENT_SINGLE_TAP, dictFromEvent(e));
					// Moved click handling to the onTouch listener, because a single tap is not the
					// same as a click. A single tap is a quick tap only, whereas clicks can be held
					// before lifting.
					// boolean handledClick = proxy.fireEvent(TiC.EVENT_CLICK, dictFromEvent(event));
					// Note: this return value is irrelevant in our case. We "want" to use it
					// in onTouch below, when we call detector.onTouchEvent(event); But, in fact,
					// onSingleTapConfirmed is *not* called in the course of onTouchEvent. It's
					// called via Handler in GestureDetector. <-- See its Java source.
					// return handledTap;// || handledClick;
				}
				return false;
			}
			
//			@Override
//            public boolean onSingleTapUp(MotionEvent e)
//            {
////                if (hierarchyHasListener(TiC.EVENT_CLICK)) {
////                    return fireEventNoCheck(TiC.EVENT_CLICK, dictFromEvent(e));
////                }
//                if (!hasListeners(TiC.EVENT_SINGLE_TAP) && hierarchyHasListener(TiC.EVENT_CLICK)) {
//                    fireEventNoCheck(TiC.EVENT_CLICK, dictFromEvent(lastUpEvent));
//                }
//                return false;
//            }

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
			{
				Log.d(TAG, "SWIPE on " + proxy, Log.DEBUG_MODE);
				if (hierarchyHasListener(TiC.EVENT_SWIPE)) {
					KrollDict data = dictFromEvent(e2);
					if (Math.abs(velocityX) > Math.abs(velocityY)) {
						data.put(TiC.EVENT_PROPERTY_DIRECTION, velocityX > 0 ? "right" : "left");
					} else {
						data.put(TiC.EVENT_PROPERTY_DIRECTION, velocityY > 0 ? "down" : "up");
					}
					fireEventNoCheck(TiC.EVENT_SWIPE, data);
					return false;
				}
				return false;
			}

			@Override
			public void onLongPress(MotionEvent e)
			{
				Log.d(TAG, "LONGPRESS on " + proxy, Log.DEBUG_MODE);

				if (hierarchyHasListener(TiC.EVENT_LONGPRESS)) {
					fireEventNoCheck(TiC.EVENT_LONGPRESS, dictFromEvent(e));
				}
			}
		});
		
		touchable.setOnTouchListener(new OnTouchListener()
		{
			public boolean onTouch(View view, MotionEvent event)
			{
				return customInterceptTouchEvent(event);
			}
		});
		
	}
	
	protected void handleTouchEvent(MotionEvent event) {
		String motionEvent = motionEvents.get(event.getAction());
		if (motionEvent != null) {
			if (hierarchyHasListener(motionEvent)) {
				fireEventNoCheck(motionEvent, dictFromEvent(event));
			}
		}
	}

	protected void registerForTouch(final View touchable)
	{
		if (touchable == null) {
			return;
		}
		
		boolean clickable = isClickable();

		if (clickable) {
			if (touchView == null || touchView.get() != touchable) registerTouchEvents(touchable);

			// Previously, we used the single tap handling above to fire our click event. It doesn't
			// work: a single tap is not the same as a click. A click can be held for a while before
			// lifting the finger; a single-tap is only generated from a quick tap (which will also cause
			// a click.) We wanted to do it in single-tap handling presumably because the singletap
			// listener gets a MotionEvent, which gives us the information we want to provide to our
			// users in our click event, whereas Android's standard OnClickListener does _not_ contain
			// that info. However, an "up" seems to always occur before the click listener gets invoked,
			// so we store the last up event's x,y coordinates (see onTouch above) and use them here.
			// Note: AdapterView throws an exception if you try to put a click listener on it.
		}
		else {
		    if (touchView != null) {
                touchView.get().setOnTouchListener(null);
		        touchView = null;
		    }
		}
		
       doSetClickable(touchable, clickable);
	}


	public void registerForKeyPress()
	{
		if (allowRegisterForKeyPress()) {
			registerForKeyPress(getNativeView());
		}
	}

	protected void registerForKeyPress(final View v)
	{
		if (v == null) {
			return;
		}
		
		registerForKeyPress(v, isFocusable);
	}

	protected void registerForKeyPress(final View v, boolean focusable)
	{
		if (v == null) {
			return;
		}

        setFocusable(v, focusable);

		// The listener for the "keypressed" event is only triggered when the view has focus. So we only register the
		// "keypressed" event when the view is focusable.
		if (focusable) {
			registerForKeyPressEvents(v);
		} else {
			v.setOnKeyListener(null);
		}
	}

	/**
	 * Registers a callback to be invoked when a hardware key is pressed in this view.
	 *
	 * @param v The view to have the key listener to attach to.
	 */
	protected void registerForKeyPressEvents(final View v)
	{
		if (v == null) {
			return;
		}

		v.setOnKeyListener(new OnKeyListener()
		{
			public boolean onKey(View view, int keyCode, KeyEvent event)
			{
				if (event.getAction() == KeyEvent.ACTION_UP) {
					if (hierarchyHasListener(TiC.EVENT_KEY_PRESSED)) {
						KrollDict data = new KrollDict();
						data.put(TiC.EVENT_PROPERTY_KEYCODE, keyCode);
						fireEventNoCheck(TiC.EVENT_KEY_PRESSED, data);
					}

					switch (keyCode) {
						case KeyEvent.KEYCODE_ENTER:
						case KeyEvent.KEYCODE_DPAD_CENTER:
							if (hierarchyHasListener(TiC.EVENT_CLICK)) {
								fireEventNoCheck(TiC.EVENT_CLICK, null);
								return true;
							}
					}
				}
				return false;
			}
		});
	}


	/**
	 * Sets the nativeView's opacity.
	 * @param opacity the opacity to set.
	 */
	public void setOpacity(float opacity)
	{
	    opacity = Math.min(1, Math.max(0, opacity));
		View view = getOuterView();
//		View parentForChildren = getParentViewForChild();
//		if (parentForChildren != view) {
//			ViewHelper.setAlpha(parentForChildren, opacity);
//		}
//		if (borderView != null) {
//			ViewHelper.setAlpha(borderView, opacity);
			if(view != null) {
			ViewHelper.setAlpha(view, opacity);
		}

	}
	
	public float getOpacity() {
		return ViewHelper.getAlpha(getOuterView());
	}


//	public void clearOpacity(View view)
//	{
//		if (background != null)
//			background.clearColorFilter();
//	}

	public TiBlob toImage(Number scale)
	{
		float scaleValue = scale.floatValue();
		Bitmap bitmap = TiUIHelper.viewToBitmap(proxy.getProperties(), getNativeView());
		if (scaleValue != 1.0f) {
			bitmap = TiImageHelper.imageScaled(bitmap, scaleValue);
		}
		return TiBlob.blobFromObject(bitmap);
	}

	protected View getTouchView()
	{
		if (nativeView != null) {
			return nativeView;
		} else {
			if (touchView != null) {
				return touchView.get();
			}
		}
		return null;
	}
	
	public boolean touchPassThrough(View view, MotionEvent event)
	{
	    if (!isTouchEnabled) return true;
		if (touchPassThrough == true)
		{
		    if (view != null) {
		        int[] location = new int[2];
                double x = event.getRawX();
                double y = event.getRawY();
    		    for (int i = 0; i < children.size(); i++) {
                    TiUIView child = children.get(i);
                    View childView = child.getOuterView();
                    if (childView == null) continue;
                    childView.getLocationOnScreen(location);
                    if(location[0] <= x && x <= (location[0] + childView.getWidth()) && 
                            location[1] <= y && y <= (location[1] + childView.getHeight())){
                        if (!child.touchPassThrough(childView, event)) {
                            return false;
                        }
                    }
                }
			}
			return true;
		}
		return false;
	}
	
	public boolean getTouchPassThrough() {
		return touchPassThrough;
	}
	
	public boolean getDispatchPressed() {
		return dispatchPressed;
	}
	   
    public boolean getPreventListViewSelection() {
        return preventListViewSelection;
    }
    
    public boolean getClipChildren() {
        return clipChildren;
    }

	protected void doSetClickable(View view, boolean clickable)
	{
		if (view == null) {
			return;
		}
		if (!clickable) {
            removeOnClickListener(view);
            removeOnLongClickListener(view);
		} else {
			// n.b.: AdapterView throws if click listener set.
			// n.b.: setting onclicklistener automatically sets clickable to true.
			setOnClickListener(view);
			setOnLongClickListener(view);
		}
        view.setClickable(clickable);
        view.setLongClickable(clickable);
	}

	private void doSetClickable(boolean clickable)
	{
		doSetClickable(getTouchView(), clickable);
	}

	protected void doSetClickable()
	{
		doSetClickable(getTouchView(), isClickable());
	}

	/*
	 * Used just to setup the click listener if applicable.
	 */
	protected void doSetClickable(View view)
	{
		if (view == null) {
			return;
		}
		doSetClickable(view, view.isClickable());
	}

	/**
	 * Can be overriden by inheriting views for special click handling.  For example,
	 * the Facebook module's login button view needs special click handling.
	 */
	protected void setOnClickListener(View view)
	{
	    if (view instanceof AdapterView) return;

		view.setOnClickListener(new OnClickListener()
		{
			public void onClick(View view)
			{
			    //if singletap is active dont send click
				if (!hasListeners(TiC.EVENT_SINGLE_TAP) && hierarchyHasListener(TiC.EVENT_CLICK)) {
					fireEventNoCheck(TiC.EVENT_CLICK, dictFromEvent(lastUpEvent));
				}
			}
		});
	}
	
	protected void removeOnClickListener(View view)
    {
	    view.setOnClickListener(null);
    }
	
	public boolean fireEvent(String eventName, KrollDict data) {
		return fireEvent(eventName, data, true, true);
	}
	
	public boolean fireEventNoCheck(String eventName, KrollDict data) {
		return fireEvent(eventName, data, true, false);
	}
	
	public boolean fireEvent(String eventName, KrollDict data, boolean bubbles) {
		return fireEvent(eventName, data, bubbles, true);
	}
	
	public boolean hasListeners(String event, boolean checkParent) {
		return proxy.hasListeners(event, checkParent);
	}
	
	public boolean hasListeners(String event) {
		return hasListeners(event, false);
	}
	
	public boolean hierarchyHasListener(String event) {
		return proxy.hierarchyHasListener(event);
	}


	public boolean fireEvent(String eventName, KrollDict data, boolean bubbles, boolean checkListeners) {
		return proxy.fireEvent(eventName, data, bubbles, checkListeners);
	}

	protected void setOnLongClickListener(View view)
	{
	    
	    if (view instanceof AdapterView) return;
		view.setOnLongClickListener(new OnLongClickListener()
		{
			public boolean onLongClick(View view)
			{
                if (hierarchyHasListener(TiC.EVENT_LONGCLICK)) {
                    return fireEvent(TiC.EVENT_LONGCLICK, dictFromEvent(lastDownEvent), true, false);
                }
                return false;
			}
		});
	}
	
	protected void removeOnLongClickListener(View view)
    {
        view.setOnLongClickListener(null);
    }

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void disableHWAcceleration(View view)
	{
		if (TiC.HONEYCOMB_OR_GREATER) {
			view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void enableHWAcceleration(View view)
	{
		if (TiC.HONEYCOMB_OR_GREATER) {
			view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		}
	}

	protected void disableHWAcceleration()
	{
		if (hardwareAccEnabled == true) {
			disableHWAcceleration(getOuterView());
			hardwareAccEnabled = false;
		}
	}
	
	protected void enableHWAcceleration()
	{
		if (hardwareAccEnabled == false) {
			enableHWAcceleration(getOuterView());
			hardwareAccEnabled = true;
		}
	}
	
	public boolean hWAccelerationDisabled(){
		return !hardwareAccEnabled;
	}

	private void applyContentDescription()
	{
		if (proxy == null || nativeView == null) {
			return;
		}
		String contentDescription = composeContentDescription();
		if (contentDescription != null) {
			nativeView.setContentDescription(contentDescription);
		}
	}

	/**
	 * Our view proxy supports three properties to match iOS regarding
	 * the text that is read aloud (or otherwise communicated) by the
	 * assistive technology: accessibilityLabel, accessibilityHint
	 * and accessibilityValue.
	 *
	 * We combine these to create the single Android property contentDescription.
	 * (e.g., View.setContentDescription(...));
	 */
	protected String composeContentDescription()
	{
		if (proxy == null) {
			return null;
		}

		final String punctuationPattern = "^.*\\p{Punct}\\s*$";
		StringBuilder buffer = new StringBuilder();

		KrollDict properties = proxy.getProperties();
		String label, hint, value;
		label = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_LABEL));
		hint = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_HINT));
		value = TiConvert.toString(properties.get(TiC.PROPERTY_ACCESSIBILITY_VALUE));

		if (!TextUtils.isEmpty(label)) {
			buffer.append(label);
			if (!label.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(value)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(value);
			if (!value.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		if (!TextUtils.isEmpty(hint)) {
			if (buffer.length() > 0) {
				buffer.append(" ");
			}
			buffer.append(hint);
			if (!hint.matches(punctuationPattern)) {
				buffer.append(".");
			}
		}

		return buffer.toString();
	}

	private void applyAccessibilityProperties()
	{
		if (nativeView != null) {
			applyContentDescription();
			applyAccessibilityHidden();
		}

	}

	private void applyAccessibilityHidden()
	{
		if (nativeView == null || proxy == null) {
			return;
		}

		applyAccessibilityHidden(proxy.getProperty(TiC.PROPERTY_ACCESSIBILITY_HIDDEN));
	}

	private void applyAccessibilityHidden(Object hiddenPropertyValue)
	{
		if (nativeView == null) {
			return;
		}

		int importanceMode = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;

		if (hiddenPropertyValue != null && TiConvert.toBoolean(hiddenPropertyValue, false)) {
				importanceMode = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
		}

		ViewCompat.setImportantForAccessibility(nativeView, importanceMode);
	}
	
	public void setTiBackgroundColor(int color) {
		int currentColor = getTiBackgroundColor();
		if (currentColor != color) {
			TiBackgroundDrawable bgdDrawable = getOrCreateBackground();
			bgdDrawable.setDefaultColor(color);
			bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_1, color);
			bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_2, color);
			View outerView =  getOuterView();
			outerView.postInvalidate();
}
	}
	
	public int getTiBackgroundColor() {
//		return TiColorHelper.parseColor(TiConvert.toString(proxy.getProperty(TiC.PROPERTY_BACKGROUND_COLOR)));
		if (background == null)
		{
			return Color.TRANSPARENT;
		}
		return background.getColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_1);
	}

	public void setTi2DMatrix(Ti2DMatrix matrix) {
		applyTransform(matrix);
	}
	
	public Ti2DMatrix getTi2DMatrix() {
		return layoutParams.matrix;
	}
	
	public float getAnimatedRectFraction() {
		float result = 0.0f;
		if (layoutParams instanceof AnimationLayoutParams) {
			result = ((AnimationLayoutParams)layoutParams).animationFraction;
		}
		return result;
	}
	
	public void setAnimatedRectFraction(float fraction) {
		if (layoutParams instanceof AnimationLayoutParams) {
			((AnimationLayoutParams)layoutParams).animationFraction = fraction;
			View outerView =  getOuterView();
			outerView.setLayoutParams(layoutParams);
			ViewParent viewParent = outerView.getParent();
			if (outerView.getVisibility() == View.VISIBLE && viewParent instanceof View) {
				((View) viewParent).postInvalidate();
			}
		}
	}

	
	@SuppressWarnings({ "unchecked", "rawtypes", "null" })
	public void prepareAnimatorSet(TiAnimatorSet tiSet, List<Animator> list, List<Animator> listReverse,
			HashMap options) {		
		
		View view = getOuterView();
		((TiViewAnimator)tiSet).setViewProxy(proxy);
		((TiViewAnimator)tiSet).setView(view);
		boolean needsReverse = listReverse != null;
		KrollDict properties = proxy.getProperties();

		if (options.containsKey(TiC.PROPERTY_OPACITY)) {
			show();
			list.add(ObjectAnimator.ofFloat(this, "opacity",
					TiConvert.toFloat(options, TiC.PROPERTY_OPACITY, 1.0f)));
			if (needsReverse) {
				listReverse.add(ObjectAnimator.ofFloat(this, "opacity",
						TiConvert.toFloat(properties, TiC.PROPERTY_OPACITY, 1.0f)));
			}
		}

		if (options.containsKey(TiC.PROPERTY_BACKGROUND_COLOR)) {
//			if (!proxy.hasProperty(TiC.PROPERTY_BACKGROUND_COLOR)) {
//				Log.w(TAG, "Cannot animate view without a backgroundColor. View doesn't have that property. Using #00000000");
//				getNativeView().setBackgroundColor(Color.argb(0, 0, 0, 0));
//			}
			ObjectAnimator anim = ObjectAnimator.ofInt(this, "tiBackgroundColor", TiConvert.toColor(options, TiC.PROPERTY_BACKGROUND_COLOR));
			 anim.setEvaluator(new ArgbEvaluator());
			list.add(anim);
			if (needsReverse) {
				anim = ObjectAnimator.ofInt(this, "tiBackgroundColor", TiConvert.toColor(properties, TiC.PROPERTY_BACKGROUND_COLOR));
				anim.setEvaluator(new ArgbEvaluator());
				listReverse.add(anim);
			}
		}
		
		ViewParent parent = view.getParent();
		View parentView = null;

		if (parent instanceof View) {
			parentView = (View) parent;
		}
		
		if (!useCustomLayoutParams) {
	        AnimationLayoutParams animParams = new AnimationLayoutParams(layoutParams);
            animParams.animationFraction = 0.0f;
          //fillLayout will try to reset animationFraction, here we dont want that
            float oldAnimationFraction = animParams.animationFraction;
            if (TiConvert.fillLayout(TiConvert.toKrollDict(options), animParams, false) != 0) {
                animParams.startRect = new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
                animParams.animationFraction = oldAnimationFraction;
                
                setLayoutParams(animParams); //we need this because otherwise applying the matrix will override it :s
                view.setLayoutParams(animParams);
                ObjectAnimator anim = ObjectAnimator.ofFloat(this, "animatedRectFraction", 1.0f);
                list.add(anim);
                if (needsReverse) {
                    listReverse.add(ObjectAnimator.ofFloat(this, "animatedRectFraction", 0.0f));
                }
            }
		}
		
		if (options.containsKey(TiC.PROPERTY_TRANSFORM)) {
			Ti2DMatrix matrix = TiConvert.toMatrix(options, TiC.PROPERTY_TRANSFORM);
			if (parentView instanceof FreeLayout) {
				Ti2DMatrixEvaluator evaluator = new Ti2DMatrixEvaluator(view);
				ObjectAnimator anim = ObjectAnimator.ofObject(this, "ti2DMatrix", evaluator, matrix);
				list.add(anim);
				if (needsReverse) {
				    Ti2DMatrix reverseMatrix = getLayoutParams().matrix;
				    if (reverseMatrix == null) {
				        reverseMatrix = TiConvert.IDENTITY_MATRIX;
				    }
					listReverse.add(ObjectAnimator.ofObject(this, "ti2DMatrix", evaluator, reverseMatrix));
				}
			}
			else {
				DecomposedType decompose = matrix.getAffineTransform(view).decompose();
				List<PropertyValuesHolder> propertiesList = new ArrayList<PropertyValuesHolder>();
				propertiesList.add(PropertyValuesHolder.ofFloat("translationX", (float)decompose.translateX));
				propertiesList.add(PropertyValuesHolder.ofFloat("translationY", (float)decompose.translateY));
				propertiesList.add(PropertyValuesHolder.ofFloat("rotation", (float)(decompose.angle*180/Math.PI)));
				propertiesList.add(PropertyValuesHolder.ofFloat("scaleX", (float)decompose.scaleX));
				propertiesList.add(PropertyValuesHolder.ofFloat("scaleY", (float)decompose.scaleY));
				list.add(ObjectAnimator.ofPropertyValuesHolder(AnimatorProxy.NEEDS_PROXY ?AnimatorProxy.wrap(view) : view,propertiesList.toArray(new PropertyValuesHolder[0])));
				if (needsReverse) {
					matrix = TiConvert.toMatrix(properties, TiC.PROPERTY_TRANSFORM);
					decompose = matrix.getAffineTransform(view).decompose();
					propertiesList = new ArrayList<PropertyValuesHolder>();
					propertiesList.add(PropertyValuesHolder.ofFloat("translationX", (float)decompose.translateX));
					propertiesList.add(PropertyValuesHolder.ofFloat("translationY", (float)decompose.translateY));
					propertiesList.add(PropertyValuesHolder.ofFloat("rotation", (float)(decompose.angle*180/Math.PI)));
					propertiesList.add(PropertyValuesHolder.ofFloat("scaleX", (float)decompose.scaleX));
					propertiesList.add(PropertyValuesHolder.ofFloat("scaleY", (float)decompose.scaleY));
					listReverse.add(ObjectAnimator.ofPropertyValuesHolder(AnimatorProxy.NEEDS_PROXY ?AnimatorProxy.wrap(view) : view,propertiesList.toArray(new PropertyValuesHolder[0])));
				}
			}
		}
		view.postInvalidate();
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private class BlurTask extends AsyncTask< Object, Void, Bitmap >
	{
		KrollProxy proxy;
		HashMap options;
		KrollFunction callback;
		String[] properties;
		@Override
		protected Bitmap doInBackground(Object... params)
		{
			Bitmap bitmap = (Bitmap)params[0];
			proxy = (TiViewProxy)params[1];
			Rect rect = (Rect)params[2];
			options = (HashMap)params[3];
			bitmap = TiImageHelper.imageCropped(bitmap, new TiRect(rect));
			if (options == null) {
				options = new KrollDict();
			}

			if (options.containsKey("callback")) {
				callback = (KrollFunction) options.get("callback");
			}
			if (options.containsKey("properties")) {
				properties = TiConvert.toStringArray((Object[]) options.get("properties"));
			}
			
			bitmap = TiImageHelper.imageFiltered(bitmap, options).first;
			return bitmap;
		}
		/**
		 * Always invoked on UI thread.
		 */
		@Override
		protected void onPostExecute(Bitmap image)
		{
			TiBlob blob = TiBlob.blobFromObject(image);
			if (properties != null) {
				for (String prop : properties) {
					proxy.setPropertyAndFire(prop, blob);
				}
			}
			if (this.callback != null)  {
				KrollDict result = new KrollDict();
				if (image != null) {
					result.put("image", blob);
				}
				this.callback.callAsync(this.proxy.getKrollObject(), new Object[] { result });
			}
		}
	}
	
	public void blurBackground(final HashMap args)
	{
		final View outerView = getOuterView();
		TiViewProxy parentProxy = getParent();
		if (outerView != null && parentProxy != null) {	
			View parentView = parentProxy.getOuterView();
			if (parentView != null) {
		        final Bitmap bitmap = TiUIHelper.viewToBitmap(parentProxy.getProperties(), parentProxy.getOuterView());
				final KrollProxy proxyToUse = this.proxy;
		        boolean viewLaidOut = outerView.getWidth() != 0 && outerView.getHeight() != 0;
    	        if (!viewLaidOut) {
    	            ViewTreeObserver vto = outerView.getViewTreeObserver();
    	            vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

    	                @Override
    	                public void onGlobalLayout() {
    	                    ViewTreeObserver obs = outerView.getViewTreeObserver();
    	                    obs.removeOnGlobalLayoutListener(this);
    	                    Rect rect = new Rect(outerView.getLeft(), outerView.getTop(), outerView.getRight(), outerView.getBottom());
    	                    (new BlurTask()).execute(bitmap, proxyToUse, rect, args);
    	                }
    	            });
    	            parentView.requestLayout();
    	        }
    	        else {
    	            Rect rect = new Rect(outerView.getLeft(), outerView.getTop(), outerView.getRight(), outerView.getBottom());
                    (new BlurTask()).execute(bitmap, this.proxy, rect, args);
    	        }
			} 
		}
	}
	
	public void setCustomLayoutParams(ViewGroup.LayoutParams params) {
        useCustomLayoutParams = true;
        View view = getOuterView();
        if (view != null) {
            view.setLayoutParams(params);
        }
	}

    public void didRealize() {
        
    }
}

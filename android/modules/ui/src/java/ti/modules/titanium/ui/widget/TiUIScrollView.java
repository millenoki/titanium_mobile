/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.util.ArrayList;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiPoint;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import com.akylas.view.DualScrollView;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

@SuppressWarnings({"unchecked", "rawtypes"})
public class TiUIScrollView extends TiUIView
{
	public static final int TYPE_VERTICAL = 0;
	public static final int TYPE_HORIZONTAL = 1;

	private static final String TAG = "TiUIScrollView";
//	private int offsetX = 0, offsetY = 0;
	private boolean mScrollingEnabled = true;

    private Point mCurrentOffset = new Point();
    private float mZoomScale = 1.0f;
	private float mMinZoomScale = 1.0f;
	private float mMaxZoomScale = 1.0f;
	
    private boolean mAutoCenter = true;
    private boolean animating = false;
    private ScaleGestureDetector mScaleGestureDetector = null;
    private boolean mIsScaling = false;
	
	protected static final int TIFLAG_NEEDS_ZOOM               = 0x00000001;
    protected static final int TIFLAG_NEEDS_CONTENT_OFFSET     = 0x00000002;

	public class TiScrollViewLayout extends TiCompositeLayout
	{
		private static final int AUTO = Integer.MAX_VALUE;
		private int parentWidth = 0;
		private int parentHeight = 0;
		private boolean canCancelEvents = true;
		
	    protected TiDimension contentWidth = null;
	    protected TiDimension contentHeight = null;

		public TiScrollViewLayout(Context context)
		{
			super(context, TiUIScrollView.this);
		}

		public void setParentWidth(int width)
		{
			parentWidth = width;
		}

		public void setParentHeight(int height)
		{
			parentHeight = height;
		}

		public void setCanCancelEvents(boolean value)
		{
			canCancelEvents = value;
		}
		
		public void setClipChildren(boolean clipChildren) {
            super.setClipChildren(clipChildren);
            ((ViewGroup) nativeView).setClipChildren(clipChildren);
        }
        
        public void setClipToOutline(boolean clipChildren) {
            super.setClipToOutline(clipChildren);
            ((ViewGroup) nativeView).setClipToOutline(clipChildren);
        }
        
        private void onScaleChanged(float scale) {
            
            //we need to request a layout because the scroll size is updated there
            nativeView.requestLayout();
            
            if (proxy.hasListeners("scale", false)) {
                KrollDict data = new KrollDict();
                data.put("zoomScale", scale);
                proxy.fireEvent("scale", data, false, false);
            }
        }
        
        private class PointEvaluator implements TypeEvaluator<Point> {
            
            public PointEvaluator() {
            }
            
            public Point evaluate(float fraction, Point startValue,
                    Point endValue) {
                return new Point((int)(fraction*(endValue.x  - startValue.x) + startValue.x), 
                        (int)(fraction*(endValue.y  - startValue.y) + startValue.y));
            }

        }
        private final PointEvaluator pointEvaluator = new PointEvaluator();
        
        public float getZoomScale() {
            return getScaleX();
        }
        public void setZoomScale(final float zoom) {
            setZoomScale(zoom, 0, 0);
        }
        
        public void setZoomScale(final float zoom, final float pivotX, final float pivotY) {
            this.setPivotX(pivotX);
            this.setPivotY(pivotY);
            this.setScaleX(zoom);
            this.setScaleY(zoom);
        }
        
        public Point getContentOffset() {
//            final float currentZoom = getZoomScale();
            final int scrollX = nativeView.getScrollX();
            final int scrollY = nativeView.getScrollY();
            return new Point(scrollX, scrollY);
        }
        public void setContentOffset(Point p) {
            ((TiScrollView) nativeView).scrollTo(p.x, p.y);
        }
        
        public void setContentOffset(Point p, final boolean animated) {
            setZoomScale(mZoomScale, p, animated);
        }

        public void setZoomScale(float zoom, final Point point, final boolean animated) {
            zoom = Math.min(mMaxZoomScale, Math.max(zoom, mMinZoomScale));
            mZoomScale = zoom;
            final float currentZoomScale = getZoomScale();
            
            Point p = new Point(point.x, point.y);
            p.x *= zoom/currentZoomScale;
            p.y *= zoom/currentZoomScale;
            
            //let's clamp it
            final int width = nativeView.getMeasuredWidth();
            final int height = nativeView.getMeasuredHeight();
            final int maxW = (int) (getMeasuredWidth() * zoom);
            final int maxH = (int) (getMeasuredHeight() * zoom);
            
            p.x = Math.max(0, Math.min(maxW - width, p.x));
            p.y = Math.max(0, Math.min(maxH - height, p.y));
            
            if (animated) {
                animating = true;
                List<PropertyValuesHolder> propertiesList = new ArrayList<>();
                propertiesList.add(PropertyValuesHolder.ofObject("contentOffset", pointEvaluator, p));
                if (zoom != getZoomScale()) {
                    propertiesList.add(PropertyValuesHolder.ofFloat("zoomScale", zoom));
                }
                if (mAutoCenter && maxW < width) {
                    final float tx = (width - maxW) / 2;
                    propertiesList.add(PropertyValuesHolder.ofFloat("translationX", tx));
                } else {
                    propertiesList.add(PropertyValuesHolder.ofFloat("translationX", 0));
                }
                if (mAutoCenter && maxH < height) {
                   final float ty = (height - maxH) / 2;
                   propertiesList.add(PropertyValuesHolder.ofFloat("translationY", ty));
                } else {
                    propertiesList.add(PropertyValuesHolder.ofFloat("translationY", 0));
                }
                ObjectAnimator animatorSet = ObjectAnimator.ofPropertyValuesHolder(this,propertiesList.toArray(new PropertyValuesHolder[0]));
                animatorSet.setDuration(200);
                animatorSet.addUpdateListener(new AnimatorUpdateListener(){
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        onScaleChanged(getZoomScale());
                    }
                });
                animatorSet.addListener(new AnimatorListener() {
                    
                    @Override
                    public void onAnimationStart(Animator animation) {                        
                    }
                    
                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                    
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mZoomScale = getZoomScale();
                        onScaleChanged(mZoomScale);                        
                        animating = false;
                   }
                    
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mZoomScale = getZoomScale();
                        onScaleChanged(mZoomScale);                        
                        animating = false;
                    }
                });
                animatorSet.start();
            } else {
                
                if (mAutoCenter && maxW < width) {
                    setTranslationX((width - maxW) / 2);
                } else {
                    setTranslationX(0);
                }
                if (mAutoCenter && maxH < height) {
                    float ty = (height - maxH) / 2;
                    setTranslationY(ty);
                } else {
                    setTranslationY(0);
                }
                if (zoom != getZoomScale()) {
                    setZoomScale(zoom);
                    onScaleChanged(zoom);
                }
                setContentOffset(point);
            }
        }

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev)
		{
			// If canCancelEvents is false, then we want to prevent the scroll view from canceling the touch
			// events of the child view
			if (!canCancelEvents) {
				requestDisallowInterceptTouchEvent(true);
			}
			return super.dispatchTouchEvent(ev);
		}

		protected int getContentProperty(String property)
        {
            Object value = getProxy().getProperty(property);
            if (value != null) {
                if (value.equals(TiC.SIZE_AUTO)) {
                    return AUTO;
                } else {
                    int type = 0;
                    TiDimension dimension;
                    if (TiC.PROPERTY_CONTENT_HEIGHT.equals(property)) {
                        type = TiDimension.TYPE_HEIGHT;
                    } else if (TiC.PROPERTY_CONTENT_WIDTH.equals(property)) {
                        type = TiDimension.TYPE_WIDTH;
                    }
                    dimension = new TiDimension(value.toString(), type);
                    return dimension.getUnits() == TiDimension.COMPLEX_UNIT_AUTO ? AUTO : dimension.getAsPixels();
                }
            }
            return AUTO;
        }

		@Override
		protected int getWidthMeasureSpec(View child)
		{
			if (contentWidth == null || contentWidth.isUnitAuto()) {
				return MeasureSpec.UNSPECIFIED;
			} else {
				return super.getWidthMeasureSpec(child);
			}
		}

		@Override
		protected int getHeightMeasureSpec(View child)
		{
            if (contentHeight == null || contentHeight.isUnitAuto()) {
				return MeasureSpec.UNSPECIFIED;
			} else {
				return super.getHeightMeasureSpec(child);
			}
		}

		@Override
		protected int getMeasuredWidth(int maxWidth, int widthSpec)
		{
		    int theWidth;
		    if (contentWidth == null) {
		        theWidth = parentWidth;
            }
            else if (contentWidth.isUnitAuto()) {
			    theWidth = maxWidth;
            } else {	
                theWidth = contentWidth.getAsPixels(parentWidth, parentWidth);
            }
			if (theWidth > parentWidth) {
				return theWidth;
			} else {
				return resolveSize(theWidth, widthSpec);
			}
		}

		@Override
		protected int getMeasuredHeight(int maxHeight, int heightSpec)
		{
		    int theHeight;
		    if (contentHeight == null) {
		        theHeight = parentHeight;
		    }
		    else if (contentHeight.isUnitAuto()) {
                theHeight = maxHeight;
            } else {    
                theHeight = contentHeight.getAsPixels(parentHeight, parentHeight);
            }
			if (theHeight > parentHeight) {
				return theHeight;
			} else {
				return resolveSize(theHeight, heightSpec);
			}
		} 
		
        @Override
		protected int computeHorizontalScrollRange() {
	        return (int) (getWidth() * getZoomScale());
	    }
        @Override
        protected int computeVerticalScrollRange() {
            return (int) (getHeight() * getZoomScale());
        }
	}

	// same code, different super-classes
	public class TiScrollView extends DualScrollView
	{
		private TiScrollViewLayout layout;

		public TiScrollView(Context context)
		{
			super(context);
			setScrollBarStyle(SCROLLBARS_INSIDE_OVERLAY);
			setScrollContainer(true);

			layout = new TiScrollViewLayout(context);
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
			layout.setLayoutParams(params);
			super.addView(layout, params);
		}

		public TiScrollViewLayout getLayout()
		{
			return layout;
		}


		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_MOVE && !mScrollingEnabled) {
				return false;
			}
			boolean handled = false;
			if (mScaleGestureDetector != null) {
			    handled |= mScaleGestureDetector.onTouchEvent(event);
            }
			
			//There's a known Android bug (version 3.1 and above) that will throw an exception when we use 3+ fingers to touch the scrollview.
			//Link: http://code.google.com/p/android/issues/detail?id=18990
			try {
			    handled |= super.onTouchEvent(event);
				return handled;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		
		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
            if (mScrollingEnabled && isTouchEnabled) {
                boolean handled = false;
                if (mScaleGestureDetector != null) {
                    mScaleGestureDetector.onTouchEvent(event);
                }
                if (!mIsScaling) {
                    handled = super.onInterceptTouchEvent(event);
                }
				return handled;
			}

			return false;
		}
		
		@Override
		public void addView(View child, android.view.ViewGroup.LayoutParams params)
		{
			layout.addView(child, params);
		}

		@Override
		protected void onScrollChanged(int l, int t, int oldl, int oldt)
		{
			super.onScrollChanged(l, t, oldl, oldt);
			mCurrentOffset.set(l , t);
			if (hasListeners(TiC.EVENT_SCROLL)) {
				getProxy().fireEvent(TiC.EVENT_SCROLL, getContentOffset(), false, false);
			}
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
		{
			layout.setParentHeight(MeasureSpec.getSize(heightMeasureSpec));
			layout.setParentWidth(MeasureSpec.getSize(widthMeasureSpec));
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);

			// This is essentially doing the same logic as if you did setFillViewPort(true). In native Android, they
			// don't measure the child again if measured height of content view < scrollViewheight. But we want to do
			// this in all cases since we allow the content view height to be greater than the scroll view. We force
			// this to allow fill behavior: TIMOB-8243.
			if (getChildCount() > 0) {
				final View child = getChildAt(0);
                int width = getMeasuredWidth();
                int height = getMeasuredHeight();
				final FrameLayout.LayoutParams lp = (LayoutParams) child.getLayoutParams();

//				int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight(),
//					lp.width);
				
				width -= getPaddingLeft();
				width -= getPaddingRight();
				width = Math.max(child.getMeasuredWidth(), width);
	            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
               
				height -= getPaddingTop();
				height -= getPaddingBottom();

				// If we measure the child height to be greater than the parent height, use it in subsequent
				// calculations to make sure the children are measured correctly the second time around.
				height = Math.max(child.getMeasuredHeight(), height);
				int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
				child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
				
	            if (!animating && !handleZoomAndOffsetUpdates()) {
	                final int w = nativeView.getMeasuredWidth();
	                final int h = nativeView.getMeasuredHeight();
	                final float scale = getZoomScale();
	                final int maxW = (int) (layout.getMeasuredWidth() * scale);
	                final int maxH = (int) (layout.getMeasuredHeight() * scale);
	                if (mAutoCenter && maxW < w) {
	                    layout.setTranslationX((w - maxW) / 2);
	                } else {
	                    layout.setTranslationX(0);
	                }
	                if (mAutoCenter && maxH < h) {
	                    float ty = (h - maxH) / 2;
	                    layout.setTranslationY(ty);
	                } else {
	                    layout.setTranslationY(0);
	                }
	            }
			}
		}

         @Override
         public boolean dispatchTouchEvent(MotionEvent event) {
             if (touchPassThrough(this, event)) {
                 return false;
             }
             return super.dispatchTouchEvent(event);
         }

        public boolean canScroll(final int deltaX, final int deltaY) {
            final boolean canScrollHorizontal =
                    canScrollHorizontal() && computeHorizontalScrollRange() > computeHorizontalScrollExtent();
            final boolean canScrollVertical =
                    canScrollVertical() && computeVerticalScrollRange() > computeVerticalScrollExtent();
                    
            int newScrollX = getScrollX() + deltaX;
            int newScrollY = getScrollY() + deltaY;

            // Clamp values if at the limits and record
            final int left = 0;
            final int right = computeHorizontalScrollRange();
            final int top = 0;
            final int bottom = computeVerticalScrollRange();

            boolean clampedX = !canScrollHorizontal;
            if (newScrollX + getWidth() >= right) {
                clampedX = true;
            } else if (newScrollX < left) {
                clampedX = true;
            }

            boolean clampedY = !canScrollVertical;
            if (newScrollY + getHeight() >= bottom) {
                clampedY = true;
            } else if (newScrollY < top) {
                clampedY = true;
            }

            return !clampedX && !clampedY;
        }
        
        @Override
        protected int computeHorizontalScrollRange() {
            return (int) (super.computeHorizontalScrollRange() * mZoomScale);
        }
        
        @Override
        protected int computeVerticalScrollRange() {
            return (int) (super.computeVerticalScrollRange() * mZoomScale);
        }
	}
	
	@Override
	public ViewGroup getParentViewForChild()
	{
		return getLayout();
	}
	
	@Override
	protected View getTouchView()
	{
		return getLayout();		
	}

	public TiUIScrollView(TiViewProxy proxy)
	{
		// we create the view after the properties are processed
		super(proxy);
        getLayoutParams().sizeOrFillWidthEnabled = true;
        getLayoutParams().autoFillsHeight = true;
        getLayoutParams().sizeOrFillHeightEnabled = true;
		getLayoutParams().autoFillsWidth = true;
		TiScrollView view = new TiScrollView(proxy.getActivity());
		view.setShouldClamp(false);
		setNativeView(view);
	}
	
	public Object getContentOffset()
    {
	    if (nativeView == null) return proxy.getProperty(TiC.PROPERTY_CONTENT_OFFSET);
	    TiPoint point = new TiPoint(mCurrentOffset.x, mCurrentOffset.y);
	    return point.toDict();
    }

//    private void setContentOffset(final Point p, final boolean animated) {
//        if (animated) {
//            smoothScrollTo((int)(p.x*mZoomScale), (int) (p.y*mZoomScale));
//        }
//        else {
//            scrollTo((int)(p.x*mZoomScale), (int) (p.y*mZoomScale));
//        }
//    }
	
	public void setContentOffset(final Object value, final boolean animated)
	{
		if (nativeView == null) return;
		TiPoint point = TiConvert.toPoint(value);
		if (point != null) {
		    Point p = point.compute(nativeView.getWidth(), nativeView.getHeight());
		    getLayout().setContentOffset(p, animated);
		}
	}


	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_HORIZONTAL_WRAP:
            getLayout().setEnableHorizontalWrap(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_CONTENT_WIDTH:
            getLayout().contentWidth = TiConvert.toTiDimension(newValue, TiDimension.TYPE_WIDTH);
            break;
        case TiC.PROPERTY_CONTENT_HEIGHT:
            getLayout().contentHeight = TiConvert.toTiDimension(newValue, TiDimension.TYPE_HEIGHT);
            break;
        case TiC.PROPERTY_SCROLLING_ENABLED:
            mScrollingEnabled = TiConvert.toBoolean(newValue, true);
            break;
        case TiC.PROPERTY_SHOW_HORIZONTAL_SCROLL_INDICATOR:
            getNativeView().setHorizontalScrollBarEnabled(TiConvert.toBoolean(newValue, true));
            break;
        case TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR:
            getNativeView().setVerticalScrollBarEnabled(TiConvert.toBoolean(newValue, true));
            break;
        case TiC.PROPERTY_OVER_SCROLL_MODE:
            getNativeView().setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
            break;
        case TiC.PROPERTY_CAN_CANCEL_EVENTS:
            ((TiScrollView) getNativeView()).getLayout().setCanCancelEvents(TiConvert.toBoolean(newValue, true));
            break;
        case TiC.PROPERTY_CONTENT_OFFSET:
            mProcessUpdateFlags |= TIFLAG_NEEDS_CONTENT_OFFSET;
            break;
        case "minZoomScale":
            mMinZoomScale = TiConvert.toFloat(newValue, 1.0f);
            mProcessUpdateFlags |= TIFLAG_NEEDS_ZOOM;
            break;
        
        case "maxZoomScale":
            mMaxZoomScale = TiConvert.toFloat(newValue, 1.0f);
            mProcessUpdateFlags |= TIFLAG_NEEDS_ZOOM;
            break;
        
        case "zoomScale":
            mZoomScale = TiConvert.toFloat(newValue, 1.0f);
            mProcessUpdateFlags |= TIFLAG_NEEDS_ZOOM;
            break;
        
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
	
	private boolean handleZoomAndOffsetUpdates() {
	    if ((mProcessUpdateFlags & TIFLAG_NEEDS_ZOOM) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_ZOOM;
            if (mMinZoomScale != mMaxZoomScale) {
//                if (this.mScaleGestureDetector == null) {
//                    this.mScaleGestureDetector = new ScaleGestureDetector(getContext(),
//                            new OnScaleGestureListener());
//                    if (TiC.KIT_KAT_OR_GREATER) {
//                        this.mScaleGestureDetector.setQuickScaleEnabled(false);
//                    }
//                }
            }
            Point p = mCurrentOffset;
            if ((mProcessUpdateFlags & TIFLAG_NEEDS_CONTENT_OFFSET) != 0) {
                mProcessUpdateFlags &= ~TIFLAG_NEEDS_CONTENT_OFFSET;
                TiPoint point = TiConvert.toPoint(proxy.getProperty(TiC.PROPERTY_CONTENT_OFFSET));
                if (point != null) {
                    p = point.compute(nativeView.getWidth(), nativeView.getHeight());
                }
            }
            getLayout().setZoomScale(mZoomScale, p, false);
            return true;
        } else {
            if ((mProcessUpdateFlags & TIFLAG_NEEDS_CONTENT_OFFSET) != 0) {
                mProcessUpdateFlags &= ~TIFLAG_NEEDS_CONTENT_OFFSET;
                setContentOffset(proxy.getProperty(TiC.PROPERTY_CONTENT_OFFSET), false);
                return true;
            }
        }
	    return false;
	}
	
	@Override
    protected void didProcessProperties() {
        super.didProcessProperties();
        
        if (nativeView.getMeasuredWidth() != 0 &&
                nativeView.getMeasuredHeight() != 0) {
            handleZoomAndOffsetUpdates();
        }
        
    }

	public TiScrollViewLayout getLayout()
	{
		if (nativeView != null){
			return ((TiScrollView) nativeView).layout;
		} 
		return null;
	}
	
	@Override
	protected void setOnClickListener(View view)
	{
		View targetView = view;
		targetView = ((TiScrollView) nativeView).layout;
		super.setOnClickListener(targetView);
	}


	public boolean getScrollingEnabled()
	{
		return mScrollingEnabled;
	}

	public void scrollTo(int x, int y)
	{
		getNativeView().scrollTo(x, y);
		getNativeView().computeScroll();
	}

	public void smoothScrollTo(int x, int y)
	{
		((TiScrollView)getNativeView()).smoothScrollTo(x, y);
	}

	public void scrollToBottom()
	{
		View view = getNativeView();

		TiScrollView scrollView = (TiScrollView) view;
		scrollView.fullScroll(View.FOCUS_DOWN, false);
	}

	@Override
	public void add(TiUIView child, int index)
	{
		if (child.hWAccelerationDisabled()) {
			hardwareAccEnabled = false;
			disableHWAcceleration(getOuterView());	
		}
		super.add(child, index);

		if (getNativeView() != null) {
			getLayout().requestLayout();
			if (child.getNativeView() != null) {
				child.getNativeView().requestLayout();
			}
		}
	}

	@Override
	public void remove(TiUIView child)
	{
		if (child != null) {
			View cv = child.getOuterView();
			if (cv != null) {
				View nv = getLayout();
				if (nv instanceof ViewGroup) {
					((ViewGroup) nv).removeView(cv);
					children.remove(child);
				}
			}
		}
	}
	
	@Override
	public void resort()
	{
		View v = getLayout();
		if ( v instanceof TiCompositeLayout) {
			((TiCompositeLayout) v).resort();
		}
 	}

    public void setZoomScale(float zoom, TiPoint point, Boolean animated) {
        Point p = mCurrentOffset;
        if (point != null) {
            final int w = nativeView.getMeasuredWidth();
            final int h = nativeView.getMeasuredHeight();
            //we need to transform the point into contentOffset
            p = point.compute(w, h);
            final float scale = getZoomScale();
            final int maxW = (int) (getLayout().getMeasuredWidth() * scale);
            final int maxH = (int) (getLayout().getMeasuredHeight() * scale);
            
            if (mAutoCenter && maxW < w) {
                p.x -= (w - maxW) / 2;
            }
            if (mAutoCenter && maxH < h) {
                p.y -= (h - maxH) / 2;
            }
            
            p.x = (int) (((mCurrentOffset.x + p.x)*zoom/scale - w/2)*scale/zoom);
            p.y = (int) (((mCurrentOffset.y + p.y)*zoom/scale - h/2)*scale/zoom);
        }
        
        getLayout().setZoomScale(zoom, p, animated);
    }

    public float getZoomScale() {
        return mZoomScale;
    }
    
    private final class OnScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {
        private float firstSpan;
        private float currentScale;
        
        // When distinguishing twofingertap and pinch events,
        // minimum motion (in pixels)
        // to qualify as a scale event.
        private static final float SCALE_THRESHOLD = 6.0f;


        @Override
        public boolean onScaleBegin(
                ScaleGestureDetector detector) {
            firstSpan = detector.getCurrentSpan() == 0 ? 1 : detector.getCurrentSpan();
            currentScale = 1.0f;
           return true;
        }
        
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float delta = detector.getCurrentSpan() - firstSpan;
        
            if (!mIsScaling && Math.abs(delta) > SCALE_THRESHOLD) {
                mIsScaling = true;
                cancelParentGestures();
            }
            currentScale *= detector.getScaleFactor();
            if (mIsScaling) {
//                float touchX = detector.getFocusX();
//                float touchY = detector.getFocusY();
                
                Log.d(TAG, "ScaleFactor " + currentScale);
                getLayout().setZoomScale(mZoomScale*currentScale);
            }
            return mIsScaling;
        }
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (mIsScaling) {
                mIsScaling = false;
                float newZoom = mZoomScale*currentScale;
                float clampedNewZoom = Math.min(mMaxZoomScale, Math.max(newZoom, mMinZoomScale));
                if (newZoom != mZoomScale) {
                    if (clampedNewZoom == newZoom) {
                        mZoomScale = clampedNewZoom;
                    } else {
                        getLayout().setZoomScale(newZoom, mCurrentOffset, true);
                    }
                }
            }
        }
    }
}

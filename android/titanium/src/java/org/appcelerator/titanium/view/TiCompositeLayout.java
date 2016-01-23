/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.view;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiLaunchActivity;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.MotionEvent;

/**
 * Base layout class for all Titanium views.
 */
public class TiCompositeLayout extends FreeLayout implements
		OnHierarchyChangeListener {
	/**
	 * Supported layout arrangements
	 * 
	 * @module.api
	 */
	public enum LayoutArrangement {
		/**
		 * The default Titanium layout arrangement.
		 */
		DEFAULT,
		/**
		 * The layout arrangement for Views and Windows that set layout:
		 * "vertical".
		 */
		VERTICAL,
		/**
		 * The layout arrangement for Views and Windows that set layout:
		 * "horizontal".
		 */
		HORIZONTAL
	}

	protected static final String TAG = "TiCompositeLayout";

	public static final int NOT_SET = Integer.MIN_VALUE;

	private TreeSet<View> viewSorter;
	private boolean needsSort;
	protected LayoutArrangement arrangement;

	// Used by horizonal arrangement calculations
	private int horizontalLayoutTopBuffer = 0;
	private int horizontalLayoutCurrentLeft = 0;
	private int horizontalLayoutLineHeight = 0;
	private boolean enableHorizontalWrap = false;
	private int horizontalLayoutLastIndexBeforeWrap = 0;
	private int horiztonalLayoutPreviousRight = 0;

	private WeakReference<TiUIView> view;
	private static final int HAS_SIZE_FILL_CONFLICT = 1;
	private static final int NO_SIZE_FILL_CONFLICT = 2;

	
	private boolean mInterTouchPassThrough = false;

	// We need these two constructors for backwards compatibility with modules

	/**
	 * Constructs a new TiCompositeLayout object.
	 * 
	 * @param context
	 *            the associated context.
	 * @module.api
	 */
	public TiCompositeLayout(Context context) {
		this(context, LayoutArrangement.DEFAULT, null);
	}

	/**
	 * Constructs a new TiCompositeLayout object.
	 * 
	 * @param context
	 *            the associated context.
	 * @param arrangement
	 *            the associated LayoutArrangement
	 * @module.api
	 */
	public TiCompositeLayout(Context context, LayoutArrangement arrangement) {
		this(context, LayoutArrangement.DEFAULT, null);
	}

	public TiCompositeLayout(Context context, AttributeSet set) {
		this(context, LayoutArrangement.DEFAULT, null);
	}

	/**
	 * Constructs a new TiCompositeLayout object.
	 * 
	 * @param context
	 *            the associated context.
	 * @param proxy
	 *            the associated proxy.
	 */
	public TiCompositeLayout(Context context, TiUIView view) {
		this(context, LayoutArrangement.DEFAULT, view);
	}

	/**
	 * Constructs a new TiCompositeLayout object.
	 * 
	 * @param context
	 *            the associated context.
	 * @param arrangement
	 *            the associated LayoutArrangement
	 * @param proxy
	 *            the associated proxy.
	 */
	public TiCompositeLayout(Context context, LayoutArrangement arrangement,
			TiUIView view) {
		super(context);
		this.arrangement = arrangement;
		this.viewSorter = new TreeSet<View>(new Comparator<View>() {

			public int compare(View o1, View o2) {
				TiCompositeLayout.LayoutParams p1 = (TiCompositeLayout.LayoutParams) o1
						.getLayoutParams();
				TiCompositeLayout.LayoutParams p2 = (TiCompositeLayout.LayoutParams) o2
						.getLayoutParams();

				int result = 0;

				if (p1.optionZIndex != NOT_SET && p2.optionZIndex != NOT_SET) {
					if (p1.optionZIndex < p2.optionZIndex) {
						result = -1;
					} else if (p1.optionZIndex > p2.optionZIndex) {
						result = 1;
					}
				} else if (p1.optionZIndex != NOT_SET) {
					if (p1.optionZIndex < 0) {
						result = -1;
					}
					if (p1.optionZIndex > 0) {
						result = 1;
					}
				} else if (p2.optionZIndex != NOT_SET) {
					if (p2.optionZIndex < 0) {
						result = 1;
					}
					if (p2.optionZIndex > 0) {
						result = -1;
					}
				}

				if (result == 0) {
					if (p1.index < p2.index) {
						result = -1;
					} else if (p1.index > p2.index) {
						result = 1;
					} else {
						Log.w(TAG, "Ambiguous Z-Order");
						// throw new IllegalStateException("Ambiguous Z-Order");
					}
				}

				return result;
			}
		});

		setNeedsSort(true);
		setOnHierarchyChangeListener(this);
		setView(view);
	}

//	private String viewToString(View view) {
//		return view.getClass().getSimpleName() + "@"
//				+ Integer.toHexString(view.hashCode());
//	}

	public void resort() {
		if (getVisibility() == View.GONE)
			return;
		setNeedsSort(true);
		requestLayout();
		invalidate();
	}

	public void onChildViewAdded(View parent, View child) {
		setNeedsSort(true);
//		if (parent != null && child != null) {
//			Log.d(TAG, "Attaching: " + viewToString(child) + " to "
//					+ viewToString(parent), Log.DEBUG_MODE);
//		}
	}
	
	public void setInternalTouchPassThrough(boolean value) {
	    mInterTouchPassThrough = value;
	}

	public void onChildViewRemoved(View parent, View child) {
		setNeedsSort(true);
//		if (Log.isDebugModeEnabled()) {
//			Log.d(TAG, "Removing: " + viewToString(child) + " from "
//					+ viewToString(parent), Log.DEBUG_MODE);
//		}
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof TiCompositeLayout.LayoutParams;
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams();
	}

	protected static int getViewWidthPadding(View child, LayoutParams params, View parent) {
		int padding = 0;
		padding += Math.abs(getLayoutOptionAsPixelsNoAnim(params.optionLeft, TiDimension.TYPE_LEFT, params, parent));
		padding += Math.abs(getLayoutOptionAsPixelsNoAnim(params.optionRight, TiDimension.TYPE_RIGHT, params, parent));
		return padding;
	}

	protected static int getViewHeightPadding(View child, LayoutParams params, View parent) {
		int padding = 0;
		padding += Math.abs(getLayoutOptionAsPixels(params.optionTop, TiDimension.TYPE_TOP, params, parent));
		padding += Math.abs(getLayoutOptionAsPixels(params.optionBottom, TiDimension.TYPE_BOTTOM, params, parent));
		return padding;
	}
	
	private boolean viewShouldFillHorizontalLayout(View view, LayoutParams params)
	{
        if (view.getVisibility() == View.GONE) return false;
        if (params.fullscreen == true) return true;
		if (params.sizeOrFillWidthEnabled == false) return false;
		if (params.autoFillsWidth) return true;
		boolean borderView = (view instanceof TiBorderWrapperView);
		if (view instanceof ViewGroup) {
			ViewGroup viewGroup = (ViewGroup)view;
	        for (int i=0; i<viewGroup.getChildCount(); i++) {
	            View child = viewGroup.getChildAt(i);
	        	ViewGroup.LayoutParams childParams = borderView?params:child.getLayoutParams();
	        	if (childParams instanceof LayoutParams && viewShouldFillHorizontalLayout(child, (LayoutParams) childParams)) {
	        		return true;
	        	}
	        }
		}
		return false;
	}
	
	private boolean viewShouldFillVerticalLayout(View view, LayoutParams params)
	{
        if (view.getVisibility() == View.GONE) return false;
		if (params.fullscreen == true) return true;
		if (params.sizeOrFillHeightEnabled == false) return false;
		if (params.autoFillsHeight) return true;
        boolean borderView = (view instanceof TiBorderWrapperView);
		if (view instanceof ViewGroup) {
			ViewGroup viewGroup = (ViewGroup)view;
	        for (int i=0; i<viewGroup.getChildCount(); i++) {
	            View child = viewGroup.getChildAt(i);
	        	ViewGroup.LayoutParams childParams = borderView?params:child.getLayoutParams();
	        	if (childParams instanceof LayoutParams && viewShouldFillVerticalLayout(child, (LayoutParams) childParams)) {
	        		return true;
	        	}
	        }
		}
		return false;
	}
	
	public static TiCompositeLayout.LayoutParams getChildParams(View child) {
        TiCompositeLayout.LayoutParams params;
        if (child.getLayoutParams() instanceof TiCompositeLayout.LayoutParams) {
            params = (TiCompositeLayout.LayoutParams) child
                    .getLayoutParams();
        }
        else {
            params = new TiCompositeLayout.LayoutParams(child.getLayoutParams());
            child.setLayoutParams(params);
        }
        return params;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	    final int childCount = getChildCount();
		int maxWidth = 0;
		int maxHeight = 0;
		final int wFromSpec = MeasureSpec.getSize(widthMeasureSpec);
		final int hFromSpec = MeasureSpec.getSize(heightMeasureSpec);
        final int wSuggested = getSuggestedMinimumWidth();
        final int hSuggested = getSuggestedMinimumHeight();
        final int w = Math.max(wFromSpec, wSuggested);
        final int h = Math.max(hFromSpec, hSuggested);
        
        final boolean horizontal = isHorizontalArrangement();
        final boolean vertical = isVerticalArrangement();
        
		
		if (childCount > 0) {
		    
	        int wMode = MeasureSpec.getMode(widthMeasureSpec);
	        int hMode = MeasureSpec.getMode(heightMeasureSpec);
		 // Used for horizontal layout only
	        int horizontalRowWidth = 0;
	        int horizontalRowHeight = 0;

	        // we need to first get the list/number of autoFillsWidth views
	        List<View> autoFillWidthViews = new ArrayList<View>();
	        List<View> autoFillHeightViews = new ArrayList<View>();

	        
	        boolean horizontalNoWrap = horizontal && !enableHorizontalWrap;
	        boolean horizontalWrap = horizontal && enableHorizontalWrap;
	        float autoFillWidthTotalWeight = 0;
	        float autoFillHeightTotalWeight = 0;
	        for (int i = 0; i < childCount; i++) {
	            View child = getChildAt(i);
	            if (child.getVisibility() == View.GONE) {
	                continue;
	            }
	            TiCompositeLayout.LayoutParams params = getChildParams(child);
	            if (params.ignoreInLayout) {
	                continue;
	            }
	            Boolean needsProcessing = true;
	            if (horizontalNoWrap && viewShouldFillHorizontalLayout(child, params)) {
	                autoFillWidthViews.add(child);
	                autoFillWidthTotalWeight += params.weight;
	                needsProcessing = false;
	            }
	            if ((vertical || horizontalWrap) && viewShouldFillVerticalLayout(child, params)) {
	                autoFillHeightViews.add(child);
	                autoFillHeightTotalWeight += params.weight;
	                needsProcessing = false;
	            }

	            if (!needsProcessing)
	                continue;
	            
	            int widthPadding = getViewWidthPadding(child, params, this);
	            int heightPadding = getViewHeightPadding(child, params, this);
	            constrainChild(child, params, horizontalNoWrap?(w - horizontalRowWidth):w, wMode, h, hMode, widthPadding, heightPadding);

	            int childWidth = child.getMeasuredWidth() + widthPadding;
	            int childHeight = child.getMeasuredHeight() + heightPadding;

	            if (horizontal) {
	                if (enableHorizontalWrap) {

	                    if ((horizontalRowWidth + childWidth) > w) {
	                        horizontalRowWidth = childWidth;
	                        maxHeight += horizontalRowHeight;
	                        horizontalRowHeight = childHeight;

	                    } else {
	                        horizontalRowWidth += childWidth;
	                        maxWidth = Math.max(maxWidth, horizontalRowWidth);
	                    }

	                } else {

	                    // For horizontal layout without wrap, just keep on adding
	                    // the widths since it doesn't wrap
	                    maxWidth += childWidth;
	                }
	                horizontalRowHeight = Math
	                        .max(horizontalRowHeight, childHeight);

	            } else {
	                if (!params.widthMatchParent) {
	                    maxWidth = Math.max(maxWidth, childWidth);
	                }

	                if (vertical) {
	                    maxHeight += childHeight;
	                } else if (!params.heightMatchParent){
	                    maxHeight = Math.max(maxHeight, childHeight);
	                }
	            }
	        }
	        int countFillWidth = autoFillWidthViews.size() ;
	        if (countFillWidth > 0) {
	            float counter = 0;
	            for (int i = 0; i < countFillWidth; i++) {
	                View child = autoFillWidthViews.get(i);
	                TiCompositeLayout.LayoutParams params = (TiCompositeLayout.LayoutParams) child
	                        .getLayoutParams();
	                final float weight = params.weight;
	                final int childW = (int) (Math.max(0, w - maxWidth)*weight / (autoFillWidthTotalWeight - counter));
	                counter += weight;
	                
	                final int widthPadding = getViewWidthPadding(child, params, this);
	                final int heightPadding = getViewHeightPadding(child, params, this);
	                constrainChild(child, params, childW, wMode, h, hMode, widthPadding, heightPadding);
	                final int childWidth = child.getMeasuredWidth() + widthPadding;
	                final int childHeight = child.getMeasuredHeight() + heightPadding;
	                
	                maxWidth += childWidth;
	                horizontalRowHeight = Math
	                        .max(horizontalRowHeight, childHeight);
	            }
	        }

	        int countFillHeight = autoFillHeightViews.size() ;
	        if (countFillHeight > 0) {
	            float counter = 0;
	            for (int i = 0; i < countFillHeight; i++) {
	                View child = autoFillHeightViews.get(i);
	                TiCompositeLayout.LayoutParams params = (TiCompositeLayout.LayoutParams) child
	                        .getLayoutParams();
	                final float weight = params.weight;
	                final int childH = (int) ((h - maxHeight)*weight / (autoFillHeightTotalWeight - counter));
	                counter += weight;

	                final int widthPadding = getViewWidthPadding(child, params, this);
	                final int heightPadding = getViewHeightPadding(child, params, this);
	                constrainChild(child, params, w, wMode, childH, hMode, widthPadding, heightPadding);
	                final int childWidth = child.getMeasuredWidth() + widthPadding;
	                final int childHeight = child.getMeasuredHeight() + heightPadding;
	                
	                maxHeight += childHeight;
	                maxWidth = Math.max(maxWidth, childWidth);
	            }
	        }

	        // Add height for last row in horizontal layout
	        if (horizontal) {
	            maxHeight += horizontalRowHeight;
	        }
		}

		

		// account for padding
		maxWidth += getPaddingLeft() + getPaddingRight();
		maxHeight += getPaddingTop() + getPaddingBottom();
		
		//if we are in a borderView our layoutParams are not the ones we wnat to look at
		ViewGroup.LayoutParams params = getTiLayoutParams();
		LayoutParams p = (params instanceof LayoutParams)?(LayoutParams)params:null;
		if (p != null) {
			//if we are fill we need to fill ….
			if (p.fullscreen || p.optionWidth == null && p.autoFillsWidth) {
				maxWidth = Math.max(maxWidth, w);
			}
			if (p.fullscreen || p.optionHeight == null && p.autoFillsHeight) {
		        maxHeight = Math.max(maxHeight, h);
			}
		}
		
		
		// check minimums
		maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
		maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
		
		int measuredWidth = getMeasuredWidth(maxWidth, widthMeasureSpec);
		int measuredHeight = getMeasuredHeight(maxHeight, heightMeasureSpec);

		setMeasuredDimension(measuredWidth, measuredHeight);
//        applyAnimationSize(this, p, false);
	}
	
	
	public ViewGroup.LayoutParams getTiLayoutParams() {
	    TiUIView view = getView();
        if (view != null)
        {
            return view.getLayoutParams();
        }
        return super.getLayoutParams();
    }
	
	
	protected void applyAnimationSize(final View child, final LayoutParams p, final boolean shouldMeasure) {
	    
	    if (p instanceof AnimationLayoutParams) {
	        int measuredWidth = child.getMeasuredWidth();
	        int measuredHeight = child.getMeasuredHeight();
            AnimationLayoutParams animP = (AnimationLayoutParams) p;
            float fraction = animP.animationFraction;
            if (fraction < 1.0f) {
                Rect startRect = animP.startRect;
                if (startRect != null) {
                    if (fraction == 0.0f) {
                        animP.finalWidth = measuredWidth;
                        animP.finalHeight = measuredHeight;
                    }

                    measuredWidth = (int) Math.ceil(measuredWidth * fraction + (1 - fraction)
                            * startRect.width());
                    int wMode = MeasureSpec.makeMeasureSpec(measuredWidth,
                            MeasureSpec.EXACTLY);
                
                    measuredHeight = (int) Math.ceil(measuredHeight * fraction + (1 - fraction)
                        * startRect.height());
                    int hMode = MeasureSpec.makeMeasureSpec(measuredHeight,
                            MeasureSpec.EXACTLY);
                    if (!shouldMeasure && child instanceof TiCompositeLayout) {
                        ((TiCompositeLayout)child).setMeasuredDimension(measuredWidth, measuredHeight);
                    } else {
                        child.measure(wMode, hMode);
                    }
               }
            }
        }
	}
	
	protected void measureChild(final View child, final LayoutParams p, int wMode,
 int hMode, int widthPadding, int heightPadding,
            final int parentWidth, final int parentHeight) {

        if (p.fullscreen) {
            wMode = MeasureSpec.makeMeasureSpec(parentWidth,
                    MeasureSpec.EXACTLY);
            hMode = MeasureSpec.makeMeasureSpec(parentHeight,
                    MeasureSpec.EXACTLY);
        }
        child.measure(wMode, hMode);
        int measuredWidth = child.getMeasuredWidth();
        int measuredHeight = child.getMeasuredHeight();

        boolean needsRecompute = false;
        if (p.heightMatchWidth) {
            measuredHeight = measuredWidth;
            hMode = MeasureSpec.makeMeasureSpec(measuredHeight,
                    MeasureSpec.EXACTLY);
            needsRecompute = true;
        } else if (p.widthMatchHeight) {
            measuredWidth = measuredHeight;
            wMode = MeasureSpec.makeMeasureSpec(measuredWidth,
                    MeasureSpec.EXACTLY);
            needsRecompute = true;
        }
        
        if (p.sizeRatio > 0) {
            if (!p.widthDefined) {
                measuredWidth = (int) (measuredHeight*p.sizeRatio);
                wMode = MeasureSpec.makeMeasureSpec(measuredWidth,
                        MeasureSpec.EXACTLY);
                needsRecompute = true;
            } else if(!p.heightDefined) {
                measuredHeight = (int) (measuredWidth*p.sizeRatio);
                hMode = MeasureSpec.makeMeasureSpec(measuredHeight,
                        MeasureSpec.EXACTLY);
                needsRecompute = true;
            }
        }


        if (p.maxWidth != null || p.minWidth != null) {
            int minMeasuredWidth = measuredWidth;
            if (p.minWidth != null) {
                minMeasuredWidth = Math.max(minMeasuredWidth,
                        p.minWidth.getAsPixels(parentWidth, parentHeight));
            }
            if (p.maxWidth != null) {
                minMeasuredWidth = Math.min(minMeasuredWidth,
                        p.maxWidth.getAsPixels(parentWidth, parentHeight));
            }
            if (minMeasuredWidth != measuredWidth) {
                wMode = MeasureSpec.makeMeasureSpec(minMeasuredWidth,
                        MeasureSpec.EXACTLY);
                measuredWidth = getMeasuredWidth(minMeasuredWidth, wMode);
                needsRecompute = true;
            }
        }

        if (p.maxHeight != null || p.minHeight != null) {
            int minMeasuredHeight = measuredHeight;
            if (p.minHeight != null)
                minMeasuredHeight = Math.max(minMeasuredHeight,
                        p.minHeight.getAsPixels(parentWidth, parentHeight));
            if (p.maxHeight != null)
                minMeasuredHeight = Math.min(minMeasuredHeight,
                        p.maxHeight.getAsPixels(parentWidth, parentHeight));
            if (minMeasuredHeight != measuredHeight) {
                hMode = MeasureSpec.makeMeasureSpec(minMeasuredHeight,
                        MeasureSpec.EXACTLY);
                measuredHeight = getMeasuredHeight(minMeasuredHeight, hMode);
                needsRecompute = true;
            }
        }
        if (needsRecompute) {
            needsRecompute = false;
            child.measure(wMode, hMode);
            measuredWidth = child.getMeasuredWidth();
            measuredHeight = child.getMeasuredHeight();
        }
        // if (!(child instanceof TiCompositeLayout)) {
        applyAnimationSize(child, p, true);
        // }

    }
	
    public static int getChildMeasureSpec(int size, int mode, int padding, int childDimension) {
        return ViewGroup.getChildMeasureSpec(
                MeasureSpec.makeMeasureSpec(size, mode), padding,
                childDimension);
    }
	
    protected void constrainChild(View child, View parent, LayoutParams p, int width, int wMode, int height,
            int hMode, int widthPadding, int heightPadding) {
        if (!p.fullscreen && !p.notTiLayout) {
            int sizeFillConflicts[] = { NOT_SET, NOT_SET };
            boolean checkedForConflict = false;
    
            // If autoFillsWidth is false, and optionWidth is null, then we use size
            // behavior.
            int childDimension = LayoutParams.WRAP_CONTENT;
            if (p.optionWidth != null) {
                childDimension = p.optionWidth.getAsPixels(width, height);
            } else {
                if (p.autoFillsWidth) {
                    childDimension = LayoutParams.MATCH_PARENT;
                } else if (!p.sizeOrFillWidthEnabled) {
                    TiDimension left = p.optionLeft;
                    TiDimension centerX = p.optionCenterX;
                    TiDimension right = p.optionRight;
                    if (left != null) {
                        if (centerX != null) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        } else if (right != null) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        }
                    } else if (centerX != null && right != null) {
                        childDimension = LayoutParams.MATCH_PARENT;
                    }
                    else {
                        // Look for sizeFill conflicts
                        hasSizeFillConflict(child, sizeFillConflicts, true);
                        checkedForConflict = true;
                        if (sizeFillConflicts[0] == HAS_SIZE_FILL_CONFLICT) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        }
                    }
                    
                }
            }

            wMode = getChildMeasureSpec(width, wMode, widthPadding, childDimension);
            
            // If autoFillsHeight is false, and optionHeight is null, then we use
            // size behavior.
            childDimension = LayoutParams.WRAP_CONTENT;
            if (p.optionHeight != null) {
                childDimension = p.optionHeight.getAsPixels(width, height);
            } else {
                // If we already checked for conflicts before, we don't need to
                // again
                if (p.autoFillsHeight
                        || (checkedForConflict && sizeFillConflicts[1] == HAS_SIZE_FILL_CONFLICT)) {
                    childDimension = LayoutParams.MATCH_PARENT;
                } else if (!p.sizeOrFillHeightEnabled) {
                    TiDimension top = p.optionTop;
                    TiDimension centerY = p.optionCenterY;
                    TiDimension bottom = p.optionBottom;
                    if (top != null) {
                        if (centerY != null) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        } else if (bottom != null) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        }
                    } else if (centerY != null && bottom != null) {
                        childDimension = LayoutParams.MATCH_PARENT;
                    }
                    else if (!checkedForConflict){
                        hasSizeFillConflict(child, sizeFillConflicts, true);
                        if (sizeFillConflicts[1] == HAS_SIZE_FILL_CONFLICT) {
                            childDimension = LayoutParams.MATCH_PARENT;
                        }
                    }
                }
            }
            hMode = getChildMeasureSpec(height, hMode, heightPadding, childDimension);
        }
        measureChild(child, p, wMode, hMode, widthPadding, heightPadding, width, height);
    }
	protected void constrainChild(View child, LayoutParams p, int width, int wMode, int height,
			int hMode, int widthPadding, int heightPadding) {
        constrainChild(child, this, p, width, wMode, height, hMode, widthPadding, heightPadding);
	}
	
	public void constrainChild(View child, View parent, int widthMeasureSpec, int heightMeasureSpec) {
        TiCompositeLayout.LayoutParams params = getChildParams(child);
	    int widthPadding = getViewWidthPadding(child, params, parent);
        int heightPadding = getViewHeightPadding(child, params, parent);
        int wFromSpec = MeasureSpec.getSize(widthMeasureSpec);
        int hFromSpec = MeasureSpec.getSize(heightMeasureSpec);
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        constrainChild(child, parent, params, wFromSpec, wMode, hFromSpec, hMode, widthPadding, heightPadding);

	}

	// Try to calculate width from pins, if we couldn't calculate from pins or
	// we don't need to, then return the
	// measured width
//	private int calculateWidthFromPins(LayoutParams params, int parentLeft,
//			int parentRight, int parentWidth, int measuredWidth, boolean canResizeFill) {
//		if (params.fullscreen) {
//			return measuredWidth;
//		}
//		int width = measuredWidth;
//
//		if (params.optionWidth != null || params.sizeOrFillWidthEnabled) {
//			if (canResizeFill && params.sizeOrFillWidthEnabled && params.autoFillsWidth) {
//				return parentWidth - getLayoutOptionAsPixels(params.optionRight, TiDimension.TYPE_RIGHT, params, this) -
//						getLayoutOptionAsPixels(params.optionLeft, TiDimension.TYPE_LEFT , params, this);
//			}
//			else 
//				return width;
//		}
//
//		TiDimension left = params.optionLeft;
//		TiDimension centerX = params.optionCenterX;
//		TiDimension right = params.optionRight;
//
//		if (left != null) {
//			if (centerX != null) {
//				width = (getLayoutOptionAsPixels(centerX, TiDimension.TYPE_CENTER_X, params, this) -
//						getLayoutOptionAsPixels(left, TiDimension.TYPE_LEFT, params, this) - parentLeft) * 2;
//			} else if (right != null) {
//				width = parentWidth - getLayoutOptionAsPixels(right, TiDimension.TYPE_RIGHT, params, this) -
//						getLayoutOptionAsPixels(left, TiDimension.TYPE_LEFT, params, this);
//			}
//		} else if (centerX != null && right != null) {
//			width =  (parentRight - getLayoutOptionAsPixels(right, TiDimension.TYPE_RIGHT, params, this) -
//					getLayoutOptionAsPixels(centerX, TiDimension.TYPE_CENTER_X , params, this)) * 2;
//		}
//		return width;
//	}

	// Try to calculate height from pins, if we couldn't calculate from pins or
	// we don't need to, then return the
	// measured height
//	private int calculateHeightFromPins(LayoutParams params, int parentTop,
//			int parentBottom, int parentHeight, int measuredHeight, boolean canResizeFill) {
//		if (params.fullscreen) {
//			return measuredHeight;
//		}
//		int height = measuredHeight;
//
//		// Return if we don't need undefined behavior
//		if (params.optionHeight != null || params.sizeOrFillHeightEnabled) {
//			if (canResizeFill && params.sizeOrFillHeightEnabled && params.autoFillsHeight) {
//				return parentHeight - getLayoutOptionAsPixels(params.optionTop, TiDimension.TYPE_TOP, params, this) -
//						getLayoutOptionAsPixels(params.optionBottom, TiDimension.TYPE_BOTTOM , params, this);
//			}
//			else 
//				return height;
//		}
//
//		TiDimension top = params.optionTop;
//		TiDimension centerY = params.optionCenterY;
//		TiDimension bottom = params.optionBottom;
//
//		if (top != null) {
//			if (centerY != null) {
//				height = (getLayoutOptionAsPixels(centerY, TiDimension.TYPE_CENTER_Y, params, this) -
//						getLayoutOptionAsPixels(top, TiDimension.TYPE_TOP, params, this) - parentTop) * 2;
//			} else if (bottom != null) {
//				height = parentHeight - getLayoutOptionAsPixels(top, TiDimension.TYPE_TOP, params, this) -
//						getLayoutOptionAsPixels(bottom, TiDimension.TYPE_BOTTOM,  params, this);
//			}
//		} else if (centerY != null && bottom != null) {
//			height =  (parentBottom - getLayoutOptionAsPixels(bottom, TiDimension.TYPE_BOTTOM, params, this) -
//					getLayoutOptionAsPixels(centerY, TiDimension.TYPE_CENTER_Y , params, this)) * 2;
//		}
//
//		return height;
//	}
	
	protected static int getMeasuredWidthStatic(int maxWidth, int widthSpec) {
        return resolveSize(maxWidth, widthSpec);
    }

    protected static int getMeasuredHeightStatic(int maxHeight, int heightSpec) {
        return resolveSize(maxHeight, heightSpec);
    }

	protected int getMeasuredWidth(int maxWidth, int widthSpec) {
		return getMeasuredWidthStatic(maxWidth, widthSpec);
	}

	protected int getMeasuredHeight(int maxHeight, int heightSpec) {
		return getMeasuredWidthStatic(maxHeight, heightSpec);
	}

	public int getChildSize(final View child, final int index, final  TiCompositeLayout.LayoutParams params,
	        final int left, final int top, final int bottom, final int right, int currentHeight,
	        final int[] horizontal, final int[] vertical, final boolean firstVisibleChild) {

		if (child.getVisibility() == View.GONE)
			return currentHeight;


		final int childMeasuredHeight = child.getMeasuredHeight();
		final int childMeasuredWidth = child.getMeasuredWidth();
        
        final boolean horizontalArr = isHorizontalArrangement();
        final boolean verticalArr = isVerticalArrangement();

		if (horizontalArr) {
			if (firstVisibleChild) {
				horizontalLayoutCurrentLeft = left;
				horizontalLayoutLineHeight = 0;
				horizontalLayoutTopBuffer = 0;
				horizontalLayoutLastIndexBeforeWrap = 0;
				horiztonalLayoutPreviousRight = 0;
				updateRowForHorizontalWrap(right, index);
			}
			computeHorizontalLayoutPosition(params, childMeasuredWidth,
			        childMeasuredHeight, right, top, bottom, horizontal,
					vertical, index);
		} else {
			computePosition(this, params, params.optionLeft, params.optionCenterX, params.optionWidth,
						params.optionRight, childMeasuredWidth, left, right,
						horizontal, false);
			
			if (verticalArr) {
				computeVerticalLayoutPosition(currentHeight, params.optionTop,
					childMeasuredHeight, top, vertical, bottom, params);
			} else {
				computePosition(this, params, params.optionTop, params.optionCenterY, params.optionHeight,
						params.optionBottom, childMeasuredHeight, top, bottom,
						vertical, true);
				

			}
		}

//		Log.d(TAG, child.getClass().getName() + " {" + horizontal[0] + ","
//				+ vertical[0] + "," + horizontal[1] + "," + vertical[1] + "}",
//				Log.DEBUG_MODE);

		return currentHeight;
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int count = getChildCount();

		int left = 0;
		int top = 0;
		int right = r - l;
		int bottom = b - t;

		if (needsSort) {
			viewSorter.clear();
			if (count > 1) { // No need to sort one item.
				for (int i = 0; i < count; i++) {
					View child = getChildAt(i);
		            TiCompositeLayout.LayoutParams params = getChildParams(child);
					params.index = i;
					viewSorter.add(child);
				}

				detachAllViewsFromParent();
				int i = 0;
				for (View child : viewSorter) {
					attachViewToParent(child, i++, child.getLayoutParams());
				}
			}
			setNeedsSort(false);
		}
		// viewSorter is not needed after this. It's a source of
		// memory leaks if it retains the views it's holding.
		viewSorter.clear();

		int[] horizontal = new int[2];
		int[] vertical = new int[2];

		int currentHeight = 0; // Used by vertical arrangement calcs
		boolean firstVisibleChild = true;
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			if (child == null || child.getVisibility() == View.GONE)
				continue;

            final TiCompositeLayout.LayoutParams params = getChildParams(child);
			currentHeight = getChildSize(child, i, params, left, top, bottom,
					right, currentHeight, horizontal, vertical, firstVisibleChild);
			
			firstVisibleChild = false;
			
			if (!TiApplication.getInstance().isRootActivityAvailable()) {
				Activity currentActivity = TiApplication
						.getAppCurrentActivity();
				if (currentActivity instanceof TiLaunchActivity) {
					if (!((TiLaunchActivity) currentActivity).isJSActivity()) {
						Log.w(TAG,
								"The root activity is no longer available.  Skipping layout pass.",
								Log.DEBUG_MODE);
						return;
					}
				}
			}


//			int newWidth = horizontal[1] - horizontal[0];
//			int newHeight = vertical[1] - vertical[0];
//			// If the old child measurements do not match the new measurements
//			// that we calculated, then update the
//			// child measurements accordingly
//			if (newWidth != child.getMeasuredWidth()
//					|| newHeight != child.getMeasuredHeight()) {
//				int newWidthSpec = MeasureSpec.makeMeasureSpec(newWidth,
//						MeasureSpec.EXACTLY);
//				int newHeightSpec = MeasureSpec.makeMeasureSpec(newHeight,
//						MeasureSpec.EXACTLY);
//				child.measure(newWidthSpec, newHeightSpec);
//			}
			child.layout(horizontal[0], vertical[0], horizontal[1], vertical[1]);
			currentHeight += vertical[1] - vertical[0];
	
			currentHeight += getLayoutOptionAsPixels(params.optionTop, TiDimension.TYPE_TOP, params, this);
            currentHeight += getLayoutOptionAsPixels(params.optionBottom, TiDimension.TYPE_BOTTOM, params, this);
		}

		TiUIView view = getView();
		if (view != null && changed) {
	        TiUIHelper.firePostLayoutEvent(view);
		}

	}

	// option0 is left/top, option1 is right/bottom
	@SuppressWarnings("null")
    public static void computePosition(final View parent, final LayoutParams params, final TiDimension leftOrTop,
            final TiDimension optionCenter, final TiDimension optionWidthOrHeight, final TiDimension rightOrBottom,
			final int measuredSize, final int layoutPosition0, final int layoutPosition1,
			int[] pos, final boolean vertical) {
		int dist = layoutPosition1 - layoutPosition0;
        final boolean leftTopDef = (leftOrTop != null && !leftOrTop.isUnitUndefined());
        final boolean rightBotDef = (rightOrBottom != null && !rightOrBottom.isUnitUndefined());
        final boolean widthHeightDef = (optionWidthOrHeight != null && !optionWidthOrHeight.isUnitUndefined());
        if (optionCenter != null && !optionCenter.isUnitUndefined()
                && optionCenter.getValue() != 0.0) {
            // Don't calculate position based on center dimension if it's 0.0
            int halfSize = measuredSize / 2;
            pos[0] = layoutPosition0 + getLayoutOptionAsPixelsNoAnim(optionCenter, optionCenter.getValueType(), params, parent)
                    - halfSize;
            pos[1] = pos[0] + measuredSize;
        } else if ((!leftTopDef && !rightBotDef) || (leftTopDef && rightBotDef && widthHeightDef)) {
         // Center
            int offset = (dist - measuredSize) / 2;
            
            pos[0] = layoutPosition0 + offset;
            //we need to update horizontal and vertical with animationFraction because computePosition
            //will assume 0 for optionLeft==null when it should be startRect.left
            if (params instanceof AnimationLayoutParams && 
                    params.fullscreen == false) {
                AnimationLayoutParams animP = (AnimationLayoutParams) params;
                final float animFraction = animP.animationFraction;
                if (animFraction < 1.0f) {
                    final Rect startRect = animP.startRect;
                    if (startRect != null) {
                        pos[0] = (int) Math.floor(pos[0] * animFraction + (1 - animFraction)
                                * (vertical?startRect.top:startRect.left));
                    }
//                    if (startRect != null ) {
//                        if (animP.optionWidth != null) {
//                          toUseWidth = (int) Math.floor(animP.finalWidth * animP.animationFraction + (1 - animP.animationFraction)* toUseWidth);
//                        }
//                        if (animP.optionHeight != null) {
//                          toUseHeight = (int) Math.floor(animP.finalHeight * animP.animationFraction + (1 - animP.animationFraction)* toUseHeight);
//                        }
//                    }
                }
            }
            pos[1] = pos[0] + measuredSize;
            
        } else if ((leftTopDef && rightBotDef)) {
            int leftOrTopPixels = getLayoutOptionAsPixels(leftOrTop, leftOrTop.getValueType(), params, parent);
            int rightOrBottomPixels = getLayoutOptionAsPixels(rightOrBottom, rightOrBottom.getValueType(), params, parent);
            
            pos[0] = layoutPosition0 + leftOrTopPixels;
            pos[1] = dist - rightOrBottomPixels;
        } else if (leftTopDef) {
			// peg left/top
			int leftOrTopPixels = getLayoutOptionAsPixels(leftOrTop, leftOrTop.getValueType(), params, parent);
			pos[0] = layoutPosition0 + leftOrTopPixels;
			pos[1] = layoutPosition0 + leftOrTopPixels + measuredSize;
		} else if (rightBotDef) {
		    
			// peg right/bottom
			int rightOrBottomPixels = getLayoutOptionAsPixels(rightOrBottom, rightOrBottom.getValueType(), params, parent);
			pos[0] = dist - rightOrBottomPixels - measuredSize;
			pos[1] = dist - rightOrBottomPixels;
		} else {
			
		}
	}

	private void computeVerticalLayoutPosition(final int currentHeight,
	        final TiDimension optionTop, final int measuredHeight, final int layoutTop,
			int[] pos, final int maxBottom, final LayoutParams params) {
		int top = layoutTop + currentHeight;
		top += (optionTop != null)?getLayoutOptionAsPixels(optionTop, TiDimension.TYPE_TOP, params, this):0;
		pos[0] = top;
		pos[1] = top + measuredHeight;
	}
	private static int getLayoutOptionAsPixelsNoAnim(final TiDimension option, final int type, final LayoutParams params, View parent) {
	    return (!params.fullscreen && option != null)?option.getAsPixels(parent):0;
	}
	private static int getLayoutOptionAsPixels(final TiDimension option, final int type, final LayoutParams params, final View parent) {
	    return getLayoutOptionAsPixels(option, type, params, parent.getMeasuredWidth(), parent.getMeasuredHeight());
	}
	private static int getLayoutOptionAsPixels(final TiDimension option, final int type, final LayoutParams params, final int parentWidth, final int parentHeight) {
		//never called with width or height so we can set 0 in fullscreen
		int result =  (!params.fullscreen && option != null)?option.getAsPixels(parentWidth, parentHeight):0;
		if (params instanceof AnimationLayoutParams) {
			float fraction = ((AnimationLayoutParams) params).animationFraction;
			ViewGroup.LayoutParams oldParams = ((AnimationLayoutParams) params).oldParams;
			if (fraction < 1.0f) {
	            LayoutParams oldTiParams = (LayoutParams) oldParams;
				TiDimension oldParam = null;
				switch (type) {
				case TiDimension.TYPE_LEFT:
					oldParam = oldTiParams.optionLeft;
					break;
				case TiDimension.TYPE_RIGHT:
					oldParam = oldTiParams.optionRight;
					break;
				case TiDimension.TYPE_TOP:
					oldParam = oldTiParams.optionTop;
					break;
				case TiDimension.TYPE_BOTTOM:
					oldParam = oldTiParams.optionBottom;
					break;
				case TiDimension.TYPE_WIDTH:
					oldParam = oldTiParams.optionWidth;
					break;
				case TiDimension.TYPE_HEIGHT:
					oldParam = oldTiParams.optionHeight;
					break;
				case TiDimension.TYPE_CENTER_X:
					oldParam = oldTiParams.optionCenterX;
					break;
				case TiDimension.TYPE_CENTER_Y:
					oldParam = oldTiParams.optionCenterY;
					break;
				default:
					break;
				}
				int oldValue = (oldParam != null)?oldParam.getAsPixels(parentWidth, parentHeight):0;
				result = (int) Math.floor(result * fraction + (1 - fraction)* oldValue);
			}
		}
		return result;
	}
	
	private void computeHorizontalLayoutPosition(
			TiCompositeLayout.LayoutParams params, int measuredWidth,
			int measuredHeight, int layoutRight, int layoutTop,
			int layoutBottom, int[] hpos, int[] vpos, int currentIndex) {

		TiDimension optionLeft = params.optionLeft;
		TiDimension optionRight = params.optionRight;
		int left = horizontalLayoutCurrentLeft + horiztonalLayoutPreviousRight;
		int optionLeftValue = getLayoutOptionAsPixels(optionLeft, TiDimension.TYPE_LEFT, params, this);
			left += optionLeftValue;
		horiztonalLayoutPreviousRight = getLayoutOptionAsPixels(optionRight, TiDimension.TYPE_RIGHT, params,this);

		int right;
		
		
		
		// If it's fill width with horizontal wrap, just take up remaining
		// space.
//		if (enableHorizontalWrap && params.autoFillsWidth
//				&& params.sizeOrFillWidthEnabled) {
//			right = measuredWidth;
//		} else {
			right = left + measuredWidth;
//		}

		if (enableHorizontalWrap
				&& ((right + horiztonalLayoutPreviousRight) > layoutRight || left >= layoutRight)) {
			// Too long for the current "line" that it's on. Need to move it
			// down.
			left = optionLeftValue;
			right = measuredWidth + left;
			horizontalLayoutTopBuffer = horizontalLayoutTopBuffer
					+ horizontalLayoutLineHeight;
			horizontalLayoutLineHeight = 0;
		} else if (!enableHorizontalWrap && params.autoFillsWidth
				&& params.sizeOrFillWidthEnabled) {
			// If there is no wrap, and width is fill behavior, cap it off at
			// the width of the screen
			right = Math.min(right, layoutRight);
		}

		hpos[0] = left;
		hpos[1] = right;
		

		
		horizontalLayoutCurrentLeft = right;

		if (enableHorizontalWrap) {
			// Don't update row on the first iteration since we already do it
			// beforehand
			if (currentIndex != 0
					&& currentIndex > horizontalLayoutLastIndexBeforeWrap) {
				updateRowForHorizontalWrap(layoutRight, currentIndex);
			}
//			measuredHeight = calculateHeightFromPins(params,
//					horizontalLayoutTopBuffer, horizontalLayoutTopBuffer
//							+ horizontalLayoutLineHeight,
//					horizontalLayoutLineHeight, measuredHeight, true);
			layoutBottom = horizontalLayoutLineHeight;
		}
//		else {
//			measuredHeight = calculateHeightFromPins(params,
//					layoutTop, layoutBottom,
//					layoutBottom - layoutTop, measuredHeight, true);
//		}

		// Get vertical position into vpos
		computePosition(this, params, params.optionTop, params.optionCenterY, params.optionHeight,
				params.optionBottom, measuredHeight, layoutTop, layoutBottom,
				vpos, true);
		if (! params.autoFillHeight() && (params.optionTop != null && !params.optionTop.isUnitUndefined() &&
				params.optionBottom != null && !params.optionBottom.isUnitUndefined()))
		{
			int height = vpos[1] - vpos[0];
			vpos[0] = layoutTop + (layoutBottom - layoutTop)/2 - height/2;
			vpos[1] = vpos[0] + height;
		}
		// account for moving the item "down" to later line(s) if there has been
		// wrapping.
		vpos[0] = vpos[0] + horizontalLayoutTopBuffer;
		vpos[1] = vpos[1] + horizontalLayoutTopBuffer;
	}

	private void updateRowForHorizontalWrap(int maxRight, int currentIndex) {
		int rowWidth = 0;
		int rowHeight = 0;
		int i = 0;
		horizontalLayoutLineHeight = 0;

		for (i = currentIndex; i < getChildCount(); i++) {
			View child = getChildAt(i);
			LayoutParams params = (LayoutParams) child.getLayoutParams();
			// Calculate row width/height with padding
			rowWidth += child.getMeasuredWidth()
					+ getViewWidthPadding(child, params, this);
			rowHeight = child.getMeasuredHeight()
					+ getViewHeightPadding(child, params, this);

			if (rowWidth > maxRight) {
				horizontalLayoutLastIndexBeforeWrap = i - 1;
				return;

			} else if (rowWidth == maxRight) {
				break;
			}

			if (horizontalLayoutLineHeight < rowHeight) {
				horizontalLayoutLineHeight = rowHeight;
			}
		}

		if (horizontalLayoutLineHeight < rowHeight) {
			horizontalLayoutLineHeight = rowHeight;
		}
		horizontalLayoutLastIndexBeforeWrap = i;
	}

	// Determine whether we have a conflict where a parent has size behavior,
	// and child has fill behavior.
	private static boolean hasSizeFillConflict(View parent, int[] conflicts,
			boolean firstIteration) {
		if (parent instanceof TiCompositeLayout) {
			TiCompositeLayout currentLayout = (TiCompositeLayout) parent;
			LayoutParams currentParams = (LayoutParams) currentLayout
					.getLayoutParams();

			// During the first iteration, the parent view needs to have size
			// behavior.
			if (firstIteration
					&& (currentParams.autoFillsWidth || currentParams.optionWidth != null)) {
				conflicts[0] = NO_SIZE_FILL_CONFLICT;
			}
			if (firstIteration
					&& (currentParams.autoFillsHeight || currentParams.optionHeight != null)) {
				conflicts[1] = NO_SIZE_FILL_CONFLICT;
			}

			// We don't check for sizeOrFillHeightEnabled. The calculations
			// during the measure phase (which includes
			// this method) will be adjusted to undefined behavior accordingly
			// during the layout phase.
			// sizeOrFillHeightEnabled is used during the layout phase to
			// determine whether we want to use the fill/size
			// measurements that we got from the measure phase.
			// if (currentParams.autoFillsWidth && currentParams.optionWidth ==
			// null && conflicts[0] == NOT_SET) {
			// conflicts[0] = HAS_SIZE_FILL_CONFLICT;
			// }
			// if (currentParams.autoFillsHeight && currentParams.optionHeight
			// == null && conflicts[1] == NOT_SET) {
			// conflicts[1] = HAS_SIZE_FILL_CONFLICT;
			// }

			// Stop traversing if we've determined whether there is a conflict
			// for both width and height
			if (conflicts[0] != NOT_SET && conflicts[1] != NOT_SET) {
				return true;
			}

			// If the child has size behavior, continue traversing through
			// children and see if any of them have fill
			// behavior
			for (int i = 0; i < currentLayout.getChildCount(); ++i) {
				if (hasSizeFillConflict(currentLayout.getChildAt(i), conflicts,
						false)) {
					return true;
				}
			}
		}

		// Default to false if we couldn't find conflicts
		if (firstIteration && conflicts[0] == NOT_SET) {
			conflicts[0] = NO_SIZE_FILL_CONFLICT;
		}
		if (firstIteration && conflicts[1] == NOT_SET) {
			conflicts[1] = NO_SIZE_FILL_CONFLICT;
		}
		return false;
	}

	protected int getWidthMeasureSpec(View child) {
		return MeasureSpec.EXACTLY;
	}

	protected int getHeightMeasureSpec(View child) {
		return MeasureSpec.EXACTLY;
	}

	/**
	 * A TiCompositeLayout specific version of
	 * {@link android.view.ViewGroup.LayoutParams}
	 */
	public static class LayoutParams extends FreeLayout.LayoutParams {
		public boolean notTiLayout = false;

        protected int index;

		public int optionZIndex = NOT_SET;
		public TiDimension optionLeft = null;
		public TiDimension optionTop = null;
		public TiDimension optionCenterX = null;
		public TiDimension optionCenterY = null;
		public TiDimension optionRight = null;
		public TiDimension optionBottom = null;
		public TiDimension optionWidth = null;
		public TiDimension optionHeight = null;
		
		
		public TiDimension maxWidth = null;
		public TiDimension maxHeight = null;
		public TiDimension minWidth = null;
		public TiDimension minHeight = null;

        public boolean fullscreen = false;
        public boolean ignoreInLayout = false;
		// This are flags to determine whether we are using fill or size
		// behavior
		public boolean sizeOrFillHeightEnabled = false;
		public boolean sizeOrFillWidthEnabled = false;

		/**
		 * If this is true, and {@link #sizeOrFillWidthEnabled} is true, then
		 * the current view will follow the fill behavior, which fills available
		 * parent width. If this value is false and
		 * {@link #sizeOrFillWidthEnabled} is true, then we use the size
		 * behavior, which constrains the view width to fit the width of its
		 * contents.
		 * 
		 * @module.api
		 */
		public boolean autoFillsWidth = false;

		/**
		 * If this is true, and {@link #sizeOrFillHeightEnabled} is true, then
		 * the current view will follow fill behavior, which fills available
		 * parent height. If this value is false and
		 * {@link #sizeOrFillHeightEnabled} is true, then we use the size
		 * behavior, which constrains the view height to fit the height of its
		 * contents.
		 * 
		 * @module.api
		 */
		public boolean autoFillsHeight = false;

        public boolean widthMatchHeight = false;
        public boolean heightMatchWidth = false;
        
        public boolean widthMatchParent = false;
        public boolean heightMatchParent = false;
        
        public float weight = 1.0f;
        public float sizeRatio = -1.0f;
        
        public boolean widthDefined = false;
        public boolean heightDefined = false;

		public LayoutParams() {
			super(WRAP_CONTENT, WRAP_CONTENT);

			index = Integer.MIN_VALUE;
		}

		private void initiateFromOther(final TiCompositeLayout.LayoutParams params) {
		    autoFillsWidth = params.autoFillsWidth;
            autoFillsHeight = params.autoFillsHeight;
            optionZIndex = params.optionZIndex;
            optionLeft = params.optionLeft;
            optionTop = params.optionTop;
            optionCenterX = params.optionCenterX;
            optionCenterY = params.optionCenterY;
            optionRight = params.optionRight;
            optionBottom = params.optionBottom;
            optionWidth = params.optionWidth;
            optionHeight = params.optionHeight;
            maxWidth = params.maxWidth;
            maxHeight = params.maxHeight;
            minWidth = params.minWidth;
            minHeight = params.minHeight;
            sizeOrFillHeightEnabled = params.sizeOrFillHeightEnabled;
            sizeOrFillWidthEnabled = params.sizeOrFillWidthEnabled;
            fullscreen = params.fullscreen;
            widthMatchHeight = params.widthMatchHeight;
            heightMatchWidth = params.heightMatchWidth;
            heightMatchParent = params.heightMatchParent;
            widthMatchParent = params.widthMatchParent;
            notTiLayout = params.notTiLayout;
            ignoreInLayout = params.ignoreInLayout;
            weight = params.weight;
            sizeRatio = params.sizeRatio;
            widthDefined = params.widthDefined;
            heightDefined = params.heightDefined;
		}
		public LayoutParams(TiCompositeLayout.LayoutParams params) {
			super(params);
			initiateFromOther(params);
		}
		
		public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams) {
                initiateFromOther((LayoutParams) source);
            } else {
                if (width == MATCH_PARENT) {
                    sizeOrFillWidthEnabled = true;
                    autoFillsWidth = true;
                }
                if (height == MATCH_PARENT) {
                    sizeOrFillHeightEnabled = true;
                    autoFillsHeight = true;
                }
            }
            
        }
		
		public LayoutParams(int width, int height) {
            super(width, height);
            if (width == MATCH_PARENT) {
                sizeOrFillWidthEnabled = true;
                autoFillsWidth = true;
            }
            if (height == MATCH_PARENT) {
                sizeOrFillHeightEnabled = true;
                autoFillsHeight = true;
            }
        }
		
		public void setWidth(int width) {
		    this.width = width;
		    sizeOrFillWidthEnabled = (width == MATCH_PARENT || width == WRAP_CONTENT);
            autoFillsWidth = (width == MATCH_PARENT);
		}

        public void setHeight(int height) {
            this.height = height;
            sizeOrFillHeightEnabled = (height == MATCH_PARENT || height == WRAP_CONTENT);
            autoFillsHeight = (height == MATCH_PARENT);
        }

        public boolean autoSizeHeight() {
			return ((!this.sizeOrFillHeightEnabled && !this.autoFillsHeight && this.optionHeight == null) || (this.sizeOrFillHeightEnabled && !this.autoFillsHeight));
		}

		public boolean autoSizeWidth() {
			return ((!this.sizeOrFillWidthEnabled && !this.autoFillsWidth && this.optionWidth == null) || (this.sizeOrFillWidthEnabled && !this.autoFillsWidth));
		}
		
		public boolean autoFillHeight() {
            return this.sizeOrFillHeightEnabled && this.autoFillsHeight;
        }

        public boolean autoFillWidth() {
            return this.sizeOrFillWidthEnabled && this.autoFillsWidth;
        }
		
		public boolean fixedSizeWidth() {
            return (this.optionWidth != null && this.optionWidth.isUnitFixed());
        }
		
        public boolean fixedSizeHeight() {
            return (this.optionHeight != null && this.optionHeight.isUnitFixed());
        }
	}

	public static class AnimationLayoutParams extends LayoutParams {
		public float animationFraction = 1.0f;
		public ViewGroup.LayoutParams oldParams = null;
		public Rect startRect = null;
		public int finalWidth = 0;
		public int finalHeight = 0;

		public AnimationLayoutParams() {
			super();
		}

		public AnimationLayoutParams(TiCompositeLayout.LayoutParams params) {
			super(params);
			oldParams = params;
		}

        public AnimationLayoutParams(ViewGroup.LayoutParams params) {
            super(params);
            oldParams = params;
        }
	}

	protected boolean isVerticalArrangement() {
		return (arrangement == LayoutArrangement.VERTICAL);
	}

	protected boolean isHorizontalArrangement() {
		return (arrangement == LayoutArrangement.HORIZONTAL);
	}

	protected boolean isDefaultArrangement() {
		return (arrangement == LayoutArrangement.DEFAULT);
	}

	public void setLayoutArrangement(String arrangementProperty) {
		Boolean needsUpdate = false;
		if (arrangementProperty != null
				&& arrangementProperty.equals(TiC.LAYOUT_HORIZONTAL)) {
			needsUpdate = (arrangement != LayoutArrangement.HORIZONTAL);
			arrangement = LayoutArrangement.HORIZONTAL;
		} else if (arrangementProperty != null
				&& arrangementProperty.equals(TiC.LAYOUT_VERTICAL)) {
			needsUpdate = (arrangement != LayoutArrangement.VERTICAL);
			arrangement = LayoutArrangement.VERTICAL;
		} else {
			needsUpdate = (arrangement != LayoutArrangement.DEFAULT);
			arrangement = LayoutArrangement.DEFAULT;
		}
		if (needsUpdate) {
			requestLayout();
			invalidate();
		}
	}

	public void setEnableHorizontalWrap(boolean enable) {
		if (enable != enableHorizontalWrap) {
			enableHorizontalWrap = enable;
			requestLayout();
			invalidate();
		}
	}

	public void setView(TiUIView view) {
	    if (view != null) {
	        this.view = new WeakReference<TiUIView>(view);
	    }
	    else {
	        this.view = null;
	    }
	}
	public TiUIView getView() {
		if (view != null) return view.get();
		return null;
	}
	@Override
	public void dispatchSetPressed(boolean pressed) {
		TiUIView view = getView();
		if (view != null && (view.getDispatchPressed() == true))
		{
			int count = getChildCount();
			for (int i = 0; i < count; i++) {
	            final View child = getChildAt(i);
	            child.setPressed(pressed);
	        }
		}
	};
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
	    if (touchPassThrough(event)) {
	        return false;
	    }
	    
		return super.dispatchTouchEvent(event);
	}
	public static boolean viewContainsTouch(final View view,
            final double rawx, final double rawy, int[] location) {
        if (location == null) {
            location = new int[2];
        }
        view.getLocationOnScreen(location);
        return (location[0] <= rawx && rawx <= (location[0] + view.getWidth())
                && location[1] <= rawy && rawy <= (location[1] + view
                .getHeight()));
    }
	
	public boolean canScroll() {
        TiUIView view = (this.view == null ? null : this.view.get());
	    return !mInterTouchPassThrough ||  (view != null && view.isTouchEnabled && !view.touchPassThrough);
	}
	
	public boolean touchPassThrough(MotionEvent event) {
	    TiUIView view = (this.view == null ? null : this.view.get());
	    if (view != null) {
	        return view.touchPassThrough(this, event);
	    } else {
	        if (mInterTouchPassThrough == true && event.getAction() == MotionEvent.ACTION_DOWN) {
	            int[] location = new int[2];
	            final double x = event.getRawX();
	            final double y = event.getRawY();
                Object tag;
                View child;
                for (int i = 0; i < getChildCount(); i++) {
                    child = getChildAt(i);
                    if (viewContainsTouch(child, x, y, location)) {
                        tag = child.getTag();
                        if (tag == null || (tag instanceof TiUIView && !((TiUIView) tag).touchPassThrough(child, event))) {
                            return false;
                        } else {
                            return false;
                        }
                    }
                }
                return mInterTouchPassThrough;
	        } else {
	            return false;
	        }
	    }
//	    return mInterTouchPassThrough;
	}

	private void setNeedsSort(boolean value) {
		// For vertical and horizontal layouts, since the controls doesn't
		// overlap, we shouldn't sort based on the zIndex, the original order
		// that controls added should be preserved
		if (isHorizontalArrangement() || isVerticalArrangement()) {
			value = false;
		}
		needsSort = value;
	}
	
	// @Override
 //    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
 //        super.onSizeChanged(w, h, oldw, oldh);
 //        if (TiC.)
 //        invalidateOutline();
 //    }
}

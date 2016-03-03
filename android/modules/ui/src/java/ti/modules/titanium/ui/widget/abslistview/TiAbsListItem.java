/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.abslistview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.UIModule;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
//import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

public class TiAbsListItem extends TiUIView implements TiTouchDelegate {
    private static final String TAG = "TiAbsListItem";
	View listItemLayout;
    private boolean canShowLeftMenu = false;
    private boolean canShowLeftMenuDefined = false;
    private boolean canShowRightMenu = false;
    private boolean canShowRightMenuDefined = false;
    private boolean hasLeftButtons = false;
    private boolean hasRightButtons = false;
    private List<TiViewProxy> leftButtons = null;
    
//    private final static int sPressedStateDuration = ViewConfiguration.getPressedStateDuration();
//    private final float mSlop;
    private List<TiViewProxy> rightButtons = null;

	public TiAbsListItem(TiViewProxy proxy, View v, View item_layout) {
		super(proxy);
//		mSlop = ViewConfiguration.get(item_layout.getContext()).getScaledTouchSlop();

        layoutParams.sizeOrFillWidthEnabled = true;
        layoutParams.autoFillsWidth = true;
		listItemLayout = item_layout;
		setNativeView(v);
		registerForTouch(v);
		v.setFocusable(false);
//		applyCustomForeground();
	}
	
    private TiViewProxy handleCreateProxy(Object value) {
        TiViewProxy viewProxy = (TiViewProxy) proxy.createProxyFromObject(value,
                proxy, true);
        if (viewProxy != null) {
            AbsListItemProxy itemProxy = (AbsListItemProxy) proxy;
            viewProxy.setParent(proxy);
            viewProxy.setEventOverrideDelegate(itemProxy);
            String bindId = viewProxy.getBindId();
            if (bindId != null) {
                HashMap data = itemProxy.getItemDataForBindId(bindId);
                if (data != null) {
                    viewProxy.applyPropertiesInternal(data, false, true);
                }
            }
        }
        return viewProxy;
    }
	
	private List<TiViewProxy> proxiesArrayFromValue(Object value) {
	    List<TiViewProxy> result = null;
	    if (value instanceof Object[]) {
	        result = new ArrayList<TiViewProxy>();
	        Object[] array  = (Object[]) value;
            for (int i = 0; i < array.length; i++) {
                TiViewProxy viewProxy  = handleCreateProxy(array[i]);
                if (viewProxy != null) {
                    result.add(viewProxy);
                }
            }
	    }
	    else {
	        TiViewProxy viewProxy  = handleCreateProxy(value);
            if (viewProxy != null) {
                result = new ArrayList<TiViewProxy>();
                result.add(viewProxy);
            }
	    }
	    return result;
	}
	
    @Override
    protected int fillLayout(HashMap d) {
        //ignore width property
        d.remove(TiC.PROPERTY_WIDTH);
        return TiConvert.fillLayout(d, layoutParams, true);
    }
	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
	    switch (key) {
        case TiC.PROPERTY_ACCESSORY_TYPE:
			int accessory = TiConvert.toInt(newValue, -1);
			handleAccessory(accessory);
		break;
//        case TiC.PROPERTY_SELECTED_BACKGROUND_COLOR:
//		    super.propertySet(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, newValue, oldValue, changedProperty);
//	        break;
//        case TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE:
//            super.propertySet(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, newValue, oldValue, changedProperty);
//            break;
        case TiC.PROPERTY_CAN_SWIPE_LEFT:
            canShowLeftMenu = TiConvert.toBoolean(newValue, true);
            canShowLeftMenuDefined = true;
            break;
        case TiC.PROPERTY_CAN_SWIPE_RIGHT:
            canShowRightMenu = TiConvert.toBoolean(newValue, true);
            canShowRightMenuDefined = true;
            break;
		
        case TiC.PROPERTY_LEFT_SWIPE_BUTTONS:
            hasLeftButtons = newValue != null;
		    if (leftButtons != null) {
		        for (TiViewProxy viewProxy : leftButtons) {
		            proxy.removeHoldedProxy(viewProxy.getBindId());
		            proxy.removeProxy(viewProxy);
		        }
		        leftButtons = null;
		    }
            break;
        case TiC.PROPERTY_RIGHT_SWIPE_BUTTONS:
            hasRightButtons = newValue != null;
            if (rightButtons != null) {
                for (TiViewProxy viewProxy : rightButtons) {
                    proxy.removeHoldedProxy(viewProxy.getBindId());

                    proxy.removeProxy(viewProxy);
                }
            }
            break;
		default:
		    super.propertySet(key, newValue, oldValue, changedProperty);
		}
	}

	protected void handleAccessory(int accessory) {
		
		ImageView accessoryImage = (ImageView) listItemLayout.findViewById(TiAbsListView.accessory);

		switch(accessory) {

			case UIModule.LIST_ACCESSORY_TYPE_CHECKMARK:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiAbsListView.isCheck);
				break;
			case UIModule.LIST_ACCESSORY_TYPE_DETAIL:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiAbsListView.hasChild);
				break;

			case UIModule.LIST_ACCESSORY_TYPE_DISCLOSURE:
                accessoryImage.setVisibility(View.VISIBLE);
				accessoryImage.setImageResource(TiAbsListView.disclosure);
				break;
	
			default:
                accessoryImage.setVisibility(View.GONE);
				accessoryImage.setImageDrawable(null);
				break;
		}
	}

	public void release() {
		if (listItemLayout != null) {
			listItemLayout = null;
		}
//		removeUnsetPressCallback();
		super.release();
	}

//	private boolean prepressed = false;
//	private final class UnsetPressedState implements Runnable {
//        public void run() {
//            if (nativeView != null) {
//                nativeView.setPressed(false);
//            }
//        }
//    }
//	private final class SetPressedState implements Runnable {
//        public void run() {
//            if (nativeView != null) {
//                prepressed = false;
//                nativeView.setPressed(true);
//            }
//        }
//    }
//	private UnsetPressedState mUnsetPressedState;
//    private SetPressedState mSetPressedState;
    /**
     * Remove the prepress detection timer.
     */
//    private void removeUnsetPressCallback() {
//        if (nativeView != null) {
//            if (nativeView.isPressed()) {
//                nativeView.setPressed(false);
//            }
//            if (mUnsetPressedState != null) {
//                nativeView.removeCallbacks(mUnsetPressedState);
//                mUnsetPressedState = null;
//            }
//            if (mSetPressedState != null) {
//                nativeView.removeCallbacks(mSetPressedState);
//                mSetPressedState = null;
//            }
//        }
//    }
    
    public boolean pointInView(final View view, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((view.getRight() - view.getX()) + slop) &&
                localY < ((view.getBottom() - view.getY()) + slop);
    }
    
    @SuppressLint("NewApi")
    public boolean isInScrollingContainer(View view) {
        ViewParent p = view.getParent();
        while (p != null && p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }
    
    
    
    @Override
    public void onTouchEvent(MotionEvent event, TiUIView fromView) {
        if (fromView == this)
            return;

        if (nativeView != null && !fromView.getPreventListViewSelection()) {
            final int action = event.getAction();
            int[] location = new int[2];
            if (action != MotionEvent.ACTION_DOWN || viewContainsTouch(nativeView,event.getRawX(), event.getRawY(), location)) {
                nativeView.onTouchEvent(event);
            }
//            final boolean pressed = nativeView.isPressed();
//
//            if (nativeView.isEnabled() == false) {
//                if (event.getAction() == MotionEvent.ACTION_UP && pressed) {
//                    nativeView.setPressed(false);
//                }
//                return;
//            }
//
//            if (nativeView.isClickable() || nativeView.isLongClickable()) {
//                switch (event.getAction()) {
//                case MotionEvent.ACTION_UP:
//                    if (mSetPressedState != null) {
//                        nativeView.removeCallbacks(mSetPressedState);
//                        mSetPressedState = null;
//                    }
//                    if (pressed || prepressed) {
//                        
//                        if (prepressed) {
//                            final int x = (int) event.getX();
//                            final int y = (int) event.getY();
//                            if (pointInView(nativeView, x, y, mSlop)) {
//                                nativeView.drawableHotspotChanged(x, y);
//                                // The button is being released before we actually
//                                // showed it as pressed. Make it show the pressed
//                                // state now (before scheduling the click) to ensure
//                                // the user sees it.
//                                nativeView.setPressed(true);
//                            } else {
//                                prepressed = false;
//                            }
//                        }
//                        if (mUnsetPressedState == null) {
//                            mUnsetPressedState = new UnsetPressedState();
//                        }
//                        if (prepressed) {
//                            nativeView.postDelayed(mUnsetPressedState, sPressedStateDuration);
//                        } else if (!nativeView.post(mUnsetPressedState)) {
//                            // If the post failed, unpress right now
//                            mUnsetPressedState.run();
//                        }
//                    }
//                    break;
//
//                case MotionEvent.ACTION_DOWN:
//                        prepressed = true;
//                    break;
//
//                case MotionEvent.ACTION_CANCEL:
//                    if (mSetPressedState != null) {
//                        nativeView.removeCallbacks(mSetPressedState);
//                        mSetPressedState = null;
//                    }
//                    prepressed = false;
//                    nativeView.setPressed(false);
//                    break;
//
//                case MotionEvent.ACTION_MOVE:
//                    final int x = (int) event.getX();
//                    final int y = (int) event.getY();
//                    // Be lenient about moving outside of buttons
//                    if (!pointInView(nativeView, x, y,mSlop)) {
//                        if (pressed) {
//                            nativeView.setPressed(false);
//                        }
//                    }
//                    else if (!pressed) {
//                        if (mSetPressedState == null) {
//                            mSetPressedState = new SetPressedState();
//                            nativeView.postDelayed(mSetPressedState, sPressedStateDuration);
//                        }
//
//                    }
//                    break;
//                }
//            }
        }
    }

    public boolean canShowLeftMenu() {
        return (canShowLeftMenuDefined && canShowLeftMenu) || hasLeftButtons;
    }
    
    public boolean canShowRightMenu() {
        return (canShowRightMenuDefined && canShowRightMenu) || hasRightButtons;
    }
    
    private View[] viewsForProxyArray(List<TiViewProxy> proxies) {
        if (proxies != null) {
            View[] buttons = new View[proxies.size()];
            int i = 0;
            final Context context = getContext();
            for (TiViewProxy viewProxy : proxies) {
                View view = viewProxy.getOrCreateView().getOuterView();
                if (view.getParent() instanceof TiCompositeLayout) {
                    buttons[i] = (View) view.getParent();
                }
                else {
                    TiCompositeLayout layout = new TiCompositeLayout(context);
                    layout.addView(view);
                    buttons[i] = layout;
                }
                
                i ++;
            }
            return buttons;
        }
        return null;
    }
    
    public View[] getLeftButtons() {
        if (hasLeftButtons) {
            if (leftButtons == null) {
                leftButtons = proxiesArrayFromValue(getProxy().getProperty(TiC.PROPERTY_LEFT_SWIPE_BUTTONS));
            }
            return viewsForProxyArray(leftButtons);
        }
        return null;
    }
    
    public View[] getRightButtons() {
        if (hasRightButtons) {
            if (rightButtons == null) {
                rightButtons = proxiesArrayFromValue(getProxy().getProperty(TiC.PROPERTY_RIGHT_SWIPE_BUTTONS));
            }
            return viewsForProxyArray(rightButtons);
        }
        return null;
    }

    public boolean canShowMenus() {
        return hasLeftButtons || hasRightButtons;
    }
}

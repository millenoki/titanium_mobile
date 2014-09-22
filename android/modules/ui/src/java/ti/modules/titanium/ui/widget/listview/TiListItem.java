/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import java.util.HashMap;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.TiUIButton;
import ti.modules.titanium.ui.widget.TiUISlider;
import ti.modules.titanium.ui.widget.TiUISwitch;
import ti.modules.titanium.ui.widget.TiUIText;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class TiListItem extends TiUIView implements TiTouchDelegate {
    private static final String TAG = "TiListItem";
	TiUIView mClickDelegate;
	View listItemLayout;
	private boolean shouldFireClick = true;
	public TiListItem(TiViewProxy proxy) {
		super(proxy);
	}

	public TiListItem(TiViewProxy proxy, View v, View item_layout) {
		super(proxy);
//		layoutParams = p;
		layoutParams.autoFillsHeight = true;
		layoutParams.autoFillsWidth = true;
		listItemLayout = item_layout;
		setNativeView(v);
		registerForTouch(v);
		v.setFocusable(false);
	}
	
	public void processProperties(KrollDict d) {
		ListItemProxy itemProxy = (ListItemProxy)getProxy();

		if (d.containsKey(TiC.PROPERTY_ACCESSORY_TYPE)) {
			int accessory = TiConvert.toInt(d.get(TiC.PROPERTY_ACCESSORY_TYPE), -1);
			handleAccessory(accessory);
		}
		if (d.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			d.put(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, d.get(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR));
		}
		if (d.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			d.put(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, d.get(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE));
		}

		super.processProperties(d);
	}

	private void handleAccessory(int accessory) {
		
		ImageView accessoryImage = (ImageView) listItemLayout.findViewById(TiListView.accessory);

		switch(accessory) {

			case UIModule.LIST_ACCESSORY_TYPE_CHECKMARK:
				accessoryImage.setImageResource(TiListView.isCheck);
				break;
			case UIModule.LIST_ACCESSORY_TYPE_DETAIL:
				accessoryImage.setImageResource(TiListView.hasChild);
				break;

			case UIModule.LIST_ACCESSORY_TYPE_DISCLOSURE:
				accessoryImage.setImageResource(TiListView.disclosure);
				break;
	
			default:
				accessoryImage.setImageResource(0);
		}
	}
	
	@Override
	protected void setOnClickListener(View view)
	{
		view.setOnClickListener(new OnClickListener()
		{
			public void onClick(View view)
			{
				
				if (shouldFireClick) {
					KrollDict data = dictFromEvent(lastUpEvent);
					handleFireItemClick(new KrollDict(data));
					fireEvent(TiC.EVENT_CLICK, data);
				}
                shouldFireClick = true;
			}
		});
	}
	
	protected void handleFireItemClick (KrollDict data) {
		TiViewProxy listViewProxy = ((ListItemProxy)proxy).getListProxy();
		if (listViewProxy != null && listViewProxy.hasListeners(TiC.EVENT_ITEM_CLICK)) {
			TiUIView listView = listViewProxy.peekView();
			if (listView != null) {
				KrollDict d = listView.getAdditionalEventData();
				if (d == null) {
					listView.setAdditionalEventData(new KrollDict((HashMap) additionalEventData));
				} else {
					d.clear();
					d.putAll(additionalEventData);
				}
				if (mClickDelegate == null) {
					listView.fireEvent(TiC.EVENT_ITEM_CLICK, data, false, false);
				}
			}
		}
	}
	
	public void release() {
		if (listItemLayout != null) {
			listItemLayout = null;
		}
		removeUnsetPressCallback();
		super.release();
	}
	@Override
	protected void handleTouchEvent(MotionEvent event) {
		mClickDelegate = null;
		super.handleTouchEvent(event);
	}
	private boolean prepressed = false;
	private final class UnsetPressedState implements Runnable {
        public void run() {
            if (nativeView != null) {
                nativeView.setPressed(false);
            }
        }
    }
    private UnsetPressedState mUnsetPressedState;
    /**
     * Remove the prepress detection timer.
     */
    private void removeUnsetPressCallback() {
        if (nativeView != null && nativeView.isPressed() && mUnsetPressedState != null) {
            nativeView.setPressed(false);
            nativeView.removeCallbacks(mUnsetPressedState);
        }
    }
    
    public boolean pointInView(final View view, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((view.getRight() - view.getLeft()) + slop) &&
                localY < ((view.getBottom() - view.getTop()) + slop);
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
        shouldFireClick = false;
        if (fromView instanceof TiUIButton || fromView instanceof TiUISwitch
                || fromView instanceof TiUISlider
                || fromView instanceof TiUIText)
            return;
        mClickDelegate = fromView;

        if (nativeView != null && !fromView.getPreventListViewSelection()) {
            final boolean pressed = nativeView.isPressed();

            if (nativeView.isEnabled() == false) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressed) {
                    nativeView.setPressed(false);
                }
                return;
            }

            if (nativeView.isClickable() || nativeView.isLongClickable()) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (pressed || prepressed) {

                        if (prepressed) {
                            // The button is being released before we actually
                            // showed it as pressed. Make it show the pressed
                            // state now (before scheduling the click) to ensure
                            // the user sees it.
                            nativeView.setPressed(true);
                        }
                        if (mUnsetPressedState == null) {
                            mUnsetPressedState = new UnsetPressedState();
                        }
                        if (prepressed) {
                            nativeView
                                    .postDelayed(mUnsetPressedState,
                                            ViewConfiguration
                                                    .getPressedStateDuration());
                        } else if (!nativeView.post(mUnsetPressedState)) {
                            // If the post failed, unpress right now
                            mUnsetPressedState.run();
                        }
                    }
                    break;

                case MotionEvent.ACTION_DOWN:

                    // Walk up the hierarchy to determine if we're inside a
                    // scrolling container.
                    boolean isInScrollingContainer = isInScrollingContainer(nativeView);

                    // For views inside a scrolling container, delay the pressed
                    // feedback for
                    // a short period in case this is a scroll.
                    if (isInScrollingContainer) {
                        prepressed = true;

                    } else {
                        // Not inside a scrolling container, so show the
                        // feedback right away
                        nativeView.setPressed(true);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    nativeView.setPressed(false);
                    break;

                case MotionEvent.ACTION_MOVE:
                    final int x = (int) event.getX();
                    final int y = (int) event.getY();

                    // Be lenient about moving outside of buttons
                    if (!pointInView(nativeView, x, y,
                            ViewConfiguration.get(getContext())
                                    .getScaledTouchSlop())) {
                        if (pressed) {
                            nativeView.setPressed(false);
                        }
                    }
                    else if (!pressed) {
                        prepressed = false;
                        nativeView.setPressed(true);
                    }
                    break;
                }
            }
        }
    }
}

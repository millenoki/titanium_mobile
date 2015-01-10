/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;

import android.annotation.SuppressLint;
import ti.modules.titanium.ui.ViewProxy;
import ti.modules.titanium.ui.widget.CustomListView;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListView;
import yaochangwei.pulltorefreshlistview.widget.RefreshableListView.OnPullListener;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

@SuppressLint("NewApi")
public class TiListView extends TiAbsListView<CustomListView> {

	public TiListView(TiViewProxy proxy, Activity activity) {
		super(proxy, activity);
	}
	
    @Override
    protected CustomListView createListView(final Activity activity) {
        final KrollProxy fProxy = this.proxy;
        CustomListView result = new CustomListView(activity) {
            
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                
                super.onLayout(changed, left, top, right, bottom);
                if (changed && fProxy != null && fProxy.hasListeners(TiC.EVENT_POST_LAYOUT, false)) {
                    fProxy.fireEvent(TiC.EVENT_POST_LAYOUT, null);
                }
            }
            
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (touchPassThrough == true)
                    return false;
                return super.dispatchTouchEvent(event);
            }
            
            @Override
            protected void dispatchDraw(Canvas canvas) {
                try {
                    super.dispatchDraw(canvas);
                } catch (IndexOutOfBoundsException e) {
                    // samsung error
                }
            }
        };
        result.setOnPullListener( new OnPullListener() {
            private boolean canUpdate = false;
            @Override
            public void onPull(boolean canUpdate) {
                if (canUpdate != this.canUpdate) {
                    this.canUpdate = canUpdate;
                    if(fProxy.hasListeners(TiC.EVENT_PULL_CHANGED, false)) {
                        KrollDict event = dictForScrollEvent();
                        event.put("active", canUpdate);
                        fProxy.fireEvent(TiC.EVENT_PULL_CHANGED, event, false, false);
                    }
                }
                if(fProxy.hasListeners(TiC.EVENT_PULL, false)) {
                    KrollDict event = dictForScrollEvent();
                    event.put("active", canUpdate);
                    fProxy.fireEvent(TiC.EVENT_PULL, event, false, false);
                }
            }
    
            @Override
            public void onPullEnd(boolean canUpdate) {
                if(fProxy.hasListeners(TiC.EVENT_PULL_END, false)) {
                    KrollDict event = dictForScrollEvent();
                    event.put("active", canUpdate);
                    fProxy.fireEvent(TiC.EVENT_PULL_END, event, false, false);
                }
            }
        });
        return result;
    }
    
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCROLLING_ENABLED:
            listView.setScrollingEnabled(newValue);
            break;
        case TiC.PROPERTY_PULL_VIEW:
            listView.setHeaderPullView(setPullView(newValue));
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
    
    private View setPullView (Object viewObj) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj, "pull");
        if (viewProxy instanceof ViewProxy) {
            return layoutHeaderOrFooterView((TiViewProxy) viewProxy);
        }
        return null;
    }
    
    public void showPullView(boolean animated) {
        listView.showHeaderPullView(animated);
    }
    
    public void closePullView(boolean animated) {
        listView.closeHeaderPullView(animated);
    }
}

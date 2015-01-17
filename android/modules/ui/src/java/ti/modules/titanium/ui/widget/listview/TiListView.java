/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;

import com.nhaarman.listviewanimations.appearance.StickyListHeadersAdapterDecorator;
import com.nhaarman.listviewanimations.itemmanipulation.swipemenu.SwipeMenuAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.swipemenu.SwipeMenuCallback;
import com.nhaarman.listviewanimations.util.StickyListHeadersListViewWrapper;

import android.annotation.SuppressLint;
import ti.modules.titanium.ui.widget.CustomListView;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListView;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

@SuppressLint("NewApi")
public class TiListView extends TiAbsListView<CustomListView> {

    private SwipeMenuAdapter mSwipeMenuAdapater;
    private SwipeMenuCallback mMenuCallback = new SwipeMenuCallback() {
        @Override
        public void onStartSwipe(View view, int position, int direction) {

        }

        @Override
        public void onMenuShown(View view, int position, int direction) {

        }

        @Override
        public void onMenuClosed(View view, int position, int direction) {

        }

        @Override
        public void beforeMenuShow(View view, int position, int direction) {

        }

        @Override
        public void beforeMenuClose(View view, int position, int direction) {

        }

    };

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
        
        return result;
    }
    
    @Override
    protected void setListViewAdapter (TiBaseAdapter adapter) {
        mSwipeMenuAdapater = new SwipeMenuAdapter(adapter, getProxy().getActivity(), mMenuCallback);
        StickyListHeadersAdapterDecorator stickyListHeadersAdapterDecorator = new StickyListHeadersAdapterDecorator(mSwipeMenuAdapater);
        stickyListHeadersAdapterDecorator.setListViewWrapper(new StickyListHeadersListViewWrapper(listView));
        listView.setAdapter(stickyListHeadersAdapterDecorator);
    }
    
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCROLLING_ENABLED:
            listView.setScrollingEnabled(newValue);
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
    
    public void closeSwipeMenu(boolean animated) {
        if (mSwipeMenuAdapater != null) {
            if (animated) {
                mSwipeMenuAdapater.closeMenusAnimated();
            }
            else {
                mSwipeMenuAdapater.closeMenus();
            }
        }
    }
}

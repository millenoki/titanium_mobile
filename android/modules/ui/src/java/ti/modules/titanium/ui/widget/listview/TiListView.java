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
import org.appcelerator.titanium.util.TiConvert;

import com.nhaarman.listviewanimations.appearance.AnimationAdapter;
import com.nhaarman.listviewanimations.appearance.StickyListHeadersAdapterDecorator;
import com.nhaarman.listviewanimations.itemmanipulation.swipemenu.SwipeMenuAdapter;
import com.nhaarman.listviewanimations.itemmanipulation.swipemenu.SwipeMenuCallback;
import com.nhaarman.listviewanimations.util.StickyListHeadersListViewWrapper;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorInflater;

import android.annotation.SuppressLint;
import ti.modules.titanium.ui.widget.CustomListView;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListView;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

@SuppressLint("NewApi")
public class TiListView extends TiAbsListView<CustomListView> {

    private Animator mAppearAnimators = null;
    private int mAppearAnimationDuration = 300;
    private SwipeMenuAdapter mSwipeMenuAdapater;
    private boolean mNeedsSwipeMenu = true;
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
            protected void onLayout(boolean changed, int left, int top,
                    int right, int bottom) {

                super.onLayout(changed, left, top, right, bottom);
                if (changed && fProxy != null
                        && fProxy.hasListeners(TiC.EVENT_POST_LAYOUT, false)) {
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
    protected void setListViewAdapter(TiBaseAdapter adapter) {
        AnimationAdapter animationAdapter = null;
        BaseAdapter currentAdapter = adapter;
        if (mAppearAnimators != null) {
            currentAdapter = animationAdapter = new AnimationAdapter(adapter) {

                @Override
                public Animator[] getAnimators(ViewGroup parent, View view) {
                    Animator anim = mAppearAnimators.clone();
                    anim.setTarget(view);
                    return new Animator[] {anim};
                }
            };            
        }
        if (mNeedsSwipeMenu) {
            currentAdapter = mSwipeMenuAdapater = new SwipeMenuAdapter(currentAdapter, getProxy()
                    .getActivity(), mMenuCallback);
        }

        StickyListHeadersAdapterDecorator stickyListHeadersAdapterDecorator = new StickyListHeadersAdapterDecorator(
                currentAdapter);
        stickyListHeadersAdapterDecorator
                .setListViewWrapper(new StickyListHeadersListViewWrapper(
                        listView));
        if (animationAdapter != null) {
            animationAdapter.getViewAnimator().setAnimationDurationMillis(mAppearAnimationDuration);
        }
        listView.setAdapter(stickyListHeadersAdapterDecorator);
    }

    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCROLLING_ENABLED:
            listView.setScrollingEnabled(newValue);
            break;
        case "appearAnimation":
            int id = TiConvert.toInt(newValue);
            if (id != 0) {
                mAppearAnimators = AnimatorInflater.loadAnimator(getProxy().getActivity(), id) ;
            } else {
                mAppearAnimators = null;
            }
            break;
        case "appearAnimationDuration":
            mAppearAnimationDuration = TiConvert.toInt(newValue);
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
            } else {
                mSwipeMenuAdapater.closeMenus();
            }
        }
    }
}

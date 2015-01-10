/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.abslistview.AbsListViewProxy;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;

@Kroll.proxy(creatableInModule = UIModule.class)
public class ListViewProxy extends AbsListViewProxy {

	private static final String TAG = "ListViewProxy";
	
	   private static final int MSG_FIRST_ID = AbsListViewProxy.MSG_LAST_ID + 1;

	    private static final int MSG_CLOSE_PULL_VIEW = MSG_FIRST_ID;
	    private static final int MSG_SHOW_PULL_VIEW = MSG_FIRST_ID + 1;
	
	public ListViewProxy() {
		super();
	}

    @Override
	public TiUIView createView(Activity activity) {
        TiUIView view = new TiListView(this, activity);
	    LayoutParams params = view.getLayoutParams();
        params.sizeOrFillWidthEnabled = true;
        params.sizeOrFillHeightEnabled = true;
        params.autoFillsHeight = true;
        params.autoFillsHeight = true;
        params.autoFillsWidth = true;
		return view;
	}
	
	@Override
    public boolean handleMessage(final Message msg)     {

        switch (msg.what) {
            case MSG_SHOW_PULL_VIEW: {
                handleShowPullView(msg.obj);
                return true;
            }
            case MSG_CLOSE_PULL_VIEW: {
                handleClosePullView(msg.obj);
                return true;
            }
            default:
                return super.handleMessage(msg);
        }
    }
	   
    public void handleShowPullView(Object obj) {
        Boolean animated = true;
        if (obj != null) {
            animated = TiConvert.toBoolean(obj);
        }
        TiUIView listView = peekView();
        if (listView != null) {
            ((TiListView) listView).showPullView(animated);
        }
    }
    
    public void handleClosePullView(Object obj) {
        Boolean animated = true;
        if (obj != null) {
            animated = TiConvert.toBoolean(obj);
        }
        TiUIView listView = peekView();
        if (listView != null) {
            ((TiListView) listView).closePullView(animated);
        }
    }
    
    @Kroll.method()
    public void showPullView(@Kroll.argument(optional = true) Object obj) {
        if (TiApplication.isUIThread()) {
            handleShowPullView(obj);
        } else {
            Handler handler = getMainHandler();
            handler.sendMessage(handler.obtainMessage(MSG_SHOW_PULL_VIEW, obj));
        }
    }
    
    @Kroll.method()
    public void closePullView(@Kroll.argument(optional = true) Object obj) {
        if (TiApplication.isUIThread()) {
            handleClosePullView(obj);
        } else {
            Handler handler = getMainHandler();
            handler.sendMessage(handler.obtainMessage(MSG_CLOSE_PULL_VIEW, obj));
        }
    }
}

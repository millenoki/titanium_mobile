/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import in.srain.cube.views.GridViewWithHeaderAndFooter;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;

import ti.modules.titanium.ui.widget.CustomGridView;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListView;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.MotionEvent;

public class TiCollectionView extends TiAbsListView<CustomGridView> {

	public TiCollectionView(TiViewProxy proxy, Activity activity) {
		super(proxy, activity);
	}
	
	private TiDimension mColumnsWidth = null;
	
    @Override
    protected CustomGridView createListView(final Activity activity) {
        final KrollProxy fProxy = this.proxy;
        CustomGridView result = new CustomGridView(activity) {
            
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                
                super.onLayout(changed, left, top, right, bottom);
                if (changed && fProxy != null && fProxy.hasListeners(TiC.EVENT_POST_LAYOUT, false)) {
                    fProxy.fireEvent(TiC.EVENT_POST_LAYOUT, null);
                }
                if (mColumnsWidth != null && !mColumnsWidth.isUnitFixed()) {
                    setColumnWidth(mColumnsWidth.getAsPixels(this));
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
    
    protected static String getCellProxyRootType() {
        return "Ti.UI.CollectionItem";
    }
    
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCROLLING_ENABLED:
            listView.setScrollingEnabled(newValue);
            break;
        case TiC.PROPERTY_COLUMN_WIDTH:
            mColumnsWidth = TiConvert.toTiDimension(newValue, TiDimension.TYPE_WIDTH);
            listView.setNumColumns( GridViewWithHeaderAndFooter.AUTO_FIT );
            if (mColumnsWidth != null) {
                listView.setColumnWidth(mColumnsWidth.getAsPixels(listView));
            }
            break;
        case TiC.PROPERTY_NUM_COLUMNS:
            mColumnsWidth = null;
            listView.setNumColumns( TiConvert.toInt(newValue));
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
}

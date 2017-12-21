/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.annotations.Kroll;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.abslistview.AbsListSectionProxy;
import ti.modules.titanium.ui.widget.abslistview.TiCollectionViewInterface;
import android.annotation.SuppressLint;

@Kroll.proxy(creatableInModule = UIModule.class, propertyAccessors = {})
@SuppressWarnings({ "unchecked", "rawtypes" })
@SuppressLint("DefaultLocale")
public class ListSectionProxy extends AbsListSectionProxy {

    private static final String TAG = "ListSectionProxy";

    public ListSectionProxy() {
        super();
    }

    @Override
    public String getApiName() {
        return "Ti.UI.ListSection";
    }

    protected void notifyItemRangeRemoved(int itemIndex, int itemCount,
            final boolean animated) {
        TiCollectionViewInterface listView = getListView();
        if (animated && listView instanceof TiListView) {
            final int position = listView.findItemPosition(sectionIndex, itemIndex);
            ((TiListView) listView).remove(position, itemCount);
        } else {
            notifyDataChange();
        }
    }

    protected void notifyItemRangeChanged(int itemIndex, int itemCount,
            final boolean animated) {
        notifyDataChange();
    }

    protected void notifyItemRangeInserted(int itemIndex, int itemCount,
            final boolean animated) {
        TiCollectionViewInterface listView = getListView();
        if (animated && listView instanceof TiListView) {
            final int position = listView.findItemPosition(sectionIndex, itemIndex);
           ((TiListView) listView).insert(position, new Object[itemCount]);
        } else {
            notifyDataChange();
        }
    }
}

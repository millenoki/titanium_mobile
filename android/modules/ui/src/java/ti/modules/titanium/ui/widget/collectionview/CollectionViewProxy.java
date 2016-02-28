/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.abslistview.AbsListViewProxy;
import android.app.Activity;

@Kroll.proxy(creatableInModule = UIModule.class,propertyAccessors = {
        TiC.PROPERTY_SCROLL_DIRECTION,
        TiC.PROPERTY_STICKY_HEADERS,
        TiC.PROPERTY_NUM_COLUMNS,
        TiC.PROPERTY_COLUMN_WIDTH,
        TiC.PROPERTY_SCROLLING_ENABLED,
        TiC.PROPERTY_HEADER_TITLE,
        TiC.PROPERTY_FOOTER_TITLE,
//        TiC.PROPERTY_SECTIONS,
        TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE,
        TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR,
        TiC.PROPERTY_SEPARATOR_COLOR,
        TiC.PROPERTY_SEARCH_TEXT,
        TiC.PROPERTY_SEARCH_VIEW,
        TiC.PROPERTY_HEADER_VIEW,
        TiC.PROPERTY_FOOTER_VIEW,
        TiC.PROPERTY_SEARCH_VIEW_EXTERNAL,
        TiC.PROPERTY_CASE_INSENSITIVE_SEARCH,
        TiC.PROPERTY_SCROLL_HIDES_KEYBOARD
    }, propertyDontEnumAccessors = {
        TiC.PROPERTY_TEMPLATES
    })
public class CollectionViewProxy extends AbsListViewProxy {

    private static final String TAG = "CollectionViewProxy";

    public CollectionViewProxy() {
        super();
      defaultValues.put(TiC.PROPERTY_SCROLL_DIRECTION, "vertical");
    }

    @Override
    public TiUIView createView(Activity activity) {
        TiUIView view = new TiCollectionView(this, activity);
        LayoutParams params = view.getLayoutParams();
        params.sizeOrFillWidthEnabled = true;
        params.sizeOrFillHeightEnabled = true;
        params.autoFillsHeight = true;
        params.autoFillsHeight = true;
        params.autoFillsWidth = true;
        return view;
    }

    @Override
    public String getApiName() {
        return "Ti.UI.CollectionView";
    }

    @Override
    public Class sectionClass() {
        return CollectionSectionProxy.class;
    }
}

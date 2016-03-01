/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import java.util.HashMap;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.util.TiConvert;

import android.support.v7.widget.RecyclerView;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.abslistview.AbsListSectionProxy;
import ti.modules.titanium.ui.widget.collectionview.TiCollectionView.TiBaseAdapter;
@Kroll.proxy(creatableInModule = UIModule.class)
public class CollectionSectionProxy extends AbsListSectionProxy {

    private static final String TAG = "CollectionSectionProxy";

    private boolean hasHeader = false;
    private boolean hasFooter = false;

    public CollectionSectionProxy() {
        super();
    }

    @Override
    public String getApiName() {
        return "Ti.UI.CollectionSection";
    }

    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_HEADER_VIEW:
        case TiC.PROPERTY_FOOTER_VIEW:
            hasHeader = newValue != null;
            if (hasHeader && adapter instanceof TiBaseAdapter) {
                notifyItemRangeChanged(RecyclerView.NO_POSITION, 1);
            }
            break;
        case TiC.PROPERTY_HEADER_TITLE:
        case TiC.PROPERTY_FOOTER_TITLE:
            hasFooter = newValue != null;
            if (hasFooter && adapter instanceof TiBaseAdapter) {
                notifyItemRangeChanged(getItemCount() - 1, 1);
            }
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

    @Override
    protected void updateCurrentItemCount() {
        if (hidden && !showHeaderWhenHidden) {
            mCurrentItemCount = 0;
            return;
        }
        int totalCount = 0;
        if (isFilterOn()) {
            totalCount = filterIndices.size();
        } else {
            totalCount = mItemCount;
        }
        // else if (!hideHeaderOrFooter() && hasHeader()) {
        // totalCount += 1;
        // }
        //
        totalCount -= getHiddenCount();

        if (!hideHeaderOrFooter() && (totalCount > 0 || !hideWhenEmpty)) {
//            if (hasHeader()) {
//                totalCount += 1;
//            }
            // footer must be counted in!
            if (hasFooter()) {
                totalCount += 1;
            }
        }
        mCurrentItemCount = totalCount;
    }

    @Override
    public boolean hasHeader() {
        return !hideHeaderOrFooter() && hasHeader;
    }

    @Override
    public boolean hasFooter() {
        return !hideHeaderOrFooter() && hasFooter;
    }

//    public int getUserItemIndexFromSectionPosition(final int position) {
//        int result = position;
//        if (hasHeader()) {
//            result -= 1;
//        }
//        return super.getUserItemIndexFromSectionPosition(result);
//    }

//    public int getUserItemInversedIndexFromSectionPosition(final int position) {
//        int result = position;
//        if (hasHeader()) {
//            result -= 1;
//        }
//        return super.getUserItemInversedIndexFromSectionPosition(result);
//    }

    @Override
    public HashMap getListItem(int itemPosition) {
        boolean hasHeader = hasHeader();
        if (hasHeader && itemPosition == RecyclerView.NO_POSITION) {
            Object item = getProperty(TiC.PROPERTY_HEADER_VIEW);
            if (item instanceof HashMap) {
                return (HashMap) item;
            }
            return null;
        }

        if (hasFooter() && itemPosition == getItemCount()) {
            Object item = getProperty(TiC.PROPERTY_FOOTER_VIEW);
            if (item instanceof HashMap) {
                return (HashMap) item;
            }
            return null;
        }
//        if (hasHeader) {
//            position -= 1;
//        }

        return super.getListItem(itemPosition);
    }

    @Override
    public String getTemplateByIndex(int itemPosition) {
        boolean hasHeader = hasHeader();
        if (hasHeader && itemPosition == RecyclerView.NO_POSITION) {
            Object item = getProperty(TiC.PROPERTY_HEADER_VIEW);
            if (item instanceof HashMap) {
                return TiConvert.toString((HashMap) item, TiC.PROPERTY_TEMPLATE,
                        "header");
            } else {
                return "__customHeader__";
                // we need to return a unique
            }
        }
        if (hasFooter() && itemPosition == getItemCount()) {
            Object item = getProperty(TiC.PROPERTY_FOOTER_VIEW);
            if (item instanceof HashMap) {
                return TiConvert.toString((HashMap) item, TiC.PROPERTY_TEMPLATE,
                        "footer");
            }
            return "__customFooter__";
        }
        if (isFilterOn()) {
            return TiConvert.toString(
                    getItemDataAt(filterIndices.get(itemPosition)),
                    TiC.PROPERTY_TEMPLATE);
        } else {
            return TiConvert.toString(getItemDataAt(itemPosition),
                    TiC.PROPERTY_TEMPLATE);
        }
    }
    
    @Override
    public void notifyDataChange() {
        if (adapter instanceof TiBaseAdapter) {
            getMainHandler().post(new Runnable() {
                @Override
                public void run() {
                    ((TiBaseAdapter) adapter).notifySectionDataSetChanged();
                }
            });
        } else {
            super.notifyDataChange();
        }
    }

    @Override
    protected void notifyItemRangeRemoved(int childPositionStart, int itemCount) {
        if (adapter instanceof TiBaseAdapter) {
            ((TiBaseAdapter) adapter)
                    .notifySectionItemRangeRemoved(this.sectionIndex, childPositionStart, itemCount);
        }
    }

    @Override
    protected void notifyItemRangeChanged(int childPositionStart, int itemCount) {
        if (adapter instanceof TiBaseAdapter) {
            ((TiBaseAdapter) adapter)
                    .notifySectionItemRangeChanged(this.sectionIndex, childPositionStart, itemCount);
        }
    }

    @Override
    protected void notifyItemRangeInserted(int childPositionStart, int itemCount) {
        if (adapter instanceof TiBaseAdapter) {
            ((TiBaseAdapter) adapter)
                    .notifySectionItemRangeInserted(this.sectionIndex, childPositionStart, itemCount);
        }

    }
    
    @Override
    public void setVisible(boolean value) {
        if (hidden == !value) return;
        hidden = !value;
        if (adapter instanceof TiBaseAdapter) {
            if (hidden) {
                ((TiBaseAdapter) adapter).collapseSection(this.sectionIndex, true);
            } else {
                ((TiBaseAdapter) adapter).expandSection(this.sectionIndex, true);
            }
        } else {
            notifyDataChange();
        }
        
    }
}

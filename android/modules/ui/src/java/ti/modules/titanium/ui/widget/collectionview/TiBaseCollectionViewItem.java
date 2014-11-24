/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import android.content.Context;
import android.util.AttributeSet;

public class TiBaseCollectionViewItem extends TiCompositeLayout{

	
	public int sectionIndex = -1;
	public int itemIndex = -1;
	public TiBaseCollectionViewItem(Context context) {
		super(context);
        setId(TiCollectionView.listContentId);
	}
	
	public TiBaseCollectionViewItem(Context context, AttributeSet set) {
		super(context, set);
        setId(TiCollectionView.listContentId);
	}
	
	
	public void setCurrentItem(int sectionIndex, int itemIndex, CollectionSectionProxy sectionProxy)
	{
		this.sectionIndex = sectionIndex;
		this.itemIndex = itemIndex;
	}
	
	public int getItemIndex() {
	    return itemIndex;
	}
	public int getSectionIndex() {
        return sectionIndex;
    }
	
	public TiCollectionItem getCollectionItem() {
        return (TiCollectionItem) getView();
    }
    
	public CollectionItemProxy getProxy() {
	    TiUIView view  = getView();
        if (view != null) {
            return (CollectionItemProxy)view.getProxy();
        }
        return null;
	}
    
	public KrollProxy getViewProxyFromBinding(String binding) {
		TiUIView view  = getView();
		if (view != null) {
		    CollectionItemProxy proxy = (CollectionItemProxy)view.getProxy();
			if (proxy != null)
			{
				return proxy.getProxyFromBinding(binding);
			}
		}
		return null;
	}
}

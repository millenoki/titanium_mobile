/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.picker;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;


public class TiUISpinner extends TiUIPicker
{
	private static final String TAG = "TiUISpinner";


	public TiUISpinner(TiViewProxy proxy)
	{
		super(proxy);
	}
	public TiUISpinner(TiViewProxy proxy, Activity activity)
	{
		this(proxy);
		TiCompositeLayout layout = new TiCompositeLayout(activity, LayoutArrangement.HORIZONTAL, this);
		layout.setEnableHorizontalWrap(true);
		setNativeView(layout);
	}

	@Override
	protected void refreshNativeView()
	{
		if (children == null || children.size() == 0) {
			return;
		}
		for (TiUIView child : children) {
			refreshColumn((TiUISpinnerColumn)child);
		}
	}
	
	private void refreshColumn(int columnIndex)
	{
		if (columnIndex < 0 || children == null || children.size() == 0 || columnIndex > (children.size() + 1)) {
			return;
		}
		refreshColumn((TiUISpinnerColumn)children.get(columnIndex));
	}
	private void refreshColumn(TiUISpinnerColumn column)
	{
		if (column == null) {
			return;
		}
		column.refreshNativeView();
	}

	@Override
	public int getSelectedRowIndex(int columnIndex)
	{
		if (columnIndex < 0 || children == null || children.size() == 0 || columnIndex >= children.size()) {
			Log.w(TAG, "Ignoring effort to get selected row index for out-of-bounds columnIndex " + columnIndex);
			return -1;
		}
		TiUIView child = children.get(columnIndex);
		if (child instanceof TiUISpinnerColumn) {
			return ((TiUISpinnerColumn)child).getSelectedRowIndex();
		} else {
			Log.w(TAG, "Could not locate column " + columnIndex + ".  Ignoring effort to get selected row index in that column.");
			return -1;
		}
	}
	@Override
	public void selectRow(int columnIndex, int rowIndex, boolean animated)
	{
		if (children == null || columnIndex >= children.size()) {
			Log.w(TAG, "Column " + columnIndex + " does not exist.  Ignoring effort to select a row in that column.");
			return;
		}
		TiUIView child = children.get(columnIndex);
		if (child instanceof TiUISpinnerColumn) {
			((TiUISpinnerColumn)child).selectRow(rowIndex);
		} else {
			Log.w(TAG, "Could not locate column " + columnIndex + ".  Ignoring effort to select a row in that column.");
		}
	}

	@Override
	public void onColumnModelChanged(int columnIndex)
	{
		refreshColumn(columnIndex);
	}
	@Override
	public void onRowChanged(int columnIndex, int rowIndex)
	{
		refreshColumn(columnIndex);
	}

	private void propagateProperty(String key, Object value)
	{
		if (children != null && children.size() > 0) {
			for (TiUIView child : children) {
				if (child instanceof TiUISpinnerColumn) {
					child.getProxy().setPropertyAndFire(key, value);
				}
			}
		}
	}
	
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_VISIBLE_ITEMS:
        case TiC.PROPERTY_SELECTION_INDICATOR:
            propagateProperty(key, newValue);
            if (changedProperty && TiC.PROPERTY_VISIBLE_ITEMS.equals(key)) {
                forceRequestLayout();
            }
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

	@Override
	public void add(TiUIView child, int index)
	{
		if (proxy.hasProperty(TiC.PROPERTY_VISIBLE_ITEMS)) {
			child.getProxy().setPropertyAndFire(TiC.PROPERTY_VISIBLE_ITEMS, TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_VISIBLE_ITEMS)));
		}
		if (proxy.hasProperty(TiC.PROPERTY_SELECTION_INDICATOR)) {
			child.getProxy().setPropertyAndFire(TiC.PROPERTY_SELECTION_INDICATOR, TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_SELECTION_INDICATOR)));
		}
		super.add(child, index);
		if (child instanceof TiUISpinnerColumn) {
            ((TiUISpinnerColumn)child).refreshNativeView();
        }
	}
	public void forceRequestLayout()
	{
		if (children != null && children.size() > 0) {
			for (TiUIView child : children) {
				if (child instanceof TiUISpinnerColumn) {
					((TiUISpinnerColumn)child).forceRequestLayout();
				}
			}
		}
		layoutNativeView();
	}
}

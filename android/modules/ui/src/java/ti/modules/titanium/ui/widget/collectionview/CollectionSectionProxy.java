/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */


package ti.modules.titanium.ui.widget.collectionview;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.KrollProxyReusableListener;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.ViewProxy;
import ti.modules.titanium.ui.widget.collectionview.TiCollectionView.TiBaseAdapter;
import android.annotation.SuppressLint;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

@Kroll.proxy(creatableInModule = UIModule.class, propertyAccessors = {})
@SuppressWarnings({ "unchecked", "rawtypes" })
@SuppressLint("DefaultLocale")
public class CollectionSectionProxy extends ViewProxy {

	private static final String TAG = "CollectionSectionProxy";
	private ArrayList<CollectionItemData> listItemData;
	private int mItemCount;
    private int mCurrentItemCount = 0;
	private TiBaseAdapter adapter;
	private ArrayList<Object> itemProperties;
	private ArrayList<Integer> filterIndices;
	private boolean preload;
	private ArrayList<Boolean> hiddenItems;
	boolean hidden = false;

	private String headerTitle;
	private String footerTitle;

	private Object headerViewArg;
	private Object footerViewArg;
	
	private TiViewProxy headerView;
	private TiViewProxy footerView;
	
	private int sectionIndex;

	private WeakReference<TiCollectionView> listView;

	private static final int MSG_FIRST_ID = TiViewProxy.MSG_LAST_ID + 1;

	private static final int MSG_SET_ITEMS = MSG_FIRST_ID + 700;
	private static final int MSG_APPEND_ITEMS = MSG_FIRST_ID + 701;
	private static final int MSG_INSERT_ITEMS_AT = MSG_FIRST_ID + 702;
	private static final int MSG_DELETE_ITEMS_AT = MSG_FIRST_ID + 703;
	private static final int MSG_GET_ITEM_AT = MSG_FIRST_ID + 704;
	private static final int MSG_REPLACE_ITEMS_AT = MSG_FIRST_ID + 705;
	private static final int MSG_UPDATE_ITEM_AT = MSG_FIRST_ID + 706;
	private static final int MSG_GET_ITEMS = MSG_FIRST_ID + 707;

	private static HashMap<String, String> toPassProps;

	public class CollectionItemData {
		private KrollDict properties;
		private String searchableText;
		private String template = null;
		private boolean visible = true;

		public CollectionItemData(KrollDict properties) {
			setProperties(properties);
		}
		
		private void updateSearchableAndVisible() {
		    if (properties.containsKey(TiC.PROPERTY_PROPERTIES)) {
                Object props = properties.get(TiC.PROPERTY_PROPERTIES);
                if (props instanceof HashMap) {
                    HashMap<String, Object> propsHash = (HashMap<String, Object>) props;
                    if (propsHash.containsKey(TiC.PROPERTY_SEARCHABLE_TEXT)) {
                        searchableText = TiConvert.toString(propsHash,
                                TiC.PROPERTY_SEARCHABLE_TEXT);
                    }
                    if (propsHash.containsKey(TiC.PROPERTY_VISIBLE)) {
                        visible = TiConvert.toBoolean(propsHash,
                                TiC.PROPERTY_VISIBLE, true);
                    }
                }
            }
		}

		public KrollDict getProperties() {
			return properties;
		}

		public String getSearchableText() {
			return searchableText;
		}
		

		public boolean isVisible() {
			return visible;
		}


		public String getTemplate() {
			return template;
		}

        public void setProperties(KrollDict d) {
            this.properties = d;
            if (properties.containsKey(TiC.PROPERTY_TEMPLATE)) {
                this.template = properties.getString(TiC.PROPERTY_TEMPLATE);
            }
            else {
                this.template = getCollectionView().getDefaultTemplateBinding();
            }
            // set searchableText
            updateSearchableAndVisible();
        }
        
        public void setProperty(String binding, String key, Object value) {
            if (properties.containsKey(binding)) {
                ((HashMap)properties.get(binding)).put(key, value);
            }
        }
	}

	public CollectionSectionProxy() {
		// initialize variables
		if (toPassProps == null) {
			toPassProps = new HashMap<String, String>();
			toPassProps.put(TiC.PROPERTY_ACCESSORY_TYPE,
					TiC.PROPERTY_ACCESSORY_TYPE);
			toPassProps.put(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR,
					TiC.PROPERTY_BACKGROUND_SELECTED_COLOR);
			toPassProps.put(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE,
					TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE);
			toPassProps.put(TiC.PROPERTY_SELECTED_BACKGROUND_GRADIENT,
					TiC.PROPERTY_BACKGROUND_SELECTED_GRADIENT);
			toPassProps.put(TiC.PROPERTY_ROW_HEIGHT, TiC.PROPERTY_HEIGHT);
			toPassProps.put(TiC.PROPERTY_MIN_ROW_HEIGHT, TiC.PROPERTY_MIN_HEIGHT);
			toPassProps.put(TiC.PROPERTY_MAX_ROW_HEIGHT, TiC.PROPERTY_MAX_HEIGHT);
		}
		listItemData = new ArrayList<CollectionItemData>();
		filterIndices = new ArrayList<Integer>();
		hiddenItems = new ArrayList<Boolean>();
		mItemCount = 0;
		preload = false;
	}

	public void handleCreationDict(KrollDict dict) {
		// getting header/footer titles from creation dictionary
		if (dict.containsKey(TiC.PROPERTY_HEADER_TITLE)) {
			headerTitle = TiConvert.toString(dict, TiC.PROPERTY_HEADER_TITLE);
		}
		if (dict.containsKey(TiC.PROPERTY_FOOTER_TITLE)) {
			footerTitle = TiConvert.toString(dict, TiC.PROPERTY_FOOTER_TITLE);
		}
		if (dict.containsKey(TiC.PROPERTY_HEADER_VIEW)) {
			headerViewArg = dict.get(TiC.PROPERTY_HEADER_VIEW);
		}
		if (dict.containsKey(TiC.PROPERTY_FOOTER_VIEW)) {
			footerViewArg = dict.get(TiC.PROPERTY_FOOTER_VIEW);
		}
		if (dict.containsKey(TiC.PROPERTY_ITEMS)) {
			handleSetItems(dict.get(TiC.PROPERTY_ITEMS));
		}
		if (dict.containsKey(TiC.PROPERTY_VISIBLE)) {
			setVisible(dict.optBoolean(TiC.PROPERTY_VISIBLE, true));
		}
	}

	public void setAdapter(TiBaseAdapter a) {
		adapter = a;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setHeaderView(TiViewProxy headerView) {
		this.headerViewArg = headerView;
		if (this.headerView != null) {
            this.headerView.releaseViews(true);
            this.headerView.setParent(null);
            this.headerView = null;
        }
		updateCurrentItemCount();
		notifyDataChange();
	}

	@Kroll.method
	@Kroll.getProperty
	public Object getHeaderView() {
		return headerViewArg;
	}
	
	public TiViewProxy getCurrentHeaderViewProxy() {
	    return headerView;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setFooterView(TiViewProxy footerView) {
		this.footerViewArg = footerView;
		if (this.footerView != null) {
		    this.footerView.releaseViews(true);
            this.footerView.setParent(null);
            this.footerView = null;
		}
		notifyDataChange();
	}

	@Kroll.method
	@Kroll.getProperty
	public Object getFooterView() {
		return footerViewArg;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setHeaderTitle(String headerTitle) {
		this.headerTitle = headerTitle;
        updateCurrentItemCount();
		notifyDataChange();
	}

	@Kroll.method
	@Kroll.getProperty
	public String getHeaderTitle() {
		return headerTitle;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setFooterTitle(String headerTitle) {
		this.footerTitle = headerTitle;
		notifyDataChange();
	}

	@Kroll.method
	@Kroll.getProperty
	public String getFooterTitle() {
		return footerTitle;
	}
	
	public boolean hasHeader() {
	    return headerViewArg != null || headerTitle != null;
	}

	public void notifyDataChange() {
		if (adapter == null) return;
        updateCurrentItemCount();
		getMainHandler().post(new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
			}
		});
	}

	public String getHeaderOrFooterTitle(int index) {
		if (isHeaderTitle(index)) {
			return headerTitle;
		} else if (isFooterTitle(index)) {
			return footerTitle;
		}
		return "";
	}

	public View getOrCreateHeaderView() {
		return layoutHeaderOrFooterView(headerViewArg, this, false);
	}
	
	public View getOrCreateFooterView(int index) {
        if (isFooterView(index)) {
            return layoutHeaderOrFooterView(footerViewArg, this, true);
        }
        return null;
    }

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {

		case MSG_SET_ITEMS: {
			AsyncResult result = (AsyncResult) msg.obj;
			handleSetItems(result.getArg());
			result.setResult(null);
			return true;
		}

		case MSG_GET_ITEMS: {
			AsyncResult result = (AsyncResult) msg.obj;
			result.setResult(itemProperties.toArray());
			return true;
		}

		case MSG_APPEND_ITEMS: {
			AsyncResult result = (AsyncResult) msg.obj;
			handleAppendItems(result.getArg());
			result.setResult(null);
			return true;
		}

		case MSG_INSERT_ITEMS_AT: {
			AsyncResult result = (AsyncResult) msg.obj;
			KrollDict data = (KrollDict) result.getArg();
			int index = data.getInt(TiC.EVENT_PROPERTY_INDEX);
			handleInsertItemsAt(index, data.get(TiC.PROPERTY_DATA));
			result.setResult(null);
			return true;
		}

		case MSG_DELETE_ITEMS_AT: {
			AsyncResult result = (AsyncResult) msg.obj;
			KrollDict data = (KrollDict) result.getArg();
			int index = data.getInt(TiC.EVENT_PROPERTY_INDEX);
			int count = data.getInt(TiC.PROPERTY_COUNT);
			handleDeleteItemsAt(index, count);
			result.setResult(null);
			return true;
		}

		case MSG_REPLACE_ITEMS_AT: {
			AsyncResult result = (AsyncResult) msg.obj;
			KrollDict data = (KrollDict) result.getArg();
			int index = data.getInt(TiC.EVENT_PROPERTY_INDEX);
			int count = data.getInt(TiC.PROPERTY_COUNT);
			handleReplaceItemsAt(index, count, data.get(TiC.PROPERTY_DATA));
			result.setResult(null);
			return true;
		}

		case MSG_GET_ITEM_AT: {
			AsyncResult result = (AsyncResult) msg.obj;
			KrollDict item = handleGetItemAt(TiConvert.toInt(result.getArg()));
			result.setResult(item);
			return true;
		}

		case MSG_UPDATE_ITEM_AT: {
			AsyncResult result = (AsyncResult) msg.obj;
			KrollDict data = (KrollDict) result.getArg();
			int index = data.getInt(TiC.EVENT_PROPERTY_INDEX);
			handleUpdateItemAt(index, data.get(TiC.PROPERTY_DATA));
			result.setResult(null);
			return true;
		}
		default: {
			return super.handleMessage(msg);
		}

		}
	}
	
	@Kroll.method
    public KrollProxy getBinding(final int itemIndex, final String bindId) {
        if (listView != null) {
            return listView.get().getChildByBindId(this.sectionIndex, itemIndex, bindId);
        }
        return null;
    }

	@Kroll.method
	public KrollDict getItemAt(int index) {
		if (TiApplication.isUIThread()) {
			return handleGetItemAt(index);
		} else {
			return (KrollDict) TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_GET_ITEM_AT), index);
		}
	}

	private KrollDict handleGetItemAt(int index) {
		if (itemProperties != null && index >= 0
				&& index < itemProperties.size()) {
			return new KrollDict((HashMap) itemProperties.get(index));
		}
		return null;
	}

	private int getRealPosition(int position) {
		int hElements = getHiddenCountUpTo(position);
		int diff = 0;
		for (int i = 0; i < hElements; i++) {
			diff++;
			if (hiddenItems.get(position + diff))
				i--;
		}
		return (position + diff);
	}
	
	private int getInverseRealPosition(int position) {
		int hElements = getHiddenCountUpTo(position);
		int diff = 0;
		for (int i = 0; i < hElements; i++) {
			diff++;
			if (hiddenItems.get(position + diff))
				i--;
		}
		return (position - diff);
	}


	private int getHiddenCountUpTo(int location) {
		int count = 0;
		for (int i = 0; i < location; i++) {
			if (hiddenItems.get(i))
				count++;
		}
		return count;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setItems(Object data) {
		if (TiApplication.isUIThread()) {
			handleSetItems(data);
		} else {
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_SET_ITEMS), data);
		}
	}

	@Kroll.method
	@Kroll.getProperty
	public Object[] getItems() {
		if (itemProperties == null) {
			return new Object[0];
		} else if (TiApplication.isUIThread()) {
			return itemProperties.toArray();
		} else {
			return (Object[]) TiMessenger
					.sendBlockingMainMessage(getMainHandler().obtainMessage(
							MSG_GET_ITEMS));
		}
	}

	@Kroll.method
	public void appendItems(Object data) {
		if (TiApplication.isUIThread()) {
			handleAppendItems(data);
		} else {
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_APPEND_ITEMS), data);
		}
	}

	public boolean isIndexValid(int index) {
		return (index >= 0) ? true : false;
	}

	@Kroll.method
	public void insertItemsAt(int index, Object data) {
		if (!isIndexValid(index)) {
			return;
		}

		if (TiApplication.isUIThread()) {
			handleInsertItemsAt(index, data);
		} else {
			KrollDict d = new KrollDict();
			d.put(TiC.PROPERTY_DATA, data);
			d.put(TiC.EVENT_PROPERTY_INDEX, index);
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_INSERT_ITEMS_AT), d);
		}
	}

	@Kroll.method
	public void deleteItemsAt(int index, int count) {
		if (!isIndexValid(index)) {
			return;
		}

		if (TiApplication.isUIThread()) {
			handleDeleteItemsAt(index, count);
		} else {
			KrollDict d = new KrollDict();
			d.put(TiC.EVENT_PROPERTY_INDEX, index);
			d.put(TiC.PROPERTY_COUNT, count);
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_DELETE_ITEMS_AT), d);
		}
	}

	@Kroll.method
	public void replaceItemsAt(int index, int count, Object data) {
		if (!isIndexValid(index)) {
			return;
		}

		if (TiApplication.isUIThread()) {
			handleReplaceItemsAt(index, count, data);
		} else {
			KrollDict d = new KrollDict();
			d.put(TiC.EVENT_PROPERTY_INDEX, index);
			d.put(TiC.PROPERTY_COUNT, count);
			d.put(TiC.PROPERTY_DATA, data);
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_REPLACE_ITEMS_AT), d);
		}
	}

	@Kroll.method
	public void updateItemAt(int index, Object data) {
		if (!isIndexValid(index) || !(data instanceof HashMap)) {
			return;
		}

		if (TiApplication.isUIThread()) {
			handleUpdateItemAt(index, data);
		} else {
			KrollDict d = new KrollDict();
			d.put(TiC.EVENT_PROPERTY_INDEX, index);
			d.put(TiC.PROPERTY_DATA, data );
			TiMessenger.sendBlockingMainMessage(
					getMainHandler().obtainMessage(MSG_UPDATE_ITEM_AT), d);
		}
	}
	
	public void updateItemAt(int index, String binding, String key, Object value) {
	    if (index < 0 || index >= mItemCount) {
	        return;
	    }
	    if (itemProperties != null) {
	        HashMap itemProp = (HashMap) itemProperties.get(index);
	        if (!itemProp.containsKey(binding)) {
	            itemProp.put(binding, new HashMap<String, Object>());
	        }
	        ((HashMap)itemProp.get(binding)).put(key, value);
        }
	    CollectionItemData itemD = getItemDataAt(index);
	    itemD.setProperty(binding, key, value);
    }
	
	@Kroll.method
	public void hide() {
        setVisible(false);
	}
	
	@Kroll.method
	public void show() {
		setVisible(true);
	}
	
	@Kroll.method
	@Kroll.setProperty
	public void setVisible(boolean value) {
		if (hidden == !value) return;
        hidden = !value;
		notifyDataChange();
	}
	
	@Kroll.method
	@Kroll.getProperty
	public boolean getVisible() {
		return !hidden;
	}

	
	public void processPreloadData() {
		if (itemProperties != null && preload) {
			handleSetItems(itemProperties.toArray());
			preload = false;
		}
	}

	public void refreshItems() {
		handleSetItems(itemProperties.toArray());
	}

	private void processData(Object[] items, int offset) {
		if (listItemData == null) {
			return;
		}

		// Second pass we would merge properties
		for (int i = 0; i < items.length; i++) {
			Object itemData = items[i];
			if (itemData instanceof HashMap) {
				KrollDict d = new KrollDict((HashMap) itemData);
				CollectionItemData itemD = new CollectionItemData(d);
				listItemData.add(i + offset, itemD);
				hiddenItems.add(i + offset, !itemD.isVisible());
			}
		}
		updateCurrentItemCount();
		// Notify adapter that data has changed.
		if (preload == false) {
	        adapter.notifyDataSetChanged();
		}
	}

	private void handleSetItems(Object data) {

		if (data instanceof Object[]) {
			Object[] items = (Object[]) data;
			itemProperties = new ArrayList<Object>(Arrays.asList(items));
			listItemData.clear();
			hiddenItems.clear();
			// only process items when listview's properties is processed.
			if (getCollectionView() == null) {
				preload = true;
				return;
			}
			mItemCount = items.length;
			processData(items, 0);

		} else {
			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
		}
	}

	private void handleAppendItems(Object data) {
		if (data instanceof Object[]) {
			Object[] views = (Object[]) data;
			if (itemProperties == null) {
				itemProperties = new ArrayList<Object>(Arrays.asList(views));
			} else {
				for (Object view : views) {
					itemProperties.add(view);
				}
			}
			// only process items when listview's properties is processed.
			if (getCollectionView() == null) {
				preload = true;
				return;
			}
			// we must update the itemCount before notify data change. If we
			// don't, it will crash
			int count = mItemCount;
			mItemCount += views.length;

			processData(views, count);

		} else {
			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
		}
	}

	private void handleInsertItemsAt(int index, Object data) {
        TiCollectionView listView = getCollectionView();
        if (listView != null) {
            int position = listView.findItemPosition(sectionIndex, index) - listView.getHeaderViewCount();
            if (data instanceof Object[]) {
                listView.insert(position, (Object[])data);
            }
            else {
                listView.insert(position, data);
            }
        }
        else {
            if (data instanceof Object[]) {
                Object[] views = (Object[]) data;

                if (itemProperties == null) {
                    itemProperties = new ArrayList<Object>(Arrays.asList(views));
                } else {
                    if (index < 0 || index > itemProperties.size()) {
                        Log.e(TAG, "Invalid index to handleInsertItem",
                                Log.DEBUG_MODE);
                        return;
                    }
                    int counter = index;
                    for (Object view : views) {
                        itemProperties.add(counter, view);
                        counter++;
                    }
                }
                // only process items when listview's properties is processed.
                preload = true;
                
            } else {
                Log.e(TAG, "Invalid argument type to insertItemsAt",
                        Log.DEBUG_MODE);
            }
        }
	}
	
	private void handleUpdateItemAt(int itemIndex, Object data) {
	    if (itemProperties == null) {
	        return;
	    }
	    int nonRealItemIndex = itemIndex;
//	    if (hasHeader()) {
//	        nonRealItemIndex += 1;
//	    }
	    
	    TiCollectionView listView = getCollectionView();
        
	    HashMap currentItem = KrollDict.merge((HashMap)itemProperties.get(itemIndex), (HashMap)(data));
	    if (currentItem == null) return;
	    itemProperties.set(itemIndex, currentItem);
	    // only process items when listview's properties is processed.
        if (listView == null) {
            preload = true;
            return;
        }
        View content = listView.getCellAt(this.sectionIndex, itemIndex);
        KrollDict d = new KrollDict(currentItem);
        CollectionItemData itemD = getItemDataAt(itemIndex);
        itemD.setProperties(d);
//        listItemData.set(index, itemD);
        hiddenItems.set(itemIndex, !itemD.isVisible());
        
        if (content != null) {
            TiBaseCollectionViewItem listItem = (TiBaseCollectionViewItem) content.findViewById(TiCollectionView.listContentId);
            if (listItem != null) {
                if (listItem.getItemIndex() == itemIndex) {
                    TiCollectionViewTemplate template = getCollectionView().getTemplate(itemD.getTemplate());
                    populateViews(d, listItem, template, nonRealItemIndex, this.sectionIndex, content, false);
                }
                else {
                    Log.d(TAG, "wrong item index", Log.DEBUG_MODE);
                }
                return;
            }
        }
        notifyDataChange();
    }

	private boolean deleteItemsData(int index, int count) {
		boolean delete = false;
		
		while (count > 0) {
			if (index < itemProperties.size()) {
				itemProperties.remove(index);
				mItemCount--;
				delete = true;
			}
			if (index < listItemData.size()) {
				listItemData.remove(index);
			}
			if (index < hiddenItems.size()) {
				hiddenItems.remove(index);
			}
			count--;
		}
		updateCurrentItemCount();
		return delete;
	}
	
	public Object deleteItemData(int index) {
        if (0 <= index && index < itemProperties.size()) {
            hiddenItems.remove(index);
            listItemData.remove(index);
            mItemCount --;
            updateCurrentItemCount();
            return itemProperties.remove(index);
        }
        return null;
    }
	
	public void insertItemData(int index, Object data) {
	    if (itemProperties == null) {
            itemProperties = new ArrayList<Object>();
            itemProperties.add(data);
        } else {
            if (index < 0 || index > itemProperties.size()) {
                Log.e(TAG, "Invalid index to handleInsertItem",
                        Log.DEBUG_MODE);
                return;
            }
            itemProperties.add(data);
        }
        // only process items when listview's properties is processed.
        if (getCollectionView() == null) {
            preload = true;
            return;
        }

        mItemCount += 1;
        if (listItemData != null && data instanceof HashMap) {
            KrollDict d = new KrollDict((HashMap) data);
            CollectionItemData itemD = new CollectionItemData(d);
            listItemData.add(index, itemD);
            hiddenItems.add(index, !itemD.isVisible());
        }
        updateCurrentItemCount();
    }

	private void handleDeleteItemsAt(int index, int count) {
	    TiCollectionView listView = getCollectionView();
	    if (listView != null) {
	        int position = listView.findItemPosition(sectionIndex, index);
	        listView.remove(position, count);
	    }
	    else {
	        deleteItemsData(index, count);
	        notifyDataChange();
	    }
		
	}

	private void handleReplaceItemsAt(int index, int count, Object data) {
		if (count == 0) {
			handleInsertItemsAt(index, data);
		} else if (deleteItemsData(index, count)) {
			handleInsertItemsAt(index, data);
		}
	}

//	private void handleUpdateItemAt(int index, Object data) {
//		handleReplaceItemsAt(index, 1, data);
//		setProperty(TiC.PROPERTY_ITEMS, itemProperties.toArray());
//	}

	/**
	 * This method creates a new cell and fill it with content. getView() calls
	 * this method when a view needs to be created.
	 * 
	 * @param sectionIndex
	 *            Entry's index relative to its section
	 * @return
	 */
	public void generateCellContent(int sectionIndex, KrollDict data,
			CollectionItemProxy itemProxy, TiBaseCollectionViewItem itemContent, TiCollectionViewTemplate template,
			int itemPosition, View item_layout) {
		// Create corresponding TiUIView for item proxy
		TiCollectionItem item = new TiCollectionItem(itemProxy, itemContent, item_layout);
		itemProxy.setView(item);
		itemContent.setView(item);
		itemProxy.realizeViews();

		if (data != null && template != null) {
			populateViews(data, itemContent, template, itemPosition,
					sectionIndex, item_layout, false);
		}
	}
	
	public int getUserItemIndexFromSectionPosition(final int position) {
	    int result = position;
//	    if (hasHeader()) {
//	        result -= 1;
//        }
	    return getRealPosition(result);
	}

	public void populateViews(KrollDict data, TiBaseCollectionViewItem cellContent, TiCollectionViewTemplate template, int itemIndex, int sectionIndex,
			View item_layout, boolean reusing) {
	    TiCollectionItem listItem = (TiCollectionItem)cellContent.getView();
		// Handling root item, since that is not in the views map.
		if (listItem == null) {
			return;
		}
		
		int realItemIndex = getUserItemIndexFromSectionPosition(itemIndex);
		cellContent.setCurrentItem(sectionIndex, realItemIndex, this);
		
		data = template.prepareDataDict(data);
		CollectionItemProxy itemProxy = (CollectionItemProxy) cellContent.getView().getProxy();
		itemProxy.setCurrentItem(sectionIndex, itemIndex, this);

		KrollDict listItemProperties;
		String itemId = null;

		if (data.containsKey(TiC.PROPERTY_PROPERTIES)) {
			listItemProperties = new KrollDict(
					(HashMap) data.get(TiC.PROPERTY_PROPERTIES));
		} else {
			listItemProperties = new KrollDict();
		}
		ProxyCollectionItem rootItem = itemProxy.getCollectionItem();
		
//		if (!reusing) {
	        KrollDict listViewProperties = getCollectionView().getProxy().getProperties();
		    for (Map.Entry<String, String> entry : toPassProps.entrySet()) {
	            String inProp = entry.getKey();
	            String outProp = entry.getValue();
	            if (!listItemProperties.containsKey(outProp) && !rootItem.containsKey(outProp) && listViewProperties.containsKey(inProp)) {
	                listItemProperties.put(outProp, listViewProperties.get(inProp));
	            }
	        }
//		}
		

		// find out if we need to update itemId
		if (listItemProperties.containsKey(TiC.PROPERTY_ITEM_ID)) {
			itemId = TiConvert.toString(listItemProperties
					.get(TiC.PROPERTY_ITEM_ID));
		}

		// update extra event data for list item
		itemProxy.setEventOverrideDelegate(itemProxy);

		HashMap<String, ProxyCollectionItem> views = itemProxy.getBindings();
		// Loop through all our views and apply default properties
		for (String binding : views.keySet()) {
			ProxyCollectionItem viewItem = views.get(binding);
			KrollProxy proxy  = viewItem.getProxy();
			if (proxy instanceof TiViewProxy) {
			    ((TiViewProxy) proxy).getOrCreateView();
			}
			KrollProxyListener modelListener = (KrollProxyListener) proxy.getModelListener();
			if (!(modelListener instanceof KrollProxyReusableListener)) {
                continue;
			}
			if (modelListener instanceof TiUIView) {
	            ((TiUIView)modelListener).setTouchDelegate((TiTouchDelegate) listItem);
            }
			// update extra event data for views
			proxy.setEventOverrideDelegate(itemProxy);
			// if binding is contain in data given to us, process that data,
			// otherwise
			// apply default properties.
			if (reusing) {
			    ((KrollProxyReusableListener) modelListener).setReusing(true);
			}
			KrollDict diffProperties = viewItem
                    .generateDiffProperties((HashMap) data.get(binding));
			
			if (diffProperties != null && !diffProperties.isEmpty()) {
                modelListener.processProperties(diffProperties);
            }
            proxy.setSetPropertyListener(itemProxy);
            
			if (reusing) {
			    ((KrollProxyReusableListener) modelListener).setReusing(false);
			}
		}
		
		for (KrollProxy theProxy : itemProxy.getNonBindedProxies()) {
		    KrollProxyListener modelListener = (KrollProxyListener) theProxy.getModelListener();
		    if (modelListener instanceof KrollProxyReusableListener) {
		        if (modelListener instanceof TiUIView) {
	                ((TiUIView)modelListener).setTouchDelegate((TiTouchDelegate) listItem);
	            }
		        theProxy.setEventOverrideDelegate(itemProxy);
            }
		}

		// process listItem properties
//		if (reusing) {
		    listItemProperties = itemProxy.getCollectionItem()
	                .generateDiffProperties(listItemProperties);
//		}
//		KrollDict listItemDiff = itemProxy.getListItem()
//				.generateDiffProperties(listItemProperties);
		if (!listItemProperties.isEmpty()) {
			listItem.processProperties(listItemProperties);
		}

	}

	public String getTemplateByIndex(int index) {
//        if (hasHeader()) {
//			index -= 1;
//		}

		if (isFilterOn()) {
			return getItemDataAt(filterIndices.get(index)).getTemplate();
		} else {
			return getItemDataAt(index).getTemplate();
		}
	}

	public int getContentCount() {
		int totalCount = 0;
		if (hidden) return totalCount;
		if (isFilterOn()) {
			totalCount = filterIndices.size();
		} else {
			totalCount = mItemCount;
		}
		return totalCount - getHiddenCount();
	}
	
	private void updateCurrentItemCount() {
	    int totalCount = 0;
        if (!hidden) {
            if (isFilterOn()) {
                totalCount = filterIndices.size();
            } else {
                totalCount = mItemCount;
            }
        }
        else if (!hideHeaderOrFooter() && hasHeader()) {
            totalCount += 1;
        }

        if (!hideHeaderOrFooter()) {
//          if (hasHeader()) {
//              totalCount += 1;
//          }
            if (footerTitle != null || footerViewArg != null) {
                totalCount += 1;
            }
        }
        totalCount -= getHiddenCount();
        mCurrentItemCount = totalCount;
	}
	/**
	 * @return number of entries within section
	 */
	public int getItemCount() {
		return mCurrentItemCount;
	}

	private int getHiddenCount() {
		int count = 0;
		if (hidden || hiddenItems == null) return count;
		for (int i = 0; i < hiddenItems.size(); i++)
			if (hiddenItems.get(i) == true)
				count++;
		return count;
	}

	private boolean hideHeaderOrFooter() {
		TiCollectionView listview = getCollectionView();
		return (listview.getSearchText() != null && filterIndices.isEmpty());
	}

	public boolean isHeaderView(int pos) {
		return (headerViewArg != null && pos == 0);
	}

	public boolean isFooterView(int pos) {
		return (footerViewArg != null && pos == getItemCount() - 1);
	}

	public boolean isHeaderTitle(int pos) {
		return (headerTitle != null && pos == 0);
	}

	public boolean isFooterTitle(int pos) {
		return (footerTitle != null && pos == getItemCount() - 1);
	}

	public void setCollectionView(TiCollectionView l) {
		listView = new WeakReference<TiCollectionView>(l);
	}

	public TiCollectionView getCollectionView() {
		if (listView != null) {
			return listView.get();
		}
		return null;
	}

	private CollectionItemData getItemDataAt(int position)
	{
		return listItemData.get(getRealPosition(position));
	}

//	public KrollDict getListItemData(int position) {
//		if (headerTitle != null || headerView != null) {
//			position -= 1;
//		}
//
//		if (isFilterOn()) {
//			return getItemDataAt(filterIndices.get(position))
//					.getProperties();
//		} else if (position >= 0 && position < getItemCount()) {
//			return getItemDataAt(position).getProperties();
//		}
//		return null;
//	}

	public CollectionItemData getCollectionItem(int position) {
//        if (hasHeader()) {
//			position -= 1;
//		}

		if (isFilterOn()) {
			return getItemDataAt(filterIndices.get(position));
		} else if (position >= 0 && position < getItemCount()) {
			return getItemDataAt(position);
		}
		return null;
	}

	public boolean isFilterOn() {
	    String searchText = getCollectionView().getSearchText();
	    return (searchText != null && searchText.length() > 0);
	}

	public void applyFilter(String searchText) {
		// Clear previous result
		filterIndices.clear();
		hidden = TiConvert.toBoolean(TiC.PROPERTY_VISIBLE, false);
		if (!isFilterOn()) return;
		boolean caseInsensitive = getCollectionView().getCaseInsensitive();
		// Add new results
		for (int i = 0; i < listItemData.size(); ++i) {
			CollectionItemData data = listItemData.get(i);
			String searchableText = data.getSearchableText();
			if (searchableText == null) continue;
			// Handle case sensitivity
			if (caseInsensitive) {
				searchText = searchText.toLowerCase();
				searchableText = searchableText.toLowerCase();
			}
			// String comparison
			if (data.isVisible() && searchableText != null && searchableText.contains(searchText)) {
				filterIndices.add(getInverseRealPosition(i));
			}
		}
        hidden = hidden || filterIndices.size() == 0;
	}

	public void release() {
		if (listItemData != null) {
			listItemData.clear();
			listItemData = null;
		}
		
		if (hiddenItems != null) {
			hiddenItems.clear();
			hiddenItems = null;
		}

		if (itemProperties != null) {
			itemProperties.clear();
			itemProperties = null;
		}
		mCurrentItemCount = 0;
		super.release();
	}

	public void releaseViews() {
		listView = null;
	}

	@Override
	public String getApiName() {
		return "Ti.UI.CollectionSection";
	}

    public void setIndex(int index) {
        this.sectionIndex = index;
        
    }
    
    public int getIndex() {
        return this.sectionIndex;
    }
    
    public View layoutHeaderOrFooterView (Object data, TiViewProxy parent, boolean isFooter) {
        TiViewProxy viewProxy = null;
        int id = TiCollectionView.HEADER_FOOTER_WRAP_ID;
        if (isFooter) {
            if (this.footerView == null || this.footerView.getParent() != parent) {
                if (this.footerView != null) {
                    this.footerView.releaseViews(false);
                    this.footerView.setParent(null);
                    this.footerView = null;
                }
                if (data instanceof TiViewProxy) {
                    this.footerView = (TiViewProxy)data;
                }
                else if(data instanceof HashMap) {
                    this.footerView = (TiViewProxy) parent.createProxyFromTemplate((HashMap) data, parent, true);
                }
            }
            viewProxy = this.footerView;
        }
        else {
            if (this.headerView == null || this.headerView.getParent() != parent) {
                if (this.headerView != null) {
                    this.headerView.releaseViews(false);
                    this.headerView.setParent(null);
                    this.headerView = null;
                }
                if (data instanceof TiViewProxy) {
                    this.headerView = (TiViewProxy)data;
                }
                else if(data instanceof HashMap) {
                    this.headerView = (TiViewProxy) parent.createProxyFromTemplate((HashMap) data, parent, true);
                }
            }
            viewProxy = this.headerView;
        }
        if (viewProxy == null) return null;
        
        viewProxy.setParent(parent);
        TiUIView tiView = viewProxy.getOrCreateView();
        if (tiView == null) return null;
        LayoutParams params = tiView.getLayoutParams();
      //If height is not dip, explicitly set it to SIZE
        if (!params.fixedSizeHeight()) {
            params.sizeOrFillHeightEnabled = true;
            params.autoFillsHeight = false;
        }
        if (params.optionWidth == null && !viewProxy.hasProperty(TiC.PROPERTY_WIDTH)) {
            params.sizeOrFillWidthEnabled = true;
            params.autoFillsWidth = true;
        }
        View outerView = tiView.getOuterView();
        ViewGroup parentView = (ViewGroup) outerView.getParent();
        if (parentView != null && parentView.getId() == id) {
            return parentView;
        } else {
            //add a wrapper so layout params such as height, width takes in effect.
            TiCompositeLayout wrapper = new TiCompositeLayout(viewProxy.getActivity(), LayoutArrangement.DEFAULT, null);
            AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,  AbsListView.LayoutParams.WRAP_CONTENT);
            wrapper.setLayoutParams(layoutParams);
            if (outerView != null) {
                wrapper.addView(outerView, tiView.getLayoutParams());
            }
            wrapper.setId(id);
            return wrapper;
        }
    }
}

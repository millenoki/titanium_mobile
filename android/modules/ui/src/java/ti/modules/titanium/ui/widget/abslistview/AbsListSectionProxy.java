/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.abslistview;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.AnimatableReusableProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.KrollProxyReusableListener;
import org.appcelerator.titanium.view.TiTouchDelegate;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.abslistview.TiAbsListView.TiBaseAdapter;
import android.view.View;

@Kroll.proxy(propertyAccessors = {
        TiC.PROPERTY_HEADER_TITLE,
        TiC.PROPERTY_FOOTER_TITLE,
        TiC.PROPERTY_HEADER_VIEW,
        TiC.PROPERTY_FOOTER_VIEW,
        TiC.PROPERTY_ITEMS
    })
public class AbsListSectionProxy extends AnimatableReusableProxy {

	private static final String TAG = "ListSectionProxy";
//	private ArrayList<AbsListItemData> listItemData;
	private int mItemCount;
    private int mCurrentItemCount = 0;
	private TiBaseAdapter adapter;
	private Object[] itemProperties;
	private ArrayList<Integer> filterIndices;
	private boolean preload;
	private ArrayList<Boolean> hiddenItems;
	boolean hidden = false;
	
	private int sectionIndex;

	private WeakReference<TiAbsListView> listView;


//	public class AbsListItemData {
//		private KrollDict properties;
//		private String searchableText;
//		private String template = null;
//		private boolean visible = true;
//
//		public AbsListItemData(KrollDict properties) {
//			setProperties(properties);
//		}
//		
//		private void updateSearchableAndVisible() {
//		    if (properties.containsKey(TiC.PROPERTY_PROPERTIES)) {
//                Object props = properties.get(TiC.PROPERTY_PROPERTIES);
//                if (props instanceof HashMap) {
//                    HashMap<String, Object> propsHash = (HashMap<String, Object>) props;
//                    
//                    if (propsHash.containsKey(TiC.PROPERTY_VISIBLE)) {
//                        visible = TiConvert.toBoolean(propsHash,
//                                TiC.PROPERTY_VISIBLE, true);
//                    }
//                }
//            }
//		    if (properties.containsKey(TiC.PROPERTY_SEARCHABLE_TEXT)) {
//                searchableText = TiConvert.toString(properties,
//                        TiC.PROPERTY_SEARCHABLE_TEXT);
//            }
//		}
//
//		public KrollDict getProperties() {
//			return properties;
//		}
//
//		public String getSearchableText() {
//			return searchableText;
//		}
//		
//
//		public boolean isVisible() {
//			return visible;
//		}
//
//
//		public String getTemplate() {
//			return template;
//		}
//
//        public void setProperties(KrollDict d) {
//            this.properties = d;
//            if (properties.containsKey(TiC.PROPERTY_TEMPLATE)) {
//                this.template = properties.getString(TiC.PROPERTY_TEMPLATE);
//            }
//            // set searchableText
//            updateSearchableAndVisible();
//        }
//        
//        public void setProperty(String binding, String key, Object value) {
//            if (properties.containsKey(binding)) {
//                ((HashMap)properties.get(binding)).put(key, value);
//            }
//        }
//	}
    
	public AbsListSectionProxy() {
//        listItemData = new ArrayList<AbsListItemData>();
        filterIndices = new ArrayList<Integer>();
        hiddenItems = new ArrayList<Boolean>();
        mItemCount = 0;
	}

	public void setAdapter(TiBaseAdapter a) {
		adapter = a;
	}
	
	public boolean hasHeader() {
        return !hideHeaderOrFooter() && getHoldedProxy(TiC.PROPERTY_HEADER_VIEW) != null;
	}
	public boolean hasFooter() {
        return !hideHeaderOrFooter() && getHoldedProxy(TiC.PROPERTY_FOOTER_VIEW) != null;
    }
	
	private static final ArrayList<String> KEY_SEQUENCE;
    static{
      ArrayList<String> tmp = new ArrayList<String>();
      tmp.add(TiC.PROPERTY_HEADER_TITLE);
      tmp.add(TiC.PROPERTY_FOOTER_TITLE);
      tmp.add(TiC.PROPERTY_HEADER_VIEW);
      tmp.add(TiC.PROPERTY_FOOTER_VIEW);
      KEY_SEQUENCE = tmp;
    }
    @Override
    protected ArrayList<String> keySequence() {
        return KEY_SEQUENCE;
    }
	
	@Override
	public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
	    switch (key) {
        case TiC.PROPERTY_HEADER_VIEW:
        case TiC.PROPERTY_FOOTER_VIEW:
            addProxyToHold(newValue, key);
            break;
        case TiC.PROPERTY_HEADER_TITLE:
            addProxyToHold(TiAbsListView.headerViewDict(TiConvert.toString(newValue)), TiC.PROPERTY_HEADER_VIEW);
            break;
       case TiC.PROPERTY_FOOTER_TITLE:
            addProxyToHold(TiAbsListView.footerViewDict(TiConvert.toString(newValue)), TiC.PROPERTY_FOOTER_VIEW);
            break;
        case TiC.PROPERTY_ITEMS:
            handleSetItems(newValue);
            break;
        case TiC.PROPERTY_VISIBLE:
            setVisible(TiConvert.toBoolean(newValue, true));
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
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


	
	@Kroll.method
    public KrollProxy getBinding(final int itemIndex, final String bindId) {
        if (listView != null) {
            return listView.get().getChildByBindId(this.sectionIndex, itemIndex, bindId);
        }
        return null;
    }

	@Kroll.method
	public KrollDict getItemAt(final int index) {
//		return getInUiThread(new Command<KrollDict>() {
//		    @Override
//            public KrollDict execute() {
		        return handleGetItemAt(index);
//            }
//        });
	}

	private KrollDict handleGetItemAt(int index) {
		if (itemProperties != null && index >= 0
				&& index < itemProperties.length) {
			return new KrollDict((HashMap) itemProperties[index]);
		}
		return null;
	}

	private int getRealPosition(int position) {
		int hElements = getHiddenCountUpTo(position);
		int diff = 0;
		for (int i = 0; i < hElements; i++) {
			diff++;
			if (hiddenItems.get(position + diff)) {
				i--;
			}
		}
		return (position + diff);
	}
	
	private int getInverseRealPosition(int position) {
//		int hElements = getHiddenCountUpTo(position);
		int diff = 0;
		for (int i = 0; i < position; i++) {
			if (hiddenItems.get(i)) {
	            diff++;
			}
		}
		return (position - diff);
	}


	private int getHiddenCountUpTo(int location) {
		int count = 0;
		for (int i = 0; i <= location; i++) {
			if (hiddenItems.get(i)) {
				count++;
			}
		}
		return count;
	}

	@Kroll.method
	@Kroll.setProperty
	public void setItems(final Object data) {
//		runInUiThread(new CommandNoReturn() {
//            @Override
//            public void execute() {
                handleSetItems(data);
//            }
//        }, true);
	}

	@Kroll.method
	@Kroll.getProperty
	public Object[] getItems() {
		if (itemProperties == null) 
			return new Object[0];
//		} else if (TiApplication.isUIThread()) {
			return itemProperties;
//		} else {
//			return (Object[]) TiMessenger
//					.sendBlockingMainMessage(getMainHandler().obtainMessage(
//							MSG_GET_ITEMS));
//		}
	}

	@Kroll.method
	public void appendItems(final Object data) {
//		runInUiThread(new CommandNoReturn() {
//            @Override
//            public void execute() {
                handleAppendItems(data);    
//            }
//        }, true);
	}

	public boolean isIndexValid(final int index) {
		return (index >= 0) ? true : false;
	}

	@Kroll.method
	public void insertItemsAt(final int index, final Object data) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                handleInsertItemsAt(index, data);    
            }
        }, true);
	}

	@Kroll.method
	public void deleteItemsAt(final int index, final int count) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                TiAbsListView listView = getListView();
                if (listView != null) {
                    int position = listView.findItemPosition(sectionIndex, index);
                    listView.remove(position, count);
                }
                else {
                    deleteItemsData(index, count);
                    notifyDataChange();
                }      
            }
        }, true);
	}

	@Kroll.method
	public void replaceItemsAt(final int index, final int count, final Object data) {
		if (!isIndexValid(index)) {
			return;
		}
		runInUiThread(new CommandNoReturn() {
            
            @Override
            public void execute() {
                if (count == 0) {
                    handleInsertItemsAt(index, data);
                } else if (deleteItemsData(index, count)) {
                    handleInsertItemsAt(index, data);
                }         
            }
        }, true);
	}
	
	@Kroll.method
    public void updateItems(final Object data,
            @Kroll.argument(optional = true) final Object options) {
        if (!(data instanceof Object[])) {
            return;
        }
        if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    updateItems(data, options);
                }
            }, true);
            return;
        }
        Object[] updates = (Object[]) data;
        if (itemProperties == null || updates.length != itemProperties.length) {
            return;
        }
        TiAbsListView listView = getListView();
        int i;
        HashMap item;
        HashMap update;
        View content;
        boolean visible;
        for (i = 0; i < itemProperties.length; i++) {
            item = (HashMap) itemProperties[i];
            update = (HashMap) updates[i];
            KrollDict.merge(item, update, false);
            content = listView.getCellAt(this.sectionIndex, i);
            visible = isItemVisible(item);
            hiddenItems.set(i, !visible);

            if (content != null && visible) {
                TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content
                        .findViewById(TiAbsListView.listContentId);
                if (listItem != null) {
                    if (listItem.getItemIndex() == i) {
                        TiAbsListViewTemplate template = getListView()
                                .getTemplate(TiConvert.toString(item,
                                        TiC.PROPERTY_TEMPLATE));
                        populateViews(item, listItem, template,
                                getUserItemInversedIndexFromSectionPosition(i),
                                this.sectionIndex, content, false);
                    }
                }
            }
        }
        notifyDataChange();
    }
	
	@Kroll.method
    public void updateItemAt(final int index, final Object data,
            @Kroll.argument(optional = true) final Object options) {
        if (!isIndexValid(index) || !(data instanceof HashMap)) {
            return;
        }
        if (!TiApplication.isUIThread()) {
            runInUiThread(new CommandNoReturn() {
                @Override
                public void execute() {
                    updateItemAt(index, data, options);
                }
            }, true);
            return;
        }
        if (itemProperties == null) {
            return;
        }
        // int nonRealItemIndex = itemIndex;
        if (index < 0 || index > itemProperties.length - 1) {
            return;
        }
        // if (hasHeader()) {
        // nonRealItemIndex += 1;
        // }

        TiAbsListView listView = getListView();

        HashMap currentItem = KrollDict.merge((HashMap) itemProperties[index],
                (HashMap) (data));
        if (currentItem == null)
            return;
        itemProperties[index] = currentItem;
        // only process items when listview's properties is processed.
        if (listView == null) {
            preload = true;
            return;
        }
        View content = listView.getCellAt(this.sectionIndex, index);
        // KrollDict d = new KrollDict(currentItem);
        // AbsListItemData itemD = listItemData.get(itemIndex);
        // itemD.setProperties(d);
        // listItemData.set(index, itemD);
        boolean visible = isItemVisible(currentItem);
        hiddenItems.set(index, !visible);

        if (content != null && visible) {
            TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content
                    .findViewById(TiAbsListView.listContentId);
            if (listItem != null) {
                if (listItem.getItemIndex() == index) {
                    TiAbsListViewTemplate template = getListView()
                            .getTemplate(TiConvert.toString(currentItem,
                                    TiC.PROPERTY_TEMPLATE));
                    populateViews(currentItem, listItem, template,
                            getUserItemInversedIndexFromSectionPosition(index),
                            this.sectionIndex, content, false);
                } else {
                    Log.d(TAG, "wrong item index", Log.DEBUG_MODE);
                }
                return;
            }
        }
        notifyDataChange();
    }
	
	
	
	public void updateItemAt(final int index, final String binding, final String key, final Object value) {
	    if (index < 0 || index >= mItemCount) {
	        return;
	    }
	    if (itemProperties != null) {
	        HashMap itemProp = (HashMap) itemProperties[index];
	        if (!itemProp.containsKey(binding)) {
	            itemProp.put(binding, new HashMap<String, Object>());
	        }
	        ((HashMap)itemProp.get(binding)).put(key, value);
        }
//	    AbsListItemData itemD = listItemData.get(index);
//	    itemD.setProperty(binding, key, value);
    }
	
	public void updateItemAt(final int index, final String binding, final HashMap props) {
        if (index < 0 || index >= mItemCount) {
            return;
        }
        if (itemProperties != null) {
            HashMap itemProp = (HashMap) itemProperties[index];
            if (!itemProp.containsKey(binding)) {
                itemProp.put(binding, new HashMap<String, Object>());
            }
            ((HashMap)itemProp.get(binding)).putAll(props);
        }
//      AbsListItemData itemD = listItemData.get(index);
//      itemD.setProperty(binding, key, value);
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

    @Kroll.method
    @Kroll.getProperty
    public int getLength() {
        return getItemCount();
    }

	
	public void processPreloadData() {
		if (itemProperties != null && preload) {
            mItemCount = itemProperties.length;
            processData(itemProperties, 0);
			preload = false;
		}
	}
	
	private boolean isItemVisible(final HashMap item) {
        boolean visible = false;
        if (item != null) {
            visible = true;
            Object props = item.get(TiC.PROPERTY_PROPERTIES);
            if (props instanceof HashMap) {
                HashMap<String, Object> propsHash = (HashMap<String, Object>) props;
                visible = TiConvert.toBoolean(propsHash,
                            TiC.PROPERTY_VISIBLE, visible);
            }
        }
        return visible;
	}

	private void processData(Object items, int offset) {
//		if (listItemData == null) {
//			return;
//		}
//        boolean visible = true;
        int i;
		if (items instanceof Object[]) {
		    Object[] array = (Object[])items;
		 // Second pass we would merge properties
	        for (i = 0; i < array.length; i++) {
	            hiddenItems.add(i + offset, !isItemVisible((HashMap) array[i]));
	        }
		} else if (items instanceof ArrayList) {
		    ArrayList<Object> array = (ArrayList<Object>)items;
		    for (i = 0; i < array.size(); i++) {
		        hiddenItems.add(i + offset, !isItemVisible((HashMap) array.get(i)));
	        }
		}
		
		updateCurrentItemCount();
		// Notify adapter that data has changed.
		if (preload == false) {
	      runInUiThread(new CommandNoReturn() {
              @Override
              public void execute() {
                adapter.notifyDataSetChanged();
              }
          }, false);
		}
	}

	private void handleSetItems(Object data) {

		if (data instanceof Object[]) {
//		    HashMap[] items = (HashMap[]) data;
			itemProperties = (Object[]) data;
//			listItemData.clear();
			hiddenItems.clear();
			filterIndices.clear();
			// only process items when listview's properties is processed.
			if (getListView() == null) {
				preload = true;
				return;
			}
			mItemCount = itemProperties.length;
			processData(itemProperties, 0);

		} else {
			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
		}
	}

	private void handleAppendItems(Object data) {
		if (data instanceof Object[]) {
		    Object[] items = (Object[]) data;
			if (itemProperties == null) {
				itemProperties = items;
                setProperty(TiC.PROPERTY_ITEMS, itemProperties);
			} else {
	            ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
			    list.addAll(Arrays.asList(items));
			    itemProperties = list.toArray();
			}
			// only process items when listview's properties is processed.
			if (getListView() == null) {
				preload = true;
				return;
			}
			int offset = mItemCount;
			mItemCount = itemProperties.length;

			processData(items, offset);

		} else {
			Log.e(TAG, "Invalid argument type to setData", Log.DEBUG_MODE);
		}
	}

	private void handleInsertItemsAt(int index, Object data) {
        TiAbsListView listView = getListView();
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
                Object[] items = (Object[]) data;

                if (itemProperties == null) {
                    itemProperties = items;
                    setProperty(TiC.PROPERTY_ITEMS, itemProperties);
               } else {
                    if (index < 0 || index > itemProperties.length) {
                        Log.e(TAG, "Invalid index to handleInsertItem",
                                Log.DEBUG_MODE);
                        return;
                    }
                    ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
                    list.addAll(index, Arrays.asList(items));
                    itemProperties = (Object[]) list.toArray();
                    processData(items, index);
                }
                // only process items when listview's properties is processed.
                preload = true;
                
            } else {
                Log.e(TAG, "Invalid argument type to insertItemsAt",
                        Log.DEBUG_MODE);
            }
        }
	}
	
	private boolean deleteItemsData(int index, int count) {
		boolean delete = false;
		
        ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
		while (count > 0) {
			if (index < itemProperties.length) {
			    list.remove(index);
				mItemCount--;
				delete = true;
			}
//			if (index < listItemData.size()) {
//				listItemData.remove(index);
//			}
			if (index < hiddenItems.size()) {
				hiddenItems.remove(index);
			}
			count--;
		}
        itemProperties = list.toArray();
		updateCurrentItemCount();
		return delete;
	}
	
	public Object deleteItemData(int index) {
        if (0 <= index && index < itemProperties.length) {
            ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
            Object result = list.remove(index);
            hiddenItems.remove(index);
//            listItemData.remove(index);
            mItemCount --;
            updateCurrentItemCount();
            itemProperties = list.toArray();
            return result;
        }
        return null;
    }
	
	public void insertItemData(int index, Object data) {
	    if (itemProperties == null) {
	        itemProperties = new Object[] {data};
	        setProperty(TiC.PROPERTY_ITEMS, itemProperties);
        } else {
            if (index < 0 || index > itemProperties.length) {
                Log.e(TAG, "Invalid index to handleInsertItem",
                        Log.DEBUG_MODE);
                return;
            }
            ArrayList<Object> list = new ArrayList(Arrays.asList(itemProperties));
            list.add(index, data);
            itemProperties = list.toArray();
        }
        // only process items when listview's properties is processed.
        if (getListView() == null) {
            preload = true;
            return;
        }

        mItemCount += 1;
//        if (listItemData != null && data instanceof HashMap) {
//            KrollDict d = new KrollDict((HashMap) data);
//            AbsListItemData itemD = new AbsListItemData(d);
//            listItemData.add(index, itemD);
//            hiddenItems.add(index, !itemD.isVisible());
//        }
        if (data instanceof HashMap) {
            hiddenItems.add(index, !isItemVisible((HashMap) data));
        }
        updateCurrentItemCount();
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
	public void generateCellContent(int sectionIndex, final HashMap item, 
			AbsListItemProxy itemProxy, TiBaseAbsListViewItem itemContent, TiAbsListViewTemplate template,
			int itemPosition, View item_layout) {
		// Create corresponding TiUIView for item proxy
		TiAbsListItem listItem = new TiAbsListItem(itemProxy, itemContent, item_layout);
        itemProxy.setActivity(this.getActivity());
		itemProxy.setView(listItem);
		itemContent.setView(listItem);
//        long startTime = System.currentTimeMillis();
		itemProxy.realizeViews();
//		long endTime = System.currentTimeMillis();
//        long diff = endTime - startTime;
//        if (diff > 20) {
//            Log.d(TAG, this.getClass().toString() + " generateCellContent " + diff + "ms");
//        }
		if (template != null) {
			populateViews(item, itemContent, template, itemPosition,
					sectionIndex, item_layout, false);
		}
		
	}
	
	public int getUserItemIndexFromSectionPosition(final int position) {
	    int result = position;
//	    if (hasHeader()) {
//	        result -= 1;
//        }
	    if (isFilterOn()) {
	        return getRealPosition(filterIndices.get(result));
	    }
	    return getRealPosition(result);
	}
	public int getUserItemInversedIndexFromSectionPosition(final int position) {
        int result = position;
//      if (hasHeader()) {
//          result -= 1;
//        }
        if (isFilterOn()) {
            return getInverseRealPosition(filterIndices.get(result));
        }
        return getInverseRealPosition(result);
    }

	public void populateViews(final HashMap item, TiBaseAbsListViewItem cellContent, TiAbsListViewTemplate template, int itemIndex, int sectionIndex,
			View item_layout, boolean reusing) {
		TiAbsListItem listItem = (TiAbsListItem)cellContent.getView();
		// Handling root item, since that is not in the views map.
		if (listItem == null) {
			return;
		}
		listItem.setReusing(reusing);
		int realItemIndex = getUserItemIndexFromSectionPosition(itemIndex);
		cellContent.setCurrentItem(sectionIndex, realItemIndex, this);
		
//		HashMap data = item;
		HashMap data = template.prepareDataDict(item);
		AbsListItemProxy itemProxy = (AbsListItemProxy) cellContent.getView().getProxy();
		itemProxy.setCurrentItem(sectionIndex, realItemIndex, this, item);

		HashMap listItemProperties;
//		String itemId = null;

		if (data.containsKey(TiC.PROPERTY_PROPERTIES)) {
			listItemProperties = (HashMap) data.get(TiC.PROPERTY_PROPERTIES);
		} else {
			listItemProperties = new HashMap();
		}
		ProxyAbsListItem rootItem = itemProxy.getListItem();
		
//		if (!reusing) {
	        HashMap<String, Object> listViewProperties = getListView().getToPassProps();
		    for (Map.Entry<String, Object> entry : listViewProperties.entrySet()) {
	            String inProp = entry.getKey();
	            if (!listItemProperties.containsKey(inProp) && !rootItem.containsKey(inProp)) {
	                listItemProperties.put(inProp, entry.getValue());
	            }
	        }
//		}
		

//		// find out if we need to update itemId
//		if (listItemProperties.containsKey(TiC.PROPERTY_ITEM_ID)) {
//			itemId = TiConvert.toString(listItemProperties
//					.get(TiC.PROPERTY_ITEM_ID));
//		}

		// update extra event data for list item
		itemProxy.setEventOverrideDelegate(itemProxy);

		HashMap<String, ProxyAbsListItem> views = itemProxy.getBindings();
		// Loop through all our views and apply default properties
		for (String binding : views.keySet()) {
			ProxyAbsListItem viewItem = views.get(binding);
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
            proxy.setSetPropertyListener(itemProxy);
			// if binding is contain in data given to us, process that data,
			// otherwise
			// apply default properties.
			if (reusing) {
			    ((KrollProxyReusableListener) modelListener).setReusing(true);
			}
			HashMap diffProperties = viewItem
                    .generateDiffProperties((HashMap) data.get(binding));
			
			if (diffProperties != null && !diffProperties.isEmpty()) {
			    if (reusing) {
	                modelListener.processApplyProperties(diffProperties);
			    } else {
	                modelListener.processProperties(diffProperties);
			    }
            }
            
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

	    listItemProperties = itemProxy.getListItem()
                .generateDiffProperties(listItemProperties);

		if (!listItemProperties.isEmpty()) {
		    if (reusing) {
		        listItem.processApplyProperties(listItemProperties);
            } else {
                listItem.processProperties(listItemProperties);
            }
		}
        listItem.setReusing(false);
	}

	public String getTemplateByIndex(int index) {
//        if (hasHeader()) {
//			index -= 1;
//		}

		if (isFilterOn()) {
			return TiConvert.toString(getItemDataAt(filterIndices.get(index)),TiC.PROPERTY_TEMPLATE);
		} else {
			return TiConvert.toString(getItemDataAt(index),TiC.PROPERTY_TEMPLATE);
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
//        else if (!hideHeaderOrFooter() && hasHeader()) {
//            totalCount += 1;
//        }
//
        if (!hideHeaderOrFooter()) {
          if (hasHeader() && totalCount == 0) {
              totalCount += 1;
          }
            //footer must be counted in!
            if (hasFooter()) {
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
			if (hiddenItems.get(i) == true) {
                count++;
			}
		return count;
	}

	private boolean hideHeaderOrFooter() {
		return (isFilterOn() && filterIndices.isEmpty());
	}

	public boolean isHeaderView(int pos) {
		return (hasHeader() && pos == 0);
	}

	public boolean isFooterView(int pos) {
		return (hasFooter() && pos == mCurrentItemCount - 1);
	}

	public void setListView(TiAbsListView l) {
		listView = new WeakReference<TiAbsListView>(l);
        updateCurrentItemCount(); //needs to be updated if no item but with a header or footer
	}

	public TiAbsListView getListView() {
		if (listView != null) {
			return listView.get();
		}
		return null;
	}

	public HashMap getItemDataAt(int position)
	{
	    if (itemProperties != null && itemProperties.length > 0) {
	        return (HashMap) itemProperties[getRealPosition(position)];
	    } else {
	        return null;
	    }
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

	public HashMap getListItem(int position) {
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
	    String searchText = getListView().getSearchText();
	    return (searchText != null && searchText.length() > 0);
	}

	public void applyFilter(String searchText) {
		// Clear previous result
		filterIndices.clear();
		hidden = TiConvert.toBoolean(TiC.PROPERTY_VISIBLE, false);
		if (isFilterOn() && itemProperties != null) {
		    
		    boolean caseInsensitive = getListView().getCaseInsensitive();
		    if (caseInsensitive) {
                searchText = searchText.toLowerCase();
            }
	        // Add new results
	        for (int i = 0; i < itemProperties.length; ++i) {
	            HashMap data = (HashMap) itemProperties[i];
	            boolean visible = isItemVisible(data);
	            String searchableText = TiConvert.toString(data, TiC.PROPERTY_SEARCHABLE_TEXT);
	            if (searchableText == null || !visible) continue;
	            // Handle case sensitivity
	            if (caseInsensitive) {
	                searchableText = searchableText.toLowerCase();
	            }
	            // String comparison
	            if (searchableText.contains(searchText)) {
	                filterIndices.add(getInverseRealPosition(i));
	            }
	        }
	        hidden = hidden || filterIndices.size() == 0;
		}
        updateCurrentItemCount();
	}

	public void release() {
//		if (listItemData != null) {
//			listItemData.clear();
////			listItemData = null;
//		}
		
		if (hiddenItems != null) {
			hiddenItems.clear();
//			hiddenItems = null;
		}
		
		if (filterIndices != null) {
		    filterIndices.clear();
//          hiddenItems = null;
        }

		if (itemProperties != null) {
//			itemProperties.clear();
			itemProperties = null;
		}
		mCurrentItemCount = 0;
		mItemCount = 0;
		super.release();
	}

    public void setIndex(int index) {
        this.sectionIndex = index;
        
    }
    
    public int getIndex() {
        return this.sectionIndex;
    }

}

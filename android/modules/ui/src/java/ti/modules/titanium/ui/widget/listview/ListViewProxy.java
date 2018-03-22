/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.widget.abslistview.AbsListViewProxy;
import android.app.Activity;
import android.os.Message;

@SuppressWarnings("unused")
@Kroll.proxy(creatableInModule = UIModule.class)
public class ListViewProxy extends AbsListViewProxy {

	private static final String TAG = "ListViewProxy";

    private static final int MSG_FIRST_ID = AbsListViewProxy.MSG_LAST_ID + 1;


    private static final int MSG_CLOSE_SWIPE_MENU = MSG_FIRST_ID + 1;
    protected static final int MSG_LAST_ID = MSG_CLOSE_SWIPE_MENU;

	public ListViewProxy() {
		super();
	}

    @Override
    public Class sectionClass() {
        return ListSectionProxy.class;
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
	public boolean handleMessage(final Message msg) 	{

		switch (msg.what) {
        case MSG_CLOSE_SWIPE_MENU: {
            handleCloseSwipeMenu(msg.obj);
				return true;
			}
			default:
				return super.handleMessage(msg);
		}
	}

    @Override
    public String getApiName() {
        return "Ti.UI.ListView";
		}

    public void handleCloseSwipeMenu(Object obj) {
        Boolean animated = true;
        if (obj != null) {
            animated = TiConvert.toBoolean(obj);
		}
		TiUIView listView = peekView();
		if (listView != null) {
            ((TiListView) listView).closeSwipeMenu(animated);
		}
	}

    @Kroll.method()
    public void closeSwipeMenu(final @Kroll.argument(optional = true) Object obj) {
        runInUiThread(new CommandNoReturn() {
            @Override
            public void execute() {
                handleCloseSwipeMenu(obj);                
		}
        }, false);
	}

			}
			preload = true;
			preloadSections.remove(index);
		}
	}

	@Kroll.method
	public void insertSectionAt(int index, Object section)
	{
		if (TiApplication.isUIThread()) {
			handleInsertSectionAt(index, section);
		} else {
			sendInsertSectionMessage(index, section);
		}
	}

	private void sendInsertSectionMessage(int index, Object section)
	{
		KrollDict data = new KrollDict();
		data.put("index", index);
		data.put("section", section);
		TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_INSERT_SECTION_AT), data);
	}

	private void handleInsertSectionAt(int index, Object section)
	{
		TiUIView listView = peekView();
		if (listView != null) {
			((TiListView) listView).insertSectionAt(index, section);
		} else {
			if (index < 0 || index > preloadSections.size()) {
				Log.e(TAG, "Invalid index to insertSection");
				return;
			}
			preload = true;
			addPreloadSections(section, index, false);
		}
	}

	@Kroll.method
	public void replaceSectionAt(int index, Object section)
	{
		if (TiApplication.isUIThread()) {
			handleReplaceSectionAt(index, section);
		} else {
			sendReplaceSectionMessage(index, section);
		}
	}

	private void sendReplaceSectionMessage(int index, Object section)
	{
		KrollDict data = new KrollDict();
		data.put("index", index);
		data.put("section", section);
		TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_REPLACE_SECTION_AT), data);
	}

	private void handleReplaceSectionAt(int index, Object section)
	{
		TiUIView listView = peekView();
		if (listView != null) {
			((TiListView) listView).replaceSectionAt(index, section);
		} else {
			handleDeleteSectionAt(index);
			handleInsertSectionAt(index, section);
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.getProperty
	public ListSectionProxy[] getSections()
	// clang-format on
	{
		if (TiApplication.isUIThread()) {
			return handleSections();
		} else {
			return (ListSectionProxy[]) TiMessenger.sendBlockingMainMessage(
				getMainHandler().obtainMessage(MSG_GET_SECTIONS));
		}
	}

	// clang-format off
	@Kroll.method
	@Kroll.setProperty
	public void setSections(Object sections)
	// clang-format on
	{
		if (!(sections instanceof Object[])) {
			Log.e(TAG, "Invalid argument type to setSection(), needs to be an array", Log.DEBUG_MODE);
			return;
		}
		//Update java and javascript property
		setProperty(TiC.PROPERTY_SECTIONS, sections);

		Object[] sectionsArray = (Object[]) sections;
		TiUIView listView = peekView();
		//Preload sections if listView is not opened.
		if (listView == null) {
			preload = true;
			clearPreloadSections();
			addPreloadSections(sectionsArray, -1, true);
		} else {
			if (TiApplication.isUIThread()) {
				((TiListView) listView).processSectionsAndNotify(sectionsArray);
			} else {
				TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_SET_SECTIONS), sectionsArray);
			}
		}
	}

	private ListSectionProxy[] handleSections()
	{
		if (peekView() == null && getParent() != null) {
			getParent().getOrCreateView();
		}
		TiUIView listView = peekView();

		if (listView != null) {
			return ((TiListView) listView).getSections();
		}
		ArrayList<ListSectionProxy> preloadedSections = getPreloadSections();
		return preloadedSections.toArray(new ListSectionProxy[preloadedSections.size()]);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.ListView";
	}
}

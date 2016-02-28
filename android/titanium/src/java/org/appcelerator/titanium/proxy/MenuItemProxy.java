/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.kroll.common.TiMessenger.Command;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiCompositeLayout;

import android.app.Activity;
import android.support.v4.view.MenuItemCompat;
import android.view.MenuItem;

@Kroll.proxy
public class MenuItemProxy extends AnimatableReusableProxy
{
	private static final String TAG = "MenuItem";

	private MenuItem item;


	private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 1000;
	
	private final class CompatActionExpandListener implements MenuItemCompat.OnActionExpandListener {
        public boolean onMenuItemActionCollapse(MenuItem item) {
            fireEvent(TiC.EVENT_COLLAPSE, null);
            return true;
        }

        public boolean onMenuItemActionExpand(MenuItem item) {
            fireEvent(TiC.EVENT_EXPAND, null);
            return true;
        }
    }

    protected MenuItemProxy(MenuItem item, Activity activity)
	{
        super();
        mProcessInUIThread = true;
        setActivity(activity);
		this.item = item;
		MenuItemCompat.setOnActionExpandListener(item, new CompatActionExpandListener());
	}

    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public int getGroupId() {
        return (Integer) item.getGroupId();
	}
	
	@Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public int getItemId() {
	    return (Integer) item.getItemId();
	}
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public int getOrder() {
        return (Integer) item.getOrder();
	}
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public String getTitle() {
        return (String) item.getTitle();
	}

	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public String getTitleCondensed() {
        return (String) item.getTitleCondensed();
	}
	
    @Kroll.method(runOnUiThread=true)
	public boolean hasSubMenu() {
        return (Boolean) item.hasSubMenu();
    }
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public boolean isChecked() {
        return (Boolean) item.isChecked();
	}
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public boolean isCheckable() {
        return (Boolean) item.isChecked();
	}
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public boolean isEnabled() {
        return (Boolean) item.isEnabled();
	}
	
    @Kroll.method(runOnUiThread=true) @Kroll.getProperty(enumerable=false, runOnUiThread=true)
	public boolean isVisible() {
        return (Boolean) item.isVisible();
	}

	public void setActionView(Object value)
	{
//        KrollProxy viewProxy = addProxyToHold(view, "rightButton");

		if (value instanceof TiViewProxy) {
		    
			final TiCompositeLayout layout = new TiCompositeLayout(getActivity());
            TiUIHelper.addView(layout, (TiViewProxy) value);            
			TiMessenger.postOnMain(new Runnable() {
				public void run() {
					if (TiC.ICS_OR_GREATER) {
	                    item.setActionView(layout);
	                } else {
	                    MenuItemCompat.setActionView(item, layout);
	                }
				}
			});
		} else {
			Log.w(TAG, "Invalid type for actionView", Log.DEBUG_MODE);
		}
	}



	@Kroll.method
	public void collapseActionView() {
	    runInUiThread(new CommandNoReturn() {
            public void execute() {
                if (TiC.ICS_OR_GREATER) {
                    item.collapseActionView();
                } else {
                    MenuItemCompat.collapseActionView(item);
                }
            }
        }, false);
	}

	@Kroll.method
	public void expandActionView() {
	    runInUiThread(new CommandNoReturn() {
            public void execute() {
                if (TiC.ICS_OR_GREATER) {
                    item.expandActionView();
                } else {
                    MenuItemCompat.expandActionView(item);
                }
            }
        }, false);
	}

	@Kroll.method @Kroll.getProperty(enumerable=false)
	public boolean isActionViewExpanded() {
	    return getValueInUIThread(new Command<Boolean>() {
            public Boolean execute() {
                if (TiC.ICS_OR_GREATER) {
                    return item.isActionViewExpanded();
                } else {
                    return MenuItemCompat.isActionViewExpanded(item);
                }
            };
        }, false);
	}

	@Override
	public String getApiName()
	{
		return "Ti.Android.MenuItem";
	}

	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_ACTION_VIEW:
            setActionView(newValue);
            break;
        case TiC.PROPERTY_CHECKABLE: {
            final Boolean toApply = TiConvert.toBoolean(newValue);
            item.setCheckable(toApply);
            break;
        }
        case TiC.PROPERTY_CHECKED: {
            final Boolean toApply = TiConvert.toBoolean(newValue);
            item.setChecked(toApply);
            break;
        }
        case TiC.PROPERTY_ENABLED: {
            final Boolean toApply = TiConvert.toBoolean(newValue);
            item.setEnabled(toApply);
            break;
        }
        case TiC.PROPERTY_ICON: {
            final Object toApply = newValue;
            item.setIcon(TiUIHelper.getResourceDrawable(toApply));
            break;
        }
        case TiC.PROPERTY_SHOW_AS_ACTION: {
            final int toApply = TiConvert.toInt(newValue);

            if (TiC.ICS_OR_GREATER) {
                item.setShowAsAction(toApply);
            } else {
                MenuItemCompat.setShowAsAction(item, toApply);
            }
            break;
        }
        case TiC.PROPERTY_TITLE_CONDENSED: {
            final String toApply = TiConvert.toString(newValue);
            item.setTitleCondensed(toApply);
            break;
        }
        case TiC.PROPERTY_TITLE: {
            final String toApply = TiConvert.toString(newValue);
            item.setTitle(toApply);
            break;
        }
        case TiC.PROPERTY_VISIBLE: {
            final Boolean toApply = TiConvert.toBoolean(newValue);
            item.setVisible(toApply);
            break;
        }
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }
}

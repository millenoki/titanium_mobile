/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.clipboard;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;

import ti.modules.titanium.ui.UIModule;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.Context;

@SuppressWarnings("deprecation")
@Kroll.module(parentModule=UIModule.class)
public class ClipboardModule extends KrollModule implements OnPrimaryClipChangedListener
{
	private String TAG = "Clipboard";

	public ClipboardModule()
	{
		super();
	}
	
	@Override
    protected void eventListenerAdded(String event, int count, KrollProxy proxy)
    {
        if (TiC.EVENT_CHANGE.equals(event)) {
            if (count == 1) {
                board().addPrimaryClipChangedListener(this);
            }
        }

        super.eventListenerAdded(event, count, proxy);
    }

    /**
     * @see org.appcelerator.kroll.KrollProxy#eventListenerRemoved(java.lang.String, int, org.appcelerator.kroll.KrollProxy)
     */
    @Override
    protected void eventListenerRemoved(String event, int count, KrollProxy proxy)
    {
        if (TiC.EVENT_CHANGE.equals(event)) {
            if (count == 0) {
                board().removePrimaryClipChangedListener(this);
            }
        }

        super.eventListenerRemoved(event, count, proxy);
    }

	/**
	 * Get the native clipboard instance.
	 */
	private ClipboardManager board()
	{
		return (ClipboardManager) TiApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
	}

	/**
	 * Android's clipboard currently only handles text; when working with
	 * arbitrary data check if it looks like text that we're being handed.
	 */
	private boolean isTextType(String type)
	{
		String mimeType = type.toLowerCase();
		return mimeType.equals("text/plain") || mimeType.startsWith("text");
	}

	@Kroll.method
	public void clearData(@Kroll.argument(optional=true) String type)
	{
		clearText();
	}

	@Kroll.method
	public void clearText()
	{
		board().setText(""); // can we use null?
	}

	@Kroll.method
	public Object getData(String type)
	{
		if (isTextType(type))
		{
			return getText();
		}
		else
		{
			// Android clipboard is text-only... :(
			return null;
		}
	}

	@Kroll.method @Kroll.getProperty
	public String getText()
	{
		return board().getText().toString();
	}

	@Kroll.method
	public boolean hasData(String type)
	{
		if (type == null || isTextType(type))
		{
			return hasText();
		}
		else
		{
			return false;
		}
	}

	@Kroll.method
	public boolean hasText()
	{
		return board().hasText();
	}

	@Kroll.method
	public void setData(String type, Object data)
	{
		if (isTextType(type) && data != null) {
			board().setText(data.toString());
		} else {
			Log.w(TAG, "Android clipboard only supports text data");
		}
	}

	@Kroll.method @Kroll.setProperty
	public void setText(String text)
	{
		board().setText(text);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.Clipboard";
	}

    @Override
    public void onPrimaryClipChanged() {
        if (hasListeners(TiC.EVENT_CHANGE)) {
            ClipboardManager cb = board();
            if (cb.hasPrimaryClip()) {
                KrollDict data = new KrollDict();
                ClipData cd = cb.getPrimaryClip();
                if (cd.getDescription()
                        .hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    data.put(TiC.PROPERTY_TEXT, cd.getItemAt(0).getText());
                } else if (cd.getDescription()
                        .hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                    data.put(TiC.PROPERTY_TEXT, cd.getItemAt(0).getText());
                } else if (cd.getDescription()
                        .hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                    data.put(TiC.PROPERTY_TEXT, cd.getItemAt(0).getUri().toString());
                }
                fireEvent(TiC.EVENT_CHANGE, data, false, false);
            }
        }

    }
}

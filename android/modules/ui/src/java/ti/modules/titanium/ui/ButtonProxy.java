/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIButton;
import android.app.Activity;

@Kroll.proxy(creatableInModule=UIModule.class, propertyAccessors = {
	TiC.PROPERTY_TITLE,
	TiC.PROPERTY_TITLEID,
	TiC.PROPERTY_COLOR,
	TiC.PROPERTY_SELECTED_COLOR,
	TiC.PROPERTY_ENABLED,
	TiC.PROPERTY_FONT,
	TiC.PROPERTY_IMAGE,
	TiC.PROPERTY_TEXT_ALIGN,
	TiC.PROPERTY_VERTICAL_ALIGN,
	TiC.PROPERTY_TITLE_PADDING,
	TiC.PROPERTY_WORD_WRAP,
	TiC.PROPERTY_SHADOW_COLOR,
	TiC.PROPERTY_SHADOW_OFFSET,
	TiC.PROPERTY_SELECTED,
	TiC.PROPERTY_SHADOW_RADIUS
})
public class ButtonProxy extends ViewProxy
{
	public ButtonProxy()
	{
		defaultValues.put(TiC.PROPERTY_TITLE, "");
		defaultValues.put(TiC.PROPERTY_SELECTED, false);
		defaultValues.put(TiC.PROPERTY_SELECTOR, true);
	}

	public ButtonProxy(TiContext tiContext)
	{
		this();
	}

	@Override
	protected KrollDict getLangConversionTable()
	{
		KrollDict table = new KrollDict();
		table.put(TiC.PROPERTY_TITLE, TiC.PROPERTY_TITLEID);
		return table;
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUIButton(this);
	}

	@Override
	public String getApiName()
	{
		return "Ti.UI.Button";
	}
}

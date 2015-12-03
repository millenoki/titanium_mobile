/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.abslistview;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiC;
import org.json.JSONException;

public class TiDefaultAbsListViewTemplate extends TiAbsListViewTemplate {
	private static final KrollDict TEMPLATE_DICT = createTemplateDict();
    private static KrollDict createTemplateDict() {
        try {
            return new KrollDict("{properties:{layout:'horizontal', selector:true},childTemplates:[{type:'Ti.UI.ImageView',bindId:'imageView',touchEnabled:false,left:15,width:35',height:35},{properties:{layout:'vertical',touchEnabled:false,left:15,right:15},childTemplates:[{type:'Ti.UI.Label',bindId:'titleView',color:'white',font:{size:16},ellipsize:true,maxLines:1,height:'FILL',width:'FILL'},{type:'Ti.UI.Label',bindId:'subtitleView',color:'#7B7B7B',font:{size:15},ellipsize:true,maxLines:1,height:'FILL',width:'FILL', verticalAlign:'top'}]}]}");
        } catch (JSONException e) {
            return null;
        }
    }

	public TiDefaultAbsListViewTemplate(String id) {
		super(id, TEMPLATE_DICT);
	}
	
	@Override
    public HashMap prepareDataDict(HashMap dict)
	{
	    HashMap result = super.prepareDataDict(dict);
	    if (dict == result) {
	        dict = (HashMap) result.clone(); //we need to copy it as we modify it
	    }
	    HashMap properties = (HashMap) (result.containsKey(TiC.PROPERTY_PROPERTIES)?result.get(TiC.PROPERTY_PROPERTIES):dict);
		
		boolean hasSubtitle = properties.containsKey(TiC.PROPERTY_SUBTITLE);
		
		if (properties.containsKey(TiC.PROPERTY_TITLE) || properties.containsKey(TiC.PROPERTY_FONT) || properties.containsKey(TiC.PROPERTY_COLOR))
		{
		    HashMap labelDict = (HashMap) result.get("titleView");
			if (labelDict == null)
			{
				labelDict = new HashMap();
				result.put("titleView", labelDict);
			}
			labelDict.put(TiC.PROPERTY_VERTICAL_ALIGN, hasSubtitle?"bottom":"center");
			if (properties.containsKey(TiC.PROPERTY_TITLE)) {
				labelDict.put(TiC.PROPERTY_TEXT, properties.get(TiC.PROPERTY_TITLE));
			}
			if (properties.containsKey(TiC.PROPERTY_FONT)) {
				labelDict.put(TiC.PROPERTY_FONT, properties.get(TiC.PROPERTY_FONT));
			}
			if (properties.containsKey(TiC.PROPERTY_COLOR)) {
				labelDict.put(TiC.PROPERTY_COLOR, properties.get(TiC.PROPERTY_COLOR));
			}
		}
		HashMap subDict = (HashMap) result.get("subtitleView");
        if (subDict == null)
        {
            subDict = new HashMap();
            result.put("subtitleView", subDict);
        }
		if (hasSubtitle)
        {
		    subDict.put(TiC.PROPERTY_TEXT, properties.get(TiC.PROPERTY_SUBTITLE));
            subDict.put(TiC.PROPERTY_VISIBLE, true);
            
        } else {
            subDict.put(TiC.PROPERTY_VISIBLE, false);
        }
		
		subDict = (HashMap) result.get("imageView");
        if (subDict == null)
        {
            subDict = new HashMap();
            result.put("imageView", subDict);
        }
        if (properties.containsKey(TiC.PROPERTY_IMAGE))
        {
            subDict.put(TiC.PROPERTY_IMAGE, properties.get(TiC.PROPERTY_IMAGE));
            subDict.put(TiC.PROPERTY_VISIBLE, true);
            
        } else {
            subDict.put(TiC.PROPERTY_VISIBLE, false);
        }
		
		return result;
	}
}

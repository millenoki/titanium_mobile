/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.util.HashMap;

import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.CardView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class TiUICardView extends TiUIView
{
    public int paddingLeft, paddingTop, paddingRight, paddingBottom;

    private static final String TAG = "TiUICardView";

    public class TiUICardViewLayout extends TiCompositeLayout {

        public TiUICardViewLayout(Context context)
        {
            super(context, TiUICardView.this);
        } 

    }

    public class TiCardView extends CardView {

        private TiUICardViewLayout layout;

        public TiCardView(Context context)
        {
            super(context);

            layout = new TiUICardViewLayout(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT);
            layout.setLayoutParams(params); 
            super.addView(layout, params);
        }

        public TiUICardViewLayout getLayout()
        {
            return layout;
        }

        @Override
        public void addView(View child, android.view.ViewGroup.LayoutParams params)
        {
            layout.addView(child, params);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (changed) {
                TiUIHelper.firePostLayoutEvent(TiUICardView.this);
            }
        }
        @Override
        public void setBackground(Drawable background) {
            if (!(background instanceof TiBackgroundDrawable)) {
                super.setBackground(background);
            }
        }

    }

    public TiUICardView(final TiViewProxy proxy)
    {
        // we create the view after the properties are processed
        super(proxy);
        View view = new TiCardView(getProxy().getActivity());
        view.setPadding(0, 0, 0, 0);
        view.setFocusable(false);
        setNativeView(view);
    }

    public TiUICardViewLayout getLayout()
    {
        View nativeView = getNativeView();
        return ((TiCardView) nativeView).layout;

    }
    
    private TiCardView getCardView() {
        return ((TiCardView) getNativeView());
    }
    
    @Override
    public ViewGroup getParentViewForChild()
    {
        return getLayout();
    }
    
    @Override
    protected void add(TiUIView child, int childIndex) {
        super.add(child, childIndex);

        if (getNativeView() != null) {
            getLayout().requestLayout();
            if (child.getNativeView() != null) {
                child.getNativeView().requestLayout();
            }
        }
    }


    @Override
    public void resort()
    {
        View v = getLayout();
        if ( v instanceof TiCompositeLayout) {
            ((TiCompositeLayout) v).resort();
        }
    }
    @Override
    protected void setBorderRadius(float[] radius) {
        super.setBorderRadius(radius);
        getCardView().setRadius(radius[0]);
    }
    
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_PADDING:
            RectF padding = TiConvert.toPaddingRect(newValue, null);
            getCardView().setContentPadding((int) padding.left,
                    (int) padding.top, (int) padding.right,
                    (int) padding.bottom);
            break;
        case TiC.PROPERTY_PREVENT_CORNER_OVERLAP:
            getCardView().setPreventCornerOverlap(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_MAX_ELEVATION:
            getCardView().setMaxCardElevation(TiConvert.toFloat(newValue));
            break;
        case TiC.PROPERTY_ELEVATION:
            getCardView().setCardElevation(TiConvert.toFloat(newValue));
            break;
        case TiC.PROPERTY_USE_COMPAT_PADDING:
            getCardView().setUseCompatPadding(TiConvert.toBoolean(newValue, false));
            break;
        case TiC.PROPERTY_BACKGROUND_COLOR:
            getCardView().setCardBackgroundColor(TiConvert.toColor(newValue));
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

}

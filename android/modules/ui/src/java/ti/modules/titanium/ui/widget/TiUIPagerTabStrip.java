package ti.modules.titanium.ui.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiHtml;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiUIHelper.Shadow;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;

import com.nineoldandroids.view.ViewHelper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public class TiUIPagerTabStrip extends PagerTabStrip {
    private ArrayList<CharSequence> mStripTitles = new ArrayList<CharSequence>();
    private static final float DEFAULT_SHADOW_RADIUS = 0.5f;
    private float shadowRadius = DEFAULT_SHADOW_RADIUS;
    private float shadowX = 0f;
    private float shadowY = -1f; // to have the same value as ios
    private int shadowColor = Color.TRANSPARENT;
    private TiBackgroundDrawable background;
    private KrollProxy parentProxy;
    private static final String TAG = "TiUIPagerTabStrip";
    private boolean backgroundRepeat = false;
    
    public TiUIPagerTabStrip(final Context context, final KrollProxy parentProxy)
    {
        super(context);
        this.parentProxy = parentProxy;
        ViewPager.LayoutParams layoutParams = new ViewPager.LayoutParams();
        layoutParams.height = ViewPager.LayoutParams.WRAP_CONTENT;
        layoutParams.width = ViewPager.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP;
        setLayoutParams(layoutParams);
    }
    
    public CharSequence getPageTitle(int position) {
        if (position < mStripTitles.size()) {
            return mStripTitles.get(position);
        }
        return null;
    }
    
 // Layout
    public void fillLayout(KrollDict hashMap)
    {
        boolean dirty = false;
        ViewPager.LayoutParams layoutParams = (ViewPager.LayoutParams) getLayoutParams();
        if (hashMap.containsKey(TiC.PROPERTY_WIDTH)) {
            Object width = hashMap.get(TiC.PROPERTY_WIDTH);
            if (width == null || width.equals(TiC.LAYOUT_FILL)) {
                layoutParams.width = ViewPager.LayoutParams.MATCH_PARENT;
            } else if (width.equals(TiC.SIZE_AUTO) || width.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.width = ViewPager.LayoutParams.WRAP_CONTENT;
            } else if (width.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.width = TiConvert.toTiDimension(width, TiDimension.TYPE_WIDTH).getAsPixels((View) getParent());
            }
            dirty = true;
        }
        if (hashMap.containsKey(TiC.PROPERTY_HEIGHT)) {
            Object height = hashMap.get(TiC.PROPERTY_HEIGHT);
            if (height == null || height.equals(TiC.LAYOUT_FILL)) {
                layoutParams.height = ViewPager.LayoutParams.MATCH_PARENT;
            } else if (height.equals(TiC.SIZE_AUTO) || height.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.height = ViewPager.LayoutParams.WRAP_CONTENT;
            } else if (height.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.height = TiConvert.toTiDimension(height, TiDimension.TYPE_HEIGHT).getAsPixels((View) getParent());
            }
            dirty = true;
        }
        if (dirty) {
            setLayoutParams(layoutParams);
        }
    }
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        ViewPager.LayoutParams layoutParams = (ViewPager.LayoutParams) getLayoutParams();
        if (key.equals(TiC.PROPERTY_WIDTH)) {
            if (newValue == null || newValue.equals(TiC.LAYOUT_FILL)) {
                layoutParams.width = ViewPager.LayoutParams.MATCH_PARENT;
            } else if (newValue.equals(TiC.SIZE_AUTO) || newValue.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.width = ViewPager.LayoutParams.WRAP_CONTENT;
            } else {
                layoutParams.width = TiConvert.toTiDimension(newValue, TiDimension.TYPE_WIDTH).getAsPixels((View) getParent());
            }
            setLayoutParams(layoutParams);
        } else if (key.equals(TiC.PROPERTY_HEIGHT)) {
            if (newValue == null || newValue.equals(TiC.LAYOUT_FILL)) {
                layoutParams.height = ViewPager.LayoutParams.MATCH_PARENT;
            } else if (newValue.equals(TiC.SIZE_AUTO) || newValue.equals(TiC.LAYOUT_SIZE)) {
                layoutParams.height = ViewPager.LayoutParams.WRAP_CONTENT;
            } else {
                layoutParams.height = TiConvert.toTiDimension(newValue, TiDimension.TYPE_HEIGHT).getAsPixels((View) getParent());
            }
            setLayoutParams(layoutParams);
        } else if (key.equals(TiC.PROPERTY_VERTICAL_ALIGN)) {
            layoutParams.gravity = TiUIHelper.getGravity(TiConvert.toString(newValue), true);
            setLayoutParams(layoutParams);
        } else if (key.equals(TiC.PROPERTY_TITLES)) {
            Object[] values = (Object[]) newValue;
            mStripTitles.clear();
            for (int i = 0; i < values.length; i++) {
                mStripTitles.add(TiHtml.fromHtml(TiConvert.toString(values[i]), false));
            }
        } else if (key.equals(TiC.PROPERTY_COLOR)) {
            setTabIndicatorColor(TiConvert.toColor(newValue));
        } else if (key.equals(TiC.PROPERTY_INDICATOR_COLOR)) {
            setTextColor(TiConvert.toColor(newValue));
        }  else if (key.equals(TiC.PROPERTY_TEXT_ALIGN)) {
            for (int counter = 0 ; counter < getChildCount(); counter++) {
                if (getChildAt(counter) instanceof TextView) {
                    TiUIHelper.setAlignment((TextView)getChildAt(counter), TiConvert.toString(newValue), null);
                }
            }
        } else if (key.equals(TiC.PROPERTY_INCLUDE_FONT_PADDING)) {
            for (int counter = 0 ; counter < getChildCount(); counter++) {
                if (getChildAt(counter) instanceof TextView) {
                    ((TextView)getChildAt(counter)).setIncludeFontPadding(TiConvert.toBoolean(newValue, true));
                }
            }
        } else if (key.equals(TiC.PROPERTY_FONT)) {
            for (int counter = 0 ; counter < getChildCount(); counter++) {
                if (getChildAt(counter) instanceof TextView) {
                    TiUIHelper.styleText((TextView)getChildAt(counter), (HashMap) newValue);
                }
            }
        } else if (key.equals(TiC.PROPERTY_SHADOW_OFFSET)) {
            if (newValue instanceof HashMap) {
                HashMap dict = (HashMap) newValue;
                shadowX = TiUIHelper.getInPixels(dict, TiC.PROPERTY_X);
                shadowY = TiUIHelper.getInPixels(dict, TiC.PROPERTY_Y);
                for (int counter = 0 ; counter < getChildCount(); counter++) {
                    if (getChildAt(counter) instanceof TextView) {
                        ((TextView)getChildAt(counter)).setShadowLayer(shadowRadius, shadowX, shadowY, shadowColor);
                    }
                }
            }
        } else if (key.equals(TiC.PROPERTY_SHADOW_RADIUS)) {
            shadowRadius = TiConvert.toFloat(newValue, DEFAULT_SHADOW_RADIUS);
            for (int counter = 0 ; counter < getChildCount(); counter++) {
                if (getChildAt(counter) instanceof TextView) {
                    ((TextView)getChildAt(counter)).setShadowLayer(shadowRadius, shadowX, shadowY, shadowColor);
                }
            }
        } else if (key.equals(TiC.PROPERTY_SHADOW_COLOR)) {
            shadowColor = TiConvert.toColor(TiConvert.toString(newValue));
            for (int counter = 0 ; counter < getChildCount(); counter++) {
                if (getChildAt(counter) instanceof TextView) {
                    ((TextView)getChildAt(counter)).setShadowLayer(shadowRadius, shadowX, shadowY, shadowColor);
                }
            }
        } else if (key.equals(TiC.PROPERTY_TEXT_SPACING)) {
            setTextSpacing(TiConvert.toTiDimension(newValue, TiDimension.TYPE_WIDTH).getAsPixels((View) getParent()));
        } else if (key.equals(TiC.PROPERTY_NON_PRIMARY_ALPHA)) {
            setNonPrimaryAlpha(TiConvert.toFloat(newValue, 0.5f));
        } else if (key.equals(TiC.PROPERTY_DRAW_FULL_UNDERLINE)) {
            setDrawFullUnderline(TiConvert.toBoolean(newValue, true));
        } else if (key.equals(TiC.PROPERTY_PADDING)) {
            RectF textPadding = TiConvert.toPaddingRect(newValue);
            setPadding((int) textPadding.left, (int) textPadding.top, (int) textPadding.right, (int) textPadding.bottom);
        } else if (key.startsWith(TiC.PROPERTY_BACKGROUND_PREFIX)) {
            TiBackgroundDrawable bgdDrawable = getOrCreateBackground();
            if (key.equals(TiC.PROPERTY_BACKGROUND_COLOR)) {
                int color = TiConvert.toColor(newValue);
                bgdDrawable.setDefaultColor(color);
                bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_1,
                        color);
                bgdDrawable.setColorForState(TiUIHelper.BACKGROUND_DEFAULT_STATE_2,
                        color);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR)) {
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE,
                        TiConvert.toColor(newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_FOCUSED_COLOR)) {
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE,
                        TiConvert.toColor(newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_DISABLED_COLOR)) {
                bgdDrawable.setColorForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE,
                        TiConvert.toColor(newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_SELECTED_GRADIENT)) {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE, drawable);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_FOCUSED_GRADIENT)) {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE, drawable);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_DISABLED_GRADIENT)) {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DISABLED_STATE, drawable);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_GRADIENT)) {
                Drawable drawable = TiUIHelper.buildGradientDrawable(TiConvert
                        .toKrollDict(newValue));
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1, drawable);
                bgdDrawable.setGradientDrawableForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2, drawable);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_IMAGE)) {
                setBackgroundImageDrawable(newValue, backgroundRepeat, new int[][] {
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1,
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2 });
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE)) {
                setBackgroundImageDrawable(newValue, backgroundRepeat,
                        new int[][] { TiUIHelper.BACKGROUND_SELECTED_STATE });
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_FOCUSED_IMAGE)) {
                setBackgroundImageDrawable(newValue, backgroundRepeat,
                        new int[][] { TiUIHelper.BACKGROUND_FOCUSED_STATE });
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_DISABLED_IMAGE)) {
                setBackgroundImageDrawable(newValue, backgroundRepeat,
                        new int[][] { TiUIHelper.BACKGROUND_DISABLED_STATE });
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_REPEAT)) {
                if (background != null)
                    background.setImageRepeat(TiConvert.toBoolean(newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_SELECTED_INNERSHADOWS)) {
                Shadow[] shadows = TiConvert.toShadowArray((Object[]) newValue);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_SELECTED_STATE, shadows);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_FOCUSED_STATE, shadows);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_FOCUSED_INNERSHADOWS)) {
                bgdDrawable.setInnerShadowsForState(
                                TiUIHelper.BACKGROUND_FOCUSED_STATE,
                                TiConvert.toShadowArray((Object[]) newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_DISABLED_INNERSHADOWS)) {
                bgdDrawable.setInnerShadowsForState(
                                TiUIHelper.BACKGROUND_DISABLED_STATE,
                                TiConvert.toShadowArray((Object[]) newValue));
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_INNERSHADOWS)) {
                Shadow[] shadows = TiConvert.toShadowArray((Object[]) newValue);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_1, shadows);
                bgdDrawable.setInnerShadowsForState(
                        TiUIHelper.BACKGROUND_DEFAULT_STATE_2, shadows);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_OPACITY)) {
                if (background != null)
                    TiUIHelper.setDrawableOpacity(
                            background,
                            ViewHelper.getAlpha(this)
                                    * TiConvert.toFloat(newValue, 1f));

            } else if (key.equals(TiC.PROPERTY_BACKGROUND_REPEAT)) {
                backgroundRepeat = TiConvert.toBoolean(newValue);
            } else if (key.equals(TiC.PROPERTY_BACKGROUND_PADDING)) {
                Log.i(TAG, key + " not yet implemented.");
            }
            if (changedProperty)
                bgdDrawable.invalidateSelf();
        }
    }
        
    private TiBackgroundDrawable getOrCreateBackground() {
        if (background == null) {
            background = new TiBackgroundDrawable();
            setBackgroundDrawable(background);
        }
        return background;
    }
    
    private void setBackgroundImageDrawable(Object object, boolean backgroundRepeat, int[][] states) {
        TiBackgroundDrawable bgdDrawable = getOrCreateBackground();
        Drawable drawable = null;
        if (object instanceof TiBlob) {
            drawable = TiUIHelper.buildImageDrawable(getContext(), ((TiBlob)object).getImage(), backgroundRepeat, parentProxy);
            }
        else {
            drawable = TiUIHelper.buildImageDrawable(TiConvert.toString(object), backgroundRepeat, parentProxy);
        }
        for (int i = 0; i < states.length; i++) {
            bgdDrawable.setImageDrawableForState(states[i], drawable);
        }
    }

    public void processProperties(KrollDict d) {
        backgroundRepeat = d.optBoolean(TiC.PROPERTY_BACKGROUND_REPEAT, false);
        for (String key : d.keySet()) {
            propertySet(key, d.get(key), null, false);
        }
    }

    public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
    {
        propertySet(key,  newValue, oldValue, true);
    }
}

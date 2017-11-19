package ti.modules.titanium.ui.widget;

import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiDrawableReference;
import org.appcelerator.titanium.view.TiUIView;

public class TiToolbar extends TiUIView implements Handler.Callback{
	//region private primitive fields
	private final int BACKGROUND_TRANSLUCENT_VALUE = 92;
	private final int BACKGROUND_SOLID_VALUE = 255;
	//endregion
	//region private Object fields
	private Toolbar toolbar;
	private Object logo = null;
	private Object navigationIcon = null;
	private Object overflowMenuIcon = null;
	private TiViewProxy[] viewProxiesArray;
	//endregion

	/**
	 * Constructs a TiUIView object with the associated proxy.
     * 
     * @param proxy
     *            the associated proxy.
	 * @module.api
	 */
	public TiToolbar(TiViewProxy proxy) {
		super(proxy);
		toolbar = new Toolbar(proxy.getActivity());
		setNativeView(toolbar);
	}

	/**
	 * Adds custom views in the toolbar
     * 
     * @param proxies
     *            View proxies to be used
	 */
	public void setItems(TiViewProxy[] proxies) {
		if (proxies != null) {
			for (int i = 0; i < proxies.length; i++) {
                toolbar.addView(convertLayoutParamsForView(
                        proxies[i].getOrCreateView()));
			}
		}
	}

	/**
	 * Calculates the Status Bar's height depending on the device
     * 
     * @return The status bar's height. 0 if the API level does not have
     *         status_bar_height resource
	 */
	private int calculateStatusBarHeight() {
        int resourceId = TiApplication.getAppCurrentActivity().getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
            return TiApplication.getAppCurrentActivity().getResources()
                    .getDimensionPixelSize(resourceId);
		}
		return 0;
	}

	/**
     * Changes the LayoutParams type of custom views added to the Toolbar. Width
     * and height are preserved. They need to be of type Toolbar.LayoutParams.
     * 
	 * @param source
	 * @return
	 */
	private View convertLayoutParamsForView(TiUIView source) {
		View res = source.getNativeView();
		TiDimension widthDimension = source.getLayoutParams().optionWidth;
        int width = widthDimension != null ? widthDimension.getAsPixels(toolbar)
                : Toolbar.LayoutParams.WRAP_CONTENT;
		TiDimension heightDimension = source.getLayoutParams().optionHeight;
        int height = heightDimension != null
                ? heightDimension.getAsPixels(toolbar)
                : Toolbar.LayoutParams.WRAP_CONTENT;
		res.setLayoutParams(new Toolbar.LayoutParams(width, height));
		return res;
	}

	/**
	 * Shows the overflow menu if there is one.
	 */
	public void showOverFlowMenu() {
		if (!TiApplication.isUIThread()) {
            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    showOverFlowMenu();
                }
            }, true);
            return;
		} else {
            ((Toolbar) getNativeView()).showOverflowMenu();
		}
	}

	/**
	 * Hides the overflow menu if there is one.
	 */
	public void hideOverFlowMenu() {
		if (!TiApplication.isUIThread()) {
            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    hideOverFlowMenu();
		}
            }, true);
        } else {
		((Toolbar) getNativeView()).hideOverflowMenu();
	}
		}

	/**
	 * Return the current logo in the format it was passed
     * 
	 * @return
	 */
	public Object getLogo() {
		return logo;
	}

	/**
	 * Returns the currently set navigation icon in the format it was set.
     * 
	 * @return
	 */
	public Object getNavigationIcon() {
		return navigationIcon;
	}

	/**
	 * Returns the overflow menu icon in the format it was set.
     * 
	 * @return
	 */
	public Object getOverflowMenuIcon() {
		return overflowMenuIcon;
	}

    
	/**
     * Closes custom views's added in the toolbar.
	 */
	public void dismissPopupMenus() {
		if (!TiApplication.isUIThread()) {

            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    dismissPopupMenus();
	}
            }, true);
		} else {
            ((Toolbar) getNativeView()).dismissPopupMenus();
		}
	}

	/**
     * Saves the proxy objects of the views passed as custom items. Sets them as
     * current custom views.
     * 
	 * @param value
	 */
	private void setViewProxiesArray(Object[] value) {
		viewProxiesArray = new TiViewProxy[value.length];
		for (int i=0; i < value.length; i++) {
			viewProxiesArray[i] = (TiViewProxy) value[i];
		}
		setItems(viewProxiesArray);
	}

	/**
	 * Closes custom views's added in the toolbar.
	 */
	public void collapseActionView() {
		if (!TiApplication.isUIThread()) {

            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    collapseActionView();
	}
            }, true);
		} else {
            toolbar.collapseActionView();
		}
	}

    public void setContentInsetsRelative(final int insetLeft, final int insetRight) {
        Integer[] values = new Integer[]{insetLeft, insetRight};
		if (!TiApplication.isUIThread()) {
            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    setContentInsetsRelative(insetLeft, insetRight);
                }
            }, true);
		} else {
            toolbar.setContentInsetsRelative(values[0], values[1]);
		}
	}

    public void setContentInsetsAbsolute(final int insetLeft, final int insetRight) {
		Integer[] values = new Integer[]{insetLeft, insetRight};
		if (!TiApplication.isUIThread()) {
            proxy.runInUiThread(new CommandNoReturn() {
                public void execute() {
                    setContentInsetsAbsolute(insetLeft, insetRight);
		}
            }, true);
        } else {
		toolbar.setContentInsetsAbsolute(values[0], values[1]);
	}
		}

	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
		//region process common properties
        case TiC.PROPERTY_BAR_COLOR:
            toolbar.setBackgroundColor((TiConvert.toColor(newValue)));
            if (proxy.hasProperty(TiC.PROPERTY_TRANSLUCENT)) {
                if ((Boolean) proxy.getProperty(TiC.PROPERTY_TRANSLUCENT)) {
                    toolbar.getBackground().setAlpha(BACKGROUND_TRANSLUCENT_VALUE);
		}
			}
            break;
        case TiC.PROPERTY_EXTEND_BACKGROUND:
            if (TiConvert.toBoolean(newValue)) {
                Window window = TiApplication.getAppCurrentActivity().getWindow();
                // Calculate Status bar's height
                int statusBarHeight = calculateStatusBarHeight();
                // Add padding to extend the toolbar's background
                toolbar.setPadding(toolbar.getPaddingLeft(),
                        statusBarHeight + toolbar.getPaddingTop(),
                        toolbar.getPaddingRight(), toolbar.getPaddingBottom());
                // Set flags for the current window that allow drawing behind status bar
                window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		}
            break;
        case TiC.PROPERTY_ITEMS:
            setViewProxiesArray((Object[]) newValue);
            break;
        case TiC.PROPERTY_TRANSLUCENT:
            if (toolbar != null) {
                toolbar.getBackground()
                        .setAlpha(TiConvert.toBoolean(newValue)
                                ? BACKGROUND_TRANSLUCENT_VALUE
                                : BACKGROUND_SOLID_VALUE);
		}
            break;

        case TiC.PROPERTY_LOGO: {
            logo = newValue;
            TiDrawableReference tiDrawableReference = TiDrawableReference
                    .fromObject(proxy, logo);
            ((Toolbar) getNativeView()).setLogo(tiDrawableReference.getDrawable());
            break;
		}
        case TiC.PROPERTY_NAVIGATION_ICON: {
            navigationIcon = newValue;
            TiDrawableReference tiDrawableReference = TiDrawableReference
                    .fromObject(proxy, navigationIcon);
            ((Toolbar) getNativeView())
                    .setNavigationIcon(tiDrawableReference.getDrawable());
            break;
		}
        case TiC.PROPERTY_OVERFLOW_ICON: {
            overflowMenuIcon = newValue;
            TiDrawableReference tiDrawableReference = TiDrawableReference
                    .fromObject(proxy, overflowMenuIcon);
            ((Toolbar) getNativeView())
                    .setOverflowIcon(tiDrawableReference.getDrawable());
            break;
		}
        case TiC.PROPERTY_TITLE:
            if (toolbar != null) {
                toolbar.setTitle(TiConvert.toString(newValue));
		}
            break;
        case TiC.PROPERTY_TITLE_TEXT_COLOR:
            if (toolbar != null) {
                toolbar.setTitleTextColor(TiConvert.toColor(newValue));
		}
            break;
        case TiC.PROPERTY_SUBTITLE:
            if (toolbar != null) {
                toolbar.setSubtitle(TiConvert.toString(newValue));
		}
            break;
        case TiC.PROPERTY_SUBTITLE_TEXT_COLOR:
            if (toolbar != null) {
                toolbar.setSubtitleTextColor(TiConvert.toColor(newValue));
		}
            break;
        case TiC.PROPERTY_CONTENT_INSET_END_WITH_ACTIONS:
			if (toolbar != null) {
                toolbar.setContentInsetEndWithActions(TiConvert.toInt(newValue));
			}
            break;
        case TiC.PROPERTY_CONTENT_INSET_START_WITH_NAVIGATION:
			if (toolbar != null) {
                toolbar.setContentInsetStartWithNavigation(TiConvert.toInt(newValue));
			}
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
		}
	}
		}

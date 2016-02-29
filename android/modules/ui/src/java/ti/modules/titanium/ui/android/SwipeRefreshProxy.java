package ti.modules.titanium.ui.android;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import ti.modules.titanium.ui.widget.TiUISwipeRefresh;

@Kroll.proxy(creatableInModule = AndroidModule.class, propertyAccessors = {
        "colors"
    })
public class SwipeRefreshProxy extends TiViewProxy {

    @Override
    public TiUIView createView(Activity activity) {
        return new TiUISwipeRefresh(this);
    }
    
    @Override
    public String getApiName()
    {
        return "Ti.UI.Android.SwipeRefresh";
    }
}

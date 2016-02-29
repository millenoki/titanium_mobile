package ti.modules.titanium.ui.widget;

import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;

public class TiUISwipeRefresh extends TiUIView implements OnRefreshListener {
    private SwipeRefreshLayout refresh;

    public TiUISwipeRefresh(TiViewProxy proxy) {
        super(proxy);
        refresh = new SwipeRefreshLayout(proxy.getActivity());
        refresh.setNestedScrollingEnabled(true);
        refresh.setOnRefreshListener(this);
        setNativeView(refresh);
    }
    
    
    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case "colors":
            int colors[] = null;
            if (newValue instanceof Object[]) {
                final int length = ((Object[])newValue).length;
                colors = new int[length];
                
                for (int i = 0; i < length; i++) {
                    colors[i] = TiConvert.toColor(((Object[])newValue)[i]);
                }
            }
            refresh.setColorSchemeColors(colors);
            break;

        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }


    @Override
    public void onRefresh() {
        proxy.fireEvent("refresh");
    }

}

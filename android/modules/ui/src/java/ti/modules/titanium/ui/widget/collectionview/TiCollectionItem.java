package ti.modules.titanium.ui.widget.collectionview;

import java.util.HashMap;

import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;

import android.view.View;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListItem;

public class TiCollectionItem extends TiAbsListItem {

    public TiCollectionItem(TiViewProxy proxy) {
        super(proxy);
    }
    
    public TiCollectionItem(TiViewProxy proxy, View v, View item_layout) {
        super(proxy, v, item_layout);
    }

    @Override
    protected int fillLayout(HashMap d) {
        return TiConvert.fillLayout(d, layoutParams, true);
    }
}

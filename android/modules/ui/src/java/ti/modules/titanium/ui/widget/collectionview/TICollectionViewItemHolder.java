package ti.modules.titanium.ui.widget.collectionview;

import java.util.HashMap;

import org.appcelerator.titanium.view.TiCompositeLayout;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import ti.modules.titanium.ui.widget.abslistview.TiBaseAbsListViewItem;
import ti.modules.titanium.ui.widget.abslistview.TiBaseAbsListViewItemHolder;

public class TICollectionViewItemHolder extends TiBaseAbsListViewItemHolder {
    private boolean hasWidthRelyingOnPercent = false;

    public TICollectionViewItemHolder(Context context) {
        super(context);
    }
    
    @Override
    protected void initialize(Context context) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (hasWidthRelyingOnPercent) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    parentView.get().getMeasuredWidth(),
                    MeasureSpec.UNSPECIFIED);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setItem(TiBaseAbsListViewItem item, HashMap itemData,
            View parentView) {
        super.setItem(item, itemData, parentView);
        hasWidthRelyingOnPercent = false;

        if (item != null) {
            ViewGroup.LayoutParams params = item.getTiLayoutParams();
            if (params instanceof TiCompositeLayout.LayoutParams
                    && !((TiCompositeLayout.LayoutParams) params)
                            .fixedSizeWidth()) {
                hasWidthRelyingOnPercent = true;
            }
        }
    }
}

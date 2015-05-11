package ti.modules.titanium.ui.widget.abslistview;


import java.lang.ref.WeakReference;

import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import se.emilsjolander.stickylistheaders.StickyListHeadersListViewAbstract;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class TiBaseAbsListViewItemHolder extends TiCompositeLayout {
	private WeakReference<StickyListHeadersListViewAbstract> listView = null;
	private WeakReference<TiBaseAbsListViewItem> item = null;

	public TiBaseAbsListViewItemHolder(Context context) {
		super(context, LayoutArrangement.HORIZONTAL, null);
		addView(new TiBaseAbsListViewItem(context));
		
	    final float scale = getResources().getDisplayMetrics().density;
		TiCompositeLayout.LayoutParams p = new TiCompositeLayout.LayoutParams();
        p.autoFillsHeight = true;
        p.sizeOrFillHeightEnabled = true;
        p.sizeOrFillWidthEnabled = true;
        p.autoFillsWidth = false;
        p.optionLeft = new TiDimension(5, TiDimension.TYPE_LEFT);
        p.optionRight = new TiDimension(5, TiDimension.TYPE_RIGHT);
		ImageView imageView = new ImageView(context);
		imageView.setId(TiAbsListView.accessory);
        imageView.setFocusable(false);
        imageView.setAdjustViewBounds(true);
        imageView.setMaxWidth((int) (25 * scale));
        imageView.setMaxHeight((int) (25 * scale));
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        imageView.setVisibility(GONE);
        addView(imageView, p);
	}

	public TiBaseAbsListViewItemHolder(Context context, AttributeSet set) {
        this(context);
	}
	public void setListView(StickyListHeadersListViewAbstract listView) {
        if (listView != null) {
            this.listView = new WeakReference<StickyListHeadersListViewAbstract>(listView);
        } else {
            this.listView = null;
        }
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	    if (item != null && listView != null) {
	        ViewGroup.LayoutParams params = item.get().getTiLayoutParams();
	        if (params instanceof TiCompositeLayout.LayoutParams) {
	            TiDimension heightOption = ((TiCompositeLayout.LayoutParams)params).optionHeight;
	            if (heightOption != null && heightOption.isUnitPercent()) {
	                heightMeasureSpec = MeasureSpec.makeMeasureSpec(listView.get().getMeasuredHeight(), MeasureSpec.AT_MOST);
	            }
	        }
	    }
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

    public void setItem(TiBaseAbsListViewItem item) {
        if (item != null) {
            this.item = new WeakReference<TiBaseAbsListViewItem>(item);
        } else {
            this.item = null;
        }
    }
}

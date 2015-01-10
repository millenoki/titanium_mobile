package ti.modules.titanium.ui.widget.abslistview;


import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.view.TiCompositeLayout;

import se.emilsjolander.stickylistheaders.StickyListHeadersListViewAbstract;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class TiBaseAbsListViewItemHolder extends TiCompositeLayout {
	private StickyListHeadersListViewAbstract listView = null;

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
	public void setListView(StickyListHeadersListViewAbstract listView2) {
		listView = listView2;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		if (listView != null) {
//	        int hFromSpec = MeasureSpec.getSize(heightMeasureSpec);
//	        if (hFromSpec == 0) {
	            heightMeasureSpec = MeasureSpec.makeMeasureSpec(listView.getMeasuredHeight(), MeasureSpec.AT_MOST);
//	        }
//		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
}

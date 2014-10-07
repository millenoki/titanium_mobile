package ti.modules.titanium.ui.widget.listview;

import org.appcelerator.titanium.view.TiCompositeLayout;

import ti.modules.titanium.ui.widget.CustomListView;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class TiBaseListViewItemHolder extends TiCompositeLayout {
	private CustomListView listView = null;

	public TiBaseListViewItemHolder(Context context) {
		super(context);
	}

	public TiBaseListViewItemHolder(Context context, AttributeSet set) {
		super(context, set);
	}
	public void setListView(CustomListView listView2) {
		listView = listView2;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (listView != null)
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(listView.getMeasuredHeight(), MeasureSpec.AT_MOST));
		else 
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);

	}
}

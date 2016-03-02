package ti.modules.titanium.ui.widget.collectionview;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;

public abstract class SwipeMenuViewHolder extends ViewHolder {
    SwipeMenuLayout swipeMenu;
    public SwipeMenuViewHolder(View itemView) {
        super(new SwipeMenuLayout(itemView.getContext()));
        swipeMenu = (SwipeMenuLayout) this.itemView;
        swipeMenu.setContentView(itemView);
    }
    public abstract boolean canShowLeftMenu();
    public abstract boolean canShowRightMenu();
    public abstract View[] getLeftButtons();
    public abstract View[] getRightButtons();

}

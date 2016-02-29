/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.ui.widget.collectionview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.TiLifecycle.OnInstanceStateEvent;
import org.appcelerator.titanium.animation.TiAnimation;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUINonViewGroupView;
import org.appcelerator.titanium.view.TiUIView;
import org.json.JSONException;

import ti.modules.titanium.ui.SearchBarProxy;
import ti.modules.titanium.ui.UIModule;
import ti.modules.titanium.ui.ViewProxy;
import ti.modules.titanium.ui.android.SearchViewProxy;
import ti.modules.titanium.ui.widget.abslistview.AbsListItemProxy;
import ti.modules.titanium.ui.widget.abslistview.AbsListSectionProxy;
import ti.modules.titanium.ui.widget.abslistview.AbsListViewProxy;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListView;
import ti.modules.titanium.ui.widget.abslistview.TiAbsListViewTemplate;
import ti.modules.titanium.ui.widget.abslistview.TiBaseAbsListViewItem;
import ti.modules.titanium.ui.widget.abslistview.TiBaseAbsListViewItemHolder;
import ti.modules.titanium.ui.widget.abslistview.TiCollectionViewAdapter;
import ti.modules.titanium.ui.widget.abslistview.TiCollectionViewInterface;
import ti.modules.titanium.ui.widget.abslistview.TiDefaultAbsListViewTemplate;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar.OnSearchChangeListener;
import ti.modules.titanium.ui.widget.searchview.TiUISearchView;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.Pair;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ListView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;

public class TiCollectionView extends TiUINonViewGroupView
        implements OnSearchChangeListener, 
        TiCollectionViewInterface, 
        OnInstanceStateEvent ,
        FlexibleAdapter.OnItemClickListener, FlexibleAdapter.OnItemLongClickListener,
        FlexibleAdapter.OnItemMoveListener, FlexibleAdapter.OnItemSwipeListener
        , FlexibleAdapter.OnStickyHeaderChangeListener{
    RecyclerView mRecyclerView;
    TiGridLayoutManager layoutManager;
    private TiBaseAdapter mAdapter;
    private HeaderDecoration headerDecoration;
    private List<AbsListSectionProxy> sections;
    private AtomicInteger itemTypeCount;
    private String defaultTemplateBinding;
    private HashMap<String, TiAbsListViewTemplate> templatesByBinding;
    private HashMap<Integer, TiAbsListViewTemplate> templatesByType;
    public static int listContentId = 24123;
    public static int isCheck;
    public static int hasChild;
    public static int disclosure;
    private int[] marker = new int[2];
    private String searchText;
    private boolean caseInsensitive;
    private static final String TAG = "TiCollectionView";
    private boolean hideKeyboardOnScroll = true;

    private TiDimension mColumnsWidth = null;
    private int mNumColumns = 0;

    private Object mAppearAnimation = null;
    private boolean mUseAppearAnimation = false;
    private Animator mAppearAnimators = null;

    protected static final int TIFLAG_NEEDS_DATASET = 0x00000001;
    protected static final int TIFLAG_NEEDS_ADAPTER_CHANGE = 0x00000002;

    private Set<AbsListItemProxy> handledProxies;

    private int overallXScroll = 0;
    private int overallYScroll = 0;

    private static final String defaultTemplateKey = UIModule.LIST_ITEM_TEMPLATE_DEFAULT;
    private static final TiAbsListViewTemplate defaultTemplate = new TiDefaultAbsListViewTemplate(
            defaultTemplateKey);
    public static final String BLANK_HEADER_TEMPLATE_KEY = "__empty_header__";
    private static TiAbsListViewTemplate blankHeaderTemplate = null;

    /*
     * We cache properties that already applied to the recycled list tiem in
     * ViewItem.java However, since Android randomly selects a cached view to
     * recycle, our cached properties will not be in sync with the native view's
     * properties when user changes those values via User Interaction - i.e
     * click. For this reason, we create a list that contains the properties
     * that must be reset every time a view is recycled, to ensure
     * synchronization. Currently, only "value" is in this list to correctly
     * update the value of Ti.UI.Switch.
     */
    // public static List<String> MUST_SET_PROPERTIES =
    // Arrays.asList(TiC.PROPERTY_VALUE, TiC.PROPERTY_AUTO_LINK,
    // TiC.PROPERTY_TEXT, TiC.PROPERTY_HTML);

    // public static final String MIN_SEARCH_HEIGHT = "50dp";
    public static final int HEADER_FOOTER_WRAP_ID = 12345;
    public static final int HEADER_VIEW_TYPE = 0;
    public static final int FOOTER_VIEW_TYPE = 1;
    public static final int BUILT_IN_TEMPLATE_ITEM_TYPE = 2;
    public static final int BLANK_HEADER_ITEM_TYPE = 3;
    public static final int CUSTOM_TEMPLATE_ITEM_TYPE = 4;
    

    protected Pair<AbsListSectionProxy, Pair<Integer, Integer>> getSectionInfoByEntryIndex(
            int index) {
        if (index < 0) {
            return null;
        }
        synchronized (sections) {
            for (int i = 0; i < sections.size(); i++) {
                AbsListSectionProxy section = sections.get(i);
                int sectionItemCount = section.getItemCount();
                if (index <= sectionItemCount - 1) {
                    Pair<AbsListSectionProxy, Pair<Integer, Integer>> result = new Pair<AbsListSectionProxy, Pair<Integer, Integer>>(
                            section, new Pair<Integer, Integer>(i, index));
                    return result;
                } else {
                    index -= sectionItemCount;
                }
            }
        }

        return null;
    }

    public class TiGridLayoutManager extends GridLayoutManager {
        private boolean isScrollEnabled = true;
        protected int requestedColumnWidth = 0;
        protected int requestedColumnCount = 1;
        private boolean needsChange = true;

        public TiGridLayoutManager(Context context) {
            /*
             * Initially set spanCount to 1, will be changed automatically
             * later.
             */
            super(context, 1);
        }

        public TiGridLayoutManager(Context context, int columnWidth,
                int orientation, boolean reverseLayout) {
            /*
             * Initially set spanCount to 1, will be changed automatically
             * later.
             */
            super(context, 1, orientation, reverseLayout);
        }

        public void setColumnWidth(final int width) {
            if (width != requestedColumnWidth) {
                requestedColumnCount = -1;
                requestedColumnWidth = width;
                needsChange = true;
                requestLayout();
            }

        }

        public void setNumColumns(int requestedColumnCount) {
            if (this.requestedColumnCount != requestedColumnCount) {
                requestedColumnWidth = 0;
                this.requestedColumnCount = requestedColumnCount;
                needsChange = true;
                requestLayout();
            }
        }

        public int getNumColumns() {
            return requestedColumnCount;
        }

        public int getColumnWidth() {
            return requestedColumnWidth;
        }

        public void requestColumnUpdate() {
            needsChange = true;
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            if (needsChange) {
                if (requestedColumnWidth > 0) {
                    int totalSpace;
                    if (getOrientation() == VERTICAL) {
                        totalSpace = getWidth() - getPaddingRight()
                                - getPaddingLeft();
                    } else {
                        totalSpace = getHeight() - getPaddingTop()
                                - getPaddingBottom();
                    }
                    int spanCount = Math.max(1,
                            totalSpace / requestedColumnWidth);
                    setSpanCount(spanCount);
                } else {
                    setSpanCount(requestedColumnCount);
                }
                needsChange = false;
            }
            super.onLayoutChildren(recycler, state);
        }

        public void setScrollEnabled(boolean flag) {
            this.isScrollEnabled = flag;
        }

        @Override
        public boolean canScrollHorizontally() {
            return isScrollEnabled && super.canScrollHorizontally();
        }

        @Override
        public boolean canScrollVertically() {
            return isScrollEnabled && super.canScrollVertically();
        }
    }

    public class HeaderDecoration extends RecyclerView.ItemDecoration {

        private final View mView;
        private final GridLayoutManager layoutManager;

        public HeaderDecoration(View view, GridLayoutManager layoutManager) {
            mView = view;
            this.layoutManager = layoutManager;
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent,
                RecyclerView.State state) {
            super.onDraw(c, parent, state);
            // layout basically just gets drawn on the reserved space on top of
            // the first view
            mView.layout(parent.getLeft(), 0, parent.getRight(),
                    mView.getMeasuredHeight());

            for (int i = 0; i < parent.getChildCount(); i++) {
                View view = parent.getChildAt(i);
                if (parent.getChildAdapterPosition(view) == 0) {
                    c.save();
                    if (this.layoutManager
                            .getLayoutDirection() == GridLayoutManager.HORIZONTAL) {
                        c.clipRect(parent.getLeft(), parent.getTop(),
                                view.getLeft(), parent.getBottom());
                        mView.draw(c);

                    } else {
                        c.clipRect(parent.getLeft(), parent.getTop(),
                                parent.getRight(), view.getTop());
                        mView.draw(c);
                    }
                    c.restore();
                    break;
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            if (parent.getChildAdapterPosition(view) < layoutManager
                    .getSpanCount()) {
                if (this.layoutManager
                        .getLayoutDirection() == GridLayoutManager.HORIZONTAL) {
                    if (mView.getMeasuredWidth() <= 0) {
                        mView.measure(
                                View.MeasureSpec.makeMeasureSpec(
                                        parent.getMeasuredWidth(),
                                        View.MeasureSpec.AT_MOST),
                                View.MeasureSpec.makeMeasureSpec(
                                        parent.getMeasuredHeight(),
                                        View.MeasureSpec.AT_MOST));
                    }
                    outRect.set(mView.getMeasuredWidth(), 0, 0, 0);
                } else {
                    if (mView.getMeasuredHeight() <= 0) {
                        mView.measure(
                                View.MeasureSpec.makeMeasureSpec(
                                        parent.getMeasuredWidth(),
                                        View.MeasureSpec.AT_MOST),
                                View.MeasureSpec.makeMeasureSpec(
                                        parent.getMeasuredHeight(),
                                        View.MeasureSpec.AT_MOST));
                    }
                    outRect.set(0, mView.getMeasuredHeight(), 0, 0);
                }
            } else {
                outRect.setEmpty();
            }
        }
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        TiBaseAbsListViewItem itemContent;

        public CollectionViewHolder(final TiBaseAbsListViewItemHolder content) {
            super(content);
            itemContent = (TiBaseAbsListViewItem) content
                    .findViewById(listContentId);

        }
    }

    public class TiBaseAdapter extends FlexibleAdapter
            implements TiCollectionViewAdapter {

        Activity context;
        private boolean canNotifyDataSetChanged = true;
        private Set mShownIndexes;
        private boolean mCounted = false;
        private int mCount = 0;

        public TiBaseAdapter(Activity activity) {
            super(TiCollectionView.this);
            context = activity;
        }

        public void setCanNotifyDataSetChanged(
                final boolean canNotifyDataSetChanged) {
            this.canNotifyDataSetChanged = canNotifyDataSetChanged;
        }

        public void notifyDataChanged() {
            if (!canNotifyDataSetChanged) {
                return;
            }
            if (mShownIndexes != null) {
                mShownIndexes.clear();
            }
            // canShowMenus = false;
            mCounted = false;
            super.notifyDataSetChanged();
        }

        @Override
        public List<Animator> getAnimators(View itemView, int position,
                boolean isSelected) {
            List<Animator> animators = new ArrayList<Animator>();
            // if (mRecyclerView.getLayoutManager() instanceof
            // GridLayoutManager) {
            // //GridLayout
            // if (position % 2 != 0)
            // addSlideInFromRightAnimator(animators, itemView, 0.5f);
            // else
            // addSlideInFromLeftAnimator(animators, itemView, 0.5f);
            // } else {
            // //LinearLayout
            // switch (getItemViewType(position)) {
            // case R.layout.recycler_uls_row:
            // case EXAMPLE_VIEW_TYPE:
            // addScaleInAnimator(animators, itemView, 0.0f);
            // break;
            // default:
            // if (isSelected)
            // addSlideInFromRightAnimator(animators, itemView, 0.5f);
            // else
            // addSlideInFromLeftAnimator(animators, itemView, 0.5f);
            // break;
            // }
            // }

            // Alpha Animator is automatically added
            return animators;
        }

        public RecyclerView.ViewHolder createViewHolder(ViewGroup parent,
                TiAbsListViewTemplate template) {
            if (template == null) {
                return null; // should never happen!
            }
            TiBaseAbsListViewItem itemContent = null;
            TICollectionViewItemHolder content = new TICollectionViewItemHolder(
                    getContext());
            content.setTag(template.getType());
            itemContent = (TiBaseAbsListViewItem) content
                    .findViewById(listContentId);

            AbsListItemProxy itemProxy = template.generateCellProxy(proxy,
                    getCellProxyRootType());
            itemProxy.setListProxy(proxy);
            addHandledProxy(itemProxy);
            TiCollectionItem listItem = new TiCollectionItem(itemProxy,
                    itemContent, content);
            itemProxy.setActivity(proxy.getActivity());
            itemProxy.setView(listItem);
            itemContent.setView(listItem);
            itemProxy.realizeViews();

            return new CollectionViewHolder(content);
        }

        @Override
        public RecyclerView.ViewHolder onCreateNormalViewHolder(
                ViewGroup parent, int viewType) {
            return createViewHolder(parent, templatesByType.get(viewType));
        }

        @Override
        public ViewHolder onCreateHeaderViewHolder(ViewGroup parent,
                int viewType) {
            return createViewHolder(parent, templatesByType.get(viewType));

        }

        // @Override
        // public void onBindViewHolder(RecyclerView.ViewHolder holder,
        // int position) {
        //
        // }

        @Override
        public int getItemCount() {
            if (mCounted) {
                return mCount;
            }
            mCount = 0;
            synchronized (sections) {
                for (int i = 0; i < sections.size(); i++) {
                    AbsListSectionProxy section = sections.get(i);
                    mCount += section.getItemCount();
                }
            }
            mCounted = true;
            return mCount;
        }

        @Override
        public void onBindNormalViewHolder(ViewHolder holder, int position,
                boolean selected) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info == null) {
                return;
            }
            AbsListSectionProxy section = info.first;

            int sectionItemIndex = info.second.second;
            int sectionIndex = info.second.first;
            // check marker
            if (sectionIndex > marker[0] || (sectionIndex == marker[0]
                    && sectionItemIndex >= marker[1])) {
                if (proxy.hasListeners(TiC.EVENT_MARKER, false)) {
                    proxy.fireEvent(TiC.EVENT_MARKER, null, false, false);
                }
                resetMarker();
            }
            // Handling templates
            HashMap item = section.getListItem(sectionItemIndex);
            if (item == null || !(holder instanceof CollectionViewHolder)) {
                return;
            }
            TiBaseAbsListViewItem itemContent = ((CollectionViewHolder) holder).itemContent;
            if (itemContent == null) {
                return;
            }
            TiAbsListViewTemplate template = getTemplate(
                    TiConvert.toString(item, TiC.PROPERTY_TEMPLATE), true);
            boolean reusing = sectionIndex != itemContent.sectionIndex
                    || itemContent.itemIndex >= section.getItemCount()
                    || item != section.getListItem(itemContent.itemIndex);
            section.populateViews(item, itemContent, template, sectionItemIndex,
                    sectionIndex, holder.itemView, reusing);
            if (holder.itemView instanceof TiBaseAbsListViewItemHolder) {
                ((TiBaseAbsListViewItemHolder) holder.itemView)
                        .setItem(itemContent, item, mRecyclerView);

            }
            if (mUseAppearAnimation) {
                if (mShownIndexes == null) {
                    mShownIndexes = new HashSet<Integer>();
                }
                if (!mShownIndexes.contains(position)) {
                    mShownIndexes.add(position);
                    if (mAppearAnimators != null) {
                        Animator anim = mAppearAnimators.clone();
                        anim.setTarget(holder.itemView);
                        anim.start();
                    } else {
                        Object anim = item.get("appearAnimation");
                        if (anim == null) {
                            anim = mAppearAnimation;
                        }
                        if (anim != null) {
                            AbsListItemProxy proxy = itemContent.getProxy();
                            Animator animator = proxy
                                    .getAnimatorSetForAnimation(anim);
                            animator.start();
                        }
                    }
                }

            }
        }

        @Override
        public Integer getHeaderId(int position) {
            // Get section info from index
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info != null) {
                AbsListSectionProxy section = info.first;
                return section.getIndex(); // return even for fake headers
            }
            return null;
        }

        @Override
        public boolean isSelectable(int position) {
            return !isHeader(position);
        }

        @Override
        public void onBindHeaderViewHolder(ViewHolder holder, int position) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info == null) {
                // onBindNormalViewHolder(holder, position, false);
                return;
            }
            int sectionItemIndex = info.second.second;
            onBindNormalViewHolder(holder, position - sectionItemIndex, false);
        }

        @Override
        public int getNormalViewType(int position) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info == null) {
                return -1; // should never happen!
            }
            AbsListSectionProxy section = info.first;
            int sectionItemIndex = info.second.second;
            if (!section.hasHeader() && sectionItemIndex == 0) {
                if (blankHeaderTemplate == null) {
                    KrollDict template;
                    try {
                        template = new KrollDict("{properties:{height:0}}");
                        blankHeaderTemplate = new TiAbsListViewTemplate(
                                BLANK_HEADER_TEMPLATE_KEY, template);
                        blankHeaderTemplate.setType(BLANK_HEADER_ITEM_TYPE);
                        templatesByBinding.put(BLANK_HEADER_TEMPLATE_KEY,
                                blankHeaderTemplate);
                        templatesByType.put(blankHeaderTemplate.getType(),
                                blankHeaderTemplate);
                    } catch (JSONException e) {
                    }
                }
                return getTemplate(BLANK_HEADER_TEMPLATE_KEY, true).getType();
            }
            final String templateText = section
                    .getTemplateByIndex(sectionItemIndex);
            return getTemplate(templateText, true).getType();
        }

        @Override
        public int getHeaderViewType(int position) {
            return getNormalViewType(position);
        }

        public void customNotifyItemRangeRemoved(final int sectionIndex,
                int positionStart, int itemCount) {
            for (AbsListItemProxy itemProxy : handledProxies) {
                if (itemProxy.sectionIndex == sectionIndex
                        && itemProxy.itemIndex >= positionStart + itemCount) {
                    itemProxy.updateItemIndex(itemProxy.itemIndex - itemCount);
                }
            }
            final int realPosition = findItemPosition(sectionIndex,
                    positionStart, true);
            mCounted = false;
            super.notifyItemRangeRemoved(realPosition, itemCount);
        }

        public void customNotifyItemRangeInserted(final int sectionIndex,
                int positionStart, int itemCount) {
            final int realPosition = findItemPosition(sectionIndex,
                    positionStart, true);
            for (AbsListItemProxy itemProxy : handledProxies) {
                if (itemProxy.sectionIndex == sectionIndex
                        && itemProxy.itemIndex >= positionStart + itemCount) {
                    itemProxy.updateItemIndex(itemProxy.itemIndex + itemCount);
                }
            }
            mCounted = false;
            super.notifyItemRangeInserted(realPosition, itemCount);
            mRecyclerView.scrollToPosition(realPosition);
        }

        public void customNotifyItemRangeChanged(final int sectionIndex,
                int positionStart, int itemCount) {
            final int realPosition = findItemPosition(sectionIndex,
                    positionStart);
            super.notifyItemRangeChanged(realPosition, itemCount);
        }

        public int findSectionStartPosition(int sectionIndex) {
            int position = 0;
            synchronized (sections) {
                for (int i = 0; i < sections.size(); i++) {
                    AbsListSectionProxy section = sections.get(i);
                    if (i == sectionIndex) {
                        break;
                    } else {
                        position += section.getItemCount();
                    }
                }
            }
            return position;
        }

        @Override
        public int getHeaderPosition(int position) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info == null) {
                // if (info == null || !info.first.hasHeader()) {
                return -1;
            }
            return findSectionStartPosition(info.second.first);
        }

        @Override
        public FrameLayout getStickyHeadersHolder() {
            return getOrCreateBorderView();
        }

        @Override
        public LayoutParams getStickyHeadersLayoutParams() {
            TiCompositeLayout.LayoutParams newParams = new TiCompositeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            newParams.optionLeft = TiConvert.toTiDimension(0,
                    TiDimension.TYPE_LEFT);
            newParams.optionTop = TiConvert.toTiDimension(0,
                    TiDimension.TYPE_TOP);
            return newParams;
        }

    }

    private void addHandledProxy(final AbsListItemProxy proxy) {
        if (handledProxies == null) {
            handledProxies = new HashSet<AbsListItemProxy>();
        }
        handledProxies.add(proxy);
    }

    public TiCollectionView(TiViewProxy proxy, Activity activity) {
        super(proxy);

        // initializing variables
        sections = Collections
                .synchronizedList(new ArrayList<AbsListSectionProxy>());
        itemTypeCount = new AtomicInteger(CUSTOM_TEMPLATE_ITEM_TYPE);
        templatesByBinding = new HashMap<String, TiAbsListViewTemplate>();
        defaultTemplateBinding = defaultTemplateKey;
        templatesByBinding.put(defaultTemplateKey, defaultTemplate);
        defaultTemplate.setType(BUILT_IN_TEMPLATE_ITEM_TYPE);
        caseInsensitive = true;

        // handling marker
        HashMap<String, Integer> preloadMarker = ((AbsListViewProxy) proxy)
                .getPreloadMarker();
        if (preloadMarker != null) {
            setMarker(preloadMarker);
        } else {
            resetMarker();
        }

        final KrollProxy fProxy = proxy;
        layoutManager = new TiGridLayoutManager(activity);
        mRecyclerView = new RecyclerView(activity) {

            @Override
            protected void onLayout(boolean changed, int left, int top,
                    int right, int bottom) {

                super.onLayout(changed, left, top, right, bottom);
                if (changed) {
                    TiUIHelper.firePostLayoutEvent(TiCollectionView.this);
                }

            }

            @Override
            protected void onMeasure(int widthMeasureSpec,
                    int heightMeasureSpec) {

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (mColumnsWidth != null && !mColumnsWidth.isUnitFixed()) {
                    layoutManager
                            .setColumnWidth(mColumnsWidth.getAsPixels(this));
                    // } else if (gridAdapter.updateNumColumns()) {
                    // adapter.notifyDataSetChanged();
                }
                layoutManager.requestColumnUpdate();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (touchPassThrough == true)
                    return false;
                return super.dispatchTouchEvent(event);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                try {
                    super.dispatchDraw(canvas);
                } catch (IndexOutOfBoundsException e) {
                    // samsung error
                }
            }
        };

        mAdapter = new TiBaseAdapter(activity);
        mAdapter.setDisplayHeaders(true);
        // mAdapter.enableStickyHeaders(100);

        mRecyclerView.addOnScrollListener(new OnScrollListener() {
            private boolean scrollTouch = false;
            private int lastValidfirstItem = 0;
            private Timer endTimer = null;

            public void cancelEndCall() {
                if (endTimer != null) {
                    endTimer.cancel();
                    endTimer = null;
                }
            }

            public void delayEndCall() {
                cancelEndCall();
                endTimer = new Timer();

                TimerTask action = new TimerTask() {
                    public void run() {
                        scrollTouch = false;
                        if (fProxy.hasListeners(TiC.EVENT_SCROLLEND, false)) {
                            fProxy.fireEvent(TiC.EVENT_SCROLLEND,
                                    dictForScrollEvent(), false, false);
                        }
                    }

                };

                this.endTimer.schedule(action, 200);
            }

            @Override
            public void onScrollStateChanged(RecyclerView view,
                    int scrollState) {

                view.requestDisallowInterceptTouchEvent(
                        scrollState != ViewPager.SCROLL_STATE_IDLE);
                if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (scrollTouch) {
                        delayEndCall();
                    }
                } else if (scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
                    cancelEndCall();
                } else if (scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    cancelEndCall();
                    if (hideKeyboardOnScroll && hasFocus()) {
                        blur();
                    }
                    if (scrollTouch == false) {
                        scrollTouch = true;
                        if (fProxy.hasListeners(TiC.EVENT_SCROLLSTART, false)) {
                            fProxy.fireEvent(TiC.EVENT_SCROLLSTART,
                                    dictForScrollEvent(), false, false);
                        }
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView view, int dx, int dy) {
                overallXScroll = overallXScroll + dx;
                overallYScroll = overallYScroll + dy;
                if (dx == 0 && dy == 0) {
                    return;
                }
                // Log.d(TAG, "onScroll : " + scrollValid, Log.DEBUG_MODE);
                // boolean fireScroll = scrollValid;
                // if (!fireScroll && visibleItemCount > 0) {
                // //Items in a list can be selected with a track ball in which
                // case
                // //we must check to see if the first visibleItem has changed.
                // fireScroll = (lastValidfirstItem != firstVisibleItem);
                // }
                if (fProxy.hasListeners(TiC.EVENT_SCROLL, false)) {
                    // Log.d(TAG, "newScrollOffset : " + newScrollOffset,
                    // Log.DEBUG_MODE);
                    fProxy.fireEvent(TiC.EVENT_SCROLL,
                            dictForScrollEvent(overallXScroll, overallYScroll),
                            false, false);
                }
            }
        });

        // mRecyclerView.setOnStickyHeaderChangedListener(
        // new OnStickyHeaderChangedListener() {
        //
        // @Override
        // public void onStickyHeaderChanged(
        // StickyListHeadersListViewAbstract l, View header,
        // int itemPosition, long headerId) {
        // // for us headerId is the section index
        // int sectionIndex = (int) headerId;
        // if (fProxy.hasListeners(TiC.EVENT_HEADER_CHANGE,
        // false)) {
        // KrollDict data = new KrollDict();
        // AbsListSectionProxy section = null;
        // synchronized (sections) {
        // if (sectionIndex >= 0
        // && sectionIndex < sections.size()) {
        // section = sections.get(sectionIndex);
        // } else {
        // return;
        // }
        // }
        // data.put(TiC.PROPERTY_HEADER_VIEW, section
        // .getHoldedProxy(TiC.PROPERTY_HEADER_VIEW));
        // data.put(TiC.PROPERTY_SECTION, section);
        // data.put(TiC.PROPERTY_SECTION_INDEX, sectionIndex);
        // fProxy.fireEvent(TiC.EVENT_HEADER_CHANGE, data,
        // false, false);
        // }
        // }
        // });

        // mRecyclerView.setCacheColorHint(Color.TRANSPARENT);
        mRecyclerView.setEnabled(true);
        getLayoutParams().autoFillsHeight = true;
        getLayoutParams().autoFillsWidth = true;
        // listView.setFocusable(false);
        mRecyclerView.setFocusable(true);
        mRecyclerView
                .setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        try {
            // headerFooterId =
            // TiRHelper.getApplicationResource("layout.titanium_ui_list_header_or_footer");
            // titleId =
            // TiRHelper.getApplicationResource("id.titanium_ui_list_header_or_footer_title");
            isCheck = TiRHelper.getApplicationResource(
                    "drawable.btn_check_buttonless_on_64");
            hasChild = TiRHelper.getApplicationResource("drawable.btn_more_64");
            disclosure = TiRHelper
                    .getApplicationResource("drawable.disclosure_64");
        } catch (ResourceNotFoundException e) {
            Log.e(TAG, "XML resources could not be found!!!", Log.DEBUG_MODE);
        }
        setNativeView(mRecyclerView);

        // needs to be fired after because
        // getStickyHeadersHolder will be called and need nativeView
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
    }
   

    public void setMarker(HashMap<String, Integer> markerItem) {
        marker[0] = markerItem.get(TiC.PROPERTY_SECTION_INDEX);
        marker[1] = markerItem.get(TiC.PROPERTY_ITEM_INDEX);

    }

    private void resetMarker() {
        marker[0] = Integer.MAX_VALUE;
        marker[1] = Integer.MAX_VALUE;
    }

    private KrollDict dictForScrollEvent(final int xScroll, final int yScroll) {
        KrollDict eventArgs = new KrollDict();
        KrollDict size = new KrollDict();
        size.put(TiC.PROPERTY_WIDTH,
                TiCollectionView.this.getNativeView().getWidth());
        size.put(TiC.PROPERTY_HEIGHT,
                TiCollectionView.this.getNativeView().getHeight());
        eventArgs.put(TiC.PROPERTY_SIZE, size);

        int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        eventArgs.put("firstVisibleItem", firstVisibleItem);
        eventArgs.put("visibleItemCount",
                lastVisiblePosition - firstVisibleItem);
        KrollDict point = new KrollDict();
        point.put(TiC.PROPERTY_X, xScroll);
        point.put(TiC.PROPERTY_Y, yScroll);
        eventArgs.put("contentOffset", point);
        return eventArgs;
    }

    protected KrollDict dictForScrollEvent() {
        return dictForScrollEvent(overallXScroll, overallYScroll);
    }

    protected void processSections(Object[] sections) {
        synchronized (this.sections) {
            this.sections.clear();
            for (int i = 0; i < sections.length; i++) {
                processSection(sections[i], -1);
            }
        }
    }

    @Override
    public void processSectionsAndNotify(Object[] sections) {
        (new ProcessSectionsTask()).execute(sections);
        // processSections(sections);
        // if (adapter != null) {
        // adapter.notifyDataSetChanged();
        // }
    }

    private class ProcessSectionsTask extends AsyncTask<Object[], Void, Void> {

        @Override
        protected Void doInBackground(Object[]... params) {
            processSections(params[0]);
            AbsListViewProxy listProxy = (AbsListViewProxy) proxy;
            listProxy.clearPreloadSections();
            listProxy.setPreload(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            notifyDataSetChanged();
        }

    }

    protected void processSection(Object sec, int index) {
        if (sec instanceof CollectionSectionProxy) {
            CollectionSectionProxy section = (CollectionSectionProxy) sec;
            section.setListView(this);
            section.setActivity(proxy.getActivity());
            section.setAdapter(mAdapter);
            synchronized (sections) {
                if (this.sections.contains(section)) {
                    return;
                }
                if (index == -1 || index >= sections.size()) {
                    section.setIndex(this.sections.size());
                    this.sections.add(section);
                } else {
                    section.setIndex(index);
                    this.sections.add(index, section);
                }
            }

            // Attempts to set type for existing templates.
            // section.setTemplateType();
            // Process preload data if any
            section.processPreloadData();
            // Apply filter if necessary
            if (searchText != null) {
                section.applyFilter(searchText, caseInsensitive);
            }
        } else if (sec instanceof HashMap) {
            CollectionSectionProxy section = (CollectionSectionProxy) KrollProxy
                    .createProxy(((AbsListViewProxy) proxy).sectionClass(),
                            null, new Object[] { sec }, null);
            section.updateKrollObjectProperties();
            processSection(section, index);
        }
    }

    public KrollDict getItem(int sectionIndex, int itemIndex) {
        if (sectionIndex < 0 || sectionIndex >= sections.size()) {
            Log.e(TAG, "getItem Invalid section index");
            return null;
        }
        synchronized (sections) {
            return sections.get(sectionIndex).getItemAt(itemIndex);
        }
    }

    public AbsListSectionProxy getSectionAt(int sectionIndex) {
        synchronized (sections) {
            if (sectionIndex < 0 || sectionIndex >= sections.size()) {
                Log.e(TAG, "getItem Invalid section index");
                return null;
            }

            return sections.get(sectionIndex);
        }
    }


    protected void notifyDataSetChanged() {
        if (mAdapter != null) {
            mAdapter.notifyDataChanged();
        }
    }

    protected static String getCellProxyRootType() {
        return "Ti.UI.CollectionItem";
    }

    @Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCROLLING_ENABLED:
            layoutManager.setScrollEnabled(TiConvert.toBoolean(newValue));
            break;
        case "appearAnimation":
            if (newValue instanceof HashMap
                    || newValue instanceof TiAnimation) {
                mAppearAnimation = newValue;
                mUseAppearAnimation = mAppearAnimation != null;
            } else {
                int id = TiConvert.toInt(newValue);
                if (id != 0) {
                    mAppearAnimators = AnimatorInflater
                            .loadAnimator(getProxy().getActivity(), id);
                } else {
                    mAppearAnimators = null;
                }
                mUseAppearAnimation = mAppearAnimators != null;
            }

            break;
        case "useAppearAnimation":
            mUseAppearAnimation = TiConvert.toBoolean(newValue, false);
            break;
        case TiC.PROPERTY_COLUMN_WIDTH:
            mColumnsWidth = TiConvert.toTiDimension(newValue,
                    TiDimension.TYPE_WIDTH);
            if (layoutManager != null) {
                if (mColumnsWidth != null) {
                    layoutManager.setColumnWidth(
                            mColumnsWidth.getAsPixels(mRecyclerView));
                }
            }

            break;
        case TiC.PROPERTY_NUM_COLUMNS:
            mColumnsWidth = null;
            mNumColumns = TiConvert.toInt(newValue);
            if (layoutManager != null) {
                layoutManager.setNumColumns(mNumColumns);
            }
            break;
        case TiC.PROPERTY_TEMPLATES:
            processTemplates((HashMap) newValue);
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            mProcessUpdateFlags |= TIFLAG_NEEDS_ADAPTER_CHANGE;
            // if (changedProperty) {
            // notifyDataSetChanged();
            // }
            break;
        case TiC.PROPERTY_SEARCH_TEXT:
            filterBy(TiConvert.toString(newValue));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_SEARCH_VIEW:
            setSearchView(newValue, true);
            break;
        case TiC.PROPERTY_SEARCH_VIEW_EXTERNAL:
            setSearchView(newValue, false);
            break;
        case TiC.PROPERTY_SCROLL_HIDES_KEYBOARD:
            this.hideKeyboardOnScroll = TiConvert.toBoolean(newValue, true);
            break;
        case TiC.PROPERTY_STICKY_HEADERS:
            boolean enabled = TiConvert.toBoolean(newValue, true);
            mAdapter.setStickyHeaders(enabled);
            break;
        case TiC.PROPERTY_CASE_INSENSITIVE_SEARCH:
            this.caseInsensitive = TiConvert.toBoolean(newValue, true);
            filterBy(TiConvert.toString(this.searchText));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        // case TiC.PROPERTY_SEPARATOR_COLOR:
        // {
        // AbsListView internalListView = getInternalListView();
        // if (internalListView instanceof ListView) {
        // int dividerHeight = listView.getDividerHeight();
        // ((ListView) internalListView).setDivider(new
        // ColorDrawable(TiConvert.toColor(newValue)));
        // ((ListView) internalListView).setDividerHeight(dividerHeight);
        // }
        // break;
        // }
        case TiC.PROPERTY_FOOTER_DIVIDERS_ENABLED:
            // {
            // AbsListView internalListView = getInternalListView();
            // if (internalListView instanceof ListView) {
            // ((ListView)
            // internalListView).setFooterDividersEnabled(TiConvert.toBoolean(newValue,
            // false));
            // }
            // break;
            // }
            // case TiC.PROPERTY_HEADER_DIVIDERS_ENABLED:
            // {
            // AbsListView internalListView = getInternalListView();
            // if (internalListView instanceof ListView) {
            // ((ListView)
            // internalListView).setHeaderDividersEnabled(TiConvert.toBoolean(newValue,
            // false));
            // }
            // break;
            // }
        case TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR:
            mRecyclerView.setVerticalScrollBarEnabled(
                    TiConvert.toBoolean(newValue, true));
            break;
        case TiC.PROPERTY_DEFAULT_ITEM_TEMPLATE:
            defaultTemplateBinding = TiConvert.toString(newValue);
            if (changedProperty) {
                notifyDataSetChanged();
            }
            break;
        case TiC.PROPERTY_SCROLL_DIRECTION: {
            final String direction = TiConvert.toString(newValue);
            if (direction.equalsIgnoreCase("horizontal")) {
                layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            } else {
                layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            }
            break;
        }
        case TiC.PROPERTY_SECTIONS:
            if (changedProperty) {
                mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
                processSectionsAndNotify((Object[]) newValue);
            } else {
                // if user didn't append/modify/delete sections before this is
                // called, we process sections
                // as usual. Otherwise, we process the preloadSections, which
                // should also contain the section(s)
                // from this dictionary as well as other sections that user
                // append/insert/deleted prior to this.
                AbsListViewProxy listProxy = (AbsListViewProxy) proxy;
                if (!listProxy.getPreload()) {
                    processSections((Object[]) newValue);
                }
            }
            break;
        // case TiC.PROPERTY_SEPARATOR_STYLE:
        // Drawable drawable = listView.getDivider();
        // listView.setDivider(drawable);
        // listView.setDividerHeight(TiConvert.toInt(newValue));
        // mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
        // break;
        case TiC.PROPERTY_OVER_SCROLL_MODE:
            // if (Build.VERSION.SDK_INT >= 9) {
            mRecyclerView.setOverScrollMode(
                    TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
            // }
            break;
        case TiC.PROPERTY_HEADER_VIEW:
            setHeaderOrFooterView(newValue, true);
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        case TiC.PROPERTY_HEADER_TITLE:
            setHeaderTitle(TiConvert.toString(newValue));
            mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
            break;
        // case TiC.PROPERTY_FOOTER_VIEW:
        // setHeaderOrFooterView(newValue, false);
        // mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
        // break;
        // case TiC.PROPERTY_FOOTER_TITLE:
        // // if (footerView == null || footerView.getId() !=
        // // HEADER_FOOTER_WRAP_ID) {
        // // if (footerView == null) {
        // // footerView = inflater.inflate(headerFooterId, null);
        // // }
        // setFooterTitle(TiConvert.toString(newValue));
        // // }
        // mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
        // break;
        // case TiC.PROPERTY_PULL_VIEW:
        // ((RefreshableListView) listView)
        // .setHeaderPullView(setPullView(newValue));
        // mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
        // break;
        case "reverseDrawingOrder":
            boolean reversed = TiConvert.toBoolean(newValue, false);
            if (reversed) {
                mRecyclerView.setChildDrawingOrderCallback(
                        new RecyclerView.ChildDrawingOrderCallback() {
                            @Override
                            public int onGetChildDrawingOrder(int childCount,
                                    int i) {
                                return childCount - i - 1;
                            }
                        });
            } else {
                mRecyclerView.setChildDrawingOrderCallback(null);
            }

            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

    @Override
    protected void aboutToProcessProperties(HashMap d) {

        super.aboutToProcessProperties(d);
        updateToPassProps(d);
        if (mAdapter != null) {
            mAdapter.setCanNotifyDataSetChanged(false);
        }
        if (mRecyclerView.getAdapter() == null) {
            mProcessUpdateFlags |= TIFLAG_NEEDS_ADAPTER_CHANGE;
        }
    }

    @Override
    protected void didProcessProperties() {
        AbsListViewProxy listProxy = (AbsListViewProxy) proxy;

        if (listProxy.getPreload()) {
            processSections(listProxy.getPreloadSections().toArray());
            listProxy.setPreload(false);
            listProxy.clearPreloadSections();
        }
        super.didProcessProperties();
        if (mAdapter != null) {
            mAdapter.setCanNotifyDataSetChanged(true);
        }
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_ADAPTER_CHANGE) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_ADAPTER_CHANGE;
            setListViewAdapter(mAdapter);
        }
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_DATASET) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
            notifyDataSetChanged();
        }
    }

    protected void setListViewAdapter(TiBaseAdapter adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    private ViewProxy getOrCreateHeaderWrapperView() {
        ViewProxy vp = (ViewProxy) this.proxy.getHoldedProxy("headerWrapper");
        if (vp == null) {
            KrollDict props = new KrollDict();
            props.put("width", "FILL");
            props.put("height", "SIZE");
            props.put("layout", "vertical");
            props.put("touchPassThrough", true);
            vp = (ViewProxy) this.proxy.addProxyToHold(props, "headerWrapper");
        }
        TiUIView view = ((ViewProxy) vp).getOrCreateView();
        view.setCustomLayoutParams(
                new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT,
                        ListView.LayoutParams.WRAP_CONTENT));
        if (headerDecoration == null) {
            headerDecoration = new HeaderDecoration(view.getOuterView(),
                    layoutManager);
        }
        mRecyclerView.addItemDecoration(headerDecoration);
        return vp;
    }

    private static KrollDict DEFAULT_HEADER_DICT = null;

    public static KrollDict headerViewDict(final String text) {
        try {
            if (DEFAULT_HEADER_DICT == null) {
                // if (TiC.LOLLIPOP_OR_GREATER) {
                int colorAccent = TiUIHelper
                        .getColorAccent(TiApplication.getAppCurrentActivity());
                String color = TiColorHelper.toHexString(colorAccent);
                DEFAULT_HEADER_DICT = new KrollDict(
                        "{type:'Ti.UI.Label',font:{size:14, weight:'bold'},padding:{top:12, bottom:1},color:'"
                                + color + "',width:'FILL',left:15,right:15}");
                // } else {
                // DEFAULT_HEADER_DICT = new
                // KrollDict("{type:'Ti.UI.Label',font:{size:14,
                // weight:'bold'},padding:{left:8, right:8,top:7,
                // bottom:7},borderPadding:{left:-2.5, right:-2.5,
                // top:-2.5},borderColor:'#666',borderWidth:2.5,color:'#ccc',width:'FILL',left:15,right:15,autocapitalization:true}");
                // }
            }
        } catch (JSONException e) {
        }
        KrollDict result = new KrollDict(DEFAULT_HEADER_DICT);
        result.put(TiC.PROPERTY_TEXT, text);
        return result;
    }

    private void setHeaderOrFooterView(Object viewObj, boolean isHeader) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj,
                isHeader ? "headerView" : "footerView", false, true);
        if (viewProxy instanceof TiViewProxy) {
            if (isHeader) {
                getOrCreateHeaderWrapperView().add(viewProxy, 1);
            } else {
                // getOrCreateFooterWrapperView().add(viewProxy, 1);
            }
        }
    }

    public void setHeaderTitle(String title) {
        if (title != null) {
            ViewProxy vp = (ViewProxy) this.proxy
                    .addProxyToHold(headerViewDict(title), "headerView");
            getOrCreateHeaderWrapperView().add(vp, 1);
        } else {
            this.proxy.removeHoldedProxy("headerView");
        }
    }

    private void setSearchView(Object viewObj, boolean addInHeader) {
        KrollProxy viewProxy = proxy.addProxyToHold(viewObj, "search");
        if (isSearchViewValid(viewProxy)) {
            // TiUIHelper.removeViewFromSuperView((TiViewProxy) viewProxy);

            TiUIView search = ((TiViewProxy) viewProxy).getOrCreateView();
            setSearchListener((TiViewProxy) viewProxy, search);
            if (addInHeader) {
                getOrCreateHeaderWrapperView().add(viewProxy, 0);
            }
        } else {
            Log.e(TAG, "Searchview type is invalid");
        }
    }

    private void reFilter(String searchText) {
        synchronized (sections) {
            for (int i = 0; i < sections.size(); ++i) {
                AbsListSectionProxy section = sections.get(i);
                section.applyFilter(searchText, caseInsensitive);
            }
        }
        notifyDataSetChanged();
    }

    private boolean isSearchViewValid(Object proxy) {
        if (proxy instanceof SearchBarProxy
                || proxy instanceof SearchViewProxy) {
            return true;
        } else {
            return false;
        }
    }

    private void setSearchListener(TiViewProxy searchView, TiUIView search) {
        if (searchView instanceof SearchBarProxy) {
            ((TiUISearchBar) search).setOnSearchChangeListener(this);
        } else if (searchView instanceof SearchViewProxy) {
            ((TiUISearchView) search).setOnSearchChangeListener(this);
        }
    }

    public TiAbsListViewTemplate getTemplate(String template,
            final boolean canReturnDefault) {

        if (template == null)
            template = defaultTemplateBinding;
        if (templatesByBinding.containsKey(template)) {
            return templatesByBinding.get(template);
        }
        if (canReturnDefault) {
            return templatesByBinding.get(UIModule.LIST_ITEM_TEMPLATE_DEFAULT);
        }
        return null;
    }

    protected void processTemplates(HashMap<String, Object> templates) {
        templatesByBinding = new HashMap<String, TiAbsListViewTemplate>();
        templatesByType = new HashMap<Integer, TiAbsListViewTemplate>();
        templatesByBinding.put(defaultTemplateKey, defaultTemplate);
        if (templates != null) {
            for (String key : templates.keySet()) {
                HashMap templateDict = (HashMap) templates.get(key);
                if (templateDict != null) {
                    // Here we bind each template with a key so we can use it to
                    // look up later
                    KrollDict properties = new KrollDict(
                            (HashMap) templates.get(key));
                    TiAbsListViewTemplate template = new TiAbsListViewTemplate(
                            key, properties);
                    template.setType(getItemType());
                    templatesByBinding.put(key, template);
                    templatesByType.put(template.getType(), template);
                } else {
                    Log.e(TAG, "null template definition: " + key);
                }
            }
        }
    }

    public int getItemType() {
        return itemTypeCount.getAndIncrement();
    }

    public TiAbsListViewTemplate getTemplateByBinding(String binding) {
        return templatesByBinding.get(binding);
    }

    public String getDefaultTemplateBinding() {
        return defaultTemplateBinding;
    }

    public int getSectionCount() {
        synchronized (sections) {
            return sections.size();
        }
    }

    public void appendSection(Object section) {
        if (section instanceof Object[]) {
            Object[] secs = (Object[]) section;
            for (int i = 0; i < secs.length; i++) {
                processSection(secs[i], -1);
            }
        } else {
            processSection(section, -1);
        }
        notifyDataSetChanged();
    }

    public void deleteSectionAt(int index) {
        synchronized (sections) {
            if (index >= 0 && index < sections.size()) {
                sections.remove(index);
                notifyDataSetChanged();
            } else {
                Log.e(TAG, "Invalid index to delete section");
            }
        }
    }

    public void insertSectionAt(int index, Object section) {
        synchronized (sections) {
            if (index > sections.size()) {
                Log.e(TAG, "Invalid index to insert/replace section");
                return;
            }
        }
        if (section instanceof Object[]) {
            Object[] secs = (Object[]) section;
            for (int i = 0; i < secs.length; i++) {
                processSection(secs[i], index);
                index++;
            }
        } else {
            processSection(section, index);
        }
        notifyDataSetChanged();
    }

    public void replaceSectionAt(int index, Object section) {
        deleteSectionAt(index);
        insertSectionAt(index, section);
    }

    public int findItemPosition(int sectionIndex, int sectionItemIndex,
            boolean canOverFlow) {
        int position = 0;
        synchronized (sections) {
            for (int i = 0; i < sections.size(); i++) {
                AbsListSectionProxy section = sections.get(i);
                if (i == sectionIndex) {
                    int sectionLength = section.getContentCount();
                    if (sectionItemIndex >= sectionLength) {
                        if (canOverFlow) {
                            // return the last one
                            sectionItemIndex = sectionLength;
                        } else {
                            Log.e(TAG, "Invalid item index");
                            return -1;
                        }

                    }
                    position += sectionItemIndex;
                    if (section.hasHeader()) {
                        position += 1;
                    }
                    break;
                } else {
                    position += section.getItemCount();
                }
            }
        }
        return position;
    }

    @Override
    public int findItemPosition(int sectionIndex, int sectionItemIndex) {
        return findItemPosition(sectionIndex, sectionItemIndex, false);
    }

    public int getHeaderViewCount() {
        int count = 0;
        synchronized (sections) {
            for (int i = 0; i < sections.size(); i++) {
                AbsListSectionProxy section = sections.get(i);
                if (section.hasHeader()) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private int getCount() {
        if (mAdapter != null) {
            return mAdapter.getItemCount();
        }
        return 0;
    }

    public void ensureVisible(int pos) {
        if (mRecyclerView == null) {
            return;
        }

        if (pos < 0 || pos >= getCount()) {
            return;
        }

        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();

        if (pos < first) {
            mRecyclerView.scrollToPosition(pos);
            return;
        }

        if (pos >= last) {
            mRecyclerView.scrollToPosition(1 + pos - (last - first));
            return;
        }
    }

    @Override
    public void scrollToItem(int sectionIndex, int sectionItemIndex,
            boolean animated) {
        final int position = findItemPosition(sectionIndex, sectionItemIndex);
        if (position > -1) {
            if (animated)
                mRecyclerView.smoothScrollToPosition(position);
            else
                ensureVisible(position);
        }
    }

    public void scrollToTop(final int y, boolean animated) {
        if (animated) {
            mRecyclerView.smoothScrollToPosition(0);
        } else {
            mRecyclerView.scrollToPosition(0);
        }
    }

    public void scrollToBottom(final int y, boolean animated) {
        // strangely if i put getCount()-1 it doesnt go to the full bottom but
        // make sure the -1 is shown …
        if (animated) {
            mRecyclerView.smoothScrollToPosition(getCount() - 1);
        } else {
            mRecyclerView.scrollToPosition(getCount() - 1);
        }
    }

    @Override
    public void release() {
        synchronized (sections) {
            for (int i = 0; i < sections.size(); i++) {
                sections.get(i).release();
            }

            sections.clear();
        }
        templatesByBinding.clear();

        if (handledProxies != null) {
            for (TiViewProxy viewProxy : handledProxies) {
                viewProxy.releaseViews(true);
                viewProxy.setParent(null);
            }
            handledProxies = null;
        }

        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            if (headerDecoration != null) {
                mRecyclerView.removeItemDecoration(headerDecoration);
            }
            mRecyclerView = null;
        }
        // footerView = null;

        super.release();
    }

    @Override
    public void filterBy(String text) {
        this.searchText = text;
        reFilter(text);
    }

    public AbsListSectionProxy[] getSections() {
        synchronized (sections) {
            return sections.toArray(new AbsListSectionProxy[sections.size()]);
        }
    }

    public KrollProxy getChildByBindId(int sectionIndex, int itemIndex,
            String bindId) {

        View content = getCellAt(sectionIndex, itemIndex);
        if (content != null) {
            TiBaseAbsListViewItem listItem = (TiBaseAbsListViewItem) content
                    .findViewById(TiAbsListView.listContentId);
            if (listItem != null) {
                if (listItem.getItemIndex() == itemIndex) {
                    return listItem.getViewProxyFromBinding(bindId);
                }
            }
        }
        return null;
    }

    public View getCellAt(int sectionIndex, int itemIndex) {
        int position = findItemPosition(sectionIndex, itemIndex);
        int childCount = layoutManager.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = layoutManager.getChildAt(i);
            TiBaseAbsListViewItem itemContent = (TiBaseAbsListViewItem) child
                    .findViewById(listContentId);
            if (itemContent != null) {
                // first visible item of ours
                int firstposition = findItemPosition(
                        itemContent.getSectionIndex(),
                        itemContent.getItemIndex());
                position -= firstposition;
                break;
            } else {
                position++;
            }
        }
        if (position > -1) {
            View content = layoutManager.getChildAt(position);
            return content;

        }
        return null;
    }

    public void insert(final int position, final Object item) {
        // listView.insert(position, item);
    }

    public void insert(final int position, final Object... items) {
        // listView.insert(position, items);
    }

    public void remove(final int position) {
        // listView.remove(position - listView.getHeaderViewsCount());
    }

    public void remove(final int position, final int count) {
        // listView.remove(position - listView.getHeaderViewsCount(), count);
    }

    private static HashMap<String, String> TO_PASS_PROPS;
    private HashMap<String, Object> toPassProps;

    private void updateToPassProps(HashMap<String, Object> props) {
        if (props == null || props.size() == 0) {
            return;
        }
        if (TO_PASS_PROPS == null) {
            TO_PASS_PROPS = new HashMap<String, String>();
            TO_PASS_PROPS.put(TiC.PROPERTY_ACCESSORY_TYPE,
                    TiC.PROPERTY_ACCESSORY_TYPE);
            TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR,
                    TiC.PROPERTY_BACKGROUND_SELECTED_COLOR);
            TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE,
                    TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE);
            TO_PASS_PROPS.put(TiC.PROPERTY_SELECTED_BACKGROUND_GRADIENT,
                    TiC.PROPERTY_BACKGROUND_SELECTED_GRADIENT);
            TO_PASS_PROPS.put(TiC.PROPERTY_ROW_HEIGHT, TiC.PROPERTY_HEIGHT);
            TO_PASS_PROPS.put(TiC.PROPERTY_COLUMN_WIDTH, TiC.PROPERTY_WIDTH);
            TO_PASS_PROPS.put(TiC.PROPERTY_MIN_ROW_HEIGHT,
                    TiC.PROPERTY_MIN_HEIGHT);
            TO_PASS_PROPS.put(TiC.PROPERTY_MAX_ROW_HEIGHT,
                    TiC.PROPERTY_MAX_HEIGHT);
        }
        if (toPassProps == null) {
            toPassProps = new HashMap<>();
        }
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String inProp = entry.getKey();
            Object outProp = entry.getValue();
            if (TO_PASS_PROPS.containsKey(inProp)) {
                toPassProps.put(TO_PASS_PROPS.get(inProp), outProp);
            }
        }
    }

    public HashMap<String, Object> getToPassProps() {
        return toPassProps;
    }

    @Override
    public String getSearchText() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.v(TAG, "onSaveInstanceState start!");

        mAdapter.onSaveInstanceState(outState);

        // if (mActivatedPosition != AdapterView.INVALID_POSITION) {
        // //Serialize and persist the activated item position.
        // outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        // Log.d(TAG, STATE_ACTIVATED_POSITION + "=" + mActivatedPosition);
        // }
        // super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mAdapter.onRestoreInstanceState(savedInstanceState);
        // Previously serialized activated item position
        // if (savedInstanceState.containsKey(STATE_ACTIVATED_POSITION))
        // setSelection(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
    }

    @Override
    public void onItemSwipe(int position, int direction) {
        // AbstractFlexibleItem abstractItem = mAdapter.getItem(position);
        // assert abstractItem != null;
        // //Experimenting NEW feature
        // if (abstractItem.isSelectable())
        // mAdapter.setRestoreSelectionOnUndo(false);
        //
        // //TODO: Create Undo Helper with SnackBar?
        // StringBuilder message = new StringBuilder();
        // message.append(extractTitleFrom(abstractItem))
        // .append(" ").append(getString(R.string.action_deleted));
        // //noinspection ResourceType
        // mSnackBar = Snackbar.make(findViewById(R.id.main_view), message,
        // 7000)
        // .setAction(R.string.undo, new View.OnClickListener() {
        // @Override
        // public void onClick(View v) {
        // mAdapter.restoreDeletedItems();
        // }
        // });
        // mSnackBar.show();
        // mAdapter.removeItem(position, true);
        // logOrphanHeaders();
        // mAdapter.startUndoTimer(5000L + 200L, this);
        // //Handle ActionMode title
        // if (mAdapter.getSelectedItemCount() == 0)
        // destroyActionModeIfCan();
        // else
        // setContextTitle(mAdapter.getSelectedItemCount());

    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onItemLongClick(int position) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onItemClick(int position) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onStickyHeaderChange(int position) {

        if (proxy.hasListeners(TiC.EVENT_HEADER_CHANGE, false)) {
            Pair<AbsListSectionProxy, Pair<Integer, Integer>> info = getSectionInfoByEntryIndex(
                    position);
            if (info == null) {
                return;
            }
            AbsListSectionProxy section = info.first;
            final int sectionIndex = section.getIndex();
            KrollDict data = new KrollDict();
            data.put(TiC.PROPERTY_HEADER_VIEW,
                    section.getProperty(TiC.PROPERTY_HEADER_VIEW));
            data.put(TiC.PROPERTY_SECTION, section);
            data.put(TiC.PROPERTY_SECTION_INDEX, sectionIndex);
            proxy.fireEvent(TiC.EVENT_HEADER_CHANGE, data, false, false);
        }
    }

}

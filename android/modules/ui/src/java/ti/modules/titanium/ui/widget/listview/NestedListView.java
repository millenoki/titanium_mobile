package ti.modules.titanium.ui.widget.listview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.widget.ListView;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * ListView which allows "NestedScrollingChild" views to be scrolled within this
 * view on a system older than API Level 21 (aka: Android 5.0).
 * <p>
 * Note that Android's "ListView" class already supports NestedScrollingParent
 * and NestedScrollingChild support on Android 5.0 and higher OS versions. This
 * class is only needed for older OS versions.
 * <p>
 * You can only create instances of this class via its static createUsing()
 * methods.
 */
@SuppressLint("NewApi")
public abstract class NestedListView extends ListView
        implements NestedScrollingParent {
    /**
     * Set true to allow scrolls via touch events. Set false to disable touch
     * scrolls but allow programmatic scrolling.
     */
    private boolean isTouchScrollable = true;

    static boolean Post19 = Build.VERSION.SDK_INT >= 19;
    static boolean Post21 = Build.VERSION.SDK_INT >= 21;

    /**
     * Helper object used to handle nested scrolling within a cooperating
     * NestedScrollingChild view. Only for < 21
     */
    private NestedScrollingParentHelper nestedParentHelper;

    /**
     * Initializes this view's settings and member variables. This method is
     * only expected to be called once from this class' constructor.
     */
    private void initializeView() {
        ViewCompat.setNestedScrollingEnabled(this, true);
        if (!Post21) {
            this.nestedParentHelper = new NestedScrollingParentHelper(this);
        }
    }

    /** Creates a new list view. */
    protected NestedListView(Context context) {
        super(context);
        initializeView();
    }

    /** Creates a new list view. */
    protected NestedListView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        initializeView();
    }

    /** Creates a new list view. */
    protected NestedListView(Context context, AttributeSet attributeSet,
            int defaultStyleAttributes) {
        super(context, attributeSet, defaultStyleAttributes);
        initializeView();
    }

    /**
     * Gets the vertical offset of the vertical scrollbar's thumb within range.
     * This value is retrieved from the protected computeVerticalScrollOffset()
     * ListView method.
     * 
     * @return Returns the vertical offset of the scrollbar's thumb.
     */
    public int getVerticalScrollOffset() {
        return computeVerticalScrollOffset();
    }

    /**
     * Determines if the end-user can scroll the list view via touches or not.
     * <p>
     * Note that the list view can still be scrolled programmatically if this
     * setting is false.
     * 
     * @return Returns true if list view can be scrolled with touches. Returns
     *         false if not.
     */
    public boolean isTouchScrollable() {
        return this.isTouchScrollable;
    }

    /**
     * Sets whether or not the end-user can scroll the list view with touches.
     * <p>
     * Note that the list view can still be scrolled programmatically if this
     * setting is false.
     * 
     * @param value
     *            Set true (the default) to allow the user to scroll with
     *            touches. Set false to disable.
     */
    public void setTouchScrollable(boolean value) {
        this.isTouchScrollable = value;
    }

    /**
     * Called when a touch event has been received by this view.
     * 
     * @param event
     *            The touch information received.
     * @return Returns true if this view handled the touch. Returns false if
     *         not.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If touch scrolling is disabled, then block touch move/drag events.
        if (!this.isTouchScrollable
                && (event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
            return false;
        }

        // Let the base class handle the touch event.
        return super.onTouchEvent(event);
    }

    /**
     * Called when a touch event has been received by a child view or this view,
     * but only while requestDisallowInterceptTouchEvent() is set to false.
     * <p>
     * Provides this view the opportunity to monitor or steal child touch
     * events. If this view returns true, then the event will not be received by
     * the child.
     * 
     * @param event
     *            The touch information received.
     * @return Returns true if this view will claim the event. Returns false if
     *         not.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (Post21) {
            // If touch scrolling is disabled, then block touch move/drag
            // events.
            if (!this.isTouchScrollable
                    && (event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
                return false;
            }
            return super.onInterceptTouchEvent(event);
        }
        // Validate argument.
        if (event == null) {
            return false;
        }

        // Do not intercept touch move/drag events while a NestedScrollingChild
        // view is scrolling.
        // Otherwise nested scrolling won't work and this view would scroll
        // instead.
        boolean isChildScrolling = (getNestedScrollAxes() != ViewCompat.SCROLL_AXIS_NONE);
        if (isChildScrolling
                && (event.getActionMasked() == MotionEvent.ACTION_MOVE)) {
            return false;
        }

        // Let this view handle the touch intercept.
        return super.onInterceptTouchEvent(event);
    }

    /**
     * Gets a bit mask of axes a NestedScrollingChild has asked this parent view
     * to scroll by via the onNestedScrollAccepted() method.
     * 
     * @return Returns a bit mask set to flags ViewCompat.SCROLL_AXIS_HORIZONTAL
     *         and/or ViewCompat.SCROLL_AXIS_VERTICAL.
     *         <p>
     *         Returns ViewCompat.SCROLL_AXIS_NONE (which is zero) if no axes
     *         flags are set.
     */
    @Override
    public int getNestedScrollAxes() {
        return this.nestedParentHelper.getNestedScrollAxes();
    }

    /**
     * Called when a NestedScrollingChild is asking this parent to fling its
     * scrollable view.
     * 
     * @param target
     *            The nested scrollable child that is making this request.
     * @param velocityX
     *            The x-axis fling velocity in pixels per second.
     * @param velocityY
     *            The y-axis fling velocity in pixels per second.
     * @param consumed
     *            Set true if the child consumed the fling, meaning that the
     *            parent view should not perform a fling. Set false if the child
     *            wants the parent view to perform the fling.
     * @return Returns true if this parent view has performed the fling. Returns
     *         false if not.
     */
    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
            boolean consumed) {
        return false;
    }

    /**
     * Called by a NestedScrollingChild before the onNestedFling() method gets
     * called, providing the parent an opportunity to steal the fling event from
     * the nested child.
     * 
     * @param target
     *            The nested scrollable child that is making this request.
     * @param velocityX
     *            The x-axis fling velocity in pixels per second.
     * @param velocityY
     *            The y-axis fling velocity in pixels per second.
     * @return Returns true if the parent view is requesting to perform the
     *         fling instead of the child. Note that the nested child can ignore
     *         this request and perform the fling anyways. The parent view
     *         should only perform the fling via the onNestedFling() method.
     *         <p>
     *         Returns false if the parent view does not want to steal the fling
     *         from the child.
     */
    @Override
    public boolean onNestedPreFling(View target, float velocityX,
            float velocityY) {
        return false;
    }

    /**
     * Called by a NestedScrollingChild before the onNestedScroll() method gets
     * called, providing the parent an opportunity to steal the scroll event
     * from the nested child.
     * 
     * @param target
     *            The nested scrollable child that is making this request.
     * @param dx
     *            The distance to scroll along the x-axis in pixels.
     * @param dy
     *            The distance to scroll along the y-axis in pixels.
     * @param consumed
     *            Optional array containing 2 elements. If this method wants to
     *            steal the scroll event, then it would set the 1st element to
     *            the x-axis scroll distance it wants to consume from argument
     *            "dx" and the 2nd element to the y-axis distance it wants to
     *            consume from "dy".
     */
    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
    }

    /**
     * Called by a NestedScrollingChild when requesting the parent view to
     * scroll.
     * <p>
     * This can happen with a NestedScrollView or a scrollable TiUIEditText
     * where scrolling past the top/bottom of the child view should cause the
     * ListView to scroll.
     * 
     * @param target
     *            The nested scrollable child that is making this request.
     * @param dxConsumed
     *            X-axis scroll distance traveled by the child view in pixels.
     * @param dxConsumed
     *            Y-axis scroll distance traveled by the child view in pixels.
     * @param dxUnconsumed
     *            The x-axis distance in pixels the child is requesting this
     *            parent view to scroll by.
     * @param dyUnconsumed
     *            The y-axis distance in pixels the child is requesting this
     *            parent view to scroll by.
     */
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed) {
        // Block nested scrolls if touch scrolling is disabled.
        if (isTouchScrollable() == false) {
            return;
        }
        if (Post21) {
            super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed,
                    dyUnconsumed);
        } else {
            if (Post19) {
                this.scrollListBy(dyConsumed);
            }
        }

    }

    /**
     * Called by a NestedScrollingChild after a call to onStartNestedScroll()
     * has returned true. This indicates that the child will be dispatching
     * scroll/fling events to this parent view.
     * 
     * @param child
     *            Reference to either the NestedScrollingChild or the ViewGroup
     *            owned by this parent view that contains the
     *            NestedScrollingChild.
     * @param target
     *            The nested scrollable child that is making this request.
     * @param axes
     *            Bit mask set to flags ViewCompat.SCROLL_AXIS_HORIZONTAL and/or
     *            ViewCompat.SCROLL_AXIS_VERTICAL that the child wants to scroll
     *            the parent by.
     */
    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        if (this.nestedParentHelper != null) {
            this.nestedParentHelper.onNestedScrollAccepted(child, target, axes);
        } else {
            super.onNestedScrollAccepted(child, target, axes);
        }
    }

    /**
     * Called by a NestedScrollingChild when its startNestedScroll() method has
     * been called. This asks this view if it supports nested scrolling with
     * along the given axes.
     * 
     * @param child
     *            Reference to either the NestedScrollingChild or the ViewGroup
     *            owned by this parent view that contains the
     *            NestedScrollingChild.
     * @param target
     *            The nested scrollable child that has been started.
     * @param axes
     *            Bit mask set to flags ViewCompat.SCROLL_AXIS_HORIZONTAL and/or
     *            ViewCompat.SCROLL_AXIS_VERTICAL that the child wants to scroll
     *            the parent by.
     * @return Returns true if this view is will to scroll in the direction(s)
     *         indicated by argument "axes". Returns false if unwilling to do
     *         the requested nested scrolling.
     */
    @Override
    public boolean onStartNestedScroll(View child, View target, int axes) {
        if (Post21) {
            return super.onStartNestedScroll(child, target, axes);
        }
        // Only return true if the NestedScrollingChild view wants to scroll
        // this view vertically.
        // Note: This is because the Android ListView class only supports
        // vertical scrolling.
        return ((axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0);
    }

    /**
     * Called by a NestedScrollingChild when its stopNestedScroll() method has
     * been called.
     * 
     * @param target
     *            The nested scrollable child that has been stopped.
     */
    @Override
    public void onStopNestedScroll(View target) {
        if (this.nestedParentHelper != null) {
            this.nestedParentHelper.onStopNestedScroll(target);
        } else {
            super.onStopNestedScroll(target);
        }
    }
}

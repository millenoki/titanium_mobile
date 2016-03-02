package ti.modules.titanium.ui.widget.collectionview;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;

public class SwipeMenuTouchListener implements OnItemTouchListener {
    
    public interface SwipeMenuCallback {
        /**
         * Called when the user start swiping to show a menu.
         *
         * @param view the parent {@code View}, which contains the buttons {@link android.view.View}s.
         * @param position the position for which the undo {@code View} is shown.
         * @param direction the direction of the shown menu (0=left, 1=right).
         */
        void onStartSwipe(@NonNull SwipeMenuViewHolder holder, int direction);


        /**
         * Called when the menu {@link android.view.View} is shown.
         *
         * @param view the parent {@code View}, which contains the buttons {@link android.view.View}s.
         * @param position the position for which the undo {@code View} is shown.
         * @param direction the direction of the shown menu (0=left, 1=right).
         */
        void onMenuShown(@NonNull SwipeMenuViewHolder holder, int direction);

        /**
         * Called when the menu {@link android.view.View} is closed.
         *
         * @param view the parent {@code View}, which contains the buttons {@link android.view.View}s.
         * @param position the position for which the undo {@code View} is shown.
         * @param direction the direction of the shown menu (0=left, 1=right).
         */
        void onMenuClosed(@NonNull SwipeMenuViewHolder holder, int direction);


        /**
         * Called before the menu {@link android.view.View} is shown.
         *
         * @param view the parent {@code View}, which contains the buttons {@link android.view.View}s.
         * @param position the position for which the undo {@code View} is shown.
         * @param direction the direction of the shown menu (0=left, 1=right).
         */
        void beforeMenuShow(@NonNull SwipeMenuViewHolder holder, int direction);


        /**
         * Called before the menu {@link android.view.View} is closed.
         *
         * @param view the parent {@code View}, which contains the buttons {@link android.view.View}s.
         * @param position the position for which the undo {@code View} is shown.
         * @param direction the direction of the shown menu (0=left, 1=right).
         */
        void beforeMenuClose(@NonNull SwipeMenuViewHolder holder, int direction);
    }

    private RecyclerView mRecyclerView;

    /**
     * The minimum distance in pixels that should be moved before starting
     * horizontal item movement.
     */
    private int mSlop;

    /**
     * The minimum velocity to initiate a fling, as measured in pixels per
     * second.
     */
    private int mMinFlingVelocity;

    /**
     * The maximum velocity to initiate a fling, as measured in pixels per
     * second.
     */
    private int mMaxFlingVelocity;

    /**
     * The duration of the fling animation.
     */
    private long mAnimationTime;

    /**
     * The raw X coordinate of the down event.
     */
    private float mDownX;

    /**
     * The raw Y coordinate of the down event.
     */
    private float mDownY;

    /**
     * Indicates whether the user is swiping an item.
     */
    private boolean mSwiping;

    /**
     * Indicates whether the user can show menu for the current item.
     */
    private boolean mCanShowMenuCurrent;

    /**
     * The {@code VelocityTracker} used in the swipe movement.
     */
    @Nullable
    private VelocityTracker mVelocityTracker;

    /**
     * The parent {@link android.view.View} being swiped.
     */
    @Nullable
    private SwipeMenuViewHolder mCurrentHolder;
    private int mCurrentDirection = SwipeMenuLayout.DIRECTION_NONE;
    /**
     * The current position being swiped.
     */
    private int mCurrentPosition = AdapterView.INVALID_POSITION;

    /**
     * The parent {@link android.view.View} being swiped.
     */
    @Nullable
    private SwipeMenuViewHolder mOpenedHolder = null;
    /**
     * The parent {@link android.view.View} being swiped.
     */
    @Nullable
    private int mOpenedPosition = AdapterView.INVALID_POSITION;

    @Nullable
    private SwipeMenuViewHolder mPreviousHolder = null;

    //
    // /**
    // * The number of items in the {@code AbsListView}, minus the pending
    // dismissed items.
    // */
    // private int mVirtualListCount = -1;

    /**
     * Indicates whether the {@link android.widget.AbsListView} is in a
     * horizontal scroll container. If so, this class will prevent the
     * horizontal scroller from receiving any touch events.
     */
    private boolean mParentIsHorizontalScrollContainer;

    /**
     * The resource id of the {@link android.view.View} that may steal touch
     * events from their parents. Useful for example when the
     * {@link android.widget.AbsListView} is in a horizontal scroll container,
     * but not the whole {@code AbsListView} should steal the touch events.
     */
    private int mTouchChildResId;

    /**
     * Indicates whether swipe is enabled.
     */
    private boolean mMenuEnabled = true;

    /**
     * The callback which gets notified of events.
     */
    @NonNull
    private final SwipeMenuCallback mCallback;

    /**
     * Constructs a new {@code SwipeTouchListener} for the given
     * {@link android.widget.AbsListView}.
     */
    protected SwipeMenuTouchListener(@NonNull final SwipeMenuCallback callback) {
        mCallback = callback;
    }

    // /**
    // * Sets the {@link
    // com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.DismissableManager}
    // to specify which views can or cannot be swiped.
    // *
    // * @param dismissableManager {@code null} for no restrictions.
    // */
    // public void setDismissableManager(@Nullable final DismissableManager
    // dismissableManager) {
    // mDismissableManager = dismissableManager;
    // }

    /**
     * If the {@link android.widget.AbsListView} is hosted inside a
     * parent(/grand-parent/etc) that can scroll horizontally, horizontal swipes
     * won't work, because the parent will prevent touch-events from reaching
     * the {@code AbsListView}.
     * <p/>
     * Call this method to fix this behavior. Note that this will prevent the
     * parent from scrolling horizontally when the user touches anywhere in a
     * list item.
     */
    public void setParentIsHorizontalScrollContainer() {
        mParentIsHorizontalScrollContainer = true;
        mTouchChildResId = 0;
    }

    /**
     * Sets the resource id of a child view that should be touched to engage
     * swipe. When the user touches a region outside of that view, no swiping
     * will occur.
     *
     * @param childResId
     *            The resource id of the list items' child that the user should
     *            touch to be able to swipe the list items.
     */
    public void setTouchChild(final int childResId) {
        mTouchChildResId = childResId;
        mParentIsHorizontalScrollContainer = false;
    }

    /**
     * Returns whether a menu is showing.
     *
     * @return {@code true} if a menu is showing.
     */
    public boolean isMenuShown() {
        return mOpenedHolder != null;
    }
    
    /**
     * Enables the menu behavior.
     */
    public void enableMenus() {
        mMenuEnabled = true;
    }

    /**
     * Disables the menus behavior.
     */
    public void disableMenus() {
        mMenuEnabled = false;
    }

    public boolean isInteracting() {
        return mSwiping;
    }

    public int getCurrentMenuPosition() {
        return mOpenedPosition;
    }

    public int getCurrentMenuDirection() {
        if (mCurrentHolder != null) {
            return mCurrentHolder.swipeMenu.getCurrentDirection();
        }
        return SwipeMenuLayout.DIRECTION_NONE;
    }

    private boolean handleDownEvent(@NonNull final MotionEvent motionEvent) {
        if (!mMenuEnabled) {
            return false;
        }
        View child = mRecyclerView.findChildViewUnder(motionEvent.getX(),motionEvent.getY());
        if (child == null) {
            return false;
        }
        RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(child);
        if (!(holder instanceof SwipeMenuViewHolder)) {
            return false;
        }
        SwipeMenuViewHolder swipeHolder = (SwipeMenuViewHolder) holder;
        int downPosition = holder.getAdapterPosition();
        if (mOpenedPosition != RecyclerView.NO_POSITION) {
            boolean shouldStop = mOpenedPosition != downPosition;
            if (!shouldStop) {
                Rect rect = new Rect();
                final float screenX = motionEvent.getX();
                final float screenY = motionEvent.getY();
                final int viewX = (int) (screenX - child.getLeft());
                final int viewY = (int) (screenY - child.getTop());
                swipeHolder.swipeMenu.getContentView().getHitRect(rect);
                shouldStop = rect.contains(viewX, viewY);
            }
            if (shouldStop) {
                closeMenusAnimated();
                // put swiping to true so that interacting get
                mSwiping = true;
                return true;
            }

        }

        mCanShowMenuCurrent = canShowMenu(downPosition, swipeHolder);

        if (!mCanShowMenuCurrent) {
            return false;
        }
        /* Check if we are processing the item at this position */
        if (mCurrentPosition == downPosition) {
            return false;
        }
        
        if (child != null) {
            child.dispatchTouchEvent(motionEvent);
        }

        disableHorizontalScrollContainerIfNecessary(motionEvent, child);

        mDownX = motionEvent.getX();
        mDownY = motionEvent.getY();

        mCurrentHolder = swipeHolder;
        mCurrentPosition = downPosition;

        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(motionEvent);
        return false;
    }

    /**
     * Finds out whether the item represented by given position can show a menu.
     *
     * @param position
     *            the position of the item.
     *
     * @return {@code true} if the item can show a menu, false otherwise.
     */
    private boolean canShowMenu(final int position,
            @NonNull final SwipeMenuViewHolder holder) {
        return holder != null
                && (holder.canShowLeftMenu()
                        || holder.canShowRightMenu());
    }

    private void disableHorizontalScrollContainerIfNecessary(
            @NonNull final MotionEvent motionEvent, @NonNull final View view) {
        if (mParentIsHorizontalScrollContainer) {
            mRecyclerView.requestDisallowInterceptTouchEvent(true);
        } else if (mTouchChildResId != 0) {
            mParentIsHorizontalScrollContainer = false;

            final View childView = view.findViewById(mTouchChildResId);
            if (childView != null) {
                final Rect childRect = getChildViewRect(
                        mRecyclerView, childView);
                if (childRect.contains((int) motionEvent.getX(),
                        (int) motionEvent.getY())) {
                    mRecyclerView.requestDisallowInterceptTouchEvent(true);
                }
            }
        }
    }

    private boolean fetchMenuButtonsIfNecessary(final int direction) {
        switch (direction) {
        case SwipeMenuLayout.DIRECTION_LEFT:
            if (mCurrentHolder.canShowLeftMenu()) {
                if (mCurrentHolder.swipeMenu.getLeftMenu() == null) {
                    mCurrentHolder.swipeMenu.setLeftButtons(mCurrentHolder
                            .getLeftButtons());
                }
                return mCurrentHolder.swipeMenu.getLeftMenu() != null;
            }
            break;
        case SwipeMenuLayout.DIRECTION_RIGHT:
            if (mCurrentHolder.canShowRightMenu()) {
                if (mCurrentHolder.swipeMenu.getRightMenu() == null) {
                    mCurrentHolder.swipeMenu.setRightButtons(mCurrentHolder
                            .getRightButtons());
                }
                return mCurrentHolder.swipeMenu.getRightMenu() != null;
            }
            break;
        }
        return false;
    }

    private boolean handleMoveEvent(@NonNull final MotionEvent motionEvent) {
        if (!mCanShowMenuCurrent || mVelocityTracker == null
                || mCurrentHolder == null) {
            return false;
        }

        mVelocityTracker.addMovement(motionEvent);

        float deltaX = motionEvent.getX() - mDownX;
        float deltaY = motionEvent.getY() - mDownY;
        int direction = (deltaX > 0) ? SwipeMenuLayout.DIRECTION_LEFT
                : SwipeMenuLayout.DIRECTION_RIGHT;

        // not in swipe yet
        if (!mSwiping) {
            if (Math.abs(deltaX) > mSlop
                    && Math.abs(deltaX) > Math.abs(deltaY)) {

                // if the menu is already opened, fake a deltaX so that we
                // translate the views
                // with the correct values. Needs to be done only here so that
                // we don't mess up
                // the test to go into swiping mode
                if (mOpenedHolder != null) {
                    mDownX -= mOpenedHolder.swipeMenu.getCurrentDeltaX();
                    deltaX = motionEvent.getX() - mDownX;
                }

                // prepare for reuse in case the buttons are used in multiple
                // items
                if (mPreviousHolder != null && mPreviousHolder != mCurrentHolder) {
                    mPreviousHolder.swipeMenu.prepareForReuse();
                    mPreviousHolder = null;
                }

                mSwiping = true;
                mIgnoreDisallow = true;
                mRecyclerView.requestDisallowInterceptTouchEvent(true);
                mIgnoreDisallow = false;
                onStartSwipe(mCurrentHolder, direction);

                /* Cancel ListView's touch (un-highlighting the item) */
                MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL | motionEvent
                        .getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                // dispatch the cancel touch event in case the highlighting is
                // done
                // in a deeper view
                if (mCurrentHolder != null) {
                    mCurrentHolder.swipeMenu.dispatchTouchEvent(cancelEvent);

                }
//                if (view != null) {
//                    view.onTouchEvent(cancelEvent);
//                }
                cancelEvent.recycle();
            } else if (Math.abs(deltaY) > mSlop
                    && Math.abs(deltaY) > Math.abs(deltaX)) {
                // the user is scrolling the listview, prevent menu until up
                reset();
            }
        }

        if (mSwiping) {
            if (mCurrentDirection != direction) {
                mCurrentDirection = direction;
                fetchMenuButtonsIfNecessary(mCurrentDirection);
            }
            if (!mCurrentHolder.swipeMenu.setSwipeOffset(deltaX)) {
                reset();
                handleMenuClosed();
            }
            return true;
        }
        return false;
    }

    private boolean handleCancelEvent() {
        if (mVelocityTracker == null || mCurrentHolder == null) {
            return false;
        }

        if (mCurrentPosition != AdapterView.INVALID_POSITION && mSwiping) {
            closeMenusAnimated();
        }

        reset();
        return false;
    }

    private long getAnimationTime() {
        long animationTime = mAnimationTime;
        if (mVelocityTracker != null) {
            float velocityX = Math.abs(mVelocityTracker.getXVelocity());
            if (velocityX != 0.0f) {
                animationTime /= Math.ceil(velocityX / 2000.0f);
            }
        }
        return animationTime;
    }
    
    private SwipeMenuLayout getSwipeMenu() {
        return mCurrentHolder.swipeMenu;
    }

    private boolean handleUpEvent(@NonNull final MotionEvent motionEvent) {
        if (mVelocityTracker == null || mCurrentHolder == null) {
            if (mSwiping) {
                // it could be true in the case where we trapped the down event
                // to close the menu
                reset();
            }
            return false;
        }

        if (mSwiping) {
            float deltaX = motionEvent.getX() - mDownX;

            mVelocityTracker.addMovement(motionEvent);
            mVelocityTracker.computeCurrentVelocity(1000);

            int shouldOpenMenu = -1;

            if (mCanShowMenuCurrent) {
                float velocityX = mVelocityTracker.getXVelocity();
                float velocityY = mVelocityTracker.getYVelocity();
                shouldOpenMenu = mCurrentHolder.swipeMenu.shouldOpenMenuOnUp(deltaX,
                        velocityX, velocityY, mMinFlingVelocity,
                        mMaxFlingVelocity);
            }

            if (shouldOpenMenu >= 0) {
                beforeOpenMenu(mCurrentHolder, shouldOpenMenu);
                getSwipeMenu().openMenu(shouldOpenMenu, getAnimationTime(),
                        new FlingAnimatorListener(mCurrentHolder));
            } else {
                closeMenusAnimated();
            }
            mRecyclerView.requestDisallowInterceptTouchEvent(false);
            mCurrentDirection = SwipeMenuLayout.DIRECTION_NONE;
        }

        reset();
        return false;
    }

    /**
     * Close the current menu with animation.
     * 
     * @param animationTime
     *            the animation duration
     */
    public void closeMenus(long animationTime) {
        if (mOpenedHolder != null) {
            beforeCloseMenu(mOpenedHolder);
            mOpenedHolder.swipeMenu.closeMenu(animationTime, mCloseAnimationListener);
        } else if (mCurrentHolder != null) {
            beforeCloseMenu(mCurrentHolder);
            getSwipeMenu().closeMenu(animationTime, mCloseAnimationListener);
        }
    }

    /**
     * Close the current menu with animation.
     */
    public void closeMenusAnimated() {
        closeMenus(getAnimationTime());
    }

    /**
     * Close the current menu without animation
     */
    public void closeMenus() {
        closeMenus(0);
        handleMenuClosed();
    }

    /**
     * Resets the fields to the initial values, ready to start over.
     */
    private void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }

        mVelocityTracker = null;
        mDownX = 0;
        mDownY = 0;
        mCurrentHolder = null;
        mCurrentPosition = AdapterView.INVALID_POSITION;
        mSwiping = false;
        mCanShowMenuCurrent = false;
    }

    /**
     * Called when the user starts swiping a {@link android.view.View}.
     *
     * @param view
     *            the {@code View} that is being swiped.
     * @param position
     *            the position of the item in the
     *            {@link android.widget.ListAdapter} corresponding to the
     *            {@code View}.
     */
    protected void onStartSwipe(@NonNull final SwipeMenuViewHolder holder, final int direction) {
        if (mCallback != null) {
            mCallback.onStartSwipe(holder, direction);
        }
    }

    /**
     * Called when the menu is about to be closed
     *
     * @param view
     *            the {@code View} that was swiped.
     * @param position
     *            the position of the item in the
     *            {@link android.widget.ListAdapter} corresponding to the
     *            {@code View}.
     */
    protected void beforeCloseMenu(@NonNull final SwipeMenuViewHolder holder) {
        if (mCallback != null) {
            mCallback.beforeMenuClose(holder,
                    holder.swipeMenu.getCurrentDirection());
        }
    }

    /**
     * Called after the restore animation of a canceled swipe movement ends.
     *
     * @param view
     *            the {@code View} that is being swiped.
     * @param position
     *            the position of the item in the
     *            {@link android.widget.ListAdapter} corresponding to the
     *            {@code View}.
     */
    protected void afterCloseMenu(@NonNull final SwipeMenuViewHolder holder, final int direction) {
        if (mCallback != null) {
            mCallback.onMenuClosed(holder, direction);
        }
    }

    /**
     * Called before the menu opens
     *
     * @param view
     *            the {@code SwipeMenuLayout} that would be flinged.
     * @param position
     *            the position of the item in the
     *            {@link android.widget.ListAdapter} corresponding to the
     *            {@code View}.
     * @param position
     *            the direction in which the menu will open.
     *
     */
    protected void beforeOpenMenu(@NonNull final SwipeMenuViewHolder holder, final int direction) {
        if (mCallback != null) {
            mCallback.beforeMenuShow(holder, direction);
        }
    }

    /**
     * Called after the menu successfully opened. Users of this class should
     * implement any finalizing behavior at this point, such as notifying the
     * adapter.
     *
     * @param view
     *            the {@code SwipeMenuLayout} that is being swiped.
     * @param position
     *            the position of the item in the
     *            {@link android.widget.ListAdapter} corresponding to the
     *            {@code View}.
     */
    protected void afterOpenMenu(@NonNull final SwipeMenuViewHolder holder) {
    }

    private static Rect getChildViewRect(final View parentView,
            final View childView) {
        Rect childRect = new Rect(childView.getLeft(), childView.getTop(),
                childView.getRight(), childView.getBottom());
        if (!parentView.equals(childView)) {
            View workingChildView = childView;
            ViewGroup parent;
            while (!(parent = (ViewGroup) workingChildView.getParent())
                    .equals(parentView)) {
                childRect.offset(parent.getLeft(), parent.getTop());
                workingChildView = parent;
            }
        }
        return childRect;
    }

    private void handleMenuOpened(final SwipeMenuViewHolder holder) {
        mOpenedHolder = holder;
        mOpenedPosition = mOpenedHolder.getAdapterPosition();
        afterOpenMenu(holder);
    }

    /**
     * An {@link com.nineoldandroids.animation.Animator.AnimatorListener} that
     * notifies when the fling animation has ended.
     */
    private class FlingAnimatorListener extends AnimatorListenerAdapter {

        @NonNull
        private final SwipeMenuViewHolder mViewHolder;

        private FlingAnimatorListener(@NonNull final SwipeMenuViewHolder holder) {
            mViewHolder = holder;
        }

        @Override
        public void onAnimationEnd(@NonNull final Animator animation) {
            handleMenuOpened(mViewHolder);
        }
    }

    private void handleMenuClosed() {
        if (mOpenedHolder == null)
            return;
        int direction = mOpenedHolder.swipeMenu.getCurrentDirection();
        mOpenedHolder.swipeMenu.reset();
        mPreviousHolder = mOpenedHolder;
        mOpenedPosition = RecyclerView.NO_POSITION;
        mOpenedHolder = null;
        afterCloseMenu(mPreviousHolder, direction);
    }

    /**
     * An {@link com.nineoldandroids.animation.Animator.AnimatorListener} that
     * performs the dismissal animation when the current animation has ended.
     */
    private AnimatorListenerAdapter mCloseAnimationListener = new AnimatorListenerAdapter() {

        @Override
        public void onAnimationEnd(@NonNull final Animator animation) {
            handleMenuClosed();
        }
    };
    


    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        boolean result = false;
        switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            if (mRecyclerView != rv) {
                mRecyclerView = rv;
                final Context context = mRecyclerView.getContext();
                ViewConfiguration vc = ViewConfiguration.get( context);
                mSlop = vc.getScaledTouchSlop();
                mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
                mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
                mAnimationTime =  context.getResources().getInteger(android.R.integer.config_shortAnimTime);
            }
            
            result = handleDownEvent(e);
            break;
        case MotionEvent.ACTION_MOVE:
            result = handleMoveEvent(e);
            break;
        case MotionEvent.ACTION_CANCEL:
            result = handleCancelEvent();
            break;
        case MotionEvent.ACTION_UP:
            result = handleUpEvent(e);
            break;
        default:
            result = false;
        }

        return result;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        if (!mSwiping) {
            // Log.w(TAG, "onTouchEvent() - unexpected state");
            return;
        }
        boolean result;
        switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            result = handleDownEvent(e);
            break;
        case MotionEvent.ACTION_MOVE:
            result = handleMoveEvent(e);
            break;
        case MotionEvent.ACTION_CANCEL:
            result = handleCancelEvent();
            break;
        case MotionEvent.ACTION_UP:
            result = handleUpEvent(e);
            break;
        default:
            result = false;
        }
    }
    
    private boolean mIgnoreDisallow = false;
    @Override
    public void onRequestDisallowInterceptTouchEvent(
            boolean disallowIntercept) {
        if (disallowIntercept && !mIgnoreDisallow) {
            handleCancelEvent();
        }
    }

}

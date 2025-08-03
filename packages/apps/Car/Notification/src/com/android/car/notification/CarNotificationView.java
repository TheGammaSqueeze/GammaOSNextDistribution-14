package com.android.car.notification;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import com.android.car.notification.template.GroupNotificationViewHolder;
import com.android.car.uxr.UxrContentLimiterImpl;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.statusbar.IStatusBarService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;


/**
 * Layout that contains Car Notifications.
 *
 * It does some extra setup in the onFinishInflate method because it may not get used from an
 * activity where one would normally attach RecyclerViews
 */
public class CarNotificationView extends ConstraintLayout
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
    public static final boolean DEBUG = Build.IS_DEBUGGABLE;
    public static final String TAG = "CarNotificationView";

    private final boolean mCollapsePanelAfterManageButton;

    private CarNotificationViewAdapter mAdapter;
    private Context mContext;
    private LinearLayoutManager mLayoutManager;
    private NotificationClickHandlerFactory mClickHandlerFactory;
    private NotificationDataManager mNotificationDataManager;
    private boolean mIsClearAllActive = false;
    private List<NotificationGroup> mNotifications;
    private UxrContentLimiterImpl mUxrContentLimiter;
    private KeyEventHandler mKeyEventHandler;
    private RecyclerView mListView;
    private Button mManageButton;
    private TextView mEmptyNotificationHeaderText;

    public CarNotificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mNotificationDataManager = NotificationDataManager.getInstance();
        mCollapsePanelAfterManageButton = context.getResources().getBoolean(
                R.bool.config_collapseShadePanelAfterManageButtonPress);
    }

    /**
     * Attaches the CarNotificationViewAdapter and CarNotificationItemTouchListener to the
     * notification list.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mListView = findViewById(R.id.notifications);

        mListView.setClipChildren(false);
        mLayoutManager = new LinearLayoutManager(mContext);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addItemDecoration(new TopAndBottomOffsetDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));
        mListView.addItemDecoration(new ItemSpacingDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));
        mAdapter = new CarNotificationViewAdapter(mContext, /* isGroupNotificationAdapter= */
                false, this::startClearAllNotifications);
        mListView.setAdapter(mAdapter);

        mUxrContentLimiter = new UxrContentLimiterImpl(mContext, R.xml.uxr_config);
        mUxrContentLimiter.setAdapter(mAdapter);
        mUxrContentLimiter.start();

        mListView.addOnItemTouchListener(new CarNotificationItemTouchListener(mContext, mAdapter));

        mListView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // RecyclerView is not currently scrolling.
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    setVisibleNotificationsAsSeen();
                }
            }
        });
        mListView.setItemAnimator(new DefaultItemAnimator(){
            @Override
            public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder
                    newHolder, int fromX, int fromY, int toX, int toY) {
                if (oldHolder == newHolder) {
                    return animateMove(newHolder, fromX, fromY, toX, toY);
                }
                // return without animation to prevent flashing on notification update.
                dispatchChangeFinished(oldHolder, /* oldItem= */ true);
                dispatchChangeFinished(newHolder, /* oldItem= */ false);
                return true;
            }
        });

        Button clearAllButton = findViewById(R.id.clear_all_button);
        mEmptyNotificationHeaderText = findViewById(R.id.empty_notification_text);
        mManageButton = findViewById(R.id.manage_button);
        mManageButton.setOnClickListener(this::manageButtonOnClickListener);

        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(v -> startClearAllNotifications());
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }

        if (mKeyEventHandler != null) {
            return mKeyEventHandler.dispatchKeyEvent(event);
        }

        return false;
    }

    @VisibleForTesting
    List<NotificationGroup> getNotifications() {
        return mNotifications;
    }

    /** Sets a {@link KeyEventHandler} to help interact with the notification panel. */
    public void setKeyEventHandler(KeyEventHandler keyEventHandler) {
        mKeyEventHandler = keyEventHandler;
    }

    /**
     * Updates notifications and update views.
     */
    public void setNotifications(List<NotificationGroup> notifications) {
        mNotifications = notifications;
        mAdapter.setNotifications(notifications, /* setRecyclerViewListHeaderAndFooter= */ true);
        refreshVisibility();
    }

    /**
     * Removes notification from group list and updates views.
     */
    public void removeNotification(AlertEntry alertEntry) {
        if (DEBUG) {
            Log.d(TAG, "Removing notification: " + alertEntry);
        }

        for (int i = 0; i < mNotifications.size(); i++) {
            NotificationGroup notificationGroup = new NotificationGroup(mNotifications.get(i));
            boolean notificationRemoved = notificationGroup.removeNotification(alertEntry);
            if (notificationRemoved) {
                if (notificationGroup.getChildCount() == 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Group deleted");
                    }
                    mNotifications.remove(i);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "Edited notification group: " + notificationGroup);
                    }
                    mNotifications.set(i, notificationGroup);
                }
                break;
            }
        }

        mAdapter.setNotifications(mNotifications, /* setRecyclerViewListHeaderAndFooter= */ true);
        refreshVisibility();
    }

    private void refreshVisibility() {
        if (mAdapter.hasNotifications()) {
            mEmptyNotificationHeaderText.setVisibility(View.GONE);
            mManageButton.setVisibility(View.GONE);
        } else {
            mEmptyNotificationHeaderText.setVisibility(View.VISIBLE);
            mManageButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Collapses all expanded groups and empties notifications being cleared set.
     */
    public void resetState() {
        mAdapter.collapseAllGroups();
        for (int i = 0; i < mAdapter.getItemCount(); i++) {
            RecyclerView.ViewHolder holder = mListView.findViewHolderForAdapterPosition(i);
            if (holder != null && holder.getItemViewType() == NotificationViewType.GROUP) {
                GroupNotificationViewHolder groupNotificationViewHolder =
                        (GroupNotificationViewHolder) holder;
                groupNotificationViewHolder.collapseGroup();
            }
        }
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        mAdapter.setCarUxRestrictions(restrictionInfo);
    }

    /**
     * Sets the NotificationClickHandlerFactory that allows for a hook to run a block off code
     * when  the notification is clicked. This is useful to dismiss a screen after
     * a notification list clicked.
     */
    public void setClickHandlerFactory(NotificationClickHandlerFactory clickHandlerFactory) {
        mClickHandlerFactory = clickHandlerFactory;
        mAdapter.setClickHandlerFactory(clickHandlerFactory);
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add a top offset to the first item and bottom
     * offset to the last item in the RecyclerView it is added to.
     */
    private static class TopAndBottomOffsetDecoration extends RecyclerView.ItemDecoration {
        private int mTopAndBottomOffset;

        private TopAndBottomOffsetDecoration(int topOffset) {
            mTopAndBottomOffset = topOffset;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            if (position == 0) {
                outRect.top = mTopAndBottomOffset;
            }
            if (position == state.getItemCount() - 1) {
                outRect.bottom = mTopAndBottomOffset;
            }
        }
    }

    /**
     * Identifies dismissible notifications views and animates them out in the order
     * specified in config. Calls finishClearNotifications on animation end.
     */
    private void startClearAllNotifications() {
        // Prevent invoking the click listeners again until the current clear all flow is complete.
        if (mIsClearAllActive) {
            return;
        }
        mIsClearAllActive = true;

        List<NotificationGroup> dismissibleNotifications = getAllDismissibleNotifications();
        List<View> dismissibleNotificationViews = getNotificationViews(dismissibleNotifications);

        if (dismissibleNotificationViews.isEmpty()) {
            finishClearAllNotifications(dismissibleNotifications);
            return;
        }

        AnimatorSet animatorSet = createDismissAnimation(dismissibleNotificationViews);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                finishClearAllNotifications(dismissibleNotifications);
            }
        });
        animatorSet.start();
    }

    /**
     * Returns a List of all Notification Groups that are dismissible.
     */
    private List<NotificationGroup> getAllDismissibleNotifications() {
        List<NotificationGroup> notifications = new ArrayList<>();
        mNotifications.forEach(notificationGroup -> {
            if (notificationGroup.isDismissible()) {
                notifications.add(notificationGroup);
            }
        });
        return notifications;
    }

    /**
     * Returns the Views that are bound to the provided notifications, sorted so that their
     * positions are in the ascending order.
     *
     * <p>Note: Provided notifications might not have Views bound to them.</p>
     */
    private List<View> getNotificationViews(List<NotificationGroup> notifications) {
        Set notificationIds = new HashSet();
        notifications.forEach(notificationGroup -> {
            long id = notificationGroup.isGroup() ? notificationGroup.getGroupKey().hashCode() :
                    notificationGroup.getSingleNotification().getKey().hashCode();
            notificationIds.add(id);
        });

        TreeMap<Integer, View> notificationViews = new TreeMap<>();
        for (int i = 0; i < mListView.getChildCount(); i++) {
            View currentChildView = mListView.getChildAt(i);
            RecyclerView.ViewHolder holder = mListView.getChildViewHolder(currentChildView);
            int position = holder.getLayoutPosition();
            if (notificationIds.contains(mAdapter.getItemId(position))) {
                notificationViews.put(position, currentChildView);
            }
        }
        List<View> notificationViewsSorted = new ArrayList<>(notificationViews.values());

        return notificationViewsSorted;
    }

    /**
     * Returns {@link AnimatorSet} for dismissing notifications from the clear all event.
     */
    private AnimatorSet createDismissAnimation(List<View> dismissibleNotificationViews) {
        ArrayList<Animator> animators = new ArrayList<>();
        boolean dismissFromBottomUp = getContext().getResources().getBoolean(
                R.bool.config_clearAllNotificationsAnimationFromBottomUp);
        int delayInterval = getContext().getResources().getInteger(
                R.integer.clear_all_notifications_animation_delay_interval_ms);
        for (int i = 0; i < dismissibleNotificationViews.size(); i++) {
            View currentView = dismissibleNotificationViews.get(i);
            ObjectAnimator animator = (ObjectAnimator) AnimatorInflater.loadAnimator(mContext,
                    R.animator.clear_all_animate_out);
            animator.setTarget(currentView);

            /*
             * Each animator is assigned a different start delay value in order to generate the
             * animation effect of dismissing notifications one by one.
             * Therefore, the delay calculation depends on whether the notifications are
             * dismissed from bottom up or from top down.
             */
            int delayMultiplier = dismissFromBottomUp ? dismissibleNotificationViews.size() - i : i;
            int delay = delayInterval * delayMultiplier;

            animator.setStartDelay(delay);
            animators.add(animator);
        }
        ObjectAnimator[] animatorsArray = animators.toArray(new ObjectAnimator[animators.size()]);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animatorsArray);

        return animatorSet;
    }

    /**
     * Clears the provided notifications with {@link IStatusBarService} and optionally collapses the
     * shade panel.
     */
    private void finishClearAllNotifications(List<NotificationGroup> dismissibleNotifications) {
        boolean collapsePanel = getContext().getResources().getBoolean(
                R.bool.config_collapseShadePanelAfterClearAllNotifications);
        int collapsePanelDelay = getContext().getResources().getInteger(
                R.integer.delay_between_clear_all_notifications_end_and_collapse_shade_panel_ms);

        mClickHandlerFactory.clearNotifications(dismissibleNotifications);

        if (collapsePanel) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.postDelayed(() -> {
                    mClickHandlerFactory.collapsePanel();
                }, collapsePanelDelay);
            }
        }

        mIsClearAllActive = false;
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private int mItemSpacing;

        private ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item.
            if (position == state.getItemCount() - 1) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }

    /**
     * Sets currently visible notifications as "seen".
     */
    public void setVisibleNotificationsAsSeen() {
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();

        // No visible items are found.
        if (firstVisible == RecyclerView.NO_POSITION) return;

        mAdapter.setVisibleNotificationsAsSeen(firstVisible, lastVisible);
    }

    private void manageButtonOnClickListener(View v) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        mContext.startActivityAsUser(intent,
                UserHandle.of(NotificationUtils.getCurrentUser(mContext)));

        if (mClickHandlerFactory != null && mCollapsePanelAfterManageButton) {
            mClickHandlerFactory.collapsePanel();
        }
    }

    /** An interface to help interact with the notification panel. */
    public interface KeyEventHandler {
        /** Allows handling of a {@link KeyEvent} if it isn't already handled by the superclass. */
        boolean dispatchKeyEvent(KeyEvent event);
    }

    @VisibleForTesting
    void setAdapter(CarNotificationViewAdapter adapter) {
        mAdapter = adapter;
    }

    @VisibleForTesting
    void setListView(RecyclerView listView) {
        mListView = listView;
    }
}

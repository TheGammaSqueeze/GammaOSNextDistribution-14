package com.android.car.notification;

import android.car.drivingstate.CarUxRestrictions;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.List;

/**
 * This class is a bridge to collect signals from the notification and ux restriction services and
 * trigger the correct UI updates.
 */
public class NotificationViewController {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "NotificationViewControl";
    private final CarNotificationView mCarNotificationView;
    private final PreprocessingManager mPreprocessingManager;
    private final CarNotificationListener mCarNotificationListener;
    private final boolean mShowRecentsAndOlderHeaders;
    private final CarUxRestrictionManagerWrapper mUxResitrictionListener;
    private final NotificationDataManager mNotificationDataManager;
    private final NotificationUpdateHandler mNotificationUpdateHandler =
            new NotificationUpdateHandler();
    private boolean mShowLessImportantNotifications;
    private boolean mIsVisible;

    public NotificationViewController(CarNotificationView carNotificationView,
            PreprocessingManager preprocessingManager,
            CarNotificationListener carNotificationListener,
            CarUxRestrictionManagerWrapper uxResitrictionListener) {
        mCarNotificationView = carNotificationView;
        mPreprocessingManager = preprocessingManager;
        mCarNotificationListener = carNotificationListener;
        mUxResitrictionListener = uxResitrictionListener;
        mNotificationDataManager = NotificationDataManager.getInstance();
        mShowRecentsAndOlderHeaders = mCarNotificationView.getContext()
                        .getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);

        // Long clicking on the notification center title toggles hiding media, navigation, and
        // less important (< IMPORTANCE_DEFAULT) ongoing foreground service notifications.
        // This is only available for ENG and USERDEBUG builds.
        View view = mCarNotificationView.findViewById(R.id.notification_center_title);
        if (view != null && (Build.IS_ENG || Build.IS_USERDEBUG)) {
            view.setOnLongClickListener(v -> {
                mShowLessImportantNotifications = !mShowLessImportantNotifications;
                Toast.makeText(
                        carNotificationView.getContext(),
                        "Foreground, navigation and media notifications " + (
                                mShowLessImportantNotifications ? "ENABLED" : "DISABLED"),
                        Toast.LENGTH_SHORT).show();
                resetNotifications(mShowLessImportantNotifications);
                return true;
            });
        }

        resetNotifications(mShowLessImportantNotifications);
    }

    /**
     * Updates UI and registers required listeners
     */
    public void enable() {
        mCarNotificationListener.setHandler(mNotificationUpdateHandler);
        mUxResitrictionListener.setCarNotificationView(mCarNotificationView);
        try {
            CarUxRestrictions currentRestrictions =
                    mUxResitrictionListener.getCurrentCarUxRestrictions();
            mCarNotificationView.onUxRestrictionsChanged(currentRestrictions);
        } catch (RuntimeException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    /**
     * Remove listeners.
     */
    public void disable() {
        mCarNotificationListener.setHandler(null);
        mUxResitrictionListener.setCarNotificationView(null);
    }

    /**
     * Called when the notification view's visibility is changed.
     */
    public void onVisibilityChanged(boolean isVisible) {
        mIsVisible = isVisible;
        mCarNotificationListener.onVisibilityChanged(mIsVisible);
        // Reset and collapse all groups when notification view disappears.
        if (!mIsVisible) {
            resetNotifications(mShowLessImportantNotifications);
            mCarNotificationView.resetState();
        }
    }

    /**
     * Reset notifications to the latest state.
     */
    private void resetNotifications(boolean showLessImportantNotifications) {
        mPreprocessingManager.init(mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        if (DEBUG) {
            Log.d(TAG, "Unprocessed notification map: "
                    + mCarNotificationListener.getNotifications());
        }

        List<NotificationGroup> notificationGroups = mPreprocessingManager.process(
                showLessImportantNotifications,
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        if (DEBUG) {
            Log.d(TAG, "Processed notification groups: " + notificationGroups);
        }

        if (!mShowRecentsAndOlderHeaders) {
            mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        }

        mCarNotificationView.setNotifications(notificationGroups);
    }

    /**
     * Update notifications: no grouping/ranking updates will go through.
     * Insertion, deletion and content update will apply immediately.
     */
    private void updateNotifications(
            boolean showLessImportantNotifications, int what, AlertEntry alertEntry) {

        if (mPreprocessingManager.shouldFilter(alertEntry,
                mCarNotificationListener.getCurrentRanking())) {
            // if the new notification should be filtered out, return early
            return;
        }

        List<NotificationGroup> notificationGroups = mPreprocessingManager.updateNotifications(
                showLessImportantNotifications,
                alertEntry,
                what,
                mCarNotificationListener.getCurrentRanking());
        if (what == CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED) {
            mCarNotificationView.removeNotification(alertEntry);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Updated notification groups: " + notificationGroups);
            }

            mCarNotificationView.setNotifications(notificationGroups);
        }
    }

    private class NotificationUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (mIsVisible) {
                if (message.what == CarNotificationListener.NOTIFY_RANKING_UPDATED) {
                    // Do not update notifications if ranking is updated while panel is visible.
                    return;
                }

                updateNotifications(
                        mShowLessImportantNotifications,
                        message.what,
                        (AlertEntry) message.obj);
            } else {
                resetNotifications(mShowLessImportantNotifications);
            }
        }
    }
}

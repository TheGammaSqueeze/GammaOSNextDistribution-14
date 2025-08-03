package android.platform.helpers;

public interface IAutoNotificationMockingHelper extends IAppHelper {
    /**
     * Setup expectations: No.
     *
     * <p>Clear all notifications generated.
     */
    void clearAllNotification();

    /**
     * Setup Expectations: None
     *
     * <p>Posts a number of notifications to the device. Successive calls to this should post new
     * notifications to those previously posted. Note that this may fail if the helper has surpassed
     * the system-defined limit for per-package notifications.
     *
     * @param count The number of notifications to post.
     */
    public default void postNotifications(int count) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Setup Expectations: None
     *
     * <p>Posts a number of notifications to the device with a package to launch. Successive calls
     * to this should post new notifications in addition to those previously posted. Note that this
     * may fail if the helper has surpassed the system-defined limit for per-package notifications.
     *
     * @param count The number of notifications to post.
     * @param pkg The application that will be launched by notifications.
     */
    public default void postNotifications(int count, String pkg) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Setup Expectations: None
     *
     * <p>Posts a number of notifications to the device with a package to launch. Successive calls
     * to this should post new notifications in addition to those previously posted. Note that this
     * may fail if the helper has surpassed the system-defined limit for per-package notifications.
     *
     * @param count The number of notifications to post.
     * @param pkg The application that will be launched by notifications.
     * @param interrupting If notification should make sounds and be on top section of the shade.
     */
    public default void postNotifications(int count, String pkg, boolean interrupting) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }
}

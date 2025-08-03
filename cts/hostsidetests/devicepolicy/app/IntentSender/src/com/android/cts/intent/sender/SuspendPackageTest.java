package com.android.cts.intent.sender;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

public class SuspendPackageTest extends InstrumentationTestCase {

    private static final String TAG = "IntentSender.SuspendPackageTest";

    private static final int WAIT_DIALOG_TIMEOUT_IN_MS = 5000;
    private static final BySelector POPUP_TITLE_WATCH_SELECTOR = By
            .clazz(android.widget.TextView.class.getName())
            .res("com.google.android.apps.wearable.settings:id/wear_alertdialog_title_text")
            .pkg("com.google.android.apps.wearable.settings");
    private static final BySelector POPUP_TITLE_AUTOMOTIVE_SELECTOR = By
            .clazz(android.widget.TextView.class.getName())
            .res("com.android.car.settings:id/car_ui_alert_title")
            .pkg("com.android.car.settings");

    private static final BySelector SUSPEND_BUTTON_SELECTOR = By
            .clazz(android.widget.Button.class.getName())
            .res("android:id/button1");

    private IntentSenderActivity mActivity;
    private Context mContext;
    private PackageManager mPackageManager;
    private UiAutomation mUiAutomation;

    private static final String DEFAULT_SETTINGS_PKG = "com.android.settings";
    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String TARGET_ACTIVITY_NAME =
            "com.android.cts.intent.receiver.SimpleIntentReceiverActivity";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        String packageName = mContext.getPackageName();
        int userId = mContext.getUserId();
        Class<IntentSenderActivity> activityClass = IntentSenderActivity.class;
        Log.d(TAG, "setup(): launching " + activityClass + " on user " + userId);
        mActivity = launchActivity(packageName, activityClass, null);
        assertWithMessage("activity %s launched on package %s on user %s",
                activityClass, packageName, userId).that(mActivity).isNotNull();
        Log.d(TAG, "setup(): launched activity " + mActivity);
        mPackageManager = mContext.getPackageManager();
        mUiAutomation = getInstrumentation().getUiAutomation();
    }

    @Override
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
        super.tearDown();
    }

    public void testPackageSuspended() throws Exception {
        assertPackageSuspended(/* suspended= */ true, /* customDialog= */ false);
    }

    public void testPackageNotSuspended() throws Exception {
        assertPackageSuspended(/* suspended= */ false, /* customDialog= */ false);
    }

    public void testPackageSuspendedWithPackageManager() throws Exception {
        assertPackageSuspended(/* suspended= */ true, /* customDialog= */ true);
    }

    /**
     * Verify that the package is suspended by trying to start the activity inside it. If the
     * package is not suspended, the target activity will return the result.
     */
    private void assertPackageSuspended(boolean suspended, boolean customDialog) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(INTENT_RECEIVER_PKG, TARGET_ACTIVITY_NAME);
        Intent result = mActivity.getResult(intent);
        Log.d(TAG, "assertPackageSuspended(suspended=" + suspended
                + ", customDialog=" + customDialog + "): result for activity "
                + INTENT_RECEIVER_PKG + "/" + TARGET_ACTIVITY_NAME + " on user "
                + mContext.getUserId() + ": " + result);
        if (suspended) {
            if (customDialog) {
                dismissCustomDialog();
            } else {
                dismissPolicyTransparencyDialog();
            }
            assertWithMessage("result for activitiy %s while suspended", intent).that(result)
                    .isNull();
        } else {
            assertWithMessage("result for activitiy %s while NOT suspended", intent)
                    .that(result).isNotNull();
        }
        // No matter if it is suspended or not, we should be able to resolve the activity.
        ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, /* flags= */ 0);
        assertWithMessage("ResolveInfo for activity %s", intent).that(resolveInfo).isNotNull();
        Log.d(TAG, "ResolveInfo: " + resolveInfo);
    }

    /**
     * Wait for the policy transparency dialog and dismiss it.
     */
    private void dismissPolicyTransparencyDialog() {
        final UiDevice device = UiDevice.getInstance(getInstrumentation());
        if (isWatch()) {
            device.wait(Until.hasObject(POPUP_TITLE_WATCH_SELECTOR), WAIT_DIALOG_TIMEOUT_IN_MS);
            final UiObject2 title = device.findObject(POPUP_TITLE_WATCH_SELECTOR);
            assertWithMessage("Policy transparency dialog title").that(title).isNotNull();
            title.swipe(Direction.RIGHT, 1.0f);
        } else if (isAutomotive()) {
            device.wait(Until.hasObject(POPUP_TITLE_AUTOMOTIVE_SELECTOR),
                    WAIT_DIALOG_TIMEOUT_IN_MS);
            Log.d(TAG, "waiting for " + POPUP_TITLE_AUTOMOTIVE_SELECTOR);
            final UiObject2 title = device.findObject(POPUP_TITLE_AUTOMOTIVE_SELECTOR);
            assertWithMessage("Policy transparency dialog title").that(title).isNotNull();
            // "OK" button only present in the dialog if it is blocked by policy.
            BySelector selector = getPopUpButtonSelector();
            Log.d(TAG, "waiting for " + selector);
            final UiObject2 button = device.findObject(getPopUpButtonSelector());
            assertWithMessage("OK button").that(button).isNotNull();
            button.click();
        } else {
            device.wait(Until.hasObject(getPopUpImageSelector()), WAIT_DIALOG_TIMEOUT_IN_MS);
            final UiObject2 icon = device.findObject(getPopUpImageSelector());
            assertWithMessage("Policy transparency dialog icon").that(icon).isNotNull();
            // "OK" button only present in the dialog if it is blocked by policy.
            final UiObject2 button = device.findObject(getPopUpButtonSelector());
            assertWithMessage("OK button").that(button).isNotNull();
            button.click();
        }
    }

    private void dismissCustomDialog() {
        final UiDevice device = UiDevice.getInstance(getInstrumentation());
        device.wait(Until.hasObject(SUSPEND_BUTTON_SELECTOR), WAIT_DIALOG_TIMEOUT_IN_MS);

        final UiObject2 button = device.findObject(SUSPEND_BUTTON_SELECTOR);
        assertWithMessage("OK button").that(button).isNotNull();
    }

    private boolean isWatch() {
        return (getInstrumentation().getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH;
    }

    private boolean isAutomotive() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private String getSettingsPackageName() {
        String settingsPackageName = "com.android.settings";
        try {
            mUiAutomation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS");
            int userId = UserManager.isHeadlessSystemUserMode()
                    ? ActivityManager.getCurrentUser() // doesn't work with UserHandle.USER_CURRENT
                    : UserHandle.USER_SYSTEM;
            ResolveInfo resolveInfo = mPackageManager.resolveActivityAsUser(
                    new Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_SYSTEM_ONLY, userId);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                String packageName = resolveInfo.activityInfo.packageName;
                Log.d(TAG, "getSettingsPackageName(): returning " + packageName);
                return packageName;
            }
            Log.w(TAG, "Unable to resolve ACTION_SETTINGS (" + Settings.ACTION_SETTINGS
                    + ") intent for user " + userId);
            return DEFAULT_SETTINGS_PKG;
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private BySelector getPopUpButtonSelector() {
        return By.clazz(android.widget.Button.class.getName())
                .res("android:id/button1")
                .pkg(getSettingsPackageName());
    }

    private BySelector getPopUpImageSelector() {
        final String settingsPackageName = getSettingsPackageName();
        return By.clazz(android.widget.ImageView.class.getName())
                .res(settingsPackageName + ":id/admin_support_icon")
                .pkg(settingsPackageName);
    }
}

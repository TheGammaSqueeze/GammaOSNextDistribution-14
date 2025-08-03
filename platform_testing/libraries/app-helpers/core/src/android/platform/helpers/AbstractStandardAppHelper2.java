/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.platform.helpers;

import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.systemui.Flags.keyguardBottomAreaRefactor;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.HomeVisibilityListener;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.platform.helpers.exceptions.AccountException;
import android.platform.helpers.exceptions.TestHelperException;
import android.platform.helpers.exceptions.UnknownUiException;
import android.platform.helpers.watchers.AppIsNotRespondingWatcher2;
import android.platform.spectatio.configs.UiElement;
import android.platform.spectatio.utils.SpectatioConfigUtil;
import android.platform.spectatio.utils.SpectatioUiUtil;
import android.support.test.launcherhelper2.ILauncherStrategy;
import android.support.test.launcherhelper2.LauncherStrategyFactory;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiWatcher;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractStandardAppHelper2 implements IAppHelper {
    private static final String LOG_TAG = AbstractStandardAppHelper2.class.getSimpleName();
    private static final String SCREENSHOT_DIR = "apphelper-screenshots";
    private static final String FAVOR_CMD = "favor-shell-commands";
    private static final String USE_HOME_CMD = "press-home-to-exit";
    private static final String APP_IDLE_OPTION = "app-idle_ms";
    private static final String LAUNCH_TIMEOUT_OPTION = "app-launch-timeout_ms";
    private static final String UNROOT_NON_PIXEL = "unroot-non-pixel";
    private static final String ERROR_NOT_FOUND =
            "Element %s %s is not found in the application %s";

    private static final long EXIT_WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final int WAIT_TIME_MS = 10000;

    private static File sScreenshotDirectory;

    public UiDevice mDevice;
    public Instrumentation mInstrumentation;
    public ILauncherStrategy mLauncherStrategy;
    private final KeyCharacterMap mKeyCharacterMap =
            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
    private final boolean mFavorShellCommands;
    private final boolean mPressHomeToExit;
    private final boolean mUnrootNonPixel;
    private final long mAppIdle;
    private final long mLaunchTimeout;

    private static final String NOTIF_PERM = android.Manifest.permission.POST_NOTIFICATIONS;
    private UiAutomation mAutomation;
    private int mPreviousFlagValues = 0;
    private boolean mChangedPermState = false;

    // Spectatio Utils
    private SpectatioConfigUtil mSpectatioConfigUtil;
    private SpectatioUiUtil mSpectatioUiUtil;

    private static final BySelector KEYGUARD_BOTTOM_AREA_VIEW =
            By.res("com.android.systemui", "keyguard_bottom_area");

    private static final BySelector KEYGUARD_ROOT_VIEW =
            By.res("com.android.systemui", "keyguard_root_view");

    public AbstractStandardAppHelper2(Instrumentation instr) {
        mInstrumentation = instr;
        mDevice = UiDevice.getInstance(instr);
        mFavorShellCommands =
                Boolean.valueOf(
                        InstrumentationRegistry.getArguments().getString(FAVOR_CMD, "false"));
        mPressHomeToExit =
                Boolean.valueOf(
                        InstrumentationRegistry.getArguments().getString(USE_HOME_CMD, "false"));
        mUnrootNonPixel =
                Boolean.valueOf(
                        InstrumentationRegistry.getArguments()
                                .getString(UNROOT_NON_PIXEL, "false"));
        mAppIdle =
                Long.valueOf(
                        InstrumentationRegistry.getArguments()
                                .getString(
                                        APP_IDLE_OPTION,
                                        String.valueOf(TimeUnit.SECONDS.toMillis(0))));
        // TODO(b/127356533): Choose a sensible default for app launch timeout after b/125356281.
        mLaunchTimeout =
                Long.valueOf(
                        InstrumentationRegistry.getArguments()
                                .getString(
                                        LAUNCH_TIMEOUT_OPTION,
                                        String.valueOf(TimeUnit.SECONDS.toMillis(30))));

        initializeSpectatioUtils();
    }

    // Start Spectatio Util Helper Methods

    private void initializeSpectatioUtils() {
        mSpectatioUiUtil = SpectatioUiUtil.getInstance(mInstrumentation);
        mSpectatioConfigUtil = SpectatioConfigUtil.getInstance();
    }

    protected SpectatioUiUtil getSpectatioUiUtil() {
        return mSpectatioUiUtil;
    }

    /**
     * Get action from Config.
     *
     * <p>e.g. JSON Config { "ACTIONS": { "ACTION_NAME1": "ACTION_VALUE1", "ACTION_NAME2":
     * "ACTION_VALUE2", ... } }
     *
     * @param actionName is the Key for the action to read from config
     */
    protected String getActionFromConfig(String actionName) {
        return mSpectatioConfigUtil.getActionFromConfig(actionName);
    }

    /**
     * Get command from Config.
     *
     * <p>e.g. JSON Config { "COMMANDS": { "CMD_NAME1": "CMD_VALUE1", "CMD_NAME2": "CMD_VALUE2", ...
     * } }
     *
     * @param commandName is the Key for the Command to read from config
     */
    protected String getCommandFromConfig(String commandName) {
        return mSpectatioConfigUtil.getCommandFromConfig(commandName);
    }

    /**
     * Get package from Config.
     *
     * <p>e.g. JSON Config { "PACKAGES": { "PKG_NAME1": "PKG_VALUE1", "PKG_NAME2": "PKG_VALUE2", ...
     * } }
     *
     * @param packageName is the Key for the package to read from config
     */
    protected String getPackageFromConfig(String packageName) {
        return mSpectatioConfigUtil.getPackageFromConfig(packageName);
    }

    /**
     * Get Ui Element Selector from Config.
     *
     * <p>e.g. JSON Config { "UI_ELEMENTS": { "UI_ELEMENT_NAME1": { "TYPE": "RESOURCE_TYPE1",
     * "VALUE": "RESOURCE_VALUE1", "PACKAGE": "RESOURCE_PACKAGE1" }, "UI_ELEMENT_NAME2": { "TYPE":
     * "RESOURCE_TYPE2", "VALUE": "RESOURCE_VALUE2", "PACKAGE": "RESOURCE_PACKAGE2" }, ... } }
     *
     * <p>RESOURCE_TYPE: TEXT, DESCRIPTION, RESOURCE_ID, TEXT_CONTAINS, CLASS; RESOURCE_VALUE: Value
     * of the Resource; RESOURCE_PACKAGE: Package is required only to type RESOURCE_ID
     *
     * <p>Resource Values are referred in code using {@link UiElement} class
     *
     * @param uiElementName is the Key for the Ui Element to read from config
     */
    protected androidx.test.uiautomator.BySelector getUiElementFromConfig(String uiElementName) {
        return mSpectatioConfigUtil.getUiElementFromConfig(uiElementName);
    }

    protected void executeWorkflow(String workflowName) {
        mSpectatioConfigUtil.executeWorkflow(workflowName, mSpectatioUiUtil);
    }

    // End Spectatio Util Helper Methods

    /** {@inheritDoc} */
    @Override
    public void open() {
        Trace.beginSection("open app");
        // Grant notification permission if necessary - otherwise the app may display a permission
        // prompt that interferes with tests
        Trace.beginSection("notification permission");
        maybeGrantNotificationPermission();
        Trace.endSection();

        // Turn on the screen if necessary.
        try {
            Trace.beginSection("wake screen");
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }
        } catch (RemoteException e) {
            throw new TestHelperException("Could not unlock the device.", e);
        } finally {
            Trace.endSection();
        }

        BySelector screenLock;
        // keyguardBottomAreaRefactor() needs READ_DEVICE_CONFIG permission
        // which is only available for system app.
        if (!mUnrootNonPixel && keyguardBottomAreaRefactor()) {
            screenLock = KEYGUARD_ROOT_VIEW;
        } else {
            screenLock = KEYGUARD_BOTTOM_AREA_VIEW;
        }

        // Unlock the screen if necessary.
        Trace.beginSection("unlock screen");
        if (mDevice.hasObject(screenLock)) {
            mDevice.pressMenu();
            mDevice.waitForIdle();
        }
        Trace.endSection();
        // Launch the application as normal.
        String pkg = getPackage();
        long launchInitiationTimeMs = System.currentTimeMillis();

        Trace.beginSection("register dialog watchers");
        registerDialogWatchers();
        Trace.endSection();
        if (mFavorShellCommands) {
            Trace.beginSection("favor shell commands, launching");
            String output = null;
            try {
                if (mUnrootNonPixel) {
                    Log.i(LOG_TAG, String.format("Sending command to launch: %s", pkg));
                    mInstrumentation
                            .getContext()
                            .startActivity(
                                    getOpenAppIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    Log.i(LOG_TAG, String.format("Sending command to launch: %s", pkg));
                    mInstrumentation
                            .getContext()
                            .startActivityAsUser(
                                    getOpenAppIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    UserHandle.CURRENT);
                }
            } catch (ActivityNotFoundException e) {
                removeDialogWatchers();
                throw new TestHelperException(String.format("Failed to find package: %s", pkg), e);
            } finally {
                Trace.endSection();
            }
        } else {
            Trace.beginSection("launch using launcher strategy");
            // Launch using the UI and launcher strategy.
            String id = getLauncherName();
            if (!mDevice.hasObject(By.pkg(pkg).depth(0))) {
                getLauncherStrategy().launch(id, pkg);
                Log.i(LOG_TAG, "Launched package: id=" + id + ", pkg=" + pkg);
            }
            Trace.endSection();
        }

        Trace.beginSection("wait for foreground");
        try {
            // Ensure the package is in the foreground for success.
            if (!mDevice.wait(Until.hasObject(By.pkg(pkg).depth(0)), mLaunchTimeout)) {
                removeDialogWatchers();
                throw new IllegalStateException(
                        String.format(
                                "Did not find package, %s, in foreground after %d ms.",
                                pkg, System.currentTimeMillis() - launchInitiationTimeMs));
            }
        } finally {
            Trace.endSection();
        }
        Trace.beginSection("removeDialogWatchers");
        removeDialogWatchers();
        Trace.endSection();
        Trace.beginSection("idleApp: " + mAppIdle);
        // Idle for specified time after app launch
        idleApp();
        Trace.endSection();
    }

    private void idleApp() {
        if (mAppIdle != 0) {
            Log.v(LOG_TAG, String.format("Idle app for %d ms", mAppIdle));
            SystemClock.sleep(mAppIdle);
        }
    }

    /**
     * Returns the {@code Intent} used by {@code open()} to launch an {@code Activity}. The default
     * implementation launches the default {@code Activity} of the package. Override this method to
     * launch a different {@code Activity}.
     */
    public Intent getOpenAppIntent() {
        Intent intent =
                mInstrumentation
                        .getContext()
                        .getPackageManager()
                        .getLaunchIntentForPackage(getPackage());
        if (intent == null) {
            throw new IllegalStateException(
                    String.format("Failed to get intent of package: %s", getPackage()));
        }
        return intent;
    }

    /** {@inheritDoc} */
    @Override
    public void exit() {
        Log.i(LOG_TAG, "Exiting the current application.");
        ActivityManager activityManager = null;
        final AtomicBoolean isHomeVisible = new AtomicBoolean();

        if (mUnrootNonPixel) {
            activityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        }

        if (mPressHomeToExit) {
            mDevice.pressHome();
            mDevice.waitForIdle();
        } else {
            int maxBacks = 4;
            while (!mDevice.hasObject(getLauncherStrategy().getWorkspaceSelector())
                    && maxBacks > 0) {
                mDevice.pressBack();
                mDevice.waitForIdle();
                maxBacks--;
            }

            if (maxBacks == 0) {
                mDevice.pressHome();
            }
        }

        // Check if home is visible for unroot non-pixel devices.
        if (mUnrootNonPixel) {
            final HomeVisibilityListener homeVisibilityListener =
                    new HomeVisibilityListener() {
                        @Override
                        public void onHomeVisibilityChanged(boolean isHomeActivityVisible) {
                            Log.i(LOG_TAG, "onHomeVisibilityChanged: " + isHomeActivityVisible);
                            isHomeVisible.set(isHomeActivityVisible);
                        }
                    };

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    activityManager,
                    (am) -> am.addHomeVisibilityListener(Runnable::run, homeVisibilityListener));

            try {
                TestUtils.waitUntil(
                        "Failed to exit the app to launcher",
                        WAIT_TIME_MS / 1000,
                        isHomeVisible::get);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                        activityManager,
                        (am) -> am.removeHomeVisibilityListener(homeVisibilityListener));
            }
        } else {
            if (!mDevice.wait(
                    Until.hasObject(getLauncherStrategy().getWorkspaceSelector()),
                    EXIT_WAIT_TIMEOUT)) {
                throw new IllegalStateException("Failed to exit the app to launcher.");
            }
        }
        restoreNotificationPermissionState();
    }

    /** {@inheritDoc} */
    @Override
    public String getVersion() throws NameNotFoundException {
        String pkg = getPackage();

        if (null == pkg || pkg.isEmpty()) {
            throw new TestHelperException("Cannot find version of empty package");
        }
        PackageManager pm = mInstrumentation.getContext().getPackageManager();
        PackageInfo pInfo = pm.getPackageInfo(pkg, 0);
        String version = pInfo.versionName;
        if (null == version || version.isEmpty()) {
            throw new TestHelperException(
                    String.format("Version isn't found for package, %s", pkg));
        }

        return version;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAppInForeground() {
        return mDevice.hasObject(By.pkg(getPackage()).depth(0));
    }

    protected int getOrientation() {
        return mInstrumentation.getContext().getResources().getConfiguration().orientation;
    }

    protected void requiresGoogleAccount() {
        if (!hasRegisteredGoogleAccount()) {
            throw new AccountException("This method requires a Google account be registered.");
        }
    }

    protected void requiresNoGoogleAccount() {
        if (hasRegisteredGoogleAccount()) {
            throw new AccountException("This method requires no Google account be registered.");
        }
    }

    protected boolean hasRegisteredGoogleAccount() {
        Context context = mInstrumentation.getContext();
        Account[] accounts = AccountManager.get(context).getAccounts();
        for (int i = 0; i < accounts.length; ++i) {
            if (accounts[i].type.equals("com.google")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean captureScreenshot(String name) throws IOException {
        File scrOut = File.createTempFile(name, ".png", getScreenshotDirectory());
        File uixOut = File.createTempFile(name, ".uix", getScreenshotDirectory());
        mDevice.dumpWindowHierarchy(uixOut);
        return mDevice.takeScreenshot(scrOut);
    }

    private static File getScreenshotDirectory() {
        if (sScreenshotDirectory == null) {
            File storage = Environment.getExternalStorageDirectory();
            sScreenshotDirectory = new File(storage, SCREENSHOT_DIR);
            if (!sScreenshotDirectory.exists()) {
                if (!sScreenshotDirectory.mkdirs()) {
                    throw new TestHelperException("Failed to create a screenshot directory.");
                }
            }
        }
        return sScreenshotDirectory;
    }

    /** {@inheritDoc} */
    @Override
    public boolean sendTextEvents(String text, long delay) {
        Log.v(LOG_TAG, String.format("Sending text events for %s", text));
        KeyEvent[] events = mKeyCharacterMap.getEvents(text.toCharArray());
        for (KeyEvent event : events) {
            if (KeyEvent.ACTION_DOWN == event.getAction()) {
                if (!mDevice.pressKeyCode(event.getKeyCode(), event.getMetaState())) {
                    return false;
                }
                SystemClock.sleep(delay);
            }
        }
        return true;
    }

    protected UiObject2 findElementById(String id) {
        UiObject2 element = mDevice.findObject(By.res(getPackage(), id));
        if (element != null) {
            return element;
        } else {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with id", id, getPackage()));
        }
    }

    protected UiObject2 findElementByText(String text) {
        UiObject2 element = mDevice.findObject(By.text(text));
        if (element != null) {
            return element;
        } else {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with text", text, getPackage()));
        }
    }

    protected UiObject2 findElementByDescription(String description) {
        UiObject2 element = mDevice.findObject(By.desc(description));
        if (element != null) {
            return element;
        } else {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with description", description, getPackage()));
        }
    }

    protected void clickOn(UiObject2 element) {
        if (element != null) {
            element.click();
        } else {
            throw new UnknownUiException(String.format(ERROR_NOT_FOUND, "", "", getPackage()));
        }
    }

    /** Returns a UI object after waiting for it, and fails if not found. */
    public static UiObject2 waitForObject(UiDevice device, BySelector selector) {
        final UiObject2 object = device.wait(Until.findObject(selector), WAIT_TIME_MS);
        if (object == null) throw new UnknownUiException("Can't find object " + selector);
        return object;
    }

    protected void waitAndClickById(String packageStr, String id, long timeout) {
        clickOn(mDevice.wait(Until.findObject(By.res(packageStr, id)), timeout));
    }

    protected void waitAndClickByText(String text, long timeout) {
        clickOn(mDevice.wait(Until.findObject(By.text(text)), timeout));
    }

    protected void waitAndClickByDescription(String description, long timeout) {
        clickOn(mDevice.wait(Until.findObject(By.desc(description)), timeout));
    }

    protected void checkElementWithIdExists(String packageStr, String id, long timeout) {
        if (!mDevice.wait(Until.hasObject(By.res(packageStr, id)), timeout)) {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with id", id, getPackage()));
        }
    }

    protected void checkElementWithTextExists(String text, long timeout) {
        if (!mDevice.wait(Until.hasObject(By.text(text)), timeout)) {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with text", text, getPackage()));
        }
    }

    protected void checkElementWithDescriptionExists(String description, long timeout) {
        if (!mDevice.wait(Until.hasObject(By.desc(description)), timeout)) {
            throw new UnknownUiException(
                    String.format(ERROR_NOT_FOUND, "with description", description, getPackage()));
        }
    }

    protected void checkIfElementChecked(UiObject2 element) {
        if (!element.isChecked()) {
            throw new UnknownUiException("Element " + element + " is not checked");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerWatcher(String name, android.support.test.uiautomator.UiWatcher watcher) {}

    /** {@inheritDoc} */
    @Override
    public void registerWatcher(String name, UiWatcher watcher) {
        mDevice.registerWatcher(name, watcher);
    }

    /** {@inheritDoc} */
    @Override
    public void removeWatcher(String name) {
        mDevice.removeWatcher(name);
    }

    private void registerDialogWatchers() {
        registerWatcher(
                AppIsNotRespondingWatcher2.class.getSimpleName(),
                new AppIsNotRespondingWatcher2(InstrumentationRegistry.getInstrumentation()));
    }

    private void removeDialogWatchers() {
        removeWatcher(AppIsNotRespondingWatcher2.class.getSimpleName());
    }

    private ILauncherStrategy getLauncherStrategy() {
        if (mLauncherStrategy == null) {
            mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
        }
        return mLauncherStrategy;
    }

    // Check whether we might need to grant this package notification permission.
    // This would be for packages that meet either of the following criteria:
    //   - build sdk <= sc-v2 and does not have notification permission
    //   - sdk > sc-v2, requests notification permission but does not have notification permission
    private boolean packageNeedsNotificationPermission(PackageManager pm, String pkg) {
        PackageInfo pInfo;
        try {
            pInfo =
                    pm.getPackageInfoAsUser(
                            pkg,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS),
                            ActivityManager.getCurrentUser());
        } catch (NameNotFoundException e) {
            Log.w(LOG_TAG, "package name not found");
            return false;
        }

        if (pInfo.applicationInfo == null) {
            return false;
        }

        boolean hasNotifPerm = pm.checkPermission(NOTIF_PERM, pkg) == PERMISSION_GRANTED;

        // for apps sc-v2 and below, we set notification permission only if they don't already have
        // it
        if (pInfo.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.S_V2) {
            return !hasNotifPerm;
        }

        // check for notification permission in the list of package's requested permissions --
        // apps T and up must request the permission if they're to be granted it
        if (pInfo.requestedPermissions == null) {
            return false;
        }
        boolean requestedNotifPerm = false;
        for (String perm : pInfo.requestedPermissions) {
            if (NOTIF_PERM.equals(perm)) {
                requestedNotifPerm = true;
                break;
            }
        }
        return requestedNotifPerm && !hasNotifPerm;
    }

    private void maybeGrantNotificationPermission() {
        mAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                android.Manifest.permission.INTERACT_ACROSS_USERS);
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        if (packageNeedsNotificationPermission(pm, getPackage())) {
            UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());
            mChangedPermState = true;
            Log.d(
                    LOG_TAG,
                    String.format(
                            "Granting missing notification permission for user: %d",
                            user.getIdentifier()));
            mAutomation.grantRuntimePermission(getPackage(), NOTIF_PERM, user);
            mPreviousFlagValues = pm.getPermissionFlags(NOTIF_PERM, getPackage(), user);
            pm.updatePermissionFlags(
                    NOTIF_PERM,
                    getPackage(),
                    FLAG_PERMISSION_USER_SET | PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                    FLAG_PERMISSION_USER_SET,
                    user);
        }
        mAutomation.dropShellPermissionIdentity();
    }

    private void restoreNotificationPermissionState() {
        if (mChangedPermState) {
            mAutomation.adoptShellPermissionIdentity(
                    android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
                    android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS,
                    android.Manifest.permission.INTERACT_ACROSS_USERS);
            PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
            // ensure permission is still granted, in case it was revoked elsewhere
            if (pm.checkPermission(NOTIF_PERM, getPackage()) == PERMISSION_GRANTED) {
                mAutomation.revokeRuntimePermission(getPackage(), NOTIF_PERM);
                UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());
                pm.updatePermissionFlags(
                        NOTIF_PERM,
                        getPackage(),
                        FLAG_PERMISSION_USER_SET | PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                        mPreviousFlagValues,
                        user);
            }
            mAutomation.dropShellPermissionIdentity();
        }
    }
}

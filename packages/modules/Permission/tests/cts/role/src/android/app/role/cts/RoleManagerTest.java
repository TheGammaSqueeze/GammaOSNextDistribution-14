/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.role.cts;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject;
import static com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.permission.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.FreezeRotationRule;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.compatibility.common.util.UiAutomatorUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Tests {@link RoleManager}.
 */
@RunWith(AndroidJUnit4.class)
public class RoleManagerTest {

    private static final long TIMEOUT_MILLIS = 15 * 1000;

    private static final long UNEXPECTED_TIMEOUT_MILLIS = 1000;

    private static final String ROLE_NAME = RoleManager.ROLE_BROWSER;
    private static final String ROLE_SHORT_LABEL = "Browser app";

    private static final String APP_APK_PATH = "/data/local/tmp/cts-role/CtsRoleTestApp.apk";
    private static final String APP_PACKAGE_NAME = "android.app.role.cts.app";
    private static final String APP_LABEL = "CtsRoleTestApp";
    private static final String APP_IS_ROLE_HELD_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".IsRoleHeldActivity";
    private static final String APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD = APP_PACKAGE_NAME
            + ".extra.IS_ROLE_HELD";
    private static final String APP_REQUEST_ROLE_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".RequestRoleActivity";
    private static final String APP_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".ChangeDefaultDialerActivity";
    private static final String APP_CHANGE_DEFAULT_SMS_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".ChangeDefaultSmsActivity";

    private static final String APP_28_APK_PATH = "/data/local/tmp/cts-role/CtsRoleTestApp28.apk";
    private static final String APP_28_PACKAGE_NAME = "android.app.role.cts.app28";
    private static final String APP_28_LABEL = "CtsRoleTestApp28";
    private static final String APP_28_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME = APP_28_PACKAGE_NAME
            + ".ChangeDefaultDialerActivity";
    private static final String APP_28_CHANGE_DEFAULT_SMS_ACTIVITY_NAME = APP_28_PACKAGE_NAME
            + ".ChangeDefaultSmsActivity";

    private static final String APP_33_WITHOUT_INCALLSERVICE_APK_PATH =
            "/data/local/tmp/cts-role/CtsRoleTestApp33WithoutInCallService.apk";
    private static final String APP_33_WITHOUT_INCALLSERVICE_PACKAGE_NAME =
            "android.app.role.cts.app33WithoutInCallService";

    private static final String PERMISSION_MANAGE_ROLES_FROM_CONTROLLER =
            "com.android.permissioncontroller.permission.MANAGE_ROLES_FROM_CONTROLLER";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final RoleManager sRoleManager = sContext.getSystemService(RoleManager.class);
    private static final boolean sIsAutomotive = sPackageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE);
    private static final boolean sIsTelevision = sPackageManager.hasSystemFeature(
            PackageManager.FEATURE_TELEVISION);
    private static final boolean sIsWatch = sPackageManager.hasSystemFeature(
            PackageManager.FEATURE_WATCH);

    // TODO(b/327528959): consider using resource selectors for Wear too, once the underlying
    // issue is handled.
    private static final BySelector NEGATIVE_BUTTON_SELECTOR =
            sIsWatch ? By.text("Cancel") : By.res("android:id/button2");
    private static final BySelector POSITIVE_BUTTON_SELECTOR =
            sIsWatch ? By.text("Set as default") : By.res("android:id/button1");
    private static final BySelector DONT_ASK_AGAIN_TOGGLE_SELECTOR =
            sIsWatch
                    ? By.text("Don\u2019t ask again")
                    : By.res("com.android.permissioncontroller:id/dont_ask_again");

    @Rule
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();

    @Rule
    public FreezeRotationRule mFreezeRotationRule = new FreezeRotationRule();

    @Rule
    public ActivityTestRule<WaitForResultActivity> mActivityRule =
            new ActivityTestRule<>(WaitForResultActivity.class);

    private String mRoleHolder;

    @Before
    public void saveRoleHolder() throws Exception {
        List<String> roleHolders = getRoleHolders(ROLE_NAME);
        mRoleHolder = !roleHolders.isEmpty() ? roleHolders.get(0) : null;

        if (Objects.equals(mRoleHolder, APP_PACKAGE_NAME)) {
            removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
            mRoleHolder = null;
        }
    }

    @After
    public void restoreRoleHolder() throws Exception {
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        if (mRoleHolder != null) {
            addRoleHolder(ROLE_NAME, mRoleHolder);
        }

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Before
    public void installApp() throws Exception {
        installPackage(APP_APK_PATH);
        installPackage(APP_28_APK_PATH);
        installPackage(APP_33_WITHOUT_INCALLSERVICE_APK_PATH);
    }

    @After
    public void uninstallApp() throws Exception {
        uninstallPackage(APP_PACKAGE_NAME);
        uninstallPackage(APP_28_PACKAGE_NAME);
        uninstallPackage(APP_33_WITHOUT_INCALLSERVICE_PACKAGE_NAME);
    }

    @Before
    public void wakeUpScreen() throws IOException {
        runShellCommand(sInstrumentation, "input keyevent KEYCODE_WAKEUP");
    }

    @Before
    public void closeNotificationShade() {
        sContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    @Test
    public void requestRoleIntentHasPermissionControllerPackage() throws Exception {
        Intent intent = sRoleManager.createRequestRoleIntent(ROLE_NAME);

        assertThat(intent.getPackage()).isEqualTo(
                sPackageManager.getPermissionControllerPackageName());
    }

    @Test
    public void requestRoleIntentHasExtraRoleName() throws Exception {
        Intent intent = sRoleManager.createRequestRoleIntent(ROLE_NAME);

        assertThat(intent.getStringExtra(Intent.EXTRA_ROLE_NAME)).isEqualTo(ROLE_NAME);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestRoleAndDenyThenIsNotRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestRoleAndAllowThenIsRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(true);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestRoleAndSelectRestrictedAppThenRestrictedSettingDialog() throws Exception {
        assumeFalse(sIsWatch || sIsAutomotive || sIsTelevision);
        runWithShellPermissionIdentity(
                () -> setEnhancedConfirmationRestrictedAppOpMode(sContext, APP_PACKAGE_NAME,
                        AppOpsManager.MODE_ERRORED));

        requestRole(ROLE_NAME);
        waitFindObject(By.text(APP_LABEL).enabled(false))
                .clickAndWait(Until.newWindow(), TIMEOUT_MILLIS);
        waitFindObject(By.textContains("Restricted setting"), TIMEOUT_MILLIS);
        pressBack();
        respondToRoleRequest(false);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestRoleFirstTimeNoDontAskAgain() throws Exception {
        requestRole(ROLE_NAME);
        UiObject2 dontAskAgainCheck = findDontAskAgainCheck(false);

        assertThat(dontAskAgainCheck).isNull();

        respondToRoleRequest(false);
    }

    @Test
    @FlakyTest
    public void requestRoleAndDenyThenHasDontAskAgain() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        requestRole(ROLE_NAME);
        UiObject2 dontAskAgainCheck = findDontAskAgainCheck();

        assertThat(dontAskAgainCheck).isNotNull();

        respondToRoleRequest(false);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestRoleAndDenyWithDontAskAgainReturnsCanceled() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        requestRole(ROLE_NAME);
        findDontAskAgainCheck().click();
        Pair<Integer, Intent> result = clickButtonAndWaitForResult(true);

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest
    public void requestRoleAndDenyWithDontAskAgainThenDeniedAutomatically() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        requestRole(ROLE_NAME);
        findDontAskAgainCheck().click();
        clickButtonAndWaitForResult(true);

        requestRole(ROLE_NAME);
        Pair<Integer, Intent> result = waitForResult();

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest
    public void requestRoleAndDenyWithDontAskAgainAndClearDataThenShowsUiWithoutDontAskAgain()
            throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        requestRole(ROLE_NAME);
        findDontAskAgainCheck().click();
        clickButtonAndWaitForResult(true);
        // Wait for the RequestRoleActivity inside the test app to be removed from our task so that
        // when the test app is force stopped, our task isn't force finished and our
        // WaitForResultActivity can survive.
        Thread.sleep(5000);

        clearPackageData(APP_PACKAGE_NAME);
        // Wait for the don't ask again to be forgotten.
        Thread.sleep(10000);

        TestUtils.waitUntil("Find and respond to request role UI", () -> {
            requestRole(ROLE_NAME);
            UiObject2 cancelButton = waitFindObjectOrNull(NEGATIVE_BUTTON_SELECTOR);
            if (cancelButton == null) {
                // Dialog not found, try again later.
                return false;
            }
            UiObject2 dontAskAgainCheck = findDontAskAgainCheck(false);

            assertThat(dontAskAgainCheck).isNull();

            respondToRoleRequest(false);
            return true;
        });
    }

    @Test
    @FlakyTest
    public void requestRoleAndDenyWithDontAskAgainAndReinstallThenShowsUiWithoutDontAskAgain()
            throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        requestRole(ROLE_NAME);
        findDontAskAgainCheck().click();
        clickButtonAndWaitForResult(true);
        // Wait for the RequestRoleActivity inside the test app to be removed from our task so that
        // when the test app is uninstalled, our task isn't force finished and our
        // WaitForResultActivity can survive.
        Thread.sleep(5000);

        uninstallPackage(APP_PACKAGE_NAME);
        // Wait for the don't ask again to be forgotten.
        Thread.sleep(10000);
        installPackage(APP_APK_PATH);

        TestUtils.waitUntil("Find and respond to request role UI", () -> {
            requestRole(ROLE_NAME);
            UiObject2 cancelButton = waitFindObjectOrNull(NEGATIVE_BUTTON_SELECTOR);
            if (cancelButton == null) {
                // Dialog not found, try again later.
                return false;
            }
            UiObject2 dontAskAgainCheck = findDontAskAgainCheck(false);

            assertThat(dontAskAgainCheck).isNull();

            respondToRoleRequest(false);
            return true;
        });
    }

    @Test
    public void requestInvalidRoleThenDeniedAutomatically() throws Exception {
        requestRole("invalid");
        Pair<Integer, Intent> result = waitForResult();

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void requestUnqualifiedRoleThenDeniedAutomatically() throws Exception {
        requestRole(RoleManager.ROLE_HOME);
        Pair<Integer, Intent> result = waitForResult();

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void requestAssistantRoleThenDeniedAutomatically() throws Exception {
        requestRole(RoleManager.ROLE_ASSISTANT);
        Pair<Integer, Intent> result = waitForResult();

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void requestHoldingRoleThenAllowedAutomatically() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(true);

        requestRole(ROLE_NAME);
        Pair<Integer, Intent> result = waitForResult();

        assertThat(result.first).isEqualTo(Activity.RESULT_OK);
    }

    private void requestRole(@NonNull String roleName) {
        Intent intent = new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME, APP_REQUEST_ROLE_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_ROLE_NAME, roleName);
        mActivityRule.getActivity().startActivityToWaitForResult(intent);
    }

    private void respondToRoleRequest(boolean allow)
            throws InterruptedException, UiObjectNotFoundException {
        if (allow) {
            waitFindObject(By.text(APP_LABEL)).click();
        }
        Pair<Integer, Intent> result = clickButtonAndWaitForResult(allow);
        int expectedResult = allow ? Activity.RESULT_OK : Activity.RESULT_CANCELED;

        assertThat(result.first).isEqualTo(expectedResult);
    }

    @Nullable
    private UiObject2 findDontAskAgainCheck(boolean expected) throws UiObjectNotFoundException {
        return expected
                ? waitFindObject(DONT_ASK_AGAIN_TOGGLE_SELECTOR)
                : waitFindObjectOrNull(DONT_ASK_AGAIN_TOGGLE_SELECTOR, UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Nullable
    private UiObject2 findDontAskAgainCheck() throws UiObjectNotFoundException {
        return findDontAskAgainCheck(true);
    }

    @NonNull
    private Pair<Integer, Intent> clickButtonAndWaitForResult(boolean positive)
            throws InterruptedException, UiObjectNotFoundException {
        waitFindObject(positive ? POSITIVE_BUTTON_SELECTOR : NEGATIVE_BUTTON_SELECTOR).click();
        return waitForResult();
    }

    @NonNull
    private Pair<Integer, Intent> waitForResult() throws InterruptedException {
        return mActivityRule.getActivity().waitForActivityResult(TIMEOUT_MILLIS);
    }

    private void clearPackageData(@NonNull String packageName) {
        runShellCommand("pm clear --user " + Process.myUserHandle().getIdentifier() + " "
                + packageName);
    }

    private void installPackage(@NonNull String apkPath) {
        runShellCommandOrThrow(
                "pm install -r --user " + Process.myUserHandle().getIdentifier() + " " + apkPath);
    }

    private void uninstallPackage(@NonNull String packageName) {
        runShellCommand("pm uninstall --user " + Process.myUserHandle().getIdentifier() + " "
                + packageName);
    }

    @Test
    public void targetCurrentSdkAndChangeDefaultDialerThenDeniedAutomatically() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER));

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME,
                        APP_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME));
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void targetCurrentSdkAndChangeDefaultSmsThenDeniedAutomatically() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME,
                        APP_CHANGE_DEFAULT_SMS_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME));
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void targetSdk28AndChangeDefaultDialerAndAllowThenIsDefaultDialer() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER));

        sContext.startActivity(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_28_PACKAGE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFindObject(By.text(APP_28_LABEL)).click();
        waitFindObject(POSITIVE_BUTTON_SELECTOR).click();

        // TODO(b/149037075): Use TelecomManager.getDefaultDialerPackage() once the bug is fixed.
        //TelecomManager telecomManager = sContext.getSystemService(TelecomManager.class);
        //TestUtils.waitUntil("App is not set as default dialer app", () -> Objects.equals(
        //        telecomManager.getDefaultDialerPackage(), APP_28_PACKAGE_NAME));
        TestUtils.waitUntil("App is not set as default dialer app", () ->
                getRoleHolders(RoleManager.ROLE_DIALER).contains(APP_28_PACKAGE_NAME));
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void targetSdk28AndChangeDefaultSmsAndAllowThenIsDefaultSms() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        sContext.startActivity(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_SMS_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_28_PACKAGE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFindObject(By.text(APP_28_LABEL)).click();
        waitFindObject(POSITIVE_BUTTON_SELECTOR).click();

        TestUtils.waitUntil("App is not set as default sms app", () -> Objects.equals(
                Telephony.Sms.getDefaultSmsPackage(sContext), APP_28_PACKAGE_NAME));
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void targetSdk28AndChangeDefaultDialerForAnotherAppThenDeniedAutomatically()
            throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER));

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME));
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void targetSdk28AndChangeDefaultSmsForAnotherAppThenDeniedAutomatically()
            throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_SMS_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME));
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void
    targetSdk28AndChangeDefaultDialerForAnotherAppAsHolderAndAllowThenTheOtherAppIsDefaultDialer()
            throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER));

        addRoleHolder(RoleManager.ROLE_DIALER, APP_28_PACKAGE_NAME);
        sContext.startActivity(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_DIALER_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFindObject(By.text(APP_LABEL)).click();
        waitFindObject(POSITIVE_BUTTON_SELECTOR).click();

        // TODO(b/149037075): Use TelecomManager.getDefaultDialerPackage() once the bug is fixed.
        //TelecomManager telecomManager = sContext.getSystemService(TelecomManager.class);
        //TestUtils.waitUntil("App is not set as default dialer app", () -> Objects.equals(
        //        telecomManager.getDefaultDialerPackage(), APP_PACKAGE_NAME));
        TestUtils.waitUntil("App is not set as default dialer app", () ->
                getRoleHolders(RoleManager.ROLE_DIALER).contains(APP_PACKAGE_NAME));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    public void testHoldDialerRoleRequirementWithInCallServiceAndSdk()
            throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER));
        // target below sdk 33 without InCallService component can hold dialer role
        addRoleHolder(
                RoleManager.ROLE_DIALER, APP_28_PACKAGE_NAME, true);
        assertIsRoleHolder(
                RoleManager.ROLE_DIALER, APP_28_PACKAGE_NAME, true);
        // target sdk 33 without InCallService component cannot hold dialer role
        addRoleHolder(
                RoleManager.ROLE_DIALER, APP_33_WITHOUT_INCALLSERVICE_PACKAGE_NAME, false);
        assertIsRoleHolder(
                RoleManager.ROLE_DIALER, APP_33_WITHOUT_INCALLSERVICE_PACKAGE_NAME, false);
        // target sdk 33 with InCallService component can hold dialer role
        addRoleHolder(
                RoleManager.ROLE_DIALER, APP_PACKAGE_NAME, true);
        assertIsRoleHolder(
                RoleManager.ROLE_DIALER, APP_PACKAGE_NAME, true);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void
    targetSdk28AndChangeDefaultSmsForAnotherAppAsHolderAndAllowThenTheOtherAppIsDefaultSms()
            throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        addRoleHolder(RoleManager.ROLE_SMS, APP_28_PACKAGE_NAME);
        sContext.startActivity(new Intent()
                .setComponent(new ComponentName(APP_28_PACKAGE_NAME,
                        APP_28_CHANGE_DEFAULT_SMS_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFindObject(By.text(APP_LABEL)).click();
        waitFindObject(POSITIVE_BUTTON_SELECTOR).click();

        TestUtils.waitUntil("App is not set as default sms app", () -> Objects.equals(
                Telephony.Sms.getDefaultSmsPackage(sContext), APP_PACKAGE_NAME));
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppDetailsThenIsNotDefaultApp() throws Exception {
        runWithShellPermissionIdentity(() -> sContext.startActivity(new Intent(
                Intent.ACTION_MANAGE_DEFAULT_APP)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Intent.EXTRA_ROLE_NAME, ROLE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK)));

        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL)));
        }

        pressBack();
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_MODE_APIS_ENABLED)
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppDetailsOnHandHeldThenRestrictedAppIsNotSelectableAsDefaultApp()
            throws Exception {
        assumeFalse(sIsWatch || sIsAutomotive || sIsTelevision);
        runWithShellPermissionIdentity(
                () -> setEnhancedConfirmationRestrictedAppOpMode(sContext, APP_PACKAGE_NAME,
                        AppOpsManager.MODE_ERRORED));

        runWithShellPermissionIdentity(() -> sContext.startActivity(new Intent(
                Intent.ACTION_MANAGE_DEFAULT_APP)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Intent.EXTRA_ROLE_NAME, ROLE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK)));

        waitFindObject(By.text(APP_LABEL).enabled(false)).clickAndWait(Until.newWindow(),
                TIMEOUT_MILLIS);

        waitFindObject(By.textContains("Restricted setting"), TIMEOUT_MILLIS);
        pressBack();

        pressBack();
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppDetailsAndSetDefaultAppThenIsDefaultApp() throws Exception {
        runWithShellPermissionIdentity(() -> sContext.startActivity(new Intent(
                Intent.ACTION_MANAGE_DEFAULT_APP)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Intent.EXTRA_ROLE_NAME, ROLE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        waitForIdle();
        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(
                    By.text(APP_LABEL))).click();
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL))).click();
        }

        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(true).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(true))
                    .hasDescendant(By.text(APP_LABEL)));
        }
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);

        pressBack();
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppDetailsAndSetDefaultAppAndSetAnotherThenIsNotDefaultApp()
            throws Exception {
        runWithShellPermissionIdentity(() -> sContext.startActivity(new Intent(
                Intent.ACTION_MANAGE_DEFAULT_APP)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Intent.EXTRA_ROLE_NAME, ROLE_NAME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        waitForIdle();
        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(
                    By.text(APP_LABEL))).click();
            waitFindObject(By.clickable(true).checked(true).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL))).click();
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(true))
                    .hasDescendant(By.text(APP_LABEL)));
        }
        waitForIdle();
        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false)).click();
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).enabled(true)
                    .checked(false))).click();
        }

        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL)));
        }
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);

        pressBack();
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppListThenHasDefaultApp() throws Exception {
        sContext.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        waitFindObject(By.text(ROLE_SHORT_LABEL));

        pressBack();
    }

    @Test
    public void openDefaultAppListThenIsNotDefaultAppInList() throws Exception {
        sContext.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        assertThat(waitFindObjectOrNull(By.text(APP_LABEL), UNEXPECTED_TIMEOUT_MILLIS))
                .isNull();

        pressBack();
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppListAndSetDefaultAppThenIsDefaultApp() throws Exception {
        sContext.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdle();
        waitFindObject(By.text(ROLE_SHORT_LABEL)).click();
        waitForIdle();
        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(
                    By.text(APP_LABEL))).click();
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL))).click();
        }

        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(true).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(true))
                    .hasDescendant(By.text(APP_LABEL)));
        }
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);

        pressBack();
        pressBack();
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void openDefaultAppListAndSetDefaultAppThenIsDefaultAppInList() throws Exception {
        sContext.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdle();
        waitFindObject(By.text(ROLE_SHORT_LABEL)).click();
        waitForIdle();
        if (sIsWatch) {
            waitFindObject(By.clickable(true).checked(false).hasDescendant(
                    By.text(APP_LABEL))).click();
            waitFindObject(By.clickable(true).checked(true).hasDescendant(By.text(APP_LABEL)));
        } else {
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(false))
                    .hasDescendant(By.text(APP_LABEL))).click();
            waitFindObject(By.clickable(true).hasDescendant(By.checkable(true).checked(true))
                    .hasDescendant(By.text(APP_LABEL)));
        }
        pressBack();

        waitFindObject(By.text(APP_LABEL));

        pressBack();
    }

    private void setEnhancedConfirmationRestrictedAppOpMode(@NonNull Context context,
            @NonNull String packageName, int mode)
            throws PackageManager.NameNotFoundException {
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS,
                context.getPackageManager().getApplicationInfo(packageName, 0).uid,
                packageName, mode);
    }

    private static void waitForIdle() {
        UiAutomatorUtils.getUiDevice().waitForIdle();
    }

    private static void pressBack() {
        UiAutomatorUtils.getUiDevice().pressBack();
        waitForIdle();
    }

    @Test
    public void roleIsAvailable() {
        assertThat(sRoleManager.isRoleAvailable(ROLE_NAME)).isTrue();
    }

    @Test
    public void dontAddRoleHolderThenRoleIsNotHeld() throws Exception {
        assertRoleIsHeld(ROLE_NAME, false);
    }

    @Test
    public void addRoleHolderThenRoleIsHeld() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertRoleIsHeld(ROLE_NAME, true);
    }

    @Test
    public void addAndRemoveRoleHolderThenRoleIsNotHeld() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertRoleIsHeld(ROLE_NAME, false);
    }

    private void assertRoleIsHeld(@NonNull String roleName, boolean isHeld)
            throws InterruptedException {
        Intent intent = new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME, APP_IS_ROLE_HELD_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_ROLE_NAME, roleName);
        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(intent);
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_OK);
        assertThat(result.second).isNotNull();
        assertThat(result.second.hasExtra(APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD)).isTrue();
        assertThat(result.second.getBooleanExtra(APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD, false))
                .isEqualTo(isHeld);
    }

    @Test
    public void dontAddRoleHolderThenIsNotRoleHolder() throws Exception {
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addRoleHolderThenIsRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);
    }

    @Test
    public void addAndRemoveRoleHolderThenIsNotRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addAndClearRoleHoldersThenIsNotRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        clearRoleHolders(ROLE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addInvalidRoleHolderThenFails() throws Exception {
        addRoleHolder("invalid", APP_PACKAGE_NAME, false);
    }

    @Test
    public void addUnqualifiedRoleHolderThenFails() throws Exception {
        addRoleHolder(RoleManager.ROLE_HOME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void removeInvalidRoleHolderThenFails() throws Exception {
        removeRoleHolder("invalid", APP_PACKAGE_NAME, false);
    }

    @Test
    public void clearInvalidRoleHoldersThenFails() throws Exception {
        clearRoleHolders("invalid", false);
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndAddRoleHolderThenIsNotified() throws Exception {
        assertOnRoleHoldersChangedListenerIsNotified(() -> addRoleHolder(ROLE_NAME,
                APP_PACKAGE_NAME));
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndRemoveRoleHolderThenIsNotified()
            throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertOnRoleHoldersChangedListenerIsNotified(() -> removeRoleHolder(ROLE_NAME,
                APP_PACKAGE_NAME));
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndClearRoleHoldersThenIsNotified()
            throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertOnRoleHoldersChangedListenerIsNotified(() -> clearRoleHolders(ROLE_NAME));
    }

    private void assertOnRoleHoldersChangedListenerIsNotified(@NonNull ThrowingRunnable runnable)
            throws Exception {
        ListenerFuture future = new ListenerFuture();
        UserHandle user = Process.myUserHandle();
        runWithShellPermissionIdentity(() -> sRoleManager.addOnRoleHoldersChangedListenerAsUser(
                sContext.getMainExecutor(), future, user));
        Pair<String, UserHandle> result;
        try {
            runnable.run();
            result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            runWithShellPermissionIdentity(() ->
                    sRoleManager.removeOnRoleHoldersChangedListenerAsUser(future, user));
        }

        assertThat(result.first).isEqualTo(ROLE_NAME);
        assertThat(result.second).isEqualTo(user);
    }

    @Test
    public void addAndRemoveOnRoleHoldersChangedListenerAndAddRoleHolderThenIsNotNotified()
            throws Exception {
        ListenerFuture future = new ListenerFuture();
        UserHandle user = Process.myUserHandle();
        runWithShellPermissionIdentity(() -> {
            sRoleManager.addOnRoleHoldersChangedListenerAsUser(sContext.getMainExecutor(), future,
                    user);
            sRoleManager.removeOnRoleHoldersChangedListenerAsUser(future, user);
        });
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        try {
            future.get(UNEXPECTED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Expected
            return;
        }
        throw new AssertionError("OnRoleHoldersChangedListener was notified after removal");
    }

    @Test
    public void setRoleNamesFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.setRoleNamesFromController(Collections.emptyList()),
                "setRoleNamesFromController");
    }

    @Test
    public void addRoleHolderFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.addRoleHolderFromController(ROLE_NAME, APP_PACKAGE_NAME),
                "addRoleHolderFromController");
    }

    @Test
    public void removeRoleHolderFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.removeRoleHolderFromController(ROLE_NAME, APP_PACKAGE_NAME),
                "removeRoleHolderFromController");
    }

    @Test
    public void getHeldRolesFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.getHeldRolesFromController(APP_PACKAGE_NAME),
                "getHeldRolesFromController");
    }

    private void assertRequiresManageRolesFromControllerPermission(@NonNull Runnable runnable,
            @NonNull String methodName) {
        try {
            runnable.run();
        } catch (SecurityException e) {
            if (e.getMessage().contains(PERMISSION_MANAGE_ROLES_FROM_CONTROLLER)) {
                // Expected
                return;
            }
            throw e;
        }
        fail("RoleManager." + methodName + "() should require "
                + PERMISSION_MANAGE_ROLES_FROM_CONTROLLER);
    }

    @Test
    public void manageRolesFromControllerPermissionShouldBeDeclaredByPermissionController()
            throws PackageManager.NameNotFoundException {
        PermissionInfo permissionInfo = sPackageManager.getPermissionInfo(
                PERMISSION_MANAGE_ROLES_FROM_CONTROLLER, 0);

        assertThat(permissionInfo.packageName).isEqualTo(
                sPackageManager.getPermissionControllerPackageName());
        assertThat(permissionInfo.getProtection()).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
        assertThat(permissionInfo.getProtectionFlags()).isEqualTo(0);
    }

    @Test
    public void smsRoleHasHolder() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        assertThat(getRoleHolders(RoleManager.ROLE_SMS)).isNotEmpty();
    }

    @Test
    public void addSmsRoleHolderThenPermissionIsGranted() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);

        assertThat(sPackageManager.checkPermission(android.Manifest.permission.SEND_SMS,
                APP_PACKAGE_NAME)).isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void removeSmsRoleHolderThenPermissionIsRevoked() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        String smsRoleHolder = getRoleHolders(RoleManager.ROLE_SMS).get(0);
        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        addRoleHolder(RoleManager.ROLE_SMS, smsRoleHolder);

        assertThat(sPackageManager.checkPermission(android.Manifest.permission.SEND_SMS,
                APP_PACKAGE_NAME)).isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void removeSmsRoleHolderThenDialerRolePermissionIsRetained() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
                && sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        addRoleHolder(RoleManager.ROLE_DIALER, APP_PACKAGE_NAME);
        String smsRoleHolder = getRoleHolders(RoleManager.ROLE_SMS).get(0);
        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        addRoleHolder(RoleManager.ROLE_SMS, smsRoleHolder);

        assertThat(sPackageManager.checkPermission(android.Manifest.permission.SEND_SMS,
                APP_PACKAGE_NAME)).isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void packageManagerGetDefaultBrowserBackedByRole() throws Exception {
        addRoleHolder(RoleManager.ROLE_BROWSER, APP_PACKAGE_NAME);

        assertThat(sPackageManager.getDefaultBrowserPackageNameAsUser(UserHandle.myUserId()))
                .isEqualTo(APP_PACKAGE_NAME);
    }

    @Test
    @FlakyTest(bugId = 288468003, detail = "CtsRoleTestCases is breaching 20min SLO")
    public void packageManagerSetDefaultBrowserBackedByRole() throws Exception {
        callWithShellPermissionIdentity(() -> sPackageManager.setDefaultBrowserPackageNameAsUser(
                APP_PACKAGE_NAME, UserHandle.myUserId()));

        assertIsRoleHolder(RoleManager.ROLE_BROWSER, APP_PACKAGE_NAME, true);
    }

    @Test
    public void telephonySmsGetDefaultSmsPackageBackedByRole() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);

        assertThat(Telephony.Sms.getDefaultSmsPackage(sContext)).isEqualTo(APP_PACKAGE_NAME);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_EMERGENCY_ROLE_HOLDER_API_ENABLED)
    public void telephonyManagerGetEmergencyAssistancePackageNameBackedByRole() throws Exception {
        TelephonyManager telephonyManager = sContext.getSystemService(TelephonyManager.class);
        List<String> emergencyRoleHolders = getRoleHolders(RoleManager.ROLE_EMERGENCY);

        if (telephonyManager.isVoiceCapable()
                && callWithShellPermissionIdentity(() ->
                telephonyManager.isEmergencyAssistanceEnabled())) {
            String emergencyAssistancePackageName = callWithShellPermissionIdentity(() ->
                    telephonyManager.getEmergencyAssistancePackageName());
            assertThat(emergencyRoleHolders).hasSize(1);
            assertThat(emergencyAssistancePackageName).isEqualTo(emergencyRoleHolders.get(0));
        } else {
            assertThrows(IllegalStateException.class, () ->
                    callWithShellPermissionIdentity(() ->
                            telephonyManager.getEmergencyAssistancePackageName()));
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
    @Test
    public void cannotBypassRoleQualificationWithoutPermission() throws Exception {
        assertThrows(SecurityException.class, () ->
                sRoleManager.setBypassingRoleQualification(true));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
    @Test
    public void bypassRoleQualificationThenCanAddUnqualifiedRoleHolder() throws Exception {
        assertThat(sRoleManager.isRoleAvailable(RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER))
                .isTrue();

        runWithShellPermissionIdentity(() -> sRoleManager.setBypassingRoleQualification(true));
        try {
            assertThat(callWithShellPermissionIdentity(() ->
                    sRoleManager.isBypassingRoleQualification())).isTrue();

            // The System Activity Recognizer role requires a system app, so this won't succeed
            // without bypassing role qualification.
            addRoleHolder(RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER, APP_PACKAGE_NAME);

            assertThat(getRoleHolders(RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER))
                    .contains(APP_PACKAGE_NAME);
        } finally {
            runWithShellPermissionIdentity(() -> sRoleManager.setBypassingRoleQualification(false));
        }
        assertThat(callWithShellPermissionIdentity(() ->
                sRoleManager.isBypassingRoleQualification())).isFalse();
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void cannotGetDefaultApplicationWithoutPermission() throws Exception {
        assertThrows(SecurityException.class, ()->
                sRoleManager.getDefaultApplication(
                        RoleManager.ROLE_SMS));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void getDefaultApplicationChecksRoles() throws Exception {
        runWithShellPermissionIdentity(() ->
                assertThrows(IllegalArgumentException.class, () ->
                        sRoleManager.getDefaultApplication(
                                RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER)));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void getDefaultApplicationReadsRole() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        runWithShellPermissionIdentity(() -> {
            assertThat(sRoleManager.getDefaultApplication(RoleManager.ROLE_SMS))
                    .isEqualTo(APP_PACKAGE_NAME);
        });
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void cannotSetDefaultApplicationWithoutPermission() throws Exception {
        CallbackFuture future = new CallbackFuture();
        assertThrows(SecurityException.class, ()->
                sRoleManager.setDefaultApplication(
                        RoleManager.ROLE_SMS, APP_PACKAGE_NAME, 0,
                        sContext.getMainExecutor(), future));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void setDefaultApplicationChecksRoles() throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() ->
                assertThrows(IllegalArgumentException.class, () ->
                          sRoleManager.setDefaultApplication(
                                RoleManager.ROLE_SYSTEM_ACTIVITY_RECOGNIZER, APP_PACKAGE_NAME, 0,
                                sContext.getMainExecutor(), future)));
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test
    public void setDefaultApplicationSetsRole() throws Exception {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> {
            sRoleManager.setDefaultApplication(
                    RoleManager.ROLE_SMS, APP_PACKAGE_NAME, 0,
                    sContext.getMainExecutor(), future);
            assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(sRoleManager.getRoleHolders(RoleManager.ROLE_SMS))
                    .containsExactly(APP_PACKAGE_NAME);
        });
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testSetAndGetRoleFallbackEnabled() {
        assumeTrue(sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS));

        runWithShellPermissionIdentity(() -> {
            sRoleManager.setRoleFallbackEnabled(RoleManager.ROLE_SMS, true);
            assertThat(sRoleManager.isRoleFallbackEnabled(RoleManager.ROLE_SMS)).isTrue();
        });
    }

    @NonNull
    private List<String> getRoleHolders(@NonNull String roleName) throws Exception {
        return callWithShellPermissionIdentity(() -> sRoleManager.getRoleHolders(roleName));
    }

    private void assertIsRoleHolder(@NonNull String roleName, @NonNull String packageName,
            boolean shouldBeRoleHolder) throws Exception {
        List<String> packageNames = getRoleHolders(roleName);

        if (shouldBeRoleHolder) {
            assertThat(packageNames).contains(packageName);
        } else {
            assertThat(packageNames).doesNotContain(packageName);
        }
     }

    private void addRoleHolder(@NonNull String roleName, @NonNull String packageName,
            boolean expectSuccess) throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.addRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), sContext.getMainExecutor(), future));
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess);
    }

    private void addRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        addRoleHolder(roleName, packageName, true);
    }

    private void removeRoleHolder(@NonNull String roleName, @NonNull String packageName,
            boolean expectSuccess) throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.removeRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), sContext.getMainExecutor(), future));
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess);
    }

    private void removeRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        removeRoleHolder(roleName, packageName, true);
    }

    private void clearRoleHolders(@NonNull String roleName, boolean expectSuccess)
            throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.clearRoleHoldersAsUser(roleName, 0,
                Process.myUserHandle(), sContext.getMainExecutor(), future));
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess);
    }

    private void clearRoleHolders(@NonNull String roleName) throws Exception {
        clearRoleHolders(roleName, true);
    }

    private static class ListenerFuture extends CompletableFuture<Pair<String, UserHandle>>
            implements OnRoleHoldersChangedListener {

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            complete(new Pair<>(roleName, user));
        }
    }

    private static class CallbackFuture extends CompletableFuture<Boolean>
            implements Consumer<Boolean> {

        @Override
        public void accept(Boolean successful) {
            complete(successful);
        }
    }
}

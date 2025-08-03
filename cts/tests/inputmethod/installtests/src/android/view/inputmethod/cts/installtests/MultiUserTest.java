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

package android.view.inputmethod.cts.installtests;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.InstantAppInfo;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.Ime2Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.SecureSettingsUtils;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.CommonPackages;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RequireFeature(CommonPackages.FEATURE_INPUT_METHODS)
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public class MultiUserTest {
    private static final String TAG = "MultiUserTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    public ErrorCollector mErrorCollector = new ErrorCollector();

    private Context mContext;
    private InputMethodManager mImm;
    private boolean mNeedsTearDown = false;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mImm = mContext.getSystemService(InputMethodManager.class);

        mNeedsTearDown = true;
    }

    @After
    public void tearDown() {
        if (!mNeedsTearDown) {
            return;
        }

        TestApis.packages().find(Ime1Constants.PACKAGE).uninstallFromAllUsers();
        TestApis.packages().find(Ime2Constants.PACKAGE).uninstallFromAllUsers();

        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());

        runShellCommandOrThrow(ShellCommandUtils.wakeUp());
        runShellCommandOrThrow(ShellCommandUtils.dismissKeyguard());
        runShellCommandOrThrow(ShellCommandUtils.closeSystemDialog());
    }

    /**
     * Make sure that InputMethodManagerService automatically updates its internal IME list upon IME
     * APK installation
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMM_USERHANDLE_HOSTSIDETESTS)
    @EnsureHasSecondaryUser
    public void testSecondaryUser() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference secondaryUser = sDeviceState.secondaryUser();
        final int currentUserId = currentUser.id();
        final int secondaryUserId = secondaryUser.id();

        assertTrue(secondaryUser.isRunning());

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(secondaryUserId);

        TestApis.packages().install(secondaryUser, new File(Ime1Constants.APK_PATH));

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, secondaryUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, secondaryUserId));
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);

        secondaryUser.switchTo();

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);

        currentUser.switchTo();

        // For devices that have config_multiuserDelayUserDataLocking set to true, the
        // secondaryUserId will be stopped after switching to the currentUserId. This means that
        // the InputMethodManager can no longer query for the Input Method Services since they have
        // all been stopped.
        secondaryUser.start();

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);
    }

    /**
     * Make sure that InputMethodManagerService automatically updates its internal IME list upon IME
     * APK installation
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMM_USERHANDLE_HOSTSIDETESTS)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile
    public void testProfileUser() throws Exception {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference profileUser = sDeviceState.workProfile(currentUser);
        final int currentUserId = currentUser.id();
        final int profileUserId = profileUser.id();

        assertTrue(profileUser.isRunning());

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);

        runShellCommandOrThrow(ShellCommandUtils.waitForBroadcastBarrier());

        // Install IME1 then enable/set it as the current IME for the main user.
        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        assertImeExistsInApiResult(Ime1Constants.IME_ID, currentUserId);
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, currentUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, currentUserId));

        // Install IME2 then enable/set it as the current IME for the profile user.
        TestApis.packages().install(profileUser, new File(Ime2Constants.APK_PATH));
        assertImeExistsInApiResult(Ime2Constants.IME_ID, profileUserId);
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime2Constants.IME_ID, profileUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime2Constants.IME_ID, profileUserId));

        // Main User: IME1:enabled, IME2:N/A
        assertImeExistsInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeEnabledInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime2Constants.IME_ID, currentUserId);
        assertImeNotEnabledInApiResult(Ime2Constants.IME_ID, currentUserId);
        assertImeSelected(Ime1Constants.IME_ID, currentUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);

        // Profile User: IME1:N/A, IME2:enabled
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertImeNotEnabledInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertImeExistsInApiResult(Ime2Constants.IME_ID, profileUserId);
        assertImeEnabledInApiResult(Ime2Constants.IME_ID, profileUserId);
        assertImeSelected(Ime2Constants.IME_ID, profileUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, profileUserId);
        assertImeInCurrentInputMethodInfo(Ime2Constants.IME_ID, profileUserId);
        // Check isStylusHandwritingAvailable() for profile user.
        assertIsStylusHandwritingAvailable(profileUserId, currentUserId);

        // Install Test App for the profile user and make sure it is available as it is used next.
        final Package testActivityPackage =
                TestApis.packages().find(MockTestActivityUtil.TEST_ACTIVITY.getPackageName());
        assertTrue(testActivityPackage.exists());
        testActivityPackage.installExisting(profileUser);
        assertTrue(testActivityPackage.installedOnUser(profileUser));

        // Make sure that IME switches depending on the target user.
        assertConnectingToTheSameUserIme(currentUserId);
        assertConnectingToTheSameUserIme(profileUserId);
        assertConnectingToTheSameUserIme(currentUserId);

        assertIme1ImplicitlyEnabledSubtypeExists(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);

        assertImeExistsInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, profileUserId);
    }

    private void assertImeExistsInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must exist for user " + userId, TIMEOUT,
                        () -> mImm.getInputMethodListAsUser(userId).stream().anyMatch(
                                imi -> TextUtils.equals(imi.getId(), imeId))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotExistInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> assertFalse(mImm.getInputMethodListAsUser(
                        userId).stream().anyMatch(imi -> TextUtils.equals(imi.getId(), imeId))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertIme1ImplicitlyEnabledSubtypeExists(int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                PollingCheck.check(
                        "Implicitly enabled Subtype must exist for Ime " + Ime1Constants.IME_ID,
                        TIMEOUT, () -> mImm.getInputMethodListAsUser(userId).stream().filter(
                                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)).flatMap(
                                    imi -> mImm.getEnabledInputMethodSubtypeListAsUser(imi.getId(),
                                        true, UserHandle.of(userId)).stream()).anyMatch(
                                InputMethodSubtype::overridesImplicitlyEnabledSubtype));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertIme1ImplicitlyEnabledSubtypeNotExist(int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> assertFalse(mImm.getInputMethodListAsUser(
                        userId).stream().filter(
                            imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)).flatMap(
                                imi -> mImm.getEnabledInputMethodSubtypeListAsUser(imi.getId(),
                                                true, UserHandle.of(userId))
                                        .stream()).anyMatch(
                                            InputMethodSubtype::overridesImplicitlyEnabledSubtype)),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeInCurrentInputMethodInfo(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> PollingCheck.check(
                String.format("Ime %s must be the current IME. Found %s", imeId,
                        mImm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId()),
                TIMEOUT, () -> TextUtils.equals(
                        mImm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId(),
                        imeId)), Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotCurrentInputMethodInfo(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must not be the current IME.", TIMEOUT,
                        () -> {
                            final InputMethodInfo info = mImm.getCurrentInputMethodInfoAsUser(
                                    UserHandle.of(userId));
                            if (info == null) {
                                return true;
                            }
                            return !TextUtils.equals(info.getId(), imeId);
                        }), Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeEnabledInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                PollingCheck.check("Ime " + imeId + " must be enabled.", TIMEOUT,
                        () -> mImm.getEnabledInputMethodListAsUser(
                                UserHandle.of(userId)).stream().anyMatch(
                                    imi -> TextUtils.equals(imi.getId(), imeId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotEnabledInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                assertFalse(mImm.getEnabledInputMethodListAsUser(
                        UserHandle.of(userId)).stream().anyMatch(
                            imi -> TextUtils.equals(imi.getId(), imeId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeSelected(String imeId, int userId) {
        assertEquals(imeId, SecureSettingsUtils.getString(
                mContext, Settings.Secure.DEFAULT_INPUT_METHOD, userId));
    }

    private void assertIsStylusHandwritingAvailable(int profileUserId, int currentUserId) {
        // Turn stylus handwriting pref ON for current user and OFF for profile user.
        SecureSettingsUtils.putInt(mContext,
                Settings.Secure.STYLUS_HANDWRITING_ENABLED, 1, currentUserId);
        SecureSettingsUtils.putInt(mContext,
                Settings.Secure.STYLUS_HANDWRITING_ENABLED, 0, profileUserId);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                // Stylus pref should still be picked from parent profile i.e. default true.
                PollingCheck.check(
                        "Handwriting should be enabled on profile user as primary user has it "
                                + "enabled",
                        TIMEOUT, () -> mImm.isStylusHandwritingAvailableAsUser(
                                UserHandle.of(profileUserId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * check if the app is connecting to the IME that runs under the same user ID.
     */
    public void assertConnectingToTheSameUserIme(int userId) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback onCreateInputConnectionCallback = new RemoteCallback(bundle -> {
            Log.i(TAG, "RemoteCallback was invoked for user #" + bundle.getInt(
                    MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE));
            mErrorCollector.checkThat(userId,
                    equalTo(bundle.getInt(MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE)));
            latch.countDown();
        });
        try (AutoCloseable ignored = MockTestActivityUtil.launchSyncAsUser(userId, isInstantApp(),
                null, onCreateInputConnectionCallback)) {
            if (!latch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                fail(String.format("IME not connected to the same user #%s within timeout",
                        userId));
            }
        }
    }

    // TODO(b/282196632): remove this method once b/282196632) is fixed
    private boolean isMultiUserMultiDisplayIme() {
        String result = runShellCommandOrThrow("dumpsys input_method");
        return result.contains("InputMethodManagerServiceProxy");
    }

    private boolean isInstantApp() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            // as this test app itself is always running as a full app, we can check if the
            // CtsInputMethodStandaloneTestApp was installed as an instant app
            boolean instant = false;
            Optional<InstantAppInfo> instantAppInfo =
                    mContext.getPackageManager().getInstantApps().stream()
                            .filter(packageInfo -> TextUtils.equals(packageInfo.getPackageName(),
                                    MockTestActivityUtil.TEST_ACTIVITY.getPackageName()))
                            .findFirst();
            if (instantAppInfo.isPresent()
                    && instantAppInfo.get().getApplicationInfo().isInstantApp()) {
                instant = true;
            }
            return instant;
        }, Manifest.permission.ACCESS_INSTANT_APPS);
    }
}

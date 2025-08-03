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

package android.multiuser.cts;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.TestingUtils.sContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.Collection;

/**
 * Tests for user-related APIs that are only available on devices that
 * {@link UserManager#isVisibleBackgroundUsersSupported() support visible background users} (such
 * as cars with passenger displays), whether or not such users can be started on default display.
 *
 * <p>NOTE: subclasses still need to explicitly add the
 * {@link com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers}
 * annotation, as {@code DeviceState} doesn't scan annotations from superclasses (TODO(b/266571920):
 * remove this comment and annotations from subclasses if that's changed).
 */
@AppModeFull(reason = "it's testing user features, not related to apps")
abstract class UserVisibilityVisibleBackgroundUsersTestCase extends UserVisibilityTestCase {

    private static final String TAG = UserVisibilityVisibleBackgroundUsersTestCase.class
            .getSimpleName();

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnInitialUser
    public final void testIsUserVisible_backgroundUserOnSecondaryDisplay() throws Exception {
        runTestOnSecondaryDisplay((user, displayId, instance) ->
                assertWithMessage("isUserVisible() for background user (id=%s) on display %s",
                    user.id(), displayId)
                            .that(instance.userManager().isUserVisible()).isTrue());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireHeadlessSystemUserMode(reason = "non-HSUM cannot have profiles on secondary users")
    @RequireRunOnSecondaryUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserVisible() on other context
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public final void testIsUserVisible_stoppedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {

        runTestOnSecondaryDisplay((user, profile, displayId, instance) ->
                assertWithMessage("isUserVisible() for stoppedprofile (id=%s) of bg user (id=%s) "
                + "on display %s", profile.id(), user.id(), displayId)
                            .that(instance.userManager().isUserVisible()).isFalse()
        );
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireRunOnInitialUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public final void testGetVisibleUsers_backgroundUserOnSecondaryDisplay() throws Exception {
        runTestOnSecondaryDisplay((user, displayId, instance) ->
                assertWithMessage("getVisibleUsers()")
                        .that(mUserManager.getVisibleUsers())
                        .containsAtLeast(TestApis.users().current().userHandle(),
                                user.userHandle()));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireHeadlessSystemUserMode(reason = "non-HSUM cannot have profiles on secondary users")
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public final void testGetVisibleUsers_stoppedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        runTestOnSecondaryDisplay((user, profile, display, instance) -> {
            Collection<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
            Log.d(TAG, "Visible users: " + visibleUsers);

            assertWithMessage("getVisibleUsers()").that(visibleUsers)
                    .containsAtLeast(TestApis.users().current().userHandle(), user.userHandle());
            assertWithMessage("getVisibleUsers()").that(visibleUsers)
                    .doesNotContain(profile.userHandle());
        });
    }

    // TODO(b/240736142): tests below should belong to ActivityManagerTest or similar, but it
    // doesn't use bedstead yet

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireRunOnInitialUser
    @Test
    public final void testStartVisibleBgUser_onSecondaryDisplay() {
        runTestOnSecondaryDisplay((user, displayId, instance) -> {
            boolean startedAgain = tryToStartVisibleBackgroundUser(user.id(), displayId);
            assertWithMessage("started user %s on id %s again", user.id(), displayId)
                    .that(startedAgain).isTrue();
        });
    }

    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireHeadlessSystemUserMode(reason = "non-HSUM cannot have profiles on secondary users")
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public final void testStartVisibleBgUser_onSecondaryDisplay_profileOnSameDisplay() {
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference parent = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "parent: id=" + parent.id());
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + parent.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(parent)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
                startVisibleBackgroundUser(parent, displayId);
                try {
                    // Make sure profile is stopped, just in case it was automatically started with
                    // parent user
                    Log.d(TAG, "Stopping profile " + profile.id()); profile.stop();
                    boolean started = tryToStartVisibleBackgroundUser(profile.id(),
                            displayId);
                    // MUMD doesn't support profiles on secondary users
                    assertWithMessage("profile user (id=%s) started on display %s", profile.id(),
                            displayId).that(started).isFalse();
                } finally {
                    parent.stop();
                } // startBackgroundUserOnSecondaryDisplay(user)
            } // new profile
        } // new user
    }

    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireHeadlessSystemUserMode(reason = "non-HSUM cannot have profiles on secondary users")
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public final void testStartVisibleBgUser_onSecondaryDisplay_profileOnDefaultDisplay() {
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference parent = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "parent: id=" + parent.id());
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + parent.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(parent)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
                startVisibleBackgroundUser(parent, displayId);
                try {
                    // Make sure profile is stopped, just in case it was automatically started with
                    // parent user
                    Log.d(TAG, "Stopping profile " + profile.id()); profile.stop();

                    // NOTE: startUserInBackgroundOnSecondaryDisplay() doesn't accept
                    // DEFAULT_DISPLAY, so we need to try to start the profile directly instead
                    assertThrows(NeneException.class, () ->profile.start());
                } finally {
                    parent.stop();
                } // startBackgroundUserOnSecondaryDisplay(user)
            } // new profile
        } // new user
    }

    // TODO(b/251565294): must create users explicitly (instead of using @EnsureHasSecondaryUser
    // and @EnsureHasWorkProfile) because otherwise the test would fail on automotive (even
    // though it's annotated with @RequireNotHeadlessSystemUserMode)
    private static final boolean BUG_251565294_FIXED = false;

    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireNotHeadlessSystemUserMode(reason = "will create profile on system user")
    @RequireRunOnPrimaryUser
    public final void testStartVisibleBgUser_parentOnDefaultDisplayProfileOnSecondaryDisplay() {
        if (!BUG_251565294_FIXED) {
            UserReference systemUser = TestApis.users().system();
            Log.d(TAG, "Creating profile");
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + systemUser.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(systemUser)
                    .create()) {
                Log.d(TAG, "Created profile: id=" + profile.id());

                // Stop the profile as it was initially running in the same display as parent
                Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser()
                        + ")");
                profile.stop();

                int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
                boolean started = tryToStartVisibleBackgroundUser(profile.id(),
                        displayId);

                assertWithMessage("started profile %s on display %s", profile.id(), displayId)
                        .that(started).isFalse();
            } // new profile
            return;
        }

        // Stop the profile as it was initially running in the same display as parent
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
        boolean started = tryToStartVisibleBackgroundUser(profile.id(), displayId);

        assertWithMessage("started profile %s on display %s", profile.id(), displayId)
                .that(started).isFalse();

        return;
    }

    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireHeadlessSystemUserMode(reason = "will create profile on secondary user")
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public final void
            testStartVisibleBgUser_parentOnDefaultDisplayProfileOnSecondaryDisplay_HSUM() {
        // NOTE: Cannot use runTestOnSecondaryDisplay() because it's mixing displays (so it calls
        // user.start() instead of startBackgroundUserOnSecondaryDisplay())
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference user = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "Starting brand new user: id=" + user.id());
            user.start();
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + user.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(user)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                // Stop the profile, just in case it was automatically started
                Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser()
                        + ")");
                profile.stop();

                int displayId = getSecondaryDisplayIdForStartingVisibleBackgroundUser();
                boolean started = tryToStartVisibleBackgroundUser(profile.id(),
                        displayId);

                assertWithMessage("started profile %s on display %s", profile.id(), displayId)
                        .that(started).isFalse();
            } // new profile
        } // new user
    }

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireRunOnInitialUser
    @Test
    public final void testStartVisibleBgUser_onSecondaryDisplay_displayInUse() {
        runTestOnSecondaryDisplay((user, displayId, instance) -> {
            try (UserReference otherUser = TestApis.users().createUser().name("other_user")
                    .create()) {
                int otherUserId = otherUser.id();
                Log.d(TAG, "otherUser: id=" + otherUserId);

                boolean started = tryToStartVisibleBackgroundUser(otherUserId,
                        displayId);
                assertWithMessage("started user %s on display %s", otherUserId, displayId)
                        .that(started).isFalse();
            }
        });
    }
}

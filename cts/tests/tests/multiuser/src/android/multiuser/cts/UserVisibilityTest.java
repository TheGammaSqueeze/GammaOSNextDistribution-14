/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.multiuser.cts.TestingUtils.getContextForOtherUser;
import static android.multiuser.cts.TestingUtils.getContextForUser;
import static android.multiuser.cts.TestingUtils.sContext;

import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.Collection;

/**
 * Tests for user visibility (as defined by {@link UserManager#isUserVisible()}) APIs that applies
 * to all devices, whether or not they {@link UserManager#isUsersOnSecondaryDisplaysSupported()
 * support background users running on secondary displays}).
 */
@AppModeFull(reason = "it's testing user features, not related to apps")
public final class UserVisibilityTest extends UserVisibilityTestCase {

    private static final String TAG = UserVisibilityTest.class.getSimpleName();

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS)
    public void testIsUserVisible_differentContext_noPermission() throws Exception {
        Context context = sContext.createContextAsUser(UserHandle.of(-42), /* flags= */ 0);
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserVisible());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserVisible_differentContext_withPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for unknown user").that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnInitialUser
    public void testIsUserVisible_currentUser() throws Exception {
        assertWithMessage("isUserVisible() for current user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserVisible_backgroundUser() throws Exception {
        assertWithMessage("isUserVisible() for background user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/239961027): should be @RequireRunOnProfile instead
    @RequireRunOnWorkProfile(switchedToParentUser = TRUE)
    public void testIsUserVisible_startedProfileOfCurrentUser() throws Exception {
        assertWithMessage("isUserVisible() for profile of current user (%s)",
                sContext.getUser()).that(mUserManager.isUserVisible()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // Cannot use @RunOnProfile as it will stop the profile
    @RequireRunOnInitialUser
    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserVisible() on other context
    public void testIsUserVisible_stoppedProfileOfCurrentUser() throws Exception {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for stopped profile (id=%s) of current user",
                profile.id()).that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS)
    public void testGetVisibleUsers_noPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.getVisibleUsers());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireRunOnInitialUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_currentUser() throws Exception {
        Collection<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_backgroundUser() throws Exception {
        Collection<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    // TODO(b/239961027): should be @RequireRunOnProfile instead
    @RequireRunOnWorkProfile(switchedToParentUser = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_startedProfileOfCurrentUser() throws Exception {
        UserReference myUser = TestApis.users().instrumented();
        Collection<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .containsAtLeast(myUser.userHandle(), myUser.parent().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    // Cannot use @RunOnProfile as it will stop the profile
    @RequireRunOnInitialUser
    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_stoppedProfileOfCurrentUser() throws Exception {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        Collection<UserHandle> visibleUsers = mUserManager.getVisibleUsers();

        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .contains(TestApis.users().current().userHandle());
        assertWithMessage("getVisibleUsers()").that(visibleUsers)
                .doesNotContain(profile.userHandle());
    }

    @Test
    @RequireNotVisibleBackgroundUsers(reason = "Because API is not supported")
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    public void testStartUserInBackgroundVisibleOnDisplay() {
        // ids doen't really matter, as it should throw right away
        assertThrows(UnsupportedOperationException.class,
                () -> tryToStartVisibleBackgroundUser(42, 108));
    }
}

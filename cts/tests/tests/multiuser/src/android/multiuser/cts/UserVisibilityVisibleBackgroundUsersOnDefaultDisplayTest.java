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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.bedstead.nene.TestApis.users;
import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.AppModeFull;

import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;


/**
 * Tests for user-related APIs that are only available on devices that
 * {@link UserManager#isVisibleBackgroundUsersSupported() support visible background users on
 * default display} (such as cars with a second Android system managing only passenger displays).
 *
 * <p>If you want to run these tests on devices that don't support it, run the following commands
 * first:
 * <pre>{@code
 *   adb shell setprop fw.visible_bg_users true
 *   adb shell setprop fw.visible_bg_users_on_default_display true
 *   adb shell stop && adb shell start
 * }</pre>
 */
@AppModeFull(reason = "it's testing user features, not related to apps")
@RequireVisibleBackgroundUsers(reason = "Must support visible bg users to support them on default "
        + "display")
@RequireVisibleBackgroundUsersOnDefaultDisplay(reason = "Because class is testing exactly that")
public final class UserVisibilityVisibleBackgroundUsersOnDefaultDisplayTest
        extends UserVisibilityVisibleBackgroundUsersTestCase {

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnInitialUser
    public void testIsUserVisible_visibleBackgroundUserOnDefaultDisplay() throws Exception {
        runTestOnDefaultDisplay((user, instance) ->
                assertWithMessage("isUserVisible() for background user (id=%s) on DEFAULT_DISPLAY",
                    user.id()).that(instance.userManager().isUserVisible()).isTrue());
    }

    // TODO(b/240736142): tests below should belong to ActivityManagerTest or similar, but it
    // doesn't use bedstead yet

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireRunOnInitialUser
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @Test
    public void testStartUserInBackgroundOnSecondaryDisplay_defaultDisplay() {
        UserReference user = sDeviceState.additionalUser();

        startVisibleBackgroundUser(user, DEFAULT_DISPLAY);

        user.stop();
    }

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireRunOnInitialUser
    @Test
    public void testStartUserInBackgroundOnSecondaryDisplay_defaultDisplay_currentUser() {
        UserReference currentUser = users().current();

        startVisibleBackgroundUser(currentUser, DEFAULT_DISPLAY);
    }

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay"})
    @RequireRunOnInitialUser
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @Test
    public void
            testStartUserInBackgroundOnSecondaryDisplay_defaultDisplay_thereCanBeOnlyOne() {
        UserReference user = sDeviceState.additionalUser();
        startVisibleBackgroundUser(user, DEFAULT_DISPLAY);

        try {
            // NOTE: in theory we should check if a new user can be created, but in practical terms,
            // if the device supports multiple users on default display, it should allow max enough
            // users for that
            UserReference otherUser = users().createUser().name("other_user").create();
            assertWithMessage("other user").that(otherUser.id()).isNotEqualTo(user.id());

            boolean started = tryToStartVisibleBackgroundUser(otherUser.id(), DEFAULT_DISPLAY);

            assertWithMessage("Started %s on DEFAULT_DISPLAY after %s was already started on it",
                    otherUser, user).that(started).isFalse();
        } finally {
            user.stop();
        }
    }
}

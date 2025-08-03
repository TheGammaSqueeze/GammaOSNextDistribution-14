/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.robotests;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS_FULL;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.DefaultUserBinder;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public final class DefaultUserBinderTest {

  private static final int OTHER_USER_ID = 5;

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final RobolectricTestUtilities utilities =
      new RobolectricTestUtilities(context, scheduledExecutorService);

  private final UserHandle otherUserHandle = utilities.createCustomUser(OTHER_USER_ID);
  private final DefaultUserBinder binder = new DefaultUserBinder(otherUserHandle);

  @Before
  public void setUp() {
    utilities.denyPermissions(
        INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL);
  }

  @Test
  @Config(minSdk = VERSION_CODES.R)
  public void hasPermissionToBind_isTargetUserProfile_hasInteractAcrossProfiles_true() {
    utilities.tryAddTargetUserProfile(otherUserHandle);
    utilities.setRequestsPermissions(INTERACT_ACROSS_PROFILES);
    utilities.grantPermissions(INTERACT_ACROSS_PROFILES);

    assertThat(binder.hasPermissionToBind(context)).isTrue();
  }

  @Test
  @Config(minSdk = VERSION_CODES.R)
  public void hasPermissionToBind_isNotTargetUserProfile_hasInteractAcrossProfiles_false() {
    utilities.tryRemoveTargetUserProfile(otherUserHandle);
    utilities.setRequestsPermissions(INTERACT_ACROSS_PROFILES);
    utilities.grantPermissions(INTERACT_ACROSS_PROFILES);

    assertThat(binder.hasPermissionToBind(context)).isFalse();
  }

  @Test
  @Config(minSdk = VERSION_CODES.R)
  public void hasPermissionToBind_isTargetUserProfile_noInteractAcrossProfiles_false() {
    utilities.tryAddTargetUserProfile(otherUserHandle);
    utilities.denyPermissions(INTERACT_ACROSS_PROFILES);

    assertThat(binder.hasPermissionToBind(context)).isFalse();
  }

  @Test
  public void hasPermissionToBind_interactAcrossUsersOnly_false() {
    utilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    utilities.grantPermissions(INTERACT_ACROSS_USERS);

    assertThat(binder.hasPermissionToBind(context)).isFalse();
  }

  @Test
  public void hasPermissionToBind_interactAcrossUsersFull_true() {
    utilities.setRequestsPermissions(INTERACT_ACROSS_USERS_FULL);
    utilities.grantPermissions(INTERACT_ACROSS_USERS_FULL);

    assertThat(binder.hasPermissionToBind(context)).isTrue();
  }
}

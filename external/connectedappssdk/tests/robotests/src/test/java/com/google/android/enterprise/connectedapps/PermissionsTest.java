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
package com.google.android.enterprise.connectedapps;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS_FULL;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class PermissionsTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector connector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final Permissions permissions = connector.permissions();
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(connector, scheduledExecutorService);

  @Test
  public void canMakeCrossProfileCalls_defaultProfileBinder_doesntDeclareAnyPermissions_isFalse() {
    testUtilities.setRequestsPermissions();

    assertThat(permissions.canMakeCrossProfileCalls()).isFalse();
  }

  @Test
  public void
      canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossUsersNotGranted_isFalse() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.denyPermissions(INTERACT_ACROSS_USERS);

    assertThat(permissions.canMakeCrossProfileCalls()).isFalse();
  }

  @Test
  public void
      canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossUsersFullNotGranted_isFalse() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS_FULL);
    testUtilities.denyPermissions(INTERACT_ACROSS_USERS_FULL);

    assertThat(permissions.canMakeCrossProfileCalls()).isFalse();
  }

  @Test
  public void
      canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossProfilesNotGranted_isFalse() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_PROFILES);
    testUtilities.denyPermissions(INTERACT_ACROSS_PROFILES);

    assertThat(permissions.canMakeCrossProfileCalls()).isFalse();
  }

  @Test
  public void canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossUsersGranted_isTrue() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);

    assertThat(permissions.canMakeCrossProfileCalls()).isTrue();
  }

  @Test
  public void
      canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossUsersFullGranted_isTrue() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS_FULL);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS_FULL);

    assertThat(permissions.canMakeCrossProfileCalls()).isTrue();
  }

  @Test
  @Ignore // TODO(161541780): Enable this test when building against a supported version of
  // Robolectric
  public void canMakeCrossProfileCalls_defaultProfileBinder_interactAcrossProfilesGranted_isTrue() {
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_PROFILES);
    testUtilities.grantPermissions(INTERACT_ACROSS_PROFILES);

    assertThat(permissions.canMakeCrossProfileCalls()).isTrue();
  }
}

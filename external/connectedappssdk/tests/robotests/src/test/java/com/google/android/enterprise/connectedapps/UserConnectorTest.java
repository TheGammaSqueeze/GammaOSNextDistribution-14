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

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS_FULL;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.MissingApiException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.crossuser.AppCrossUserConfiguration;
import com.google.android.enterprise.connectedapps.testapp.crossuser.AppCrossUserConnector;
import com.google.android.enterprise.connectedapps.testapp.crossuser.UserTestCrossUserType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public final class UserConnectorTest {

  private static final class TestBinder extends DefaultUserBinder {

    TestBinder(UserHandle userHandle) {
      super(userHandle);
    }

    static int tryBindCalls = 0;

    @Override
    public boolean tryBind(
        Context context,
        ComponentName bindToService,
        ServiceConnection connection,
        AvailabilityRestrictions availabilityRestrictions)
        throws MissingApiException {
      ++tryBindCalls;
      return super.tryBind(context, bindToService, connection, availabilityRestrictions);
    }
  }

  private static final String STRING = "hello";
  private static final int TARGET_USER_ID = 5;

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final RobolectricTestUtilities utilities =
      new RobolectricTestUtilities(context, scheduledExecutorService);

  private final UserHandle targetUserHandle = utilities.createCustomUser(TARGET_USER_ID);

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(AppCrossUserConfiguration.getService());
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    utilities.setBinding(binder, AppCrossUserConnector.class.getName());
    utilities.setRequestsPermissions(INTERACT_ACROSS_USERS_FULL);
    utilities.grantPermissions(INTERACT_ACROSS_USERS_FULL);
  }

  @Test
  public void defaultBinder_works() throws UnavailableProfileException {
    AppCrossUserConnector connector =
        AppCrossUserConnector.create(context, scheduledExecutorService);
    UserTestCrossUserType testCrossUserType = UserTestCrossUserType.create(connector);

    utilities.addDefaultConnectionHolderAndWait(connector, targetUserHandle);

    assertThat(testCrossUserType.user(targetUserHandle).identityStringMethod(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void testBinderFactory_isUsed_tryBindCallsIncremented() {
    TestBinder.tryBindCalls = 0;
    AppCrossUserConnector connector =
        AppCrossUserConnector.create(context, scheduledExecutorService, TestBinder::new);

    utilities.addDefaultConnectionHolderAndWait(connector, targetUserHandle);

    assertThat(TestBinder.tryBindCalls).isEqualTo(1);
  }
}

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

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.fail;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.CustomError;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class BothProfilesSynchronousTest {

  private static final String STRING = "String";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);

  private final Profile currentProfileIdentifier = testProfileConnector.utils().getCurrentProfile();
  private final Profile otherProfileIdentifier = testProfileConnector.utils().getOtherProfile();

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, RobolectricTestUtilities.TEST_CONNECTOR_CLASS_NAME);
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  @Test
  public void both_synchronous_isBound_resultContainsBothProfileResults() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    Map<Profile, String> result = profileTestCrossProfileType.both().identityStringMethod(STRING);

    assertThat(result.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(result.get(otherProfileIdentifier)).isEqualTo(STRING);
  }

  @Test
  public void both_synchronous_isNotBound_resultOnlyContainsCurrentProfileResult() {
    testUtilities.turnOffWorkProfile();

    Map<Profile, String> result = profileTestCrossProfileType.both().identityStringMethod(STRING);

    assertThat(result.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(result).doesNotContainKey(otherProfileIdentifier);
  }

  @Test
  public void both_synchronous_throwsRuntimeException_exceptionThrownOnCurrentProfileIsThrown() {
    // Since the exception is thrown on both sides, which is thrown first is not deterministic.
    // This test just confirms one of the two is thrown
    try {
      profileTestCrossProfileType.both().methodWhichThrowsRuntimeException();
      fail();
    } catch (CustomRuntimeException expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void both_synchronous_throwsError_errorThrownOnCurrentProfileIsThrown() {
    // Since the exception is thrown on both sides, which is thrown first is not deterministic.
    // This test just confirms one of the two is thrown
    try {
      profileTestCrossProfileType.both().methodWhichThrowsError();
      fail();
    } catch (CustomError expected) {

    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomError.class);
    }
  }

  @Test
  public void both_synchronous_contextArgument_works() {
    Map<Profile, Boolean> result = profileTestCrossProfileType.both().isContextArgumentPassed();

    assertThat(result.get(currentProfileIdentifier)).isTrue();
    assertThat(result.get(otherProfileIdentifier)).isTrue();
  }
}

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
import static org.junit.Assert.assertThrows;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.CrossProfileConnector;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.TestStringCallbackListener;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector;
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
public class CrossProfileTypeProfileTest {

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
  private final TestStringCallbackListener stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();

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
  public void profile_withIdentifierForCurrentProfile_runsOnCurrentProfile()
      throws UnavailableProfileException {
    testUtilities.turnOffWorkProfile();

    Profile currentProfileIdentifier = testProfileConnector.utils().getCurrentProfile();

    // If this runs on the other profile, an exception will be thrown.
    profileTestCrossProfileType.profile(currentProfileIdentifier).voidMethod();
  }

  @Test
  public void profile_withIdentifierForOtherProfile_runsOnOtherProfile() {
    testUtilities.turnOffWorkProfile();

    Profile otherProfileIdentifier = testProfileConnector.utils().getOtherProfile();

    // If this runs on the other profile, an exception will be thrown.
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.profile(otherProfileIdentifier).voidMethod());
  }

  @Test
  public void work_calledFromWorkProfile_runsOnCurrentProfile() throws UnavailableProfileException {
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnWorkProfile();

    // If this runs on the other profile, an exception will be thrown.
    profileTestCrossProfileType.work().voidMethod();
  }

  @Test
  public void work_calledFromPersonalProfile_runsOnOtherProfile() {
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnPersonalProfile();

    // If this runs on the other profile, an exception will be thrown.
    assertThrows(
        UnavailableProfileException.class, () -> profileTestCrossProfileType.work().voidMethod());
  }

  @Test
  public void personal_calledFromWorkProfile_runsOnOtherProfile() {
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnWorkProfile();

    // If this runs on the other profile, an exception will be thrown.
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.personal().voidMethod());
  }

  @Test
  public void personal_calledFromPersonalProfile_runsOnCurrentProfile()
      throws UnavailableProfileException {
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnPersonalProfile();

    // If this runs on the other profile, an exception will be thrown.
    profileTestCrossProfileType.personal().voidMethod();
  }

  @Test
  public void primary_calledFromPrimaryProfile_runsOnCurrentProfile()
      throws UnavailableProfileException {
    // The primary profile is defined as work in TestProfileConnector
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnWorkProfile();

    // If this runs on the other profile, an exception will be thrown.
    profileTestCrossProfileType.primary().voidMethod();
  }

  @Test
  public void primary_calledFromSecondaryProfile_runsOnOtherProfile()
      throws UnavailableProfileException {
    // The primary profile is defined as work in TestProfileConnector
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnPersonalProfile();

    // If this runs on the other profile, an exception will be thrown.
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.primary().voidMethod());
  }

  @Test
  public void secondary_calledFromSecondaryProfile_runsOnCurrentProfile()
      throws UnavailableProfileException {
    // The primary profile is defined as work in TestProfileConnector
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnPersonalProfile();

    // If this runs on the other profile, an exception will be thrown.
    profileTestCrossProfileType.secondary().voidMethod();
  }

  @Test
  public void secondary_calledFromPrimaryProfile_runsOnOtherProfile()
      throws UnavailableProfileException {
    // The primary profile is defined as work in TestProfileConnector
    testUtilities.turnOffWorkProfile();
    testUtilities.setRunningOnWorkProfile();

    // If this runs on the other profile, an exception will be thrown.
    assertThrows(
        UnavailableProfileException.class,
        () -> profileTestCrossProfileType.secondary().voidMethod());
  }

  @Test
  public void primary_calledOnTypeWithoutConnector_connectorHasPrimary_works()
      throws UnavailableProfileException {
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(testProfileConnector);

    assertThat(type.primary().identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void secondary_calledOnTypeWithoutConnector_connectorHasPrimary_works()
      throws UnavailableProfileException {
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(testProfileConnector);

    assertThat(type.secondary().identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void suppliers_calledOnTypeWithoutConnector_connectorHasPrimary_works() {
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(testProfileConnector);

    assertThat(type.suppliers().identityStringMethod(STRING)).isNotEmpty();
  }

  @Test
  public void
      primary_calledOnTypeWithoutConnector_connectorDoesNotHavePrimary_throwsIllegalStateException() {
    CrossProfileConnector crossProfileConnector = CrossProfileConnector.builder(context).build();
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(crossProfileConnector);

    assertThrows(IllegalStateException.class, () -> type.primary().identityStringMethod(STRING));
  }

  @Test
  public void
      secondary_calledOnTypeWithoutConnector_connectorDoesNotHavePrimary_throwsIllegalStateException() {
    CrossProfileConnector crossProfileConnector = CrossProfileConnector.builder(context).build();
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(crossProfileConnector);

    assertThrows(IllegalStateException.class, () -> type.secondary().identityStringMethod(STRING));
  }

  @Test
  public void
      suppliers_calledOnTypeWithoutConnector_connectorDoesNotHavePrimary_throwsIllegalStateException() {
    CrossProfileConnector crossProfileConnector = CrossProfileConnector.builder(context).build();
    ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector type =
        ProfileTestCrossProfileTypeWhichDoesNotSpecifyConnector.create(crossProfileConnector);

    assertThrows(IllegalStateException.class, () -> type.suppliers().identityStringMethod(STRING));
  }

  @Test
  public void personal_synchronous_runningOnWork_throwsException_exceptionIsWrapped()
      throws UnavailableProfileException {
    testUtilities.setRunningOnWorkProfile();

    try {
      profileTestCrossProfileType.personal().methodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void work_synchronous_runningOnPersonal_throwsException_exceptionIsWrapped()
      throws UnavailableProfileException {
    testUtilities.setRunningOnPersonalProfile();

    try {
      profileTestCrossProfileType.work().methodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void primary_synchronous_runningOnSecondaryProfile_throwsException_exceptionIsWrapped()
      throws UnavailableProfileException {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType.primary().methodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void secondary_synchronous_runningOnPrimaryProfile_throwsException_exceptionIsWrapped()
      throws UnavailableProfileException {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType.secondary().methodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void personal_synchronous_runningOnPersonal_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile();

    assertThrows(
        CustomRuntimeException.class,
        () -> profileTestCrossProfileType.personal().methodWhichThrowsRuntimeException());
  }

  @Test
  public void work_synchronous_runningOnWork_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile();

    assertThrows(
        CustomRuntimeException.class,
        () -> profileTestCrossProfileType.work().methodWhichThrowsRuntimeException());
  }

  @Test
  public void primary_synchronous_runningOnPrimaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () -> profileTestCrossProfileType.primary().methodWhichThrowsRuntimeException());
  }

  @Test
  public void
      secondary_synchronous_runningOnSecondaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () -> profileTestCrossProfileType.secondary().methodWhichThrowsRuntimeException());
  }

  @Test
  public void personal_async_runningOnWork_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnWorkProfile();

    try {
      profileTestCrossProfileType
          .personal()
          .asyncStringMethodWhichThrowsRuntimeException(
              stringCallbackListener, exceptionCallbackListener);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void work_async_runningOnPersonal_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnPersonalProfile();

    try {
      profileTestCrossProfileType
          .work()
          .asyncStringMethodWhichThrowsRuntimeException(
              stringCallbackListener, exceptionCallbackListener);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void primary_async_runningOnSecondaryProfile_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType
          .primary()
          .asyncStringMethodWhichThrowsRuntimeException(
              stringCallbackListener, exceptionCallbackListener);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void secondary_async_runningOnPrimaryProfile_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType
          .secondary()
          .asyncStringMethodWhichThrowsRuntimeException(
              stringCallbackListener, exceptionCallbackListener);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void personal_async_runningOnPersonal_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile();

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .personal()
                .asyncStringMethodWhichThrowsRuntimeException(
                    stringCallbackListener, exceptionCallbackListener));
  }

  @Test
  public void work_async_runningOnWork_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile();

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .work()
                .asyncStringMethodWhichThrowsRuntimeException(
                    stringCallbackListener, exceptionCallbackListener));
  }

  @Test
  public void primary_async_runningOnPrimaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .primary()
                .asyncStringMethodWhichThrowsRuntimeException(
                    stringCallbackListener, exceptionCallbackListener));
  }

  @Test
  public void secondary_async_runningOnSecondaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .secondary()
                .asyncStringMethodWhichThrowsRuntimeException(
                    stringCallbackListener, exceptionCallbackListener));
  }

  @Test
  public void personal_future_runningOnWork_throwsException_wrapsInProfileRuntimeException() {
    testUtilities.setRunningOnWorkProfile();

    try {
      profileTestCrossProfileType
          .personal()
          .listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void work_future_runningOnPersonal_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnPersonalProfile();

    try {
      profileTestCrossProfileType.work().listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void primary_future_runningOnSecondaryProfile_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType.primary().listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void secondary_future_runningOnPrimaryProfile_throwsException_exceptionIsWrapped() {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    try {
      profileTestCrossProfileType
          .secondary()
          .listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void personal_future_runningOnPersonal_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile();

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .personal()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }

  @Test
  public void work_future_runningOnWork_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile();

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .work()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }

  @Test
  public void primary_future_runningOnPrimaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnWorkProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .primary()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }

  @Test
  public void secondary_future_runningOnSecondaryProfile_throwsException_exceptionIsNotWrapped() {
    testUtilities.setRunningOnPersonalProfile(); // Work profile is primary for TestProfileConnector

    assertThrows(
        CustomRuntimeException.class,
        () ->
            profileTestCrossProfileType
                .secondary()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }
}

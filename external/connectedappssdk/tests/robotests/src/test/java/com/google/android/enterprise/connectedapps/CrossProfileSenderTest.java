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

import static com.google.android.enterprise.connectedapps.RobolectricTestUtilities.TEST_CONNECTOR_CLASS_NAME;
import static com.google.android.enterprise.connectedapps.RobolectricTestUtilities.TEST_SERVICE_CLASS_NAME;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.tryForceRaceCondition;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class CrossProfileSenderTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final DevicePolicyManager devicePolicyManager =
      context.getSystemService(DevicePolicyManager.class);
  private final TestService testService = new TestService();

  private CrossProfileSender sender;
  private final TestConnectionListener connectionListener = new TestConnectionListener();
  private final TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
  private final TestAvailabilityListener availabilityListener2 = new TestAvailabilityListener();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(context, scheduledExecutorService);

  @Before
  public void setUp() {
    testUtilities.initTests();
    sender =
        new CrossProfileSender(
            context,
            TEST_SERVICE_CLASS_NAME,
            new DefaultProfileBinder(),
            connectionListener,
            availabilityListener,
            scheduledExecutorService,
            AvailabilityRestrictions.DEFAULT);

    testUtilities.setBinding(testService, TEST_CONNECTOR_CLASS_NAME);
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
    sender.clearConnectionHolders();
  }

  @Test
  public void construct_nullContext_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                /* context= */ null,
                TEST_SERVICE_CLASS_NAME,
                new DefaultProfileBinder(),
                connectionListener,
                availabilityListener,
                scheduledExecutorService,
                AvailabilityRestrictions.DEFAULT));
  }

  @Test
  public void construct_nullConnectedAppsServiceClassName_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                /* connectedAppsServiceClassName= */ null,
                new DefaultProfileBinder(),
                connectionListener,
                availabilityListener,
                scheduledExecutorService,
                AvailabilityRestrictions.DEFAULT));
  }

  @Test
  public void construct_nullConnectionListener_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                TEST_SERVICE_CLASS_NAME,
                new DefaultProfileBinder(),
                /* connectionListener= */ null,
                availabilityListener,
                scheduledExecutorService,
                AvailabilityRestrictions.DEFAULT));
  }

  @Test
  public void construct_nullAvailabilityListener_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                TEST_SERVICE_CLASS_NAME,
                new DefaultProfileBinder(),
                connectionListener,
                /* availabilityListener= */ null,
                scheduledExecutorService,
                AvailabilityRestrictions.DEFAULT));
  }

  @Test
  public void construct_nullBindingConfig_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                TEST_SERVICE_CLASS_NAME,
                /* binder= */ null,
                connectionListener,
                availabilityListener,
                scheduledExecutorService,
                AvailabilityRestrictions.DEFAULT));
  }

    @Test
  public void construct_nullTimeoutExecutor_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                TEST_SERVICE_CLASS_NAME,
                new DefaultProfileBinder(),
                connectionListener,
                availabilityListener,
                /* scheduledExecutorService= */ null,
                AvailabilityRestrictions.DEFAULT));
  }

  @Test
  public void construct_nullAvailabilityRestrictions_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () ->
            new CrossProfileSender(
                context,
                TEST_SERVICE_CLASS_NAME,
                new DefaultProfileBinder(),
                connectionListener,
                availabilityListener,
                scheduledExecutorService,
                /* availabilityRestrictions= */ null));
  }

  // Other manuallyBind tests are covered in Instrumented ConnectTest because Robolectric doesn't
  // handle the multiple threads very well
  @Test
  public void manuallyBind_callingFromUIThread_throwsIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () -> sender.manuallyBind(CrossProfileSender.MANUAL_MANAGEMENT_CONNECTION_HOLDER));
  }

  @Test
  public void addConnectionHolder_otherProfileIsNotAvailable_doesNotbind() {
    testUtilities.turnOffWorkProfile();
    sender.addConnectionHolder(this);

    assertThat(sender.isBound()).isFalse();
  }

  @Test
  public void addConnectionHolder_bindingIsNotPossible_doesNotCallConnectionListener() {
    testUtilities.turnOffWorkProfile();

    sender.addConnectionHolder(this);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(0);
  }

  @Test
  public void addConnectionHolder_otherProfileIsAvailable_binds() {
    testUtilities.turnOnWorkProfile();
    sender.addConnectionHolder(this);

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void addConnectionHolder_binds_callsConnectionListener() {
    testUtilities.turnOnWorkProfile();
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(1);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_otherProfileBecomesAvailable_binds() {
    testUtilities.turnOffWorkProfile();
    sender.addConnectionHolder(this);

    testUtilities.turnOnWorkProfile();

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void addConnectionHolder_otherProfileBecomesAvailable_callsConnectionListener() {
    testUtilities.turnOffWorkProfile();
    sender.addConnectionHolder(this);

    testUtilities.turnOnWorkProfile();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_profileBecomesUnavailable_unbinds() {
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);

    testUtilities.turnOffWorkProfile();

    assertThat(sender.isBound()).isFalse();
  }

  @Test
  public void addConnectionHolder_profileBecomesUnavailable_callsConnectionListener() {
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);
    connectionListener.resetConnectionChangedCount();

    testUtilities.turnOffWorkProfile();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_profileBecomesAvailableAgain_rebinds() {
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);
    testUtilities.turnOffWorkProfile();

    testUtilities.turnOnWorkProfile();

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void addConnectionHolder_profileBecomesAvailableAgain_callsConnectionListener() {
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);
    testUtilities.turnOffWorkProfile();
    connectionListener.resetConnectionChangedCount();

    testUtilities.turnOnWorkProfile();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void unbind_callsConnectionListener() {
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(1);
    connectionListener.resetConnectionChangedCount();

    sender.removeConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(31);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void bind_bindingFromPersonalProfile_binds() {
    testUtilities.setRunningOnPersonalProfile();
    sender.addConnectionHolder(this);

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void bind_bindingFromWorkProfile_binds() {
    testUtilities.setRunningOnWorkProfile();
    sender.addConnectionHolder(this);

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void call_isNotBound_throwsException() {
    // As we can't force disconnection - we just give time for any existing connections to close
    testUtilities.advanceTimeBySeconds(31);
    int crossProfileTypeIdentifier = 1;
    int methodIdentifier = 0;

    Bundle params = new Bundle(Bundler.class.getClassLoader());

    assertThrows(
        UnavailableProfileException.class,
        () -> sender.call(crossProfileTypeIdentifier, methodIdentifier, params));
  }

  @Test
  public void call_isBound_callsMethod() throws UnavailableProfileException {
    int crossProfileTypeIdentifier = 1;
    int methodIdentifier = 0;

    Bundle params = new Bundle(Bundler.class.getClassLoader());
    params.putString("value", "value");
    sender.addConnectionHolder(this);

    sender.call(crossProfileTypeIdentifier, methodIdentifier, params);

    assertThat(testService.lastCall().getCrossProfileTypeIdentifier())
        .isEqualTo(crossProfileTypeIdentifier);
    assertThat(testService.lastCall().getMethodIdentifier()).isEqualTo(methodIdentifier);
    assertThat(testService.lastCall().getParams().getString("value")).isEqualTo("value");
  }

  @Test
  public void call_isBound_returnsResponse() throws UnavailableProfileException {
    int crossProfileTypeIdentifier = 1;
    int methodIdentifier = 0;
    Bundle params = new Bundle(Bundler.class.getClassLoader());
    Bundle expectedResponseBundle = new Bundle(Bundler.class.getClassLoader());
    expectedResponseBundle.putString("value", "value");
    testService.setResponseBundle(expectedResponseBundle);
    sender.addConnectionHolder(this);

    Bundle actualResponseBundle = sender.call(crossProfileTypeIdentifier, methodIdentifier, params);

    assertThat(actualResponseBundle.getString("value")).isEqualTo("value");
  }

  @Test
  public void bind_usingDpcBinding_otherProfileIsAvailable_binds() {
    initWithDpcBinding();
    testUtilities.turnOnWorkProfile();
    sender.addConnectionHolder(this);

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void bind_usingDpcBinding_binds_callsConnectionListener() {
    initWithDpcBinding();
    testUtilities.turnOnWorkProfile();
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(1);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void bind_usingDpcBinding_otherProfileDoesNotExist_doesNotBind() {
    initWithDpcBinding();
    shadowOf(devicePolicyManager).setBindDeviceAdminTargetUsers(ImmutableList.of());

    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(sender.isBound()).isFalse();
  }

  @Test
  public void bind_usingDpcBinding_otherProfileIsCreated_binds() {
    initWithDpcBinding();
    shadowOf(devicePolicyManager).setBindDeviceAdminTargetUsers(ImmutableList.of());
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);

    shadowOf(devicePolicyManager)
        .setBindDeviceAdminTargetUsers(ImmutableList.of(getWorkUserHandle()));
    testUtilities.turnOnWorkProfile();

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void bind_usingDpcBinding_otherProfileBecomesAvailable_binds() {
    initWithDpcBinding();
    testUtilities.turnOffWorkProfile();
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);

    testUtilities.turnOnWorkProfile();

    assertThat(sender.isBound()).isTrue();
  }

  @Test
  public void bind_usingDpcBinding_otherProfileBecomesAvailable_callsConnectionListener() {
    initWithDpcBinding();
    testUtilities.turnOffWorkProfile();
    sender.addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(10);

    testUtilities.turnOnWorkProfile();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void workProfileBecomesAvailable_callsAvailabilityListener() {
    testUtilities.turnOffWorkProfile();
    availabilityListener.reset();

    testUtilities.turnOnWorkProfile();

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(1);
  }

  @Test
  public void workProfileBecomesUnavailable_callsAvailabilityListener() {
    testUtilities.turnOnWorkProfile();
    availabilityListener.reset();

    testUtilities.turnOffWorkProfile();

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(1);
  }

  @Test
  public void createMultipleSenders_workProfileBecomesAvailable_callsAvailabilityListenerForEachSender() {
    CrossProfileSender sender2 =
        new CrossProfileSender(
            context,
            TEST_SERVICE_CLASS_NAME,
            new DefaultProfileBinder(),
            connectionListener,
            availabilityListener2,
            scheduledExecutorService,
            AvailabilityRestrictions.DEFAULT);

    testUtilities.turnOffWorkProfile();
    availabilityListener.reset();
    availabilityListener2.reset();

    testUtilities.turnOnWorkProfile();

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(1);
    assertThat(availabilityListener2.availabilityChangedCount()).isEqualTo(1);
  }

  @Test
  // Regression test for b/195910311.
  // Do not ignore if this test turns flaky, this likely highlights a real race condition.
  public void concurrentDisconnectionCall_doesntCrash() throws Exception {
    int crossProfileTypeIdentifier = 1;
    int methodIdentifier = 0;
    Bundle params = new Bundle(Bundler.class.getClassLoader());
    params.putString("value", "value");
    sender.addConnectionHolder(this);
    Object connectionHolderAlias = new Object();

    tryForceRaceCondition(
        10000,
        () ->
            sender.callAsync(
                crossProfileTypeIdentifier,
                methodIdentifier,
                params,
                new LocalCallback() {
                  @Override
                  public void onResult(int methodIdentifier, Bundle params) {}

                  @Override
                  public void onException(Bundle exception) {}
                },
                connectionHolderAlias),
        testUtilities::simulateDisconnectingServiceConnection);
  }

  private void initWithDpcBinding() {
    shadowOf(devicePolicyManager)
        .setBindDeviceAdminTargetUsers(ImmutableList.of(getWorkUserHandle()));

    ComponentName deviceAdminReceiver = new ComponentName("A", "B");

    testUtilities.initTests();
    sender =
        new CrossProfileSender(
            context,
            TEST_SERVICE_CLASS_NAME,
            new DpcProfileBinder(deviceAdminReceiver),
            connectionListener,
            availabilityListener,
            scheduledExecutorService,
            AvailabilityRestrictions.DEFAULT);

    testUtilities.setBinding(testService, TEST_CONNECTOR_CLASS_NAME);
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
  }

  private static UserHandle getWorkUserHandle() {
    return SharedTestUtilities.getUserHandleForUserId(10);
  }
}

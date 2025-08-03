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
package com.google.android.enterprise.connectedapps.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestAvailabilityListener;
import com.google.android.enterprise.connectedapps.TestConnectionListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.UserConnectionHolder;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class AbstractFakeUserConnectorTest {
  static class FakeUserConnector extends AbstractFakeUserConnector {
    FakeUserConnector(Context context) {
      super(context);
    }
  }

  private static final int CURRENT_USER_ID = 4;
  private static final int TARGET_USER_ID = 5;

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final RobolectricTestUtilities utilities =
      new RobolectricTestUtilities(context, scheduledExecutorService);
  private final UserHandle currentUser = utilities.createCustomUser(CURRENT_USER_ID);
  private final UserHandle targetUser = utilities.createCustomUser(TARGET_USER_ID);
  private final FakeUserConnector fakeUserConnector = new FakeUserConnector(context);
  private final TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
  private final TestConnectionListener connectionListener = new TestConnectionListener();
  private final Object connectionHolder = new Object();

  @Test
  public void addConnectionHolder_connectionIsAvailable_isConnected() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);

    fakeUserConnector.addConnectionHolder(targetUser, this);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void addConnectionHolder_connectionIsAvailable_notifiesConnectionChanged() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);

    fakeUserConnector.addConnectionHolder(targetUser, this);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_unregisteredConnectionListener_doesNotNotifyConnectionChanged() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);
    fakeUserConnector.removeConnectionListener(targetUser, connectionListener);

    fakeUserConnector.addConnectionHolder(targetUser, this);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(0);
  }

  @Test
  public void addConnectionHolder_connectionIsNotAvailable_doesNotNotifyOfConnectionChanged() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOffUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);

    fakeUserConnector.addConnectionHolder(targetUser, this);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(0);
  }

  @Test
  public void connect_connectionIsAvailable_isConnected() throws Exception {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);

    fakeUserConnector.connect(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void connect_connectionIsAvailable_notifiesConnectionChanged() throws Exception {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);

    fakeUserConnector.connect(targetUser);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void connect_unregisteredConnectionListener_doesNotNotifyConnectionChanged()
      throws Exception {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);
    fakeUserConnector.removeConnectionListener(targetUser, connectionListener);

    fakeUserConnector.connect(targetUser);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(0);
  }

  @Test
  public void connect_connectionIsNotAvailable_throwsUnavailableProfileException() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOffUser(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);

    assertThrows(UnavailableProfileException.class, () -> fakeUserConnector.connect(targetUser));
  }

  @Test
  public void turnOnTargetUser_targetUserWasOff_notifiesAvailabilityChange() {
    fakeUserConnector.turnOffUser(targetUser);
    fakeUserConnector.addAvailabilityListener(targetUser, availabilityListener);

    fakeUserConnector.turnOnUser(targetUser);

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(1);
  }

  @Test
  public void turnOnTargetUser_targetUserWasOn_doesNotNotifyAvailabilityChange() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addAvailabilityListener(targetUser, availabilityListener);

    fakeUserConnector.turnOnUser(targetUser);

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(0);
  }

  @Test
  public void turnOffTargetUser_targetUserWasOn_notifiesAvailabilityChange() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addAvailabilityListener(targetUser, availabilityListener);

    fakeUserConnector.turnOffUser(targetUser);

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(1);
  }

  @Test
  public void turnOffTargetUser_targetUserWasOff_doesNotNotifyAvailabilityChange() {
    fakeUserConnector.turnOffUser(targetUser);
    fakeUserConnector.addAvailabilityListener(targetUser, availabilityListener);

    fakeUserConnector.turnOffUser(targetUser);

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(0);
  }

  @Test
  public void turnOffTargetUser_wasConnected_notifiesConnectionChange() throws Exception {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.connect(targetUser);
    fakeUserConnector.addConnectionListener(targetUser, connectionListener);

    fakeUserConnector.turnOffUser(targetUser);

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void setRunningOnUser_setsRunningOnUser() {
    fakeUserConnector.setRunningOnUser(targetUser);

    assertThat(fakeUserConnector.runningOnUser()).isEqualTo(targetUser);
  }

  @Test
  public void setRunningOnTargetUser_startsTargetUser() {
    fakeUserConnector.setRunningOnUser(targetUser);
    fakeUserConnector.setRunningOnUser(currentUser);

    assertThat(fakeUserConnector.isAvailable(targetUser)).isTrue();
  }

  @Test
  public void removeTargetUser_targetUserBecomesUnavailable() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.removeUser(targetUser);

    assertThat(fakeUserConnector.isAvailable(targetUser)).isFalse();
  }

  @Test
  public void isConnected_isConnected_returnsTrue() throws Exception {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);

    fakeUserConnector.connect(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void isConnected_isNotConnected_returnsFalse() {
    fakeUserConnector.setRunningOnUser(currentUser);
    fakeUserConnector.turnOnUser(targetUser);

    fakeUserConnector.disconnect(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isFalse();
  }

  @Test
  public void canMakeCrossProfileCalls_defaultsToTrue() {
    assertThat(fakeUserConnector.permissions(targetUser).canMakeCrossProfileCalls()).isTrue();
  }

  @Test
  public void canMakeCrossProfileCalls_setToFalse_returnsFalse() {
    fakeUserConnector.setHasPermissionToMakeCrossProfileCalls(targetUser, false);

    assertThat(fakeUserConnector.permissions(targetUser).canMakeCrossProfileCalls()).isFalse();
  }

  @Test
  public void canMakeCrossProfileCalls_setToTrue_returnsTrue() {
    fakeUserConnector.setHasPermissionToMakeCrossProfileCalls(targetUser, false);
    fakeUserConnector.setHasPermissionToMakeCrossProfileCalls(targetUser, true);

    assertThat(fakeUserConnector.permissions(targetUser).canMakeCrossProfileCalls()).isTrue();
  }

  @Test
  public void timeoutConnection_hasNoConnectionHolders_disconnects() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.clearConnectionHolders(targetUser);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isFalse();
  }

  @Test
  public void addConnectionHolder_connects() {
    fakeUserConnector.turnOnUser(targetUser);

    fakeUserConnector.addConnectionHolder(targetUser, this);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void timeoutConnection_hasConnectionHolder_doesNotDisconnect() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionHolder(targetUser, this);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void removeConnectionHolder_lastConnectionHolder_doesNotDisconnect() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionHolder(targetUser, this);

    fakeUserConnector.removeConnectionHolder(targetUser, this);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void removeConnectionHolder_timeout_disconnects() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionHolder(targetUser, this);
    fakeUserConnector.removeConnectionHolder(targetUser, this);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isFalse();
  }

  @Test
  public void removeConnectionHolder_stillAnotherConnectionHolder_timeout_doesNotDisconnect() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionHolder(targetUser, this);
    fakeUserConnector.addConnectionHolder(targetUser, connectionHolder);
    fakeUserConnector.removeConnectionHolder(targetUser, this);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isTrue();
  }

  @Test
  public void removeConnectionHolder_removingAlias_timeout_disconnects() {
    fakeUserConnector.turnOnUser(targetUser);
    fakeUserConnector.addConnectionHolder(targetUser, this);
    fakeUserConnector.addConnectionHolderAlias(targetUser, connectionHolder, this);
    fakeUserConnector.removeConnectionHolder(targetUser, connectionHolder);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isFalse();
  }

  @Test
  public void removeConnectionHolder_removingWrapper_timeout_disconnects() {
    fakeUserConnector.turnOnUser(targetUser);
    UserConnectionHolder connectionHolder = fakeUserConnector.addConnectionHolder(targetUser, this);
    fakeUserConnector.removeConnectionHolder(targetUser, connectionHolder);

    fakeUserConnector.timeoutConnection(targetUser);

    assertThat(fakeUserConnector.isConnected(targetUser)).isFalse();
  }
}

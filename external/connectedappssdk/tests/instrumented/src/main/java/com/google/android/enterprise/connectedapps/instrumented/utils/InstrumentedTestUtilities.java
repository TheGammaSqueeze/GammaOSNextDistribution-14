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
package com.google.android.enterprise.connectedapps.instrumented.utils;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.getUserHandleForUserId;
import static com.google.android.enterprise.connectedapps.instrumented.utils.UserAndProfileTestUtilities.runCommandWithOutput;

import android.content.Context;
import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.ProfileConnectionHolder;
import com.google.android.enterprise.connectedapps.ProfileConnector;
import com.google.android.enterprise.connectedapps.testing.ProfileAvailabilityPoll;

/**
 * Wrapper around {@link
 * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities} which adds
 * features needed only by the SDK.
 */
public class InstrumentedTestUtilities {

  private final ProfileConnector connector;
  private final Context context;
  private final com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities
      instrumentedTestUtilities;

  public InstrumentedTestUtilities(Context context, ProfileConnector connector) {
    this.context = context;
    this.connector = connector;
    this.instrumentedTestUtilities =
        new com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities(
            context, connector);
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#hasWorkProfile()}.
   */
  public boolean hasWorkProfile() {
    return instrumentedTestUtilities.hasWorkProfile();
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#getWorkProfileUserId()}.
   */
  public int getWorkProfileUserId() {
    return instrumentedTestUtilities.getWorkProfileUserId();
  }

  private UserHandle getWorkProfileUserHandle() {
    return getUserHandleForUserId(getWorkProfileUserId());
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#ensureReadyForCrossProfileCalls()}.
   */
  public void ensureReadyForCrossProfileCalls() {
    instrumentedTestUtilities.ensureReadyForCrossProfileCalls();
    ensureWorkProfileTurnedOn();
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#ensureNoWorkProfile()}.
   */
  public void ensureNoWorkProfile() {
    instrumentedTestUtilities.ensureNoWorkProfile();
  }

  public void removeUser(int userId) {
    runCommandWithOutput("pm remove-user " + userId);
  }

  public void installInUser(int userId) {
    runCommandWithOutput(
        "cmd package install-existing --user " + userId + " " + context.getPackageName());
  }

  /**
   * Grant the {@code INTERACT_ACROSS_USERS} permission if this app declares it.
   *
   * <p>This is required before cross-profile interaction will work.
   */
  public void grantInteractAcrossUsers() {
    // TODO(scottjonathan): Support INTERACT_ACROSS_PROFILES in these tests.
    runCommandWithOutput(
        "pm grant " + context.getPackageName() + " android.permission.INTERACT_ACROSS_USERS");
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#ensureWorkProfileExists()}
   */
  public void ensureWorkProfileExists() {
    instrumentedTestUtilities.ensureWorkProfileExists();
  }

  /**
   * Create a work profile but do not install the test app.
   *
   * <p>This means that, as there is no profile owner, it will not be recognised as a work profile
   * by the SDK when running on that profile.
   */
  public void ensureWorkProfileExistsWithoutTestApp() {
    if (hasWorkProfile()) {
      if (!userHasPackageInstalled(getWorkProfileUserId(), context.getPackageName())) {
        return;
      }

      // TODO(162219825): Try to remove the package

      throw new IllegalStateException(
          "There is already a work profile on the device with user id "
              + getWorkProfileUserId()
              + ".");
    }
    runCommandWithOutput("pm create-user --profileOf 0 --managed TestProfile123");
    int workProfileUserId = getWorkProfileUserId();
    startUser(workProfileUserId);
  }

  private static boolean userHasPackageInstalled(int userId, String packageName) {
    String expectedPackageLine = "package:" + packageName;
    String[] installedPackages =
        runCommandWithOutput("pm list packages --user " + userId).split("\n");
    for (String installedPackage : installedPackages) {
      if (installedPackage.equals(expectedPackageLine)) {
        return true;
      }
    }
    return false;
  }

  /** Ensure that the work profile is running. */
  public void ensureWorkProfileTurnedOn() {
    turnOnWorkProfileAndWait();
  }

  /** Ensure that the work profile is not running. */
  public void ensureWorkProfileTurnedOff() {
    turnOffWorkProfileAndWait();
  }

  /**
   * Turn on the work profile and block until it has been turned on.
   *
   * <p>This uses {@link ServiceCall} and so is only guaranteed to work correctly on AOSP.
   *
   * @see #turnOnWorkProfile()
   */
  public void turnOnWorkProfileAndWait() {
    if (connector.isAvailable()) {
      return; // Already on
    }

    turnOnWorkProfile();

    ProfileAvailabilityPoll.blockUntilUserRunningAndUnlocked(context, getWorkProfileUserHandle());
  }

  /**
   * Turn on the work profile.
   *
   * <p>This uses {@link ServiceCall} and so is only guaranteed to work correctly on AOSP.
   *
   * @see #turnOnWorkProfileAndWait()
   */
  public void turnOnWorkProfile() {
    UserAndProfileTestUtilities.turnOnUser(getWorkProfileUserId());
  }

  /**
   * Turn off the work profile and block until it has been turned off.
   *
   * <p>This uses {@link ServiceCall} and so is only guaranteed to work correctly on AOSP.
   *
   * @see #turnOffWorkProfile()
   */
  public void turnOffWorkProfileAndWait() {
    turnOffWorkProfile();

    ProfileAvailabilityPoll.blockUntilUserNotAvailable(context, getWorkProfileUserHandle());
  }

  /**
   * Turn off the work profile.
   *
   * <p>This uses {@link ServiceCall} and so is only guaranteed to work correctly on AOSP.
   *
   * @see #turnOffWorkProfileAndWait()
   */
  public void turnOffWorkProfile() {
    UserAndProfileTestUtilities.turnOffUser(getWorkProfileUserId());
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#waitForDisconnected()}.
   */
  public void waitForDisconnected() {
    instrumentedTestUtilities.waitForDisconnected();
  }

  /**
   * See {@link
   * com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities#waitForConnected()}.
   */
  public void waitForConnected() {
    instrumentedTestUtilities.waitForConnected();
  }

  /**
   * Call {@link ProfileConnector#addConnectionHolder(Object)} ()} and wait for connection to be
   * complete.
   */
  public ProfileConnectionHolder addConnectionHolderAndWait(Object connectionHolder) {
    ProfileConnectionHolder p = connector.addConnectionHolder(connectionHolder);
    waitForConnected();
    return p;
  }

  public int createUser(String username) {
    return UserAndProfileTestUtilities.createUser(username);
  }

  public void startUser(int userId) {
    UserAndProfileTestUtilities.startUserAndBlock(context, userId);
  }
}

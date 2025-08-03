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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.ProfileConnector;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilities for interacting with an instrumented environment for cross-profile tests. */
public class InstrumentedTestUtilities {

  private static final String LOG_TAG = "InstrumentedTestUtilities";

  private static final Pattern USERINFO_PATTERN = Pattern.compile("UserInfo\\{(.*):.*:.*\\}");

  private final ProfileConnector connector;
  private final Context context;

  public InstrumentedTestUtilities(Context context, ProfileConnector connector) {
    if (context == null || connector == null) {
      throw new NullPointerException();
    }
    this.context = context;
    this.connector = connector;
  }

  /**
   * Ensure that a work profile exists, this app is installed in both profiles, a relevant
   * permission is granted for this app, and the work profile is unlocked.
   */
  public void ensureReadyForCrossProfileCalls() {
    ensureReadyForCrossProfileCalls(context.getPackageName());
  }

  /**
   * Ensure that a work profile exists, the given package is installed in both profiles, a relevant
   * permission is granted for the app, and the work profile is unlocked.
   */
  public void ensureReadyForCrossProfileCalls(String packageName) {
    ensureWorkProfileExists();

    if (!packageName.equals(context.getPackageName())) {
      // ensureWorkProfileExists will install the test package
      installInWorkProfile(packageName);
    }

    int workProfileUserId = getWorkProfileUserId();
    startUser(workProfileUserId);

    grantInteractAcrossUsers(packageName);

    ProfileAvailabilityPoll.blockUntilUserRunningAndUnlocked(context, getWorkProfileUserHandle());
  }

  private UserHandle getWorkProfileUserHandle() {
    try {
      return (UserHandle)
          UserHandle.class
              .getMethod("of", int.class)
              .invoke(/* object= */ null, getWorkProfileUserId());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Error getting current user handle", e);
    }
  }

  /**
   * Create a work profile if one does not exist.
   *
   * <p>This will also install this app into the new profile and set it as the profile owner.
   */
  public void ensureWorkProfileExists() {
    if (hasWorkProfile()) {
      return;
    }

    createWorkProfile();
  }

  private static void grantInteractAcrossUsers(String packageName) {
    runCommandWithOutput("pm grant " + packageName + " android.permission.INTERACT_ACROSS_USERS");
    runCommandWithOutput(
        "pm grant " + packageName + " android.permission.INTERACT_ACROSS_PROFILES");
  }

  /** Remove a work profile if one exists. */
  public void ensureNoWorkProfile() {
    if (hasWorkProfile()) {
      removeWorkProfile();
    }
  }

  /**
   * Create a work profile.
   *
   * <p>This will install this app in the profile and set it as profile owner.
   *
   * <p>If {@link #hasWorkProfile()} returns true then an {@link IllegalStateException} will be
   * thrown.
   */
  private void createWorkProfile() {
    if (hasWorkProfile()) {
      throw new IllegalStateException(
          "There is already a work profile on the device with user id "
              + getWorkProfileUserId()
              + ".");
    }
    runCommandWithOutput("pm create-user --profileOf 0 --managed TestProfile123");
    BlockingPoll.poll(this::hasWorkProfile, 100, 10000);
    installInWorkProfile(context.getPackageName());
    int workProfileUserId = getWorkProfileUserId();
    startUser(workProfileUserId);
    setTestAsProfileOwner(workProfileUserId);
  }

  private static void startUser(int userId) {
    runCommandWithOutput("am start-user " + userId);
  }

  private void setTestAsProfileOwner(int userId) {
    runCommandWithOutput(
        "dpm set-profile-owner --user "
            + userId
            + " "
            + context.getPackageName()
            + "/com.google.android.enterprise.connectedapps.testing.DeviceAdminReceiver");
  }

  private void removeWorkProfile() {
    removeUser(getWorkProfileUserId());
  }

  private static void removeUser(int userId) {
    runCommandWithOutput("pm remove-user " + userId);
  }

  /** Install this app in the work profile. */
  private void installInWorkProfile(String packageName) {
    if (!hasWorkProfile()) {
      throw new IllegalStateException("There is no work profile on the device.");
    }

    installInUser(getWorkProfileUserId(), packageName);
  }

  private static void installInUser(int userId, String packageName) {
    runCommandWithOutput("cmd package install-existing --user " + userId + " " + packageName);
  }

  /** Return true if a work profile exists on the device. */
  public boolean hasWorkProfile() {
    try {
      getWorkProfileUserId();
      return true;
    } catch (IllegalStateException e) {
      Log.i(LOG_TAG, "hasWorkProfile() found no work profile", e);
      return false;
    }
  }

  /**
   * Get the user ID of the work profile.
   *
   * <p>If there is no work profile, an {@link IllegalStateException} will be thrown.
   */
  public int getWorkProfileUserId() {
    String userList = runCommandWithOutput("pm list users");

    // TODO(162219825): Instead of assuming the first non-system user is managed, actually check
    Matcher matcher = USERINFO_PATTERN.matcher(userList);

    while (matcher.find()) {
      int userId = Integer.parseInt(matcher.group(1));
      if (userId != 0) {
        // Skip system user
        return userId;
      }
    }

    throw new IllegalStateException("No non-system user found: " + userList);
  }

  /**
   * Block until the given {@link ProfileConnector} has disconnected.
   *
   * <p>This should not be run from the UI thread.
   */
  public void waitForDisconnected() {
    CountDownLatch connectionLatch = new CountDownLatch(1);

    ConnectionListener connectionListener =
        () -> {
          if (!connector.isConnected()) {
            connectionLatch.countDown();
          }
        };

    connector.addConnectionListener(connectionListener);
    connectionListener.connectionChanged();

    try {
      connectionLatch.await();
    } catch (InterruptedException e) {
      throw new AssertionError("Error waiting to disconnect", e);
    }

    connector.removeConnectionListener(connectionListener);
  }

  /**
   * Block until the given {@link ProfileConnector} has connected.
   *
   * <p>This should not be run from the UI thread.
   */
  public void waitForConnected() {
    CountDownLatch connectionLatch = new CountDownLatch(1);

    ConnectionListener connectionListener =
        () -> {
          if (connector.isConnected()) {
            connectionLatch.countDown();
          }
        };

    connector.addConnectionListener(connectionListener);
    connectionListener.connectionChanged();

    try {
      connectionLatch.await();
    } catch (InterruptedException e) {
      throw new AssertionError("Error waiting to connect", e);
    }

    connector.removeConnectionListener(connectionListener);
  }

  private static String runCommandWithOutput(String command) {
    try (ParcelFileDescriptor p = runCommand(command);
        InputStream inputStream = new FileInputStream(p.getFileDescriptor());
        Scanner scanner = new Scanner(inputStream, UTF_8.name())) {
      return scanner.useDelimiter("\\A").next();
    } catch (NoSuchElementException e) {
      // Empty output
      return "";
    } catch (IOException e) {
      throw new IllegalStateException("Error getting command output", e);
    }
  }

  private static ParcelFileDescriptor runCommand(String command) {
    return InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .executeShellCommand(command);
  }
}

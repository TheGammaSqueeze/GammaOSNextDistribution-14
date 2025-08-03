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

import android.content.Context;
import com.google.android.enterprise.connectedapps.AvailabilityListener;
import com.google.android.enterprise.connectedapps.ConnectedAppsUtils;
import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.CrossProfileSender;
import com.google.android.enterprise.connectedapps.Permissions;
import com.google.android.enterprise.connectedapps.ProfileConnectionHolder;
import com.google.android.enterprise.connectedapps.ProfileConnector;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

/**
 * A fake {@link ProfileConnector} for use in tests.
 *
 * <p>This should be extended to make it compatible with a specific {@link ProfileConnector}
 * interface.
 */
public abstract class AbstractFakeProfileConnector implements FakeProfileConnector {

  enum WorkProfileState {
    DOES_NOT_EXIST,
    TURNED_OFF,
    TURNED_ON
  }

  private final Context applicationContext;
  private final ProfileType primaryProfileType;
  private ProfileType runningOnProfile = ProfileType.PERSONAL;
  private WorkProfileState workProfileState = WorkProfileState.DOES_NOT_EXIST;
  private boolean isConnected = false;
  private boolean hasPermissionToMakeCrossProfileCalls = true;
  private ConnectionHandler connectionHandler = () -> true;
  private Executor executor = Runnable::run;

  private final Set<Object> connectionHolders = Collections.newSetFromMap(new WeakHashMap<>());
  private final Map<Object, Set<Object>> connectionHolderAliases = new WeakHashMap<>();
  private final Set<ConnectionListener> connectionListeners = new HashSet<>();
  private final Set<AvailabilityListener> availabilityListeners = new HashSet<>();

  public AbstractFakeProfileConnector(Context context, ProfileType primaryProfileType) {
    if (context == null || primaryProfileType == null) {
      throw new NullPointerException();
    }
    this.applicationContext = context.getApplicationContext();
    this.primaryProfileType = primaryProfileType;
  }

  /**
   * Simulate running on a particular profile type.
   *
   * <p>If {@code currentProfile} is {@link ProfileType#WORK} and a work profile does not exist or
   * is not turned on, then a work profile will be created and turned on.
   *
   * @see #runningOnProfile
   */
  public void setRunningOnProfile(ProfileType currentProfile) {
    if (currentProfile == ProfileType.WORK && workProfileState != WorkProfileState.TURNED_ON) {
      turnOnWorkProfile();
    }
    this.runningOnProfile = currentProfile;
  }

  /**
   * Get the current profile type being simulated.
   *
   * @see #setRunningOnProfile(ProfileType)
   */
  public ProfileType runningOnProfile() {
    return runningOnProfile;
  }

  /**
   * Simulate the creation of a work profile.
   *
   * <p>The new work profile will be turned off by default.
   */
  public void createWorkProfile() {
    if (workProfileState != WorkProfileState.DOES_NOT_EXIST) {
      return;
    }
    this.workProfileState = WorkProfileState.TURNED_OFF;
  }

  /**
   * Remove a simulated work profile.
   *
   * <p>The simulated work profile will be turned off first.
   */
  public void removeWorkProfile() {
    if (workProfileState == WorkProfileState.DOES_NOT_EXIST) {
      return;
    }

    turnOffWorkProfile();
    this.workProfileState = WorkProfileState.DOES_NOT_EXIST;
  }

  /**
   * Simulate a work profile being turned on.
   *
   * <p>If no simulated work profile exists, then it will be created.
   */
  public void turnOnWorkProfile() {
    if (workProfileState == WorkProfileState.TURNED_ON) {
      return;
    }
    if (workProfileState == WorkProfileState.DOES_NOT_EXIST) {
      createWorkProfile();
    }
    workProfileState = WorkProfileState.TURNED_ON;
    notifyAvailabilityChanged();
  }

  /**
   * Simulate a work profile being turned off.
   *
   * <p>If no simulated work profile exists, then it will be created.
   *
   * <p>This fake will also be set to simulate running on the personal profile for future calls.
   */
  public void turnOffWorkProfile() {
    if (workProfileState == WorkProfileState.DOES_NOT_EXIST) {
      createWorkProfile();
    }
    setRunningOnProfile(ProfileType.PERSONAL);
    if (workProfileState == WorkProfileState.TURNED_OFF) {
      return;
    }

    if (isConnected) {
      isConnected = false;
      notifyConnectionChanged();
    }

    workProfileState = WorkProfileState.TURNED_OFF;
    notifyAvailabilityChanged();
  }

  @Override
  public void setConnectionHandler(ConnectionHandler connectionHandler) {
    this.connectionHandler = connectionHandler;
  }

  @Override
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void automaticallyConnect() {
    if (hasPermissionToMakeCrossProfileCalls
        && isAvailable()
        && !isConnected
        && connectionHandler.tryConnect()) {
      isConnected = true;
      notifyConnectionChanged();
    }
  }

  @Override
  public void timeoutConnection() {
    if (!connectionHolders.isEmpty()) {
      return;
    }

    if (isConnected) {
      isConnected = false;
      notifyConnectionChanged();
    }
  }

  /**
   * This fake does not enforce the requirement that calls to {@link #connect()} do not occur on the
   * UI Thread.
   */
  @Override
  public ProfileConnectionHolder connect() throws UnavailableProfileException {
    return connect(CrossProfileSender.MANUAL_MANAGEMENT_CONNECTION_HOLDER);
  }

  @Override
  public ProfileConnectionHolder connect(Object connectionHolder)
      throws UnavailableProfileException {
    if (!hasPermissionToMakeCrossProfileCalls || !isAvailable()) {
      throw new UnavailableProfileException("No profile available");
    }

    connectionHolders.add(connectionHolder);
    automaticallyConnect();

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  /** Stop manually managing the connection and ensure that the connector is disconnected. */
  public void disconnect() {
    connectionHolders.clear();
    timeoutConnection();
  }

  /** Unsupported by the fake so always returns {@code null}. */
  @Override
  public CrossProfileSender crossProfileSender() {
    return null;
  }

  @Override
  public void addConnectionListener(ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  @Override
  public void removeConnectionListener(ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  private void notifyConnectionChanged() {
    for (ConnectionListener listener : connectionListeners) {
      listener.connectionChanged();
    }
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    availabilityListeners.add(listener);
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    availabilityListeners.remove(listener);
  }

  private void notifyAvailabilityChanged() {
    for (AvailabilityListener listener : availabilityListeners) {
      listener.availabilityChanged();
    }
  }

  @Override
  public boolean isAvailable() {
    return (runningOnProfile == ProfileType.WORK || workProfileState == WorkProfileState.TURNED_ON);
  }

  @Override
  public boolean isConnected() {
    return isConnected;
  }

  @Override
  public ConnectedAppsUtils utils() {
    return new FakeConnectedAppsUtils(this, primaryProfileType);
  }

  @Override
  public Permissions permissions() {
    return new FakeCrossProfilePermissions(this);
  }

  @Override
  public Context applicationContext() {
    return applicationContext;
  }

  /**
   * Set whether or not the app has been given the appropriate permission to make cross-profile
   * calls.
   */
  public void setHasPermissionToMakeCrossProfileCalls(
      boolean hasPermissionToMakeCrossProfileCalls) {
    this.hasPermissionToMakeCrossProfileCalls = hasPermissionToMakeCrossProfileCalls;
  }

  boolean hasPermissionToMakeCrossProfileCalls() {
    return hasPermissionToMakeCrossProfileCalls;
  }

  @Override
  public ProfileConnectionHolder addConnectionHolder(Object connectionHolder) {
    connectionHolders.add(connectionHolder);
    executor.execute(this::automaticallyConnect);

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(Object key, Object value) {
    if (!connectionHolderAliases.containsKey(key)) {
      connectionHolderAliases.put(key, Collections.newSetFromMap(new WeakHashMap<>()));
    }

    connectionHolderAliases.get(key).add(value);
  }

  @Override
  public void removeConnectionHolder(Object connectionHolder) {
    if (connectionHolderAliases.containsKey(connectionHolder)) {
      Set<Object> aliases = connectionHolderAliases.get(connectionHolder);
      connectionHolderAliases.remove(connectionHolder);
      for (Object alias : aliases) {
        removeConnectionHolder(alias);
      }
    }

    connectionHolders.remove(connectionHolder);
  }

  @Override
  public void clearConnectionHolders() {
    connectionHolderAliases.clear();
    connectionHolders.clear();
  }

  @Override
  public boolean hasExplicitConnectionHolders() {
    return !connectionHolders.isEmpty();
  }
}

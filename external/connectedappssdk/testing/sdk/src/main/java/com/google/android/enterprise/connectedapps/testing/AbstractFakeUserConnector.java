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
import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.AvailabilityListener;
import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.CrossProfileSender;
import com.google.android.enterprise.connectedapps.Permissions;
import com.google.android.enterprise.connectedapps.UserConnectionHolder;
import com.google.android.enterprise.connectedapps.UserConnector;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A fake {@link UserConnector} for use in tests.
 *
 * <p>This should be extended to make it compatible with a specific {@link UserConnector} interface.
 */
public abstract class AbstractFakeUserConnector implements FakeUserConnector {

  private static final class SpecificUserConnector {

    enum UserState {
      DOES_NOT_EXIST,
      TURNED_OFF,
      TURNED_ON
    }

    private UserState userState = UserState.DOES_NOT_EXIST;
    private boolean isConnected = false;
    private boolean hasPermissionToMakeCrossUserCalls = true;

    private final Set<Object> connectionHolders = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, Set<Object>> connectionHolderAliases = new WeakHashMap<>();
    private final Set<ConnectionListener> connectionListeners = new HashSet<>();
    private final Set<AvailabilityListener> availabilityListeners = new HashSet<>();

    private void createUser() {
      if (userState == UserState.DOES_NOT_EXIST) {
        userState = UserState.TURNED_OFF;
      }
    }

    private void removeUser() {
      turnOffUser();
      userState = UserState.DOES_NOT_EXIST;
    }

    private void turnOnUser() {
      if (userState == UserState.TURNED_ON) {
        return;
      }

      if (userState == UserState.DOES_NOT_EXIST) {
        createUser();
      }

      userState = UserState.TURNED_ON;
      notifyAvailabilityChanged();
    }

    private void turnOffUser() {
      if (userState == UserState.TURNED_OFF) {
        return;
      }

      if (userState == UserState.DOES_NOT_EXIST) {
        createUser();
      }

      if (isConnected) {
        isConnected = false;
        notifyConnectionChanged();
      }

      userState = UserState.TURNED_OFF;
      notifyAvailabilityChanged();
    }

    private boolean isConnected() {
      return isConnected;
    }

    private void connect(Object connectionHolder) {
      addConnectionHolder(connectionHolder);
    }

    private void timeoutConnection() {
      if (!connectionHolders.isEmpty()) {
        return;
      }

      if (isConnected) {
        isConnected = false;
        notifyConnectionChanged();
      }
    }

    private void automaticallyConnect() {
      if (isAvailable() && !isConnected) {
        isConnected = true;
        notifyConnectionChanged();
      }
    }

    private void disconnect() {
      connectionHolders.clear();
      timeoutConnection();
    }

    private boolean isAvailable() {
      return userState == UserState.TURNED_ON;
    }

    private void addConnectionListener(ConnectionListener listener) {
      connectionListeners.add(listener);
    }

    private void removeConnectionListener(ConnectionListener listener) {
      connectionListeners.remove(listener);
    }

    private void notifyConnectionChanged() {
      for (ConnectionListener listener : connectionListeners) {
        listener.connectionChanged();
      }
    }

    private void addAvailabilityListener(AvailabilityListener listener) {
      availabilityListeners.add(listener);
    }

    private void removeAvailabilityListener(AvailabilityListener listener) {
      availabilityListeners.remove(listener);
    }

    private void notifyAvailabilityChanged() {
      for (AvailabilityListener listener : availabilityListeners) {
        listener.availabilityChanged();
      }
    }

    private void addConnectionHolder(Object connectionHolder) {
      connectionHolders.add(connectionHolder);
      automaticallyConnect();
    }

    private void addConnectionHolderAlias(Object key, Object value) {
      if (!connectionHolderAliases.containsKey(key)) {
        connectionHolderAliases.put(key, Collections.newSetFromMap(new WeakHashMap<>()));
      }

      connectionHolderAliases.get(key).add(value);
    }

    private void removeConnectionHolder(Object connectionHolder) {
      if (connectionHolderAliases.containsKey(connectionHolder)) {
        Set<Object> aliases = connectionHolderAliases.get(connectionHolder);
        connectionHolderAliases.remove(connectionHolder);
        for (Object alias : aliases) {
          removeConnectionHolder(alias);
        }
      }

      connectionHolders.remove(connectionHolder);
    }

    private void clearConnectionHolders() {
      connectionHolderAliases.clear();
      ;
      connectionHolders.clear();
    }

    private boolean hasExplicitConnectionHolders() {
      return !connectionHolders.isEmpty();
    }
  }

  private final Context applicationContext;

  private final Map<UserHandle, SpecificUserConnector> specificUsers = new HashMap<>();

  private UserHandle currentUser;

  public AbstractFakeUserConnector(Context context) {
    if (context == null) {
      throw new NullPointerException();
    }

    this.applicationContext = context.getApplicationContext();
  }

  /**
   * Simulate running on a particular user.
   *
   * <p>If {@code currentUser} does not exist or is not turned on, then it will be created and
   * turned on.
   *
   * @see #runningOnUser
   */
  public void setRunningOnUser(UserHandle currentUser) {
    this.currentUser = currentUser;
    connectorFor(currentUser).turnOnUser();
  }

  /**
   * Get the current user being simulated.
   *
   * @see #setRunningOnUser(UserHandle)
   */
  public UserHandle runningOnUser() {
    return currentUser;
  }

  /**
   * Simulate the creation of a user.
   *
   * <p>The new user will be turned off by default.
   */
  public void createUser(UserHandle userHandle) {
    connectorFor(userHandle).createUser();
  }

  /**
   * Remove a simulated user.
   *
   * <p>The simulated user will be turned off first.
   */
  public void removeUser(UserHandle userHandle) {
    connectorFor(userHandle).removeUser();
  }

  /**
   * Simulate a user being turned on.
   *
   * <p>If no such user exists, then it will be created.
   */
  public void turnOnUser(UserHandle userHandle) {
    connectorFor(userHandle).turnOnUser();
  }

  /**
   * Simulate a user being turned off.
   *
   * <p>If no such user exists, then it will be created.
   */
  public void turnOffUser(UserHandle userHandle) {
    connectorFor(userHandle).turnOffUser();
  }

  /**
   * Force the connector to be "automatically" connected.
   *
   * <p>This call should only be used by the SDK and should not be called in tests. If you want to
   * connect manually, use {@link #addConnectionHolder(UserHandle, Object)}, or for automatic
   * management just make the asynchronous call directly.
   *
   * @hide
   */
  public void automaticallyConnect(UserHandle userHandle) {
    connectorFor(userHandle).automaticallyConnect();
  }

  /**
   * Disconnect after an automatic connection.
   *
   * <p>In reality, this timeout happens some arbitrary time of no interaction with the other
   * profile.
   *
   * <p>If there are connection holders, then this will do nothing.
   */
  public void timeoutConnection(UserHandle userHandle) {
    connectorFor(userHandle).timeoutConnection();
  }

  /**
   * This fake does not enforce the requirement that calls to {@link #connect(UserHandle)} do not
   * occur on the UI Thread.
   */
  @Override
  public UserConnectionHolder connect(UserHandle userHandle) throws UnavailableProfileException {
    return connect(userHandle, new Object());
  }

  @Override
  public UserConnectionHolder connect(UserHandle userHandle, Object connectionHolder)

      throws UnavailableProfileException {
    if (!isAvailable(userHandle)) {
      throw new UnavailableProfileException("User not available");
    }

    connectorFor(userHandle).connect(connectionHolder);

    return UserConnectionHolder.create(this, userHandle, connectionHolder);
  }

  /** Stop manually managing the connection and ensure that the connector is disconnected. */
  public void disconnect(UserHandle userHandle) {
    connectorFor(userHandle).disconnect();
  }

  /** Unsupported by the fake so always returns {@code null}. */
  @Override
  public CrossProfileSender crossProfileSender(UserHandle userHandle) {
    return null;
  }

  @Override
  public void addConnectionListener(UserHandle userHandle, ConnectionListener listener) {
    connectorFor(userHandle).addConnectionListener(listener);
  }

  @Override
  public void removeConnectionListener(UserHandle userHandle, ConnectionListener listener) {
    connectorFor(userHandle).removeConnectionListener(listener);
  }

  @Override
  public void addAvailabilityListener(UserHandle userHandle, AvailabilityListener listener) {
    connectorFor(userHandle).addAvailabilityListener(listener);
  }

  @Override
  public void removeAvailabilityListener(UserHandle userHandle, AvailabilityListener listener) {
    connectorFor(userHandle).removeAvailabilityListener(listener);
  }

  @Override
  public boolean isAvailable(UserHandle userHandle) {
    return connectorFor(userHandle).isAvailable();
  }

  @Override
  public boolean isConnected(UserHandle userHandle) {
    return connectorFor(userHandle).isConnected();
  }

  @Override
  public Permissions permissions(UserHandle userHandle) {
    return new FakeCrossUserPermissions(this, userHandle);
  }

  @Override
  public Context applicationContext(UserHandle userHandle) {
    return applicationContext;
  }

  /**
   * Set whether or not the app has been given the appropriate permission to make cross-user calls.
   */
  public void setHasPermissionToMakeCrossProfileCalls(
      UserHandle userHandle, boolean hasPermissionToMakeCrossUserCalls) {
    connectorFor(userHandle).hasPermissionToMakeCrossUserCalls = hasPermissionToMakeCrossUserCalls;
  }

  boolean hasPermissionToMakeCrossProfileCalls(UserHandle userHandle) {
    return connectorFor(userHandle).hasPermissionToMakeCrossUserCalls;
  }

  private SpecificUserConnector connectorFor(UserHandle userHandle) {
    if (!specificUsers.containsKey(userHandle)) {
      specificUsers.put(userHandle, new SpecificUserConnector());
    }

    return specificUsers.get(userHandle);
  }

  @Override
  public UserConnectionHolder addConnectionHolder(UserHandle userHandle, Object connectionHolder) {
    connectorFor(userHandle).addConnectionHolder(connectionHolder);

    return UserConnectionHolder.create(this, userHandle, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(UserHandle userHandle, Object key, Object value) {
    connectorFor(userHandle).addConnectionHolderAlias(key, value);
  }

  @Override
  public void removeConnectionHolder(UserHandle userHandle, Object connectionHolder) {
    connectorFor(userHandle).removeConnectionHolder(connectionHolder);
  }

  @Override
  public void clearConnectionHolders(UserHandle userHandle) {
    connectorFor(userHandle).clearConnectionHolders();
  }

  @Override
  public boolean hasExplicitConnectionHolders(UserHandle userHandle) {
    return connectorFor(userHandle).hasExplicitConnectionHolders();
  }
}

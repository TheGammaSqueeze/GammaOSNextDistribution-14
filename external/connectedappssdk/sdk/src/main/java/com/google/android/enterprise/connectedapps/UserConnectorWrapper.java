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

import android.content.Context;
import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

/**
 * A compatibility wrapper which allows a {@link UserConnector} to be used in generated code where a
 * {@link ProfileConnector} is expected.
 */
public class UserConnectorWrapper implements ProfileConnector {

  private final UserConnector userConnector;

  private final UserHandle userHandle;

  public UserConnectorWrapper(UserConnector userConnector, UserHandle userHandle) {
    this.userConnector = userConnector;
    this.userHandle = userHandle;
  }

  @Override
  public ProfileConnectionHolder connect() throws UnavailableProfileException {
    return connect(CrossProfileSender.MANUAL_MANAGEMENT_CONNECTION_HOLDER);
  }

  @Override

  public ProfileConnectionHolder connect(Object connectionHolder)
      throws UnavailableProfileException {

    userConnector.connect(userHandle, connectionHolder);


    return ProfileConnectionHolder.create(this, connectionHolder);

  }

  @Override
  public CrossProfileSender crossProfileSender() {
    return userConnector.crossProfileSender(userHandle);
  }

  @Override
  public void addConnectionListener(ConnectionListener listener) {
    userConnector.addConnectionListener(userHandle, listener);
  }

  @Override
  public void removeConnectionListener(ConnectionListener listener) {
    userConnector.removeConnectionListener(userHandle, listener);
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    userConnector.addAvailabilityListener(userHandle, listener);
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    userConnector.removeAvailabilityListener(userHandle, listener);
  }

  @Override
  public boolean isAvailable() {
    return userConnector.isAvailable(userHandle);
  }

  @Override
  public boolean isConnected() {
    return userConnector.isConnected(userHandle);
  }

  @Override
  public ConnectedAppsUtils utils() {
    throw new UnsupportedOperationException(
        "Cannot get ConnectedAppsUtils for a cross-user connector.");
  }

  @Override
  public Permissions permissions() {
    return userConnector.permissions(userHandle);
  }

  @Override
  public Context applicationContext() {
    return userConnector.applicationContext(userHandle);
  }

  @Override
  public ProfileConnectionHolder addConnectionHolder(Object connectionHolder) {
    userConnector.addConnectionHolder(userHandle, connectionHolder);

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(Object key, Object value) {
    userConnector.addConnectionHolderAlias(userHandle, key, value);
  }

  @Override
  public void removeConnectionHolder(Object connectionHolder) {
    userConnector.removeConnectionHolder(userHandle, connectionHolder);
  }

  @Override
  public void clearConnectionHolders() {
    userConnector.clearConnectionHolders(userHandle);
  }
}

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
import com.google.android.enterprise.connectedapps.ConnectedAppsUtils;
import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.CrossProfileSender;
import com.google.android.enterprise.connectedapps.CrossUserConnector;
import com.google.android.enterprise.connectedapps.Permissions;
import com.google.android.enterprise.connectedapps.ProfileConnectionHolder;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.util.concurrent.Executor;

/**
 * A compatibility wrapper which allows an {@link AbstractFakeUserConnector} to be used in generated
 * code where a {@link FakeProfileConnector} is expected.
 */
public class FakeUserConnectorWrapper implements FakeProfileConnector {

  private final AbstractFakeUserConnector connector;

  private final UserHandle handle;

  public FakeUserConnectorWrapper(AbstractFakeUserConnector connector, UserHandle handle) {
    this.connector = connector;
    this.handle = handle;
  }

  @Override
  public void timeoutConnection() {
    connector.timeoutConnection(handle);
  }

  @Override
  public void setConnectionHandler(ConnectionHandler connectionHandler) {
    // TODO(b/214511168): Implement it
    throw new UnsupportedOperationException("Cannot set ConnectionHandler for UserConnector");
  }

  @Override
  public void setExecutor(Executor executor) {
    // TODO(b/214511168): Implement it
    throw new UnsupportedOperationException("Cannot set Executor for UserConnector");
  }

  @Override
  public void automaticallyConnect() {
    connector.automaticallyConnect(handle);
  }

  @Override
  public ProfileConnectionHolder connect(Object connectionHolder)
      throws UnavailableProfileException {
    return addConnectionHolder(connectionHolder);
  }

  @Override
  public ProfileConnectionHolder connect() throws UnavailableProfileException {
    return connect(CrossProfileSender.MANUAL_MANAGEMENT_CONNECTION_HOLDER);
  }

  @Override
  public CrossProfileSender crossProfileSender() {
    return connector.crossProfileSender(handle);
  }

  @Override
  public void addConnectionListener(ConnectionListener listener) {
    connector.addConnectionListener(handle, listener);
  }

  @Override
  public void removeConnectionListener(ConnectionListener listener) {
    connector.removeConnectionHolder(handle, listener);
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    connector.addAvailabilityListener(handle, listener);
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    connector.removeAvailabilityListener(handle, listener);
  }

  @Override
  public boolean isAvailable() {
    return connector.isAvailable(handle);
  }

  @Override
  public boolean isConnected() {
    return connector.isConnected(handle);
  }

  /**
   * Unsupported for {@link FakeUserConnectorWrapper} as unsupported by all {@link
   * CrossUserConnector}s.
   */
  @Override
  public ConnectedAppsUtils utils() {
    throw new UnsupportedOperationException("Cannot get ConnectedAppsUtils for UserConnector");
  }

  @Override
  public Permissions permissions() {
    return connector.permissions(handle);
  }

  @Override
  public Context applicationContext() {
    return connector.applicationContext(handle);
  }

  @Override
  public ProfileConnectionHolder addConnectionHolder(Object connectionHolder) {
    connector.addConnectionHolder(handle, connectionHolder);

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(Object key, Object value) {
    connector.addConnectionHolderAlias(handle, key, value);
  }

  @Override
  public void removeConnectionHolder(Object connectionHolder) {
    connector.removeConnectionHolder(handle, connectionHolder);
  }

  @Override
  public void clearConnectionHolders() {
    connector.clearConnectionHolders(handle);
  }

  @Override
  public boolean hasExplicitConnectionHolders() {
    return connector.hasExplicitConnectionHolders(handle);
  }
}

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
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

/** A {@link ProfileConnector} is used to manage the connection between profiles. */
public interface ProfileConnector {
  /**
   * Execute {@link #connect(Object)} with a new connection holder.
   *
   * <p>You must use {@link #removeConnectionHolder(Object)} with the returned {@link
   * ProfileConnectionHolder} or call {@link ProfileConnectionHolder#close()} when you are finished
   * with the connection.
   */
  ProfileConnectionHolder connect() throws UnavailableProfileException;

  /**
   * Attempt to connect to the other profile and add a connection holder.
   *
   * <p>This will mean that the connection will not be dropped automatically to save resources.
   *
   * <p>This must not be called from the main thread.
   *
   * <p>You must remove the connection holder once you have finished with it. See {@link
   * #removeConnectionHolder(Object)}.
   *
   * <p>Returns a {@link ProfileConnectionHolder} which can be used to automatically remove this
   * connection holder using try-with-resources. Either the {@link ProfileConnectionHolder} or the
   * passed in {@code connectionHolder} can be used with {@link #removeConnectionHolder(Object)}.
   *
   * @throws UnavailableProfileException If the connection cannot be made.
   */
  ProfileConnectionHolder connect(Object connectionHolder) throws UnavailableProfileException;

  /**
   * Return the {@link CrossProfileSender} being used for this connection.
   *
   * <p>This API should only be used by generated code.
   */
  CrossProfileSender crossProfileSender();

  /**
   * Add a listener to be called when a profile is connected or disconnected.
   *
   * <p>{@link #isConnected()} can be called to check if a connection is established.
   *
   * @see #removeConnectionListener(ConnectionListener)
   */
  void addConnectionListener(ConnectionListener listener);

  /** Remove a listener added using {@link #addConnectionListener(ConnectionListener)}. */
  void removeConnectionListener(ConnectionListener listener);

  /**
   * Add a listener to be called when a profile becomes available or unavailable.
   *
   * <p>{@link #isAvailable()} can be called to check if a profile is available.
   *
   * @see #removeAvailabilityListener(AvailabilityListener)
   */
  void addAvailabilityListener(AvailabilityListener listener);

  /** Remove a listener registered using {@link #addAvailabilityListener( AvailabilityListener)}. */
  void removeAvailabilityListener(AvailabilityListener listener);

  /**
   * Return true if there is another profile which could be connected to.
   *
   * <p>If this returns true, then asynchronous calls should succeed. Synchronous calls will only
   * succeed if {@link #isConnected()} also returns true.
   */
  boolean isAvailable();

  /**
   * Return true if there is another profile connected.
   *
   * <p>If this returns true, then synchronous calls should succeed unless they are disconnected
   * before the call completes.
   */
  boolean isConnected();

  /** Return an instance of {@link ConnectedAppsUtils} for dealing with this connection. */
  ConnectedAppsUtils utils();

  Permissions permissions();

  /** Return the application context used by this connector. */
  Context applicationContext();

  /**
   * Register an object as holding the connection open.
   *
   * <p>While there is at least one connection holder, the connected apps SDK will attempt to stay
   * connected.
   *
   * <p>You must remove the connection holder once you have finished with it. See {@link
   * #removeConnectionHolder(Object)}.
   *
   * <p>Returns a {@link ProfileConnectionHolder} which can be used to automatically remove this
   * connection holder using try-with-resources. Either the {@link ProfileConnectionHolder} or the
   * passed in {@code connectionHolder} can be used with {@link #removeConnectionHolder(Object)}.
   */
  ProfileConnectionHolder addConnectionHolder(Object connectionHolder);

  /**
   * Registers a connection holder alias.
   *
   * <p>This means that if the key is removed, then the value will also be removed. If the value is
   * removed, the key will not be removed.
   */
  void addConnectionHolderAlias(Object key, Object value);

  /**
   * Remove a connection holder.
   *
   * <p>Once there are no remaining connection holders, the connection will be able to be closed.
   *
   * <p>See {@link #addConnectionHolder(Object)}.
   */
  void removeConnectionHolder(Object connectionHolder);

  void clearConnectionHolders();
}

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

/** A {@link UserConnector} is used to manage the connection between users. */
public interface UserConnector {

  /**
   * Execute {@link #connect(UserHandle, Object)} with a new connection holder.
   *
   * <p>You must use {@link #removeConnectionHolder(UserHandle, Object)} with the returned {@link
   * UserConnectionHolder} or call {@link UserConnectionHolder#close()} when you are finished with
   * the connection.
   */
  UserConnectionHolder connect(UserHandle userHandle) throws UnavailableProfileException;

  /**
   * Attempt to connect to the other user and add a connection holder.
   *
   * <p>This will mean that the connection will not be dropped automatically to save resources.
   *
   * <p>This must not be called from the main thread.
   *
   * <p>You must remove the connection holder once you have finished with it. See {@link
   * #removeConnectionHolder(UserHandle, Object)}.
   *
   * <p>Returns a {@link UserConnectionHolder} which can be used to automatically remove this
   * connection holder using try-with-resources. Either the {@link UserConnectionHolder} or the
   * passed in {@code connectionHolder} can be used with {@link #removeConnectionHolder(UserHandle,
   * Object)}.
   *
   * @throws UnavailableProfileException If the connection cannot be made.
   */
  UserConnectionHolder connect(UserHandle userHandle, Object connectionHolder)
      throws UnavailableProfileException;

  /**
   * Return the {@link CrossProfileSender} being used for the connection to the user.
   *
   * <p>This API should only be used by generated code.
   */
  CrossProfileSender crossProfileSender(UserHandle userHandle);

  /**
   * Add a listener to be called when the user is connected or disconnected.
   *
   * <p>{@link #isConnected(UserHandle)} can be called to check if a connection is established.
   *
   * @see #removeConnectionListener(UserHandle, ConnectionListener)
   */
  void addConnectionListener(UserHandle userHandle, ConnectionListener listener);

  /**
   * Remove a listener registered using {@link #addConnectionListener(UserHandle,
   * ConnectionListener)}.
   */
  void removeConnectionListener(UserHandle userHandle, ConnectionListener listener);

  /**
   * Add a listener to be called when a user becomes available or unavailable.
   *
   * <p>{@link #isAvailable(UserHandle)} can be called to check if a user is available.
   *
   * @see #removeAvailabilityListener(UserHandle, AvailabilityListener)
   */
  void addAvailabilityListener(UserHandle userHandle, AvailabilityListener listener);

  /**
   * Remove a listener registered using {@link #addAvailabilityListener(UserHandle,
   * AvailabilityListener)}.
   */
  void removeAvailabilityListener(UserHandle userHandle, AvailabilityListener listener);

  /**
   * Return true if the user can be connected to.
   *
   * <p>If this returns true, then asynchronous calls should succeed. Synchronous calls will only
   * succeed if {@link #isConnected(UserHandle)} also returns true.
   */
  boolean isAvailable(UserHandle userHandle);

  /**
   * Return true if the user is connected.
   *
   * <p>If this returns true, then synchronous calls should succeed unless they are disconnected
   * before the call completes.
   */
  boolean isConnected(UserHandle userHandle);

  Permissions permissions(UserHandle userHandle);

  /** Return the application context used by the user. */
  Context applicationContext(UserHandle userHandle);

  /**
   * Register an object as holding the connection open.
   *
   * <p>While there is at least one connection holder, the Connected Apps SDK will attempt to stay
   * connected.
   *
   * <p>You must remove the connection holder once you have finished with it. See {@link
   * #removeConnectionHolder(UserHandle, Object)}.
   *
   * <p>Returns a {@link UserConnectionHolder} which can be used to automatically remove this
   * connection holder using try-with-resources. Either the {@link UserConnectionHolder} or the
   * passed in {@code connectionHolder} can be used with {@link #removeConnectionHolder(UserHandle,
   * Object)}.
   */
  UserConnectionHolder addConnectionHolder(UserHandle userHandle, Object connectionHolder);

  /**
   * Registers a connection holder alias.
   *
   * <p>This means that if the key is removed, then the value will also be removed. If the value is
   * removed, the key will not be removed.
   */
  void addConnectionHolderAlias(UserHandle userHandle, Object key, Object value);

  /**
   * Remove a connection holder.
   *
   * <p>Once there are no remaining connection holders, the connection will be able to be closed.
   *
   * <p>See {@link #addConnectionHolder(UserHandle, Object)}.
   */
  void removeConnectionHolder(UserHandle userHandle, Object connectionHolder);

  void clearConnectionHolders(UserHandle userHandle);
}

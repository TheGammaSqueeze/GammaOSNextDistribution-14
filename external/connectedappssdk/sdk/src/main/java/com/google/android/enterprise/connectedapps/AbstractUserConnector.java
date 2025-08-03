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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Standard implementation of {@link UserConnector}. */
public abstract class AbstractUserConnector implements UserConnector {

  private final Context context;
  private final UserBinderFactory binderFactory;
  private final ScheduledExecutorService scheduledExecutorService;
  private final String serviceClassName;
  private final AvailabilityRestrictions availabilityRestrictions;

  private final Map<UserHandle, UserConnection> userConnections = new HashMap<>();

  private static final class UserConnection {
    private final ConnectionBinder binder;

    private final CrossProfileSender crossProfileSender;

    private final Set<ConnectionListener> connectionListeners;

    private final Set<AvailabilityListener> availabilityListeners;

    private UserConnection(
        ConnectionBinder binder,
        CrossProfileSender crossProfileSender,
        Set<ConnectionListener> connectionListeners,
        Set<AvailabilityListener> availabilityListeners) {
      this.binder = binder;
      this.crossProfileSender = crossProfileSender;
      this.connectionListeners = connectionListeners;
      this.availabilityListeners = availabilityListeners;
    }

    public CrossProfileSender crossProfileSender() {
      return crossProfileSender;
    }

    public Set<ConnectionListener> connectionListeners() {
      return connectionListeners;
    }

    public Set<AvailabilityListener> availabilityListeners() {
      return availabilityListeners;
    }

    public static UserConnection create(
        ConnectionBinder binder, CrossProfileSender crossProfileSender) {
      return new UserConnection(
          binder, crossProfileSender, new CopyOnWriteArraySet<>(), new CopyOnWriteArraySet<>());
    }
  }

  public AbstractUserConnector(Class<? extends UserConnector> userConnectorClass, Builder builder) {
    if (userConnectorClass == null || builder == null || builder.context == null) {
      throw new NullPointerException();
    }

    if (builder.binderFactory == null) {
      binderFactory = DefaultUserBinder::new;
    } else {
      binderFactory = builder.binderFactory;
    }

    if (builder.scheduledExecutorService == null) {
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    } else {
      scheduledExecutorService = builder.scheduledExecutorService;
    }

    context = builder.context.getApplicationContext();
    availabilityRestrictions = builder.availabilityRestrictions;

    if (builder.serviceClassName == null) {
      throw new NullPointerException("serviceClassName must be specified");
    }
    serviceClassName = builder.serviceClassName;
  }

  @Override
  public boolean isAvailable(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return crossProfileSender(userHandle).isBindingPossible();
  }

  @Override
  public UserConnectionHolder connect(UserHandle userHandle) throws UnavailableProfileException {
    return connect(userHandle, new Object());
  }

  @Override
  public UserConnectionHolder connect(UserHandle userHandle, Object connectionHolder)
      throws UnavailableProfileException {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      throw new UnavailableProfileException(
          "Cross-user calls are not supported on this version of Android");
    }
    crossProfileSender(userHandle).manuallyBind(connectionHolder);

    return UserConnectionHolder.create(this, userHandle, connectionHolder);
  }

  @Override
  public boolean isConnected(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return crossProfileSender(userHandle).isBound();
  }

  @Override
  public Permissions permissions(UserHandle userHandle) {
    return new PermissionsImpl(context, userConnection(userHandle).binder);
  }

  @Override
  public Context applicationContext(UserHandle userHandle) {
    return context;
  }

  @Override
  public CrossProfileSender crossProfileSender(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return null;
    }
    return userConnection(userHandle).crossProfileSender();
  }

  private UserConnection userConnection(UserHandle userHandle) {
    if (userConnections.containsKey(userHandle)) {
      return userConnections.get(userHandle);
    }

    ConnectionBinder binder = binderFactory.createBinder(userHandle);
    UserHandleEventForwarder userHandleEventForwarder =
        new UserHandleEventForwarder(this, userHandle);
    CrossProfileSender crossProfileSender =
        new CrossProfileSender(
            context.getApplicationContext(),
            serviceClassName,
            binder,
            /* connectionListener= */ userHandleEventForwarder,
            /* availabilityListener= */ userHandleEventForwarder,
            scheduledExecutorService,
            availabilityRestrictions);
    UserConnection userConnection = UserConnection.create(binder, crossProfileSender);
    userConnections.put(userHandle, userConnection);
    return userConnection;
  }

  private static final class UserHandleEventForwarder
      implements ConnectionListener, AvailabilityListener {

    private final AbstractUserConnector connector;

    private final UserHandle userHandle;

    private UserHandleEventForwarder(AbstractUserConnector connector, UserHandle userHandle) {
      this.connector = connector;
      this.userHandle = userHandle;
    }

    @Override
    public void connectionChanged() {
      connector.connectionChanged(userHandle);
    }

    @Override
    public void availabilityChanged() {
      connector.availabilityChanged(userHandle);
    }
  }

  @Override
  public void addConnectionListener(UserHandle userHandle, ConnectionListener listener) {
    userConnection(userHandle).connectionListeners().add(listener);
  }

  @Override
  public void removeConnectionListener(UserHandle userHandle, ConnectionListener listener) {
    userConnection(userHandle).connectionListeners().remove(listener);
  }

  private void availabilityChanged(UserHandle userHandle) {
    for (AvailabilityListener listener : userConnection(userHandle).availabilityListeners()) {
      listener.availabilityChanged();
    }
  }

  @Override
  public void addAvailabilityListener(UserHandle userHandle, AvailabilityListener listener) {
    userConnection(userHandle).availabilityListeners().add(listener);
  }

  @Override
  public void removeAvailabilityListener(UserHandle userHandle, AvailabilityListener listener) {
    userConnection(userHandle).availabilityListeners().remove(listener);
  }

  private void connectionChanged(UserHandle userHandle) {
    for (ConnectionListener listener : userConnection(userHandle).connectionListeners()) {
      listener.connectionChanged();
    }
  }

  @Override
  public UserConnectionHolder addConnectionHolder(UserHandle userHandle, Object connectionHolder) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return UserConnectionHolder.create(this, userHandle, connectionHolder);
    }
    crossProfileSender(userHandle).addConnectionHolder(connectionHolder);

    return UserConnectionHolder.create(this, userHandle, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(UserHandle userHandle, Object key, Object value) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender(userHandle).addConnectionHolderAlias(key, value);
  }

  @Override
  public void removeConnectionHolder(UserHandle userHandle, Object connectionHolder) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender(userHandle).removeConnectionHolder(connectionHolder);
  }

  @Override
  public void clearConnectionHolders(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender(userHandle).clearConnectionHolders();
  }

  /** A builder for an {@link AbstractUserConnector}. */
  public static final class Builder {
    @Nullable UserBinderFactory binderFactory;
    @Nullable ScheduledExecutorService scheduledExecutorService;
    @Nullable AvailabilityRestrictions availabilityRestrictions;
    Context context;
    String serviceClassName;

    public Builder setContext(Context context) {
      this.context = context;
      return this;
    }

    public Builder setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
      this.scheduledExecutorService = scheduledExecutorService;
      return this;
    }

    public Builder setBinderFactory(UserBinderFactory binderFactory) {
      this.binderFactory = binderFactory;
      return this;
    }

    public Builder setServiceClassName(String serviceClassName) {
      this.serviceClassName = serviceClassName;
      return this;
    }

    public Builder setAvailabilityRestrictions(AvailabilityRestrictions availabilityRestrictions) {
      this.availabilityRestrictions = availabilityRestrictions;
      return this;
    }
  }
}

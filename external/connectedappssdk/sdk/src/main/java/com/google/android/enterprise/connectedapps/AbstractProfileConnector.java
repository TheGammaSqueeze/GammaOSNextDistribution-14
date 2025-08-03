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
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Standard implementation of {@link ProfileConnector}. */
public abstract class AbstractProfileConnector
    implements ProfileConnector, ConnectionListener, AvailabilityListener {

  private CrossProfileSender crossProfileSender;
  private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
  private final Set<AvailabilityListener> availabilityListeners = new CopyOnWriteArraySet<>();

  private final Context context;
  private final ScheduledExecutorService scheduledExecutorService;
  private final boolean createdScheduledExecutorService;
  private final ConnectionBinder binder;
  private final String serviceClassName;
  private final @Nullable ProfileType primaryProfileType;
  private final AvailabilityRestrictions availabilityRestrictions;

  public AbstractProfileConnector(
      Class<? extends ProfileConnector> profileConnectorClass, Builder builder) {
    if (profileConnectorClass == null || builder == null || builder.context == null) {
      throw new NullPointerException();
    }
    if (builder.scheduledExecutorService == null) {
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
      createdScheduledExecutorService = true;
    } else {
      scheduledExecutorService = builder.scheduledExecutorService;
      createdScheduledExecutorService = false;
    }

    if (builder.binder == null) {
      binder = new DefaultProfileBinder();
    } else {
      binder = builder.binder;
    }

    context = builder.context.getApplicationContext();
    availabilityRestrictions = builder.availabilityRestrictions;

    if (builder.serviceClassName == null) {
      throw new NullPointerException("serviceClassName must be specified");
    }
    serviceClassName = builder.serviceClassName;
    primaryProfileType = builder.primaryProfileType;
  }

  @Override
  protected void finalize() throws Throwable {
    if (createdScheduledExecutorService) {
      scheduledExecutorService.shutdownNow();
    }
  }

  @Override
  public ProfileConnectionHolder connect() throws UnavailableProfileException {
    return connect(CrossProfileSender.MANUAL_MANAGEMENT_CONNECTION_HOLDER);
  }

  @Override

  public ProfileConnectionHolder connect(Object connectionHolder)
      throws UnavailableProfileException {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      throw new UnavailableProfileException(
          "Cross-profile calls are not supported on this version of Android");
    }
    crossProfileSender().manuallyBind(connectionHolder);

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  @Override
  public CrossProfileSender crossProfileSender() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return null;
    }
    if (crossProfileSender == null) {
      crossProfileSender =
          new CrossProfileSender(
              context.getApplicationContext(),
              serviceClassName,
              binder,
              /* connectionListener= */ this,
              /* availabilityListener= */ this,
              scheduledExecutorService,
              availabilityRestrictions);
    }
    return crossProfileSender;
  }

  @Override
  public void addConnectionListener(ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  @Override
  public void removeConnectionListener(ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  private void notifyConnectionChange() {
    for (ConnectionListener listener : connectionListeners) {
      listener.connectionChanged();
    }
  }

  @Override
  public void connectionChanged() {
    notifyConnectionChange();
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    availabilityListeners.add(listener);
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    availabilityListeners.remove(listener);
  }

  private void notifyAvailabilityChange() {
    for (AvailabilityListener listener : availabilityListeners) {
      listener.availabilityChanged();
    }
  }

  @Override
  public void availabilityChanged() {
    notifyAvailabilityChange();
  }

  @Override
  public boolean isAvailable() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return crossProfileSender().isBindingPossible();
  }

  @Override
  public boolean isConnected() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return crossProfileSender().isBound();
  }

  @Override
  public ConnectedAppsUtils utils() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return new ConnectedAppsUtilsImpl(context);
    }
    return new ConnectedAppsUtilsImpl(context, getPrimaryProfileIdentifier());
  }

  @Override
  public Permissions permissions() {
    return new PermissionsImpl(context, binder);
  }

  @Nullable
  private Profile getPrimaryProfileIdentifier() {
    if (Objects.equals(primaryProfileType, ProfileType.WORK)) {
      return new ConnectedAppsUtilsImpl(context).getWorkProfile();
    }

    if (Objects.equals(primaryProfileType, ProfileType.PERSONAL)) {
      return new ConnectedAppsUtilsImpl(context).getPersonalProfile();
    }

    return null;
  }

  @Override
  public Context applicationContext() {
    return context;
  }

  @Override
  public ProfileConnectionHolder addConnectionHolder(Object connectionHolder) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return ProfileConnectionHolder.create(this, connectionHolder);
    }
    crossProfileSender().addConnectionHolder(connectionHolder);

    return ProfileConnectionHolder.create(this, connectionHolder);
  }

  @Override
  public void addConnectionHolderAlias(Object key, Object value) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender().addConnectionHolderAlias(key, value);
  }

  @Override
  public void removeConnectionHolder(Object connectionHolder) {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender().removeConnectionHolder(connectionHolder);
  }

  @Override
  public void clearConnectionHolders() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return;
    }
    crossProfileSender().clearConnectionHolders();
  }

  /** A builder for an {@link AbstractProfileConnector}. */
  public static final class Builder {
    @Nullable ScheduledExecutorService scheduledExecutorService;
    @Nullable ConnectionBinder binder;
    @Nullable ProfileType primaryProfileType;
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

    public Builder setBinder(ConnectionBinder binder) {
      this.binder = binder;
      return this;
    }

    public Builder setServiceClassName(String serviceClassName) {
      this.serviceClassName = serviceClassName;
      return this;
    }

    public Builder setPrimaryProfileType(ProfileType primaryProfileType) {
      this.primaryProfileType = primaryProfileType;
      return this;
    }

    public Builder setAvailabilityRestrictions(AvailabilityRestrictions availabilityRestrictions) {
      this.availabilityRestrictions = availabilityRestrictions;
      return this;
    }
  }
}

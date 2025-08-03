// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.downloader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Possible constraints that a download request requires. Primarily used by {@link
 * ConnectivityHandler} to determine if sufficient connectivity is available.
 */
@AutoValue
public abstract class DownloadConstraints {
  /**
   * Special value of {@code DownloadConstraints}. If this value is specified, then the {@link
   * ConnectivityHandler} short-circuits and no constraint checks are performed, even if no network
   * is present at all!
   */
  public static final DownloadConstraints NONE =
      DownloadConstraints.builder()
          // Note: Can't use EnumSet because it relies on reflection over the enum class, which
          // breaks unless the right proguard configuration is applied.
          .setRequiredNetworkTypes(ImmutableSet.of())
          .setRequireUnmeteredNetwork(false)
          .build();

  /**
   * Common value to indicate that the active network must be unmetered. This value permits any
   * network type as long as it doesn't indicate it is metered in some way.
   */
  public static final DownloadConstraints NETWORK_UNMETERED =
      DownloadConstraints.builder()
          // Note: Can't use EnumSet because it relies on reflection over the enum class, which
          // breaks unless the right proguard configuration is applied.
          .setRequiredNetworkTypes(ImmutableSet.of(NetworkType.ANY))
          .setRequireUnmeteredNetwork(true)
          .build();

  /**
   * Common value to indicate that the required network must simply be connected in some way, and
   * otherwise doesn't have any restrictions. Any network type is allowed.
   *
   * <p>This is the default value for download requests.
   */
  public static final DownloadConstraints NETWORK_CONNECTED =
      DownloadConstraints.builder()
          // Note: Can't use EnumSet because it relies on reflection over the enum class, which
          // breaks unless the right proguard configuration is applied.
          .setRequiredNetworkTypes(ImmutableSet.of(NetworkType.ANY))
          .setRequireUnmeteredNetwork(false)
          .build();

  /**
   * The type of network that is required. This is a subset of the network types enumerated by
   * {@link android.net.ConnectivityManager}.
   */
  public enum NetworkType {
    /** Special network type to allow any type of network, even if it not one of the known types. */
    ANY,
    /** Equivalent to {@link android.net.NetworkCapabilities#TRANSPORT_BLUETOOTH} */
    BLUETOOTH,
    /** Equivalent to {@link android.net.NetworkCapabilities#TRANSPORT_ETHERNET} */
    ETHERNET,
    /** Equivalent to {@link android.net.NetworkCapabilities#TRANSPORT_CELLULAR} */
    CELLULAR,
    /** Equivalent to {@link android.net.NetworkCapabilities#TRANSPORT_WIFI} */
    WIFI,
  }

  /**
   * Whether the connection must be unmetered to pass connectivity checks. See {@link
   * androidx.core.net.ConnectivityManagerCompat#isActiveNetworkMetered} for more details on this
   * variable.
   *
   * <p>False by default.
   */
  public abstract boolean requireUnmeteredNetwork();

  /**
   * The types of networks that are allowed for the request to pass connectivity checks. The
   * currently active network type must be one of the values in this set. This set may not be empty.
   */
  public abstract ImmutableSet<NetworkType> requiredNetworkTypes();

  /** Creates a {@code DownloadConstraints.Builder} instance. */
  public static Builder builder() {
    return new AutoValue_DownloadConstraints.Builder().setRequireUnmeteredNetwork(false);
  }

  /** Converts this instance to a builder for modifications. */
  public abstract Builder toBuilder();

  /** Builder for creating instances of {@link DownloadConstraints}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setRequiredNetworkTypes(Set<NetworkType> networkTypes);

    abstract ImmutableSet.Builder<NetworkType> requiredNetworkTypesBuilder();

    public Builder addRequiredNetworkType(NetworkType networkType) {
      requiredNetworkTypesBuilder().add(networkType);
      return this;
    }

    public abstract Builder setRequireUnmeteredNetwork(boolean requireUnmeteredNetwork);

    public abstract DownloadConstraints build();
  }
}

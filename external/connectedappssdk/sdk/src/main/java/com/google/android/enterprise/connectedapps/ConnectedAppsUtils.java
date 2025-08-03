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

import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility methods for dealing with profile awareness. */
public interface ConnectedAppsUtils {
  /**
   * Get the identifier of the current profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   */
  Profile getCurrentProfile();

  /**
   * Get the identifier of the other profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   */
  Profile getOtherProfile();

  /**
   * Get the identifier of the primary profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   *
   * @throws IllegalStateException if the used connector has not set a primary profile.
   */
  @Nullable
  Profile getPrimaryProfile();

  /**
   * Get the identifier of the primary profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   *
   * @throws IllegalStateException if the used connector has not set a primary profile.
   */
  @Nullable
  Profile getSecondaryProfile();

  /**
   * Get the identifier of the work profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   */
  Profile getWorkProfile();

  /**
   * Get the identifier of the personal profile.
   *
   * <p>For use with {@code profile(int profileIdentifier) calls.
   *
   * <p>These values may change between runs of your app and should not be persisted.
   */
  Profile getPersonalProfile();

  /** Return true if the current profile is the personal profile. */
  boolean runningOnPersonal();

  /** Return true if the current profile is the work profile. */
  boolean runningOnWork();
}

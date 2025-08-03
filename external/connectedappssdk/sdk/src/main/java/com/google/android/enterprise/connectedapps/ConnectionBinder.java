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

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.MissingApiException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

/** {@link ConnectionBinder} instances are used to establish bindings with other profiles. */
public interface ConnectionBinder {

  /**
   * Try to bind to the given {@link ComponentName} with the given {@link ServiceConnection}.
   *
   * <p>{@link AvailabilityRestrictions} should be enforced.
   *
   * <p>This should not be called if {@link #hasPermissionToBind(Context)} returns {@code False} or
   * {@link #bindingIsPossible(Context, AvailabilityRestrictions)} returns {@code False}.
   */
  boolean tryBind(
      Context context,
      ComponentName bindToService,
      ServiceConnection connection,
      AvailabilityRestrictions availabilityRestrictions)
      throws MissingApiException, UnavailableProfileException;

  /**
   * Return true if there is a profile available to bind to, while enforcing the passed in {@link
   * AvailabilityRestrictions}.
   *
   * <p>This should not be called if {@link #hasPermissionToBind(Context)} returns {@code False}.
   */
  boolean bindingIsPossible(Context context, AvailabilityRestrictions availabilityRestrictions);

  /**
   * Return true if the permissions required for {@link #tryBind(Context, ComponentName,
   * ServiceConnection, AvailabilityRestrictions)} are granted.
   */
  boolean hasPermissionToBind(Context context);
}

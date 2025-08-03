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

import static com.google.android.enterprise.connectedapps.CrossProfileSDKUtilities.filterUsersByAvailabilityRestrictions;
import static com.google.android.enterprise.connectedapps.CrossProfileSDKUtilities.selectUserHandleToBind;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.UserHandle;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A {@link ConnectionBinder} used by Device Policy Controllers. */
public class DpcProfileBinder implements ConnectionBinder {

  private final ComponentName deviceAdminReceiver;

  public DpcProfileBinder(ComponentName deviceAdminReceiver) {
    if (deviceAdminReceiver == null) {
      throw new NullPointerException();
    }
    this.deviceAdminReceiver = deviceAdminReceiver;
  }

  @Override
  public boolean tryBind(
      Context context,
      ComponentName bindToService,
      ServiceConnection connection,
      AvailabilityRestrictions availabilityRestrictions) {
    DevicePolicyManager devicePolicyManager = context.getSystemService(DevicePolicyManager.class);

    UserHandle otherUserHandle =
        getRunningBindDeviceAdminTargetUser(context, availabilityRestrictions);

    if (otherUserHandle == null) {
      return false;
    }

    Intent bindIntent = new Intent();
    bindIntent.setComponent(bindToService);
    boolean hasBound =
        devicePolicyManager.bindDeviceAdminServiceAsUser(
            deviceAdminReceiver, bindIntent, connection, Context.BIND_AUTO_CREATE, otherUserHandle);
    if (!hasBound) {
      context.unbindService(connection);
    }
    return hasBound;
  }

  @Override
  public boolean bindingIsPossible(
      Context context, AvailabilityRestrictions availabilityRestrictions) {
    UserHandle targetUser = getRunningBindDeviceAdminTargetUser(context, availabilityRestrictions);
    return targetUser != null;
  }

  @Override
  public boolean hasPermissionToBind(Context context) {
    DevicePolicyManager devicePolicyManager = context.getSystemService(DevicePolicyManager.class);
    return !devicePolicyManager.getBindDeviceAdminTargetUsers(deviceAdminReceiver).isEmpty();
  }

  @Nullable
  private UserHandle getRunningBindDeviceAdminTargetUser(
      Context context, AvailabilityRestrictions availabilityRestrictions) {
    DevicePolicyManager devicePolicyManager = context.getSystemService(DevicePolicyManager.class);

    List<UserHandle> userHandles =
        filterUsersByAvailabilityRestrictions(
            context,
            devicePolicyManager.getBindDeviceAdminTargetUsers(deviceAdminReceiver),
            availabilityRestrictions);

    return selectUserHandleToBind(context, userHandles);
  }
}

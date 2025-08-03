/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.ota;

import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.R;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ApplicationPackageManager;
import android.app.KeyguardManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDevicePolicyManager;


@Implements(DevicePolicyManager.class)
@SuppressLint("NewApi")
public class ExtendsShadowDevicePolicyManager extends ShadowDevicePolicyManager {

  private Set<String> defaultCrossProfilePackages = new HashSet<>();
  private Set<String> crossProfileCalendarPackages = Collections.emptySet();

  private void enforceProfileOwner(ComponentName admin) {
    if (!admin.equals(getProfileOwner())) {
      throw new SecurityException("[" + admin + "] is not a profile owner");
    }
  }

  // BEGIN-INTERNAL
  @Implementation(minSdk = Q)
  protected Set<String> getCrossProfileCalendarPackages() {
    return crossProfileCalendarPackages;
  }

  @Implementation(minSdk = Q)
  public void setCrossProfileCalendarPackages(ComponentName admin, Set<String> packageNames) {
    enforceProfileOwner(admin);
    crossProfileCalendarPackages = packageNames;
  }

  @Implementation(minSdk = R)
  protected Set<String> getDefaultCrossProfilePackages() {
    return new HashSet<>(defaultCrossProfilePackages);
  }

  public void addDefaultCrossProfilePackage(String packageName) {
    defaultCrossProfilePackages.add(packageName);
  }
  
  public void setDefaultCrossProfilePackages(Set<String> defaultCrossProfilePackages) {
    this.defaultCrossProfilePackages = new HashSet<>(defaultCrossProfilePackages);
  }

}

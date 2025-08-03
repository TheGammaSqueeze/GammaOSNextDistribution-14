/*
 * Copyright (C) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.aidl.permission.service;

import android.aidl.tests.permission.IProtected;
import android.annotation.EnforcePermission;
import android.os.Binder;
import android.os.ServiceManager;
import android.os.test.FakePermissionEnforcer;
import java.util.List;

public class PermissionTestService extends IProtected.Stub {
  private FakePermissionEnforcer mPermissionEnforcer;

  public static void main(String[] args) {
    PermissionTestService myServer = new PermissionTestService(new FakePermissionEnforcer());
    ServiceManager.addService(IProtected.class.getName(), myServer);

    Binder.joinThreadPool();
  }

  public PermissionTestService(FakePermissionEnforcer permissionEnforcer) {
    super(permissionEnforcer);
    mPermissionEnforcer = permissionEnforcer;
  }

  @Override
  @EnforcePermission("READ_PHONE_STATE")
  public void PermissionProtected() {
    PermissionProtected_enforcePermission();
  }

  @Override
  @EnforcePermission(allOf = {"INTERNET", "VIBRATE"})
  public void MultiplePermissionsAll() {
    MultiplePermissionsAll_enforcePermission();
  }

  @Override
  @EnforcePermission(anyOf = {"INTERNET", "VIBRATE"})
  public void MultiplePermissionsAny() {
    MultiplePermissionsAny_enforcePermission();
  }

  @Override
  @EnforcePermission("android.permission.MAINLINE_NETWORK_STACK")
  public void NonManifestPermission() {
    NonManifestPermission_enforcePermission();
  }

  @Override
  public void Grant(String permission) {
    mPermissionEnforcer.grant(permission);
  }

  @Override
  public void Revoke(String permission) {
    mPermissionEnforcer.revoke(permission);
  }

  @Override
  public void RevokeAll() {
    mPermissionEnforcer.revokeAll();
  }
}

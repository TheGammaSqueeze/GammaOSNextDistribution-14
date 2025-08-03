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

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.os.Looper.getMainLooper;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS_FULL;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.getUserHandleForUserId;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.robolectric.Robolectric;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextImpl;
import org.robolectric.shadows.ShadowProcess;
import org.robolectric.shadows.ShadowUserManager.UserState;

public class RobolectricTestUtilities {

  private static final int PERSONAL_PROFILE_USER_ID = 0;
  private static final int WORK_PROFILE_USER_ID = 10;

  /* Matches UserHandle#PER_USER_RANGE */
  private static final int PER_USER_RANGE = 100000;

  private final UserHandle personalProfileUserHandle =
      getUserHandleForUserId(PERSONAL_PROFILE_USER_ID);
  private final UserHandle workProfileUserHandle = getUserHandleForUserId(WORK_PROFILE_USER_ID);
  private static final int WORK_UID = PER_USER_RANGE * WORK_PROFILE_USER_ID;
  private static final int PERSONAL_UID = PER_USER_RANGE * PERSONAL_PROFILE_USER_ID;
  private final Application context;
  private final DevicePolicyManager devicePolicyManager;
  private final UserManager userManager;
  private final CrossProfileApps crossProfileApps;
  private final ShadowContextImpl shadowContext;
  private final PackageManager packageManager;
  private final ComponentName profileOwnerComponentName = new ComponentName("profileowner", "");
  private final PackageInfo profileOwnerPackage = new PackageInfo();
  private final TestScheduledExecutorService scheduledExecutorService;

  public static final String TEST_CONNECTOR_CLASS_NAME = TestProfileConnector.class.getName();
  public static final String TEST_SERVICE_CLASS_NAME = TEST_CONNECTOR_CLASS_NAME + "_Service";

  // These permissions should persist across profiles
  private boolean hasGrantedInteractAcrossProfiles = false;
  private boolean hasGrantedInteractAcrossUsers = false;
  private boolean hasGrantedInteractAcrossUsersFull = false;

  private @Nullable ProfileConnector connector;

  public RobolectricTestUtilities(
      ProfileConnector connector, TestScheduledExecutorService scheduledExecutorService) {
    this((Application) connector.applicationContext(), scheduledExecutorService);
    this.connector = connector;
  }

  public RobolectricTestUtilities(
      Application context, TestScheduledExecutorService scheduledExecutorService) {
    this.context = context;
    devicePolicyManager = context.getSystemService(DevicePolicyManager.class);
    userManager = context.getSystemService(UserManager.class);
    crossProfileApps = context.getSystemService(CrossProfileApps.class);
    packageManager = context.getPackageManager();
    shadowContext = Shadow.extract(context.getBaseContext());
    this.scheduledExecutorService = scheduledExecutorService;

    profileOwnerPackage.applicationInfo = new ApplicationInfo();
    profileOwnerPackage.packageName = profileOwnerComponentName.getPackageName();
  }

  public void initTests() {
    TestCrossProfileType.voidMethodCalls = 0;
    CrossProfileSDKUtilities.clearCache();
    CrossProfileSender.clearStaticState();
    createPersonalUser();
  }

  public void addDefaultConnectionHolderAndWait() {
    connector.addConnectionHolder(this);
    advanceTimeBySeconds(1);
  }

  public void addDefaultConnectionHolderAndWait(UserConnector connector, UserHandle handle) {
    connector.addConnectionHolder(handle, this);
    advanceTimeBySeconds(1);
  }

  public void disconnect() {
    connector.clearConnectionHolders();
    advanceTimeBySeconds(31); // Give time to timeout connection
  }

  public void createPersonalUser() {
    shadowOf(userManager).addUser(PERSONAL_PROFILE_USER_ID, "Personal Profile", /* flags= */ 0);
    shadowOf(userManager)
        .addProfile(PERSONAL_PROFILE_USER_ID, PERSONAL_PROFILE_USER_ID, "Personal Profile", 0);
    shadowOf(userManager).setUserState(personalProfileUserHandle, UserState.STATE_RUNNING_UNLOCKED);
  }

  public void createWorkUser() {
    shadowOf(userManager).addUser(WORK_PROFILE_USER_ID, "Work Profile", /* flags= */ 0);
    shadowOf(userManager)
        .addProfile(PERSONAL_PROFILE_USER_ID, WORK_PROFILE_USER_ID, "Work Profile", 0);
    shadowOf(userManager).addProfile(WORK_PROFILE_USER_ID, WORK_PROFILE_USER_ID, "Work Profile", 0);
    shadowOf(userManager)
        .addProfile(WORK_PROFILE_USER_ID, PERSONAL_PROFILE_USER_ID, "Personal Profile", 0);
  }

  public UserHandle createCustomUser(int id) {
    UserHandle handle = getUserHandleForUserId(id);
    shadowOf(userManager).addUser(id, "Custom User", /* flags= */ 0);
    shadowOf(userManager).setUserState(handle, UserState.STATE_RUNNING_UNLOCKED);
    return handle;
  }

  public void turnOnWorkProfileWithoutUnlocking() {
    shadowOf(userManager).setUserState(workProfileUserHandle, UserState.STATE_RUNNING_LOCKED);
    tryAddTargetUserProfile(workProfileUserHandle);
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
    context.sendBroadcast(intent);
    shadowOf(getMainLooper()).idle();
  }

  public void turnOnWorkProfile() {
    shadowOf(userManager).setUserState(workProfileUserHandle, UserState.STATE_RUNNING_UNLOCKED);
    tryAddTargetUserProfile(workProfileUserHandle);
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
    context.sendBroadcast(intent);
    intent = new Intent();
    intent.setAction(Intent.ACTION_USER_UNLOCKED);
    context.sendBroadcast(intent);
    advanceTimeBySeconds(10);
  }

  public void turnOffWorkProfile() {
    shadowOf(userManager).setUserState(workProfileUserHandle, UserState.STATE_SHUTDOWN);
    removeTargetUserProfile(workProfileUserHandle);
    simulateDisconnectingServiceConnection();
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
    context.sendBroadcast(intent);
    advanceTimeBySeconds(10);
  }

  public void tryAddTargetUserProfile(UserHandle userHandle) {
    try {
      addTargetUserProfile(userHandle);
    } catch (IllegalArgumentException e) {
      // This is thrown if we are running on that profile
    }
  }

  private void addTargetUserProfile(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.P) {
      return;
    }
    shadowOf(crossProfileApps).addTargetUserProfile(userHandle);
  }

  public void tryRemoveTargetUserProfile(UserHandle userHandle) {
    try {
      removeTargetUserProfile(userHandle);
    } catch (IllegalArgumentException e) {
      // This is thrown if we are running on that profile
    }
  }

  private void removeTargetUserProfile(UserHandle userHandle) {
    if (VERSION.SDK_INT < VERSION_CODES.P) {
      return;
    }
    shadowOf(crossProfileApps).removeTargetUserProfile(userHandle);
  }

  public void simulateDisconnectingServiceConnection() {
    ServiceConnection serviceConnection = getServiceConnection();
    if (serviceConnection == null) {
      return;
    }
    serviceConnection.onServiceDisconnected(new ComponentName("", ""));
  }

  private ServiceConnection getServiceConnection() {
    if (getBoundServiceConnections().isEmpty()) {
      return null;
    }
    return getBoundServiceConnections().get(0);
  }

  private List<ServiceConnection> getBoundServiceConnections() {
    return shadowOf(context).getBoundServiceConnections();
  }

  public void setRunningOnPersonalProfile() {
    shadowContext.setUserId(PERSONAL_PROFILE_USER_ID);
    shadowOf(context.getPackageManager()).removePackage(profileOwnerPackage.packageName);
    shadowOf(devicePolicyManager).setProfileOwner(null);
    shadowOf(userManager).setManagedProfile(false);
    setUid(PERSONAL_UID);
    shadowOf(context.getPackageManager()).setPackagesForUid(PERSONAL_UID, context.getPackageName());
    tryRemoveTargetUserProfile(personalProfileUserHandle);
    tryAddTargetUserProfile(workProfileUserHandle);
    regrantPermissions();
  }

  public void setRunningOnWorkProfile() {
    shadowContext.setUserId(WORK_PROFILE_USER_ID);
    setHasProfileOwner();
    shadowOf(userManager).setManagedProfile(true);
    tryRemoveTargetUserProfile(workProfileUserHandle);
    setUid(WORK_UID);
    shadowOf(context.getPackageManager()).setPackagesForUid(WORK_UID, context.getPackageName());
    shadowOf(userManager).setUserState(personalProfileUserHandle, UserState.STATE_RUNNING_UNLOCKED);
    addTargetUserProfile(personalProfileUserHandle);
    regrantPermissions();
  }

  public void setHasProfileOwner() {
    shadowOf(context.getPackageManager()).installPackage(profileOwnerPackage);
    shadowOf(devicePolicyManager).setProfileOwner(profileOwnerComponentName);
  }

  private void setUid(int uid) {
    ShadowProcess.setUid(uid);
    // This is needed for CrossProfileApps but causes issues for < P
    if (VERSION.SDK_INT >= VERSION_CODES.P) {
      shadowOf(context.getPackageManager()).setNameForUid(uid, context.getPackageName());
    }
  }

  public void setBinding(IBinder binder, String connectorClassQualifiedName) {
    ComponentName serviceClassComponentName =
        new ComponentName(context.getPackageName(), connectorClassQualifiedName + "_Service");
    Intent bindIntent = new Intent();
    bindIntent.setComponent(serviceClassComponentName);

    ICrossProfileService.Stub actualServiceStub = (ICrossProfileService.Stub) binder;

    shadowOf(context)
        .setComponentNameAndServiceForBindServiceForIntent(
            bindIntent, serviceClassComponentName, actualServiceStub);
  }

  public void setRequestsPermissions(String... permissions) {
    PackageInfo packageInfo = new PackageInfo();
    packageInfo.packageName = context.getPackageName();
    packageInfo.requestedPermissions = permissions;
    shadowOf(packageManager).installPackage(packageInfo);
  }

  public void grantPermissions(String... permissions) {
    for (String permission : permissions) {
      if (permission.equals(INTERACT_ACROSS_USERS)) {
        hasGrantedInteractAcrossUsers = true;
      }
      if (permission.equals(INTERACT_ACROSS_USERS_FULL)) {
        hasGrantedInteractAcrossUsersFull = true;
      }
      if (permission.equals(INTERACT_ACROSS_PROFILES)) {
        hasGrantedInteractAcrossProfiles = true;
      }
    }
    shadowOf(context).grantPermissions(permissions);
  }

  public void denyPermissions(String... permissions) {
    for (String permission : permissions) {
      if (permission.equals(INTERACT_ACROSS_USERS)) {
        hasGrantedInteractAcrossUsers = false;
      }
      if (permission.equals(INTERACT_ACROSS_USERS_FULL)) {
        hasGrantedInteractAcrossUsersFull = false;
      }
      if (permission.equals(INTERACT_ACROSS_PROFILES)) {
        hasGrantedInteractAcrossProfiles = false;
      }
    }
    shadowOf(context).denyPermissions(permissions);
  }

  private void regrantPermissions() {
    if (hasGrantedInteractAcrossProfiles) {
      grantPermissions(INTERACT_ACROSS_PROFILES);
    } else {
      denyPermissions(INTERACT_ACROSS_PROFILES);
    }
    if (hasGrantedInteractAcrossUsers) {
      grantPermissions(INTERACT_ACROSS_USERS);
    } else {
      denyPermissions(INTERACT_ACROSS_USERS);
    }
    if (hasGrantedInteractAcrossUsersFull) {
      grantPermissions(INTERACT_ACROSS_USERS_FULL);
    } else {
      denyPermissions(INTERACT_ACROSS_USERS_FULL);
    }
  }

  public @Nullable Throwable assertFutureHasException(
      ListenableFuture<?> future, Class<? extends Throwable> throwable) {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    try {
      FluentFuture.from(future)
          .catching(
              throwable,
              t -> {
                // Expected
                thrown.set(t);
                return null;
              },
              directExecutor())
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssertionError("Unhandled exception", e);
    }

    assertThat(thrown.get()).isNotNull();
    return thrown.get();
  }

  public void advanceTimeBySeconds(int intervalSeconds) {
    for (int i = 0; i < intervalSeconds; i++) {
      if (scheduledExecutorService != null) {
        try {
          scheduledExecutorService.advanceTimeBy(1, SECONDS);
        } catch (Exception e) {
          throw new IllegalStateException("Error advancing time", e);
        }

      }
      Robolectric.getForegroundThreadScheduler().advanceBy(1, SECONDS);
      shadowOf(getMainLooper()).idle();
    }
  }
}

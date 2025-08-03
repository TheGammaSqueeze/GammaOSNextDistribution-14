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
package com.google.android.enterprise.connectedapps.instrumented.utils;

import static android.os.UserHandle.getUserHandleForUid;
import static com.google.android.enterprise.connectedapps.instrumented.utils.UserAndProfileTestUtilities.runCommandWithOutput;

import android.content.Context;
import android.os.UserHandle;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.enterprise.connectedapps.testing.BlockingPoll;
import com.google.android.enterprise.connectedapps.testing.ProfileAvailabilityPoll;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserManagementTestUtilities {

  private final Context context;

  public UserManagementTestUtilities(Context context) {
    this.context = context;
  }

  public void stopUser(int userId) {
    runCommandWithOutput("am stop-user -w -f " + userId);
  }

  /** Create a user, perform all necessary user setup, and return their ID. */
  public int ensureUserReadyForCrossUserCalls() {
    return ensureUserReadyForCrossUserCalls(context.getPackageName());
  }

  public int ensureUserReadyForCrossUserCalls(String packageName) {
    int userId = createOrFindOtherUser();
    UserAndProfileTestUtilities.startUserAndBlock(context, userId);

    ensurePackageInstalled(userId, packageName);
    grantInteractAcrossUsersFull();

    waitForChangesToTakeEffect(getUserHandleForUid(userId));

    return userId;
  }

  private int createOrFindOtherUser() {
    if (hasOtherUser()) {
      return getOtherUserId();
    }

    return createOtherUser();
  }

  private int createOtherUser() {
    int userId = UserAndProfileTestUtilities.createUser("TestOtherUser");
    BlockingPoll.poll(this::hasOtherUser, 100, 10000);
    installPackage(userId, context.getPackageName());
    return userId;
  }

  private boolean hasOtherUser() {
    try {
      getOtherUserId();
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }

  private int getOtherUserId() {
    String userList = runCommandWithOutput("pm list users");

    Matcher matcher = Pattern.compile("UserInfo\\{(.*):.*:.*\\}").matcher(userList);

    while (matcher.find()) {
      int userId = Integer.parseInt(matcher.group(1));
      if (userId != 0) {
        // Skip system user
        return userId;
      }
    }

    throw new IllegalStateException("No non-system user found: " + userList);
  }

  private void ensurePackageInstalled(int userId, String packageName) {
    if (!packageName.equals(context.getPackageName())) {
      installPackage(userId, packageName);
    }
  }

  private static void installPackage(int userId, String packageName) {
    runCommandWithOutput("cmd package install-existing --user " + userId + " " + packageName);
  }

  private void grantInteractAcrossUsersFull() {
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS_FULL");
  }

  private void waitForChangesToTakeEffect(UserHandle userHandle) {
    ProfileAvailabilityPoll.blockUntilUserRunningAndUnlocked(context, userHandle);
  }
}

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
package com.google.android.enterprise.connectedapps.testing;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

/** A class to allow blocking until a profile is available. */
public final class ProfileAvailabilityPoll {

  private static final int POLL_FREQUENCY_MS = 1000;
  private static final int POLL_TIMEOUT_MS = 30000;

  public static void blockUntilUserRunningAndUnlocked(Context context, UserHandle userHandle) {
    UserManager userManager = context.getSystemService(UserManager.class);
    BlockingPoll.poll(
        () -> userManager.isUserRunning(userHandle) && userManager.isUserUnlocked(userHandle),
        POLL_FREQUENCY_MS,
        POLL_TIMEOUT_MS);
  }

  public static void blockUntilUserNotAvailable(Context context, UserHandle userHandle) {
    UserManager userManager = context.getSystemService(UserManager.class);
    BlockingPoll.poll(
        () -> !userManager.isUserRunning(userHandle) || userManager.isQuietModeEnabled(userHandle),
        POLL_FREQUENCY_MS,
        POLL_TIMEOUT_MS);
  }

  private ProfileAvailabilityPoll() {}
}

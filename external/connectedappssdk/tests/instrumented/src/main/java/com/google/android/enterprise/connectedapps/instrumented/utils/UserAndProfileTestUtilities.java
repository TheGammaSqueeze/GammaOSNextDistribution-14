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

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.getUserHandleForUserId;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.enterprise.connectedapps.instrumented.utils.ServiceCall.Parameter;
import com.google.android.enterprise.connectedapps.testing.ProfileAvailabilityPoll;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO(b/160147511): Remove use of service calls for versions after R
final class UserAndProfileTestUtilities {

  private static final int R_REQUEST_QUIET_MODE_ENABLED_ID = 72;
  private static final int REQUEST_QUIET_MODE_ENABLED_ID = 58;

  private static final String USER_ID_KEY = "USER_ID";
  private static final Parameter USER_ID_PARAMETER = new Parameter(USER_ID_KEY);

  private static final ServiceCall R_TURN_OFF_USER_COMMAND =
      new ServiceCall("user", R_REQUEST_QUIET_MODE_ENABLED_ID)
          .setUser(1000) // user 1000 has packageName "android"
          .addStringParam("android") // callingPackage
          .addBooleanParam(true) // enableQuietMode
          .addIntParam(USER_ID_PARAMETER) // userId
          .addIntParam(0) // target
          .addIntParam(0); // flags

  private static final ServiceCall TURN_OFF_USER_COMMAND =
      new ServiceCall("user", REQUEST_QUIET_MODE_ENABLED_ID)
          .setUser(1000) // user 1000 has packageName "android"
          .addStringParam("android") // callingPackage
          .addBooleanParam(true) // enableQuietMode
          .addIntParam(USER_ID_PARAMETER) // userId
          .addIntParam(0); // target

  private static final ServiceCall R_TURN_ON_USER_COMMAND =
      new ServiceCall("user", R_REQUEST_QUIET_MODE_ENABLED_ID)
          .setUser(1000) // user 1000 has packageName "android"
          .addStringParam("android") // callingPackage
          .addBooleanParam(false) // enableQuietMode
          .addIntParam(USER_ID_PARAMETER) // userId
          .addIntParam(0) // target
          .addIntParam(0); // flags

  private static final ServiceCall TURN_ON_USER_COMMAND =
      new ServiceCall("user", REQUEST_QUIET_MODE_ENABLED_ID)
          .setUser(1000) // user 1000 has packageName "android"
          .addStringParam("android") // callingPackage
          .addBooleanParam(false) // enableQuietMode
          .addIntParam(USER_ID_PARAMETER) // userId
          .addIntParam(0); // target

  static void turnOnUser(int userId) {
    if (VERSION.SDK_INT == VERSION_CODES.R) {
      runServiceCall(R_TURN_ON_USER_COMMAND, userId);
    } else if (VERSION.SDK_INT == VERSION_CODES.Q || VERSION.SDK_INT == VERSION_CODES.P) {
      runServiceCall(TURN_ON_USER_COMMAND, userId);
    } else {
      throw new IllegalStateException("Cannot turn on user on this version of android");
    }
  }

  static void turnOffUser(int userId) {
    if (VERSION.SDK_INT == VERSION_CODES.R) {
      runServiceCall(R_TURN_OFF_USER_COMMAND, userId);
    } else if (VERSION.SDK_INT == VERSION_CODES.Q || VERSION.SDK_INT == VERSION_CODES.P) {
      runServiceCall(TURN_OFF_USER_COMMAND, userId);
    } else {
      throw new IllegalStateException("Cannot turn off user on this version of android");
    }
  }

  private static void runServiceCall(ServiceCall serviceCall, int userId) {
    runCommandWithOutput(serviceCall.prepare().setInt(USER_ID_KEY, userId).getCommand());
  }

  private static final Pattern CREATE_USER_PATTERN =
      Pattern.compile("Success: created user id (\\d+)");

  static int createUser(String username) {
    String output = runCommandWithOutput("pm create-user " + username);

    Matcher userMatcher = CREATE_USER_PATTERN.matcher(output);
    if (userMatcher.find()) {
      return Integer.parseInt(userMatcher.group(1));
    }

    throw new IllegalStateException("Could not create user. Output: " + output);
  }

  static void startUserAndBlock(Context context, int userId) {
    runCommandWithOutput("am start-user " + userId);
    ProfileAvailabilityPoll.blockUntilUserRunningAndUnlocked(
        context, getUserHandleForUserId(userId));
  }

  static String runCommandWithOutput(String command) {
    ParcelFileDescriptor p = runCommand(command);
    InputStream inputStream = new FileInputStream(p.getFileDescriptor());

    try (Scanner scanner = new Scanner(inputStream, UTF_8.name())) {
      return scanner.useDelimiter("\\A").next();
    } catch (NoSuchElementException ignored) {
      return "";
    }
  }

  private static ParcelFileDescriptor runCommand(String command) {
    return InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .executeShellCommand(command);
  }

  private UserAndProfileTestUtilities() {}
}

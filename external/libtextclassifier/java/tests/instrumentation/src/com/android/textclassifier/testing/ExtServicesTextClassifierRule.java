/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.testing;

import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.google.common.io.ByteStreams;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.rules.ExternalResource;

/** A rule that manages a text classifier that is backed by the ExtServices. */
public final class ExtServicesTextClassifierRule extends ExternalResource {
  private static final String TAG = "androidtc";
  private static final String CONFIG_TEXT_CLASSIFIER_SERVICE_PACKAGE_OVERRIDE =
      "textclassifier_service_package_override";
  private static final String PKG_NAME_GOOGLE_EXTSERVICES = "com.google.android.ext.services";
  private static final String PKG_NAME_AOSP_EXTSERVICES = "android.ext.services";

  private UiAutomation uiAutomation;
  private DeviceConfig.Properties originalProperties;
  private DeviceConfig.Properties.Builder newPropertiesBuilder;

  @Override
  protected void before() throws Exception {
    uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    uiAutomation.adoptShellPermissionIdentity();
    originalProperties = DeviceConfig.getProperties(DeviceConfig.NAMESPACE_TEXTCLASSIFIER);
    newPropertiesBuilder =
        new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_TEXTCLASSIFIER)
            .setString(
                CONFIG_TEXT_CLASSIFIER_SERVICE_PACKAGE_OVERRIDE, getExtServicesPackageName());
    overrideDeviceConfig();
  }

  @Override
  protected void after() {
    try {
      DeviceConfigStateHelper.callWithSyncEnabledWithShellPermissions(() ->
          DeviceConfig.setProperties(originalProperties));
    } catch (Throwable t) {
      Log.e(TAG, "Failed to reset DeviceConfig", t);
    } finally {
      uiAutomation.dropShellPermissionIdentity();
    }
  }

  public void addDeviceConfigOverride(String name, String value) {
    newPropertiesBuilder.setString(name, value);
  }

  /**
   * Overrides the TextClassifier DeviceConfig manually.
   *
   * <p>This will clean up all device configs not in newPropertiesBuilder.
   *
   * <p>We will need to call this everytime before testing, because DeviceConfig can be synced in
   * background at anytime. DeviceConfig#setSyncDisabledMode is to disable sync, however it's a
   * hidden API.
   */
  public void overrideDeviceConfig() throws Exception {
    DeviceConfigStateHelper.callWithSyncEnabledWithShellPermissions(() ->
        DeviceConfig.setProperties(newPropertiesBuilder.build()));
  }

  /** Force stop ExtServices. Force-stop-and-start can be helpful to reload some states. */
  public void forceStopExtServices() {
    runShellCommand("am force-stop com.google.android.ext.services");
    runShellCommand("am force-stop android.ext.services");
  }

  public TextClassifier getTextClassifier() {
    TextClassificationManager textClassificationManager =
        ApplicationProvider.getApplicationContext()
            .getSystemService(TextClassificationManager.class);
    textClassificationManager.setTextClassifier(null); // Reset TC overrides
    return textClassificationManager.getTextClassifier();
  }

  public void dumpDefaultTextClassifierService() {
    runShellCommand(
        "dumpsys activity service com.google.android.ext.services/"
            + "com.android.textclassifier.DefaultTextClassifierService");
    runShellCommand("cmd device_config list textclassifier");
  }

  public void enableVerboseLogging() {
    runShellCommand("setprop log.tag.androidtc VERBOSE");
  }

  private void runShellCommand(String cmd) {
    Log.v(TAG, "run shell command: " + cmd);
    try (FileInputStream output =
        new FileInputStream(uiAutomation.executeShellCommand(cmd).getFileDescriptor())) {
      String cmdOutput = new String(ByteStreams.toByteArray(output));
      if (!cmdOutput.isEmpty()) {
        Log.d(TAG, "cmd output: " + cmdOutput);
      }
    } catch (IOException ioe) {
      Log.w(TAG, "failed to get cmd output", ioe);
    }
  }

  private static String getExtServicesPackageName() {
    PackageManager packageManager = ApplicationProvider.getApplicationContext().getPackageManager();
    try {
      packageManager.getApplicationInfo(PKG_NAME_GOOGLE_EXTSERVICES, /* flags= */ 0);
      return PKG_NAME_GOOGLE_EXTSERVICES;
    } catch (NameNotFoundException e) {
      return PKG_NAME_AOSP_EXTSERVICES;
    }
  }
}

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

package android.settings.cts;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.Secure;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import android.app.slice.Slice;
import android.app.slice.SliceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import androidx.slice.SliceConvert;
import androidx.slice.SliceMetadata;
import androidx.slice.core.SliceAction;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WifiSliceTest {
  private static final String TAG = "WifiSliceTest";

  private static final Uri WIFI_SLICE_URI =
          Uri.parse("content://android.settings.slices/action/wifi");

  private static final String ACTION_ASSIST = "android.intent.action.ASSIST";
  private static final String ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST";
  private static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
  private static final String FEATURE_VOICE_RECOGNIZERS = "android.software.voice_recognizers";
  private static final String ASSISTANT = "assistant";

  private final Context mContext = InstrumentationRegistry.getContext();
  private final SliceManager mSliceManager = mContext.getSystemService(SliceManager.class);
  private final boolean mHasVoiceRecognizersFeature =
          mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS);

  private Slice mWifiSlice;
  private String mAssistant;

  @Before
  public void setUp() throws Exception {
    assumeFalse("Skipping test: Auto does not support provider android.settings.slices", isCar());
    assumeFalse("Skipping test: TV does not support provider android.settings.slices", isTv());
    assumeFalse(
        "Skipping test: Watch does not support provider android.settings.slices", isWatch());
    mWifiSlice = mSliceManager.bindSlice(WIFI_SLICE_URI, Collections.emptySet());
    mAssistant = Secure.getString(mContext.getContentResolver(), ASSISTANT);
  }

  @After
  public void tearDown() {
    final String assistant = Secure.getString(mContext.getContentResolver(), ASSISTANT);
    if (!TextUtils.equals(mAssistant, assistant)) {
      Secure.putString(mContext.getContentResolver(), ASSISTANT, mAssistant);
    }
  }

  @Test
  public void wifiSliceToggle_changeWifiState() {
    SliceMetadata mWifiSliceMetadata =
            SliceMetadata.from(mContext, SliceConvert.wrap(mWifiSlice, mContext));
    List<SliceAction> wifiSliceActions = mWifiSliceMetadata.getToggles();
    if (wifiSliceActions.size() != 0) {
      SliceAction toggleAction = wifiSliceActions.get(0);

      toggleAction.setChecked(true);
      assertThat(toggleAction.isChecked()).isEqualTo(isWifiEnabled());

      toggleAction.setChecked(false);
      assertThat(toggleAction.isChecked()).isEqualTo(isWifiEnabled());
    }
  }

  @Test
  public void wifiSlice_hasCorrectUri() {
    assertThat(mWifiSlice.getUri()).isEqualTo(WIFI_SLICE_URI);
  }

  @Test
  public void wifiSlice_grantedPermissionToDefaultAssistant() throws NameNotFoundException {
    if (!mHasVoiceRecognizersFeature) {
      Log.i(TAG, "The device doesn't support feature: " + FEATURE_VOICE_RECOGNIZERS);
      return;
    }
    final PackageManager pm = mContext.getPackageManager();
    final Intent requestDefaultAssistant =
            new Intent(ACTION_ASSIST).addCategory(CATEGORY_DEFAULT);

    final List<ResolveInfo> infos = pm.queryIntentActivities(requestDefaultAssistant, 0);

    if (!infos.isEmpty()) {
      final String packageName;
      if (!TextUtils.isEmpty(mAssistant)) {
        packageName = ComponentName.unflattenFromString(mAssistant).getPackageName();
        Log.i(TAG, "Default assistant: " + packageName);
      } else {
        packageName = infos.get(0).activityInfo.packageName;
        Log.i(TAG, "Set assistant: " + packageName);
        Secure.putString(mContext.getContentResolver(), ASSISTANT,
                new ComponentName(packageName, infos.get(0).activityInfo.name).flattenToString());
      }
      final int testPid = Process.myPid();
      final int testUid = pm.getPackageUid(packageName, 0);

      assertThat(mSliceManager.checkSlicePermission(WIFI_SLICE_URI, testPid, testUid))
              .isEqualTo(PERMISSION_GRANTED);
    }
  }

  @Test
  public void wifiSlice_grantedPermissionToDefaultVoiceAssistant() throws NameNotFoundException {
    if (!mHasVoiceRecognizersFeature) {
      Log.i(TAG, "The device doesn't support feature: " + FEATURE_VOICE_RECOGNIZERS);
      return;
    }
    final PackageManager pm = mContext.getPackageManager();
    final Intent requestDefaultAssistant =
            new Intent(ACTION_VOICE_ASSIST).addCategory(CATEGORY_DEFAULT);

    final ResolveInfo info = pm.resolveActivity(requestDefaultAssistant, 0);

    if (info != null) {
      final int testPid = Process.myPid();
      final int testUid = pm.getPackageUid(info.activityInfo.packageName, 0);

      assertThat(mSliceManager.checkSlicePermission(WIFI_SLICE_URI, testPid, testUid))
              .isEqualTo(PERMISSION_GRANTED);
    }
  }

  private boolean isCar() {
    PackageManager pm = mContext.getPackageManager();
    return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
  }

  private boolean isTv() {
    PackageManager pm = mContext.getPackageManager();
    return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
  }

  private boolean isWatch() {
    return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
  }

  private boolean isWifiEnabled() {
    final WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
    return wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
            || wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
  }

}

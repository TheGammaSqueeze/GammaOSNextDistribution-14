/**
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.cujsmalltest.cts;

import android.media.cujcommon.cts.CallNotificationTestPlayerListener;
import android.media.cujcommon.cts.CujTestBase;
import android.media.cujcommon.cts.CujTestParam;
import android.media.cujcommon.cts.MessageNotificationTestPlayerListener;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


@LargeTest
@AppModeFull(reason = "Instant apps doesn't support push message notification and phone calling")
@RunWith(Parameterized.class)
public class CtsMediaShortFormFullModePlaybackTest extends CujTestBase {

  private static final String MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerJoyrides_720p_hevc_15s";

  CujTestParam mCujTestParam;
  private final String mTestType;

  public CtsMediaShortFormFullModePlaybackTest(CujTestParam cujTestParam, String testType) {
    super(cujTestParam.playerListener());
    mCujTestParam = cujTestParam;
    this.mTestType = testType;
  }

  /**
   * Returns the list of parameters
   */
  @Parameterized.Parameters(name = "{index}_{1}")
  public static Collection<Object[]> input() {
    // CujTestParam, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15secVideoListForNotificationTest())
            .setTimeoutMilliSeconds(52000)
            .setPlayerListener(new CallNotificationTestPlayerListener(4000)).build(),
            "Hevc_720p_15sec_CallNotificationTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15secVideoListForNotificationTest())
            .setTimeoutMilliSeconds(45000)
            .setPlayerListener(new MessageNotificationTestPlayerListener(4000)).build(),
            "Hevc_720p_15sec_MessageNotificationTest"},
    }));
    return exhaustiveArgsList;
  }

  /**
   * Prepare Hevc 720p 15sec video list for notification test.
   */
  public static List<String> prepareHevc_720p_15secVideoListForNotificationTest() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING);
    return videoInput;
  }

  // Test to Verify video playback with and without seek
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @Test
  @PlatinumTest(focusArea = "media")
  public void testVideoPlayback() throws Exception {
    if (mCujTestParam.playerListener().isOrientationTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support orientation.",
          supportOrientationRequest(mActivity));
    }
    if (mCujTestParam.playerListener().isCallNotificationTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support call feature",
          deviceSupportPhoneCall(mActivity));
    }
    play(mCujTestParam.mediaUrls(), mCujTestParam.timeoutMilliSeconds());
  }
}

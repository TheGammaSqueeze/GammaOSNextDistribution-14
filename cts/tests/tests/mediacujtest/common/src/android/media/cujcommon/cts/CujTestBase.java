/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ActivityScenario;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class comprises of routines that are generic to all tests.
 */
public class CujTestBase {

  static final String SHORTFORM_PLAYBAK_TEST_APP = "android.media.cujsmalltest.cts";

  static final int[] ORIENTATIONS = {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
  };
  protected MainActivity mActivity;
  protected ScrollTestActivity mScrollActivity;
  public PlayerListener mListener;
  private boolean mIsScrollTest;

  public CujTestBase(PlayerListener playerListener) {
    if (!playerListener.isScrollTest()) {
      ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
      scenario.onActivity(activity -> {
        this.mActivity = activity;
      });
      mListener = playerListener;
      mActivity.addPlayerListener(mListener);
      mListener.setActivity(mActivity);
    } else {
      mIsScrollTest = true;
      ActivityScenario<ScrollTestActivity> scenario = ActivityScenario.launch(
          ScrollTestActivity.class);
      scenario.onActivity(activity -> {
        this.mScrollActivity = activity;
      });
      mListener = playerListener;
      mScrollActivity.addPlayerListener(mListener);
      mListener.setScrollActivity(mScrollActivity);
    }
  }

  /**
   * Whether the device supports orientation request from apps.
   */
  public static boolean supportOrientationRequest(final Activity activity) {
    final PackageManager pm = activity.getPackageManager();
    return pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
        && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT);
  }

  /**
   * Whether the device supports phone call feature.
   */
  public static boolean deviceSupportPhoneCall(final Activity activity) {
    return (((TelephonyManager) activity.getApplicationContext()
        .getSystemService(Context.TELEPHONY_SERVICE)).getPhoneType()
        != TelephonyManager.PHONE_TYPE_NONE);
  }

  /**
   * Whether the device is a watch.
   */
  public static boolean isWatchDevice(final Activity activity) {
    return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
  }

  /**
   * Prepare the player, input list and add input list to player's playlist. After that, play for
   * the provided playlist and validate playback time.
   *
   * @param mediaUrls           List of mediaurl
   * @param timeoutMilliSeconds Timeout for the test
   */
  public void play(List<String> mediaUrls, long timeoutMilliSeconds)
      throws TimeoutException, InterruptedException {
    long startTime = System.currentTimeMillis();
    if (!mIsScrollTest) {
      mActivity.runOnUiThread(() -> {
        mActivity.prepareMediaItems(mediaUrls);
        mActivity.run();
      });
    } else {
      mScrollActivity.runOnUiThread(() -> {
        mScrollActivity.prepareMediaItems(mediaUrls);
        mScrollActivity.run();
      });
    }

    long endTime = System.currentTimeMillis() + timeoutMilliSeconds;
    // Wait for playback to finish
    synchronized (PlayerListener.LISTENER_LOCK) {
      while (!PlayerListener.mPlaybackEnded) {
        PlayerListener.LISTENER_LOCK.wait(timeoutMilliSeconds);
        if (endTime < System.currentTimeMillis()) {
          throw new TimeoutException(
              "playback timed out after " + timeoutMilliSeconds + " milli seconds.");
        }
      }
      PlayerListener.mPlaybackEnded = false;
    }
    long actualTotalTime = System.currentTimeMillis() - startTime;
    long expectedTotalTime = mListener.getExpectedTotalTime();
    assertEquals((float) expectedTotalTime, (float) actualTotalTime, 30000);
  }
}

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

package android.media.cujcommon.cts;

import static org.junit.Assert.assertTrue;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

public class PipModeTestPlayerListener extends PlayerListener {

  private static final int PIP_DURATION_MS = 5000;

  public PipModeTestPlayerListener(long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  @Override
  public TestType getTestType() {
    return TestType.PIP_MODE_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Switch to picture in picture minimized playback mode
          mActivity.enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
          mActivity.mConfiguredPipMode = true;
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Verify that the activity is in PIP mode before switching back to normal mode
          assertTrue(mActivity.mIsInPipMode);
          // Switch to normal playback mode
          mActivity.moveTaskToBack(false);
          Intent startIntent = new Intent(mActivity, MainActivity.class);
          startIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
          mActivity.startActivity(startIntent);
          mActivity.mConfiguredPipMode = false;
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition + PIP_DURATION_MS)
        .setDeleteAfterDelivery(true)
        .send();
  }
}

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

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

public class MessageNotificationTestPlayerListener extends PlayerListener {

  private static final int NUM_OF_MESSAGE_NOTIFICATIONS = 2;

  public MessageNotificationTestPlayerListener(long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  @Override
  public TestType getTestType() {
    return TestType.MESSAGE_NOTIFICATION_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
      mStartTime = System.currentTimeMillis();
      // Let the ExoPlayer handle audio focus internally
      mActivity.mPlayer.setAudioAttributes(mActivity.mPlayer.getAudioAttributes(), true);
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    for (int i = 0; i < NUM_OF_MESSAGE_NOTIFICATIONS; i++) {
      mActivity.mPlayer.createMessage((messageType, payload) -> {
            // Place a sample message notification
            try {
              NotificationGenerator.createNotification(mActivity);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition * (i + 1))
          .setDeleteAfterDelivery(true)
          .send();
    }
  }
}

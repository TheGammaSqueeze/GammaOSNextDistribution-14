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

import static org.junit.Assert.assertEquals;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;

public class SpeedChangeTestPlayerListener extends PlayerListener {

  private static final long START_SPEED_CHANGE_MS = 1000;
  private static final float[] PLAYBACK_SPEEDS = {
      0.25f, 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 1.00f
  };
  private static final float DELTA = 0.001f;

  private int mSpeedIndex;

  public SpeedChangeTestPlayerListener() {
    super();
    this.mSendMessagePosition = START_SPEED_CHANGE_MS;
    this.mSpeedIndex = 0;
  }

  @Override
  public TestType getTestType() {
    return TestType.SPEED_CHANGE_TEST;
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
    // Iterate sequentially through all the playback speeds
    // Create messages to be executed at different positions
    for (int index = 0; index < PLAYBACK_SPEEDS.length; index++) {
      // Increment the message position to run the clip at different speeds for the same duration
      // of 4 seconds i.e. for 0.25 speed it runs from PTS 1 sec (speed change starts from 1 sec) to
      // 2 sec which takes playback time of 4 sec, for 0.50 speed it runs from PTS 2 sec to 4 sec
      // which again takes playback time of 4 sec, similarly for 0.75 from PTS 4-7, for 1.0 from PTS
      // 7-11, for 1.25 from PTS 11-16, for 1.5 from PTS 16-22 and for 1.75 from PTS 22-29 and back
      // to normal speed from PTS 29 sec onwards. Total time taken to do the 7 speed change
      // operations is 28 (7 * 4) seconds which is equivalent to the PTS duration within which speed
      // change operations are done i.e. 28 (29 - 1) sec. So, The total time consumed is
      // 7 (speeds) * 4 + 2 which is equal to the clip duration i.e. 30 sec.
      mSendMessagePosition += (index * 1000L);
      mActivity.mPlayer.createMessage((messageType, payload) -> {
            PlaybackParameters newParameters = new PlaybackParameters(PLAYBACK_SPEEDS[mSpeedIndex]);
            mActivity.mPlayer.setPlaybackParameters(newParameters);
          }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
          .setDeleteAfterDelivery(true).send();
    }
  }

  /**
   * Called when the value of getPlaybackParameters() changes. The playback parameters may change
   * due to a call to setPlaybackParameters(PlaybackParameters), or the player itself may change
   * them (for example, if audio playback switches to passthrough or offload mode, where speed
   * adjustment is no longer possible).
   *
   * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
   * other events that happen in the same {@link Looper} message queue iteration.
   *
   * @param playbackParameters The playback parameters.
   */
  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    assertEquals(PLAYBACK_SPEEDS[mSpeedIndex], playbackParameters.speed, DELTA);
    mSpeedIndex++;
  }
}

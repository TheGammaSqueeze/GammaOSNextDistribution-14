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
import static org.junit.Assert.assertFalse;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

public class SwitchAudioTrackTestPlayerListener extends PlayerListener {

  private final int mNumOfAudioTrack;

  public SwitchAudioTrackTestPlayerListener(int numOfAudioTrack, long sendMessagePosition) {
    super();
    this.mNumOfAudioTrack = numOfAudioTrack;
    this.mSendMessagePosition = sendMessagePosition;
  }

  @Override
  public TestType getTestType() {
    return TestType.SWITCH_AUDIO_TRACK_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
      // When player is ready, get the list of audio/subtitle track groups in the mediaItem
      mTrackGroups = getTrackGroups();
    } else if (mTrackChangeRequested && player.getPlaybackState() == Player.STATE_ENDED) {
      assertEquals(mConfiguredTrackFormat, mCurrentTrackFormat);
      assertFalse(isFormatSimilar(mStartTrackFormat, mCurrentTrackFormat));
      mTrackChangeRequested = false;
      mStartTrackFormat = mCurrentTrackFormat;
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    // Create messages to be executed at different positions
    // First trackGroupIndex is selected at the time of playback start, so changing
    // track from second track group Index onwards.
    for (int trackGroupIndex = 1; trackGroupIndex < mNumOfAudioTrack; trackGroupIndex++) {
      createSwitchTrackMessage(mSendMessagePosition * trackGroupIndex, trackGroupIndex,
          0 /* TrackIndex */);
    }
  }
}

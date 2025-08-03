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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;

public class SeekTestPlayerListener extends PlayerListener {

  private static final String LOG_TAG = SeekTestPlayerListener.class.getSimpleName();

  private final int mNumOfSeekIteration;
  private long mSeekTimeUs;
  private final long mSeed;
  private boolean mSeekDone;

  public SeekTestPlayerListener(int numOfSeekIteration, long seekTimeUs,
      long sendMessagePosition) {
    super();
    this.mNumOfSeekIteration = numOfSeekIteration;
    this.mSeekTimeUs = seekTimeUs;
    this.mSeed = getSeed();
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Returns seed for Seek test.
   */
  private long getSeed() {
    // Truncate time to the nearest day.
    long seed = Clock.tick(Clock.systemUTC(), Duration.ofDays(1)).instant().toEpochMilli();
    Log.d(LOG_TAG, "Random seed = " + seed);
    return seed;
  }

  /**
   * Seek the player.
   */
  private void seek() {
    Random random = new Random(mSeed);
    // If number of seek requested is one then seek forward or backward alternatively for
    // mSeekTimeUs on given media list.
    // If number of seek requested is 30 then seek for mSeekTimeUs- forward 10 times,
    // backward 10 times and then randomly backwards or forwards 10 times on each
    // media item.
    for (int i = 0; i < mNumOfSeekIteration; i++) {
      mActivity.mPlayer.seekTo(mActivity.mPlayer.getCurrentPosition() + mSeekTimeUs);
      if (mNumOfSeekIteration == 1 || i == 10) {
        mSeekTimeUs *= -1;
      } else if (i >= 20) {
        mSeekTimeUs *= random.nextBoolean() ? -1 : 1;
      }
    }
    mSeekDone = true;
  }

  @Override
  public TestType getTestType() {
    return TestType.SEEK_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // Add change in duration due to seek
      if (mSeekDone) {
        mExpectedTotalTime += (mSendMessagePosition - player.getCurrentPosition());
        mSeekDone = false;
      } else {
        // At the first media transition player is not ready. So, add duration of
        // first clip when player is ready
        mExpectedTotalTime += player.getDuration();
      }
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          seek();
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
        .setDeleteAfterDelivery(true)
        .send();
  }
}

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
import androidx.media3.exoplayer.ExoPlayer;

public class ScrollTestPlayerListener extends PlayerListener {

  private final int mNumOfScrollIteration;

  public ScrollTestPlayerListener(int numOfScrollIteration, long sendMessagePosition) {
    super();
    this.mNumOfScrollIteration = numOfScrollIteration;
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Scroll the View vertically.
   *
   * @param yIndex The yIndex to scroll the view vertically.
   */
  private void scrollView(int yIndex) {
    mScrollActivity.mScrollView.scrollTo(0, yIndex);
    if (CURRENT_MEDIA_INDEX == mNumOfScrollIteration) {
      mScrollRequested = true;
    }
  }

  @Override
  public TestType getTestType() {
    return TestType.SCROLL_TEST;
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
    // In case of scroll test, send the message to scroll the view to change the surface
    // positions. Scroll has two surfaceView (top and bottom), playback start on top view and
    // after each mSendMessagePosition sec playback is switched to other view alternatively.
    int yIndex;
    ExoPlayer currentPlayer;
    if ((CURRENT_MEDIA_INDEX % 2) == 0) {
      currentPlayer = mScrollActivity.mFirstPlayer;
      yIndex = mScrollActivity.SURFACE_HEIGHT * 2;
    } else {
      currentPlayer = mScrollActivity.mSecondPlayer;
      yIndex = 0;
    }
    CURRENT_MEDIA_INDEX++;
    for (int i = 0; i < mNumOfScrollIteration; i++) {
      currentPlayer.createMessage((messageType, payload) -> {
            scrollView(yIndex);
          }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition * (i + 1))
          .setDeleteAfterDelivery(true)
          .send();
    }
  }
}

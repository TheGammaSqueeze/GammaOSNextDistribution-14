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
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;

import java.util.ArrayList;
import java.util.List;

public class AdaptivePlaybackTestPlayerListener extends PlayerListener {

  private boolean mResolutionChangeRequested;
  private int mCurrentResolutionWidth;
  private int mCurrentResolutionHeight;
  private final int mNumOfVideoTrack;
  private int mIndexIncrement;
  private int mCurrentTrackIndex = C.INDEX_UNSET;
  private Tracks.Group mVideoTrackGroup;
  private List<Format> mVideoFormatList;

  public AdaptivePlaybackTestPlayerListener(int numOfVideoTrack, long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
    this.mNumOfVideoTrack = numOfVideoTrack;
    this.mIndexIncrement = 1;
  }

  @Override
  public TestType getTestType() {
    return TestType.ADAPTIVE_PLAYBACK_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      if (mResolutionChangeRequested) {
        int configuredResolutionWidth = mVideoFormatList.get(mCurrentTrackIndex).width;
        int configuredResolutionHeight = mVideoFormatList.get(mCurrentTrackIndex).height;
        assertEquals(configuredResolutionWidth, mCurrentResolutionWidth);
        assertEquals(configuredResolutionHeight, mCurrentResolutionHeight);
        // Reversing the track iteration order
        if (mCurrentTrackIndex == mVideoFormatList.size() - 1) {
          mIndexIncrement *= -1;
        }
        mCurrentTrackIndex += mIndexIncrement;
        mResolutionChangeRequested = false;
      } else {
        // At the first media transition player is not ready. So, add duration of
        // first clip when player is ready
        mExpectedTotalTime += player.getDuration();
        // When player is ready, get the list of supported video Format(s) in the DASH mediaItem
        mVideoFormatList = getVideoFormatList();
        mCurrentTrackIndex = 0;
      }
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    // Iterating forwards and then backwards for all the available video tracks
    final int totalNumOfVideoTrackChange = mNumOfVideoTrack * 2;
    // Create messages to be executed at different positions
    for (int count = 1; count < totalNumOfVideoTrackChange; count++) {
      createAdaptivePlaybackMessage(mSendMessagePosition * (count));
    }
  }

  /**
   * Called each time when getVideoSize() changes. onEvents(Player, Player.Events) will also be
   * called to report this event along with other events that happen in the same Looper message
   * queue iteration.
   *
   * @param videoSize The new size of the video.
   */
  public void onVideoSizeChanged(VideoSize videoSize) {
    mCurrentResolutionWidth = videoSize.width;
    mCurrentResolutionHeight = videoSize.height;
  }

  /**
   * Create a message at given position to change the resolution
   *
   * @param sendMessagePosition Position at which message needs to be executed
   */
  private void createAdaptivePlaybackMessage(long sendMessagePosition) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          TrackSelectionParameters currentParameters =
              mActivity.mPlayer.getTrackSelectionParameters();
          TrackSelectionParameters newParameters = currentParameters
              .buildUpon()
              .setOverrideForType(
                  new TrackSelectionOverride(mVideoTrackGroup.getMediaTrackGroup(),
                      mCurrentTrackIndex))
              .build();
          mActivity.mPlayer.setTrackSelectionParameters(newParameters);
          mResolutionChangeRequested = true;
        }).setLooper(Looper.getMainLooper()).setPosition(sendMessagePosition)
        .setDeleteAfterDelivery(true).send();
  }

  /**
   * Fetch only video tracks group from the given clip
   *
   * @param currentTracks Current tracks in the clip
   * @return Video tracks group
   */
  private Tracks.Group getVideoTrackGroup(Tracks currentTracks) {
    Tracks.Group videoTrackGroup = null;
    for (Tracks.Group currentTrackGroup : currentTracks.getGroups()) {
      if (currentTrackGroup.getType() == C.TRACK_TYPE_VIDEO) {
        videoTrackGroup = currentTrackGroup;
        break;
      }
    }
    return videoTrackGroup;
  }

  /**
   * Get all available formats from videoTrackGroup.
   */
  private List<Format> getVideoFormatList() {
    Tracks currentTracks = mActivity.mPlayer.getCurrentTracks();
    mVideoTrackGroup = getVideoTrackGroup(currentTracks);
    List<Format> videoFormatList = new ArrayList<>();
    // Populate the videoFormatList with video tracks
    for (int trackIndex = 0; trackIndex < mVideoTrackGroup.length; trackIndex++) {
      Format trackFormat = mVideoTrackGroup.getTrackFormat(trackIndex);
      videoFormatList.add(trackFormat);
    }
    assertEquals(mNumOfVideoTrack, videoFormatList.size());
    return videoFormatList;
  }
}

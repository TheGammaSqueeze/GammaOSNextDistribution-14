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

import static android.media.cujcommon.cts.CujTestBase.ORIENTATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import java.util.ArrayList;
import java.util.List;

public abstract class PlayerListener implements Player.Listener {

  public static final Object LISTENER_LOCK = new Object();
  public static int CURRENT_MEDIA_INDEX = 0;

  // Enum Declared for Test Type
  protected enum TestType {
    PLAYBACK_TEST,
    SEEK_TEST,
    ORIENTATION_TEST,
    ADAPTIVE_PLAYBACK_TEST,
    SCROLL_TEST,
    SWITCH_AUDIO_TRACK_TEST,
    SWITCH_SUBTITLE_TRACK_TEST,
    CALL_NOTIFICATION_TEST,
    MESSAGE_NOTIFICATION_TEST,
    PINCH_TO_ZOOM_TEST,
    SPEED_CHANGE_TEST,
    PIP_MODE_TEST
  }

  public static boolean mPlaybackEnded;
  protected long mExpectedTotalTime;
  protected MainActivity mActivity;
  protected ScrollTestActivity mScrollActivity;
  protected long mSendMessagePosition;
  protected int mPreviousOrientation;
  protected int mOrientationIndex;
  protected boolean mScrollRequested;
  protected boolean mTrackChangeRequested;
  protected List<Tracks.Group> mTrackGroups;
  protected Format mStartTrackFormat;
  protected Format mCurrentTrackFormat;
  protected Format mConfiguredTrackFormat;
  protected long mStartTime;

  public PlayerListener() {
    this.mSendMessagePosition = 0;
  }

  /**
   * Returns the type of test.
   */
  public abstract TestType getTestType();

  /**
   * Returns True for Orientation test.
   */
  public final boolean isOrientationTest() {
    return getTestType().equals(TestType.ORIENTATION_TEST);
  }

  /**
   * Returns True for Scroll test.
   */
  public final boolean isScrollTest() {
    return getTestType().equals(TestType.SCROLL_TEST);
  }

  /**
   * Returns True for Call Notification test.
   */
  public final boolean isCallNotificationTest() {
    return getTestType().equals(TestType.CALL_NOTIFICATION_TEST);
  }

  /**
   * Returns True for PinchToZoom test.
   */
  public final boolean isPinchToZoomTest() {
    return getTestType().equals(TestType.PINCH_TO_ZOOM_TEST);
  }

  /**
   * Returns True for PIP Minimized Playback Mode test.
   */
  public final boolean isPipTest() {
    return getTestType().equals(TestType.PIP_MODE_TEST);
  }

  /**
   * Returns expected playback time for the playlist.
   */
  public final long getExpectedTotalTime() {
    return mExpectedTotalTime;
  }

  /**
   * Sets activity for test.
   */
  public final void setActivity(MainActivity activity) {
    this.mActivity = activity;
    if (isOrientationTest()) {
      mOrientationIndex = 0;
      mActivity.setRequestedOrientation(
          ORIENTATIONS[mOrientationIndex] /* SCREEN_ORIENTATION_PORTRAIT */);
    }
  }

  /**
   * Get Orientation of the device.
   */
  protected static int getDeviceOrientation(final Activity activity) {
    final DisplayMetrics displayMetrics = new DisplayMetrics();
    activity.getDisplay().getRealMetrics(displayMetrics);
    if (displayMetrics.widthPixels < displayMetrics.heightPixels) {
      return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    } else {
      return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }
  }

  /**
   * Sets activity for scroll test.
   */
  public final void setScrollActivity(ScrollTestActivity activity) {
    this.mScrollActivity = activity;
  }

  /**
   * Check if two formats are similar.
   *
   * @param refFormat  Reference format
   * @param testFormat Test format
   * @return True, if two formats are similar, false otherwise
   */
  protected final boolean isFormatSimilar(Format refFormat, Format testFormat) {
    String refMediaType = refFormat.sampleMimeType;
    String testMediaType = testFormat.sampleMimeType;
    if (getTestType().equals(TestType.SWITCH_AUDIO_TRACK_TEST)) {
      assertTrue(refMediaType.startsWith("audio/") && testMediaType.startsWith("audio/"));
      if ((refFormat.channelCount != testFormat.channelCount) || (refFormat.sampleRate
          != testFormat.sampleRate)) {
        return false;
      }
    } else if (getTestType().equals(TestType.SWITCH_SUBTITLE_TRACK_TEST)) {
      assertTrue((refMediaType.startsWith("text/") && testMediaType.startsWith("text/")) || (
          refMediaType.startsWith("application/") && testMediaType.startsWith("application/")));
    }
    if (!refMediaType.equals(testMediaType)) {
      return false;
    }
    if (!refFormat.id.equals(testFormat.id)) {
      return false;
    }
    return true;
  }

  /**
   * Called when player states changed.
   *
   * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
   *               states.
   * @param events The {@link Events} that happened in this iteration, indicating which player
   *               states changed.
   */
  public final void onEvents(@NonNull Player player, Events events) {
    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
      onEventsPlaybackStateChanged(player);
      synchronized (LISTENER_LOCK) {
        if (player.getPlaybackState() == Player.STATE_ENDED) {
          if (mPlaybackEnded) {
            throw new RuntimeException("mPlaybackEnded already set, player could be ended");
          }
          if (!isScrollTest()) {
            mActivity.removePlayerListener();
          } else {
            assertTrue(mScrollRequested);
            mScrollActivity.removePlayerListener();
          }
          // Verify the total time taken by the notification test
          if (getTestType().equals(TestType.CALL_NOTIFICATION_TEST) || getTestType().equals(
              TestType.MESSAGE_NOTIFICATION_TEST)) {
            long actualTime = System.currentTimeMillis() - mStartTime;
            assertEquals((float) mExpectedTotalTime, (float) actualTime, 3000);
          }
          mPlaybackEnded = true;
          LISTENER_LOCK.notify();
        }
      }
    }
    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
      onEventsMediaItemTransition(player);
      // Add duration on media transition.
      long duration = player.getDuration();
      if (duration != C.TIME_UNSET) {
        mExpectedTotalTime += duration;
      }
    }
  }

  /**
   * Called when the value returned from {@link Player#getPlaybackState()} changes.
   *
   * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
   *               states.
   */
  public abstract void onEventsPlaybackStateChanged(@NonNull Player player);

  /**
   * Called when the value returned from {@link Player#getCurrentMediaItem()} changes or the player
   * starts repeating the current item.
   *
   * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
   *               states.
   */
  public abstract void onEventsMediaItemTransition(@NonNull Player player);

  /**
   * Create a message at given position to change the audio or the subtitle track
   *
   * @param sendMessagePosition Position at which message needs to be executed
   * @param trackGroupIndex     Index of the current track group
   * @param trackIndex          Index of the current track
   */
  protected final void createSwitchTrackMessage(long sendMessagePosition, int trackGroupIndex,
      int trackIndex) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          TrackSelectionParameters currentParameters =
              mActivity.mPlayer.getTrackSelectionParameters();
          TrackSelectionParameters newParameters = currentParameters
              .buildUpon()
              .setOverrideForType(
                  new TrackSelectionOverride(
                      mTrackGroups.get(trackGroupIndex).getMediaTrackGroup(),
                      trackIndex))
              .build();
          mActivity.mPlayer.setTrackSelectionParameters(newParameters);
          mConfiguredTrackFormat = mTrackGroups.get(trackGroupIndex)
              .getTrackFormat(trackIndex);
          mTrackChangeRequested = true;
        }).setLooper(Looper.getMainLooper()).setPosition(sendMessagePosition)
        .setDeleteAfterDelivery(true).send();
  }

  /**
   * Called when the value of getCurrentTracks() changes. onEvents(Player, Player.Events) will also
   * be called to report this event along with other events that happen in the same Looper message
   * queue iteration.
   *
   * @param tracks The available tracks information. Never null, but may be of length zero.
   */
  @Override
  public final void onTracksChanged(Tracks tracks) {
    for (Tracks.Group currentTrackGroup : tracks.getGroups()) {
      if (currentTrackGroup.isSelected() && (
          (getTestType().equals(TestType.SWITCH_AUDIO_TRACK_TEST) && (currentTrackGroup.getType()
              == C.TRACK_TYPE_AUDIO)) || (getTestType().equals(TestType.SWITCH_SUBTITLE_TRACK_TEST)
              && (currentTrackGroup.getType() == C.TRACK_TYPE_TEXT)))) {
        for (int trackIndex = 0; trackIndex < currentTrackGroup.length; trackIndex++) {
          if (currentTrackGroup.isTrackSelected(trackIndex)) {
            if (!mTrackChangeRequested) {
              mStartTrackFormat = currentTrackGroup.getTrackFormat(trackIndex);
            } else {
              mCurrentTrackFormat = currentTrackGroup.getTrackFormat(trackIndex);
            }
          }
        }
      }
    }
  }

  /**
   * Get all audio/subtitle tracks group from the player's Tracks.
   */
  protected final List<Tracks.Group> getTrackGroups() {
    List<Tracks.Group> trackGroups = new ArrayList<>();
    Tracks currentTracks = mActivity.mPlayer.getCurrentTracks();
    for (Tracks.Group currentTrackGroup : currentTracks.getGroups()) {
      if ((currentTrackGroup.getType() == C.TRACK_TYPE_AUDIO) || (currentTrackGroup.getType()
          == C.TRACK_TYPE_TEXT)) {
        trackGroups.add(currentTrackGroup);
      }
    }
    return trackGroups;
  }
}

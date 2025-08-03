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

import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;
import android.os.Process;
import android.os.UserManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PlaybackSuppressionReason;
import androidx.test.platform.app.InstrumentationRegistry;

public class CallNotificationTestPlayerListener extends PlayerListener {

  private static final String COMMAND_ENABLE = "telecom set-phone-account-enabled";

  private TelecomManager mTelecomManager;
  private PhoneAccountHandle mPhoneAccountHandle;

  public CallNotificationTestPlayerListener(long sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Create a phone account using a unique handle and return it.
   */
  private PhoneAccount getSamplePhoneAccount() {
    mPhoneAccountHandle = new PhoneAccountHandle(
        new ComponentName(mActivity, CallNotificationService.class), "SampleID");
    return PhoneAccount.builder(mPhoneAccountHandle, "SamplePhoneAccount")
        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
        .build();
  }

  /**
   * Enable the registered phone account by running adb command.
   */
  private void enablePhoneAccount() {
    final ComponentName component = mPhoneAccountHandle.getComponentName();
    final UserManager userManager = mActivity.getSystemService(UserManager.class);
    try {
      String command =
          COMMAND_ENABLE + " " + component.getPackageName() + "/" + component.getClassName() + " "
              + mPhoneAccountHandle.getId() + " " + userManager.getSerialNumberForUser(
              Process.myUserHandle());
      InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public TestType getTestType() {
    return TestType.CALL_NOTIFICATION_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
      mStartTime = System.currentTimeMillis();
      // Add the duration of the incoming call
      mExpectedTotalTime += CallNotificationService.DURATION_MS;
      // Let the ExoPlayer handle audio focus internally
      mActivity.mPlayer.setAudioAttributes(mActivity.mPlayer.getAudioAttributes(), true);
      mTelecomManager = (TelecomManager) mActivity.getApplicationContext().getSystemService(
          Context.TELECOM_SERVICE);
      mTelecomManager.registerPhoneAccount(getSamplePhoneAccount());
      enablePhoneAccount();
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Place a sample incoming call
          mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, null);
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition)
        .setDeleteAfterDelivery(true)
        .send();
  }

  /**
   * Called when the value returned from getPlaybackSuppressionReason() changes. onEvents(Player,
   * Player.Events) will also be called to report this event along with other events that happen in
   * the same Looper message queue iteration.
   *
   * @param playbackSuppressionReason The current {@link PlaybackSuppressionReason}.
   */
  @Override
  public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
    // Verify suppression reason change caused by call notification test
    if (!mActivity.mPlayer.isPlaying()) {
      assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
          playbackSuppressionReason);
    } else {
      assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, playbackSuppressionReason);
    }
  }
}

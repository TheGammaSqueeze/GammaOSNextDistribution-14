/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import static android.provider.CallLog.Calls.USER_MISSED_DND_MODE;
import static android.provider.CallLog.Calls.USER_MISSED_LOW_RING_VOLUME;
import static android.provider.CallLog.Calls.USER_MISSED_NO_VIBRATE;
import static android.provider.Settings.Global.ZEN_MODE_OFF;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Person;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.vibrator.persistence.ParsedVibration;
import android.os.vibrator.persistence.VibrationXmlParser;
import android.telecom.Log;
import android.telecom.TelecomManager;
import android.util.Pair;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.LogUtils.EventTimer;
import com.android.server.telecom.flags.FeatureFlags;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Controls the ringtone player.
 */
@VisibleForTesting
public class Ringer {
    private static final String TAG = "TelecomRinger";

    public interface AccessibilityManagerAdapter {
        boolean startFlashNotificationSequence(@NonNull Context context,
                @AccessibilityManager.FlashNotificationReason int reason);
        boolean stopFlashNotificationSequence(@NonNull Context context);
    }
    /**
     * Flag only for local debugging. Do not submit enabled.
     */
    private static final boolean DEBUG_RINGER = false;

    public static class VibrationEffectProxy {
        public VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
            return VibrationEffect.createWaveform(timings, amplitudes, repeat);
        }

        public VibrationEffect get(Uri ringtoneUri, Context context) {
            return VibrationEffect.get(ringtoneUri, context);
        }
    }
    @VisibleForTesting
    public VibrationEffect mDefaultVibrationEffect;

    // Used for test to notify the completion of RingerAttributes
    private CountDownLatch mAttributesLatch;

    /**
     * Delay to be used between consecutive vibrations when a non-repeating vibration effect is
     * provided by the device.
     *
     * <p>If looking to customize the loop delay for a device's ring vibration, the desired repeat
     * behavior should be encoded directly in the effect specification in the device configuration
     * rather than changing the here (i.e. in `R.raw.default_ringtone_vibration_effect` resource).
     */
    private static int DEFAULT_RING_VIBRATION_LOOP_DELAY_MS = 1000;

    private static final long[] PULSE_PRIMING_PATTERN = {0,12,250,12,500}; // priming  + interval

    private static final int[] PULSE_PRIMING_AMPLITUDE = {0,255,0,255,0};  // priming  + interval

    // ease-in + peak + pause
    private static final long[] PULSE_RAMPING_PATTERN = {
        50,50,50,50,50,50,50,50,50,50,50,50,50,50,300,1000};

    // ease-in (min amplitude = 30%) + peak + pause
    private static final int[] PULSE_RAMPING_AMPLITUDE = {
        77,77,78,79,81,84,87,93,101,114,133,162,205,255,255,0};

    @VisibleForTesting
    public static final long[] PULSE_PATTERN;

    @VisibleForTesting
    public static final int[] PULSE_AMPLITUDE;

    private static final int RAMPING_RINGER_VIBRATION_DURATION = 5000;
    private static final int RAMPING_RINGER_DURATION = 10000;

    static {
        // construct complete pulse pattern
        PULSE_PATTERN = new long[PULSE_PRIMING_PATTERN.length + PULSE_RAMPING_PATTERN.length];
        System.arraycopy(
            PULSE_PRIMING_PATTERN, 0, PULSE_PATTERN, 0, PULSE_PRIMING_PATTERN.length);
        System.arraycopy(PULSE_RAMPING_PATTERN, 0, PULSE_PATTERN,
            PULSE_PRIMING_PATTERN.length, PULSE_RAMPING_PATTERN.length);

        // construct complete pulse amplitude
        PULSE_AMPLITUDE = new int[PULSE_PRIMING_AMPLITUDE.length + PULSE_RAMPING_AMPLITUDE.length];
        System.arraycopy(
            PULSE_PRIMING_AMPLITUDE, 0, PULSE_AMPLITUDE, 0, PULSE_PRIMING_AMPLITUDE.length);
        System.arraycopy(PULSE_RAMPING_AMPLITUDE, 0, PULSE_AMPLITUDE,
            PULSE_PRIMING_AMPLITUDE.length, PULSE_RAMPING_AMPLITUDE.length);
    }

    private static final long[] SIMPLE_VIBRATION_PATTERN = {
            0, // No delay before starting
            1000, // How long to vibrate
            1000, // How long to wait before vibrating again
    };

    private static final int[] SIMPLE_VIBRATION_AMPLITUDE = {
            0, // No delay before starting
            255, // Vibrate full amplitude
            0, // No amplitude while waiting
    };

    /**
     * Indicates that vibration should be repeated at element 5 in the {@link #PULSE_AMPLITUDE} and
     * {@link #PULSE_PATTERN} arrays.  This means repetition will happen for the main ease-in/peak
     * pattern, but the priming + interval part will not be repeated.
     */
    private static final int REPEAT_VIBRATION_AT = 5;

    private static final int REPEAT_SIMPLE_VIBRATION_AT = 1;

    private static final long RINGER_ATTRIBUTES_TIMEOUT = 5000; // 5 seconds

    private static final float EPSILON = 1e-6f;

    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_RINGTONE).build();

    private static VolumeShaper.Configuration mVolumeShaperConfig;

    /**
     * Used to keep ordering of unanswered incoming calls. There can easily exist multiple incoming
     * calls and explicit ordering is useful for maintaining the proper state of the ringer.
     */

    private final SystemSettingsUtil mSystemSettingsUtil;
    private final InCallTonePlayer.Factory mPlayerFactory;
    private final AsyncRingtonePlayer mRingtonePlayer;
    private final Context mContext;
    private final Vibrator mVibrator;
    private final InCallController mInCallController;
    private final VibrationEffectProxy mVibrationEffectProxy;
    private final boolean mIsHapticPlaybackSupportedByDevice;
    private final FeatureFlags mFlags;
    /**
     * For unit testing purposes only; when set, {@link #startRinging(Call, boolean)} will complete
     * the future provided by the test using {@link #setBlockOnRingingFuture(CompletableFuture)}.
     */
    private CompletableFuture<Void> mBlockOnRingingFuture = null;

    private InCallTonePlayer mCallWaitingPlayer;
    private RingtoneFactory mRingtoneFactory;
    private AudioManager mAudioManager;
    private NotificationManager mNotificationManager;
    private AccessibilityManagerAdapter mAccessibilityManagerAdapter;

    /**
     * Call objects that are ringing, vibrating or call-waiting. These are used only for logging
     * purposes (except mVibratingCall is also used to ensure consistency).
     */
    private Call mRingingCall;
    private Call mVibratingCall;
    private Call mCallWaitingCall;

    /**
     * Used to track the status of {@link #mVibrator} in the case of simultaneous incoming calls.
     */
    private boolean mIsVibrating = false;

    private Handler mHandler = null;

    /**
     * Use lock different from the Telecom sync because ringing process is asynchronous outside that
     * lock
     */
    private final Object mLock;

    /** Initializes the Ringer. */
    @VisibleForTesting
    public Ringer(
            InCallTonePlayer.Factory playerFactory,
            Context context,
            SystemSettingsUtil systemSettingsUtil,
            AsyncRingtonePlayer asyncRingtonePlayer,
            RingtoneFactory ringtoneFactory,
            Vibrator vibrator,
            VibrationEffectProxy vibrationEffectProxy,
            InCallController inCallController,
            NotificationManager notificationManager,
            AccessibilityManagerAdapter accessibilityManagerAdapter,
            FeatureFlags featureFlags) {

        mLock = new Object();
        mSystemSettingsUtil = systemSettingsUtil;
        mPlayerFactory = playerFactory;
        mContext = context;
        // We don't rely on getSystemService(Context.VIBRATOR_SERVICE) to make sure this
        // vibrator object will be isolated from others.
        mVibrator = vibrator;
        mRingtonePlayer = asyncRingtonePlayer;
        mRingtoneFactory = ringtoneFactory;
        mInCallController = inCallController;
        mVibrationEffectProxy = vibrationEffectProxy;
        mNotificationManager = notificationManager;
        mAccessibilityManagerAdapter = accessibilityManagerAdapter;

        mDefaultVibrationEffect =
                loadDefaultRingVibrationEffect(
                        mContext, mVibrator, mVibrationEffectProxy, featureFlags);

        mIsHapticPlaybackSupportedByDevice =
                mSystemSettingsUtil.isHapticPlaybackSupported(mContext);

        mAudioManager = mContext.getSystemService(AudioManager.class);
        mFlags = featureFlags;
    }

    @VisibleForTesting
    public void setBlockOnRingingFuture(CompletableFuture<Void> future) {
        mBlockOnRingingFuture = future;
    }

    @VisibleForTesting
    public void setNotificationManager(NotificationManager notificationManager) {
        mNotificationManager = notificationManager;
    }

    public boolean startRinging(Call foregroundCall, boolean isHfpDeviceAttached) {
        boolean deferBlockOnRingingFuture = false;
        // try-finally to ensure that the block on ringing future is always called.
        try {
            if (foregroundCall == null) {
                Log.wtf(this, "startRinging called with null foreground call.");
                return false;
            }

            if (foregroundCall.getState() != CallState.RINGING
                    && foregroundCall.getState() != CallState.SIMULATED_RINGING) {
                // It's possible for bluetooth to connect JUST as a call goes active, which would
                // mean the call would start ringing again.
                Log.i(this, "startRinging called for non-ringing foreground callid=%s",
                        foregroundCall.getId());
                return false;
            }

            // Use completable future to establish a timeout, not intent to make these work outside
            // the main thread asynchronously
            // TODO: moving these RingerAttributes calculation out of Telecom lock to avoid blocking
            CompletableFuture<RingerAttributes> ringerAttributesFuture = CompletableFuture
                    .supplyAsync(() -> getRingerAttributes(foregroundCall, isHfpDeviceAttached),
                            new LoggedHandlerExecutor(getHandler(), "R.sR", null));

            RingerAttributes attributes = null;
            try {
                mAttributesLatch = new CountDownLatch(1);
                attributes = ringerAttributesFuture.get(
                        RINGER_ATTRIBUTES_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                // Keep attributes as null
                Log.i(this, "getAttributes error: " + e);
            }

            if (attributes == null) {
                Log.addEvent(foregroundCall, LogUtils.Events.SKIP_RINGING,
                        "RingerAttributes error");
                return false;
            }

            if (attributes.isEndEarly()) {
                boolean acquireAudioFocus = attributes.shouldAcquireAudioFocus();
                if (attributes.letDialerHandleRinging()) {
                    Log.addEvent(foregroundCall, LogUtils.Events.SKIP_RINGING, "Dialer handles");
                    // Dialer will setup a ringtone, provide the audio focus if its audible.
                    acquireAudioFocus |= attributes.isRingerAudible();
                }

                if (attributes.isSilentRingingRequested()) {
                    Log.addEvent(foregroundCall, LogUtils.Events.SKIP_RINGING, "Silent ringing "
                            + "requested");
                }
                if (attributes.isWorkProfileInQuietMode()) {
                    Log.addEvent(foregroundCall, LogUtils.Events.SKIP_RINGING,
                            "Work profile in quiet mode");
                }
                return acquireAudioFocus;
            }

            stopCallWaiting();

            final boolean shouldFlash = attributes.shouldRingForContact();
            if (mAccessibilityManagerAdapter != null && shouldFlash) {
                Log.addEvent(foregroundCall, LogUtils.Events.FLASH_NOTIFICATION_START);
                getHandler().post(() ->
                        mAccessibilityManagerAdapter.startFlashNotificationSequence(mContext,
                                AccessibilityManager.FLASH_REASON_CALL));
            }

            // Determine if the settings and DND mode indicate that the vibrator can be used right
            // now.
            final boolean isVibratorEnabled =
                    isVibratorEnabled(mContext, attributes.shouldRingForContact());
            boolean shouldApplyRampingRinger =
                    isVibratorEnabled && mSystemSettingsUtil.isRampingRingerEnabled(mContext);

            boolean isHapticOnly = false;
            boolean useCustomVibrationEffect = false;

            mVolumeShaperConfig = null;

            if (attributes.isRingerAudible()) {
                mRingingCall = foregroundCall;
                Log.addEvent(foregroundCall, LogUtils.Events.START_RINGER);
                // Because we wait until a contact info query to complete before processing a
                // call (for the purposes of direct-to-voicemail), the information about custom
                // ringtones should be available by the time this code executes. We can safely
                // request the custom ringtone from the call and expect it to be current.
                if (shouldApplyRampingRinger) {
                    Log.i(this, "create ramping ringer.");
                    float silencePoint = (float) (RAMPING_RINGER_VIBRATION_DURATION)
                            / (float) (RAMPING_RINGER_VIBRATION_DURATION + RAMPING_RINGER_DURATION);
                    mVolumeShaperConfig =
                            new VolumeShaper.Configuration.Builder()
                                    .setDuration(RAMPING_RINGER_VIBRATION_DURATION
                                            + RAMPING_RINGER_DURATION)
                                    .setCurve(
                                            new float[]{0.f, silencePoint + EPSILON
                                                    /*keep monotonicity*/, 1.f},
                                            new float[]{0.f, 0.f, 1.f})
                                    .setInterpolatorType(
                                            VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                                    .build();
                    if (mSystemSettingsUtil.isAudioCoupledVibrationForRampingRingerEnabled()) {
                        useCustomVibrationEffect = true;
                    }
                } else {
                    if (DEBUG_RINGER) {
                        Log.i(this, "Create ringer with custom vibration effect");
                    }
                    // Ramping ringtone is not enabled.
                    useCustomVibrationEffect = true;
                }
            } else {
                Log.addEvent(foregroundCall, LogUtils.Events.SKIP_RINGING,
                        "Inaudible: " + attributes.getInaudibleReason()
                                + " isVibratorEnabled=" + isVibratorEnabled);

                if (isVibratorEnabled) {
                    // If ringer is not audible for this call, then the phone is in "Vibrate" mode.
                    // Use haptic-only ringtone or do not play anything.
                    isHapticOnly = true;
                    if (DEBUG_RINGER) {
                        Log.i(this, "Set ringtone as haptic only: " + isHapticOnly);
                    }
                } else {
                    foregroundCall.setUserMissed(USER_MISSED_NO_VIBRATE);
                    return attributes.shouldAcquireAudioFocus(); // ringer not audible
                }
            }

            boolean hapticChannelsMuted = !isVibratorEnabled || !mIsHapticPlaybackSupportedByDevice;
            if (shouldApplyRampingRinger
                    && !mSystemSettingsUtil.isAudioCoupledVibrationForRampingRingerEnabled()
                    && isVibratorEnabled) {
                Log.i(this, "Muted haptic channels since audio coupled ramping ringer is disabled");
                hapticChannelsMuted = true;
            } else if (hapticChannelsMuted) {
                Log.i(this,
                        "Muted haptic channels isVibratorEnabled=%s, hapticPlaybackSupported=%s",
                        isVibratorEnabled, mIsHapticPlaybackSupportedByDevice);
            }
            // Defer ringtone creation to the async player thread.
            Supplier<Pair<Uri, Ringtone>> ringtoneInfoSupplier;
            final boolean finalHapticChannelsMuted = hapticChannelsMuted;
            if (isHapticOnly) {
                if (hapticChannelsMuted) {
                    Log.i(this,
                            "want haptic only ringtone but haptics are muted, skip ringtone play");
                    ringtoneInfoSupplier = null;
                } else {
                    ringtoneInfoSupplier = mRingtoneFactory::getHapticOnlyRingtone;
                }
            } else {
                ringtoneInfoSupplier = () -> mRingtoneFactory.getRingtone(
                        foregroundCall, mVolumeShaperConfig, finalHapticChannelsMuted);
            }

            // If vibration will be done, reserve the vibrator.
            boolean vibratorReserved = isVibratorEnabled && attributes.shouldRingForContact()
                && tryReserveVibration(foregroundCall);
            if (!vibratorReserved) {
                foregroundCall.setUserMissed(USER_MISSED_NO_VIBRATE);
                Log.addEvent(foregroundCall, LogUtils.Events.SKIP_VIBRATION,
                        "hasVibrator=%b, userRequestsVibrate=%b, ringerMode=%d, "
                                + "isVibratorEnabled=%b",
                        mVibrator.hasVibrator(),
                        mSystemSettingsUtil.isRingVibrationEnabled(mContext),
                        mAudioManager.getRingerMode(), isVibratorEnabled);
            }

            // The vibration logic depends on the loaded ringtone, but we need to defer the ringtone
            // load to the async ringtone thread. Hence, we bundle up the final part of this method
            // for that thread to run after loading the ringtone. This logic is intended to run even
            // if the loaded ringtone is null. However if a stop event arrives before the ringtone
            // creation finishes, then this consumer can be skipped.
            final boolean finalUseCustomVibrationEffect = useCustomVibrationEffect;
            BiConsumer<Pair<Uri, Ringtone>, Boolean> afterRingtoneLogic =
                    (Pair<Uri, Ringtone> ringtoneInfo, Boolean stopped) -> {
                try {
                    Uri ringtoneUri = null;
                    Ringtone ringtone = null;
                    if (ringtoneInfo != null) {
                        ringtoneUri = ringtoneInfo.first;
                        ringtone = ringtoneInfo.second;
                    } else {
                        Log.w(this, "The ringtone could not be loaded.");
                    }

                    if (stopped.booleanValue() || !vibratorReserved) {
                        // don't start vibration if the ringing is already abandoned, or the
                        // vibrator wasn't reserved. This still triggers the mBlockOnRingingFuture.
                        return;
                    }
                    final VibrationEffect vibrationEffect;
                    if (ringtone != null && finalUseCustomVibrationEffect) {
                        if (DEBUG_RINGER) {
                            Log.d(this, "Using ringtone defined vibration effect.");
                        }
                        vibrationEffect = getVibrationEffectForRingtone(ringtoneUri);
                    } else {
                        vibrationEffect = mDefaultVibrationEffect;
                    }

                    boolean isUsingAudioCoupledHaptics =
                            !finalHapticChannelsMuted && ringtone != null
                                    && ringtone.hasHapticChannels();
                    vibrateIfNeeded(isUsingAudioCoupledHaptics, foregroundCall, vibrationEffect);
                } finally {
                    // This is used to signal to tests that the async play() call has completed.
                    if (mBlockOnRingingFuture != null) {
                        mBlockOnRingingFuture.complete(null);
                    }
                }
            };
            deferBlockOnRingingFuture = true;  // Run in vibrationLogic.
            if (ringtoneInfoSupplier != null) {
                mRingtonePlayer.play(ringtoneInfoSupplier, afterRingtoneLogic, isHfpDeviceAttached);
            } else {
                afterRingtoneLogic.accept(/* ringtoneUri, ringtone = */ null, /* stopped= */ false);
            }

            // shouldAcquireAudioFocus is meant to be true, but that check is deferred to here
            // because until now is when we actually know if the ringtone loading worked.
            return attributes.shouldAcquireAudioFocus()
                    || (!isHapticOnly && attributes.isRingerAudible());
        } finally {
            // This is used to signal to tests that the async play() call has completed. It can
            // be deferred into AsyncRingtonePlayer
            if (mBlockOnRingingFuture != null && !deferBlockOnRingingFuture) {
                mBlockOnRingingFuture.complete(null);
            }
        }
    }

    /**
     * Try to reserve the vibrator for this call, returning false if it's already committed.
     * The vibration will be started by AsyncRingtonePlayer to ensure timing is aligned with the
     * audio. The logic uses mVibratingCall to say which call is currently getting ready to vibrate,
     * or actually vibrating (indicated by mIsVibrating).
     *
     * Once reserved, the vibrateIfNeeded method is expected to be called. Note that if
     * audio-coupled haptics were used instead of vibrator, the reservation still stays until
     * ringing is stopped, because the vibrator is exclusive to a single vibration source.
     *
     * Note that this "reservation" is only local to the Ringer - it's not locking the vibrator, so
     * if it's busy with some other important vibration, this ringer's one may not displace it.
     */
    private boolean tryReserveVibration(Call foregroundCall) {
        synchronized (mLock) {
            if (mVibratingCall != null || mIsVibrating) {
                return false;
            }
            mVibratingCall = foregroundCall;
            return true;
        }
   }

    private void vibrateIfNeeded(boolean isUsingAudioCoupledHaptics, Call foregroundCall,
            VibrationEffect effect) {
        if (isUsingAudioCoupledHaptics) {
            Log.addEvent(
                foregroundCall, LogUtils.Events.SKIP_VIBRATION, "using audio-coupled haptics");
            return;
        }

        synchronized (mLock) {
            // Ensure the reservation is live. The mIsVibrating check should be redundant.
            if (foregroundCall == mVibratingCall && !mIsVibrating) {
                Log.addEvent(foregroundCall, LogUtils.Events.START_VIBRATOR,
                    "hasVibrator=%b, userRequestsVibrate=%b, ringerMode=%d, isVibrating=%b",
                    mVibrator.hasVibrator(), mSystemSettingsUtil.isRingVibrationEnabled(mContext),
                    mAudioManager.getRingerMode(), mIsVibrating);
                mIsVibrating = true;
                mVibrator.vibrate(effect, VIBRATION_ATTRIBUTES);
                Log.i(this, "start vibration.");
            }
            // else stopped already: this isn't started unless a reservation was made.
        }
    }

    private VibrationEffect getVibrationEffectForRingtone(Uri ringtoneUri) {
        if (ringtoneUri == null) {
            return mDefaultVibrationEffect;
        }
        try {
            VibrationEffect effect = mVibrationEffectProxy.get(ringtoneUri, mContext);
            if (effect == null) {
              Log.i(this, "did not find vibration effect, falling back to default vibration");
              return mDefaultVibrationEffect;
            }
            return effect;
        } catch (IllegalArgumentException iae) {
            // Deep in the bowels of the VibrationEffect class it is possible for an
            // IllegalArgumentException to be thrown if there is an invalid URI specified in the
            // device config, or a content provider failure.  Rather than crashing the Telecom
            // process we will just use the default vibration effect.
            Log.e(this, iae, "getVibrationEffectForRingtone: failed to get vibration effect");
            return mDefaultVibrationEffect;
        }
    }

    public void startCallWaiting(Call call) {
        startCallWaiting(call, null);
    }

    public void startCallWaiting(Call call, String reason) {
        if (mSystemSettingsUtil.isTheaterModeOn(mContext)) {
            return;
        }

        if (mInCallController.doesConnectedDialerSupportRinging(
                call.getAssociatedUser())) {
            Log.addEvent(call, LogUtils.Events.SKIP_RINGING, "Dialer handles");
            return;
        }

        if (call.isSelfManaged()) {
            Log.addEvent(call, LogUtils.Events.SKIP_RINGING, "Self-managed");
            return;
        }

        Log.v(this, "Playing call-waiting tone.");

        stopRinging();

        if (mCallWaitingPlayer == null) {
            Log.addEvent(call, LogUtils.Events.START_CALL_WAITING_TONE, reason);
            mCallWaitingCall = call;
            mCallWaitingPlayer =
                    mPlayerFactory.createPlayer(call, InCallTonePlayer.TONE_CALL_WAITING);
            mCallWaitingPlayer.startTone();
        }
    }

    public void stopRinging() {
        final Call foregroundCall = mRingingCall != null ? mRingingCall : mVibratingCall;
        if (mAccessibilityManagerAdapter != null) {
            Log.addEvent(foregroundCall, LogUtils.Events.FLASH_NOTIFICATION_STOP);
            getHandler().post(() ->
                    mAccessibilityManagerAdapter.stopFlashNotificationSequence(mContext));
        }

        synchronized (mLock) {
            if (mRingingCall != null) {
                Log.addEvent(mRingingCall, LogUtils.Events.STOP_RINGER);
                mRingingCall = null;
            }

            mRingtonePlayer.stop();

            if (mIsVibrating) {
                Log.addEvent(mVibratingCall, LogUtils.Events.STOP_VIBRATOR);
                mVibrator.cancel();
                mIsVibrating = false;
            }
            mVibratingCall = null;  // Prevents vibrations from starting via AsyncRingtonePlayer.
        }
    }

    public void stopCallWaiting() {
        Log.v(this, "stop call waiting.");
        if (mCallWaitingPlayer != null) {
            if (mCallWaitingCall != null) {
                Log.addEvent(mCallWaitingCall, LogUtils.Events.STOP_CALL_WAITING_TONE);
                mCallWaitingCall = null;
            }

            mCallWaitingPlayer.stopTone();
            mCallWaitingPlayer = null;
        }
    }

    public boolean isRinging() {
        return mRingtonePlayer.isPlaying();
    }

    /**
     * shouldRingForContact checks if the caller matches one of the Do Not Disturb bypass
     * settings (ex. A contact or repeat caller might be able to bypass DND settings). If
     * matchesCallFilter returns true, this means the caller can bypass the Do Not Disturb settings
     * and interrupt the user; otherwise call is suppressed.
     */
    public boolean shouldRingForContact(Call call) {
        // avoid re-computing manager.matcherCallFilter(Bundle)
        if (call.wasDndCheckComputedForCall()) {
            Log.i(this, "shouldRingForContact: returning computation from DndCallFilter.");
            return !call.isCallSuppressedByDoNotDisturb();
        }
        Uri contactUri = call.getHandle();
        if (mFlags.telecomResolveHiddenDependencies()) {
            if (contactUri == null) {
                contactUri = Uri.EMPTY;
            }
            return mNotificationManager.matchesCallFilter(contactUri);
        } else {
            final Bundle peopleExtras = new Bundle();
            if (contactUri != null) {
                ArrayList<Person> personList = new ArrayList<>();
                personList.add(new Person.Builder().setUri(contactUri.toString()).build());
                peopleExtras.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, personList);
            }
            return mNotificationManager.matchesCallFilter(peopleExtras);
        }
    }

    private boolean hasExternalRinger(Call foregroundCall) {
        Bundle intentExtras = foregroundCall.getIntentExtras();
        if (intentExtras != null) {
            return intentExtras.getBoolean(TelecomManager.EXTRA_CALL_HAS_IN_BAND_RINGTONE, false);
        } else {
            return false;
        }
    }

    private boolean isVibratorEnabled(Context context, boolean shouldRingForContact) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Use AudioManager#getRingerMode for more accurate result, instead of
        // AudioManager#getRingerModeInternal which only useful for volume controllers
        boolean zenModeOn = mNotificationManager != null
                && mNotificationManager.getZenMode() != ZEN_MODE_OFF;
        return mVibrator.hasVibrator()
                && mSystemSettingsUtil.isRingVibrationEnabled(context)
                && (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT
                || (zenModeOn && shouldRingForContact));
    }

    private RingerAttributes getRingerAttributes(Call call, boolean isHfpDeviceAttached) {
        mAudioManager = mContext.getSystemService(AudioManager.class);
        RingerAttributes.Builder builder = new RingerAttributes.Builder();

        LogUtils.EventTimer timer = new EventTimer();

        boolean isVolumeOverZero = mAudioManager.getStreamVolume(AudioManager.STREAM_RING) > 0;
        timer.record("isVolumeOverZero");
        boolean shouldRingForContact = shouldRingForContact(call);
        timer.record("shouldRingForContact");
        boolean isSelfManaged = call.isSelfManaged();
        timer.record("isSelfManaged");
        boolean isSilentRingingRequested = call.isSilentRingingRequested();
        timer.record("isSilentRingRequested");

        boolean isRingerAudible = isVolumeOverZero && shouldRingForContact;
        timer.record("isRingerAudible");
        String inaudibleReason = "";
        if (!isRingerAudible) {
            inaudibleReason = String.format("isVolumeOverZero=%s, shouldRingForContact=%s",
                isVolumeOverZero, shouldRingForContact);
        }

        boolean hasExternalRinger = hasExternalRinger(call);
        timer.record("hasExternalRinger");
        // Don't do call waiting operations or vibration unless these are false.
        boolean isTheaterModeOn = mSystemSettingsUtil.isTheaterModeOn(mContext);
        timer.record("isTheaterModeOn");
        boolean letDialerHandleRinging = mInCallController.doesConnectedDialerSupportRinging(
                call.getAssociatedUser());
        timer.record("letDialerHandleRinging");
        boolean isWorkProfileInQuietMode =
                isProfileInQuietMode(call.getAssociatedUser());
        timer.record("isWorkProfileInQuietMode");

        Log.i(this, "startRinging timings: " + timer);
        boolean endEarly = isTheaterModeOn || letDialerHandleRinging || isSelfManaged ||
                hasExternalRinger || isSilentRingingRequested || isWorkProfileInQuietMode;

        if (endEarly) {
            Log.i(this, "Ending early -- isTheaterModeOn=%s, letDialerHandleRinging=%s, " +
                            "isSelfManaged=%s, hasExternalRinger=%s, silentRingingRequested=%s, " +
                            "isWorkProfileInQuietMode=%s",
                    isTheaterModeOn, letDialerHandleRinging, isSelfManaged, hasExternalRinger,
                    isSilentRingingRequested, isWorkProfileInQuietMode);
        }

        // Acquire audio focus under any of the following conditions:
        // 1. Should ring for contact and there's an HFP device attached
        // 2. Volume is over zero, we should ring for the contact, and there's a audible ringtone
        //    present. (This check is deferred until ringer knows the ringtone)
        // 3. The call is self-managed.
        boolean shouldAcquireAudioFocus = !isWorkProfileInQuietMode &&
                ((isHfpDeviceAttached && shouldRingForContact) || isSelfManaged);

        // Set missed reason according to attributes
        if (!isVolumeOverZero) {
            call.setUserMissed(USER_MISSED_LOW_RING_VOLUME);
        }
        if (!shouldRingForContact) {
            call.setUserMissed(USER_MISSED_DND_MODE);
        }

        mAttributesLatch.countDown();
        return builder.setEndEarly(endEarly)
                .setLetDialerHandleRinging(letDialerHandleRinging)
                .setAcquireAudioFocus(shouldAcquireAudioFocus)
                .setRingerAudible(isRingerAudible)
                .setInaudibleReason(inaudibleReason)
                .setShouldRingForContact(shouldRingForContact)
                .setSilentRingingRequested(isSilentRingingRequested)
                .setWorkProfileQuietMode(isWorkProfileInQuietMode)
                .build();
    }

    private boolean isProfileInQuietMode(UserHandle user) {
        UserManager um = mContext.getSystemService(UserManager.class);
        return um.isManagedProfile(user.getIdentifier()) && um.isQuietModeEnabled(user);
    }

    private Handler getHandler() {
        if (mHandler == null) {
            HandlerThread handlerThread = new HandlerThread("Ringer");
            handlerThread.start();
            mHandler = handlerThread.getThreadHandler();
        }
        return mHandler;
    }

    @VisibleForTesting
    public boolean waitForAttributesCompletion() throws InterruptedException {
        if (mAttributesLatch != null) {
            return mAttributesLatch.await(RINGER_ATTRIBUTES_TIMEOUT, TimeUnit.MILLISECONDS);
        } else {
            return false;
        }
    }

    @Nullable
    private static VibrationEffect loadSerializedDefaultRingVibration(
            Resources resources, Vibrator vibrator) {
        try {
            InputStream vibrationInputStream =
                    resources.openRawResource(
                            com.android.internal.R.raw.default_ringtone_vibration_effect);
            ParsedVibration parsedVibration = VibrationXmlParser
                    .parseDocument(
                            new InputStreamReader(vibrationInputStream, StandardCharsets.UTF_8));
            if (parsedVibration == null) {
                Log.w(TAG, "Got null parsed default ring vibration effect.");
                return null;
            }
            return parsedVibration.resolve(vibrator);
        } catch (IOException | Resources.NotFoundException e) {
            Log.e(TAG, e, "Error parsing default ring vibration effect.");
            return null;
        }
    }

    private static VibrationEffect loadDefaultRingVibrationEffect(
            Context context,
            Vibrator vibrator,
            VibrationEffectProxy vibrationEffectProxy,
            FeatureFlags featureFlags) {
        Resources resources = context.getResources();

        if (resources.getBoolean(R.bool.use_simple_vibration_pattern)) {
            Log.i(TAG, "Using simple default ring vibration.");
            return createSimpleRingVibration(vibrationEffectProxy);
        }

        if (featureFlags.useDeviceProvidedSerializedRingerVibration()) {
            VibrationEffect parsedEffect = loadSerializedDefaultRingVibration(resources, vibrator);
            if (parsedEffect != null) {
                Log.i(TAG, "Using parsed default ring vibration.");
                // Make the parsed effect repeating to make it vibrate continuously during ring.
                // If the effect is already repeating, this API call is a no-op.
                // Otherwise, it  uses `DEFAULT_RING_VIBRATION_LOOP_DELAY_MS` when changing a
                // non-repeating vibration to a repeating vibration.
                // This is so that we ensure consecutive loops of the vibration play with some gap
                // in between.
                return parsedEffect.applyRepeatingIndefinitely(
                        /* wantRepeating= */ true, DEFAULT_RING_VIBRATION_LOOP_DELAY_MS);
            }
            // Fallback to the simple vibration if the serialized effect cannot be loaded.
            return createSimpleRingVibration(vibrationEffectProxy);
        }

        Log.i(TAG, "Using pulse default ring vibration.");
        return vibrationEffectProxy.createWaveform(
                PULSE_PATTERN, PULSE_AMPLITUDE, REPEAT_VIBRATION_AT);
    }

    private static VibrationEffect createSimpleRingVibration(
            VibrationEffectProxy vibrationEffectProxy) {
        return vibrationEffectProxy.createWaveform(SIMPLE_VIBRATION_PATTERN,
                SIMPLE_VIBRATION_AMPLITUDE, REPEAT_SIMPLE_VIBRATION_AT);
    }
}

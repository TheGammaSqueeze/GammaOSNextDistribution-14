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
 * limitations under the License.
 */
package com.android.car.audio;

import static android.car.builtin.media.AudioManagerHelper.UNDEFINED_STREAM_TYPE;
import static android.car.builtin.media.AudioManagerHelper.isMasterMute;
import static android.car.feature.Flags.carAudioFadeManagerConfiguration;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_AUDIO_MIRRORING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_MIN_MAX_ACTIVATION_VOLUME;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_OEM_AUDIO_SERVICE;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_EVENTS;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.CONFIG_STATUS_CHANGED;
import static android.car.media.CarAudioManager.CarAudioFeature;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;
import static android.car.media.CarAudioManager.INVALID_VOLUME_GROUP_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EXTRA_INFO_ATTENUATION_ACTIVATION;
import static android.car.media.CarVolumeGroupEvent.EXTRA_INFO_SHOW_UI;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_SAME;
import static android.media.AudioManager.ADJUST_TOGGLE_MUTE;
import static android.media.AudioManager.FLAG_FROM_KEY;
import static android.media.AudioManager.FLAG_PLAY_SOUND;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.audiopolicy.Flags.enableFadeManagerConfiguration;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

import static com.android.car.audio.CarAudioUtils.convertVolumeChangeToEvent;
import static com.android.car.audio.CarAudioUtils.convertVolumeChangesToEvents;
import static com.android.car.audio.CarAudioUtils.excludesDynamicDevices;
import static com.android.car.audio.CarAudioUtils.getDynamicDevicesInConfig;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEPRECATED_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;

import static java.util.Collections.EMPTY_LIST;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.ICarOccupantZoneCallback;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.media.AudioManagerHelper.AudioPatchInfo;
import android.car.builtin.media.AudioManagerHelper.VolumeAndMuteReceiver;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.feature.Flags;
import android.car.media.AudioZonesMirrorStatusCallback;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioPatchHandle;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.IAudioZoneConfigurationsChangeCallback;
import android.car.media.IAudioZonesMirrorStatusCallback;
import android.car.media.ICarAudio;
import android.car.media.ICarVolumeCallback;
import android.car.media.ICarVolumeEventCallback;
import android.car.media.IMediaAudioRequestStatusCallback;
import android.car.media.IPrimaryZoneMediaAudioRequestCallback;
import android.car.media.ISwitchAudioZoneConfigCallback;
import android.car.oem.CarAudioFadeConfiguration;
import android.car.oem.CarAudioFeaturesInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.AudioManager.AudioServerStateCallback;
import android.media.FadeManagerConfiguration;
import android.media.audiopolicy.AudioPolicy;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;
import android.view.KeyEvent;

import com.android.car.CarInputService;
import com.android.car.CarInputService.KeyEventListener;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarOccupantZoneService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.audio.CarAudioDumpProto.AudioZoneToOccupantZone;
import com.android.car.audio.CarAudioDumpProto.CarAudioConfiguration;
import com.android.car.audio.CarAudioDumpProto.CarAudioState;
import com.android.car.audio.CarAudioDumpProto.UidToAudioZone;
import com.android.car.audio.CarAudioDumpProto.UserIdToAudioZone;
import com.android.car.audio.CarAudioPolicyVolumeCallback.AudioPolicyVolumeCallbackInternal;
import com.android.car.audio.hal.AudioControlFactory;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.AudioControlWrapperV1;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.audio.hal.HalAudioFocus;
import com.android.car.audio.hal.HalAudioGainCallback;
import com.android.car.audio.hal.HalAudioModuleChangeCallback;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.annotation.AttributeUsage;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.LocalLog;
import com.android.car.oem.CarOemProxyService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Service responsible for interaction with car's audio system.
 */
public final class CarAudioService extends ICarAudio.Stub implements CarServiceBase {

    static final String TAG = CarLog.TAG_AUDIO;
    private static final String MIRROR_COMMAND_SEPARATOR = ";";
    private static final String MIRROR_COMMAND_DESTINATION_SEPARATOR = ",";
    private static final String MIRROR_COMMAND_SOURCE = "mirroring_src=";
    private static final String MIRROR_COMMAND_DESTINATION = "mirroring_dst=";
    private static final String DISABLE_AUDIO_MIRRORING = "mirroring=off";

    private static final String CAR_AUDIO_SERVICE_THREAD_NAME = "CarAudioService";

    static final AudioAttributes CAR_DEFAULT_AUDIO_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);

    private static final String PROPERTY_RO_ENABLE_AUDIO_PATCH =
            "ro.android.car.audio.enableaudiopatch";

    // CarAudioService reads configuration from the following paths respectively.
    // If the first one is found, all others are ignored.
    // If no one is found, it fallbacks to car_volume_groups.xml resource file.
    private static final String[] AUDIO_CONFIGURATION_PATHS = new String[] {
            "/vendor/etc/car_audio_configuration.xml",
            "/system/etc/car_audio_configuration.xml"
    };

    private static final String FADE_CONFIGURATION_PATH =
            "/vendor/etc/car_audio_fade_configuration.xml";

    private static final List<Integer> KEYCODES_OF_INTEREST = List.of(
            KEYCODE_VOLUME_DOWN,
            KEYCODE_VOLUME_UP,
            KEYCODE_VOLUME_MUTE
    );
    private static final AudioAttributes MEDIA_AUDIO_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final int EVENT_LOGGER_QUEUE_SIZE = 50;

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            CAR_AUDIO_SERVICE_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final Object mImplLock = new Object();

    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final AudioManager mAudioManager;
    private final boolean mUseDynamicRouting;
    private final boolean mUseCoreAudioVolume;
    private final boolean mUseCoreAudioRouting;
    private final boolean mUseCarVolumeGroupEvents;
    private final boolean mUseCarVolumeGroupMuting;
    private final boolean mUseHalDuckingSignals;
    private final boolean mUseMinMaxActivationVolume;
    private final boolean mUseIsolatedFocusForDynamicDevices;
    private final @CarVolume.CarVolumeListVersion int mAudioVolumeAdjustmentContextsVersion;
    private final boolean mPersistMasterMuteState;
    private final boolean mUseFadeManagerConfiguration;
    private final CarAudioSettings mCarAudioSettings;
    private final int mKeyEventTimeoutMs;
    private final MediaRequestHandler mMediaRequestHandler = new MediaRequestHandler();
    private final CarAudioMirrorRequestHandler mCarAudioMirrorRequestHandler =
            new CarAudioMirrorRequestHandler();
    private final CarVolumeEventHandler mCarVolumeEventHandler = new CarVolumeEventHandler();
    private final AudioServerStateCallback mAudioServerStateCallback;

    private final CarAudioDeviceCallback mAudioDeviceInfoCallback;

    private final LocalLog mServiceEventLogger = new LocalLog(EVENT_LOGGER_QUEUE_SIZE);

    private AudioControlWrapper mAudioControlWrapper;
    private CarDucking mCarDucking;
    private CarVolumeGroupMuting mCarVolumeGroupMuting;
    private HalAudioFocus mHalAudioFocus;
    private @Nullable CarAudioGainMonitor mCarAudioGainMonitor;
    @GuardedBy("mImplLock")
    private @Nullable CoreAudioVolumeGroupCallback mCoreAudioVolumeGroupCallback;

    private CarOccupantZoneService mOccupantZoneService;
    private CarAudioModuleChangeMonitor mCarAudioModuleChangeMonitor;
    private @Nullable CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;

    /**
     * Simulates {@link ICarVolumeCallback} when it's running in legacy mode.
     * This receiver assumes the intent is sent to {@link CarAudioManager#PRIMARY_AUDIO_ZONE}.
     */
    private final VolumeAndMuteReceiver mLegacyVolumeChangedHelper =
            new AudioManagerHelper.VolumeAndMuteReceiver() {
                @Override
                public void onVolumeChanged(int streamType) {
                    if (streamType == UNDEFINED_STREAM_TYPE) {
                        Slogf.w(TAG, "Invalid stream type: %d", streamType);
                    }
                    int groupId = getVolumeGroupIdForStreamType(streamType);
                    if (groupId == INVALID_VOLUME_GROUP_ID) {
                        Slogf.w(TAG, "Unknown stream type: %d", streamType);
                    } else {
                        callbackGroupVolumeChange(PRIMARY_AUDIO_ZONE, groupId,
                                FLAG_FROM_KEY | FLAG_SHOW_UI);
                    }
                }

                @Override
                public void onMuteChanged() {
                    callbackMasterMuteChange(PRIMARY_AUDIO_ZONE, FLAG_FROM_KEY | FLAG_SHOW_UI);
                }
    };

    private final KeyEventListener mCarKeyEventListener = new KeyEventListener() {
        @Override
        public void onKeyEvent(KeyEvent event, int displayType, int seat) {
            Slogf.i(TAG, "On key event for audio with display type: %d and seat %d", displayType,
                    seat);
            if (event.getAction() != ACTION_DOWN) {
                return;
            }
            int audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(
                    mOccupantZoneService.getOccupantZoneIdForSeat(seat));
            if (!isAudioZoneIdValid(audioZoneId)) {
                Slogf.e(TAG, "Audio zone is invalid for event %s, displayType %d, and seat %d",
                        event, displayType, seat);
                return;
            }
            int adjustment;
            switch (event.getKeyCode()) {
                case KEYCODE_VOLUME_DOWN:
                    adjustment = ADJUST_LOWER;
                    break;
                case KEYCODE_VOLUME_UP:
                    adjustment = ADJUST_RAISE;
                    break;
                case KEYCODE_VOLUME_MUTE:
                    adjustment = ADJUST_TOGGLE_MUTE;
                    break;
                default:
                    adjustment = ADJUST_SAME;
                    break;
            }
            synchronized (mImplLock) {
                mCarAudioPolicyVolumeCallback.onVolumeAdjustment(adjustment, audioZoneId);
            }
        }
    };

    @GuardedBy("mImplLock")
    private AudioPolicy mVolumeControlAudioPolicy;
    @GuardedBy("mImplLock")
    private AudioPolicy mFocusControlAudioPolicy;
    @GuardedBy("mImplLock")
    private AudioPolicy mRoutingAudioPolicy;
    @GuardedBy("mImplLock")
    private AudioPolicy mFadeManagerConfigAudioPolicy;
    private CarZonesAudioFocus mFocusHandler;
    private String mCarAudioConfigurationPath;
    private String mCarAudioFadeConfigurationPath;
    private CarAudioFadeConfigurationHelper mCarAudioFadeConfigurationHelper;
    private SparseIntArray mAudioZoneIdToOccupantZoneIdMapping;
    @GuardedBy("mImplLock")
    private SparseArray<CarAudioZone> mCarAudioZones;
    @GuardedBy("mImplLock")
    private CarVolume mCarVolume;
    @GuardedBy("mImplLock")
    private CarAudioContext mCarAudioContext;
    private final CarVolumeCallbackHandler mCarVolumeCallbackHandler;
    private final SparseIntArray mAudioZoneIdToUserIdMapping;
    private final SystemClockWrapper mClock = new SystemClockWrapper();

    @GuardedBy("mImplLock")
    private final SparseArray<DeathRecipient>
            mUserAssignedToPrimaryZoneToCallbackDeathRecipient = new SparseArray<>();

    private final RemoteCallbackList<IAudioZoneConfigurationsChangeCallback> mConfigsCallbacks =
            new RemoteCallbackList<>();

    // TODO do not store uid mapping here instead use the uid
    //  device affinity in audio policy when available
    private Map<Integer, Integer> mUidToZoneMap;
    private CarAudioPlaybackCallback mCarAudioPlaybackCallback;
    private CarAudioPowerListener mCarAudioPowerListener;
    private CarInputService mCarInputService;

    private final HalAudioGainCallback mHalAudioGainCallback =
            new HalAudioGainCallback() {
                @Override
                public void onAudioDeviceGainsChanged(
                        List<Integer> halReasons, List<CarAudioGainConfigInfo> gains) {
                    synchronized (mImplLock) {
                        handleAudioDeviceGainsChangedLocked(halReasons, gains);
                    }
                }
            };

    private final ICarOccupantZoneCallback mOccupantZoneCallback =
            new ICarOccupantZoneCallback.Stub() {
                @Override
                public void onOccupantZoneConfigChanged(int flags) {
                    Slogf.d(TAG, "onOccupantZoneConfigChanged(%d)", flags);
                    if (((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)
                            != CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)
                            && ((flags & CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY)
                            != CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY)) {
                        return;
                    }
                    handleOccupantZoneUserChanged();
                }
            };
    @GuardedBy("mImplLock")
    private CarAudioPolicyVolumeCallback mCarAudioPolicyVolumeCallback;

    private final HalAudioModuleChangeCallback mHalAudioModuleChangeCallback =
            new HalAudioModuleChangeCallback() {
                @Override
                public void onAudioPortsChanged(List<HalAudioDeviceInfo> deviceInfos) {
                    synchronized (mImplLock) {
                        handleAudioPortsChangedLocked(deviceInfos);
                    }
                }
            };

    public CarAudioService(Context context) {
        this(context, getAudioConfigurationPath(), new CarVolumeCallbackHandler(),
                getAudioFadeConfigurationPath());
    }

    @VisibleForTesting
    CarAudioService(Context context, @Nullable String audioConfigurationPath,
            CarVolumeCallbackHandler carVolumeCallbackHandler,
            @Nullable String audioFadeConfigurationPath) {
        mContext = Objects.requireNonNull(context,
                "Context to create car audio service can not be null");
        mCarAudioConfigurationPath = audioConfigurationPath;
        mCarAudioFadeConfigurationPath = audioFadeConfigurationPath;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mUseDynamicRouting = mContext.getResources().getBoolean(R.bool.audioUseDynamicRouting);
        mUseCoreAudioVolume = mContext.getResources().getBoolean(R.bool.audioUseCoreVolume);
        mUseCoreAudioRouting = mContext.getResources().getBoolean(R.bool.audioUseCoreRouting);
        mKeyEventTimeoutMs =
                mContext.getResources().getInteger(R.integer.audioVolumeKeyEventTimeoutMs);
        mUseHalDuckingSignals = mContext.getResources().getBoolean(
                R.bool.audioUseHalDuckingSignals);

        mUidToZoneMap = new HashMap<>();
        mCarVolumeCallbackHandler = carVolumeCallbackHandler;
        mCarAudioSettings = new CarAudioSettings(mContext);
        mAudioZoneIdToUserIdMapping = new SparseIntArray();
        mAudioVolumeAdjustmentContextsVersion =
                mContext.getResources().getInteger(R.integer.audioVolumeAdjustmentContextsVersion);
        boolean useCarVolumeGroupMuting = !runInLegacyMode() && mContext.getResources().getBoolean(
                R.bool.audioUseCarVolumeGroupMuting);
        mUseCarVolumeGroupEvents = !runInLegacyMode() && mContext.getResources().getBoolean(
                R.bool.audioUseCarVolumeGroupEvent);
        mUseCarVolumeGroupMuting = useCarVolumeGroupMuting;
        mPersistMasterMuteState = !mUseCarVolumeGroupMuting && mContext.getResources().getBoolean(
                R.bool.audioPersistMasterMuteState);
        mUseFadeManagerConfiguration = enableFadeManagerConfiguration()
                && carAudioFadeManagerConfiguration()
                && mContext.getResources().getBoolean(R.bool.audioUseFadeManagerConfiguration);
        mUseMinMaxActivationVolume = Flags.carAudioMinMaxActivationVolume() && !runInLegacyMode()
                && mContext.getResources().getBoolean(R.bool.audioUseMinMaxActivationVolume);
        mUseIsolatedFocusForDynamicDevices = Flags.carAudioDynamicDevices() && !runInLegacyMode()
                && mContext.getResources().getBoolean(
                        R.bool.audioUseIsolatedAudioFocusForDynamicDevices);
        validateFeatureFlagSettings();
        mAudioServerStateCallback = new CarAudioServerStateCallback(this);
        mAudioDeviceInfoCallback = new CarAudioDeviceCallback(this);
    }

    private void validateFeatureFlagSettings() {
        Preconditions.checkArgument(!(runInLegacyMode() && mUseFadeManagerConfiguration),
                "Fade manager configuration feature can not be enabled in legacy mode");
    }

    /**
     * Dynamic routing and volume groups are set only if
     * {@link #runInLegacyMode} is {@code false}. Otherwise, this service runs in legacy mode.
     */
    @Override
    public void init() {
        synchronized (mImplLock) {
            mOccupantZoneService = CarLocalServices.getService(CarOccupantZoneService.class);
            mCarInputService = CarLocalServices.getService(CarInputService.class);
            if (!runInLegacyMode()) {
                // Must be called before setting up policies or audio control hal
                loadAndInitCarAudioZonesLocked();
                setupCarAudioPlaybackMonitor();
                setupAudioControlDuckingAndVolumeControlLocked();
                setupControlAndRoutingAudioPoliciesLocked();
                setupFadeManagerConfigAudioPolicyLocked();
                setupHalAudioFocusListenerLocked();
                setupHalAudioGainCallbackLocked();
                setupHalAudioModuleChangeCallbackLocked();
                setupAudioConfigurationCallbackLocked();
                setupPowerPolicyListener();
                mCarInputService.registerKeyEventListener(mCarKeyEventListener,
                        KEYCODES_OF_INTEREST);
                setupAudioDeviceInfoCallback();
            } else {
                Slogf.i(TAG, "Audio dynamic routing not enabled, run in legacy mode");
                setupLegacyVolumeChangedListener();
            }

            mAudioManager.setSupportedSystemUsages(CarAudioContext.getSystemUsages());
            mAudioManager.setAudioServerStateCallback(mContext.getMainExecutor(),
                    mAudioServerStateCallback);
        }

        restoreMasterMuteState();
    }

    private void setupAudioDeviceInfoCallback() {
        if (!Flags.carAudioDynamicDevices()) {
            return;
        }
        mAudioManager.registerAudioDeviceCallback(mAudioDeviceInfoCallback, mHandler);
    }

    private void releaseAudioDeviceInfoCallback() {
        if (!Flags.carAudioDynamicDevices()) {
            return;
        }
        mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceInfoCallback);
    }

    private void setupPowerPolicyListener() {
        mCarAudioPowerListener = CarAudioPowerListener.newCarAudioPowerListener(this);
        mCarAudioPowerListener.startListeningForPolicyChanges();
    }

    private void restoreMasterMuteState() {
        if (mUseCarVolumeGroupMuting) {
            return;
        }
        // Restore master mute state if applicable
        if (mPersistMasterMuteState) {
            boolean storedMasterMute = mCarAudioSettings.isMasterMute();
            setMasterMute(storedMasterMute, 0);
        }
    }

    @Override
    public void release() {
        synchronized (mImplLock) {
            if (!runInLegacyMode()) {
                releaseAudioPoliciesLocked();
                releaseAudioDeviceInfoCallback();
            } else {
                AudioManagerHelper.unregisterVolumeAndMuteReceiver(mContext,
                        mLegacyVolumeChangedHelper);
            }
            if (mCoreAudioVolumeGroupCallback != null) {
                mCoreAudioVolumeGroupCallback.release();
            }
            mCarVolumeCallbackHandler.release();
            mOccupantZoneService.unregisterCallback(mOccupantZoneCallback);

            if (mHalAudioFocus != null) {
                mHalAudioFocus.unregisterFocusListener();
            }

            if (mAudioControlWrapper != null) {
                try {
                    mAudioControlWrapper.clearModuleChangeCallback();
                } catch (Exception e) {
                    Slogf.w(TAG, "Failed to clear audio control wrapper module change callback",
                            e);
                }
                mAudioControlWrapper.unlinkToDeath();
                mAudioControlWrapper = null;
            }

            if (mCarAudioPowerListener != null) {
                mCarAudioPowerListener.stopListeningForPolicyChanges();
            }
            mAudioManager.clearAudioServerStateCallback();
            mCarInputService.unregisterKeyEventListener(mCarKeyEventListener);
        }
    }

    @GuardedBy("mImplLock")
    private void releaseAudioPoliciesLocked() {
        releaseAudioRoutingPolicyLocked();
        releaseVolumeControlAudioPolicyLocked();
        releaseFocusControlAudioPolicyLocked();
        releaseFadeManagerConfigAudioPolicyLocked();
    }

    @GuardedBy("mImplLock")
    private void releaseVolumeControlAudioPolicyLocked() {
        if (mVolumeControlAudioPolicy == null) {
            return;
        }
        mAudioManager.unregisterAudioPolicyAsync(mVolumeControlAudioPolicy);
        mVolumeControlAudioPolicy = null;
        mCarAudioPolicyVolumeCallback = null;
    }

    @GuardedBy("mImplLock")
    private void releaseFocusControlAudioPolicyLocked() {
        if (mFocusControlAudioPolicy == null) {
            return;
        }
        mAudioManager.unregisterAudioPolicyAsync(mFocusControlAudioPolicy);
        mFocusControlAudioPolicy = null;
        mFocusHandler.setOwningPolicy(null, null);
        mFocusHandler = null;
    }

    @GuardedBy("mImplLock")
    private void releaseAudioRoutingPolicyLocked() {
        if (mRoutingAudioPolicy == null) {
            return;
        }
        mAudioManager.unregisterAudioPolicyAsync(mRoutingAudioPolicy);
        mRoutingAudioPolicy = null;
    }

    @GuardedBy("mImplLock")
    private void releaseFadeManagerConfigAudioPolicyLocked() {
        if (!mUseFadeManagerConfiguration || mFadeManagerConfigAudioPolicy == null) {
            return;
        }

        mAudioManager.unregisterAudioPolicyAsync(mFadeManagerConfigAudioPolicy);
        mFadeManagerConfigAudioPolicy = null;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mImplLock) {
            writer.println("*CarAudioService*");
            writer.increaseIndent();

            writer.println("Configurations:");
            writer.increaseIndent();
            writer.printf("Run in legacy mode? %b\n", runInLegacyMode());
            writer.printf("Rely on core audio for volume? %b\n", mUseCoreAudioVolume);
            writer.printf("Rely on core audio for routing? %b\n",  mUseCoreAudioRouting);
            writer.printf("Audio Patch APIs enabled? %b\n", areAudioPatchAPIsEnabled());
            writer.printf("Persist master mute state? %b\n", mPersistMasterMuteState);
            writer.printf("Use hal ducking signals? %b\n", mUseHalDuckingSignals);
            writer.printf("Volume key event timeout ms: %d\n", mKeyEventTimeoutMs);
            if (mCarAudioConfigurationPath != null) {
                writer.printf("Car audio configuration path: %s\n", mCarAudioConfigurationPath);
            }
            writer.decreaseIndent();
            writer.println();

            writer.println("Current State:");
            writer.increaseIndent();
            writer.printf("Master muted? %b\n", isMasterMute(mAudioManager));
            if (mCarAudioPowerListener != null) {
                writer.printf("Audio enabled? %b\n", mCarAudioPowerListener.isAudioEnabled());
            }
            writer.decreaseIndent();
            writer.println();

            if (!runInLegacyMode()) {
                writer.printf("Volume Group Mute Enabled? %b\n", mUseCarVolumeGroupMuting);
                writer.printf("Volume Group Events Enabled? %b\n", mUseCarVolumeGroupEvents);
                writer.printf("Use fade manager configuration? %b\n", mUseFadeManagerConfiguration);
                writer.printf("Use min/max activation volume? %b\n", mUseMinMaxActivationVolume);
                writer.printf("Use isolated focus for dynamic devices? %b\n",
                        mUseIsolatedFocusForDynamicDevices);
                writer.println();
                mCarVolume.dump(writer);
                writer.println();
                mCarAudioContext.dump(writer);
                writer.println();
                for (int i = 0; i < mCarAudioZones.size(); i++) {
                    CarAudioZone zone = mCarAudioZones.valueAt(i);
                    zone.dump(writer);
                }

                writer.println();
                writer.println("UserId to Zone Mapping:");
                writer.increaseIndent();
                for (int index = 0; index < mAudioZoneIdToUserIdMapping.size(); index++) {
                    int audioZoneId = mAudioZoneIdToUserIdMapping.keyAt(index);
                    writer.printf("UserId %d mapped to zone %d\n",
                            mAudioZoneIdToUserIdMapping.get(audioZoneId),
                            audioZoneId);
                }
                writer.decreaseIndent();
                writer.println();
                writer.println("Audio Zone to Occupant Zone Mapping:");
                writer.increaseIndent();
                for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                    int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                    writer.printf("AudioZoneId %d mapped to OccupantZoneId %d\n", audioZoneId,
                            mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId));
                }
                writer.decreaseIndent();
                writer.println();
                writer.println("UID to Zone Mapping:");
                writer.increaseIndent();
                for (int callingId : mUidToZoneMap.keySet()) {
                    writer.printf("UID %d mapped to zone %d\n",
                            callingId,
                            mUidToZoneMap.get(callingId));
                }
                writer.decreaseIndent();

                writer.println();
                mFocusHandler.dump(writer);

                writer.println();
                getAudioControlWrapperLocked().dump(writer);

                if (mHalAudioFocus != null) {
                    writer.println();
                    mHalAudioFocus.dump(writer);
                } else {
                    writer.println("No HalAudioFocus instance\n");
                }
                if (mCarDucking != null) {
                    writer.println();
                    mCarDucking.dump(writer);
                }
                if (mCarVolumeGroupMuting != null) {
                    mCarVolumeGroupMuting.dump(writer);
                }
                if (mCarAudioPlaybackCallback != null) {
                    mCarAudioPlaybackCallback.dump(writer);
                }

                mCarAudioMirrorRequestHandler.dump(writer);
                mMediaRequestHandler.dump(writer);
                writer.printf("Number of car audio configs callback registered: %d\n",
                        mConfigsCallbacks.getRegisteredCallbackCount());
                writer.printf("Car audio fade configurations available? %b\n",
                        mCarAudioFadeConfigurationHelper != null);
                if (mCarAudioFadeConfigurationHelper != null) {
                    mCarAudioFadeConfigurationHelper.dump(writer);
                }
            }

            writer.println("Service Events:");
            writer.increaseIndent();
            mServiceEventLogger.dump(writer);
            writer.decreaseIndent();

            writer.decreaseIndent();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        synchronized (mImplLock) {
            long currentStateToken = proto.start(CarAudioDumpProto.CURRENT_STATE);
            proto.write(CarAudioState.MASTER_MUTED, isMasterMute(mAudioManager));
            if (mCarAudioPowerListener != null) {
                proto.write(CarAudioState.AUDIO_ENABLED, mCarAudioPowerListener.isAudioEnabled());
            }
            proto.end(currentStateToken);

            long configurationToken = proto.start(CarAudioDumpProto.CONFIGURATION);
            proto.write(CarAudioConfiguration.USE_DYNAMIC_ROUTING, !runInLegacyMode());
            proto.write(CarAudioConfiguration.USE_CORE_AUDIO_VOLUME, mUseCoreAudioVolume);
            proto.write(CarAudioConfiguration.USE_CORE_AUDIO_ROUTING, mUseCoreAudioRouting);
            proto.write(CarAudioConfiguration.PATCH_API_ENABLED, areAudioPatchAPIsEnabled());
            proto.write(CarAudioConfiguration.PERSIST_MASTER_MUTE_STATE, mPersistMasterMuteState);
            proto.write(CarAudioConfiguration.USE_HAL_DUCKING_SIGNALS, mUseHalDuckingSignals);
            proto.write(CarAudioConfiguration.KEY_EVENT_TIMEOUT_MS, mKeyEventTimeoutMs);
            if (mCarAudioConfigurationPath != null) {
                proto.write(CarAudioConfiguration.CAR_AUDIO_CONFIGURATION_PATH,
                        mCarAudioConfigurationPath);
            }
            if (runInLegacyMode()) {
                proto.end(configurationToken);
                return;
            }
            proto.write(CarAudioConfiguration.USE_CAR_VOLUME_GROUP_MUTING,
                    mUseCarVolumeGroupMuting);
            proto.write(CarAudioConfiguration.USE_CAR_VOLUME_GROUP_EVENTS,
                    mUseCarVolumeGroupEvents);
            proto.write(CarAudioConfiguration.USE_FADE_MANAGER_CONFIGURATION,
                    mUseFadeManagerConfiguration);
            proto.write(CarAudioConfiguration.USE_MIN_MAX_ACTIVATION_VOLUME,
                    mUseMinMaxActivationVolume);
            proto.write(CarAudioConfiguration.USE_ISOLATED_FOCUS_FOR_DYNAMIC_DEVICES,
                    mUseIsolatedFocusForDynamicDevices);
            proto.end(configurationToken);

            mCarVolume.dumpProto(proto);
            mCarAudioContext.dumpProto(proto);

            for (int i = 0; i < mCarAudioZones.size(); i++) {
                CarAudioZone zone = mCarAudioZones.valueAt(i);
                zone.dumpProto(proto);
            }

            for (int index = 0; index < mAudioZoneIdToUserIdMapping.size(); index++) {
                long audioZoneIdToUserIdMappingToken = proto.start(CarAudioDumpProto
                        .USER_ID_TO_AUDIO_ZONE_MAPPINGS);
                int audioZoneId = mAudioZoneIdToUserIdMapping.keyAt(index);
                proto.write(UserIdToAudioZone.USER_ID,
                        mAudioZoneIdToUserIdMapping.get(audioZoneId));
                proto.write(UserIdToAudioZone.AUDIO_ZONE_ID, audioZoneId);
                proto.end(audioZoneIdToUserIdMappingToken);
            }

            for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                long audioZoneIdToOccupantZoneIdMappingToken = proto.start(
                        CarAudioDumpProto.AUDIO_ZONE_TO_OCCUPANT_ZONE_MAPPINGS);
                int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                proto.write(AudioZoneToOccupantZone.AUDIO_ZONE_ID, audioZoneId);
                proto.write(AudioZoneToOccupantZone.OCCUPANT_ZONE_ID,
                        mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId));
                proto.end(audioZoneIdToOccupantZoneIdMappingToken);
            }

            for (int callingId : mUidToZoneMap.keySet()) {
                long uidToZoneMapToken = proto.start(CarAudioDumpProto.UID_TO_AUDIO_ZONE_MAPPINGS);
                proto.write(UidToAudioZone.UID, callingId);
                proto.write(UidToAudioZone.AUDIO_ZONE_ID, mUidToZoneMap.get(callingId));
                proto.end(uidToZoneMapToken);
            }

            mFocusHandler.dumpProto(proto);

            if (mHalAudioFocus != null) {
                mHalAudioFocus.dumpProto(proto);
            }
            if (mCarDucking != null) {
                mCarDucking.dumpProto(proto);
            }
            if (mCarVolumeGroupMuting != null) {
                mCarVolumeGroupMuting.dumpProto(proto);
            }
            if (mCarAudioPlaybackCallback != null) {
                mCarAudioPlaybackCallback.dumpProto(proto);
            }

            mCarAudioMirrorRequestHandler.dumpProto(proto);
            mMediaRequestHandler.dumpProto(proto);
        }
    }

    @Override
    public boolean isAudioFeatureEnabled(@CarAudioFeature int audioFeatureType) {
        switch (audioFeatureType) {
            case AUDIO_FEATURE_DYNAMIC_ROUTING:
                return !runInLegacyMode();
            case AUDIO_FEATURE_VOLUME_GROUP_MUTING:
                return mUseCarVolumeGroupMuting;
            case AUDIO_FEATURE_OEM_AUDIO_SERVICE:
                return isAnyOemFeatureEnabled();
            case AUDIO_FEATURE_VOLUME_GROUP_EVENTS:
                return mUseCarVolumeGroupEvents;
            case AUDIO_FEATURE_AUDIO_MIRRORING:
                return mCarAudioMirrorRequestHandler.isMirrorAudioEnabled();
            case AUDIO_FEATURE_MIN_MAX_ACTIVATION_VOLUME:
                return mUseMinMaxActivationVolume;
            default:
                throw new IllegalArgumentException("Unknown Audio Feature type: "
                        + audioFeatureType);
        }
    }

    private boolean isAnyOemFeatureEnabled() {
        CarOemProxyService proxy = CarLocalServices.getService(CarOemProxyService.class);

        return proxy != null && proxy.isOemServiceEnabled()
                && (proxy.getCarOemAudioFocusService() != null
                || proxy.getCarOemAudioVolumeService() != null
                || proxy.getCarOemAudioDuckingService() != null);
    }

    /**
     * {@link android.car.media.CarAudioManager#setGroupVolume(int, int, int, int)}
     */
    @Override
    public void setGroupVolume(int zoneId, int groupId, int index, int flags) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        callbackGroupVolumeChange(zoneId, groupId, flags);
        int callbackFlags = flags;
        int eventTypes = EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
        // For legacy stream type based volume control
        boolean wasMute;
        if (runInLegacyMode()) {
            mAudioManager.setStreamVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId], index, flags);
            return;
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            wasMute = group.isMuted();
            group.setCurrentGainIndex(index);
        }
        if (wasMute) {
            handleMuteChanged(zoneId, groupId, flags);
            eventTypes |= EVENT_TYPE_MUTE_CHANGED;
        }

        if (!runInLegacyMode() && !isPlaybackOnVolumeGroupActive(zoneId, groupId)) {
            callbackFlags |= FLAG_PLAY_SOUND;
        }
        callbackVolumeGroupEvent(List.of(convertVolumeChangeToEvent(
                getVolumeGroupInfo(zoneId, groupId), callbackFlags, eventTypes)));
    }

    void handleActivationVolumeWithAudioAttributes(List<AudioAttributes> audioAttributesList,
                                                   int zoneId) {
        ArrayList<Integer> groupIdList = new ArrayList<>();
        synchronized (mImplLock) {
            for (int i = 0; i < audioAttributesList.size(); i++) {
                AudioAttributes audioAttributes = audioAttributesList.get(i);
                CarVolumeGroup volumeGroup = mCarAudioZones.get(zoneId)
                        .getVolumeGroupForAudioAttributes(audioAttributes);
                if (volumeGroup == null) {
                    Slogf.w(CarLog.TAG_AUDIO, "Audio attributes %s is not found in zone %d",
                            audioAttributes, zoneId);
                    continue;
                }
                if (!volumeGroup.handleActivationVolume()) {
                    continue;
                }
                groupIdList.add(volumeGroup.getId());
            }
        }
        handleActivationVolumeCallback(groupIdList, zoneId);
    }

    private void handleActivationVolumeCallback(List<Integer> groupIdList, int zoneId) {
        if (groupIdList.isEmpty()) {
            return;
        }
        List<CarVolumeGroupInfo> volumeGroupInfoList = new ArrayList<>(groupIdList.size());
        for (int i = 0; i < groupIdList.size(); i++) {
            int groupId = groupIdList.get(i);
            callbackGroupVolumeChange(zoneId, groupId, FLAG_SHOW_UI);
            volumeGroupInfoList.add(getVolumeGroupInfo(zoneId, groupId));
        }
        callbackVolumeGroupEvent(List.of(convertVolumeChangesToEvents(volumeGroupInfoList,
                EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED, List.of(EXTRA_INFO_ATTENUATION_ACTIVATION,
                        EXTRA_INFO_SHOW_UI))));
    }

    private void handleMuteChanged(int zoneId, int groupId, int flags) {
        if (!mUseCarVolumeGroupMuting) {
            return;
        }
        callbackGroupMuteChanged(zoneId, groupId, flags);
        mCarVolumeGroupMuting.carMuteChanged();
    }

    private void callbackGroupVolumeChange(int zoneId, int groupId, int flags) {
        int callbackFlags = flags;
        if (!runInLegacyMode() && !isPlaybackOnVolumeGroupActive(zoneId, groupId)) {
            callbackFlags |= FLAG_PLAY_SOUND;
        }
        mCarVolumeCallbackHandler.onVolumeGroupChange(zoneId, groupId, callbackFlags);
    }

    private void callbackGroupMuteChanged(int zoneId, int groupId, int flags) {
        mCarVolumeCallbackHandler.onGroupMuteChange(zoneId, groupId, flags);
    }

    void setMasterMute(boolean mute, int flags) {
        AudioManagerHelper.setMasterMute(mAudioManager, mute, flags);

        // Master Mute only applies to primary zone
        callbackMasterMuteChange(PRIMARY_AUDIO_ZONE, flags);
    }

    void callbackMasterMuteChange(int zoneId, int flags) {
        mCarVolumeCallbackHandler.onMasterMuteChanged(zoneId, flags);

        // Persists master mute state if applicable
        if (mPersistMasterMuteState) {
            mCarAudioSettings.storeMasterMute(isMasterMute(mAudioManager));
        }
    }

    void callbackVolumeGroupEvent(List<CarVolumeGroupEvent> events) {
        if (events.isEmpty()) {
            Slogf.w(TAG, "Callback not initiated for empty events list");
            return;
        }
        mCarVolumeEventHandler.onVolumeGroupEvent(events);
    }

    /**
     * {@link android.car.media.CarAudioManager#getGroupMaxVolume(int, int)}
     */
    @Override
    public int getGroupMaxVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (runInLegacyMode()) {
            return mAudioManager.getStreamMaxVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getMaxGainIndex();
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#getGroupMinVolume(int, int)}
     */
    @Override
    public int getGroupMinVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (runInLegacyMode()) {
            return mAudioManager.getStreamMinVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getMinGainIndex();
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#getGroupVolume(int, int)}
     */
    @Override
    public int getGroupVolume(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        // For legacy stream type based volume control
        if (runInLegacyMode()) {
            return mAudioManager.getStreamVolume(
                    CarAudioDynamicRouting.STREAM_TYPES[groupId]);
        }

        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getCurrentGainIndex();
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#setPrimaryZoneMediaAudioRequestCallback()}
     */
    @Override
    public boolean registerPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        return mMediaRequestHandler.registerPrimaryZoneMediaAudioRequestCallback(callback);
    }

    /**
     * {@link android.car.media.CarAudioManager#clearPrimaryZoneMediaAudioRequestCallback()}
     */
    @Override
    public void unregisterPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        List<Long> ownedRequests = mMediaRequestHandler.getRequestsOwnedByApprover(callback);
        for (int index = 0; index < ownedRequests.size(); index++) {
            long requestId = ownedRequests.get(index);
            handleUnassignAudioFromUserIdOnPrimaryAudioZone(requestId);
        }
        if (!mMediaRequestHandler.unregisterPrimaryZoneMediaAudioRequestCallback(callback)) {
            Slogf.e(TAG,
                    "unregisterPrimaryZoneMediaAudioRequestCallback could not remove callback");
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#requestMediaAudioOnPrimaryZone(
     *      MediaAudioRequest)}
     */
    @Override
    public long requestMediaAudioOnPrimaryZone(IMediaAudioRequestStatusCallback callback,
            CarOccupantZoneManager.OccupantZoneInfo info) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        Objects.requireNonNull(callback, "Media audio request callback can not be null");
        Objects.requireNonNull(info, "Occupant zone info can not be null");

        int audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(info.zoneId);
        if (audioZoneId == PRIMARY_AUDIO_ZONE) {
            throw new IllegalArgumentException("Occupant " + info
                    + " already owns the primary audio zone");
        }

        verifyMirrorNotEnabledForZone(/* runIfFailed= */ null, "request",  audioZoneId);

        synchronized (mImplLock) {
            int index = mAudioZoneIdToUserIdMapping.indexOfKey(audioZoneId);
            if (index < 0) {
                Slogf.w(TAG, "Audio zone id %d is not mapped to any user id", audioZoneId);
                return INVALID_REQUEST_ID;
            }
        }

        return mMediaRequestHandler.requestMediaAudioOnPrimaryZone(callback, info);
    }

    private void verifyMirrorNotEnabledForZone(Runnable runIfFailed, String requestType,
            int audioZoneId) {
        if (mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(audioZoneId)) {
            long mirrorId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(audioZoneId);
            CarOccupantZoneManager.OccupantZoneInfo info =
                    mOccupantZoneService.getOccupantForAudioZoneId(audioZoneId);
            if (runIfFailed != null) {
                runIfFailed.run();
            }
            throw new IllegalStateException("Can not " + requestType + " audio share to primary "
                    + "zone for occupant " + info + ", as occupant is currently mirroring audio "
                    + "in mirroring id " + mirrorId);
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#allowMediaAudioOnPrimaryZone(
     *  android.car.media.CarAudioManager.MediaRequestToken, long, boolean)}
     */
    @Override
    public boolean allowMediaAudioOnPrimaryZone(IBinder token, long requestId, boolean allow) {
        Objects.requireNonNull(token, "Media request token must not be null");
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();

        boolean canApprove = mMediaRequestHandler.isAudioMediaCallbackRegistered(token);
        if (!allow || !canApprove) {
            if (!canApprove) {
                Slogf.w(TAG, "allowMediaAudioOnPrimaryZone Request %d can not be approved by "
                                + "token %s", requestId, token);
            }
            return mMediaRequestHandler.rejectMediaAudioRequest(requestId);
        }

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        if (info == null) {
            Slogf.w(TAG, "allowMediaAudioOnPrimaryZone Request %d is no longer present",
                    requestId);
            return false;
        }

        int audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(info.zoneId);

        verifyMirrorNotEnabledForZone(() -> mMediaRequestHandler
                .rejectMediaAudioRequest(requestId), "allow",  audioZoneId);

        synchronized (mImplLock) {
            int userId = mOccupantZoneService.getUserForOccupant(info.zoneId);
            return handleAssignAudioFromUserIdToPrimaryAudioZoneLocked(token,
                    userId, audioZoneId, requestId);
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#isMediaAudioAllowedInPrimaryZone(
     *      CarOccupantZoneManager.OccupantZoneInfo)}
     */
    @Override
    public boolean isMediaAudioAllowedInPrimaryZone(CarOccupantZoneManager.OccupantZoneInfo info) {
        Objects.requireNonNull(info, "Occupant zone info can not be null");
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();

        return mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info);
    }

    /**
     * {@link android.car.media.CarAudioManager#resetMediaAudioOnPrimaryZone(
     *      CarOccupantZoneManager.OccupantZoneInfo)}
     */
    @Override
    public boolean resetMediaAudioOnPrimaryZone(CarOccupantZoneManager.OccupantZoneInfo info) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();

        long requestId = mMediaRequestHandler.getRequestIdForOccupant(info);
        if (requestId == INVALID_REQUEST_ID) {
            Slogf.w(TAG, "resetMediaAudioOnPrimaryZone no request id for occupant %s", info);
            return false;
        }
        return handleUnassignAudioFromUserIdOnPrimaryAudioZone(requestId);
    }

    /**
     * {@link android.car.media.CarAudioManager#cancelMediaAudioOnPrimaryZone(long)}
     */
    @Override
    public boolean cancelMediaAudioOnPrimaryZone(long requestId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();

        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);
        if (info == null) {
            Slogf.w(TAG, "cancelMediaAudioOnPrimaryZone no occupant for request %d",
                    requestId);
            return false;
        }

        if (!mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
            return mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);
        }

        return handleUnassignAudioFromUserIdOnPrimaryAudioZone(requestId);
    }

    /**
     * {@link CarAudioManager#setAudioZoneMirrorStatusCallback(Executor,
     *      AudioZonesMirrorStatusCallback)}
     */
    @Override
    public boolean registerAudioZonesMirrorStatusCallback(
            IAudioZonesMirrorStatusCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();

        return mCarAudioMirrorRequestHandler.registerAudioZonesMirrorStatusCallback(callback);
    }

    /**
     * {@link CarAudioManager#clearAudioZonesMirrorStatusCallback()}
     */
    @Override
    public void unregisterAudioZonesMirrorStatusCallback(IAudioZonesMirrorStatusCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();

        if (!mCarAudioMirrorRequestHandler.unregisterAudioZonesMirrorStatusCallback(callback)) {
            Slogf.w(TAG, "Could not unregister audio zones mirror status callback ,"
                    + "callback could have died before unregister was called.");
        }
    }

    /**
     * {@link CarAudioManager#canEnableAudioMirror()}
     */
    @Override
    public int canEnableAudioMirror()  {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();

        return mCarAudioMirrorRequestHandler.canEnableAudioMirror();
    }

    /**
     * {@link CarAudioManager#enableMirrorForAudioZones(List)}
     */
    @Override
    public long enableMirrorForAudioZones(int[] audioZones) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        verifyCanMirrorToAudioZones(audioZones, /* forExtension= */ false);

        long requestId = mCarAudioMirrorRequestHandler.getUniqueRequestIdAndAssignMirrorDevice();

        if (requestId == INVALID_REQUEST_ID) {
            Slogf.e(TAG, "enableMirrorForAudioZones failed,"
                    + " audio mirror not allowed, no more audio mirroring devices available");
            throw new IllegalStateException("Out of available mirror output devices");
        }

        mHandler.post(() -> handleEnableAudioMirrorForZones(audioZones, requestId));

        return requestId;
    }

    /**
     * {@link CarAudioManager#extendAudioMirrorRequest(long, List)}
     */
    @Override
    public void extendAudioMirrorRequest(long mirrorId, int[] audioZones) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        verifyCanMirrorToAudioZones(audioZones, /* forExtension= */ true);
        mCarAudioMirrorRequestHandler.verifyValidRequestId(mirrorId);

        mHandler.post(() -> handleEnableAudioMirrorForZones(audioZones, mirrorId));
    }

    /**
     * {@link CarAudioManager#disableAudioMirrorForZone(int)}
     */
    @Override
    public void disableAudioMirrorForZone(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        checkAudioZoneId(zoneId);
        long requestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(zoneId);
        if (requestId == INVALID_REQUEST_ID) {
            Slogf.w(TAG, "Could not disable audio mirror for zone %d, zone was not mirroring",
                    zoneId);
            return;
        }

        mHandler.post(() -> handleDisableAudioMirrorForZonesInConfig(new int[]{zoneId}, requestId));
    }

    /**
     * {@link CarAudioManager#disableAudioMirror(long)}}
     */
    @Override
    public void disableAudioMirror(long mirrorId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        Preconditions.checkArgument(mirrorId != INVALID_REQUEST_ID,
                "Mirror id can not be INVALID_REQUEST_ID");

        int[] config = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(mirrorId);
        if (config == null) {
            Slogf.w(TAG, "disableAudioMirror mirror id %d no longer exist",
                    mirrorId);
            return;
        }

        mHandler.post(() -> handleDisableAudioMirrorForZonesInConfig(config, mirrorId));
    }

    /**
     * {@link CarAudioManager#getMirrorAudioZonesForAudioZone(int)}
     */
    @Override
    public int[] getMirrorAudioZonesForAudioZone(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        long requestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(zoneId);

        if (requestId == INVALID_REQUEST_ID) {
            return EMPTY_INT_ARRAY;
        }
        int[] config = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(requestId);
        return config == null ? new int[0] : config;
    }

    /**
     * {@link CarAudioManager#getMirrorAudioZonesForMirrorRequest(long)}
     */
    @Override
    public int[] getMirrorAudioZonesForMirrorRequest(long mirrorId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        requireAudioMirroring();
        Preconditions.checkArgument(mirrorId != INVALID_REQUEST_ID,
                "Mirror request id can not be INVALID_REQUEST_ID");

        int[] config = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(mirrorId);
        return config == null ? new int[0] : config;
    }

    @GuardedBy("mImplLock")
    private CarVolumeGroup getCarVolumeGroupLocked(int zoneId, int groupId) {
        return getCarAudioZoneLocked(zoneId).getCurrentVolumeGroup(groupId);
    }

    @GuardedBy("mImplLock")
    @Nullable
    private CarVolumeGroup getCarVolumeGroupLocked(int zoneId, String groupName) {
        return getCarAudioZoneLocked(zoneId).getCurrentVolumeGroup(groupName);
    }

    private void verifyCanMirrorToAudioZones(int[] audioZones, boolean forExtension) {
        Objects.requireNonNull(audioZones, "Mirror audio zones can not be null");
        int minSize = 2;
        if (forExtension) {
            minSize = 1;
        }
        Preconditions.checkArgument(audioZones.length >= minSize,
                "Mirror audio zones needs to have at least " + minSize + " zones");
        ArraySet<Integer> zones = CarServiceUtils.toIntArraySet(audioZones);

        if (zones.size() != audioZones.length) {
            throw new IllegalArgumentException(
                    "Audio zones in mirror configuration must be unique "
                            + Arrays.toString(audioZones));
        }

        if (zones.contains(PRIMARY_AUDIO_ZONE)) {
            throw new IllegalArgumentException(
                    "Audio mirroring not allowed for primary audio zone");
        }

        for (int c = 0; c < audioZones.length; c++) {
            int zoneId = audioZones[c];

            checkAudioZoneId(zoneId);

            int userId = getUserIdForZone(zoneId);
            if (userId == UserManagerHelper.USER_NULL) {
                throw new IllegalStateException(
                        "Audio zone must have an active user to allow mirroring");
            }

            CarOccupantZoneManager.OccupantZoneInfo info = mOccupantZoneService
                    .getOccupantForAudioZoneId(zoneId);

            if (mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
                throw new IllegalStateException(
                        "Occupant " + info + " in audio zone " + zoneId
                                + " is currently sharing to primary zone, "
                                + "undo audio sharing in primary zone before setting up mirroring");
            }

            long zoneRequestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(zoneId);

            if (zoneRequestId == INVALID_REQUEST_ID) {
                continue;
            }

            throw new IllegalStateException(
                    "Audio zone " + zoneId + " is already mirroring");
        }
    }

    private void handleEnableAudioMirrorForZones(int[] audioZoneIds, long requestId) {
        AudioDeviceAttributes mirrorDevice =
                mCarAudioMirrorRequestHandler.getAudioDevice(requestId);
        if (mirrorDevice == null) {
            Slogf.e(TAG, "handleEnableAudioMirrorForZones failed,"
                    + " audio mirror not allowed as there are no more mirror devices available");
            mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIds);
            return;
        }
        int[] config = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(requestId);
        // Check it is same configuration as requested, order is preserved as it is assumed
        // that the first zone id is the source and other zones are the receiver of the audio
        // mirror
        if (Arrays.equals(audioZoneIds, config)) {
            Slogf.i(TAG, "handleEnableAudioMirrorForZones audio mirror already set for zones %s",
                    Arrays.toString(audioZoneIds));
            mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, audioZoneIds);
            return;
        }

        ArrayList<Integer> zones = new ArrayList<>();
        if (config != null) {
            zones.addAll(CarServiceUtils.asList(config));
        }

        for (int index = 0; index < audioZoneIds.length; index++) {
            int audioZoneId = audioZoneIds[index];

            int userId = getUserIdForZone(audioZoneId);
            if (userId == UserManagerHelper.USER_NULL) {
                Slogf.w(TAG, "handleEnableAudioMirrorForZones failed,"
                        + " audio mirror not allowed for unassigned audio zone %d", audioZoneId);
                mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIds);
                return;
            }

            long zoneRequestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(
                    audioZoneId);

            if (zoneRequestId != INVALID_REQUEST_ID && zoneRequestId != requestId) {
                Slogf.w(TAG, "handleEnableAudioMirrorForZones failed,"
                        + " audio mirror not allowed for already mirroring audio zone %d",
                        audioZoneId);
                mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIds);
                return;
            }

            CarOccupantZoneManager.OccupantZoneInfo info = mOccupantZoneService
                    .getOccupantForAudioZoneId(audioZoneId);

            if (mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
                Slogf.w(TAG, "handleEnableAudioMirrorForZones failed,"
                        + " audio mirror not allowed for audio zone %d sharing to primary zone",
                        audioZoneId);
                mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIds);
                return;
            }
            zones.add(audioZoneId);
        }

        int[] audioZoneIdsToAdd = CarServiceUtils.toIntArray(zones);

        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        t.traceBegin("audio-mirror-" + Arrays.toString(audioZoneIdsToAdd));
        synchronized (mImplLock) {
            List<AudioFocusStackRequest> mediaFocusStacks = new ArrayList<>();
            t.traceBegin("audio-mirror-focus-loss-" + Arrays.toString(audioZoneIdsToAdd));
            transientlyLoseFocusForMirrorLocked(audioZoneIdsToAdd, t, mediaFocusStacks);
            t.traceEnd();

            t.traceBegin("audio-mirror-routing-" + Arrays.toString(audioZoneIdsToAdd));
            if (!setupAudioRoutingForUserInMirrorDeviceLocked(audioZoneIdsToAdd, mirrorDevice)) {
                for (int index = 0; index < mediaFocusStacks.size(); index++) {
                    AudioFocusStackRequest request = mediaFocusStacks.get(index);
                    mFocusHandler.regainMediaAudioFocusInZone(request.mStack,
                            request.mOriginalZoneId);
                }
                mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIdsToAdd);
                return;
            }
            t.traceEnd();

            // TODO(b/268383539): Implement multi zone focus for mirror
            // Currently only selecting the source zone as focus manager
            t.traceBegin("audio-mirror-focus-gain-" + Arrays.toString(audioZoneIdsToAdd));
            int zoneId = audioZoneIdsToAdd[0];
            for (int index = 0; index < mediaFocusStacks.size(); index++) {
                AudioFocusStackRequest request = mediaFocusStacks.get(index);
                t.traceBegin("audio-mirror-focus-gain-" + index + "-zone-" + zoneId);
                mFocusHandler.regainMediaAudioFocusInZone(request.mStack, zoneId);
                t.traceEnd();
            }
            t.traceEnd();
        }
        t.traceEnd();
        sendMirrorInfoToAudioHal(mirrorDevice.getAddress(), audioZoneIdsToAdd);
        mCarAudioMirrorRequestHandler.enableMirrorForZones(requestId, audioZoneIdsToAdd);
    }

    private void sendMirrorInfoToAudioHal(String mirrorSource, int[] audioZoneIds) {
        StringBuilder builder = new StringBuilder();
        builder.append(MIRROR_COMMAND_SOURCE);
        builder.append(mirrorSource);
        builder.append(MIRROR_COMMAND_SEPARATOR);

        builder.append(MIRROR_COMMAND_DESTINATION);
        for (int index = 0; index < audioZoneIds.length; index++) {
            int zoneId = audioZoneIds[index];
            String zoneMediaAddress = getOutputDeviceAddressForUsageInternal(zoneId, USAGE_MEDIA);
            builder.append(zoneMediaAddress);
            builder.append(index < audioZoneIds.length - 1
                    ? MIRROR_COMMAND_DESTINATION_SEPARATOR : "");
        }
        builder.append(MIRROR_COMMAND_SEPARATOR);

        Slogf.i(TAG, "Sending mirror command to audio HAL: %s", builder);
        mAudioManager.setParameters(builder.toString());
    }

    private String getAudioMirroringOffCommand(String mirrorSource) {
        return new StringBuilder().append(MIRROR_COMMAND_SOURCE).append(mirrorSource)
                .append(MIRROR_COMMAND_SEPARATOR).append(DISABLE_AUDIO_MIRRORING)
                .append(MIRROR_COMMAND_SEPARATOR).toString();
    }

    private String getOutputDeviceAddressForUsageInternal(int zoneId, int usage) {
        int contextForUsage = getCarAudioContext()
                .getContextForAudioAttribute(CarAudioContext.getAudioAttributeFromUsage(usage));
        return getCarAudioZone(zoneId).getAddressForContext(contextForUsage);
    }

    @GuardedBy("mImplLock")
    private void transientlyLoseFocusForMirrorLocked(int[] audioZoneIdsToAdd,
            TimingsTraceLog traceLog, List<AudioFocusStackRequest> mediaFocusStacks) {
        for (int index = 0; index < audioZoneIdsToAdd.length; index++) {
            int zoneId = audioZoneIdsToAdd[index];
            traceLog.traceBegin("audio-mirror-focus-loss-zone-" + zoneId);
            mediaFocusStacks.add(new AudioFocusStackRequest(mFocusHandler
                    .transientlyLoseAudioFocusForZone(zoneId), zoneId));
            traceLog.traceEnd();
        }
    }

    private void handleDisableAudioMirrorForZonesInConfig(int[] audioZoneIds, long requestId) {
        AudioDeviceAttributes mirrorDevice =
                mCarAudioMirrorRequestHandler.getAudioDevice(requestId);
        if (mirrorDevice == null) {
            Slogf.e(TAG, "handleDisableAudioMirrorForZonesInConfig failed,"
                    + " audio mirror not allowed as there are no more mirror devices available");
            mCarAudioMirrorRequestHandler.rejectMirrorForZones(requestId, audioZoneIds);
            return;
        }

        int[] oldConfigs = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(requestId);
        if (oldConfigs == null) {
            Slogf.w(TAG, "Could not disable audio mirror for zones %s,"
                            + " %d request id was no longer mirroring",
                    Arrays.toString(audioZoneIds), requestId);
            return;
        }
        for (int index = 0; index < audioZoneIds.length; index++) {
            int zoneId = audioZoneIds[index];

            if (!mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zoneId)) {
                Slogf.w(TAG, "Could not disable audio mirror for zone %d,"
                                + " zone was no longer mirroring",
                        zoneId);
                return;
            }

            long currentRequestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(zoneId);

            // The configuration to remove must be the same for the zones
            if (currentRequestId != requestId) {
                Slogf.w(TAG, "Could not disable audio mirror for zone %d,"
                                + " found non matching configuration",
                        zoneId);
                return;
            }
        }

        int[] newConfig = mCarAudioMirrorRequestHandler
                .calculateAudioConfigurationAfterRemovingZonesFromRequestId(requestId, audioZoneIds
                );

        if (newConfig == null) {
            Slogf.w(TAG, " handleDisableAudioMirrorForZone could not disable audio "
                    + "mirror for zones %s, configuration not found",
                    Arrays.toString(audioZoneIds));
            return;
        }

        // If there are less than two zones mirroring, remove all the zones
        if (newConfig.length < 2) {
            newConfig = EMPTY_INT_ARRAY;
        }

        modifyAudioMirrorForZones(oldConfigs, newConfig);

        // If there are no more zones mirroring then turn it off at HAL
        if (newConfig.length == 0) {
            Slogf.i(TAG, "Sending mirror off command to audio HAL for address %s",
                    mirrorDevice.getAddress());
            mAudioManager.setParameters(getAudioMirroringOffCommand(mirrorDevice.getAddress()));
        }

        //Send the signal to current listeners at the end
        mCarAudioMirrorRequestHandler.updateRemoveMirrorConfigurationForZones(requestId, newConfig);
    }

    private void modifyAudioMirrorForZones(int[] audioZoneIds, int[] newConfig) {
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        ArraySet<Integer> newConfigSet = CarServiceUtils.toIntArraySet(newConfig);
        int focusZoneId = audioZoneIds[0];
        List<AudioFocusStackRequest> mediaFocusStacks = new ArrayList<>();
        ArrayList<Integer> zonesToUndoRouting = new ArrayList<>(audioZoneIds.length
                - newConfig.length);
        t.traceBegin("audio-remove-mirror-" + Arrays.toString(audioZoneIds));
        synchronized (mImplLock) {
            t.traceBegin("audio-remove-mirror-focus-loss-" + Arrays.toString(audioZoneIds));
            for (int index = 0; index < audioZoneIds.length; index++) {
                int zoneId = audioZoneIds[index];
                int newFocusZoneId = newConfig.length > 0 ? newConfig[0] : zoneId;
                // Focus for zones not in the new config remove focus and routing
                if (!newConfigSet.contains(zoneId)) {
                    newFocusZoneId = zoneId;
                    zonesToUndoRouting.add(zoneId);
                }
                t.traceBegin("audio-remove-mirror-focus-loss-zone-" + zoneId);
                mediaFocusStacks.add(new AudioFocusStackRequest(mFocusHandler
                        .transientlyLoseAudioFocusForZone(focusZoneId),
                        newFocusZoneId));
                t.traceEnd();
            }
            t.traceEnd();

            t.traceBegin("audio-remove-mirror-routing-" + zonesToUndoRouting);
            setupAudioRoutingForUsersZoneLocked(zonesToUndoRouting);
            t.traceEnd();

            t.traceBegin("audio-remove-mirror-focus-gain-" + Arrays.toString(audioZoneIds));
            for (int index = 0; index < mediaFocusStacks.size(); index++) {
                AudioFocusStackRequest request = mediaFocusStacks.get(index);
                t.traceBegin("audio-remove-mirror-focus-gain-" + index + "-zone-"
                        + request.mOriginalZoneId);
                mFocusHandler.regainMediaAudioFocusInZone(request.mStack, request.mOriginalZoneId);
                t.traceEnd();
            }
            t.traceEnd();
        }
        t.traceEnd();
    }

    @GuardedBy("mImplLock")
    private void setupAudioRoutingForUsersZoneLocked(ArrayList<Integer> audioZoneIds) {
        for (int index = 0; index < audioZoneIds.size(); index++) {
            int zoneId = audioZoneIds.get(index);
            int userId = getUserIdForZone(zoneId);
            if (userId == UserManagerHelper.USER_NULL) {
                continue;
            }
            CarAudioZone audioZone = getCarAudioZone(zoneId);
            setUserIdDeviceAffinitiesLocked(audioZone, userId, zoneId);
        }
    }

    @GuardedBy("mImplLock")
    private boolean setupAudioRoutingForUserInMirrorDeviceLocked(int[] audioZones,
            AudioDeviceAttributes mirrorDevice) {
        int index;
        boolean succeeded = true;
        for (index = 0; index < audioZones.length; index++) {
            int zoneId = audioZones[index];
            int userId = getUserIdForZone(zoneId);
            CarAudioZone audioZone = getCarAudioZone(zoneId);
            boolean enabled = setupMirrorDeviceForUserIdLocked(userId, audioZone, mirrorDevice);
            if (!enabled) {
                succeeded = false;
                Slogf.w(TAG, "setupAudioRoutingForUserInMirrorDeviceLocked failed for zone "
                        + "id %d and user id %d", zoneId, userId);
                break;
            }
        }

        if (succeeded) {
            return true;
        }

        // Attempt to reset user id routing for other mirror zones
        for (int count = 0; count < index; count++) {
            int zoneId = audioZones[count];
            int userId = getUserIdForZone(zoneId);
            CarAudioZone audioZone = getCarAudioZone(zoneId);
            setUserIdDeviceAffinitiesLocked(audioZone, userId, zoneId);
        }

        return false;
    }

    private void setupLegacyVolumeChangedListener() {
        AudioManagerHelper.registerVolumeAndMuteReceiver(mContext, mLegacyVolumeChangedHelper);
    }

    private List<CarAudioDeviceInfo> generateCarAudioDeviceInfos() {
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(
                AudioManager.GET_DEVICES_OUTPUTS);

        List<CarAudioDeviceInfo> carInfos = new ArrayList<>();

        for (int index = 0; index < deviceInfos.length; index++) {
            if (!isValidDeviceType(deviceInfos[index].getType())) {
                continue;
            }

            AudioDeviceInfo info = deviceInfos[index];
            AudioDeviceAttributes attributes = new AudioDeviceAttributes(info);
            CarAudioDeviceInfo carInfo = new CarAudioDeviceInfo(mAudioManager, attributes);
            // TODO(b/305301155): Move set audio device info closer to where it is used.
            //  On dynamic configuration change for example
            carInfo.setAudioDeviceInfo(info);

            carInfos.add(carInfo);
        }
        return carInfos;
    }

    private AudioDeviceInfo[] getAllInputDevices() {
        return mAudioManager.getDevices(
                AudioManager.GET_DEVICES_INPUTS);
    }

    @GuardedBy("mImplLock")
    private SparseArray<CarAudioZone> loadCarAudioConfigurationLocked(
            List<CarAudioDeviceInfo> carAudioDeviceInfos, AudioDeviceInfo[] inputDevices) {

        try (InputStream fileStream = new FileInputStream(mCarAudioConfigurationPath);
                 InputStream inputStream = new BufferedInputStream(fileStream)) {
            CarAudioZonesHelper zonesHelper = new CarAudioZonesHelper(mAudioManager,
                    mCarAudioSettings, inputStream, carAudioDeviceInfos, inputDevices,
                    mServiceEventLogger, mUseCarVolumeGroupMuting, mUseCoreAudioVolume,
                    mUseCoreAudioRouting, mUseFadeManagerConfiguration,
                    mCarAudioFadeConfigurationHelper);
            mAudioZoneIdToOccupantZoneIdMapping =
                    zonesHelper.getCarAudioZoneIdToOccupantZoneIdMapping();
            SparseArray<CarAudioZone> zones = zonesHelper.loadAudioZones();
            mCarAudioMirrorRequestHandler.setMirrorDeviceInfos(zonesHelper.getMirrorDeviceInfos());
            mCarAudioContext = zonesHelper.getCarAudioContext();
            return zones;
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Failed to parse audio zone configuration", e);
        }
    }

    @GuardedBy("mImplLock")
    private CarAudioFadeConfigurationHelper loadCarAudioFadeConfigurationLocked() {
        if (mCarAudioFadeConfigurationPath == null) {
            String message = "Car audio fade configuration xml file expected, but not found at: "
                    + FADE_CONFIGURATION_PATH;
            Slogf.w(TAG, message);
            mServiceEventLogger.log(message);
            return null;
        }
        try (InputStream fileStream = new FileInputStream(mCarAudioFadeConfigurationPath);
                 InputStream inputStream = new BufferedInputStream(fileStream)) {
            return new CarAudioFadeConfigurationHelper(inputStream);
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Failed to parse audio fade configuration", e);
        }
    }

    @GuardedBy("mImplLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEPRECATED_CODE)
    private SparseArray<CarAudioZone> loadVolumeGroupConfigurationWithAudioControlLocked(
            List<CarAudioDeviceInfo> carAudioDeviceInfos, AudioDeviceInfo[] inputDevices) {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!(audioControlWrapper instanceof AudioControlWrapperV1)) {
            throw new IllegalStateException(
                    "Updated version of IAudioControl no longer supports CarAudioZonesHelperLegacy."
                    + " Please provide car_audio_configuration.xml.");
        }
        mCarAudioContext = new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                mUseCoreAudioVolume);
        CarAudioZonesHelperLegacy legacyHelper = new CarAudioZonesHelperLegacy(mContext,
                mCarAudioContext, R.xml.car_volume_groups, carAudioDeviceInfos,
                (AudioControlWrapperV1) audioControlWrapper,
                mCarAudioSettings, inputDevices);
        return legacyHelper.loadAudioZones();
    }

    // Required to be called before setting up audio routing, volume management, focus management
    @GuardedBy("mImplLock")
    private void loadAndInitCarAudioZonesLocked() {
        if (mUseFadeManagerConfiguration) {
            mCarAudioFadeConfigurationHelper = loadCarAudioFadeConfigurationLocked();
        }

        List<CarAudioDeviceInfo> carAudioDeviceInfos = generateCarAudioDeviceInfos();
        AudioDeviceInfo[] inputDevices = getAllInputDevices();

        if (mCarAudioConfigurationPath != null) {
            mCarAudioZones = loadCarAudioConfigurationLocked(carAudioDeviceInfos, inputDevices);
        } else {
            mCarAudioZones =
                    loadVolumeGroupConfigurationWithAudioControlLocked(carAudioDeviceInfos,
                            inputDevices);
        }

        CarAudioZonesValidator.validate(mCarAudioZones, mUseCoreAudioRouting);

        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            // Ensure HAL gets our initial value
            zone.init();
            Slogf.v(TAG, "Processed audio zone: %s", zone);
        }
    }

    @GuardedBy("mImplLock")
    private void setupCarAudioPlaybackMonitor() {
        if (mUseMinMaxActivationVolume) {
            mCarAudioPlaybackMonitor = new CarAudioPlaybackMonitor(this);
        }
    }

    @GuardedBy("mImplLock")
    private void setupControlAndRoutingAudioPoliciesLocked() {
        setupVolumeControlAudioPolicyLocked();
        setupFocusControlAudioPolicyLocked();
        mRoutingAudioPolicy = setupRoutingAudioPolicyLocked();
        setupOccupantZoneInfoLocked();
        setupCoreAudioVolumeCallback();
    }

    @GuardedBy("mImplLock")
    private void setupAudioControlDuckingAndVolumeControlLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (mUseHalDuckingSignals) {
            if (audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_DUCKING)) {
                mCarDucking = new CarDucking(mCarAudioZones, audioControlWrapper);
            }
        }

        if (mUseCarVolumeGroupMuting) {
            mCarVolumeGroupMuting = new CarVolumeGroupMuting(mCarAudioZones, audioControlWrapper);
        }
    }

    @GuardedBy("mImplLock")
    private void setupCoreAudioVolumeCallback() {
        if (!mUseCoreAudioVolume) {
            Slogf.i(TAG, "Not using core volume, core volume callback not setup");
            return;
        }
        mCoreAudioVolumeGroupCallback = new CoreAudioVolumeGroupCallback(
                new CarVolumeInfoWrapper(this), mAudioManager);
        mCoreAudioVolumeGroupCallback.init(mContext.getMainExecutor());
    }

    @GuardedBy("mImplLock")
    private AudioPolicy setupRoutingAudioPolicyLocked() {
        if (!mUseDynamicRouting) {
            Slogf.i(TAG, "Not using dynamic audio routing, routing audio policy not setup");
            return null;
        }
        TimingsTraceLog log = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        log.traceBegin("routing-policy");
        AudioPolicy.Builder builder = new AudioPolicy.Builder(mContext);
        builder.setLooper(Looper.getMainLooper());

        // Mirror policy has to be set before general audio policy
        log.traceBegin("routing-policy-setup");
        setupMirrorDevicePolicyLocked(builder);
        CarAudioDynamicRouting.setupAudioDynamicRouting(mCarAudioContext, mAudioManager, builder,
                mCarAudioZones);
        log.traceEnd();

        AudioPolicy routingAudioPolicy = builder.build();
        log.traceBegin("routing-policy-register");
        int r = mAudioManager.registerAudioPolicy(routingAudioPolicy);
        log.traceEnd();

        log.traceEnd();
        if (r != AudioManager.SUCCESS) {
            throw new IllegalStateException("Audio routing policy registration, error: " + r);
        }
        return routingAudioPolicy;
    }

    @GuardedBy("mImplLock")
    private void setupVolumeControlAudioPolicyLocked() {
        mCarVolume = new CarVolume(mCarAudioContext, mClock,
                mAudioVolumeAdjustmentContextsVersion, mKeyEventTimeoutMs);

        AudioPolicy.Builder volumeControlPolicyBuilder = new AudioPolicy.Builder(mContext);
        volumeControlPolicyBuilder.setLooper(Looper.getMainLooper());

        AudioPolicyVolumeCallbackInternal volumeCallbackInternal =
                new AudioPolicyVolumeCallbackInternal() {
                    @Override
                    public void onMuteChange(boolean mute, int zoneId, int groupId, int flags) {
                        if (mUseCarVolumeGroupMuting) {
                            setVolumeGroupMute(zoneId, groupId, mute, flags);
                            return;
                        }
                        setMasterMute(mute, flags);
                    }

                    @Override
                    public void onGroupVolumeChange(int zoneId, int groupId, int volumeValue,
                                                    int flags) {
                        setGroupVolume(zoneId, groupId, volumeValue, flags);
                    }
                };

        mCarAudioPolicyVolumeCallback = new CarAudioPolicyVolumeCallback(volumeCallbackInternal,
                mAudioManager, new CarVolumeInfoWrapper(this), mUseCarVolumeGroupMuting);
        // Attach the {@link AudioPolicyVolumeCallback}
        CarAudioPolicyVolumeCallback.addVolumeCallbackToPolicy(volumeControlPolicyBuilder,
                mCarAudioPolicyVolumeCallback);

        mVolumeControlAudioPolicy = volumeControlPolicyBuilder.build();

        int status = mAudioManager.registerAudioPolicy(mVolumeControlAudioPolicy);
        if (status != AudioManager.SUCCESS) {
            throw new IllegalStateException("Could not register the car audio service's volume"
                    + " control audio policy, error: " + status);
        }
    }

    @GuardedBy("mImplLock")
    private void setupFocusControlAudioPolicyLocked() {
        // Used to configure our audio policy to handle focus events.
        // This gives us the ability to decide which audio focus requests to accept and bypasses
        // the framework ducking logic.
        mFocusHandler = CarZonesAudioFocus.createCarZonesAudioFocus(mAudioManager,
                mContext.getPackageManager(), mCarAudioZones, mCarAudioSettings, mCarDucking,
                new CarVolumeInfoWrapper(this), getAudioFeaturesInfo());

        AudioPolicy.Builder focusControlPolicyBuilder = new AudioPolicy.Builder(mContext);
        focusControlPolicyBuilder.setLooper(Looper.getMainLooper());

        focusControlPolicyBuilder.setAudioPolicyFocusListener(mFocusHandler);
        focusControlPolicyBuilder.setIsAudioFocusPolicy(true);

        mFocusControlAudioPolicy = focusControlPolicyBuilder.build();
        mFocusHandler.setOwningPolicy(this, mFocusControlAudioPolicy);

        int status = mAudioManager.registerAudioPolicy(mFocusControlAudioPolicy);
        if (status != AudioManager.SUCCESS) {
            throw new IllegalStateException("Could not register the car audio service's focus"
                    + " control audio policy, error: " + status);
        }
    }

    private CarAudioFeaturesInfo getAudioFeaturesInfo() {
        if (!Flags.carAudioDynamicDevices()) {
            return null;
        }
        CarAudioFeaturesInfo.Builder builder =
                new CarAudioFeaturesInfo.Builder(CarAudioFeaturesInfo.AUDIO_FEATURE_NO_FEATURE);
        if (mUseIsolatedFocusForDynamicDevices) {
            builder.addAudioFeature(CarAudioFeaturesInfo.AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS);
        }
        if (mUseFadeManagerConfiguration) {
            builder.addAudioFeature(CarAudioFeaturesInfo.AUDIO_FEATURE_FADE_MANAGER_CONFIGS);
        }

        return builder.build();
    }

    @GuardedBy("mImplLock")
    private void setupFadeManagerConfigAudioPolicyLocked() {
        if (!mUseFadeManagerConfiguration) {
            return;
        }

        mFadeManagerConfigAudioPolicy = new AudioPolicy.Builder(mContext).build();
        int status = mAudioManager.registerAudioPolicy(mFadeManagerConfigAudioPolicy);
        if (status != AudioManager.SUCCESS) {
            throw new IllegalStateException("Could not register the car audio service's fade"
                    + " configuration audio policy, error: " + status);
        }
        updateFadeManagerConfigurationForPrimaryZoneLocked();
    }

    @GuardedBy("mImplLock")
    private void updateFadeManagerConfigurationForPrimaryZoneLocked() {
        CarAudioFadeConfiguration carAudioFadeConfiguration = mCarAudioZones.get(PRIMARY_AUDIO_ZONE)
                .getCurrentCarAudioZoneConfig().getDefaultCarAudioFadeConfiguration();
        if (carAudioFadeConfiguration == null) {
            return;
        }
        // for primary zone, core framework handles the default fade config
        setAudioPolicyFadeManagerConfigurationLocked(
                carAudioFadeConfiguration.getFadeManagerConfiguration());
    }

    @GuardedBy("mImplLock")
    private void updateFadeManagerConfigurationLocked(boolean isPrimaryZone) {
        if (!mUseFadeManagerConfiguration || !isPrimaryZone) {
            return;
        }
        updateFadeManagerConfigurationForPrimaryZoneLocked();
    }

    @GuardedBy("mImplLock")
    private void setAudioPolicyFadeManagerConfigurationLocked(
            FadeManagerConfiguration fadeManagerConfiguration) {
        if (!mUseFadeManagerConfiguration || fadeManagerConfiguration == null
                || mFadeManagerConfigAudioPolicy == null) {
            String message = "Can not set fade manager configuration: feature flag enabled? "
                    + mUseFadeManagerConfiguration
                    + " audio policy for fade configs registered? "
                    + (mFadeManagerConfigAudioPolicy != null)
                    + " fade manager configuration: " + fadeManagerConfiguration;
            mServiceEventLogger.log(message);
            Slogf.e(TAG, message);
            return;
        }

        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        t.traceBegin("set-fade-manager-configuration-for-focus-loss");
        int status = mFadeManagerConfigAudioPolicy.setFadeManagerConfigurationForFocusLoss(
                fadeManagerConfiguration);
        t.traceEnd();
        if (status != AudioManager.SUCCESS) {
            String message = "Failed setting audio policy fade manager configuration: "
                    + fadeManagerConfiguration + " with error: " + status;
            mServiceEventLogger.log(message);
            Slogf.e(TAG, message);
        }
    }

    @GuardedBy("mImplLock")
    private void setupMirrorDevicePolicyLocked(AudioPolicy.Builder mirrorPolicyBuilder) {
        if (!mCarAudioMirrorRequestHandler.isMirrorAudioEnabled()) {
            Slogf.w(TAG, "setupMirrorDevicePolicyLocked Audio mirroring is not enabled");
            return;
        }

        CarAudioDynamicRouting.setupAudioDynamicRoutingForMirrorDevice(mirrorPolicyBuilder,
                mCarAudioMirrorRequestHandler.getMirroringDeviceInfos(), mAudioManager);
    }

    @GuardedBy("mImplLock")
    private void setupAudioConfigurationCallbackLocked() {
        mCarAudioPlaybackCallback = new CarAudioPlaybackCallback(mCarAudioZones,
                mCarAudioPlaybackMonitor, mClock, mKeyEventTimeoutMs);
        mAudioManager.registerAudioPlaybackCallback(mCarAudioPlaybackCallback, null);
    }

    @GuardedBy("mImplLock")
    private void setupOccupantZoneInfoLocked() {
        CarOccupantZoneService occupantZoneService;
        SparseIntArray audioZoneIdToOccupantZoneMapping;
        audioZoneIdToOccupantZoneMapping = mAudioZoneIdToOccupantZoneIdMapping;
        occupantZoneService = mOccupantZoneService;
        occupantZoneService.setAudioZoneIdsForOccupantZoneIds(audioZoneIdToOccupantZoneMapping);
        occupantZoneService.registerCallback(mOccupantZoneCallback);
    }

    @GuardedBy("mImplLock")
    private void setupHalAudioFocusListenerLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS)) {
            Slogf.d(TAG, "HalAudioFocus is not supported on this device");
            return;
        }

        mHalAudioFocus = new HalAudioFocus(mAudioManager, mAudioControlWrapper,
                mCarAudioPlaybackMonitor, mCarAudioContext, getAudioZoneIds());
        mHalAudioFocus.registerFocusListener();
    }

    @GuardedBy("mImplLock")
    private void setupHalAudioGainCallbackLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK)) {
            Slogf.d(CarLog.TAG_AUDIO, "HalAudioGainCallback is not supported on this device");
            return;
        }
        mCarAudioGainMonitor = new CarAudioGainMonitor(mAudioControlWrapper,
                new CarVolumeInfoWrapper(this), mCarAudioZones);
        mCarAudioGainMonitor.registerAudioGainListener(mHalAudioGainCallback);
    }

    @GuardedBy("mImplLock")
    private void setupHalAudioModuleChangeCallbackLocked() {
        AudioControlWrapper audioControlWrapper = getAudioControlWrapperLocked();
        if (!audioControlWrapper.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK)) {
            Slogf.w(CarLog.TAG_AUDIO, "HalModuleChangeCallback is not supported on this device");
            return;
        }
        mCarAudioModuleChangeMonitor = new CarAudioModuleChangeMonitor(mAudioControlWrapper,
                new CarVolumeInfoWrapper(this), mCarAudioZones);
        mCarAudioModuleChangeMonitor.setModuleChangeCallback(mHalAudioModuleChangeCallback);
    }

    /*
     * Currently only BUS and BUILT_SPEAKER devices are valid static devices.
     */
    private static boolean isValidDeviceType(int type) {
        return type == AudioDeviceInfo.TYPE_BUS || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }

    /**
     * Read from {@link #AUDIO_CONFIGURATION_PATHS} respectively.
     * @return File path of the first hit in {@link #AUDIO_CONFIGURATION_PATHS}
     */
    @Nullable
    private static String getAudioConfigurationPath() {
        for (String path : AUDIO_CONFIGURATION_PATHS) {
            File configuration = new File(path);
            if (configuration.exists()) {
                return path;
            }
        }
        return null;
    }

    @Nullable
    private static String getAudioFadeConfigurationPath() {
        File fadeConfiguration = new File(FADE_CONFIGURATION_PATH);
        if (fadeConfiguration.exists()) {
            return FADE_CONFIGURATION_PATH;
        }
        return null;
    }

    @Override
    public void setFadeTowardFront(float value) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            requireValidFadeRange(value);
            getAudioControlWrapperLocked().setFadeTowardFront(value);
        }
    }

    @Override
    public void setBalanceTowardRight(float value) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            requireValidBalanceRange(value);
            getAudioControlWrapperLocked().setBalanceTowardRight(value);
        }
    }

    /**
     * @return Array of accumulated device addresses, empty array if we found nothing
     */
    @Override
    public @NonNull String[] getExternalSources() {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
            List<String> sourceAddresses = new ArrayList<>();

            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            if (devices.length == 0) {
                Slogf.w(TAG, "getExternalSources, no input devices found");
            }

            // Collect the list of non-microphone input ports
            for (AudioDeviceInfo info : devices) {
                switch (info.getType()) {
                    // TODO:  Can we trim this set down? Especially duplicates like FM vs FM_TUNER?
                    case AudioDeviceInfo.TYPE_FM:
                    case AudioDeviceInfo.TYPE_FM_TUNER:
                    case AudioDeviceInfo.TYPE_TV_TUNER:
                    case AudioDeviceInfo.TYPE_HDMI:
                    case AudioDeviceInfo.TYPE_AUX_LINE:
                    case AudioDeviceInfo.TYPE_LINE_ANALOG:
                    case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                    case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                    case AudioDeviceInfo.TYPE_USB_DEVICE:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                    case AudioDeviceInfo.TYPE_IP:
                    case AudioDeviceInfo.TYPE_BUS:
                        String address = info.getAddress();
                        if (TextUtils.isEmpty(address)) {
                            Slogf.w(TAG, "Discarded device with empty address, type=%d",
                                    info.getType());
                        } else {
                            sourceAddresses.add(address);
                        }
                        break;
                    default:
                        Slogf.w(TAG, "Unsupported input devices, type=%d", info.getType());
                        break;
                }
            }

            return sourceAddresses.toArray(new String[0]);
        }
    }

    @Override
    public CarAudioPatchHandle createAudioPatch(String sourceAddress,
            @AttributeUsage int usage, int gainInMillibels) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        enforceCanUseAudioPatchAPI();
        synchronized (mImplLock) {
            return createAudioPatchLocked(sourceAddress, usage, gainInMillibels);
        }
    }

    @Override
    public void releaseAudioPatch(CarAudioPatchHandle carPatch) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        enforceCanUseAudioPatchAPI();
        synchronized (mImplLock) {
            releaseAudioPatchLocked(carPatch);
        }
    }

    private void enforceCanUseAudioPatchAPI() {
        if (!areAudioPatchAPIsEnabled()) {
            throw new IllegalStateException("Audio Patch APIs not enabled, see "
                    + PROPERTY_RO_ENABLE_AUDIO_PATCH);
        }
    }

    private boolean areAudioPatchAPIsEnabled() {
        return SystemProperties.getBoolean(PROPERTY_RO_ENABLE_AUDIO_PATCH, /* default= */ false);
    }

    @GuardedBy("mImplLock")
    private CarAudioPatchHandle createAudioPatchLocked(String sourceAddress,
            @AttributeUsage int usage, int gainInMillibels) {
        // Find the named source port
        AudioDeviceInfo sourcePortInfo = null;
        AudioDeviceInfo[] deviceInfos = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo info : deviceInfos) {
            if (sourceAddress.equals(info.getAddress())) {
                // This is the one for which we're looking
                sourcePortInfo = info;
                break;
            }
        }
        Objects.requireNonNull(sourcePortInfo,
                "Specified source is not available: " + sourceAddress);

        AudioAttributes audioAttributes = CarAudioContext.getAudioAttributeFromUsage(usage);

        AudioPatchInfo audioPatchInfo = AudioManagerHelper.createAudioPatch(sourcePortInfo,
                getOutputDeviceForAudioAttributeLocked(PRIMARY_AUDIO_ZONE, audioAttributes),
                gainInMillibels);

        Slogf.d(TAG, "Audio patch created: %s", audioPatchInfo);

        // Ensure the initial volume on output device port
        int groupId = getVolumeGroupIdForAudioAttributeLocked(PRIMARY_AUDIO_ZONE, audioAttributes);
        setGroupVolume(PRIMARY_AUDIO_ZONE, groupId,
                getGroupVolume(PRIMARY_AUDIO_ZONE, groupId), 0);

        return new CarAudioPatchHandle(audioPatchInfo.getHandleId(),
                audioPatchInfo.getSourceAddress(), audioPatchInfo.getSinkAddress());
    }

    @GuardedBy("mImplLock")
    private void releaseAudioPatchLocked(CarAudioPatchHandle carPatch) {
        Objects.requireNonNull(carPatch);

        if (AudioManagerHelper.releaseAudioPatch(mAudioManager, getAudioPatchInfo(carPatch))) {
            Slogf.d(TAG, "releaseAudioPatch %s successfully", carPatch);
        }
        // If we didn't find a match, then something went awry, but it's probably not fatal...
        Slogf.e(TAG, "releaseAudioPatch found no match for %s", carPatch);
    }

    private static AudioPatchInfo getAudioPatchInfo(CarAudioPatchHandle carPatch) {
        return new AudioPatchInfo(carPatch.getSourceAddress(),
                carPatch.getSinkAddress(),
                carPatch.getHandleId());
    }

    @Override
    public int getVolumeGroupCount(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (runInLegacyMode()) {
            return CarAudioDynamicRouting.STREAM_TYPES.length;
        }

        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId).getCurrentVolumeGroupCount();
        }
    }

    @Override
    public int getVolumeGroupIdForUsage(int zoneId, @AttributeUsage int usage) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (!CarAudioContext.isValidAudioAttributeUsage(usage)) {
            return INVALID_VOLUME_GROUP_ID;
        }

        synchronized (mImplLock) {
            return getVolumeGroupIdForAudioAttributeLocked(zoneId,
                    CarAudioContext.getAudioAttributeFromUsage(usage));
        }
    }

    @Override
    public CarVolumeGroupInfo getVolumeGroupInfo(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (runInLegacyMode()) {
            return null;
        }
        synchronized (mImplLock) {
            return getCarVolumeGroupLocked(zoneId, groupId).getCarVolumeGroupInfo();
        }
    }

    @Override
    public List<CarVolumeGroupInfo> getVolumeGroupInfosForZone(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (runInLegacyMode()) {
            return EMPTY_LIST;
        }
        synchronized (mImplLock) {
            return getVolumeGroupInfosForZoneLocked(zoneId);
        }
    }

    @Override
    public List<AudioAttributes> getAudioAttributesForVolumeGroup(CarVolumeGroupInfo groupInfo) {
        Objects.requireNonNull(groupInfo, "Car volume group info can not be null");
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        if (runInLegacyMode()) {
            return EMPTY_LIST;
        }

        synchronized (mImplLock) {
            return getCarAudioZoneLocked(groupInfo.getZoneId())
                    .getCurrentVolumeGroup(groupInfo.getId()).getAudioAttributes();
        }
    }

    @GuardedBy("mImplLock")
    private int getVolumeGroupIdForAudioAttributeLocked(int zoneId,
            AudioAttributes audioAttributes) {
        if (runInLegacyMode()) {
            return getStreamTypeFromAudioAttribute(audioAttributes);
        }

        @AudioContext int audioContext =
                mCarAudioContext.getContextForAudioAttribute(audioAttributes);
        return getVolumeGroupIdForAudioContextLocked(zoneId, audioContext);
    }

    private static int getStreamTypeFromAudioAttribute(AudioAttributes audioAttributes) {
        int usage = audioAttributes.getSystemUsage();
        for (int i = 0; i < CarAudioDynamicRouting.STREAM_TYPE_USAGES.length; i++) {
            if (usage == CarAudioDynamicRouting.STREAM_TYPE_USAGES[i]) {
                return i;
            }
        }

        return INVALID_VOLUME_GROUP_ID;
    }

    @GuardedBy("mImplLock")
    private int getVolumeGroupIdForAudioContextLocked(int zoneId, @AudioContext int audioContext) {
        CarVolumeGroup[] groups = getCarAudioZoneLocked(zoneId).getCurrentVolumeGroups();
        for (int i = 0; i < groups.length; i++) {
            int[] groupAudioContexts = groups[i].getContexts();
            for (int groupAudioContext : groupAudioContexts) {
                if (audioContext == groupAudioContext) {
                    return i;
                }
            }
        }
        return INVALID_VOLUME_GROUP_ID;
    }

    @Override
    public @NonNull int[] getUsagesForVolumeGroupId(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

        if (runInLegacyMode()) {
            return new int[] { CarAudioDynamicRouting.STREAM_TYPE_USAGES[groupId] };
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            int[] contexts = group.getContexts();
            List<Integer> usages = new ArrayList<>();
            for (int index = 0; index < contexts.length; index++) {
                AudioAttributes[] attributesForContext =
                        mCarAudioContext.getAudioAttributesForContext(contexts[index]);
                for (int counter = 0; counter < attributesForContext.length; counter++) {
                    usages.add(attributesForContext[counter].getSystemUsage());
                }
            }

            int[] usagesArray = CarServiceUtils.toIntArray(usages);

            return usagesArray;
        }
    }

    @Override
    public boolean isPlaybackOnVolumeGroupActive(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireNonLegacyRouting();
        Preconditions.checkArgument(isAudioZoneIdValid(zoneId),
                "Invalid audio zone id %d", zoneId);

        CarVolume carVolume;
        synchronized (mImplLock) {
            carVolume = mCarVolume;
        }
        return carVolume.isAnyContextActive(getContextsForVolumeGroupId(zoneId, groupId),
                getActiveAttributesFromPlaybackConfigurations(zoneId),
                getCallStateForZone(zoneId), getActiveHalAudioAttributesForZone(zoneId));
    }

    /**
     *
     * returns the current call state ({@code CALL_STATE_OFFHOOK}, {@code CALL_STATE_RINGING},
     * {@code CALL_STATE_IDLE}) from the telephony manager.
     */
    int getCallStateForZone(int zoneId) {
        synchronized (mImplLock) {
            // Only driver can use telephony stack
            if (getUserIdForZoneLocked(zoneId) == mOccupantZoneService.getDriverUserId()) {
                return mTelephonyManager.getCallState();
            }
        }
        return TelephonyManager.CALL_STATE_IDLE;
    }

    private List<AudioAttributes> getActiveAttributesFromPlaybackConfigurations(int zoneId) {
        return getCarAudioZone(zoneId)
                .findActiveAudioAttributesFromPlaybackConfigurations(mAudioManager
                        .getActivePlaybackConfigurations());
    }

    private @NonNull @AudioContext int[] getContextsForVolumeGroupId(int zoneId, int groupId) {
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.getContexts();
        }
    }

    @GuardedBy("mImplLock")
    private List<CarVolumeGroupInfo> getVolumeGroupInfosForZoneLocked(int zoneId) {
        return getCarAudioZoneLocked(zoneId).getCurrentVolumeGroupInfos();
    }

    /**
     * Gets the ids of all available audio zones
     *
     * @return Array of available audio zones ids
     */
    @Override
    public @NonNull int[] getAudioZoneIds() {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        synchronized (mImplLock) {
            int[] zoneIds = new int[mCarAudioZones.size()];
            for (int i = 0; i < mCarAudioZones.size(); i++) {
                zoneIds[i] = mCarAudioZones.keyAt(i);
            }
            return zoneIds;
        }
    }

    /**
     * Gets the audio zone id currently mapped to uid,
     *
     * <p><b>Note:</b> Will use uid mapping first, followed by uid's user id mapping.
     * defaults to PRIMARY_AUDIO_ZONE if no mapping exist
     *
     * @param uid The uid
     * @return zone id mapped to uid
     */
    @Override
    public int getZoneIdForUid(int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        synchronized (mImplLock) {
            return getZoneIdForUidLocked(uid);
        }
    }

    @GuardedBy("mImplLock")
    private int getZoneIdForUidLocked(int uid) {
        if (mUidToZoneMap.containsKey(uid)) {
            return mUidToZoneMap.get(uid);
        }

        return getZoneIdForUserLocked(UserHandle.getUserHandleForUid(uid));
    }

    @GuardedBy("mImplLock")
    private int getZoneIdForUserLocked(UserHandle handle) {
        CarOccupantZoneManager.OccupantZoneInfo info =
                mOccupantZoneService.getOccupantZoneForUser(handle);

        int audioZoneId = CarAudioManager.INVALID_AUDIO_ZONE;
        if (info != null) {
            audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(info.zoneId);
        }

        return audioZoneId == CarAudioManager.INVALID_AUDIO_ZONE ? PRIMARY_AUDIO_ZONE : audioZoneId;
    }

    /**
     * Maps the audio zone id to uid
     *
     * @param zoneId The audio zone id
     * @param uid The uid to map
     *
     * <p><b>Note:</b> Will throw if occupant zone mapping exist, as uid and occupant zone mapping
     * do not work in conjunction.
     *
     * @return true if the device affinities, for devices in zone, are successfully set
     */
    @Override
    public boolean setZoneIdForUid(int zoneId, int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        Slogf.i(TAG, "setZoneIdForUid Calling uid %d mapped to : %d", uid, zoneId);
        synchronized (mImplLock) {
            checkAudioZoneIdLocked(zoneId);
            // If occupant mapping exist uid routing can not be used
            requiredOccupantZoneMappingDisabledLocked();

            // Figure out if anything is currently holding focus,
            // This will change the focus to transient loss while we are switching zones
            Integer currentZoneId = mUidToZoneMap.get(uid);
            ArrayList<AudioFocusInfo> currentFocusHoldersForUid = new ArrayList<>();
            ArrayList<AudioFocusInfo> currentFocusLosersForUid = new ArrayList<>();
            if (currentZoneId != null) {
                currentFocusHoldersForUid = mFocusHandler.getAudioFocusHoldersForUid(uid,
                        currentZoneId.intValue());
                currentFocusLosersForUid = mFocusHandler.getAudioFocusLosersForUid(uid,
                        currentZoneId.intValue());
                if (!currentFocusHoldersForUid.isEmpty() || !currentFocusLosersForUid.isEmpty()) {
                    // Order matters here: Remove the focus losers first
                    // then do the current holder to prevent loser from popping up while
                    // the focus is being remove for current holders
                    // Remove focus for current focus losers
                    mFocusHandler.transientlyLoseInFocusInZone(currentFocusLosersForUid,
                            currentZoneId.intValue());
                    // Remove focus for current holders
                    mFocusHandler.transientlyLoseInFocusInZone(currentFocusHoldersForUid,
                            currentZoneId.intValue());
                }
            }

            // if the current uid is in the list
            // remove it from the list

            if (checkAndRemoveUidLocked(uid)) {
                if (setZoneIdForUidNoCheckLocked(zoneId, uid)) {
                    // Order matters here: Regain focus for
                    // Previously lost focus holders then regain
                    // focus for holders that had it last
                    // Regain focus for the focus losers from previous zone
                    if (!currentFocusLosersForUid.isEmpty()) {
                        regainAudioFocusLocked(currentFocusLosersForUid, zoneId);
                    }
                    // Regain focus for the focus holders from previous zone
                    if (!currentFocusHoldersForUid.isEmpty()) {
                        regainAudioFocusLocked(currentFocusHoldersForUid, zoneId);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    @GuardedBy("mImplLock")
    private boolean handleAssignAudioFromUserIdToPrimaryAudioZoneLocked(
            IBinder token, int userId, int zoneId, long requestId) {
        AudioFocusStack mediaFocusStack =
                mFocusHandler.transientlyLoseMediaAudioFocusForUser(userId, zoneId);

        if (!shareAudioRoutingForUserInPrimaryAudioZoneLocked(userId, zoneId)) {
            Slogf.w(TAG, "Can not route user id %s to primary audio zone", userId);
            mFocusHandler.regainMediaAudioFocusInZone(mediaFocusStack, zoneId);
            return false;
        }

        DeathRecipient deathRecipient = () -> handleAssignedAudioFromUserDeath(requestId);
        try {
            token.linkToDeath(deathRecipient, /* flags= */ 0);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Can not route user id %d to primary audio zone, caller died", userId);
            mFocusHandler.regainMediaAudioFocusInZone(mediaFocusStack, zoneId);
            return false;
        }

        mFocusHandler.regainMediaAudioFocusInZone(mediaFocusStack, PRIMARY_AUDIO_ZONE);
        mUserAssignedToPrimaryZoneToCallbackDeathRecipient.put(userId, deathRecipient);
        mMediaRequestHandler.acceptMediaAudioRequest(token, requestId);

        Slogf.d(TAG, "Assigning user id %d from primary audio zone", userId);

        return true;
    }

    @GuardedBy("mImplLock")
    private boolean shareAudioRoutingForUserInPrimaryAudioZoneLocked(int userId, int zoneId) {
        CarAudioZone zone = mCarAudioZones.get(zoneId);
        return shareUserIdMediaInMainZoneLocked(userId, zone);
    }

    @GuardedBy("mImplLock")
    private boolean shareUserIdMediaInMainZoneLocked(int userId, CarAudioZone audioZone) {
        List<AudioDeviceInfo> devices = getAudioDeviceInfos(audioZone);
        devices.add(getMediaDeviceForPrimaryZoneLocked());

        return setUserIdDeviceAffinityLocked(devices, userId, audioZone.getId());
    }

    private AudioDeviceInfo getAudioDeviceInfoOrThrowIfNotFound(
            AudioDeviceAttributes audioDeviceAttributes) {
        AudioDeviceInfo info = CarAudioUtils.getAudioDeviceInfo(audioDeviceAttributes,
                mAudioManager);
        if (info != null) {
            return info;
        }
        throw new IllegalStateException("Output audio device address "
                + audioDeviceAttributes.getAddress() + " is not currently available");
    }

    @GuardedBy("mImplLock")
    private boolean setupMirrorDeviceForUserIdLocked(int userId, CarAudioZone audioZone,
                                                     AudioDeviceAttributes mirrorDevice) {
        List<AudioDeviceAttributes> devices = audioZone.getCurrentAudioDevices();
        devices.add(mirrorDevice);

        Slogf.d(TAG, "setupMirrorDeviceForUserIdLocked for userId %d in zone %d", userId,
                audioZone.getId());

        return setUserIdDeviceAffinityLocked(getAudioDeviceInfosFromAttributes(devices), userId,
                audioZone.getId());
    }

    @GuardedBy("mImplLock")
    private boolean setUserIdDeviceAffinityLocked(List<AudioDeviceInfo> devices,
            int userId, int zoneId) {
        boolean results = mRoutingAudioPolicy.setUserIdDeviceAffinity(userId, devices);
        if (!results) {
            Slogf.w(TAG, "setUserIdDeviceAffinityLocked for userId %d in zone %d Failed,"
                    + " could not set audio routing.", userId, zoneId);
        }
        return results;
    }

    private void handleAssignedAudioFromUserDeath(long requestId) {
        Slogf.e(TAG, "IBinder for request %d died", requestId);
        handleUnassignAudioFromUserIdOnPrimaryAudioZone(requestId);
    }

    private boolean handleUnassignAudioFromUserIdOnPrimaryAudioZone(long requestId) {
        CarOccupantZoneManager.OccupantZoneInfo info =
                mMediaRequestHandler.getOccupantForRequest(requestId);

        if (info == null) {
            Slogf.w(TAG, "Occupant %s is not mapped to any audio zone", info);
            return false;
        }
        int userId = mOccupantZoneService.getUserForOccupant(info.zoneId);
        int audioZoneId = mOccupantZoneService.getAudioZoneIdForOccupant(info.zoneId);

        synchronized (mImplLock) {
            CarAudioZone audioZone = getCarAudioZoneLocked(audioZoneId);

            AudioFocusStack mediaFocusStack =
                    mFocusHandler.transientlyLoseMediaAudioFocusForUser(userId, PRIMARY_AUDIO_ZONE);

            if (!resetUserIdMediaInMainZoneLocked(userId, audioZone)) {
                Slogf.w(TAG, "Can not remove route for user id %d to primary audio zone", userId);
                mFocusHandler.regainMediaAudioFocusInZone(mediaFocusStack, PRIMARY_AUDIO_ZONE);
                return false;
            }

            mFocusHandler.regainMediaAudioFocusInZone(mediaFocusStack, audioZoneId);
            removeAssignedUserInfoLocked(userId);
        }

        Slogf.d(TAG, "Unassigned user id %d from primary audio zone", userId);

        return mMediaRequestHandler.stopMediaAudioOnPrimaryZone(requestId);
    }

    @GuardedBy("mImplLock")
    private void removeAssignedUserInfoLocked(int userId) {
        mUserAssignedToPrimaryZoneToCallbackDeathRecipient.remove(userId);
    }

    @GuardedBy("mImplLock")
    private boolean resetUserIdMediaInMainZoneLocked(int userId, CarAudioZone audioZone) {
        List<AudioDeviceInfo> devices = getAudioDeviceInfos(audioZone);
        return setUserIdDeviceAffinityLocked(devices, userId, audioZone.getId());
    }

    @GuardedBy("mImplLock")
    private AudioDeviceInfo getOutputDeviceForAudioAttributeLocked(int zoneId,
            AudioAttributes audioAttributes) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        int contextForUsage = mCarAudioContext.getContextForAudioAttribute(audioAttributes);
        Preconditions.checkArgument(!CarAudioContext.isInvalidContextId(contextForUsage),
                "Invalid audio attribute usage %s", audioAttributes);
        return getAudioDeviceInfoOrThrowIfNotFound(getCarAudioZoneLocked(zoneId)
                .getAudioDeviceForContext(contextForUsage));
    }

    @Override
    public String getOutputDeviceAddressForUsage(int zoneId, @AttributeUsage int usage) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        CarAudioContext.checkAudioAttributeUsage(usage);
        return getOutputDeviceAddressForUsageInternal(zoneId, usage);
    }

    /**
     * Regain focus for the focus list passed in
     * @param afiList focus info list to regain
     * @param zoneId zone id where the focus holder belong
     */
    @GuardedBy("mImplLock")
    void regainAudioFocusLocked(ArrayList<AudioFocusInfo> afiList, int zoneId) {
        for (AudioFocusInfo info : afiList) {
            if (mFocusHandler.reevaluateAndRegainAudioFocus(info)
                    != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Slogf.i(TAG,
                        " Focus could not be granted for entry %s uid %d in zone %d",
                        info.getClientId(), info.getClientUid(), zoneId);
            }
        }
    }

    /**
     * Removes the current mapping of the uid, focus will be lost in zone
     * @param uid The uid to remove
     *
     * <p><b>Note:</b> Will throw if occupant zone mapping exist, as uid and occupant zone mapping
     * do not work in conjunction.
     *
     * return true if all the devices affinities currently
     *            mapped to uid are successfully removed
     */
    @Override
    public boolean clearZoneIdForUid(int uid) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        synchronized (mImplLock) {
            // Throw so as to not set the wrong expectation,
            // that routing will be changed if clearZoneIdForUid is called.
            requiredOccupantZoneMappingDisabledLocked();

            return checkAndRemoveUidLocked(uid);
        }
    }

    /**
     * Sets the zone id for uid
     * @param zoneId zone id to map to uid
     * @param uid uid to map
     * @return true if setting uid device affinity is successful
     */
    @GuardedBy("mImplLock")
    private boolean setZoneIdForUidNoCheckLocked(int zoneId, int uid) {
        Slogf.d(TAG, "setZoneIdForUidNoCheck Calling uid %d mapped to %d", uid, zoneId);
        //Request to add uid device affinity
        List<AudioDeviceInfo> deviceInfos =
                getAudioDeviceInfos(getCarAudioZoneLocked(zoneId));
        if (mRoutingAudioPolicy.setUidDeviceAffinity(uid, deviceInfos)) {
            // TODO do not store uid mapping here instead use the uid
            //  device affinity in audio policy when available
            mUidToZoneMap.put(uid, zoneId);
            return true;
        }
        Slogf.w(TAG, "setZoneIdForUidNoCheck Failed set device affinity for uid %d in zone %d",
                uid, zoneId);
        return false;
    }

    /**
     * Check if uid is attached to a zone and remove it
     * @param uid unique id to remove
     * @return true if the uid was successfully removed or mapping was not assigned
     */
    @GuardedBy("mImplLock")
    private boolean checkAndRemoveUidLocked(int uid) {
        Integer zoneId = mUidToZoneMap.get(uid);
        if (zoneId != null) {
            Slogf.i(TAG, "checkAndRemoveUid removing Calling uid %d from zone %d", uid, zoneId);
            if (mRoutingAudioPolicy.removeUidDeviceAffinity(uid)) {
                // TODO use the uid device affinity in audio policy when available
                mUidToZoneMap.remove(uid);
                return true;
            }
            //failed to remove device affinity from zone devices
            Slogf.w(TAG, "checkAndRemoveUid Failed remove device affinity for uid %d in zone %d",
                    uid, zoneId);
            return false;
        }
        return true;
    }

    /*
     *  {@link android.car.media.CarAudioManager#registerCarVolumeGroupEventCallback()}
     */
    @Override
    public boolean registerCarVolumeEventCallback(ICarVolumeEventCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireNonLegacyRouting();
        requireVolumeGroupEvents();

        int uid = Binder.getCallingUid();
        mCarVolumeEventHandler.registerCarVolumeEventCallback(callback, uid);
        mCarVolumeCallbackHandler.checkAndRepriotize(uid, false);
        return true;
    }

    /*
     *  {@link android.car.media.CarAudioManager#unregisterCarVolumeGroupEventCallback()}
     */
    @Override
    public boolean unregisterCarVolumeEventCallback(ICarVolumeEventCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireNonLegacyRouting();
        requireVolumeGroupEvents();

        int uid = Binder.getCallingUid();
        mCarVolumeEventHandler.unregisterCarVolumeEventCallback(callback, uid);
        mCarVolumeCallbackHandler.checkAndRepriotize(uid, true);
        return true;
    }

    @Override
    public void registerVolumeCallback(@NonNull IBinder binder) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            int uid = Binder.getCallingUid();
            mCarVolumeCallbackHandler.registerCallback(binder, uid,
                    !mCarVolumeEventHandler.checkIfUidIsRegistered(uid));
        }
    }

    @Override
    public void unregisterVolumeCallback(@NonNull IBinder binder) {
        synchronized (mImplLock) {
            enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
            mCarVolumeCallbackHandler.unregisterCallback(binder, Binder.getCallingUid());
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#isVolumeGroupMuted(int, int)}
     */
    @Override
    public boolean isVolumeGroupMuted(int zoneId, int groupId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireNonLegacyRouting();
        if (!mUseCarVolumeGroupMuting) {
            return false;
        }
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            return group.isMuted();
        }
    }

    /**
     * {@link android.car.media.CarAudioManager#setVolumeGroupMute(int, int, boolean, int)}
     */
    @Override
    public void setVolumeGroupMute(int zoneId, int groupId, boolean mute, int flags) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        requireNonLegacyRouting();
        requireVolumeGroupMuting();
        boolean muteStateChanged;
        boolean isSystemMuted;
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
            isSystemMuted = group.isHalMuted();
            muteStateChanged = group.setMute(mute);
        }
        if (muteStateChanged || (isSystemMuted && !mute)) {
            handleMuteChanged(zoneId, groupId, flags);
            callbackVolumeGroupEvent(List.of(convertVolumeChangeToEvent(
                    getVolumeGroupInfo(zoneId, groupId), flags, EVENT_TYPE_MUTE_CHANGED)));
        }
    }

    @Override
    public @NonNull List<AudioDeviceAttributes> getInputDevicesForZoneId(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();

        return getCarAudioZone(zoneId).getInputAudioDevices();
    }

    @Override
    public CarAudioZoneConfigInfo getCurrentAudioZoneConfigInfo(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId).getCurrentCarAudioZoneConfig()
                    .getCarAudioZoneConfigInfo();
        }
    }

    @Override
    public List<CarAudioZoneConfigInfo> getAudioZoneConfigInfos(int zoneId) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId).getCarAudioZoneConfigInfos();
        }
    }

    @Override
    public void switchZoneToConfig(CarAudioZoneConfigInfo zoneConfig,
            ISwitchAudioZoneConfigCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        Objects.requireNonNull(zoneConfig, "Car audio zone config to switch to can not be null");
        verifyCanSwitchZoneConfigs(zoneConfig);
        mHandler.post(() -> {
            boolean isSuccessful = handleSwitchZoneConfig(zoneConfig);
            CarAudioZoneConfigInfo updatedInfo = getAudioZoneConfigInfo(zoneConfig);
            try {
                callback.onAudioZoneConfigSwitched(updatedInfo, isSuccessful);
            } catch (RemoteException e) {
                Slogf.e(TAG, e, "Could not inform zone configuration %s switch result",
                        updatedInfo);
            }
        });
    }

    @Override
    public boolean registerAudioZoneConfigsChangeCallback(
            IAudioZoneConfigurationsChangeCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        Objects.requireNonNull(callback, "Car audio zone configs callback can not be null");

        return mConfigsCallbacks.register(callback);
    }

    @Override
    public boolean unregisterAudioZoneConfigsChangeCallback(
            IAudioZoneConfigurationsChangeCallback callback) {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        requireNonLegacyRouting();
        Objects.requireNonNull(callback, "Car audio zone configs callback can not be null");

        return mConfigsCallbacks.unregister(callback);
    }

    @Nullable
    private CarAudioZoneConfigInfo getAudioZoneConfigInfo(CarAudioZoneConfigInfo zoneConfig) {
        List<CarAudioZoneConfigInfo> infos = getAudioZoneConfigInfos(zoneConfig.getZoneId());
        for (int c = 0; c < infos.size(); c++) {
            if (infos.get(c).getConfigId() != zoneConfig.getConfigId()) {
                continue;
            }
            return infos.get(c);
        }
        return null;
    }

    private void verifyCanSwitchZoneConfigs(CarAudioZoneConfigInfo zoneConfig) {
        int zoneId = zoneConfig.getZoneId();
        synchronized (mImplLock) {
            checkAudioZoneIdLocked(zoneId);
        }

        CarAudioZoneConfigInfo updatedInfo = getAudioZoneConfigInfo(zoneConfig);

        if (updatedInfo == null) {
            throw  new IllegalStateException("Car audio zone config " + zoneConfig.getConfigId()
                    + " in zone " + zoneId + " does not exist");
        }

        if (!updatedInfo.isActive()) {
            throw  new IllegalStateException("Car audio zone config " + zoneConfig.getConfigId()
            + " in zone " + zoneId + " is not active");
        }

        int userId = getUserIdForZone(zoneId);
        if (userId == UserManagerHelper.USER_NULL) {
            throw new IllegalStateException(
                    "Audio zone must have an active user to allow switching zone configuration");
        }

        CarOccupantZoneManager.OccupantZoneInfo info =
                mOccupantZoneService.getOccupantForAudioZoneId(zoneId);

        if (mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
            throw new IllegalStateException(
                    "Occupant " + info + " in audio zone " + zoneId
                            + " is currently sharing to primary zone, undo audio sharing in "
                            + "primary zone before switching zone configuration");
        }

        if (mCarAudioMirrorRequestHandler.isMirrorAudioEnabled()
                && mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zoneId)) {
            throw new IllegalStateException("Audio zone " + zoneId + " is currently in a mirroring"
                    + " configuration, undo audio mirroring before switching zone configuration");
        }
    }

    private boolean handleSwitchZoneConfig(CarAudioZoneConfigInfo zoneConfig) {
        int zoneId = zoneConfig.getZoneId();
        CarAudioZone zone;
        TimingsTraceLog log = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        log.traceBegin("switch-config-" + zoneConfig.getConfigId());
        synchronized (mImplLock) {
            zone = getCarAudioZoneLocked(zoneId);
        }
        if (zone.isCurrentZoneConfig(zoneConfig)) {
            Slogf.w(TAG, "handleSwitchZoneConfig switch current zone configuration");
            log.traceEnd();
            return true;
        }

        CarOccupantZoneManager.OccupantZoneInfo info =
                mOccupantZoneService.getOccupantForAudioZoneId(zoneId);
        if (mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
            Slogf.w(TAG, "handleSwitchZoneConfig failed, occupant %s in audio zone %d is "
                            + "currently sharing to primary zone, undo audio sharing in primary "
                            + "zone before switching zone configuration", info, zoneId);
            log.traceEnd();
            return false;
        }

        if (mCarAudioMirrorRequestHandler.isMirrorAudioEnabled()
                && mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zoneId)) {
            Slogf.w(TAG, "handleSwitchZoneConfig failed, audio zone %d is currently in a mirroring"
                    + "configuration, undo audio mirroring before switching zone configuration",
                    zoneId);
            log.traceEnd();
            return false;
        }

        boolean succeeded = true;
        List<CarVolumeGroupInfo> carVolumeGroupInfoList = null;
        AudioPolicy newAudioPolicy = null;
        CarAudioZoneConfig prevZoneConfig;
        synchronized (mImplLock) {
            int userId = getUserIdForZoneLocked(zoneId);
            if (userId == UserManagerHelper.USER_NULL) {
                Slogf.w(TAG, "handleSwitchZoneConfig failed, audio zone configuration switching "
                        + "not allowed for unassigned audio zone %d", zoneId);
                log.traceEnd();
                return false;
            }
            List<AudioFocusInfo> pendingFocusInfos =
                    mFocusHandler.transientlyLoseAllFocusHoldersInZone(zoneId);

            prevZoneConfig = zone.getCurrentCarAudioZoneConfig();
            try {
                log.traceBegin("switch-config-set-" + zoneConfig.getConfigId());
                zone.setCurrentCarZoneConfig(zoneConfig);
                CarAudioZoneConfig newZoneConfig = zone.getCurrentCarAudioZoneConfig();
                // Default config always exists in the policy, so should be able to switch to
                // default without creating a new policy
                newAudioPolicy = newZoneConfig.isDefault()
                        ? mRoutingAudioPolicy : setupRoutingAudioPolicyLocked();
                setAllUserIdDeviceAffinitiesToNewPolicyLocked(newAudioPolicy);
                zone.updateVolumeGroupsSettingsForUser(userId);
                carVolumeGroupInfoList = getVolumeGroupInfosForZoneLocked(zoneId);
                updateFadeManagerConfigurationLocked(zone.isPrimaryZone());
            } catch (Exception e) {
                Slogf.e(TAG, "Failed to switch configuration id " + zoneConfig.getConfigId());
                zone.setCurrentCarZoneConfig(prevZoneConfig.getCarAudioZoneConfigInfo());
                succeeded = false;
                // No need to unset the user id device affinities, since the policy is removed
                if (newAudioPolicy != null && newAudioPolicy != mRoutingAudioPolicy) {
                    mAudioManager.unregisterAudioPolicyAsync(newAudioPolicy);
                }
            } finally {
                log.traceEnd();
            }
            log.traceBegin("switch-config-focus" + zoneConfig.getConfigId());
            mFocusHandler.reevaluateAndRegainAudioFocusList(pendingFocusInfos);
            log.traceEnd();
            if (succeeded) {
                swapRoutingAudioPolicyLocked(newAudioPolicy);
            }
        }
        if (!succeeded) {
            log.traceEnd();
            return false;
        }
        enableDynamicDevicesInOtherZones(prevZoneConfig.getCarAudioZoneConfigInfo());
        disableDynamicDevicesInOtherZones(zoneConfig);

        log.traceEnd();
        callbackVolumeGroupEvent(getVolumeGroupEventsForSwitchZoneConfig(carVolumeGroupInfoList));
        return true;
    }

    private void enableDynamicDevicesInOtherZones(CarAudioZoneConfigInfo zoneConfig) {
        if (!Flags.carAudioDynamicDevices()) {
            return;
        }
        if (excludesDynamicDevices(zoneConfig)) {
            return;
        }
        List<AudioDeviceInfo> dynamicDevicesInConfig =
                getDynamicDevicesInConfig(zoneConfig, mAudioManager);
        // If the devices were already removed just move on, device removal will manage the rest
        if (dynamicDevicesInConfig.isEmpty()) {
            return;
        }
        List<Integer> zonesToSkip = List.of(zoneConfig.getZoneId());
        handleDevicesAdded(dynamicDevicesInConfig, zonesToSkip);
    }

    private void disableDynamicDevicesInOtherZones(CarAudioZoneConfigInfo zoneConfig) {
        if (!Flags.carAudioDynamicDevices()) {
            return;
        }
        if (excludesDynamicDevices(zoneConfig)) {
            return;
        }
        List<AudioDeviceInfo> dynamicDevicesInConfig =
                getDynamicDevicesInConfig(zoneConfig, mAudioManager);
        // If the devices were already removed just move on, device removal will manage the rest
        if (dynamicDevicesInConfig.isEmpty()) {
            return;
        }
        List<Integer> zonesToSkip = List.of(zoneConfig.getZoneId());
        handleDevicesRemoved(dynamicDevicesInConfig, zonesToSkip);
    }

    @GuardedBy("mImplLock")
    private void swapRoutingAudioPolicyLocked(AudioPolicy newAudioPolicy) {
        if (newAudioPolicy == mRoutingAudioPolicy) {
            return;
        }
        AudioPolicy previousRoutingPolicy = mRoutingAudioPolicy;
        mRoutingAudioPolicy = newAudioPolicy;
        if (previousRoutingPolicy == null) {
            return;
        }
        mAudioManager.unregisterAudioPolicyAsync(previousRoutingPolicy);
    }

    @GuardedBy("mImplLock")
    private void setAllUserIdDeviceAffinitiesToNewPolicyLocked(AudioPolicy newAudioPolicy) {
        TimingsTraceLog log = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        log.traceBegin("device-affinities-all-zones");
        for (int c = 0; c < mAudioZoneIdToOccupantZoneIdMapping.size(); c++) {
            int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(c);
            int occupantZoneId = mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId);
            int userId = mOccupantZoneService.getUserForOccupant(occupantZoneId);
            if (userId == UserManagerHelper.USER_NULL) {
                continue;
            }
            log.traceBegin("device-affinities-" + audioZoneId);
            CarAudioZone zone = getCarAudioZoneLocked(audioZoneId);
            resetUserIdDeviceAffinitiesLocked(newAudioPolicy, userId, zone);
            log.traceEnd();
        }
        log.traceEnd();
    }

    @GuardedBy("mImplLock")
    private void resetUserIdDeviceAffinitiesLocked(AudioPolicy audioPolicy, int userId,
            CarAudioZone zone) {
        List<AudioDeviceInfo> devices = getAudioDeviceInfos(zone);
        CarOccupantZoneManager.OccupantZoneInfo info =
                mOccupantZoneService.getOccupantForAudioZoneId(zone.getId());
        if (mMediaRequestHandler.isMediaAudioAllowedInPrimaryZone(info)) {
            devices.add(getMediaDeviceForPrimaryZoneLocked());
        } else if (mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zone.getId())) {
            long request = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(zone.getId());
            if (request != INVALID_REQUEST_ID) {
                devices.add(getAudioDeviceInfoOrThrowIfNotFound(
                        mCarAudioMirrorRequestHandler.getAudioDevice(request)));
            }
        }
        if (audioPolicy.setUserIdDeviceAffinity(userId, devices)) {
            return;
        }
        throw new IllegalStateException("Could not setup audio policy routing for user " + userId
                + " in audio zone " + zone.getId());
    }

    @GuardedBy("mImplLock")
    private AudioDeviceInfo getMediaDeviceForPrimaryZoneLocked() {
        CarAudioZone primaryAudioZone = getCarAudioZoneLocked(PRIMARY_AUDIO_ZONE);
        AudioDeviceAttributes audioDeviceAttributes =
                primaryAudioZone.getAudioDeviceForContext(mCarAudioContext
                        .getContextForAudioAttribute(MEDIA_AUDIO_ATTRIBUTE));
        return getAudioDeviceInfoOrThrowIfNotFound(audioDeviceAttributes);
    }

    private List<CarVolumeGroupEvent> getVolumeGroupEventsForSwitchZoneConfig(
            List<CarVolumeGroupInfo> volumeGroupInfos) {
        CarVolumeGroupEvent.Builder builder = new CarVolumeGroupEvent.Builder(volumeGroupInfos,
                CarVolumeGroupEvent.EVENT_TYPE_ZONE_CONFIGURATION_CHANGED);
        return List.of(builder.build());
    }

    void setAudioEnabled(boolean isAudioEnabled) {
        Slogf.i(TAG, "Setting isAudioEnabled to %b", isAudioEnabled);

        mFocusHandler.setRestrictFocus(/* isFocusRestricted= */ !isAudioEnabled);
        if (mUseCarVolumeGroupMuting) {
            mCarVolumeGroupMuting.setRestrictMuting(/* isMutingRestricted= */ !isAudioEnabled);
        }
        // TODO(b/176258537) if not using group volume, then set master mute accordingly
    }

    private void enforcePermission(String permissionName) {
        if (mContext.checkCallingOrSelfPermission(permissionName)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires permission " + permissionName);
        }
    }

    private void requireNonLegacyRouting() {
        Preconditions.checkState(!runInLegacyMode(), "Non legacy routing is required");
    }

    private void requireAudioMirroring() {
        Preconditions.checkState(mCarAudioMirrorRequestHandler.isMirrorAudioEnabled(),
                "Audio zones mirroring is required");
    }

    private void requireVolumeGroupMuting() {
        Preconditions.checkState(mUseCarVolumeGroupMuting,
                "Car Volume Group Muting is required");
    }

    private void requireVolumeGroupEvents() {
        Preconditions.checkState(mUseCarVolumeGroupEvents,
                "Car Volume Group Event is required");
    }

    private void requireValidFadeRange(float value) {
        Preconditions.checkArgumentInRange(value, -1f, 1f, "Fade");
    }

    private void requireValidBalanceRange(float value) {
        Preconditions.checkArgumentInRange(value, -1f, 1f, "Balance");
    }

    @GuardedBy("mImplLock")
    private void requiredOccupantZoneMappingDisabledLocked() {
        if (isOccupantZoneMappingAvailableLocked()) {
            throw new IllegalStateException(
                    "UID based routing is not supported while using occupant zone mapping");
        }
    }

    @AudioContext int getSuggestedAudioContextForZone(int zoneId) {
        if (!isAudioZoneIdValid(zoneId)) {
            return CarAudioContext.getInvalidContext();
        }
        CarVolume carVolume;
        synchronized (mImplLock) {
            carVolume = mCarVolume;
        }
        return carVolume.getSuggestedAudioContextAndSaveIfFound(
                getAllActiveAttributesForZone(zoneId), getCallStateForZone(zoneId),
                getActiveHalAudioAttributesForZone(zoneId));
    }

    private List<AudioAttributes> getActiveHalAudioAttributesForZone(int zoneId) {
        if (mHalAudioFocus == null) {
            return new ArrayList<>(0);
        }
        return mHalAudioFocus.getActiveAudioAttributesForZone(zoneId);
    }

    /**
     * Gets volume group by a given legacy stream type
     * @param streamType Legacy stream type such as {@link AudioManager#STREAM_MUSIC}
     * @return volume group id mapped from stream type
     */
    private int getVolumeGroupIdForStreamType(int streamType) {
        int groupId = INVALID_VOLUME_GROUP_ID;
        for (int i = 0; i < CarAudioDynamicRouting.STREAM_TYPES.length; i++) {
            if (streamType == CarAudioDynamicRouting.STREAM_TYPES[i]) {
                groupId = i;
                break;
            }
        }
        return groupId;
    }

    private void handleOccupantZoneUserChanged() {
        int driverUserId = mOccupantZoneService.getDriverUserId();
        synchronized (mImplLock) {
            if (!isOccupantZoneMappingAvailableLocked()) {
                adjustZonesToUserIdLocked(driverUserId);
                return;
            }
            int occupantZoneForDriver =  getOccupantZoneIdForDriver();
            Set<Integer> assignedZones = new HashSet<Integer>();
            for (int index = 0; index < mAudioZoneIdToOccupantZoneIdMapping.size(); index++) {
                int audioZoneId = mAudioZoneIdToOccupantZoneIdMapping.keyAt(index);
                int occupantZoneId = mAudioZoneIdToOccupantZoneIdMapping.get(audioZoneId);
                assignedZones.add(audioZoneId);
                updateUserForOccupantZoneLocked(occupantZoneId, audioZoneId, driverUserId,
                        occupantZoneForDriver);
            }

            assignMissingZonesToDriverLocked(driverUserId, assignedZones);
        }
        restoreVolumeGroupMuteState();
    }

    private void restoreVolumeGroupMuteState() {
        if (!mUseCarVolumeGroupMuting) {
            return;
        }
        mCarVolumeGroupMuting.carMuteChanged();
    }

    @GuardedBy("mImplLock")
    private void assignMissingZonesToDriverLocked(@UserIdInt int driverUserId,
            Set<Integer> assignedZones) {
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            if (assignedZones.contains(zone.getId())) {
                continue;
            }
            assignUserIdToAudioZoneLocked(zone, driverUserId);
        }
    }

    @GuardedBy("mImplLock")
    private void adjustZonesToUserIdLocked(@UserIdInt int userId) {
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            assignUserIdToAudioZoneLocked(zone, userId);
        }
    }

    @GuardedBy("mImplLock")
    private void assignUserIdToAudioZoneLocked(CarAudioZone zone, @UserIdInt int userId) {
        if (userId == getUserIdForZoneLocked(zone.getId())) {
            Slogf.d(TAG, "assignUserIdToAudioZone userId(%d) already assigned to audioZoneId(%d)",
                    userId, zone.getId());
            return;
        }
        Slogf.d(TAG, "assignUserIdToAudioZone assigning userId(%d) to audioZoneId(%d)",
                userId, zone.getId());
        zone.updateVolumeGroupsSettingsForUser(userId);
        mFocusHandler.updateUserForZoneId(zone.getId(), userId);
        setUserIdForAudioZoneLocked(userId, zone.getId());
    }

    @GuardedBy("mImplLock")
    private boolean isOccupantZoneMappingAvailableLocked() {
        return mAudioZoneIdToOccupantZoneIdMapping.size() > 0;
    }

    @GuardedBy("mImplLock")
    private void updateUserForOccupantZoneLocked(int occupantZoneId, int audioZoneId,
            @UserIdInt int driverUserId, int occupantZoneForDriver) {
        CarAudioZone audioZone = getCarAudioZoneLocked(audioZoneId);
        int userId = mOccupantZoneService.getUserForOccupant(occupantZoneId);
        int prevUserId = getUserIdForZoneLocked(audioZoneId);

        if (userId == prevUserId) {
            Slogf.d(TAG, "updateUserForOccupantZone userId(%d) already assigned to audioZoneId(%d)",
                    userId, audioZoneId);
            return;
        }

        // No need to undo focus or user device affinities.
        // Focus is handled as user exits.
        // User device affinities are handled below as the user id routing is undone.
        removePrimaryZoneRequestForOccupantLocked(occupantZoneId, prevUserId);

        removeAudioMirrorForZoneId(audioZoneId);

        Slogf.d(TAG, "updateUserForOccupantZone assigning userId(%d) to audioZoneId(%d)",
                userId, audioZoneId);
        // If the user has changed, be sure to remove from current routing
        // This would be true even if the new user is UserManagerHelper.USER_NULL,
        // as that indicates the user has logged out.
        removeUserIdDeviceAffinitiesLocked(prevUserId);

        if (userId == UserManagerHelper.USER_NULL) {
            // Reset zone back to driver user id
            resetZoneToDefaultUser(audioZone, driverUserId);
            setUserIdForAudioZoneLocked(userId, audioZoneId);
            return;
        }

        // Only set user id device affinities for driver when it is the driver's occupant zone
        if (userId != driverUserId || occupantZoneId == occupantZoneForDriver) {
            setUserIdDeviceAffinitiesLocked(audioZone, userId, audioZoneId);
        }
        audioZone.updateVolumeGroupsSettingsForUser(userId);
        mFocusHandler.updateUserForZoneId(audioZoneId, userId);
        setUserIdForAudioZoneLocked(userId, audioZoneId);
    }

    private void removeAudioMirrorForZoneId(int audioZoneId) {
        long requestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(audioZoneId);
        if (requestId == INVALID_REQUEST_ID) {
            return;
        }
        Slogf.i(TAG, "Removing audio zone mirror for zone id %s", audioZoneId);
        handleDisableAudioMirrorForZonesInConfig(new int[]{audioZoneId}, requestId);
    }

    @GuardedBy("mImplLock")
    private void removePrimaryZoneRequestForOccupantLocked(int occupantZoneId, int userId) {
        long requestId = mMediaRequestHandler.getAssignedRequestIdForOccupantZoneId(occupantZoneId);

        if (requestId == INVALID_REQUEST_ID) {
            return;
        }

        Slogf.d(TAG, "removePrimaryZoneRequestForOccupant removing request for %d occupant %d"
                        + " and user id %d", requestId, occupantZoneId, userId);
        removeAssignedUserInfoLocked(userId);
        mMediaRequestHandler.cancelMediaAudioOnPrimaryZone(requestId);
    }

    private int getOccupantZoneIdForDriver() {
        List<CarOccupantZoneManager.OccupantZoneInfo> occupantZoneInfos =
                mOccupantZoneService.getAllOccupantZones();
        for (CarOccupantZoneManager.OccupantZoneInfo info: occupantZoneInfos) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                return info.zoneId;
            }
        }
        return CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID;
    }

    @GuardedBy("mImplLock")
    private void setUserIdDeviceAffinitiesLocked(CarAudioZone zone, @UserIdInt int userId,
            int audioZoneId) {
        List<AudioDeviceInfo> infos = getAudioDeviceInfos(zone);
        if (!infos.isEmpty() && !mRoutingAudioPolicy.setUserIdDeviceAffinity(userId, infos)) {
            throw new IllegalStateException(String.format(
                    "setUserIdDeviceAffinity for userId %d in zone %d Failed,"
                            + " could not set audio routing.",
                    userId, audioZoneId));
        }
    }

    private List<AudioDeviceInfo> getAudioDeviceInfos(CarAudioZone zone) {
        List<AudioDeviceAttributes> attributes = zone.getCurrentAudioDeviceSupportingDynamicMix();
        return getAudioDeviceInfosFromAttributes(attributes);
    }

    private List<AudioDeviceInfo> getAudioDeviceInfosFromAttributes(
            List<AudioDeviceAttributes> attributes) {
        List<AudioDeviceInfo> devices = new ArrayList<>(attributes.size());
        for (int i = 0; i < attributes.size(); i++) {
            devices.add(getAudioDeviceInfoOrThrowIfNotFound(attributes.get(i)));
        }
        return devices;
    }

    private void resetZoneToDefaultUser(CarAudioZone zone, @UserIdInt int driverUserId) {
        resetCarZonesAudioFocus(zone.getId(), driverUserId);
        zone.updateVolumeGroupsSettingsForUser(driverUserId);
    }

    private void resetCarZonesAudioFocus(int audioZoneId, @UserIdInt int driverUserId) {
        mFocusHandler.updateUserForZoneId(audioZoneId, driverUserId);
    }

    @GuardedBy("mImplLock")
    private void removeUserIdDeviceAffinitiesLocked(@UserIdInt int userId) {
        Slogf.d(TAG, "removeUserIdDeviceAffinities(%d) Succeeded", userId);
        if (userId == UserManagerHelper.USER_NULL) {
            return;
        }
        if (!mRoutingAudioPolicy.removeUserIdDeviceAffinity(userId)) {
            Slogf.e(TAG, "removeUserIdDeviceAffinities(%d) Failed", userId);
            return;
        }
    }

    @VisibleForTesting
    @UserIdInt int getUserIdForZone(int audioZoneId) {
        synchronized (mImplLock) {
            return getUserIdForZoneLocked(audioZoneId);
        }
    }

    @GuardedBy("mImplLock")
    private @UserIdInt int getUserIdForZoneLocked(int audioZoneId) {
        return mAudioZoneIdToUserIdMapping.get(audioZoneId, UserManagerHelper.USER_NULL);
    }

    @GuardedBy("mImplLock")
    private void setUserIdForAudioZoneLocked(@UserIdInt int userId, int audioZoneId) {
        mAudioZoneIdToUserIdMapping.put(audioZoneId, userId);
    }

    @GuardedBy("mImplLock")
    private AudioControlWrapper getAudioControlWrapperLocked() {
        if (mAudioControlWrapper == null) {
            mAudioControlWrapper = AudioControlFactory.newAudioControl();
            mAudioControlWrapper.linkToDeath(this::audioControlDied);
        }
        return mAudioControlWrapper;
    }

    private void resetHalAudioFocus() {
        if (mHalAudioFocus != null) {
            mHalAudioFocus.reset();
            mHalAudioFocus.registerFocusListener();
        }
    }

    private void resetHalAudioGain() {
        if (mCarAudioGainMonitor != null) {
            mCarAudioGainMonitor.reset();
            mCarAudioGainMonitor.registerAudioGainListener(mHalAudioGainCallback);
        }
    }

    private void resetHalAudioModuleChange() {
        if (mCarAudioModuleChangeMonitor != null) {
            mCarAudioModuleChangeMonitor.setModuleChangeCallback(mHalAudioModuleChangeCallback);
        }
    }

    private void handleAudioDeviceGainsChangedLocked(
            List<Integer> halReasons, List<CarAudioGainConfigInfo> gains) {
        mCarAudioGainMonitor.handleAudioDeviceGainsChanged(halReasons, gains);
    }

    private void handleAudioPortsChangedLocked(List<HalAudioDeviceInfo> deviceInfos) {
        mCarAudioModuleChangeMonitor.handleAudioPortsChanged(deviceInfos);
    }

    private void audioControlDied() {
        resetHalAudioFocus();
        resetHalAudioGain();
        resetHalAudioModuleChange();
    }

    boolean isAudioZoneIdValid(int zoneId) {
        synchronized (mImplLock) {
            return mCarAudioZones.contains(zoneId);
        }
    }

    private CarAudioZone getCarAudioZone(int zoneId) {
        synchronized (mImplLock) {
            return getCarAudioZoneLocked(zoneId);
        }
    }

    @GuardedBy("mImplLock")
    private CarAudioZone getCarAudioZoneLocked(int zoneId) {
        checkAudioZoneIdLocked(zoneId);
        return mCarAudioZones.get(zoneId);
    }

    private void checkAudioZoneId(int zoneId) {
        synchronized (mImplLock) {
            checkAudioZoneIdLocked(zoneId);
        }
    }

    @GuardedBy("mImplLock")
    private void checkAudioZoneIdLocked(int zoneId) {
        Preconditions.checkArgument(mCarAudioZones.contains(zoneId),
                "Invalid audio zone Id " + zoneId);
    }

    int getVolumeGroupIdForAudioContext(int zoneId, int suggestedContext) {
        synchronized (mImplLock) {
            return getVolumeGroupIdForAudioContextLocked(zoneId, suggestedContext);
        }
    }

    /**
     * Resets the last selected volume context.
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    public void resetSelectedVolumeContext() {
        enforcePermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        synchronized (mImplLock) {
            mCarVolume.resetSelectedVolumeContext();
            mCarAudioPlaybackCallback.resetStillActiveContexts();
        }
    }

    @VisibleForTesting
    CarAudioContext getCarAudioContext() {
        synchronized (mImplLock) {
            return mCarAudioContext;
        }
    }

    @VisibleForTesting
    void requestAudioFocusForTest(AudioFocusInfo audioFocusInfo, int audioFocusResult) {
        mFocusHandler.onAudioFocusRequest(audioFocusInfo, audioFocusResult);
    }

    int getZoneIdForAudioFocusInfo(AudioFocusInfo focusInfo) {
        if (isAllowedInPrimaryZone(focusInfo)) {
            return PRIMARY_AUDIO_ZONE;
        }

        int audioZoneId;
        synchronized (mImplLock) {
            audioZoneId = getZoneIdForUidLocked(focusInfo.getClientUid());
        }

        if (isAudioZoneMirroringEnabledForZone(audioZoneId)) {
            long requestId = mCarAudioMirrorRequestHandler.getRequestIdForAudioZone(audioZoneId);
            int[] mirrorZones = mCarAudioMirrorRequestHandler.getMirrorAudioZonesForRequest(
                    requestId);
            return ArrayUtils.isEmpty(mirrorZones) ? audioZoneId : mirrorZones[0];
        }

        return audioZoneId;
    }

    private boolean isAllowedInPrimaryZone(AudioFocusInfo focusInfo) {
        boolean isMedia = CarAudioContext.AudioAttributesWrapper.audioAttributeMatches(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                focusInfo.getAttributes());

        return isMedia && mMediaRequestHandler
                .isMediaAudioAllowedInPrimaryZone(mOccupantZoneService
                        .getOccupantZoneForUser(UserHandle
                                .getUserHandleForUid(focusInfo.getClientUid())));
    }

    private boolean isAudioZoneMirroringEnabledForZone(int zoneId) {
        return mCarAudioMirrorRequestHandler.isMirrorEnabledForZone(zoneId);
    }

    private List<AudioAttributes> getAllActiveAttributesForZone(int zoneId) {
        synchronized (mImplLock) {
            return mCarAudioPlaybackCallback.getAllActiveAudioAttributesForZone(zoneId);
        }
    }

    private boolean runInLegacyMode() {
        return !mUseDynamicRouting && !mUseCoreAudioRouting;
    }

    List<CarVolumeGroupInfo> getMutedVolumeGroups(int zoneId) {
        List<CarVolumeGroupInfo> mutedGroups = new ArrayList<>();

        if (!mUseCarVolumeGroupMuting || !isAudioZoneIdValid(zoneId)) {
            return mutedGroups;
        }

        synchronized (mImplLock) {
            int groupCount = getCarAudioZoneLocked(zoneId).getCurrentVolumeGroupCount();
            for (int groupId = 0; groupId < groupCount; groupId++) {
                CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupId);
                if (!group.isMuted()) {
                    continue;
                }

                mutedGroups.add(group.getCarVolumeGroupInfo());
            }
        }

        return mutedGroups;
    }

    List<AudioAttributes> getActiveAudioAttributesForZone(int zoneId) {
        List<AudioAttributes> activeAudioAttributes = new ArrayList<>();
        activeAudioAttributes.addAll(getAllActiveAttributesForZone(zoneId));
        activeAudioAttributes.addAll(getActiveHalAudioAttributesForZone(zoneId));

        return activeAudioAttributes;
    }

    int getVolumeGroupIdForAudioAttribute(int audioZoneId, AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "Audio attributes can not be null");
        checkAudioZoneId(audioZoneId);
        synchronized (mImplLock) {
            return getVolumeGroupIdForAudioAttributeLocked(audioZoneId, attributes);
        }
    }

    void audioDevicesAdded(AudioDeviceInfo[] addedDevices) {
        Slogf.d(TAG, "Added audio devices " + Arrays.toString(addedDevices));
        List<AudioDeviceInfo> devices = filterBusDevices(addedDevices);

        if (devices.isEmpty()) {
            return;
        }

        handleDevicesAdded(devices, EMPTY_LIST);
    }

    private void handleDevicesAdded(List<AudioDeviceInfo> devices, List<Integer> zonesToSkip) {
        List<CarAudioZoneConfigInfo> updatedInfos = new ArrayList<>();
        synchronized (mImplLock) {
            for (int c = 0; c < mCarAudioZones.size(); c++) {
                CarAudioZone zone = mCarAudioZones.valueAt(c);
                if (zonesToSkip.contains(zone.getId())) {
                    continue;
                }
                if (!zone.audioDevicesAdded(devices)) {
                    continue;
                }
                updatedInfos.addAll(zone.getCarAudioZoneConfigInfos());
            }
        }
        mHandler.post(() -> {
            triggerAudioZoneConfigInfosUpdated(new AudioZoneConfigCallbackInfo(updatedInfos,
                    CONFIG_STATUS_CHANGED));
        });
    }

    void audioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        Slogf.d(TAG, "Removed audio devices " + Arrays.toString(removedDevices));
        List<AudioDeviceInfo> devices = filterBusDevices(removedDevices);

        if (devices.isEmpty()) {
            return;
        }

        handleDevicesRemoved(devices, EMPTY_LIST);
    }

    private void handleDevicesRemoved(List<AudioDeviceInfo> devices, List<Integer> zonesToSkip) {
        List<AudioZoneConfigCallbackInfo> callbackInfos = new ArrayList<>();
        List<CarAudioZoneConfigInfo> updatedInfos = new ArrayList<>();
        synchronized (mImplLock) {
            for (int c = 0; c < mCarAudioZones.size(); c++) {
                CarAudioZone zone = mCarAudioZones.valueAt(c);
                if (zonesToSkip.contains(zone.getId())) {
                    continue;
                }
                if (!zone.audioDevicesRemoved(devices)) {
                    continue;
                }
                CarAudioZoneConfigInfo prevConfig =
                        zone.getCurrentCarAudioZoneConfig().getCarAudioZoneConfigInfo();
                if (!prevConfig.isSelected() || prevConfig.isActive()) {
                    // Only update the infos if it is not auto switching
                    // Otherwise let auto switching handle the callback for the config info
                    // change
                    updatedInfos.addAll(zone.getCarAudioZoneConfigInfos());
                    continue;
                }
                // If we are skipping configurations then auto switch to prevent recursion
                if (!zonesToSkip.isEmpty()) {
                    continue;
                }
                // Current config is no longer active, switch back to default and trigger
                // callback with auto switched signal
                CarAudioZoneConfigInfo defaultConfig = zone.getDefaultAudioZoneConfigInfo();
                handleSwitchZoneConfig(defaultConfig);
                CarAudioZoneConfigInfo updatedConfig = getAudioZoneConfigInfo(defaultConfig);
                CarAudioZoneConfigInfo updatedPrevInfo = getAudioZoneConfigInfo(prevConfig);
                callbackInfos.add(new AudioZoneConfigCallbackInfo(
                        List.of(updatedConfig, updatedPrevInfo),
                        CarAudioManager.CONFIG_STATUS_AUTO_SWITCHED));
            }
        }
        callbackInfos.add(new AudioZoneConfigCallbackInfo(updatedInfos, CONFIG_STATUS_CHANGED));
        mHandler.post(() -> {
            for (int c = 0; c < callbackInfos.size(); c++) {
                triggerAudioZoneConfigInfosUpdated(callbackInfos.get(c));
            }
        });
    }

    private void triggerAudioZoneConfigInfosUpdated(AudioZoneConfigCallbackInfo configsInfo) {
        if (configsInfo.mInfos.isEmpty()) {
            return;
        }
        int n = mConfigsCallbacks.beginBroadcast();
        while (n > 0) {
            n--;
            IAudioZoneConfigurationsChangeCallback callback = mConfigsCallbacks.getBroadcastItem(n);
            try {
                callback.onAudioZoneConfigurationsChanged(configsInfo.mInfos, configsInfo.mStatus);
            } catch (RemoteException e) {
                Slogf.e(TAG, "Failed to trigger audio zone config changed callback "
                        + configsInfo.mStatus + " callback[" + n + "] " + callback.asBinder());
            }
        }
        mConfigsCallbacks.finishBroadcast();
    }

    private static List<AudioDeviceInfo> filterBusDevices(AudioDeviceInfo[] infos) {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (int c = 0; c < infos.length; c++) {
            if (infos[c].isSource() || infos[c].getType() == AudioDeviceInfo.TYPE_BUS) {
                continue;
            }
            devices.add(infos[c]);
        }
        return devices;
    }

    static final class SystemClockWrapper {
        public long uptimeMillis() {
            return SystemClock.uptimeMillis();
        }
    }

    void onVolumeGroupEvent(List<CarVolumeGroupEvent> events) {
        for (int index = 0; index < events.size(); index++) {
            CarVolumeGroupEvent event = events.get(index);
            List<CarVolumeGroupInfo> infos = event.getCarVolumeGroupInfos();
            boolean volumeEvent =
                    (event.getEventTypes() & EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED) != 0;
            boolean muteEvent = (event.getEventTypes() & EVENT_TYPE_MUTE_CHANGED) != 0;
            if (!volumeEvent && !muteEvent) {
                continue;
            }
            for (int infoIndex = 0; infoIndex < infos.size(); infoIndex++) {
                CarVolumeGroupInfo info = infos.get(infoIndex);
                int groupId = info.getId();
                int zoneId = info.getZoneId();
                if (volumeEvent) {
                    mCarVolumeCallbackHandler.onVolumeGroupChange(zoneId, groupId, /* flags= */ 0);
                }
                if (muteEvent) {
                    handleMuteChanged(zoneId, groupId, /* flags= */ 0);
                }
            }
        }
        callbackVolumeGroupEvent(events);
    }

    void onAudioVolumeGroupChanged(int zoneId, String groupName, int flags) {
        int callbackFlags = flags;
        synchronized (mImplLock) {
            CarVolumeGroup group = getCarVolumeGroupLocked(zoneId, groupName);
            if (group == null) {
                Slogf.w(TAG, "onAudioVolumeGroupChanged reported on unmanaged group (%s)",
                        groupName);
                return;
            }
            int eventTypes = group.onAudioVolumeGroupChanged(flags);
            if (eventTypes == 0) {
                return;
            }
            if ((eventTypes & EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED) != 0) {
                callbackGroupVolumeChange(zoneId, group.getId(), FLAG_SHOW_UI);
                if (!runInLegacyMode() && !isPlaybackOnVolumeGroupActive(zoneId, group.getId())) {
                    callbackFlags |= FLAG_PLAY_SOUND;
                }
            }
            if ((eventTypes & EVENT_TYPE_MUTE_CHANGED) != 0) {
                handleMuteChanged(zoneId, group.getId(), FLAG_SHOW_UI);
            }
            callbackVolumeGroupEvent(List.of(convertVolumeChangeToEvent(
                    getVolumeGroupInfo(zoneId, group.getId()), callbackFlags, eventTypes)));
        }
    }

    private static final class AudioFocusStackRequest {
        private final AudioFocusStack mStack;
        private final int mOriginalZoneId;

        AudioFocusStackRequest(AudioFocusStack stack, int originalZoneId) {
            mOriginalZoneId = originalZoneId;
            mStack = stack;
        }
    }

    private static final class AudioZoneConfigCallbackInfo {
        private final List<CarAudioZoneConfigInfo> mInfos;
        private final int mStatus;

        AudioZoneConfigCallbackInfo(List<CarAudioZoneConfigInfo> infos, int status) {
            mInfos = infos;
            mStatus = status;
        }
    }
}

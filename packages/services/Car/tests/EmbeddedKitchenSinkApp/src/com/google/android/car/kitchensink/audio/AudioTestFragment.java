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

package com.google.android.car.kitchensink.audio;

import static android.R.layout.simple_spinner_dropdown_item;
import static android.R.layout.simple_spinner_item;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION;
import static android.car.CarAppFocusManager.APP_FOCUS_TYPE_VOICE_COMMAND;
import static android.car.media.CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST;
import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.GET_DEVICES_INPUTS;
import static android.os.Build.IS_EMULATOR;

import static com.google.android.car.kitchensink.R.raw.free_flight;
import static com.google.android.car.kitchensink.R.raw.one2six;
import static com.google.android.car.kitchensink.R.raw.ring_classic_01;
import static com.google.android.car.kitchensink.R.raw.turnright;
import static com.google.android.car.kitchensink.R.raw.well_worth_the_wait;
import static com.google.android.car.kitchensink.audio.AudioPlayer.PLAYER_STATE_COMPLETED;
import static com.google.android.car.kitchensink.audio.AudioUtils.getCurrentZoneId;

import android.Manifest;
import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarAppFocusManager.OnAppFocusChangedListener;
import android.car.CarAppFocusManager.OnAppFocusOwnershipCallback;
import android.car.CarOccupantZoneManager;
import android.car.feature.Flags;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioRouting;
import android.media.HwAudioSource;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.android.internal.util.Preconditions;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.audio.AudioPlayer.PlayStateListener;
import com.google.android.car.kitchensink.bluetooth.BluetoothPermissionChecker;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;

public class AudioTestFragment extends Fragment {
    public static final String FRAGMENT_NAME = "audio";
    private static final String TAG = "CAR.AUDIO.KS";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final long STOP_DELAY_TIME_MS = 3_000;

    // Key for communicating to hall which audio zone has been selected to play
    private static final String AAE_PARAMETER_KEY_FOR_SELECTED_ZONE =
            "com.android.car.emulator.selected_zone";

    private static final Integer[] TONES = new Integer[] { 200, 400, 600, 800, 1_000, 1_200 };
    public static final String DEVICE_SELECTED_NONE = "None";

    private AudioManager mAudioManager;
    private FocusHandler mAudioFocusHandler;

    private AudioPlayer mMusicPlayer;
    @GuardedBy("mLock")
    private AudioPlayer mMusicPlayerWithDelayedFocus;
    private AudioPlayer mMusicPlayerShort;
    private AudioPlayer mNavGuidancePlayer;
    private AudioPlayer mPhoneAudioPlayer;
    private AudioPlayer mVrPlayer;
    private AudioPlayer mSystemPlayer;
    private AudioPlayer mWavPlayer;
    private AudioPlayer mMusicPlayerForSelectedDeviceAddress;
    private HwAudioSource mHwAudioSource;
    private AudioPlayer[] mAllPlayers;

    @GuardedBy("mLock")
    private AudioTrackPlayer mAudioTrackPlayer;

    private Handler mHandler;
    private Context mContext;

    private Car mCar;
    private CarAppFocusManager mAppFocusManager;
    private AudioAttributes mMusicAudioAttrib;
    private AudioAttributes mNavAudioAttrib;
    private AudioAttributes mPhoneAudioAttrib;
    private AudioAttributes mVrAudioAttrib;
    private AudioAttributes mRadioAudioAttrib;
    private AudioAttributes mSystemSoundAudioAttrib;
    private AudioAttributes mMusicAudioAttribForDeviceAddress;
    private CarAudioManager mCarAudioManager;
    private Spinner mZoneSpinner;
    private ArrayAdapter<Integer> mZoneAdapter;
    private Spinner mDeviceAddressSpinner;
    private ArrayAdapter<CarAudioZoneDeviceInfo> mDeviceAddressAdapter;
    private LinearLayout mDeviceAddressLayout;
    private boolean mDeviceAddressAvailable = false;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private AudioFocusRequest mDelayedFocusRequest;
    private OnAudioFocusChangeListener mMediaWithDelayedFocusListener;
    private TextView mDelayedStatusText;
    private TextView mDelayedAudioDeviceText;

    private final OnAudioFocusChangeListener mNavFocusListener = (focusChange) -> {
        Log.i(TAG, "Nav focus change:" + focusChange);
    };
    private final OnAudioFocusChangeListener mVrFocusListener = (focusChange) -> {
        Log.i(TAG, "VR focus change:" + focusChange);
    };
    private final OnAudioFocusChangeListener mRadioFocusListener = (focusChange) -> {
        Log.i(TAG, "Radio focus change:" + focusChange);
    };

    private final CarAppFocusManager.OnAppFocusOwnershipCallback mOwnershipCallbacks =
            new OnAppFocusOwnershipCallback() {
                @Override
                public void onAppFocusOwnershipLost(int focus) {
                }
                @Override
                public void onAppFocusOwnershipGranted(int focus) {
                }
    };

    private final PlayStateListener mNavigationStateListener = (state) -> {
        if (state == PLAYER_STATE_COMPLETED) {
            mAppFocusManager.abandonAppFocus(mOwnershipCallbacks, APP_FOCUS_TYPE_NAVIGATION);
        }
    };

    private VolumeKeyEventsButtonManager mVolumeKeyEventHandler;
    private ZoneConfigSelectionController mZoneConfigController;

    private void connectCar(View view) {
        mContext = getContext();
        mHandler = new Handler(Looper.getMainLooper());
        mCar = Car.createCar(mContext, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mAppFocusManager =
                            (CarAppFocusManager) car.getCarManager(Car.APP_FOCUS_SERVICE);
                    OnAppFocusChangedListener listener = new OnAppFocusChangedListener() {
                        @Override
                        public void onAppFocusChanged(int appType, boolean active) {
                        }
                    };
                    mAppFocusManager.addFocusListener(listener, APP_FOCUS_TYPE_NAVIGATION);
                    mAppFocusManager.addFocusListener(listener, APP_FOCUS_TYPE_VOICE_COMMAND);

                    mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
                    handleBluetoothPermissionIfNeeded();
                    handleSetUpZoneSelection();

                    handleSetUpZoneConfigurationSelection(view);
                });
    }

    private void handleBluetoothPermissionIfNeeded() {
        for (int zoneId : mCarAudioManager.getAudioZoneIds()) {
            List<CarAudioZoneConfigInfo> infos = mCarAudioManager.getAudioZoneConfigInfos(zoneId);
            if (!infos.stream().anyMatch(info-> configHasBluetoothDevice(info))) {
                continue;
            }
            checkBluetoothPermissions();
            return;
        }
        setBluetoothAvailable(true);
    }

    private boolean configHasBluetoothDevice(CarAudioZoneConfigInfo info) {
        return Flags.carAudioDynamicDevices()
                && info.getConfigVolumeGroups().stream()
                .anyMatch(group -> groupHasBluetoothDevice(group));
    }

    private boolean groupHasBluetoothDevice(CarVolumeGroupInfo group) {
        return group.getAudioDeviceAttributes().stream()
                .anyMatch(device-> isBluetoothDevice(device));
    }

    private boolean isBluetoothDevice(AudioDeviceAttributes device) {
        switch (device.getType()) {
            case TYPE_BLUETOOTH_A2DP: // fall through
            case TYPE_BLE_HEADSET: // fall through
            case TYPE_BLE_SPEAKER: // fall through
            case TYPE_BLE_BROADCAST:
                return true;
            default:
                return false;
        }
    }

    private void checkBluetoothPermissions() {
        if (!BluetoothPermissionChecker.isPermissionGranted(getActivity(),
                Manifest.permission.BLUETOOTH_CONNECT)) {
            BluetoothPermissionChecker.requestPermission(Manifest.permission.BLUETOOTH_CONNECT,
                    this, () -> setBluetoothAvailable(true),
                    () -> setBluetoothAvailable(false));
            return;
        }
        setBluetoothAvailable(true);
    }

    private void setBluetoothAvailable(boolean hasPermission) {
        // Since there is an existing BT device available
        // the fragment must rely on having BT permission to find it.
        mDeviceAddressAvailable = hasPermission;
        setUpDeviceAddressPlayer();
    }

    private void handleSetUpZoneConfigurationSelection(View view) {
        Log.e(TAG, "Setup car audio zone config selection view");
        try {
            mZoneConfigController = new ZoneConfigSelectionController(view, mCarAudioManager,
                    mContext, getCurrentZoneId(mContext, mCarAudioManager),
                    (autoSelected) -> updateDeviceAddressPlayer(),
                    /* configUpdatedListener= */ null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup car audio zone config selection view", e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.audio, container, false);
        //Zone Spinner
        setUpZoneSpinnerView(view);

        setUpDeviceAddressLayoutView(view);

        connectCar(view);
        setUpTrackToneSpinnerView(view);
        setUpCarSoundsLayouts(view);
        initializePlayers();

        TextView currentZoneIdTextView = view.findViewById(R.id.activity_current_zone);
        setActivityCurrentZoneId(currentZoneIdTextView);

        mAudioManager = mContext.getSystemService(AudioManager.class);
        mAudioFocusHandler = new FocusHandler(
                view.findViewById(R.id.button_focus_request_selection),
                view.findViewById(R.id.button_audio_focus_request),
                view.findViewById(R.id.text_audio_focus_state));
        view.findViewById(R.id.button_media_play_start).setOnClickListener(v -> {
            boolean requestFocus = true;
            boolean repeat = true;
            mMusicPlayer.start(requestFocus, repeat, AUDIOFOCUS_GAIN);
        });
        view.findViewById(R.id.button_media_play_once).setOnClickListener(v -> {
            mMusicPlayerShort.start(true, false, AUDIOFOCUS_GAIN_TRANSIENT);
            mHandler.postDelayed(() -> mMusicPlayerShort.stop(), STOP_DELAY_TIME_MS);
        });
        view.findViewById(R.id.button_media_play_stop).setOnClickListener(v -> mMusicPlayer.stop());
        view.findViewById(R.id.button_wav_play_start).setOnClickListener(
                v -> mWavPlayer.start(true, true, AUDIOFOCUS_GAIN));
        view.findViewById(R.id.button_wav_play_stop).setOnClickListener(v -> mWavPlayer.stop());
        view.findViewById(R.id.button_nav_play_once).setOnClickListener(v -> {
            if (mAppFocusManager == null) {
                Log.e(TAG, "mAppFocusManager is null");
                return;
            }
            if (DBG) {
                Log.i(TAG, "Nav start");
            }
            mAppFocusManager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, mOwnershipCallbacks);
            if (!mNavGuidancePlayer.isPlaying()) {
                mNavGuidancePlayer.start(true, false,
                        AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, mNavigationStateListener);
            }
        });
        view.findViewById(R.id.button_vr_play_once).setOnClickListener(v -> {
            if (mAppFocusManager == null) {
                Log.e(TAG, "mAppFocusManager is null");
                return;
            }
            if (DBG) {
                Log.i(TAG, "VR start");
            }
            mAppFocusManager.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, mOwnershipCallbacks);
            if (!mVrPlayer.isPlaying()) {
                mVrPlayer.start(true, false,
                        AUDIOFOCUS_GAIN_TRANSIENT, mNavigationStateListener);
            }
        });
        view.findViewById(R.id.button_system_play_once).setOnClickListener(v -> {
            if (DBG) {
                Log.i(TAG, "System start");
            }
            if (!mSystemPlayer.isPlaying()) {
                // system sound played without focus
                mSystemPlayer.start(false, false, 0);
            }
        });
        view.findViewById(R.id.button_nav_start).setOnClickListener(v -> handleNavStart());
        view.findViewById(R.id.button_nav_end).setOnClickListener(v -> handleNavEnd());
        view.findViewById(R.id.button_vr_start).setOnClickListener(v -> handleVrStart());
        view.findViewById(R.id.button_vr_end).setOnClickListener(v -> handleVrEnd());
        view.findViewById(R.id.button_radio_start).setOnClickListener(v -> handleRadioStart());
        view.findViewById(R.id.button_radio_end).setOnClickListener(v -> handleRadioEnd());
        view.findViewById(R.id.button_speaker_phone_on).setOnClickListener(
                v -> mAudioManager.setSpeakerphoneOn(true));
        view.findViewById(R.id.button_speaker_phone_off).setOnClickListener(
                v -> mAudioManager.setSpeakerphoneOn(false));
        view.findViewById(R.id.button_microphone_on).setOnClickListener(
                v -> mAudioManager.setMicrophoneMute(false));
        view.findViewById(R.id.button_microphone_off).setOnClickListener(
                v -> mAudioManager.setMicrophoneMute(true));
        final View hwAudioSourceNotFound = view.findViewById(R.id.hw_audio_source_not_found);
        final View hwAudioSourceStart = view.findViewById(R.id.hw_audio_source_start);
        final View hwAudioSourceStop = view.findViewById(R.id.hw_audio_source_stop);
        if (mHwAudioSource == null) {
            hwAudioSourceNotFound.setVisibility(View.VISIBLE);
            hwAudioSourceStart.setVisibility(View.GONE);
            hwAudioSourceStop.setVisibility(View.GONE);
        } else {
            hwAudioSourceNotFound.setVisibility(View.GONE);
            hwAudioSourceStart.setVisibility(View.VISIBLE);
            hwAudioSourceStop.setVisibility(View.VISIBLE);
            view.findViewById(R.id.hw_audio_source_start).setOnClickListener(
                    v -> handleHwAudioSourceStart());
            view.findViewById(R.id.hw_audio_source_stop).setOnClickListener(
                    v -> handleHwAudioSourceStop());
        }

        // Manage buttons for audio player for device address
        view.findViewById(R.id.button_device_media_play_start).setOnClickListener(v -> {
            startDeviceAudio();
        });
        view.findViewById(R.id.button_device_media_play_once).setOnClickListener(v -> {
            startDeviceAudio();
            mHandler.postDelayed(
                    () -> mMusicPlayerForSelectedDeviceAddress.stop(), STOP_DELAY_TIME_MS);
        });
        view.findViewById(R.id.button_device_media_play_stop)
                .setOnClickListener(v -> mMusicPlayerForSelectedDeviceAddress.stop());

        view.findViewById(R.id.media_delayed_focus_start)
                .setOnClickListener(v -> handleDelayedMediaStart());
        view.findViewById(R.id.media_delayed_focus_stop)
                .setOnClickListener(v -> handleDelayedMediaStop());

        view.findViewById(R.id.phone_audio_focus_start)
                .setOnClickListener(v -> mPhoneAudioPlayer.start(true, true,
                        AUDIOFOCUS_GAIN_TRANSIENT));
        view.findViewById(R.id.phone_audio_focus_stop)
                .setOnClickListener(v -> mPhoneAudioPlayer.stop());

        view.findViewById(R.id.track_audio_start)
                .setOnClickListener(v-> startAudioTrack());
        view.findViewById(R.id.track_audio_stop)
                .setOnClickListener(v -> stopAudioTrack());

        mDelayedStatusText = view.findViewById(R.id.media_delayed_player_status);
        mDelayedAudioDeviceText = view.findViewById(R.id.media_delayed_player_device);
        resetDeviceSelectedForDelayedMedia();

        mVolumeKeyEventHandler = new VolumeKeyEventsButtonManager(
                mCar.getCarManager(CarOccupantZoneManager.class));

        Button upButton = view.findViewById(R.id.volume_plus_key_event_button);
        upButton.setOnClickListener((v) -> mVolumeKeyEventHandler
                .sendClickEvent(KeyEvent.KEYCODE_VOLUME_UP));

        Button downButton = view.findViewById(R.id.volume_minus_key_event_button);
        downButton.setOnClickListener(
                (v) -> mVolumeKeyEventHandler
                        .sendClickEvent(KeyEvent.KEYCODE_VOLUME_DOWN));

        Button muteButton = view.findViewById(R.id.volume_mute_key_event_button);
        muteButton.setOnClickListener(
                (v) -> mVolumeKeyEventHandler
                        .sendClickEvent(KeyEvent.KEYCODE_VOLUME_MUTE));

        return view;
    }

    private void startAudioTrack() {
        synchronized (mLock) {
            mAudioTrackPlayer.start();
        }
    }

    private void stopAudioTrack() {
        synchronized (mLock) {
            mAudioTrackPlayer.stop();
        }
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");
        for (AudioPlayer p : mAllPlayers) {
            p.stop();
        }
        if (mMusicPlayerForSelectedDeviceAddress != null) {
            mMusicPlayerForSelectedDeviceAddress.stop();
            mMusicPlayerForSelectedDeviceAddress = null;
        }
        stopAudioTrack();
        handleHwAudioSourceStop();
        if (mAudioFocusHandler != null) {
            mAudioFocusHandler.release();
            mAudioFocusHandler = null;
        }
        if (mDelayedFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mDelayedFocusRequest);
        }
        if (mAppFocusManager != null) {
            mAppFocusManager.abandonAppFocus(mOwnershipCallbacks);
        }
        mZoneConfigController.release();
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }

        super.onDestroyView();
    }

    private void initializePlayers() {
        mMusicAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_MEDIA)
                .build();
        mNavAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .build();
        mPhoneAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_VOICE_COMMUNICATION)
                .build();
        mVrAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_ASSISTANT)
                .build();
        mRadioAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_MEDIA)
                .build();
        mSystemSoundAudioAttrib = new AudioAttributes.Builder()
                .setUsage(USAGE_ASSISTANCE_SONIFICATION)
                .build();
        // Create an audio device address audio attribute
        mMusicAudioAttribForDeviceAddress = new AudioAttributes.Builder()
                .setUsage(USAGE_MEDIA)
                .build();


        mMusicPlayerForSelectedDeviceAddress = new AudioPlayer(mContext, well_worth_the_wait,
                mMusicAudioAttribForDeviceAddress);
        mMusicPlayer = new AudioPlayer(mContext, well_worth_the_wait, mMusicAudioAttrib);
        mMusicPlayerWithDelayedFocus = new AudioPlayer(mContext, well_worth_the_wait,
                mMusicAudioAttrib, /* preferredDeviceInfo= */ null,
                router -> setRoutedDeviceForDelayedPlayer(router));
        mMusicPlayerShort = new AudioPlayer(mContext, ring_classic_01, mMusicAudioAttrib);
        mNavGuidancePlayer = new AudioPlayer(mContext, turnright, mNavAudioAttrib);
        mPhoneAudioPlayer = new AudioPlayer(mContext, free_flight, mPhoneAudioAttrib);
        mVrPlayer = new AudioPlayer(mContext, one2six, mVrAudioAttrib);
        mSystemPlayer = new AudioPlayer(mContext, ring_classic_01, mSystemSoundAudioAttrib);
        mWavPlayer = new AudioPlayer(mContext, free_flight, mMusicAudioAttrib);
        final AudioDeviceInfo tuner = findTunerDevice(mContext);
        if (tuner != null) {
            mHwAudioSource = new HwAudioSource.Builder()
                    .setAudioAttributes(mMusicAudioAttrib)
                    .setAudioDeviceInfo(findTunerDevice(mContext))
                    .build();
        }
        mAllPlayers = new AudioPlayer[] {
                mMusicPlayer,
                mMusicPlayerShort,
                mNavGuidancePlayer,
                mVrPlayer,
                mSystemPlayer,
                mWavPlayer,
                mMusicPlayerWithDelayedFocus,
                mPhoneAudioPlayer
        };

        mAudioTrackPlayer = new AudioTrackPlayer(getTone(0));
    }

    private void setRoutedDeviceForDelayedPlayer(AudioRouting router) {
        Log.i(TAG, "setRoutedDeviceForDelayedPlayer: " + router);
        String deviceAddress = "Does not exist";
        if (router.getRoutedDevice() != null) {
            deviceAddress = router.getRoutedDevice().getAddress();
        }

        mDelayedAudioDeviceText.setText(getString(R.string.audio_device_selected, deviceAddress));
    }

    private void setActivityCurrentZoneId(TextView currentZoneIdTextView) {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            try {
                int audioZoneId = getCurrentZoneId(mContext, mCarAudioManager);
                currentZoneIdTextView.setText(Integer.toString(audioZoneId));
            } catch (Exception e) {
                Log.e(TAG, "setActivityCurrentZoneId Failed to find name: " , e);
            }
        }
    }

    private void handleDelayedMediaStart() {
        synchronized (mLock) {
            if (mDelayedFocusRequest != null) {
                return;
            }
            mMediaWithDelayedFocusListener = new MediaWithDelayedFocusListener();
            mDelayedFocusRequest = new AudioFocusRequest
                    .Builder(AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mMusicAudioAttrib)
                    .setOnAudioFocusChangeListener(mMediaWithDelayedFocusListener)
                    .setForceDucking(false)
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(true)
                    .build();
            int delayedFocusRequestResults = mAudioManager.requestAudioFocus(mDelayedFocusRequest);
            if (delayedFocusRequestResults == AUDIOFOCUS_REQUEST_GRANTED) {
                startDelayedMediaPlayerLocked();
                return;
            }
            if (delayedFocusRequestResults == AUDIOFOCUS_REQUEST_DELAYED) {
                if (DBG) Log.d(TAG, "Media With Delayed Focus delayed focus granted");
                mDelayedStatusText.setText(R.string.player_delayed);
                resetDeviceSelectedForDelayedMedia();
                return;
            }
            mMediaWithDelayedFocusListener = null;
            mDelayedFocusRequest = null;
            mDelayedStatusText.setText(R.string.player_not_started);
            resetDeviceSelectedForDelayedMedia();
        }
        if (DBG) Log.d(TAG, "Media With Delayed Focus focus rejected");
    }

    private void startDelayedMediaPlayerLocked() {
        if (!mMusicPlayerWithDelayedFocus.isPlaying()) {
            if (DBG) Log.d(TAG, "Media With Delayed Focus starting player");
            mMusicPlayerWithDelayedFocus.start(false, true,
                    AUDIOFOCUS_GAIN);
            mDelayedStatusText.setText(R.string.player_started);
            return;
        }
        if (DBG) Log.d(TAG, "Media With Delayed Focus player already started");
    }

    private void handleDelayedMediaStop() {
        synchronized (mLock) {
            if (mDelayedFocusRequest != null)  {
                int requestResults = mAudioManager.abandonAudioFocusRequest(mDelayedFocusRequest);
                if (DBG) {
                    Log.d(TAG, "Media With Delayed Focus abandon focus " + requestResults);
                }
                mDelayedFocusRequest = null;
                mMediaWithDelayedFocusListener = null;
                stopDelayedMediaPlayerLocked();
            }
        }
    }

    private void stopDelayedMediaPlayerLocked() {
        mDelayedStatusText.setText(R.string.player_not_started);
        resetDeviceSelectedForDelayedMedia();
        if (mMusicPlayerWithDelayedFocus.isPlaying()) {
            if (DBG) Log.d(TAG, "Media With Delayed Focus stopping player");
            mMusicPlayerWithDelayedFocus.stop();
            return;
        }
        if (DBG) Log.d(TAG, "Media With Delayed Focus already stopped");
    }

    private void pauseDelayedMediaPlayerLocked() {
        mDelayedStatusText.setText(R.string.player_paused);
        resetDeviceSelectedForDelayedMedia();
        if (mMusicPlayerWithDelayedFocus.isPlaying()) {
            if (DBG) Log.d(TAG, "Media With Delayed Focus pausing player");
            mMusicPlayerWithDelayedFocus.stop();
            return;
        }
        if (DBG) Log.d(TAG, "Media With Delayed Focus already stopped");
    }

    private void setUpDeviceAddressLayoutView(View view) {
        mDeviceAddressLayout = view.findViewById(R.id.audio_select_device_address_layout);

        mDeviceAddressSpinner = view.findViewById(R.id.device_address_spinner);
        mDeviceAddressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleDeviceAddressSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setUpZoneSpinnerView(View view) {
        mZoneSpinner = view.findViewById(R.id.zone_spinner);
        mZoneSpinner.setEnabled(false);
        mZoneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleZoneSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    public void handleZoneSelection() {
        int position = mZoneSpinner.getSelectedItemPosition();
        int zone = mZoneAdapter.getItem(position);
        if (DBG) {
            Log.d(TAG, "Zone Selected: " + zone);
        }
        if (IS_EMULATOR && zone != PRIMARY_AUDIO_ZONE) {
            setZoneToPlayOnSpeaker(zone);
        }
    }

    private void handleSetUpZoneSelection() {
        if (!IS_EMULATOR || !mCarAudioManager.isAudioFeatureEnabled(
                AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            return;
        }
        mZoneSpinner.setEnabled(true);
        List<Integer> zoneList = mCarAudioManager.getAudioZoneIds();
        Integer[] zoneArray = zoneList.stream()
                .filter(i -> i != PRIMARY_AUDIO_ZONE).toArray(Integer[]::new);
        mZoneAdapter = new ArrayAdapter<>(mContext,
                simple_spinner_item, zoneArray);
        mZoneAdapter.setDropDownViewResource(
                simple_spinner_dropdown_item);
        mZoneSpinner.setAdapter(mZoneAdapter);
        mZoneSpinner.setEnabled(true);
    }

    private void setUpTrackToneSpinnerView(View view) {
        Spinner toneSpinner = view.findViewById(R.id.tone_spinner);
        toneSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                handleTrackToneSelection(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        ArrayAdapter<Integer> toneAdaper = new ArrayAdapter<Integer>(mContext,
                simple_spinner_item, TONES);
        toneAdaper.setDropDownViewResource(
                simple_spinner_dropdown_item);
        toneSpinner.setAdapter(toneAdaper);
    }

    private void handleTrackToneSelection(int pos) {
        boolean isPlaying = false;
        int tone = getTone(pos);
        synchronized (mLock) {
            if (mAudioTrackPlayer.isPlaying()) {
                if (DBG) {
                    Log.d(TAG,
                            "Audio Track currently playing, stopping. Switching to frequency: "
                                    + tone);
                }
                isPlaying = true;
                mAudioTrackPlayer.stop();
            }
            mAudioTrackPlayer = new AudioTrackPlayer(tone);

            if (isPlaying) {
                if (DBG) {
                    Log.d(TAG,
                            "Audio Track was playing, starting again. Switched to frequency: "
                            + tone);
                }
                mAudioTrackPlayer.start();
            }
        }
    }

    private static int getTone(int index) {
        Preconditions.checkArgumentInRange(index, 0, TONES.length, "index");
        return TONES[index];
    }

    private void setUpCarSoundsLayouts(View view) {
        AudioPlayersTabControllers.setUpAudioPlayersTab(view, getChildFragmentManager());
    }

    private void handleNavStart() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "Nav start");
        }
        mAppFocusManager.requestAppFocus(APP_FOCUS_TYPE_NAVIGATION, mOwnershipCallbacks);
        mAudioManager.requestAudioFocus(mNavFocusListener, mNavAudioAttrib,
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, 0);
    }

    private void handleNavEnd() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "Nav end");
        }
        mAudioManager.abandonAudioFocus(mNavFocusListener, mNavAudioAttrib);
        mAppFocusManager.abandonAppFocus(mOwnershipCallbacks, APP_FOCUS_TYPE_NAVIGATION);
    }

    private AudioDeviceInfo findTunerDevice(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = am.getDevices(GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_FM_TUNER) {
                return device;
            }
        }
        return null;
    }

    private void handleHwAudioSourceStart() {
        if (mHwAudioSource != null && !mHwAudioSource.isPlaying()) {
            mHwAudioSource.start();
        }
    }

    private void handleHwAudioSourceStop() {
        if (mHwAudioSource != null) {
            mHwAudioSource.stop();
        }
    }

    private void handleVrStart() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "VR start");
        }
        mAppFocusManager.requestAppFocus(APP_FOCUS_TYPE_VOICE_COMMAND, mOwnershipCallbacks);
        mAudioManager.requestAudioFocus(mVrFocusListener, mVrAudioAttrib,
                AUDIOFOCUS_GAIN_TRANSIENT, 0);
    }

    private void handleVrEnd() {
        if (mAppFocusManager == null) {
            Log.e(TAG, "mAppFocusManager is null");
            return;
        }
        if (DBG) {
            Log.i(TAG, "VR end");
        }
        mAudioManager.abandonAudioFocus(mVrFocusListener, mVrAudioAttrib);
        mAppFocusManager.abandonAppFocus(mOwnershipCallbacks, APP_FOCUS_TYPE_VOICE_COMMAND);
    }

    private void handleRadioStart() {
        if (DBG) {
            Log.i(TAG, "Radio start");
        }
        mAudioManager.requestAudioFocus(mRadioFocusListener, mRadioAudioAttrib, AUDIOFOCUS_GAIN, 0);
    }

    private void handleRadioEnd() {
        if (DBG) {
            Log.i(TAG, "Radio end");
        }
        mAudioManager.abandonAudioFocus(mRadioFocusListener, mRadioAudioAttrib);
    }

    private void setUpDeviceAddressPlayer() {
        if (!mDeviceAddressAvailable
                || !mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            mDeviceAddressLayout.setVisibility(View.GONE);
            return;
        }
        mDeviceAddressLayout.setVisibility(View.VISIBLE);
        updateDeviceAddressPlayer();
    }

    private void updateDeviceAddressPlayer() {
        List<CarAudioZoneDeviceInfo> deviceList = new ArrayList<>();
        for (int audioZoneId: mCarAudioManager.getAudioZoneIds()) {
            AudioDeviceInfo deviceInfo = mCarAudioManager
                    .getOutputDeviceForUsage(audioZoneId, USAGE_MEDIA);
            CarAudioZoneDeviceInfo carAudioZoneDeviceInfo = new CarAudioZoneDeviceInfo();
            carAudioZoneDeviceInfo.mDeviceInfo = deviceInfo;
            carAudioZoneDeviceInfo.mAudioZoneId = audioZoneId;
            deviceList.add(carAudioZoneDeviceInfo);
            if (DBG) {
                Log.d(TAG, "Found device address"
                        + carAudioZoneDeviceInfo.mDeviceInfo.getAddress()
                        + " for audio zone id " + audioZoneId);
            }

        }

        CarAudioZoneDeviceInfo[] deviceArray =
                deviceList.stream().toArray(CarAudioZoneDeviceInfo[]::new);
        mDeviceAddressAdapter = new ArrayAdapter<>(mContext,
                simple_spinner_item, deviceArray);
        mDeviceAddressAdapter.setDropDownViewResource(
                simple_spinner_dropdown_item);
        mDeviceAddressSpinner.setAdapter(mDeviceAddressAdapter);
        handleDeviceAddressSelection();
    }

    private void createDeviceAddressAudioPlayer() {
        CarAudioZoneDeviceInfo carAudioZoneDeviceInfo = mDeviceAddressAdapter.getItem(
                mDeviceAddressSpinner.getSelectedItemPosition());
        Log.d(TAG, "Setting Bundle to zone " + carAudioZoneDeviceInfo.mAudioZoneId);
        Bundle bundle = new Bundle();
        bundle.putInt(AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID, carAudioZoneDeviceInfo.mAudioZoneId);
        mMusicAudioAttribForDeviceAddress = new AudioAttributes.Builder()
                .setUsage(USAGE_MEDIA)
                .addBundle(bundle)
                .build();

        mMusicPlayerForSelectedDeviceAddress = new AudioPlayer(mContext, well_worth_the_wait,
                mMusicAudioAttribForDeviceAddress, carAudioZoneDeviceInfo.mDeviceInfo,
                /* routingListener= */ null);
    }

    private void startDeviceAudio() {
        Log.d(TAG, "Starting device address audio");
        mMusicPlayerForSelectedDeviceAddress.start(true, false,
                AUDIOFOCUS_GAIN_TRANSIENT);
    }

    public void handleDeviceAddressSelection() {
        if (mMusicPlayerForSelectedDeviceAddress != null
                && mMusicPlayerForSelectedDeviceAddress.isPlaying()) {
            mMusicPlayerForSelectedDeviceAddress.stop();
        }
        createDeviceAddressAudioPlayer();
    }

    /**
     * Sets the left speaker to output sound from zoneId
     * @param zoneId zone id to set left speakers output
     * @Note this should only be used with emulator where the zones are separated into right
     * and left speaker, other platforms would have real devices where audio is routed.
     */
    private void setZoneToPlayOnSpeaker(int zoneId) {
        String selectedZoneKeyValueString = AAE_PARAMETER_KEY_FOR_SELECTED_ZONE + "=" + zoneId;
        // send key value  parameter list to audio HAL
        mAudioManager.setParameters(selectedZoneKeyValueString);
        Log.d(TAG, "setZoneToPlayOnSpeaker : " + zoneId);
    }

    private void resetDeviceSelectedForDelayedMedia() {
        mDelayedAudioDeviceText.setText(getString(R.string.audio_device_selected,
                DEVICE_SELECTED_NONE));
    }

    private void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
    }

    private class FocusHandler {
        private static final String AUDIO_FOCUS_STATE_GAIN = "gain";
        private static final String AUDIO_FOCUS_STATE_RELEASED_UNKNOWN = "released / unknown";

        private final RadioGroup mRequestSelection;
        private final TextView mText;
        private final AudioFocusListener mFocusListener;
        private AudioFocusRequest mFocusRequest;

        public FocusHandler(RadioGroup radioGroup, Button requestButton, TextView text) {
            mText = text;
            mRequestSelection = radioGroup;
            mRequestSelection.check(R.id.focus_gain);
            setFocusText(AUDIO_FOCUS_STATE_RELEASED_UNKNOWN);
            mFocusListener = new AudioFocusListener();
            requestButton.setOnClickListener(v -> {
                int selectedButtonId = mRequestSelection.getCheckedRadioButtonId();
                int focusRequest;
                switch (selectedButtonId) {
                    case R.id.focus_gain:
                        focusRequest = AUDIOFOCUS_GAIN;
                        break;
                    case R.id.focus_gain_transient:
                        focusRequest = AUDIOFOCUS_GAIN_TRANSIENT;
                        break;
                    case R.id.focus_gain_transient_duck:
                        focusRequest = AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
                        break;
                    case R.id.focus_gain_transient_exclusive:
                        focusRequest = AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
                        break;
                    case R.id.focus_release:
                    default:
                        abandonAudioFocus();
                        return;
                }
                mFocusRequest = new AudioFocusRequest.Builder(focusRequest)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build())
                        .setOnAudioFocusChangeListener(mFocusListener)
                        .build();
                int ret = mAudioManager.requestAudioFocus(mFocusRequest);
                Log.i(TAG, "requestAudioFocus returned " + ret);
                if (ret == AUDIOFOCUS_REQUEST_GRANTED) {
                    setFocusText(AUDIO_FOCUS_STATE_GAIN);
                }
            });
        }

        public void release() {
            abandonAudioFocus();
        }

        private void abandonAudioFocus() {
            if (DBG) {
                Log.i(TAG, "abandonAudioFocus");
            }
            if (mFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mFocusRequest);
                mFocusRequest = null;
            } else {
                Log.i(TAG, "mFocusRequest is already null");
            }
            setFocusText(AUDIO_FOCUS_STATE_RELEASED_UNKNOWN);
        }

        private void setFocusText(String msg) {
            mText.setText("focus state:" + msg);
        }

        private class AudioFocusListener implements OnAudioFocusChangeListener {
            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.i(TAG, "onAudioFocusChange " + focusChange);
                if (focusChange == AUDIOFOCUS_GAIN) {
                    setFocusText(AUDIO_FOCUS_STATE_GAIN);
                } else if (focusChange == AUDIOFOCUS_LOSS) {
                    setFocusText("loss");
                } else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT) {
                    setFocusText("loss,transient");
                } else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    setFocusText("loss,transient,duck");
                }
            }
        }
    }

    private final class MediaWithDelayedFocusListener implements OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (DBG) Log.d(TAG, "Media With Delayed Focus focus change:" + focusChange);
            synchronized (mLock) {
                switch (focusChange) {
                    case AUDIOFOCUS_GAIN:
                    case AUDIOFOCUS_GAIN_TRANSIENT:
                    case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                    case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        startDelayedMediaPlayerLocked();
                        break;
                    case AUDIOFOCUS_LOSS_TRANSIENT:
                    case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        pauseDelayedMediaPlayerLocked();
                        break;
                    case AUDIOFOCUS_LOSS:
                    default:
                        stopDelayedMediaPlayerLocked();
                        mDelayedFocusRequest = null;
                        mMediaWithDelayedFocusListener = null;
                        break;
                }
            }
        }
    }

    private static final class CarAudioZoneDeviceInfo {
        AudioDeviceInfo mDeviceInfo;
        int mAudioZoneId;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Device Address : ");
            builder.append(mDeviceInfo.getAddress());
            builder.append(", Audio Zone Id: ");
            builder.append(mAudioZoneId);
            return builder.toString();
        }
    }

    static String getAudioLogTag(Class clazz) {
        return TAG + "." + clazz.getSimpleName();
    }
}

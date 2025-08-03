/*
 * Copyright (C) 2010-2011 The Android Open Source Project
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

package com.android.musicfx;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.media.audiofx.Virtualizer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.audiofx.OpenSLESConstants;

import java.util.Formatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class ActivityMusic extends Activity implements OnSeekBarChangeListener {
    private final static String TAG = "MusicFXActivityMusic";

    /**
     * Max number of EQ bands supported
     */
    private final static int EQUALIZER_MAX_BANDS = 32;

    /**
     * Max levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MAX_LEVEL = 1000;

    /**
     * Min levels per EQ band in millibels (1 dB = 100 mB)
     */
    private final static int EQUALIZER_MIN_LEVEL = -1000;

    /**
     * Indicates if Virtualizer effect is supported.
     */
    private boolean mVirtualizerSupported = false;
    private boolean mVirtualizerIsHeadphoneOnly = false;
    /**
     * Indicates if BassBoost effect is supported.
     */
    private boolean mBassBoostSupported = false;
    /**
     * Indicates if Equalizer effect is supported.
     */
    private boolean mEqualizerSupported = false;
    /**
     * Indicates if Preset Reverb effect is supported.
     */
    private boolean mPresetReverbSupported = false;

    // Equalizer fields
    private final SeekBar[] mEqualizerSeekBar = new SeekBar[EQUALIZER_MAX_BANDS];
    private int mNumberEqualizerBands;
    private int mEqualizerMinBandLevel;
    private int mEQPresetUserPos = 1;
    private int mEQPreset;
    private int mEQPresetPrevious;
    private int[] mEQPresetUserBandLevelsPrev;
    private String[] mEQPresetNames;

    private int mPRPreset;
    private int mPRPresetPrevious;

    private boolean mIsHeadsetOn = false;
    private CompoundButton mToggleSwitch;

    private StringBuilder mFormatBuilder = new StringBuilder();
    private Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

    /**
     * Mapping for the EQ widget ids per band
     */
    private static final int[][] EQViewElementIds = {
            { R.id.EQBand0TextView, R.id.EQBand0SeekBar },
            { R.id.EQBand1TextView, R.id.EQBand1SeekBar },
            { R.id.EQBand2TextView, R.id.EQBand2SeekBar },
            { R.id.EQBand3TextView, R.id.EQBand3SeekBar },
            { R.id.EQBand4TextView, R.id.EQBand4SeekBar },
            { R.id.EQBand5TextView, R.id.EQBand5SeekBar },
            { R.id.EQBand6TextView, R.id.EQBand6SeekBar },
            { R.id.EQBand7TextView, R.id.EQBand7SeekBar },
            { R.id.EQBand8TextView, R.id.EQBand8SeekBar },
            { R.id.EQBand9TextView, R.id.EQBand9SeekBar },
            { R.id.EQBand10TextView, R.id.EQBand10SeekBar },
            { R.id.EQBand11TextView, R.id.EQBand11SeekBar },
            { R.id.EQBand12TextView, R.id.EQBand12SeekBar },
            { R.id.EQBand13TextView, R.id.EQBand13SeekBar },
            { R.id.EQBand14TextView, R.id.EQBand14SeekBar },
            { R.id.EQBand15TextView, R.id.EQBand15SeekBar },
            { R.id.EQBand16TextView, R.id.EQBand16SeekBar },
            { R.id.EQBand17TextView, R.id.EQBand17SeekBar },
            { R.id.EQBand18TextView, R.id.EQBand18SeekBar },
            { R.id.EQBand19TextView, R.id.EQBand19SeekBar },
            { R.id.EQBand20TextView, R.id.EQBand20SeekBar },
            { R.id.EQBand21TextView, R.id.EQBand21SeekBar },
            { R.id.EQBand22TextView, R.id.EQBand22SeekBar },
            { R.id.EQBand23TextView, R.id.EQBand23SeekBar },
            { R.id.EQBand24TextView, R.id.EQBand24SeekBar },
            { R.id.EQBand25TextView, R.id.EQBand25SeekBar },
            { R.id.EQBand26TextView, R.id.EQBand26SeekBar },
            { R.id.EQBand27TextView, R.id.EQBand27SeekBar },
            { R.id.EQBand28TextView, R.id.EQBand28SeekBar },
            { R.id.EQBand29TextView, R.id.EQBand29SeekBar },
            { R.id.EQBand30TextView, R.id.EQBand30SeekBar },
            { R.id.EQBand31TextView, R.id.EQBand31SeekBar } };

    // Preset Reverb fields
    /**
     * Array containing the PR preset names.
     */
    private static final String[] PRESETREVERBPRESETSTRINGS = { "None", "SmallRoom", "MediumRoom",
            "LargeRoom", "MediumHall", "LargeHall", "Plate" };

    /**
     * Default localized equalizer preset names. Keep the same as EffectBundle::gEqualizerPresets.
     */
    private static final Map<String, Integer> LOCALIZED_EQUALIZER_PRESET_NAMES = Map.of(
            "Normal", R.string.normal,
            "Classical", R.string.classical,
            "Dance", R.string.dance,
            "Flat", R.string.flat,
            "Folk", R.string.folk,
            "Heavy Metal", R.string.heavy_metal,
            "Hip Hop", R.string.hip_hop,
            "Jazz", R.string.jazz,
            "Pop", R.string.pop,
            "Rock", R.string.rock);

    /**
     * Context field
     */
    private Context mContext;

    /**
     * Calling package name field
     */
    private String mCallingPackageName = "empty";

    // Listen to AudioDeviceCallback to determine if a headset is connected
    private final AudioDeviceCallback mMyAudioDeviceCallback =
            new AudioDeviceCallback() {

                /**
                 * Called by the {@link AudioManager} to indicate that one or more audio devices
                 * have been connected.
                 *
                 * @see AudioDeviceCallback.
                 */
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    // do nothing if mIsHeadsetOn is true and new devices added
                    if (mIsHeadsetOn) {
                        return;
                    }
                    final boolean isHeadsetOn = isHeadSetDeviceConnected();
                    if (isHeadsetOn) {
                        Log.v(TAG, " HeadSet connected");
                        mIsHeadsetOn = true;
                        updateUIHeadset();
                    }
                }

                /**
                 * Called by the {@link AudioManager} to indicate that one or more audio devices
                 * have been disconnected.
                 *
                 * @param removedDevices An array of {@link AudioDeviceInfo} objects corresponding
                 *     to any newly removed audio devices.
                 */
                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    // do nothing if mIsHeadsetOn is false and some devices removed
                    if (!mIsHeadsetOn) {
                        return;
                    }
                    final boolean isHeadsetOn = isHeadSetDeviceConnected();
                    if (!isHeadsetOn) {
                        Log.v(TAG, " HeadSet disconnected");
                        mIsHeadsetOn = false;
                        updateUIHeadset();
                    }
                }
            };

    public static final Set<Integer> HEADSET_DEVICE_TYPES = new HashSet<>();
    static {
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_WIRED_HEADSET);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_USB_HEADSET);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_HEARING_AID);
        HEADSET_DEVICE_TYPES.add(AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    private boolean isHeadSetDeviceConnected() {
        final AudioManager audioManager = getSystemService(AudioManager.class);
        final AudioDeviceInfo[] deviceInfos =
                audioManager.getDevicesStatic(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo deviceInfo : deviceInfos) {
            if (deviceInfo == null) {
                continue;
            }
            final int type = deviceInfo.getType();
            if (HEADSET_DEVICE_TYPES.contains(type)) {
                Log.v(TAG, " at least a HeadSet device type " + type + " connected");
                return true;
            }
        }

        Log.v(TAG, " no HeadSet device type connected");
        return false;
    }

    /*
     * Declares and initializes all objects and widgets in the layouts and the CheckBox and SeekBar
     * onchange methods on creation.
     *
     * (non-Javadoc)
     *
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init context to be used in listeners
        mContext = this;

        // Receive intent
        // get calling intent
        final Intent intent = getIntent();
        final int audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                ControlPanelEffect.AUDIO_SESSION_ID_UNSPECIFIED);
        Log.v(TAG, "audio session: " + audioSession);

        mCallingPackageName = getCallingPackage();

        // check for errors
        if (mCallingPackageName == null) {
            Log.e(TAG, "Package name is null");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        setResult(RESULT_OK);

        Log.v(TAG, mCallingPackageName + " (" + audioSession + ")");

        ControlPanelEffect.initEffectsPreferences(mContext, mCallingPackageName, audioSession);

        // Make sure the package name and the audio session are saved, since MusicFX might be killed
        // after receiving the AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION intent.
        ControlPanelEffect.openSession(mContext, mCallingPackageName, audioSession);

        // query available effects
        final Descriptor[] effects = AudioEffect.queryEffects();

        // Determine available/supported effects
        Log.v(TAG, "Available effects:");
        for (final Descriptor effect : effects) {
            Log.v(TAG, effect.name.toString() + ", type: " + effect.type.toString());

            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
                mVirtualizerIsHeadphoneOnly = !isVirtualizerTransauralSupported();
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                mEqualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_PRESET_REVERB)) {
                mPresetReverbSupported = true;
            }
        }

        setContentView(R.layout.music_main);
        final ViewGroup viewGroup = (ViewGroup) findViewById(R.id.contentSoundEffects);

        // Set accessibility label for bass boost and virtualizer strength seekbars.
        findViewById(R.id.bBStrengthText).setLabelFor(R.id.bBStrengthSeekBar);
        findViewById(R.id.vIStrengthText).setLabelFor(R.id.vIStrengthSeekBar);

        // Fill array with presets from AudioEffects call.
        // allocate a space for 2 extra strings (CI Extreme & User)
        final int numPresets = ControlPanelEffect.getParameterInt(mContext, mCallingPackageName,
                ControlPanelEffect.Key.eq_num_presets);
        mEQPresetNames = new String[numPresets + 2];
        for (short i = 0; i < numPresets; i++) {
            mEQPresetNames[i] = ControlPanelEffect.getParameterString(mContext,
                    mCallingPackageName, ControlPanelEffect.Key.eq_preset_name, i);
            Integer localizedNameId = LOCALIZED_EQUALIZER_PRESET_NAMES.get(mEQPresetNames[i]);
            if (localizedNameId != null) {
                mEQPresetNames[i] = getString(localizedNameId);
            }
        }
        mEQPresetNames[numPresets] = getString(R.string.ci_extreme);
        mEQPresetNames[numPresets + 1] = getString(R.string.user);
        mEQPresetUserPos = numPresets + 1;

        // Watch for button clicks and initialization.
        if ((mVirtualizerSupported) || (mBassBoostSupported) || (mEqualizerSupported)
                || (mPresetReverbSupported)) {
            // Set the listener for the main enhancements toggle button.
            // Depending on the state enable the supported effects if they were
            // checked in the setup tab.
            mToggleSwitch = new Switch(this);
            mToggleSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView,
                        final boolean isChecked) {

                    // set parameter and state
                    ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                            ControlPanelEffect.Key.global_enabled, isChecked);
                    // Enable Linear layout (in scroll layout) view with all
                    // effect contents depending on checked state
                    setEnabledAllChildren(viewGroup, isChecked);
                    // update UI according to headset state
                    updateUIHeadset();
                }
            });

            // Initialize the Virtualizer elements.
            // Set the SeekBar listener.
            if (mVirtualizerSupported) {
                // Show msg when disabled slider (layout) is touched
                findViewById(R.id.vILayout).setOnTouchListener(new OnTouchListener() {

                    @Override
                    public boolean onTouch(final View v, final MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            showHeadsetMsg();
                        }
                        return false;
                    }
                });

                final SeekBar seekbar = (SeekBar) findViewById(R.id.vIStrengthSeekBar);
                seekbar.setMax(OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH
                        - OpenSLESConstants.VIRTUALIZER_MIN_STRENGTH);

                seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    // Update the parameters while SeekBar changes and set the
                    // effect parameter.

                    @Override
                    public void onProgressChanged(final SeekBar seekBar, final int progress,
                            final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCallingPackageName,
                                ControlPanelEffect.Key.virt_strength, progress);
                    }

                    // If slider pos was 0 when starting re-enable effect
                    @Override
                    public void onStartTrackingTouch(final SeekBar seekBar) {
                        if (seekBar.getProgress() == 0) {
                            ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                                    ControlPanelEffect.Key.virt_enabled, true);
                        }
                    }

                    // If slider pos = 0 when stopping disable effect
                    @Override
                    public void onStopTrackingTouch(final SeekBar seekBar) {
                        if (seekBar.getProgress() == 0) {
                            // disable
                            ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                                    ControlPanelEffect.Key.virt_enabled, false);
                        }
                    }
                });

                final Switch sw = (Switch) findViewById(R.id.vIStrengthToggle);
                sw.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView,
                            final boolean isChecked) {
                        ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                                ControlPanelEffect.Key.virt_enabled, isChecked);
                    }
                });
            } else {
                findViewById(R.id.vILayout).setVisibility(View.GONE);
            }

            // Initialize the Bass Boost elements.
            // Set the SeekBar listener.
            if (mBassBoostSupported) {
                // Show msg when disabled slider (layout) is touched
                findViewById(R.id.bBLayout).setOnTouchListener(new OnTouchListener() {

                    @Override
                    public boolean onTouch(final View v, final MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            showHeadsetMsg();
                        }
                        return false;
                    }
                });

                final SeekBar seekbar = (SeekBar) findViewById(R.id.bBStrengthSeekBar);
                seekbar.setMax(OpenSLESConstants.BASSBOOST_MAX_STRENGTH
                        - OpenSLESConstants.BASSBOOST_MIN_STRENGTH);

                seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    // Update the parameters while SeekBar changes and set the
                    // effect parameter.

                    @Override
                    public void onProgressChanged(final SeekBar seekBar, final int progress,
                            final boolean fromUser) {
                        // set parameter and state
                        ControlPanelEffect.setParameterInt(mContext, mCallingPackageName,
                                ControlPanelEffect.Key.bb_strength, progress);
                    }

                    // If slider pos was 0 when starting re-enable effect
                    @Override
                    public void onStartTrackingTouch(final SeekBar seekBar) {
                        if (seekBar.getProgress() == 0) {
                            ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                                    ControlPanelEffect.Key.bb_enabled, true);
                        }
                    }

                    // If slider pos = 0 when stopping disable effect
                    @Override
                    public void onStopTrackingTouch(final SeekBar seekBar) {
                        if (seekBar.getProgress() == 0) {
                            // disable
                            ControlPanelEffect.setParameterBoolean(mContext, mCallingPackageName,
                                    ControlPanelEffect.Key.bb_enabled, false);
                        }
                    }
                });
            } else {
                findViewById(R.id.bBLayout).setVisibility(View.GONE);
            }

            // Initialize the Equalizer elements.
            if (mEqualizerSupported) {
                mEQPreset = ControlPanelEffect.getParameterInt(mContext, mCallingPackageName,
                        ControlPanelEffect.Key.eq_current_preset);
                if (mEQPreset >= mEQPresetNames.length) {
                    mEQPreset = 0;
                }
                mEQPresetPrevious = mEQPreset;
                equalizerSpinnerInit((Spinner)findViewById(R.id.eqSpinner));
                equalizerBandsInit(findViewById(R.id.eqcontainer));
            } else {
                findViewById(R.id.eqSpinner).setVisibility(View.GONE);
            }

            // Initialize the Preset Reverb elements.
            // Set Spinner listeners.
            if (mPresetReverbSupported) {
                mPRPreset = ControlPanelEffect.getParameterInt(mContext, mCallingPackageName,
                        ControlPanelEffect.Key.pr_current_preset);
                mPRPresetPrevious = mPRPreset;
                reverbSpinnerInit((Spinner)findViewById(R.id.prSpinner));
            } else {
                findViewById(R.id.prSpinner).setVisibility(View.GONE);
            }

            ActionBar ab = getActionBar();
            final int padding = getResources().getDimensionPixelSize(
                    R.dimen.action_bar_switch_padding);
            mToggleSwitch.setPadding(0,0, padding, 0);
            ab.setCustomView(mToggleSwitch, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            ab.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        } else {
            viewGroup.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.noEffectsTextView)).setVisibility(View.VISIBLE);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if ((mVirtualizerSupported) || (mBassBoostSupported) || (mEqualizerSupported)
                || (mPresetReverbSupported)) {
            // Monitor AudioDeviceCallback for device change
            final AudioManager audioManager = getSystemService(AudioManager.class);
            audioManager.registerAudioDeviceCallback(mMyAudioDeviceCallback, null);
            mIsHeadsetOn = isHeadSetDeviceConnected();
            Log.v(TAG, "onResume: mIsHeadsetOn : " + mIsHeadsetOn);
            // Update UI
            updateUI();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();

        // Stop monitoring AudioDeviceCallback, (these affect the visible UI, so we only care about
        // them while we're in the foreground).
        if ((mVirtualizerSupported)
                || (mBassBoostSupported)
                || (mEqualizerSupported)
                || (mPresetReverbSupported)) {
            final AudioManager audioManager = getSystemService(AudioManager.class);
            audioManager.unregisterAudioDeviceCallback(mMyAudioDeviceCallback);
        }
    }

    private void reverbSpinnerInit(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, PRESETREVERBPRESETSTRINGS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mPRPresetPrevious) {
                    presetReverbSetPreset(position);
                }
                mPRPresetPrevious = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(mPRPreset);
    }

    private void equalizerSpinnerInit(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, mEQPresetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mEQPresetPrevious) {
                    equalizerSetPreset(position);
                }
                mEQPresetPrevious = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(mEQPreset);
    }


    /**
     * En/disables all children for a given view. For linear and relative layout children do this
     * recursively
     *
     * @param viewGroup
     * @param enabled
     */
    private void setEnabledAllChildren(final ViewGroup viewGroup, final boolean enabled) {
        final int count = viewGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = viewGroup.getChildAt(i);
            if ((view instanceof ViewGroup)) {
                final ViewGroup vg = (ViewGroup) view;
                setEnabledAllChildren(vg, enabled);
            }
            view.setEnabled(enabled);
        }
    }

    /**
     * Updates UI (checkbox, seekbars, enabled states) according to the current stored preferences.
     */
    private void updateUI() {
        final boolean isEnabled = ControlPanelEffect.getParameterBoolean(mContext,
                mCallingPackageName, ControlPanelEffect.Key.global_enabled);
        mToggleSwitch.setChecked(isEnabled);
        setEnabledAllChildren((ViewGroup) findViewById(R.id.contentSoundEffects), isEnabled);
        updateUIHeadset();

        if (mVirtualizerSupported) {
            SeekBar bar = (SeekBar) findViewById(R.id.vIStrengthSeekBar);
            Switch sw = (Switch) findViewById(R.id.vIStrengthToggle);
            int strength = ControlPanelEffect
                    .getParameterInt(mContext, mCallingPackageName,
                            ControlPanelEffect.Key.virt_strength);
            bar.setProgress(strength);
            boolean hasStrength = ControlPanelEffect.getParameterBoolean(mContext,
                    mCallingPackageName, ControlPanelEffect.Key.virt_strength_supported);
            if (hasStrength) {
                sw.setVisibility(View.GONE);
            } else {
                bar.setVisibility(View.GONE);
                sw.setChecked(sw.isEnabled() && strength != 0);
            }
        }
        if (mBassBoostSupported) {
            ((SeekBar) findViewById(R.id.bBStrengthSeekBar)).setProgress(ControlPanelEffect
                    .getParameterInt(mContext, mCallingPackageName,
                            ControlPanelEffect.Key.bb_strength));
        }
        if (mEqualizerSupported) {
            equalizerUpdateDisplay();
        }
        if (mPresetReverbSupported) {
            int reverb = ControlPanelEffect.getParameterInt(
                                    mContext, mCallingPackageName,
                                    ControlPanelEffect.Key.pr_current_preset);
            ((Spinner)findViewById(R.id.prSpinner)).setSelection(reverb);
        }
    }

    /**
     * Updates UI for headset mode. En/disable VI and BB controls depending on headset state
     * (on/off) if effects are on. Do the inverse for their layouts so they can take over
     * control/events.
     */
    private void updateUIHeadset() {
        if (mToggleSwitch.isChecked()) {
            if (mVirtualizerSupported) {
                // virtualizer is on if headset is on or transaural virtualizer supported
                final boolean isVirtualizerOn = mIsHeadsetOn || !mVirtualizerIsHeadphoneOnly;
                ControlPanelEffect.setParameterBoolean(
                        mContext,
                        mCallingPackageName,
                        ControlPanelEffect.Key.virt_enabled,
                        isVirtualizerOn);
                ((TextView) findViewById(R.id.vIStrengthText)).setEnabled(isVirtualizerOn);
                ((SeekBar) findViewById(R.id.vIStrengthSeekBar)).setEnabled(isVirtualizerOn);
                findViewById(R.id.vILayout).setEnabled(!isVirtualizerOn);
            }
            if (mBassBoostSupported) {
                ControlPanelEffect.setParameterBoolean(
                        mContext,
                        mCallingPackageName,
                        ControlPanelEffect.Key.bb_enabled,
                        mIsHeadsetOn);
                ((TextView) findViewById(R.id.bBStrengthText)).setEnabled(mIsHeadsetOn);
                ((SeekBar) findViewById(R.id.bBStrengthSeekBar)).setEnabled(mIsHeadsetOn);
                findViewById(R.id.bBLayout).setEnabled(!mIsHeadsetOn);
            }
        }
    }

    /**
     * Initializes the equalizer elements. Set the SeekBars and Spinner listeners.
     */
    private void equalizerBandsInit(View eqcontainer) {
        // Initialize the N-Band Equalizer elements.
        mNumberEqualizerBands = ControlPanelEffect.getParameterInt(mContext, mCallingPackageName,
                ControlPanelEffect.Key.eq_num_bands);
        mEQPresetUserBandLevelsPrev = ControlPanelEffect.getParameterIntArray(mContext,
                mCallingPackageName, ControlPanelEffect.Key.eq_preset_user_band_level);
        final int[] centerFreqs = ControlPanelEffect.getParameterIntArray(mContext,
                mCallingPackageName, ControlPanelEffect.Key.eq_center_freq);
        final int[] bandLevelRange = ControlPanelEffect.getParameterIntArray(mContext,
                mCallingPackageName, ControlPanelEffect.Key.eq_level_range);
        mEqualizerMinBandLevel = (int) Math.max(EQUALIZER_MIN_LEVEL, bandLevelRange[0]);
        final int mEqualizerMaxBandLevel = (int) Math.min(EQUALIZER_MAX_LEVEL, bandLevelRange[1]);

        for (int band = 0; band < mNumberEqualizerBands; band++) {
            // Unit conversion from mHz to Hz and use k prefix if necessary to display
            final int centerFreq = centerFreqs[band] / 1000;
            float centerFreqHz = centerFreq;
            String unitPrefix = "";
            if (centerFreqHz >= 1000) {
                centerFreqHz = centerFreqHz / 1000;
                unitPrefix = "k";
            }
            ((TextView) eqcontainer.findViewById(EQViewElementIds[band][0])).setText(
                    format("%.0f ", centerFreqHz) + unitPrefix + "Hz");
            mEqualizerSeekBar[band] = (SeekBar) eqcontainer
                    .findViewById(EQViewElementIds[band][1]);
            eqcontainer.findViewById(EQViewElementIds[band][0])
                    .setLabelFor(EQViewElementIds[band][1]);
            mEqualizerSeekBar[band].setMax(mEqualizerMaxBandLevel - mEqualizerMinBandLevel);
            mEqualizerSeekBar[band].setOnSeekBarChangeListener(this);
        }

        // Hide the inactive Equalizer bands.
        for (int band = mNumberEqualizerBands; band < EQUALIZER_MAX_BANDS; band++) {
            // CenterFreq text
            eqcontainer.findViewById(EQViewElementIds[band][0]).setVisibility(View.GONE);
            // SeekBar
            eqcontainer.findViewById(EQViewElementIds[band][1]).setVisibility(View.GONE);
        }

        TextView tv = (TextView) findViewById(R.id.maxLevelText);
        tv.setText(String.format("+%d dB", (int) Math.ceil(mEqualizerMaxBandLevel / 100)));
        tv = (TextView) findViewById(R.id.centerLevelText);
        tv.setText("0 dB");
        tv = (TextView) findViewById(R.id.minLevelText);
        tv.setText(String.format("%d dB", (int) Math.floor(mEqualizerMinBandLevel / 100)));
        equalizerUpdateDisplay();
    }

    private String format(String format, Object... args) {
        mFormatBuilder.setLength(0);
        mFormatter.format(format, args);
        return mFormatBuilder.toString();
    }

    /*
     * For the EQ Band SeekBars
     *
     * (non-Javadoc)
     *
     * @see android.widget.SeekBar.OnSeekBarChangeListener#onProgressChanged(android
     * .widget.SeekBar, int, boolean)
     */

    @Override
    public void onProgressChanged(final SeekBar seekbar, final int progress, final boolean fromUser) {
        final int id = seekbar.getId();

        for (short band = 0; band < mNumberEqualizerBands; band++) {
            if (id == EQViewElementIds[band][1]) {
                final short level = (short) (progress + mEqualizerMinBandLevel);
                if (fromUser) {
                    equalizerBandUpdate(band, level);
                }
                break;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.widget.SeekBar.OnSeekBarChangeListener#onStartTrackingTouch(android
     * .widget.SeekBar)
     */

    @Override
    public void onStartTrackingTouch(final SeekBar seekbar) {
        // get current levels
        final int[] bandLevels = ControlPanelEffect.getParameterIntArray(mContext,
                mCallingPackageName, ControlPanelEffect.Key.eq_band_level);
        // copy current levels to user preset
        for (short band = 0; band < mNumberEqualizerBands; band++) {
            equalizerBandUpdate(band, bandLevels[band]);
        }
        equalizerSetPreset(mEQPresetUserPos);
        ((Spinner)findViewById(R.id.eqSpinner)).setSelection(mEQPresetUserPos);
    }

    /*
     * Updates the EQ display when the user stops changing.
     *
     * (non-Javadoc)
     *
     * @see android.widget.SeekBar.OnSeekBarChangeListener#onStopTrackingTouch(android
     * .widget.SeekBar)
     */

    @Override
    public void onStopTrackingTouch(final SeekBar seekbar) {
        equalizerUpdateDisplay();
    }

    /**
     * Updates the EQ by getting the parameters.
     */
    private void equalizerUpdateDisplay() {
        // Update and show the active N-Band Equalizer bands.
        final int[] bandLevels = ControlPanelEffect.getParameterIntArray(mContext,
                mCallingPackageName, ControlPanelEffect.Key.eq_band_level);
        for (short band = 0; band < mNumberEqualizerBands; band++) {
            final int level = bandLevels[band];
            final int progress = level - mEqualizerMinBandLevel;
            mEqualizerSeekBar[band].setProgress(progress);
        }
    }

    /**
     * Updates/sets a given EQ band level.
     *
     * @param band
     *            Band id
     * @param level
     *            EQ band level
     */
    private void equalizerBandUpdate(final int band, final int level) {
        ControlPanelEffect.setParameterInt(mContext, mCallingPackageName,
                ControlPanelEffect.Key.eq_band_level, level, band);
    }

    /**
     * Sets the given EQ preset.
     *
     * @param preset
     *            EQ preset id.
     */
    private void equalizerSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCallingPackageName,
                ControlPanelEffect.Key.eq_current_preset, preset);
        equalizerUpdateDisplay();
    }

    /**
     * Sets the given PR preset.
     *
     * @param preset
     *            PR preset id.
     */
    private void presetReverbSetPreset(final int preset) {
        ControlPanelEffect.setParameterInt(mContext, mCallingPackageName,
                ControlPanelEffect.Key.pr_current_preset, preset);
    }

    /**
     * Show msg that headset needs to be plugged.
     */
    private void showHeadsetMsg() {
        final Context context = getApplicationContext();
        final int duration = Toast.LENGTH_SHORT;

        final Toast toast = Toast.makeText(context, getString(R.string.headset_plug), duration);
        toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
        toast.show();
    }

    private static boolean isVirtualizerTransauralSupported() {
        Virtualizer virt = null;
        boolean transauralSupported = false;
        try {
            virt = new Virtualizer(0, android.media.AudioSystem.newAudioSessionId());
            transauralSupported = virt.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO,
                    Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL);
        } catch (Exception e) {
        } finally {
            if (virt != null) {
                virt.release();
            }
        }
        return transauralSupported;
    }
}

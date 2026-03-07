package com.gammaos.joystickled.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.gammaos.joystickled.R;
import com.gammaos.joystickled.ui.ColorWheelView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Manual binding shim to replace Gradle ViewBinding when building with Soong.
 * Field names and IDs mirror activity_main.xml in this project.
 */
public final class ActivityMainBinding {
    private final View root;

    // Core views
    public final ColorWheelView colorWheel;
    public final MaterialCardView previewColor;
    public final TextView previewLabel;

    // Control Targets section
    public final TextView labelControlTargets;
    public final ViewGroup layoutControlTargets;
    public final MaterialButton btnLeft;
    public final MaterialButton btnRight;
    public final MaterialButton btnBoth;

    // Split toggle
    public final TextView labelSplitColors;
    public final SwitchCompat switchEnable;

    // Effects section
    public final TextView labelCustomEffects;
    public final MaterialButton btnEffectNone;
    public final MaterialButton btnEffectOff;
    public final MaterialButton btnEffectFollow;
    public final MaterialButton btnEffect1;
    public final MaterialButton btnEffect2;
    public final MaterialButton btnEffect3;
    public final MaterialButton btnEffect4;
    public final MaterialButton btnEffect5;

    // Preset colors
    public final MaterialCardView presetWhite, presetYellow, presetOrange, presetPink, presetRed,
            presetBlue, presetGreen, presetPurple, presetCyan, presetMagenta, presetAmber, presetTeal;

    // Sliders
    public final TextView labelBrightness;
    public final SeekBar brightnessSlider;
    public final TextView brightnessValue;

    public final TextView labelSaturation;
    public final SeekBar saturationSlider;
    public final TextView saturationValue;
 
    public final TextView labelEffectSpeed;
    public final SeekBar effectSpeedSlider;
    public final TextView effectSpeedValue;
    public final View layoutEffectSpeed;

    // Follow brightness toggle
    public final TextView labelFollowBrightness;
    public final SwitchCompat switchFollowBrightness;

    // LED brightness (static, when follow brightness is off)
    public final TextView labelLedBrightness;
    public final View layoutLedBrightness;
    public final SeekBar ledBrightnessSlider;
    public final TextView ledBrightnessValue;

    private ActivityMainBinding(View root) {
        this.root = root;

        // Core
        colorWheel   = root.findViewById(R.id.color_wheel);
        previewColor = root.findViewById(R.id.preview_color);
        previewLabel = root.findViewById(R.id.preview_label);

        // Control targets
        labelControlTargets   = root.findViewById(R.id.label_control_targets);
        layoutControlTargets  = root.findViewById(R.id.layout_control_targets);
        btnLeft  = root.findViewById(R.id.btn_left);
        btnRight = root.findViewById(R.id.btn_right);
        btnBoth  = root.findViewById(R.id.btn_both);

        // Split toggle
        labelSplitColors = root.findViewById(R.id.label_split_colors);
        switchEnable     = root.findViewById(R.id.switch_enable);

        // Effects
        labelCustomEffects = root.findViewById(R.id.label_custom_effects);
        btnEffectNone   = root.findViewById(R.id.btn_effect_none);
        btnEffectOff    = root.findViewById(R.id.btn_effect_off);
        btnEffectFollow = root.findViewById(R.id.btn_effect_follow);
        // IDs are btn_effect1..btn_effect5 (no underscores)
        btnEffect1 = root.findViewById(R.id.btn_effect1);
        btnEffect2 = root.findViewById(R.id.btn_effect2);
        btnEffect3 = root.findViewById(R.id.btn_effect3);
        btnEffect4 = root.findViewById(R.id.btn_effect4);
        btnEffect5 = root.findViewById(R.id.btn_effect5);

        // Presets
        presetWhite   = root.findViewById(R.id.preset_white);
        presetYellow  = root.findViewById(R.id.preset_yellow);
        presetOrange  = root.findViewById(R.id.preset_orange);
        presetPink    = root.findViewById(R.id.preset_pink);
        presetRed     = root.findViewById(R.id.preset_red);
        presetBlue    = root.findViewById(R.id.preset_blue);
        presetGreen   = root.findViewById(R.id.preset_green);
        presetPurple  = root.findViewById(R.id.preset_purple);
        presetCyan    = root.findViewById(R.id.preset_cyan);
        presetMagenta = root.findViewById(R.id.preset_magenta);
        presetAmber   = root.findViewById(R.id.preset_amber);
        presetTeal    = root.findViewById(R.id.preset_teal);

        // Sliders
        labelBrightness  = root.findViewById(R.id.label_brightness);
        brightnessSlider = root.findViewById(R.id.brightness_slider);
        brightnessValue  = root.findViewById(R.id.brightness_value);

        labelSaturation  = root.findViewById(R.id.label_saturation);
        saturationSlider = root.findViewById(R.id.saturation_slider);
        saturationValue  = root.findViewById(R.id.saturation_value);
 
        labelEffectSpeed  = root.findViewById(R.id.label_effect_speed);
        effectSpeedSlider = root.findViewById(R.id.effect_speed_slider);
        effectSpeedValue  = root.findViewById(R.id.effect_speed_value);
        layoutEffectSpeed = root.findViewById(R.id.layout_effect_speed);

        // Follow brightness
        labelFollowBrightness  = root.findViewById(R.id.label_follow_brightness);
        switchFollowBrightness = root.findViewById(R.id.switch_follow_brightness);

        // LED brightness
        labelLedBrightness  = root.findViewById(R.id.label_led_brightness);
        layoutLedBrightness = root.findViewById(R.id.layout_led_brightness);
        ledBrightnessSlider = root.findViewById(R.id.led_brightness_slider);
        ledBrightnessValue  = root.findViewById(R.id.led_brightness_value);
    }

    public static ActivityMainBinding inflate(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.activity_main, (ViewGroup) null, false);
        return new ActivityMainBinding(root);
    }

    public View getRoot() {
        return root;
    }
}
package com.gammaos.joystickled;

import android.graphics.Color;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.gammaos.joystickled.databinding.ActivityMainBinding;
import com.gammaos.joystickled.ui.ColorWheelView;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    // Extra references for buttons not in the generated binding
    private MaterialButton btnPrimary;
    private MaterialButton btnFollow;

    // Selected HSV values (hue, saturation, value/brightness)
    private final float[] hsv = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Manually bind Primary / Follow buttons
        btnPrimary = findViewById(R.id.btn_primary);
        btnFollow = findViewById(R.id.btn_follow);

        // 1) Initialise from persist.gammaos.primary.rgb_hex (default #FFFFFF)
        initColorFromSystemProperty();

        setupColorWheel();
        setupColorPresets();
        setupCustomEffects();
        setupTargetButtons();
        setupSliders();
        restoreControlAndEffectSelectionFromProperties();
        updateControlTargetsVisibility();

        // Ensure picker starts at initial HSV
        binding.colorWheel.setHueAndSaturation(hsv[0], hsv[1]);
        updatePreviewFromHsv();

        setupSplitColorsSwitch();
        setupBrightnessFollowSwitch();
    }

    // --- System property helpers / initial color ---------------------------------------------

    private void initColorFromSystemProperty() {
        // Default to white (#FFFFFF) at full brightness
        int defaultColor = Color.WHITE;
        Color.colorToHSV(defaultColor, hsv); // hsv[2] = 1.0f

        String hex = getSystemProperty("persist.gammaos.primary.rgb_hex_custom", "");
        if (hex != null && !hex.trim().isEmpty()) {
            int colorFromProp = parseHexColor(hex);
            Color.colorToHSV(colorFromProp, hsv);
        }
    }

    private String getSystemProperty(String key, String def) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod =
                    spClass.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private void setSystemProperty(String key, String value) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method setMethod =
                    spClass.getMethod("set", String.class, String.class);
            setMethod.invoke(null, key, value);
        } catch (Exception ignored) {
        }
    }

    private boolean getSystemPropertyBool(String key, boolean def) {
        String v = getSystemProperty(key, def ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }


    private int getEffectSpeedFromProperty() {
        // Default to 10 if the property is missing or invalid.
        String raw = getSystemProperty("persist.gammaos.rgb.effect_speed", "10");
        if (raw == null) {
            return 10;
        }
        raw = raw.trim();
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            value = 10;
        }
        if (value < 0 || value > 255) {
            value = 10;
        }
        return value;
    }

    private int parseHexColor(String hex) {
        if (hex == null) return Color.WHITE;
        hex = hex.trim();
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // --- Split colors (left/right) toggle -----------------------------------------------

    private boolean isColorSplitEnabled() {
        return "1".equals(getSystemProperty("persist.gammaos.rgb.color_split", "0"));
    }

    private void setColorSplitEnabled(boolean enabled) {
        setSystemProperty("persist.gammaos.rgb.color_split", enabled ? "1" : "0");
    }

    // --- Follow screen brightness toggle -----------------------------------------------

    private boolean isBrightnessFollowEnabled() {
        // Default to enabled ("1") if the property does not exist
        String v = getSystemProperty("persist.gammaos.rgb.scale_with_brightness", "1");
        return "1".equals(v);
    }

    private void setBrightnessFollowEnabled(boolean enabled) {
        setSystemProperty("persist.gammaos.rgb.scale_with_brightness", enabled ? "1" : "0");
    }

    private void loadColorForCurrentTarget() {
        String hexProp;

        if (isNumberedEffectSelected()) {
            // Effects 1–5 use Primary / Follow channels.
            if (btnFollow != null
                    && btnFollow.getVisibility() == View.VISIBLE
                    && btnFollow.isSelected()) {
                // Follow channel: do NOT fall back to primary. If unset, start from white.
                hexProp = getSystemProperty("persist.gammaos.primary.rgb_hex_follow", "");
                if (hexProp == null || hexProp.trim().isEmpty()) {
                    Color.colorToHSV(Color.WHITE, hsv);
                    binding.colorWheel.setHueAndSaturation(hsv[0], hsv[1]);
                    int b = Math.round(hsv[2] * 100f);
                    binding.brightnessSlider.setProgress(b);
                    binding.brightnessValue.setText(b + "%");
                    int s = Math.round(hsv[1] * 100f);
                    binding.saturationSlider.setProgress(s);
                    binding.saturationValue.setText(s + "%");
                    updatePreviewFromHsv();
                    return;
                }
            } else {
                // Primary channel in numbered-effect mode.
                hexProp = getSystemProperty("persist.gammaos.primary.rgb_hex_custom", "");
            }
        } else if (binding.btnLeft.isSelected()) {
            hexProp = getSystemProperty("persist.gammaos.rgb.left_hex_custom", "");
        } else if (binding.btnRight.isSelected()) {
            hexProp = getSystemProperty("persist.gammaos.rgb.right_hex_custom", "");
        } else {
            hexProp = getSystemProperty("persist.gammaos.primary.rgb_hex_custom", "");
        }

        if (hexProp != null && !hexProp.trim().isEmpty()) {
            int colorFromProp = parseHexColor(hexProp);
            Color.colorToHSV(colorFromProp, hsv);
            // push into UI
            binding.colorWheel.setHueAndSaturation(hsv[0], hsv[1]);
            int b = Math.round(hsv[2] * 100f);
            binding.brightnessSlider.setProgress(b);
            binding.brightnessValue.setText(b + "%");
            int s = Math.round(hsv[1] * 100f);
            binding.saturationSlider.setProgress(s);
            binding.saturationValue.setText(s + "%");
            updatePreviewFromHsv();
        }
    }

    /**
     * Publish the current static colour to the appropriate system property,
     * depending on the active effect and control target.
     */
    private void maybePublishStaticColor(String hex) {
        if (hex == null) return;

        // Effects 1–5: Primary / Follow channels.
        if (isNumberedEffectSelected()) {
            if (btnFollow != null
                    && btnFollow.getVisibility() == View.VISIBLE
                    && btnFollow.isSelected()) {
                // Follow: dedicated follow colour channel.
                setSystemProperty("persist.gammaos.primary.rgb_hex_follow", hex);
            } else {
                // Primary: keep using the custom primary colour and also mirror
                // it into the live primary hex used by the LED service.
                setSystemProperty("persist.gammaos.primary.rgb_hex_custom", hex);
                setSystemProperty("persist.gammaos.primary.rgb_hex", hex);
            }
            return;
        }

        // "Solid" (None) behaviour: respect Both/Left/Right and split mode.
        if (binding.btnEffectNone != null && binding.btnEffectNone.isSelected()) {
            boolean splitSupported = isSplitModeEnabled();
            boolean colorSplit = isColorSplitEnabled();
            if (splitSupported && colorSplit) {
                if (binding.btnLeft.isSelected()) {
                    setSystemProperty("persist.gammaos.rgb.left_hex_custom", hex);
                } else if (binding.btnRight.isSelected()) {
                    setSystemProperty("persist.gammaos.rgb.right_hex_custom", hex);
                }
            } else {
                if (binding.btnBoth.isSelected()) {
                    setSystemProperty("persist.gammaos.primary.rgb_hex_custom", hex);
                }
            }
        }
    }

    private boolean isNumberedEffectSelected() {
        return binding.btnEffect1.isSelected()
                || binding.btnEffect2.isSelected()
                || binding.btnEffect3.isSelected()
                || binding.btnEffect4.isSelected()
                || binding.btnEffect5.isSelected();
    }

    // --- Color wheel & presets --------------------------------------------------------------

    private void setupColorWheel() {
        binding.colorWheel.setOnColorChangeListener(new ColorWheelView.OnColorChangeListener() {
            @Override
            public void onColorChanged(float hue, float saturation) {
                hsv[0] = hue;

                // When a new color is chosen from the wheel, force saturation & brightness to 100%
                hsv[1] = 1f;
                hsv[2] = 1f;

                binding.saturationSlider.setProgress(100);
                binding.brightnessSlider.setProgress(100);
                binding.brightnessValue.setText("100%");
                binding.saturationValue.setText("100%");

                // User manually picked a color.
                // Keep Effects 1–5 selected so they can be colour-configured;
                // otherwise drop back to plain "Solid" behaviour.
                if (!isNumberedEffectSelected()) {
                    clearEffectSelectionAndRestoreControls();
                }
                updatePreviewFromHsv();
            }
        });
    }

    private void setupColorPresets() {
        View[] presetViews = new View[]{
                binding.presetWhite,
                binding.presetYellow,
                binding.presetOrange,
                binding.presetPink,
                binding.presetRed,
                binding.presetBlue,
                binding.presetGreen,
                binding.presetPurple,
                binding.presetCyan,
                binding.presetMagenta,
                binding.presetAmber,
                binding.presetTeal
        };

        int[] presetColors = new int[]{
                Color.WHITE,
                Color.parseColor("#FFF400"), // yellow
                Color.parseColor("#FF8A2A"), // orange
                Color.parseColor("#FF3C8D"), // pink
                Color.parseColor("#FF3737"), // red
                Color.parseColor("#003BFF"), // blue
                Color.parseColor("#71FF35"), // green
                Color.parseColor("#8A2BE2"), // purple
                Color.parseColor("#00BCD4"), // cyan
                Color.parseColor("#FF00FF"), // magenta
                Color.parseColor("#FFC107"), // amber
                Color.parseColor("#009688")  // teal
        };

        for (int i = 0; i < presetViews.length; i++) {
            final int color = presetColors[i];
            final View view = presetViews[i];
            view.setBackgroundTintList(ColorStateList.valueOf(color));
            view.setOnClickListener(v -> {
                Color.colorToHSV(color, hsv);

                // Keep greys/white neutral; otherwise push to full saturation. Always max brightness.
                if (hsv[1] > 0.05f) {
                    hsv[1] = 1f;
                }
                hsv[2] = 1f;

                binding.brightnessSlider.setProgress(100);
                binding.saturationSlider.setProgress(100);
                binding.brightnessValue.setText("100%");
                binding.saturationValue.setText("100%");

                // User selected a preset colour.
                // Keep Effects 1–5 selected so they can be colour-configured;
                // otherwise drop back to plain "Solid" behaviour.
                if (!isNumberedEffectSelected()) {
                    clearEffectSelectionAndRestoreControls();
                }
                updatePreviewFromHsv();
                binding.colorWheel.setHueAndSaturation(hsv[0], hsv[1]);
            });
        }
    }

    // Returns all color preset views so state changes (enable/disable/alpha) apply uniformly
    private View[] allPresetViews() {
        return new View[]{
                binding.presetWhite,
                binding.presetYellow,
                binding.presetOrange,
                binding.presetPink,
                binding.presetRed,
                binding.presetBlue,
                binding.presetGreen,
                binding.presetPurple,
                binding.presetCyan,
                binding.presetMagenta,
                binding.presetAmber,
                binding.presetTeal
        };
    }

    // --- Custom Effects ---------------------------------------------------------------------

    private void setupCustomEffects() {
        MaterialButton[] effectButtons = new MaterialButton[]{
                binding.btnEffectFollow,
                binding.btnEffectNone,
                binding.btnEffectOff,
                binding.btnEffect1,
                binding.btnEffect2,
                binding.btnEffect3,
                binding.btnEffect4,
                binding.btnEffect5
        };

        // Follow Screen RGB, Solid, and Off always visible; Effects 1–5 gated by props
        boolean[] supported = new boolean[]{
                true, // Follow Screen
                true, // Solid (manual / None)
                true, // Off
                getSystemPropertyBool("persist.gammaos.rgb.effect1.supported", false),
                getSystemPropertyBool("persist.gammaos.rgb.effect2.supported", false),
                getSystemPropertyBool("persist.gammaos.rgb.effect3.supported", false),
                getSystemPropertyBool("persist.gammaos.rgb.effect4.supported", false),
                getSystemPropertyBool("persist.gammaos.rgb.effect5.supported", false)
        };

        for (int i = 0; i < effectButtons.length; i++) {
            MaterialButton b = effectButtons[i];
            if (!supported[i]) {
                b.setVisibility(View.GONE);
            } else {
                b.setVisibility(View.VISIBLE);
            }
        }

        View.OnClickListener listener = view -> {
            if (view instanceof MaterialButton) {
                updateEffectSelection((MaterialButton) view);
            }
        };

        for (MaterialButton b : effectButtons) {
            b.setOnClickListener(listener);
        }
    }

    private void updateEffectSelection(MaterialButton selected) {
        MaterialButton[] effectButtons = new MaterialButton[]{
                binding.btnEffectFollow,
                binding.btnEffectNone,
                binding.btnEffectOff,
                binding.btnEffect1,
                binding.btnEffect2,
                binding.btnEffect3,
                binding.btnEffect4,
                binding.btnEffect5
        };

        ColorStateList selectedColor =
                ColorStateList.valueOf(getColor(R.color.colorAccent));
        ColorStateList normalColor =
                ColorStateList.valueOf(getColor(R.color.preset_border));

        for (MaterialButton b : effectButtons) {
            if (b.getVisibility() != View.VISIBLE) continue;
            boolean isSelected = (b == selected);
            b.setSelected(isSelected);
            b.setStrokeWidth(dpToPx(isSelected ? 2f : 1f));
            b.setStrokeColor(isSelected ? selectedColor : normalColor);
        }

        // Persist the currently selected custom effect so we can restore it next time.
        String effectValue = "";
        if (selected == binding.btnEffectFollow) {
            effectValue = "follow";
        } else if (selected == binding.btnEffectNone) {
            effectValue = "none";
        } else if (selected == binding.btnEffect1) {
            effectValue = "1";
        } else if (selected == binding.btnEffect2) {
            effectValue = "2";
        } else if (selected == binding.btnEffect3) {
            effectValue = "3";
        } else if (selected == binding.btnEffect4) {
            effectValue = "4";
        } else if (selected == binding.btnEffect5) {
            effectValue = "5";
        }

        // Update global LED control on/off state:
        if (selected == binding.btnEffectOff) {
            setSystemProperty("persist.gammargb.control", "off");
        } else {
            setSystemProperty("persist.gammargb.control", "on");
        }

        if (!effectValue.isEmpty()) {
            setSystemProperty("persist.gammaos.rgb.effect", effectValue);
        }

        // If entering any of Effects 1–5, force Primary as the active control target
        // and load its value immediately.
        if (selected == binding.btnEffect1
                || selected == binding.btnEffect2
                || selected == binding.btnEffect3
                || selected == binding.btnEffect4
                || selected == binding.btnEffect5) {
            if (btnPrimary != null) {
                updateTargetSelection(btnPrimary);
            }
        }

        // If "None" is selected, immediately publish the current static colour
        // (when Control Target is set to Both).
        if (selected == binding.btnEffectNone) {
            int color = Color.HSVToColor(hsv);
            String hex = String.format("#%06X", (0xFFFFFF & color));
            maybePublishStaticColor(hex);
        }

        applyEffectSelection();
    }

    /**
     * Restore both the LED control on/off state and the last selected custom effect.
     * If persist.gammargb.control is "off", select the Off effect and grey out controls.
     * Otherwise fall back to persist.gammaos.rgb.effect for the last-used effect.
     */
    private void restoreControlAndEffectSelectionFromProperties() {
        String control = getSystemProperty("persist.gammargb.control", "on");
        if ("off".equalsIgnoreCase(control)) {
            // Honour global off state regardless of any stored effect.
            updateEffectSelection(binding.btnEffectOff);
            return;
        }

        // Any non-off state should keep control "on" and restore the stored effect selection.
        setSystemProperty("persist.gammargb.control", "on");
        restoreEffectSelectionFromProperty();
    }

    /**
     * Restore the last selected custom effect from persist.gammaos.rgb.effect.
     */
    private void restoreEffectSelectionFromProperty() {
        String effect = getSystemProperty("persist.gammaos.rgb.effect", "");

        MaterialButton toSelect = null;
        if ("follow".equalsIgnoreCase(effect)) {
            toSelect = binding.btnEffectFollow;
        } else if ("none".equalsIgnoreCase(effect)) {
            toSelect = binding.btnEffectNone;
        } else if ("1".equals(effect)) {
            toSelect = binding.btnEffect1;
        } else if ("2".equals(effect)) {
            toSelect = binding.btnEffect2;
        } else if ("3".equals(effect)) {
            toSelect = binding.btnEffect3;
        } else if ("4".equals(effect)) {
            toSelect = binding.btnEffect4;
        } else if ("5".equals(effect)) {
            toSelect = binding.btnEffect5;
        }

        if (toSelect != null && toSelect.getVisibility() == View.VISIBLE) {
            updateEffectSelection(toSelect);
        }
    }

    private void clearEffectSelectionAndRestoreControls() {
        MaterialButton[] effectButtons = new MaterialButton[]{
                binding.btnEffectFollow,
                binding.btnEffectNone,
                binding.btnEffectOff,
                binding.btnEffect1,
                binding.btnEffect2,
                binding.btnEffect3,
                binding.btnEffect4,
                binding.btnEffect5
        };

        ColorStateList normalColor =
                ColorStateList.valueOf(getColor(R.color.preset_border));

        for (MaterialButton b : effectButtons) {
            b.setSelected(false);
            b.setStrokeWidth(dpToPx(1f));
            b.setStrokeColor(normalColor);
        }

        // When the user starts manually picking a colour, treat that as the "None" effect:
        // we want all custom effects disabled but keep the None button as the active choice
        // so that we continue publishing static colours.
        updateEffectSelection(binding.btnEffectNone);

        updateControlTargetsVisibility();

        binding.saturationSlider.setEnabled(true);
        binding.saturationSlider.setAlpha(1f);
    }

    private void applyEffectSelection() {
        // Off = hard disable of LED control; "Solid" (None) = unlocked manual mode.
        boolean isOff = binding.btnEffectOff.isSelected();
 
        // Swap slider layout depending on whether Effects 1–5 are active.
        updateSliderVisibilityForEffect();

        // Swap Control Targets UI depending on which effect is active.
        updateControlTargetsVisibility();

        // Only "Follow Screen RGB" should lock out manual colour controls.
        boolean hasActiveEffect =
                !isOff && binding.btnEffectFollow.isSelected();

        MaterialButton[] targets = new MaterialButton[]{
                binding.btnBoth, binding.btnLeft, binding.btnRight
        };

        MaterialButton[] effectButtons = new MaterialButton[]{
                binding.btnEffectFollow,
                binding.btnEffectNone,
                binding.btnEffectOff,
                binding.btnEffect1,
                binding.btnEffect2,
                binding.btnEffect3,
                binding.btnEffect4,
                binding.btnEffect5
        };

        if (isOff) {
            // Global OFF: keep LED effect buttons fully interactive so user can switch
            // to another effect (which will turn control back on), but grey out the
            // rest of the controls.
            for (MaterialButton b : effectButtons) {
                b.setEnabled(true);
                b.setAlpha(1f);
            }

            for (MaterialButton t : targets) {
                t.setEnabled(false);
                t.setAlpha(0.3f);
            }

            binding.saturationSlider.setEnabled(false);
            binding.saturationSlider.setAlpha(0.3f);

            binding.brightnessSlider.setEnabled(false);
            binding.brightnessSlider.setAlpha(0.3f);

            binding.colorWheel.setEnabled(false);
            binding.colorWheel.setAlpha(0.3f);

            for (View p : allPresetViews()) {
                p.setEnabled(false);
                p.setAlpha(0.3f);
            }

            return;
        }

        // Not off: ensure all effect buttons are interactable, then gate the rest on hasActiveEffect.
        for (MaterialButton b : effectButtons) {
            b.setEnabled(true);
            b.setAlpha(1f);
        }

        if (hasActiveEffect) {
            // Disable color controls when Follow Screen RGB is active.
            for (MaterialButton t : targets) {
                t.setEnabled(false);
                t.setAlpha(0.3f);
            }

            binding.saturationSlider.setEnabled(false);
            binding.saturationSlider.setAlpha(0.3f);

            binding.brightnessSlider.setEnabled(false);
            binding.brightnessSlider.setAlpha(0.3f);

            binding.colorWheel.setEnabled(false);
            binding.colorWheel.setAlpha(0.3f);

            for (View p : allPresetViews()) {
                p.setEnabled(false);
                p.setAlpha(0.3f);
            }

            // Force picker to white
            Color.colorToHSV(Color.WHITE, hsv);
            binding.colorWheel.setHueAndSaturation(hsv[0], hsv[1]);
            updatePreviewFromHsv();
        } else {

            for (MaterialButton t : targets) {
                t.setEnabled(true);
                t.setAlpha(1f);
            }

            binding.saturationSlider.setEnabled(true);
            binding.saturationSlider.setAlpha(1f);

            binding.brightnessSlider.setEnabled(true);
            binding.brightnessSlider.setAlpha(1f);

            binding.colorWheel.setEnabled(true);
            binding.colorWheel.setAlpha(1f);

            for (View p : allPresetViews()) {
                p.setEnabled(true);
                p.setAlpha(1f);
            }
        }
    }


    /**
     * Swap between colour brightness/saturation sliders and the Effect Speed slider
     * depending on whether a numbered effect (1–5) is active.
     */
    private void updateSliderVisibilityForEffect() {
        boolean numberedEffectActive = isNumberedEffectSelected();

        if (numberedEffectActive) {
            // For Effects 1–5:
            //   • Keep Color Brightness visible (still useful as a global intensity)
            //   • Hide Color Saturation
            //   • Show Effect Speed slider

            // Brightness: keep visible
            binding.labelBrightness.setVisibility(View.VISIBLE);
            binding.brightnessSlider.setVisibility(View.VISIBLE);
            binding.brightnessValue.setVisibility(View.VISIBLE);

            // Saturation: hide for numbered effects
            binding.labelSaturation.setVisibility(View.GONE);
            binding.saturationSlider.setVisibility(View.GONE);
            binding.saturationValue.setVisibility(View.GONE);

            if (binding.labelEffectSpeed != null) {
                binding.labelEffectSpeed.setVisibility(View.VISIBLE);
            }
            if (binding.layoutEffectSpeed != null) {
                binding.layoutEffectSpeed.setVisibility(View.VISIBLE);
            }
            binding.effectSpeedSlider.setVisibility(View.VISIBLE);
            binding.effectSpeedValue.setVisibility(View.VISIBLE);

            // Keep the Effect Speed slider in sync with the backing system property.
            int effectSpeed = getEffectSpeedFromProperty();
            binding.effectSpeedSlider.setMax(255);
            binding.effectSpeedSlider.setProgress(effectSpeed);
            binding.effectSpeedValue.setText(String.valueOf(effectSpeed));
        } else {
            // Default (non 1–5 effects): show both colour sliders and hide Effect Speed.
            binding.labelBrightness.setVisibility(View.VISIBLE);
            binding.brightnessSlider.setVisibility(View.VISIBLE);
            binding.brightnessValue.setVisibility(View.VISIBLE);

            binding.labelSaturation.setVisibility(View.VISIBLE);
            binding.saturationSlider.setVisibility(View.VISIBLE);
            binding.saturationValue.setVisibility(View.VISIBLE);

            if (binding.labelEffectSpeed != null) {
                binding.labelEffectSpeed.setVisibility(View.GONE);
            }
            if (binding.layoutEffectSpeed != null) {
                binding.layoutEffectSpeed.setVisibility(View.GONE);
            }
            binding.effectSpeedSlider.setVisibility(View.GONE);
            binding.effectSpeedValue.setVisibility(View.GONE);
        }
    }

    // --- Split-mode / Control Targets -------------------------------------------------------

    private boolean isSplitModeEnabled() {
        return "1".equals(getSystemProperty("persist.gammaos.rgb.split", "0"));
    }

    // Always show the Control Targets section, but only show Left/Right
    // when persist.gammaos.rgb.split = 1. Follow-brightness is always visible.
    private void updateControlTargetsVisibility() {
        boolean splitSupported = isSplitModeEnabled();
        boolean colorSplit = isColorSplitEnabled();
        boolean numberedEffectActive = isNumberedEffectSelected();

        // Always show section header/container
        binding.labelControlTargets.setVisibility(View.VISIBLE);
        binding.layoutControlTargets.setVisibility(View.VISIBLE);

        // Follow Screen Brightness toggle must ALWAYS be visible, including effects 1–5
        if (binding.labelFollowBrightness != null) {
            binding.labelFollowBrightness.setVisibility(View.VISIBLE);
            binding.labelFollowBrightness.setAlpha(1f);
        }
        binding.switchFollowBrightness.setVisibility(View.VISIBLE);
        binding.switchFollowBrightness.setEnabled(true);
        binding.switchFollowBrightness.setAlpha(1f);

        if (numberedEffectActive) {
            // Effects 1–5: Primary / Follow targets, no Both/Left/Right or split toggle.

            // Hide split-colour controls; they do not apply in this mode.
            if (binding.labelSplitColors != null) {
                binding.labelSplitColors.setVisibility(View.GONE);
            }
            binding.switchEnable.setVisibility(View.GONE);

            // Hide legacy control targets
            binding.btnBoth.setVisibility(View.GONE);
            binding.btnBoth.setEnabled(false);
            binding.btnBoth.setAlpha(0f);

            binding.btnLeft.setVisibility(View.GONE);
            binding.btnLeft.setEnabled(false);
            binding.btnLeft.setAlpha(0f);

            binding.btnRight.setVisibility(View.GONE);
            binding.btnRight.setEnabled(false);
            binding.btnRight.setAlpha(0f);

            // Show Primary / Follow targets
            if (btnPrimary != null) {
                btnPrimary.setVisibility(View.VISIBLE);
                btnPrimary.setEnabled(true);
                btnPrimary.setAlpha(1f);
            }
            if (btnFollow != null) {
                btnFollow.setVisibility(View.VISIBLE);
                btnFollow.setEnabled(true);
                btnFollow.setAlpha(1f);
            }

            // If neither Primary nor Follow selected yet, default to Primary
            if (btnPrimary != null && btnFollow != null
                    && !btnPrimary.isSelected() && !btnFollow.isSelected()) {
                updateTargetSelection(btnPrimary);   // will immediately load Primary value
            }

            return;
        }

        // Normal operation (Follow Screen RGB, Solid, Off): use Both/Left/Right;
        // hide Primary/Follow.

        if (btnPrimary != null) {
            btnPrimary.setVisibility(View.GONE);
            btnPrimary.setEnabled(false);
            btnPrimary.setAlpha(0f);
        }
        if (btnFollow != null) {
            btnFollow.setVisibility(View.GONE);
            btnFollow.setEnabled(false);
            btnFollow.setAlpha(0f);
        }

        // Show/hide "Split colors" toggle+label only if hardware split is supported
        int toggleVis = splitSupported ? View.VISIBLE : View.GONE;
        if (binding.labelSplitColors != null) {
            binding.labelSplitColors.setVisibility(toggleVis);
        }
        binding.switchEnable.setVisibility(toggleVis);

        if (!splitSupported || !colorSplit) {
            // Only Both
            binding.btnBoth.setVisibility(View.VISIBLE);
            binding.btnBoth.setEnabled(true);
            binding.btnBoth.setAlpha(1f);

            binding.btnLeft.setVisibility(View.GONE);
            binding.btnLeft.setEnabled(false);
            binding.btnLeft.setAlpha(0f);

            binding.btnRight.setVisibility(View.GONE);
            binding.btnRight.setEnabled(false);
            binding.btnRight.setAlpha(0f);

            if (!binding.btnBoth.isSelected()) {
                updateTargetSelection(binding.btnBoth);
            }
        } else {
            // Only Left/Right
            binding.btnBoth.setVisibility(View.GONE);
            binding.btnBoth.setEnabled(false);
            binding.btnBoth.setAlpha(0f);

            binding.btnLeft.setVisibility(View.VISIBLE);
            binding.btnLeft.setEnabled(true);
            binding.btnLeft.setAlpha(1f);

            binding.btnRight.setVisibility(View.VISIBLE);
            binding.btnRight.setEnabled(true);
            binding.btnRight.setAlpha(1f);

            if (!binding.btnLeft.isSelected() && !binding.btnRight.isSelected()) {
                updateTargetSelection(binding.btnLeft);
            }
        }
    }

    private void setupTargetButtons() {
        MaterialButton[] targets = new MaterialButton[]{
                binding.btnBoth,
                binding.btnLeft,
                binding.btnRight,
                btnPrimary,
                btnFollow
        };

        View.OnClickListener listener = view -> {
            if (view instanceof MaterialButton) {
                updateTargetSelection((MaterialButton) view);
            }
        };

        for (MaterialButton v : targets) {
            if (v != null) {
                v.setOnClickListener(listener);
            }
        }

        // Default to "Both" selected for normal operation; numbered effects will switch to Primary.
        updateTargetSelection(binding.btnBoth);
    }

    private void updateTargetSelection(MaterialButton selected) {
        MaterialButton[] targets = new MaterialButton[]{
                binding.btnBoth,
                binding.btnLeft,
                binding.btnRight,
                btnPrimary,
                btnFollow
        };

        ColorStateList selectedColor =
                ColorStateList.valueOf(getColor(R.color.colorAccent));
        ColorStateList normalColor =
                ColorStateList.valueOf(getColor(R.color.preset_border));

        for (MaterialButton b : targets) {
            if (b == null || b.getVisibility() != View.VISIBLE) continue;
            boolean isSelected = (b == selected);
            b.setSelected(isSelected);
            b.setStrokeWidth(dpToPx(isSelected ? 2f : 1f));
            b.setStrokeColor(isSelected ? selectedColor : normalColor);
        }
        // Load stored colour according to current target
        loadColorForCurrentTarget();
    }

    // --- Sliders & power --------------------------------------------------------------------

    private void setupSliders() {
        // Brightness
        binding.brightnessSlider.setMax(100);
        int initialBrightness = Math.round(hsv[2] * 100f);
        binding.brightnessSlider.setProgress(initialBrightness);
        binding.brightnessValue.setText(initialBrightness + "%");
        binding.brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[2] = progress / 100f;
                binding.brightnessValue.setText(progress + "%");
                updatePreviewFromHsv();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // Effect speed (0–255) used by Effects 1–5
        int effectSpeed = getEffectSpeedFromProperty();
        binding.effectSpeedSlider.setMax(255);
        binding.effectSpeedSlider.setProgress(effectSpeed);
        binding.effectSpeedValue.setText(String.valueOf(effectSpeed));
        binding.effectSpeedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.effectSpeedValue.setText(String.valueOf(progress));
                setSystemProperty("persist.gammaos.rgb.effect_speed", String.valueOf(progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // Saturation
        binding.saturationSlider.setMax(100);
        int initialSaturation = Math.round(hsv[1] * 100f);
        binding.saturationSlider.setProgress(initialSaturation);
        binding.saturationValue.setText(initialSaturation + "%");
        binding.saturationSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hsv[1] = progress / 100f;
                binding.saturationValue.setText(progress + "%");
                updatePreviewFromHsv();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void setupSplitColorsSwitch() {
        // Hide the toggle entirely if split isn't supported
        boolean splitSupported = isSplitModeEnabled();
        int vis = splitSupported ? View.VISIBLE : View.GONE;
        if (binding.labelSplitColors != null) binding.labelSplitColors.setVisibility(vis);
        binding.switchEnable.setVisibility(vis);

        // Initialise from property (default OFF)
        boolean enabled = isColorSplitEnabled();
        binding.switchEnable.setOnCheckedChangeListener(null);
        binding.switchEnable.setChecked(enabled);

        // Update targets based on initial state
        updateControlTargetsVisibility();

        // Listen for changes and persist to property
        binding.switchEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setColorSplitEnabled(isChecked);
                updateControlTargetsVisibility();
                loadColorForCurrentTarget();
            }
        });
    }

    private void setupBrightnessFollowSwitch() {
        // Always visible: no support gating here
        boolean enabled = isBrightnessFollowEnabled();
        binding.switchFollowBrightness.setOnCheckedChangeListener(null);
        binding.switchFollowBrightness.setChecked(enabled);

        binding.switchFollowBrightness.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        setBrightnessFollowEnabled(isChecked);
                    }
                });
    }

    private void updatePreviewFromHsv() {
        int color = Color.HSVToColor(hsv);
        binding.previewColor.setCardBackgroundColor(color);

        // Update the preview label with the current hex color
        String hex = String.format("#%06X", (0xFFFFFF & color));
        binding.previewLabel.setText("Preview (" + hex + ")");

        // For the "None" effect targeting "Both", or Effects 1–5 in Primary/Follow mode,
        // push the colour out to the appropriate system property.
        maybePublishStaticColor(hex);
    }
}

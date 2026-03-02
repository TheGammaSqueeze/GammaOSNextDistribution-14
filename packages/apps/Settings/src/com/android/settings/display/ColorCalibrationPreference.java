/*
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

package com.android.settings.display;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;

import java.io.IOException;

import lineageos.hardware.LiveDisplayManager;

/**
 * Preference that directly opens a color calibration dialog with RGB and
 * saturation seekbars when clicked.
 */
public class ColorCalibrationPreference extends Preference {

    private static final String SATURATION_PROPERTY = "persist.sys.sf.color_saturation";
    private static final float DEFAULT_SATURATION = 1.0f;

    private static final int[] SEEKBAR_ID = new int[] {
        R.id.color_red_seekbar,
        R.id.color_green_seekbar,
        R.id.color_blue_seekbar,
        R.id.color_saturation_seekbar
    };

    private static final int[] SEEKBAR_VALUE_ID = new int[] {
        R.id.color_red_value,
        R.id.color_green_value,
        R.id.color_blue_value,
        R.id.color_saturation_value
    };

    public ColorCalibrationPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ColorCalibrationPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ColorCalibrationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorCalibrationPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        showCalibrationDialog();
    }

    private void showCalibrationDialog() {
        final Context context = getContext();
        final LiveDisplayManager liveDisplay = LiveDisplayManager.getInstance(context);

        final float[] originalColors = new float[4];
        final float[] currentColors = new float[4];
        final IntervalSeekBar[] seekBars = new IntervalSeekBar[SEEKBAR_ID.length];

        // Get initial values
        float[] colorAdjustment = liveDisplay.getColorAdjustment();
        System.arraycopy(colorAdjustment, 0, originalColors, 0,
                Math.min(colorAdjustment.length, 3));
        String saturationStr = SystemProperties.get(SATURATION_PROPERTY,
                String.valueOf(DEFAULT_SATURATION));
        originalColors[3] = Float.parseFloat(saturationStr);
        System.arraycopy(originalColors, 0, currentColors, 0, 4);

        // Inflate dialog layout
        View view = LayoutInflater.from(context)
                .inflate(R.layout.display_color_calibration, null);

        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            IntervalSeekBar seekBar = view.findViewById(SEEKBAR_ID[i]);
            TextView value = view.findViewById(SEEKBAR_VALUE_ID[i]);

            seekBar.setMinimum(0.1f);
            seekBar.setMaximum(i == 3 ? 2.0f : 1.0f);
            seekBar.setProgressFloat(currentColors[i]);

            int percent = Math.round(100F * currentColors[i]);
            value.setText(String.format("%d%%", percent));

            final int index = i;
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    float fp = ((IntervalSeekBar) sb).getProgressFloat();
                    if (fromUser) {
                        currentColors[index] = Math.min(fp, seekBars[index].getMaximum());
                        applyColors(liveDisplay, currentColors);
                    }
                    int pct = Math.round(100F * fp);
                    value.setText(String.format("%d%%", pct));
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {}

                @Override
                public void onStopTrackingTouch(SeekBar sb) {}
            });

            seekBars[i] = seekBar;
        }

        // Rainbow preview bar
        View rainbow = view.findViewById(R.id.rainbow_scale_picture_color);
        if (rainbow != null) {
            int[] colors = new int[] {
                0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00, 0xFF00FF00,
                0xFF00FFFF, 0xFF0000FF, 0xFF8B00FF, 0xFFFF0000
            };
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, colors);
            gd.setDither(true);
            gd.setCornerRadius(0f);
            rainbow.setBackground(gd);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.color_calibration_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    // Keep current colors
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    // Restore original colors
                    applyColors(liveDisplay, originalColors);
                })
                .setNeutralButton(R.string.color_calibration_reset, null)
                .setOnCancelListener(d -> {
                    // Restore original colors on back press
                    applyColors(liveDisplay, originalColors);
                })
                .create();

        dialog.show();

        // Override neutral button to reset without dismissing
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
            for (int i = 0; i < seekBars.length; i++) {
                seekBars[i].setProgressFloat(1.0f);
                currentColors[i] = 1.0f;
                View seekBarView = view.findViewById(SEEKBAR_VALUE_ID[i]);
                if (seekBarView instanceof TextView) {
                    ((TextView) seekBarView).setText("100%");
                }
            }
            applyColors(liveDisplay, currentColors);
        });
    }

    private static void applyColors(LiveDisplayManager liveDisplay, float[] colors) {
        float[] rgbOnly = {colors[0], colors[1], colors[2]};
        liveDisplay.setColorAdjustment(rgbOnly);
        setSaturationLevel(colors[3]);
    }

    private static void setSaturationLevel(float saturationLevel) {
        SystemProperties.set(SATURATION_PROPERTY, String.valueOf(saturationLevel));
        try {
            Runtime.getRuntime().exec(new String[] {
                "service", "call", "SurfaceFlinger", "1022", "f", String.valueOf(saturationLevel)
            });
        } catch (IOException e) {
            // Ignore
        }
    }
}

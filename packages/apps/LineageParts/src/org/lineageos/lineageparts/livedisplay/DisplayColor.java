/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.livedisplay;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.lineageos.lineageparts.widget.CustomDialogPreference;
import org.lineageos.lineageparts.widget.IntervalSeekBar;
import org.lineageos.lineageparts.R;

import java.io.IOException;

import lineageos.hardware.LiveDisplayManager;
import android.graphics.drawable.GradientDrawable;

/**
 * Special preference type that allows configuration of Color settings
 */
public class DisplayColor extends CustomDialogPreference<AlertDialog> {
    private static final String TAG = "ColorCalibration";
    private static final String SATURATION_PROPERTY = "persist.sys.sf.color_saturation";
    private static final float DEFAULT_SATURATION = 1.0f;

    private final LiveDisplayManager mLiveDisplay;
    private float mSaturationLevel;

    // These arrays include saturation
    private static final int[] SEEKBAR_ID = new int[] {
        R.id.color_red_seekbar,
        R.id.color_green_seekbar,
        R.id.color_blue_seekbar,
        R.id.color_saturation_seekbar // New saturation seekbar ID
    };

    private static final int[] SEEKBAR_VALUE_ID = new int[] {
        R.id.color_red_value,
        R.id.color_green_value,
        R.id.color_blue_value,
        R.id.color_saturation_value // New saturation value ID
    };

    private final ColorSeekBar[] mSeekBars = new ColorSeekBar[SEEKBAR_ID.length];
    private final float[] mCurrentColors = new float[4];
    private final float[] mOriginalColors = new float[4];

    public DisplayColor(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLiveDisplay = LiveDisplayManager.getInstance(context);
        setDialogLayoutResource(R.layout.display_color_calibration);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder, DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);
        builder.setNeutralButton(R.string.reset, null);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.dlg_ok, null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        // Get initial color values and initialize saturation
        float[] colorAdjustment = mLiveDisplay.getColorAdjustment();
        System.arraycopy(colorAdjustment, 0, mOriginalColors, 0, Math.min(colorAdjustment.length, 3));

        // Get saved saturation level, defaulting to 1.0 if not set
        String saturationString = SystemProperties.get(SATURATION_PROPERTY, String.valueOf(DEFAULT_SATURATION));
        mSaturationLevel = Float.parseFloat(saturationString);
        mOriginalColors[3] = mSaturationLevel;
        System.arraycopy(mOriginalColors, 0, mCurrentColors, 0, 4);

        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            IntervalSeekBar seekBar = view.findViewById(SEEKBAR_ID[i]);
            TextView value = view.findViewById(SEEKBAR_VALUE_ID[i]);
            mSeekBars[i] = new ColorSeekBar(seekBar, value, i);

            // Set min and max values, with saturation having a range from 0.1 to 2.0
            mSeekBars[i].mSeekBar.setMinimum(0.1f);
            mSeekBars[i].mSeekBar.setMaximum(i == 3 ? 2.0f : 1.0f);

            mSeekBars[i].mSeekBar.setProgressFloat(mCurrentColors[i]);
            int percent = Math.round(100F * mCurrentColors[i]);
            value.setText(String.format("%d%%", percent));
        }

        // ---- Rainbow preview bar under the grayscale bar ----
        final View rainbow = view.findViewById(R.id.rainbow_scale_picture_color);
        if (rainbow != null) {
            // ROYGBIV + wrap to Red for smooth looping
            final int[] colors = new int[] {
                    0xFFFF0000, // Red
                    0xFFFF7F00, // Orange
                    0xFFFFFF00, // Yellow
                    0xFF00FF00, // Green
                    0xFF00FFFF, // Cyan
                    0xFF0000FF, // Blue
                    0xFF8B00FF, // Violet
                    0xFFFF0000  // Red (wrap)
            };
            final GradientDrawable gd =
                    new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
            gd.setDither(true);
            gd.setCornerRadius(0f);
            rainbow.setBackground(gd);
        }
    }

    @Override
    protected boolean onDismissDialog(AlertDialog dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            for (int i = 0; i < mSeekBars.length; i++) {
                mSeekBars[i].mSeekBar.setProgressFloat(1.0f);
                mCurrentColors[i] = 1.0f;
            }
            updateColors(mCurrentColors);
            return false;
        }
        return true;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        updateColors(positiveResult ? mCurrentColors : mOriginalColors);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.currentColors = mCurrentColors;
        myState.originalColors = mOriginalColors;

        // Restore the old state when the activity or dialog is being paused
        updateColors(mOriginalColors);

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        System.arraycopy(myState.originalColors, 0, mOriginalColors, 0, 4);
        System.arraycopy(myState.currentColors, 0, mCurrentColors, 0, 4);
        for (int i = 0; i < mSeekBars.length; i++) {
            mSeekBars[i].mSeekBar.setProgressFloat(mCurrentColors[i]);
        }
        updateColors(mCurrentColors);
    }

    private static class SavedState extends BaseSavedState {
        float[] originalColors;
        float[] currentColors;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            originalColors = source.createFloatArray();
            currentColors = source.createFloatArray();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloatArray(originalColors);
            dest.writeFloatArray(currentColors);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void updateColors(float[] colors) {
        // Set RGB and saturation values
        float[] rgbOnly = {colors[0], colors[1], colors[2]};
        mLiveDisplay.setColorAdjustment(rgbOnly);
        setSaturationLevel(colors[3]);
    }

    private void setSaturationLevel(float saturationLevel) {
        // Save the saturation level to the system property
        SystemProperties.set(SATURATION_PROPERTY, String.valueOf(saturationLevel));

        // Call the SurfaceFlinger service to apply the saturation level
        try {
            String command = String.format("service call SurfaceFlinger 1022 f %f", saturationLevel);
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ColorSeekBar implements SeekBar.OnSeekBarChangeListener {
        private final int mIndex;
        private final IntervalSeekBar mSeekBar;
        private final TextView mValue;

        public ColorSeekBar(IntervalSeekBar seekBar, TextView value, int index) {
            mSeekBar = seekBar;
            mValue = value;
            mIndex = index;
            mSeekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            IntervalSeekBar isb = (IntervalSeekBar) seekBar;
            float fp = isb.getProgressFloat();
            if (fromUser) {
                mCurrentColors[mIndex] = Math.min(fp, mSeekBar.getMaximum());
                updateColors(mCurrentColors);
            }
            int percent = Math.round(100F * fp);
            mValue.setText(String.format("%d%%", percent));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}

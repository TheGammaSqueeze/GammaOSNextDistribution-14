package com.gammaos.shadercontrol;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    // Shader mode controls
    private RadioGroup radioGroupMode;
    private RadioButton radioNone;
    private RadioButton radioCrt;
    private RadioButton radioLcd3x;

    // CRT controls
    private View groupCrt;
    private SeekBar seekScanPx, seekScanStrength, seekCurv, seekVignette, seekEdgeSoft, seekBlur;
    private TextView valueScanPx, valueScanStrength, valueCurv, valueVignette, valueEdgeSoft, valueBlur;
    private MaterialButton btnResetCrt;

    // LCD3x controls
    private View groupLcd3x;
    private SeekBar seekBrightenScan, seekBrightenLcd, seekGridPxX, seekGridPxY;
    private TextView valueBrightenScan, valueBrightenLcd, valueGridPxX, valueGridPxY;
    private MaterialButton btnResetLcd3x;

    // CRT defaults from GammaCrtSimple.cpp
    private static final float CRT_SCAN_PX_DEFAULT = 4.0f;
    private static final float CRT_SCAN_STRENGTH_DEFAULT = 0.40f;
    private static final float CRT_CURV_DEFAULT = 0.03f;
    private static final float CRT_VIGNETTE_DEFAULT = 0.01f;
    private static final float CRT_EDGE_SOFT_PX_DEFAULT = 4.0f;
    private static final float CRT_BLUR_INTENSITY_DEFAULT = 0.0f;

    // LCD3x defaults from GammaLcd3x.cpp
    private static final float LCD_BRIGHTEN_SCAN_DEFAULT = 4.0f;
    private static final float LCD_BRIGHTEN_LCD_DEFAULT = 4.0f;
    private static final float LCD_GRID_PX_X_DEFAULT = 4.0f;
    private static final float LCD_GRID_PX_Y_DEFAULT = 4.0f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupModeSelector();
        setupCrtControls();
        setupLcd3xControls();
        restoreInitialState();
    }

    private void bindViews() {
        radioGroupMode = findViewById(R.id.radioGroupMode);
        radioNone = findViewById(R.id.radioNone);
        radioCrt = findViewById(R.id.radioCrt);
        radioLcd3x = findViewById(R.id.radioLcd3x);

        groupCrt = findViewById(R.id.groupCrt);
        seekScanPx = findViewById(R.id.seekScanPx);
        seekScanStrength = findViewById(R.id.seekScanStrength);
        seekCurv = findViewById(R.id.seekCurv);
        seekVignette = findViewById(R.id.seekVignette);
        seekEdgeSoft = findViewById(R.id.seekEdgeSoft);
        seekBlur = findViewById(R.id.seekBlur);
        valueScanPx = findViewById(R.id.valueScanPx);
        valueScanStrength = findViewById(R.id.valueScanStrength);
        valueCurv = findViewById(R.id.valueCurv);
        valueVignette = findViewById(R.id.valueVignette);
        valueEdgeSoft = findViewById(R.id.valueEdgeSoft);
        valueBlur = findViewById(R.id.valueBlur);
        btnResetCrt = findViewById(R.id.btnResetCrt);

        groupLcd3x = findViewById(R.id.groupLcd3x);
        seekBrightenScan = findViewById(R.id.seekBrightenScan);
        seekBrightenLcd = findViewById(R.id.seekBrightenLcd);
        seekGridPxX = findViewById(R.id.seekGridPxX);
        seekGridPxY = findViewById(R.id.seekGridPxY);
        valueBrightenScan = findViewById(R.id.valueBrightenScan);
        valueBrightenLcd = findViewById(R.id.valueBrightenLcd);
        valueGridPxX = findViewById(R.id.valueGridPxX);
        valueGridPxY = findViewById(R.id.valueGridPxY);
        btnResetLcd3x = findViewById(R.id.btnResetLcd3x);
    }

    // --- System property helpers --------------------------------------------------------

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

    private float getSystemPropertyFloat(String key, float def) {
        String v = getSystemProperty(key, Float.toString(def));
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void setSystemPropertyFloat(String key, float value) {
        setSystemProperty(key, Float.toString(value));
    }

    // --- Mode selector ------------------------------------------------------------------

    private void setupModeSelector() {
        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioNone) {
                onModeSelectedNone();
            } else if (checkedId == R.id.radioCrt) {
                onModeSelectedCrt();
            } else if (checkedId == R.id.radioLcd3x) {
                onModeSelectedLcd3x();
            }
        });
    }

    private void onModeSelectedNone() {
        // Disable shaders entirely
        setSystemProperty("persist.gammaos.shader.enable", "0");
        // Do not change type here; just hide parameter groups
        updateGroupsVisibility();
    }

    private void onModeSelectedCrt() {
        setSystemProperty("persist.gammaos.shader.enable", "1");
        setSystemProperty("persist.gammaos.shader.type", "crt-simple");
        updateGroupsVisibility();
    }

    private void onModeSelectedLcd3x() {
        setSystemProperty("persist.gammaos.shader.enable", "1");
        setSystemProperty("persist.gammaos.shader.type", "lcd3x");
        updateGroupsVisibility();
    }

    private void updateGroupsVisibility() {
        int checkedId = radioGroupMode.getCheckedRadioButtonId();
        if (checkedId == R.id.radioCrt) {
            groupCrt.setVisibility(View.VISIBLE);
            groupLcd3x.setVisibility(View.GONE);
        } else if (checkedId == R.id.radioLcd3x) {
            groupCrt.setVisibility(View.GONE);
            groupLcd3x.setVisibility(View.VISIBLE);
        } else {
            groupCrt.setVisibility(View.GONE);
            groupLcd3x.setVisibility(View.GONE);
        }
    }

    private void restoreInitialState() {
        boolean shaderOn = getSystemPropertyBool("persist.gammaos.shader.enable", false);
        String type = getSystemProperty("persist.gammaos.shader.type", "crt-simple");

        if (!shaderOn) {
            radioNone.setChecked(true);
        } else if ("lcd3x".equals(type)) {
            radioLcd3x.setChecked(true);
        } else {
            // Default to crt-simple when enabled but unknown type
            radioCrt.setChecked(true);
        }

        // Initialize parameter sliders from current properties
        loadCrtParamsFromProperties();
        loadLcd3xParamsFromProperties();

        updateGroupsVisibility();
    }

    // --- CRT controls -------------------------------------------------------------------

    private void setupCrtControls() {
        // Scan px: range [1, 12] mapped to progress [0, 110] (step 0.1)
        seekScanPx.setMax(110);
        seekScanPx.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + (progress / 10.0f);
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_px", value);
                valueScanPx.setText(String.format(java.util.Locale.US, "%.1f", value));
            }
        });

        // Scan strength: [0,1]
        seekScanStrength.setMax(100);
        seekScanStrength.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_strength", value);
                valueScanStrength.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // Curvature: [0, 0.1]
        seekCurv.setMax(100);
        seekCurv.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 1000.0f; // 0..0.1
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.curv", value);
                valueCurv.setText(String.format(java.util.Locale.US, "%.3f", value));
            }
        });

        // Vignette: [0, 0.4]
        seekVignette.setMax(100);
        seekVignette.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.4f * (progress / 100.0f);
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.vignette", value);
                valueVignette.setText(String.format(java.util.Locale.US, "%.3f", value));
            }
        });

        // Edge soft px: [0, 32]
        seekEdgeSoft.setMax(320);
        seekEdgeSoft.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 10.0f; // 0..32
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.edge_soft_px", value);
                valueEdgeSoft.setText(String.format(java.util.Locale.US, "%.1f", value));
            }
        });

        // Blur intensity: [0,1]
        seekBlur.setMax(100);
        seekBlur.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.crt-simple.blur_intensity", value);
                valueBlur.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        btnResetCrt.setOnClickListener(v -> resetCrtDefaults());
    }

    private void loadCrtParamsFromProperties() {
        float scanPx = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_px", CRT_SCAN_PX_DEFAULT);
        float scanStrength = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_strength", CRT_SCAN_STRENGTH_DEFAULT);
        float curv = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.curv", CRT_CURV_DEFAULT);
        float vignette = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.vignette", CRT_VIGNETTE_DEFAULT);
        float edgeSoft = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.edge_soft_px", CRT_EDGE_SOFT_PX_DEFAULT);
        float blur = getSystemPropertyFloat("persist.gammaos.shader.crt-simple.blur_intensity", CRT_BLUR_INTENSITY_DEFAULT);

        // Clamp to the ranges used in sliders
        if (scanPx < 1.0f) scanPx = 1.0f;
        if (scanPx > 12.0f) scanPx = 12.0f;
        int scanPxProgress = Math.round((scanPx - 1.0f) * 10.0f);
        seekScanPx.setProgress(scanPxProgress);
        valueScanPx.setText(String.format(java.util.Locale.US, "%.1f", scanPx));

        if (scanStrength < 0f) scanStrength = 0f;
        if (scanStrength > 1f) scanStrength = 1f;
        seekScanStrength.setProgress(Math.round(scanStrength * 100f));
        valueScanStrength.setText(String.format(java.util.Locale.US, "%.2f", scanStrength));

        if (curv < 0f) curv = 0f;
        if (curv > 0.1f) curv = 0.1f;
        seekCurv.setProgress(Math.round(curv * 1000f));
        valueCurv.setText(String.format(java.util.Locale.US, "%.3f", curv));

        if (vignette < 0f) vignette = 0f;
        if (vignette > 0.4f) vignette = 0.4f;
        seekVignette.setProgress(Math.round((vignette / 0.4f) * 100f));
        valueVignette.setText(String.format(java.util.Locale.US, "%.3f", vignette));

        if (edgeSoft < 0f) edgeSoft = 0f;
        if (edgeSoft > 32f) edgeSoft = 32f;
        seekEdgeSoft.setProgress(Math.round(edgeSoft * 10f));
        valueEdgeSoft.setText(String.format(java.util.Locale.US, "%.1f", edgeSoft));

        if (blur < 0f) blur = 0f;
        if (blur > 1f) blur = 1f;
        seekBlur.setProgress(Math.round(blur * 100f));
        valueBlur.setText(String.format(java.util.Locale.US, "%.2f", blur));
    }

    private void resetCrtDefaults() {
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_px", CRT_SCAN_PX_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.scan_strength", CRT_SCAN_STRENGTH_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.curv", CRT_CURV_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.vignette", CRT_VIGNETTE_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.edge_soft_px", CRT_EDGE_SOFT_PX_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.crt-simple.blur_intensity", CRT_BLUR_INTENSITY_DEFAULT);
        loadCrtParamsFromProperties();
    }

    // --- LCD3x controls -----------------------------------------------------------------

    private void setupLcd3xControls() {
        // brighten_scanlines: [1,10] mapped to 1.0..10.0
        seekBrightenScan.setMax(900);
        seekBrightenScan.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + (progress / 100.0f);
                setSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_scanlines", value);
                valueBrightenScan.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // brighten_lcd: [1,10]
        seekBrightenLcd.setMax(900);
        seekBrightenLcd.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + (progress / 100.0f);
                setSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_lcd", value);
                valueBrightenLcd.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // grid_px_x: [1, 32]
        seekGridPxX.setMax(310);
        seekGridPxX.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + (progress / 10.0f);
                setSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_x", value);
                valueGridPxX.setText(String.format(java.util.Locale.US, "%.1f", value));
            }
        });

        // grid_px_y: [1, 32]
        seekGridPxY.setMax(310);
        seekGridPxY.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + (progress / 10.0f);
                setSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_y", value);
                valueGridPxY.setText(String.format(java.util.Locale.US, "%.1f", value));
            }
        });

        btnResetLcd3x.setOnClickListener(v -> resetLcd3xDefaults());
    }

    private void loadLcd3xParamsFromProperties() {
        float brightenScan = getSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_scanlines", LCD_BRIGHTEN_SCAN_DEFAULT);
        float brightenLcd = getSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_lcd", LCD_BRIGHTEN_LCD_DEFAULT);
        float gridPxX = getSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_x", LCD_GRID_PX_X_DEFAULT);
        float gridPxY = getSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_y", LCD_GRID_PX_Y_DEFAULT);

        if (brightenScan < 1f) brightenScan = 1f;
        if (brightenScan > 10f) brightenScan = 10f;
        seekBrightenScan.setProgress(Math.round((brightenScan - 1.0f) * 100f));
        valueBrightenScan.setText(String.format(java.util.Locale.US, "%.2f", brightenScan));

        if (brightenLcd < 1f) brightenLcd = 1f;
        if (brightenLcd > 10f) brightenLcd = 10f;
        seekBrightenLcd.setProgress(Math.round((brightenLcd - 1.0f) * 100f));
        valueBrightenLcd.setText(String.format(java.util.Locale.US, "%.2f", brightenLcd));

        if (gridPxX < 1f) gridPxX = 1f;
        if (gridPxX > 32f) gridPxX = 32f;
        seekGridPxX.setProgress(Math.round((gridPxX - 1.0f) * 10f));
        valueGridPxX.setText(String.format(java.util.Locale.US, "%.1f", gridPxX));

        if (gridPxY < 1f) gridPxY = 1f;
        if (gridPxY > 32f) gridPxY = 32f;
        seekGridPxY.setProgress(Math.round((gridPxY - 1.0f) * 10f));
        valueGridPxY.setText(String.format(java.util.Locale.US, "%.1f", gridPxY));
    }

    private void resetLcd3xDefaults() {
        setSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_scanlines", LCD_BRIGHTEN_SCAN_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd3x.brighten_lcd", LCD_BRIGHTEN_LCD_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_x", LCD_GRID_PX_X_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd3x.grid_px_y", LCD_GRID_PX_Y_DEFAULT);
        loadLcd3xParamsFromProperties();
    }

    // Simple helper to avoid implementing unused listener methods everywhere
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}

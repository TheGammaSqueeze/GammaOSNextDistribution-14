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
    private RadioButton radioLcdShader;
    private RadioButton radioBlurFill;

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
 
    // LCD shader controls (lcd-shader)
    private View groupLcdShader;
    private SeekBar seekLcdResponseTime, seekLcdScanStrength, seekLcdSubpixelStrength, seekLcdGapStrength, seekLcdGapPx;
    private TextView valueLcdResponseTime, valueLcdScanStrength, valueLcdSubpixelStrength, valueLcdGapStrength, valueLcdGapPx;
    private MaterialButton btnResetLcdShader;

    // Blur fill controls (blur-fill)
    private View groupBlurFill;
    private SeekBar seekBlurSigma, seekBlurScale, seekBlurEdgePx, seekBlurFeatherPx, seekBlurStrength,
            seekBlurResScale, seekBlurLumaThreshold;
    private TextView valueBlurSigma, valueBlurScale, valueBlurEdgePx, valueBlurFeatherPx, valueBlurStrength,
            valueBlurResScale, valueBlurLumaThreshold;
    private RadioGroup radioGroupBlurOrientation;
    private RadioButton radioBlurOrientAuto, radioBlurOrientVertical, radioBlurOrientHorizontal;
    private MaterialButton btnResetBlurFill;

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
 
    // LCD shader defaults from GammaLcdShader.cpp
    private static final float LCD_RESPONSE_TIME_DEFAULT = 0.0f; // 0..0.78
    private static final float LCD_SCAN_STRENGTH_DEFAULT = 0.20f;
    private static final float LCD_SUBPIXEL_STRENGTH_DEFAULT = 0.40f;
    private static final float LCD_GAP_STRENGTH_DEFAULT = 0.10f;
    private static final float LCD_GAP_PX_DEFAULT = 0.05f; // 0..0.45

    // Blur fill defaults from GammaBlurFill.cpp
    private static final float BLURFILL_SIGMA_DEFAULT = 12.0f;           // 0.1..64
    private static final float BLURFILL_BLUR_SCALE_DEFAULT = 1.0f;       // 0.25..4
    private static final float BLURFILL_EDGE_PX_DEFAULT = 160.0f;        // 1..4096
    private static final float BLURFILL_FEATHER_PX_DEFAULT = 40.0f;      // 0..1024
    private static final float BLURFILL_STRENGTH_DEFAULT = 1.0f;         // 0..1
    private static final float BLURFILL_RES_SCALE_DEFAULT = 0.5f;        // 0.1..1
    private static final float BLURFILL_LUMA_THRESHOLD_DEFAULT = 0.06f;  // 0..0.3
    private static final String BLURFILL_ORIENTATION_DEFAULT = "auto";   // auto|vertical|horizontal

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupModeSelector();
        setupCrtControls();
        setupLcd3xControls();
        setupLcdShaderControls();
        setupBlurFillControls();
        restoreInitialState();
    }

    private void bindViews() {
        radioGroupMode = findViewById(R.id.radioGroupMode);
        radioNone = findViewById(R.id.radioNone);
        radioCrt = findViewById(R.id.radioCrt);
        radioLcd3x = findViewById(R.id.radioLcd3x);
        radioLcdShader = findViewById(R.id.radioLcdShader);
        radioBlurFill = findViewById(R.id.radioBlurFill);

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

        groupLcdShader = findViewById(R.id.groupLcdShader);
        seekLcdResponseTime = findViewById(R.id.seekLcdResponseTime);
        seekLcdScanStrength = findViewById(R.id.seekLcdScanStrength);
        seekLcdSubpixelStrength = findViewById(R.id.seekLcdSubpixelStrength);
        seekLcdGapStrength = findViewById(R.id.seekLcdGapStrength);
        seekLcdGapPx = findViewById(R.id.seekLcdGapPx);
        valueLcdResponseTime = findViewById(R.id.valueLcdResponseTime);
        valueLcdScanStrength = findViewById(R.id.valueLcdScanStrength);
        valueLcdSubpixelStrength = findViewById(R.id.valueLcdSubpixelStrength);
        valueLcdGapStrength = findViewById(R.id.valueLcdGapStrength);
        valueLcdGapPx = findViewById(R.id.valueLcdGapPx);
        btnResetLcdShader = findViewById(R.id.btnResetLcdShader);

        groupBlurFill = findViewById(R.id.groupBlurFill);
        seekBlurSigma = findViewById(R.id.seekBlurSigma);
        seekBlurScale = findViewById(R.id.seekBlurScale);
        seekBlurEdgePx = findViewById(R.id.seekBlurEdgePx);
        seekBlurFeatherPx = findViewById(R.id.seekBlurFeatherPx);
        seekBlurStrength = findViewById(R.id.seekBlurStrength);
        seekBlurResScale = findViewById(R.id.seekBlurResScale);
        seekBlurLumaThreshold = findViewById(R.id.seekBlurLumaThreshold);
        valueBlurSigma = findViewById(R.id.valueBlurSigma);
        valueBlurScale = findViewById(R.id.valueBlurScale);
        valueBlurEdgePx = findViewById(R.id.valueBlurEdgePx);
        valueBlurFeatherPx = findViewById(R.id.valueBlurFeatherPx);
        valueBlurStrength = findViewById(R.id.valueBlurStrength);
        valueBlurResScale = findViewById(R.id.valueBlurResScale);
        valueBlurLumaThreshold = findViewById(R.id.valueBlurLumaThreshold);
        radioGroupBlurOrientation = findViewById(R.id.radioGroupBlurOrientation);
        radioBlurOrientAuto = findViewById(R.id.radioBlurOrientAuto);
        radioBlurOrientVertical = findViewById(R.id.radioBlurOrientVertical);
        radioBlurOrientHorizontal = findViewById(R.id.radioBlurOrientHorizontal);
        btnResetBlurFill = findViewById(R.id.btnResetBlurFill);
    }

    // System property helpers

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

    // Mode selector

    private void setupModeSelector() {
        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioNone) {
                onModeSelectedNone();
            } else if (checkedId == R.id.radioCrt) {
                onModeSelectedCrt();
            } else if (checkedId == R.id.radioLcd3x) {
                onModeSelectedLcd3x();
            } else if (checkedId == R.id.radioLcdShader) {
                onModeSelectedLcdShader();
            } else if (checkedId == R.id.radioBlurFill) {
                onModeSelectedBlurFill();
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
 
    private void onModeSelectedLcdShader() {
        setSystemProperty("persist.gammaos.shader.enable", "1");
        setSystemProperty("persist.gammaos.shader.type", "lcd-shader");
        updateGroupsVisibility();
    }

    private void onModeSelectedBlurFill() {
        setSystemProperty("persist.gammaos.shader.enable", "1");
        setSystemProperty("persist.gammaos.shader.type", "blur-fill");
        updateGroupsVisibility();
    }

    private void updateGroupsVisibility() {
        int checkedId = radioGroupMode.getCheckedRadioButtonId();
        groupCrt.setVisibility(checkedId == R.id.radioCrt ? View.VISIBLE : View.GONE);
        groupLcd3x.setVisibility(checkedId == R.id.radioLcd3x ? View.VISIBLE : View.GONE);
        groupLcdShader.setVisibility(checkedId == R.id.radioLcdShader ? View.VISIBLE : View.GONE);
        groupBlurFill.setVisibility(checkedId == R.id.radioBlurFill ? View.VISIBLE : View.GONE);
    }

    private static boolean isLcdShaderType(String t) {
        return "lcd-shader".equals(t) || "lcdshader".equals(t) || "lcd_shader".equals(t) || "lcd".equals(t);
    }

    private static boolean isBlurFillType(String t) {
        return "blur-fill".equals(t) || "blurfill".equals(t) || "blur_fill".equals(t);
    }

    private void restoreInitialState() {
        boolean shaderOn = getSystemPropertyBool("persist.gammaos.shader.enable", false);
        String type = getSystemProperty("persist.gammaos.shader.type", "crt-simple");

        if (!shaderOn) {
            radioNone.setChecked(true);
        } else if ("lcd3x".equals(type)) {
            radioLcd3x.setChecked(true);
        } else if (isLcdShaderType(type)) {
            radioLcdShader.setChecked(true);
        } else if (isBlurFillType(type)) {
            radioBlurFill.setChecked(true);
        } else {
            // Default to crt-simple when enabled but unknown type
            radioCrt.setChecked(true);
        }

        // Initialize parameter sliders from current properties
        loadCrtParamsFromProperties();
        loadLcd3xParamsFromProperties();
        loadLcdShaderParamsFromProperties();
        loadBlurFillParamsFromProperties();

        updateGroupsVisibility();
    }

    // CRT controls

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

    // LCD3x controls

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

    // LCD shader controls

    private void setupLcdShaderControls() {
        // response_time: [0.00, 0.78] step 0.01
        seekLcdResponseTime.setMax(78);
        seekLcdResponseTime.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.lcd.response_time", value);
                valueLcdResponseTime.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // scan_strength: [0,1]
        seekLcdScanStrength.setMax(100);
        seekLcdScanStrength.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.lcd.scan_strength", value);
                valueLcdScanStrength.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // subpixel_strength: [0,1]
        seekLcdSubpixelStrength.setMax(100);
        seekLcdSubpixelStrength.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.lcd.subpixel_strength", value);
                valueLcdSubpixelStrength.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // gap_strength: [0,1]
        seekLcdGapStrength.setMax(100);
        seekLcdGapStrength.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.lcd.gap_strength", value);
                valueLcdGapStrength.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // gap_px: [0.00, 0.45] step 0.01
        seekLcdGapPx.setMax(45);
        seekLcdGapPx.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.lcd.gap_px", value);
                valueLcdGapPx.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        btnResetLcdShader.setOnClickListener(v -> resetLcdShaderDefaults());
    }

    private void loadLcdShaderParamsFromProperties() {
        float rt = getSystemPropertyFloat("persist.gammaos.shader.lcd.response_time", LCD_RESPONSE_TIME_DEFAULT);
        float scan = getSystemPropertyFloat("persist.gammaos.shader.lcd.scan_strength", LCD_SCAN_STRENGTH_DEFAULT);
        float sub = getSystemPropertyFloat("persist.gammaos.shader.lcd.subpixel_strength", LCD_SUBPIXEL_STRENGTH_DEFAULT);
        float gap = getSystemPropertyFloat("persist.gammaos.shader.lcd.gap_strength", LCD_GAP_STRENGTH_DEFAULT);
        float gapPx = getSystemPropertyFloat("persist.gammaos.shader.lcd.gap_px", LCD_GAP_PX_DEFAULT);

        if (rt < 0f) rt = 0f;
        if (rt > 0.78f) rt = 0.78f;
        seekLcdResponseTime.setProgress(Math.round(rt * 100f));
        valueLcdResponseTime.setText(String.format(java.util.Locale.US, "%.2f", rt));

        if (scan < 0f) scan = 0f;
        if (scan > 1f) scan = 1f;
        seekLcdScanStrength.setProgress(Math.round(scan * 100f));
        valueLcdScanStrength.setText(String.format(java.util.Locale.US, "%.2f", scan));

        if (sub < 0f) sub = 0f;
        if (sub > 1f) sub = 1f;
        seekLcdSubpixelStrength.setProgress(Math.round(sub * 100f));
        valueLcdSubpixelStrength.setText(String.format(java.util.Locale.US, "%.2f", sub));

        if (gap < 0f) gap = 0f;
        if (gap > 1f) gap = 1f;
        seekLcdGapStrength.setProgress(Math.round(gap * 100f));
        valueLcdGapStrength.setText(String.format(java.util.Locale.US, "%.2f", gap));

        if (gapPx < 0f) gapPx = 0f;
        if (gapPx > 0.45f) gapPx = 0.45f;
        seekLcdGapPx.setProgress(Math.round(gapPx * 100f));
        valueLcdGapPx.setText(String.format(java.util.Locale.US, "%.2f", gapPx));
    }

    private void resetLcdShaderDefaults() {
        setSystemPropertyFloat("persist.gammaos.shader.lcd.response_time", LCD_RESPONSE_TIME_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd.scan_strength", LCD_SCAN_STRENGTH_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd.subpixel_strength", LCD_SUBPIXEL_STRENGTH_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd.gap_strength", LCD_GAP_STRENGTH_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.lcd.gap_px", LCD_GAP_PX_DEFAULT);
        loadLcdShaderParamsFromProperties();
    }

    // Blur fill controls

    private void setupBlurFillControls() {
        // sigma: [0.1, 64.0] step 0.1
        seekBlurSigma.setMax(639);
        seekBlurSigma.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.1f + (progress / 10.0f);
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.sigma", value);
                valueBlurSigma.setText(String.format(java.util.Locale.US, "%.1f", value));
            }
        });

        // blur_scale: [0.25, 4.00] step 0.01
        seekBlurScale.setMax(375);
        seekBlurScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.25f + (progress / 100.0f);
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.blur_scale", value);
                valueBlurScale.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // edge_px: [1, 4096]
        seekBlurEdgePx.setMax(4095);
        seekBlurEdgePx.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1.0f + progress;
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.edge_px", value);
                valueBlurEdgePx.setText(String.format(java.util.Locale.US, "%.0f", value));
            }
        });

        // feather_px: [0, 1024]
        seekBlurFeatherPx.setMax(1024);
        seekBlurFeatherPx.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress;
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.feather_px", value);
                valueBlurFeatherPx.setText(String.format(java.util.Locale.US, "%.0f", value));
            }
        });

        // strength: [0,1]
        seekBlurStrength.setMax(100);
        seekBlurStrength.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.strength", value);
                valueBlurStrength.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // res_scale: [0.10, 1.00] step 0.01
        seekBlurResScale.setMax(90);
        seekBlurResScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.10f + (progress / 100.0f);
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.res_scale", value);
                valueBlurResScale.setText(String.format(java.util.Locale.US, "%.2f", value));
            }
        });

        // luma_threshold: [0.000, 0.300] step 0.001
        seekBlurLumaThreshold.setMax(300);
        seekBlurLumaThreshold.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 1000.0f;
                setSystemPropertyFloat("persist.gammaos.shader.blurfill.luma_threshold", value);
                valueBlurLumaThreshold.setText(String.format(java.util.Locale.US, "%.3f", value));
            }
        });

        radioGroupBlurOrientation.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioBlurOrientVertical) {
                setSystemProperty("persist.gammaos.shader.blurfill.orientation", "vertical");
            } else if (checkedId == R.id.radioBlurOrientHorizontal) {
                setSystemProperty("persist.gammaos.shader.blurfill.orientation", "horizontal");
            } else {
                setSystemProperty("persist.gammaos.shader.blurfill.orientation", "auto");
            }
        });

        btnResetBlurFill.setOnClickListener(v -> resetBlurFillDefaults());
    }

    private void loadBlurFillParamsFromProperties() {
        float sigma = getSystemPropertyFloat("persist.gammaos.shader.blurfill.sigma", BLURFILL_SIGMA_DEFAULT);
        float blurScale = getSystemPropertyFloat("persist.gammaos.shader.blurfill.blur_scale", BLURFILL_BLUR_SCALE_DEFAULT);
        float edgePx = getSystemPropertyFloat("persist.gammaos.shader.blurfill.edge_px", BLURFILL_EDGE_PX_DEFAULT);
        float featherPx = getSystemPropertyFloat("persist.gammaos.shader.blurfill.feather_px", BLURFILL_FEATHER_PX_DEFAULT);
        float strength = getSystemPropertyFloat("persist.gammaos.shader.blurfill.strength", BLURFILL_STRENGTH_DEFAULT);
        float resScale = getSystemPropertyFloat("persist.gammaos.shader.blurfill.res_scale", BLURFILL_RES_SCALE_DEFAULT);
        float lumaThreshold = getSystemPropertyFloat("persist.gammaos.shader.blurfill.luma_threshold", BLURFILL_LUMA_THRESHOLD_DEFAULT);
        String orientation = getSystemProperty("persist.gammaos.shader.blurfill.orientation", BLURFILL_ORIENTATION_DEFAULT);

        if (sigma < 0.1f) sigma = 0.1f;
        if (sigma > 64.0f) sigma = 64.0f;
        seekBlurSigma.setProgress(Math.round((sigma - 0.1f) * 10f));
        valueBlurSigma.setText(String.format(java.util.Locale.US, "%.1f", sigma));

        if (blurScale < 0.25f) blurScale = 0.25f;
        if (blurScale > 4.0f) blurScale = 4.0f;
        seekBlurScale.setProgress(Math.round((blurScale - 0.25f) * 100f));
        valueBlurScale.setText(String.format(java.util.Locale.US, "%.2f", blurScale));

        if (edgePx < 1f) edgePx = 1f;
        if (edgePx > 4096f) edgePx = 4096f;
        seekBlurEdgePx.setProgress(Math.round(edgePx - 1f));
        valueBlurEdgePx.setText(String.format(java.util.Locale.US, "%.0f", edgePx));

        if (featherPx < 0f) featherPx = 0f;
        if (featherPx > 1024f) featherPx = 1024f;
        seekBlurFeatherPx.setProgress(Math.round(featherPx));
        valueBlurFeatherPx.setText(String.format(java.util.Locale.US, "%.0f", featherPx));

        if (strength < 0f) strength = 0f;
        if (strength > 1f) strength = 1f;
        seekBlurStrength.setProgress(Math.round(strength * 100f));
        valueBlurStrength.setText(String.format(java.util.Locale.US, "%.2f", strength));

        if (resScale < 0.10f) resScale = 0.10f;
        if (resScale > 1.0f) resScale = 1.0f;
        seekBlurResScale.setProgress(Math.round((resScale - 0.10f) * 100f));
        valueBlurResScale.setText(String.format(java.util.Locale.US, "%.2f", resScale));

        if (lumaThreshold < 0f) lumaThreshold = 0f;
        if (lumaThreshold > 0.3f) lumaThreshold = 0.3f;
        seekBlurLumaThreshold.setProgress(Math.round(lumaThreshold * 1000f));
        valueBlurLumaThreshold.setText(String.format(java.util.Locale.US, "%.3f", lumaThreshold));

        if ("vertical".equalsIgnoreCase(orientation) || "pillar".equalsIgnoreCase(orientation) || "v".equalsIgnoreCase(orientation)) {
            radioBlurOrientVertical.setChecked(true);
        } else if ("horizontal".equalsIgnoreCase(orientation) || "letterbox".equalsIgnoreCase(orientation) || "h".equalsIgnoreCase(orientation)) {
            radioBlurOrientHorizontal.setChecked(true);
        } else {
            radioBlurOrientAuto.setChecked(true);
        }
    }

    private void resetBlurFillDefaults() {
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.sigma", BLURFILL_SIGMA_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.blur_scale", BLURFILL_BLUR_SCALE_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.edge_px", BLURFILL_EDGE_PX_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.feather_px", BLURFILL_FEATHER_PX_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.strength", BLURFILL_STRENGTH_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.res_scale", BLURFILL_RES_SCALE_DEFAULT);
        setSystemPropertyFloat("persist.gammaos.shader.blurfill.luma_threshold", BLURFILL_LUMA_THRESHOLD_DEFAULT);
        setSystemProperty("persist.gammaos.shader.blurfill.orientation", BLURFILL_ORIENTATION_DEFAULT);
        loadBlurFillParamsFromProperties();
    }

    // Simple helper to avoid implementing unused listener methods everywhere
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}

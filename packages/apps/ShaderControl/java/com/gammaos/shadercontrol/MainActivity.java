package com.gammaos.shadercontrol;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Shader mode controls
    private RadioGroup radioGroupMode;
    private RadioButton radioNone;
    private RadioButton radioCrt;
    private RadioButton radioLcd3x;
    private RadioButton radioLcdShader;
    private RadioButton radioBlurFill;
    private RadioButton radioCustomSksl;
    private RadioButton radioCustomVk;
    private RadioButton radioCustomGl;

    // Current custom shader type determines file extension filter
    // "custom" = SkSL transpiler (.slangp), "custom-vk" = Vulkan (.slangp), "custom-gl" = GLSL (.glslp)
    private String customShaderType = "custom";

    // 5-tap title to reveal hidden custom shader modes
    private int titleTapCount = 0;
    private long lastTitleTapTime = 0;
    private boolean allCustomModesVisible = false;
    private boolean hwuiUsesVulkan = false;

    // Custom shader controls
    private View groupCustom;
    private TextView labelCustomHint;
    private TextView labelActivePreset;
    private TextView labelPresetCount;
    private RecyclerView recyclerPresets;
    private PresetAdapter presetAdapter;
    private final List<PresetEntry> presetList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RadioGroup radioGroupCustomResScale;
    private RadioButton radioResFull, radioRes3_4, radioRes1_2, radioRes1_4;
    private MaterialButton btnBrowsePreset;
    private LinearLayout containerShaderParams;
    private FileObserver paramMetaObserver;
    private final Runnable loadShaderParamsRunnable = this::loadShaderParams;
    private String lastLoadedParamPreset = "";  // track which preset's params are currently shown
    private ActivityResultLauncher<Intent> browsePresetLauncher;
    private EditText editSearch;
    private TextView labelCurrentPath;
    private String currentBrowsePath = "";
    private final List<PresetEntry> allBrowseEntries = new ArrayList<>();

    private static final String DEFAULT_SHADER_ROOT = "/sdcard/GammaShader";

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

        browsePresetLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleBrowseResult(result.getData().getData());
                    }
                });

        setContentView(R.layout.activity_main);

        bindViews();
        setupModeSelector();
        setupCrtControls();
        setupLcd3xControls();
        setupLcdShaderControls();
        setupBlurFillControls();
        setupCustomControls();
        restoreInitialState();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            // If custom group is visible and we can navigate up, go up a directory
            if (groupCustom.getVisibility() == View.VISIBLE
                    && !currentBrowsePath.isEmpty()) {
                File current = new File(currentBrowsePath);
                String parent = current.getParent();
                if (parent != null) {
                    editSearch.setText("");
                    browseTo(parent);
                    return true;
                }
            }
        }
        // L1/R1 shoulder buttons: cycle to previous/next shader preset in the list
        if (groupCustom.getVisibility() == View.VISIBLE
                && (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_L1
                    || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R1)) {
            cyclePreset(keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Cycle to the next (forward=true) or previous (forward=false) preset in the list. */
    private void cyclePreset(boolean forward) {
        if (presetList.isEmpty()) return;

        // Find current selection index
        String currentPath = getSystemProperty("persist.gammaos.shader.custom.preset", "");
        int currentIdx = -1;
        for (int i = 0; i < presetList.size(); i++) {
            if (!presetList.get(i).isFolder && presetList.get(i).absolutePath.equals(currentPath)) {
                currentIdx = i;
                break;
            }
        }

        // Find next/prev non-folder entry
        int direction = forward ? 1 : -1;
        int startIdx = (currentIdx >= 0) ? currentIdx + direction : 0;
        for (int step = 0; step < presetList.size(); step++) {
            int idx = ((startIdx + step * direction) % presetList.size() + presetList.size()) % presetList.size();
            PresetEntry entry = presetList.get(idx);
            if (!entry.isFolder) {
                onPresetSelected(entry);
                recyclerPresets.scrollToPosition(idx);
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paramMetaObserver != null) {
            paramMetaObserver.stopWatching();
            paramMetaObserver = null;
        }
    }

    private void bindViews() {
        radioGroupMode = findViewById(R.id.radioGroupMode);
        radioNone = findViewById(R.id.radioNone);
        radioCrt = findViewById(R.id.radioCrt);
        radioLcd3x = findViewById(R.id.radioLcd3x);
        radioLcdShader = findViewById(R.id.radioLcdShader);
        radioBlurFill = findViewById(R.id.radioBlurFill);
        radioCustomSksl = findViewById(R.id.radioCustomSksl);
        radioCustomVk = findViewById(R.id.radioCustomVk);
        radioCustomGl = findViewById(R.id.radioCustomGl);

        // Mark the recommended custom pipeline based on the HWUI rendering backend.
        // If HWUI uses Vulkan (ro.hwui.use_vulkan=true), Vulkan shaders run natively
        // on the same backend. Otherwise GL is the native path and most stable pipeline.
        hwuiUsesVulkan = "true".equals(
                getSystemProperty("ro.hwui.use_vulkan", ""));
        if (hwuiUsesVulkan) {
            radioCustomVk.setText(radioCustomVk.getText() + " \u2605");
            // Hide non-recommended modes by default
            radioCustomSksl.setVisibility(View.GONE);
            radioCustomGl.setVisibility(View.GONE);
        } else {
            radioCustomGl.setText(radioCustomGl.getText() + " \u2605");
            // Hide non-recommended modes by default
            radioCustomSksl.setVisibility(View.GONE);
            radioCustomVk.setVisibility(View.GONE);
        }

        // 5-tap on title to reveal all custom shader modes
        TextView titleView = findViewById(R.id.title);
        titleView.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTitleTapTime > 1500) titleTapCount = 0;
            lastTitleTapTime = now;
            titleTapCount++;
            if (titleTapCount >= 5 && !allCustomModesVisible) {
                allCustomModesVisible = true;
                radioCustomSksl.setVisibility(View.VISIBLE);
                radioCustomVk.setVisibility(View.VISIBLE);
                radioCustomGl.setVisibility(View.VISIBLE);
            }
        });

        groupCustom = findViewById(R.id.groupCustom);
        labelCustomHint = findViewById(R.id.labelCustomHint);
        labelActivePreset = findViewById(R.id.labelActivePreset);
        labelPresetCount = findViewById(R.id.labelPresetCount);
        recyclerPresets = findViewById(R.id.recyclerPresets);
        radioGroupCustomResScale = findViewById(R.id.radioGroupCustomResScale);
        radioResFull = findViewById(R.id.radioResFull);
        radioRes3_4 = findViewById(R.id.radioRes3_4);
        radioRes1_2 = findViewById(R.id.radioRes1_2);
        radioRes1_4 = findViewById(R.id.radioRes1_4);
        btnBrowsePreset = findViewById(R.id.btnBrowsePreset);
        containerShaderParams = findViewById(R.id.containerShaderParams);
        editSearch = findViewById(R.id.editSearch);
        labelCurrentPath = findViewById(R.id.labelCurrentPath);

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
            } else if (checkedId == R.id.radioCustomSksl) {
                onModeSelectedCustom("custom");
            } else if (checkedId == R.id.radioCustomVk) {
                onModeSelectedCustom("custom-vk");
            } else if (checkedId == R.id.radioCustomGl) {
                onModeSelectedCustom("custom-gl");
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

    private void onModeSelectedCustom(String type) {
        customShaderType = type;
        setSystemProperty("persist.gammaos.shader.enable", "1");
        setSystemProperty("persist.gammaos.shader.type", type);
        // Update hint text for current pipeline
        if ("custom-gl".equals(type)) {
            labelCustomHint.setText(R.string.hint_custom_gl);
        } else if ("custom-vk".equals(type)) {
            labelCustomHint.setText(R.string.hint_custom_vk);
        } else {
            labelCustomHint.setText(R.string.hint_custom_sksl);
        }
        updateGroupsVisibility();
        String preset = getSystemProperty("persist.gammaos.shader.custom.preset", "");
        browseToPresetOrDefault(preset);
    }

    private boolean isCustomMode(int checkedId) {
        return checkedId == R.id.radioCustomSksl
            || checkedId == R.id.radioCustomVk
            || checkedId == R.id.radioCustomGl;
    }

    private void updateGroupsVisibility() {
        int checkedId = radioGroupMode.getCheckedRadioButtonId();
        groupCrt.setVisibility(checkedId == R.id.radioCrt ? View.VISIBLE : View.GONE);
        groupLcd3x.setVisibility(checkedId == R.id.radioLcd3x ? View.VISIBLE : View.GONE);
        groupLcdShader.setVisibility(checkedId == R.id.radioLcdShader ? View.VISIBLE : View.GONE);
        groupBlurFill.setVisibility(checkedId == R.id.radioBlurFill ? View.VISIBLE : View.GONE);
        groupCustom.setVisibility(isCustomMode(checkedId) ? View.VISIBLE : View.GONE);
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
        } else if ("custom-vk".equals(type)) {
            customShaderType = "custom-vk";
            // Ensure this button is visible even if hidden by default
            radioCustomVk.setVisibility(View.VISIBLE);
            radioCustomVk.setChecked(true);
            labelCustomHint.setText(R.string.hint_custom_vk);
        } else if ("custom-gl".equals(type)) {
            customShaderType = "custom-gl";
            radioCustomGl.setVisibility(View.VISIBLE);
            radioCustomGl.setChecked(true);
            labelCustomHint.setText(R.string.hint_custom_gl);
        } else if ("custom".equals(type)) {
            customShaderType = "custom";
            radioCustomSksl.setVisibility(View.VISIBLE);
            radioCustomSksl.setChecked(true);
            labelCustomHint.setText(R.string.hint_custom_sksl);
        } else {
            // Default to crt-simple when enabled but unknown type
            radioCrt.setChecked(true);
        }

        // Initialize parameter sliders from current properties
        loadCrtParamsFromProperties();
        loadLcd3xParamsFromProperties();
        loadLcdShaderParamsFromProperties();
        loadBlurFillParamsFromProperties();

        // Set up custom shader state — resume to the preset's parent folder
        String customPreset = getSystemProperty("persist.gammaos.shader.custom.preset", "");
        if (presetAdapter != null) {
            presetAdapter.setSelectedPath(customPreset);
        }
        if (("custom".equals(type) || "custom-vk".equals(type) || "custom-gl".equals(type)) && shaderOn) {
            browseToPresetOrDefault(customPreset);
        }

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

    // ---- Custom shader preset support ----

    private void setupCustomControls() {
        presetAdapter = new PresetAdapter(presetList, entry -> {
            if (entry.isFolder) {
                editSearch.setText("");
                browseTo(entry.absolutePath);
            } else {
                onPresetSelected(entry);
            }
        });
        recyclerPresets.setLayoutManager(new LinearLayoutManager(this));
        recyclerPresets.setAdapter(presetAdapter);

        // Show current preset path if set
        String current = getSystemProperty("persist.gammaos.shader.custom.preset", "");
        if (!current.isEmpty()) {
            labelActivePreset.setText(friendlyPresetName(current));
        }

        // Browse button — launches DocumentsUI / file picker for .slangp/.glslp files
        btnBrowsePreset.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            browsePresetLauncher.launch(intent);
        });

        // Search bar — filters current folder listing
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterBrowseEntries(s.toString());
            }
        });

        // Resolution scale selector
        radioGroupCustomResScale.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioRes3_4) {
                setSystemProperty("persist.gammaos.shader.custom.res_scale", "3/4");
            } else if (checkedId == R.id.radioRes1_2) {
                setSystemProperty("persist.gammaos.shader.custom.res_scale", "1/2");
            } else if (checkedId == R.id.radioRes1_4) {
                setSystemProperty("persist.gammaos.shader.custom.res_scale", "1/4");
            } else {
                setSystemProperty("persist.gammaos.shader.custom.res_scale", "full");
            }
        });

        // Restore current resolution scale
        loadCustomResScaleFromProperty();

        // Watch for parameter metadata changes from SurfaceFlinger
        loadShaderParams();
        startParamMetaObserver();
    }

    private void loadCustomResScaleFromProperty() {
        String scale = getSystemProperty("persist.gammaos.shader.custom.res_scale", "full");
        switch (scale) {
            case "3/4":
                radioRes3_4.setChecked(true);
                break;
            case "1/2":
                radioRes1_2.setChecked(true);
                break;
            case "1/4":
                radioRes1_4.setChecked(true);
                break;
            default:
                radioResFull.setChecked(true);
                break;
        }
    }

    private void onPresetSelected(PresetEntry entry) {
        // Use the current custom shader type (set when the mode radio was selected)
        String shaderType = customShaderType;

        // Cancel any pending param load from a previous selection and clear stale params
        mainHandler.removeCallbacks(loadShaderParamsRunnable);
        containerShaderParams.removeAllViews();

        // Clear stale param overrides so the new preset starts with its defaults
        clearParamOverrides();

        // If the preset is in /data/data/ (RetroArch app-private dir), SurfaceFlinger
        // cannot access it due to DAC. Copy the preset and its referenced shader files
        // to /sdcard/GammaShader/.cache/ so SF can read them.
        if (entry.absolutePath.startsWith("/data/data/") ||
                entry.absolutePath.startsWith("/data/user/")) {
            Executors.newSingleThreadExecutor().execute(() -> {
                String cachedPath = copyPresetToCache(entry.absolutePath);
                mainHandler.post(() -> {
                    String path = cachedPath != null ? cachedPath : entry.absolutePath;
                    setSystemProperty("persist.gammaos.shader.type", shaderType);
                    setSystemProperty("persist.gammaos.shader.custom.preset", path);
                    labelActivePreset.setText(entry.displayName);
                    presetAdapter.setSelectedPath(entry.absolutePath);
                    scheduleParamLoad(path);
                });
            });
        } else {
            setSystemProperty("persist.gammaos.shader.type", shaderType);
            setSystemProperty("persist.gammaos.shader.custom.preset", entry.absolutePath);
            labelActivePreset.setText(entry.displayName);
            presetAdapter.setSelectedPath(entry.absolutePath);
            scheduleParamLoad(entry.absolutePath);
        }
    }

    /** Schedule param metadata load with retry until the native side has written fresh data. */
    private void scheduleParamLoad(String presetPath) {
        lastLoadedParamPreset = "";  // force reload
        // Delete stale meta so we know when native writes fresh data
        new File(PARAM_META_PATH).delete();
        // Poll for the new meta file (native writes it within ~1-2 frames)
        final long startTime = System.currentTimeMillis();
        Runnable pollForMeta = new Runnable() {
            @Override
            public void run() {
                File meta = new File(PARAM_META_PATH);
                if (meta.exists() && meta.length() > 0) {
                    loadShaderParams();
                    return;
                }
                // Retry for up to 3 seconds
                if (System.currentTimeMillis() - startTime < 3000) {
                    mainHandler.postDelayed(this, 200);
                }
            }
        };
        mainHandler.postDelayed(pollForMeta, 300);
    }

    /** Clear the .shader_params override file. */
    private void clearParamOverrides() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                new java.io.FileWriter(PARAM_VALUES_PATH).close();
            } catch (IOException ignored) {}
        });
    }

    /**
     * Copy a .slangp preset and all .slang files it references to
     * /sdcard/GammaShader/.cache/ so SurfaceFlinger can access them.
     * Returns the cached preset path, or null on failure.
     */
    private String copyPresetToCache(String presetPath) {
        try {
            File src = new File(presetPath);
            if (!src.exists()) return null;

            File cacheDir = new File("/sdcard/GammaShader/.cache");
            cacheDir.mkdirs();

            // Read the preset and find referenced shader files
            String content = readTextFile(src);
            if (content == null) return null;

            // Copy the preset file
            File cachedPreset = new File(cacheDir, src.getName());
            StringBuilder rewrittenContent = new StringBuilder();
            String srcDir = src.getParent();

            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                // Rewrite shader paths: shader0 = "path/to/shader.slang"
                if (trimmed.matches("shader\\d+\\s*=.*")) {
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq + 1);
                    String val = trimmed.substring(eq + 1).trim().replace("\"", "");
                    // Resolve relative path
                    File shaderFile = val.startsWith("/") ? new File(val) : new File(srcDir, val);
                    if (shaderFile.exists()) {
                        File cachedShader = new File(cacheDir, shaderFile.getName());
                        copyFile(shaderFile, cachedShader);
                        rewrittenContent.append(key).append(" \"").append(cachedShader.getName()).append("\"\n");
                    } else {
                        rewrittenContent.append(line).append("\n");
                    }
                } else {
                    rewrittenContent.append(line).append("\n");
                }
            }

            writeTextFile(cachedPreset, rewrittenContent.toString());
            return cachedPreset.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private String readTextFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private void writeTextFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    /** Returns true if the filename matches the expected preset extension for the current custom mode. */
    private boolean matchesCustomExtension(String name) {
        if ("custom-gl".equals(customShaderType)) {
            return name.endsWith(".glslp");
        }
        // Both SkSL transpiler and Vulkan use .slangp
        return name.endsWith(".slangp");
    }

    /** Browse to the parent folder of the given preset, or fall back to default root. */
    private void browseToPresetOrDefault(String presetPath) {
        if (presetPath != null && !presetPath.isEmpty()) {
            File presetFile = new File(presetPath);
            File parentDir = presetFile.getParentFile();
            if (parentDir != null && parentDir.isDirectory()) {
                browseTo(parentDir.getAbsolutePath());
                return;
            }
        }
        browseToDefault();
    }

    private void browseToDefault() {
        File root = new File(DEFAULT_SHADER_ROOT);
        if (root.isDirectory()) {
            browseTo(DEFAULT_SHADER_ROOT);
        } else {
            // Fallback to sdcard root
            browseTo("/sdcard");
        }
    }

    private void browseTo(String dirPath) {
        currentBrowsePath = dirPath;
        labelCurrentPath.setText(dirPath);
        labelPresetCount.setText(R.string.custom_searching);

        // Save parent ScrollView position before clearing the list
        android.widget.ScrollView scrollParams = findViewById(R.id.scrollParams);
        final int savedScrollY = scrollParams.getScrollY();

        presetList.clear();
        allBrowseEntries.clear();
        presetAdapter.notifyDataSetChanged();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<PresetEntry> entries = new ArrayList<>();
            File dir = new File(dirPath);

            // Add ".." entry unless at filesystem root
            if (dir.getParent() != null) {
                PresetEntry up = new PresetEntry();
                up.absolutePath = dir.getParent();
                up.displayName = "..";
                up.source = "";
                up.isFolder = true;
                entries.add(up);
            }

            File[] children = dir.listFiles();
            if (children != null) {
                // Separate folders and files, sort each group
                List<File> folders = new ArrayList<>();
                List<File> files = new ArrayList<>();
                for (File f : children) {
                    if (f.getName().startsWith(".")) continue;
                    if (f.isDirectory()) {
                        folders.add(f);
                    } else if (matchesCustomExtension(f.getName())) {
                        files.add(f);
                    }
                }
                Collections.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                for (File f : folders) {
                    PresetEntry entry = new PresetEntry();
                    entry.absolutePath = f.getAbsolutePath();
                    entry.displayName = f.getName();
                    entry.source = "";
                    entry.isFolder = true;
                    entries.add(entry);
                }
                for (File f : files) {
                    PresetEntry entry = new PresetEntry();
                    entry.absolutePath = f.getAbsolutePath();
                    entry.displayName = f.getName();
                    entry.source = "";
                    entry.isFolder = false;
                    entries.add(entry);
                }
            }

            mainHandler.post(() -> {
                allBrowseEntries.clear();
                allBrowseEntries.addAll(entries);
                presetList.clear();
                presetList.addAll(entries);
                presetAdapter.notifyDataSetChanged();

                // Restore parent ScrollView position so the browser stays in view
                scrollParams.post(() -> scrollParams.scrollTo(0, savedScrollY));

                int fileCount = 0;
                int folderCount = 0;
                for (PresetEntry e : entries) {
                    if (e.displayName.equals("..")) continue;
                    if (e.isFolder) folderCount++;
                    else fileCount++;
                }
                if (folderCount == 0 && fileCount == 0) {
                    labelPresetCount.setText("custom-gl".equals(customShaderType)
                            ? R.string.custom_no_presets_found_glslp
                            : R.string.custom_no_presets_found_slangp);
                } else {
                    labelPresetCount.setText(folderCount + " folders, " + fileCount + " presets");
                }
                // Scroll to the currently selected preset so it's visible
                scrollToSelectedPreset();
            });
        });
    }

    /** Scroll the RecyclerView to show the currently selected preset. */
    private void scrollToSelectedPreset() {
        String selectedPath = getSystemProperty("persist.gammaos.shader.custom.preset", "");
        if (selectedPath.isEmpty()) return;
        for (int i = 0; i < presetList.size(); i++) {
            if (presetList.get(i).absolutePath.equals(selectedPath)) {
                recyclerPresets.scrollToPosition(i);
                break;
            }
        }
    }

    private void filterBrowseEntries(String query) {
        presetList.clear();
        if (query == null || query.isEmpty()) {
            presetList.addAll(allBrowseEntries);
        } else {
            String lower = query.toLowerCase(Locale.US);
            for (PresetEntry e : allBrowseEntries) {
                if (e.displayName.equals("..") || e.displayName.toLowerCase(Locale.US).contains(lower)) {
                    presetList.add(e);
                }
            }
        }
        presetAdapter.notifyDataSetChanged();
    }

    private String friendlyPresetName(String absPath) {
        if (absPath == null || absPath.isEmpty()) return getString(R.string.custom_no_preset);
        int lastSlash = absPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < absPath.length() - 1) {
            return absPath.substring(lastSlash + 1);
        }
        return absPath;
    }

    // ---- File picker result handling ----

    private void handleBrowseResult(Uri uri) {
        if (uri == null) return;
        // Try to resolve the URI to a filesystem path
        String path = resolveUriToPath(uri);
        if (path != null && matchesCustomExtension(path)) {
            mainHandler.removeCallbacks(loadShaderParamsRunnable);
            containerShaderParams.removeAllViews();
            clearParamOverrides();
            setSystemProperty("persist.gammaos.shader.type", customShaderType);
            setSystemProperty("persist.gammaos.shader.custom.preset", path);
            labelActivePreset.setText(friendlyPresetName(path));
            presetAdapter.setSelectedPath(path);
            scheduleParamLoad(path);
            return;
        }
        // Fallback: copy content via ContentResolver to GammaShader cache
        Executors.newSingleThreadExecutor().execute(() -> {
            String copied = copyUriToCache(uri);
            if (copied != null) {
                mainHandler.post(() -> {
                    mainHandler.removeCallbacks(loadShaderParamsRunnable);
                    containerShaderParams.removeAllViews();
                    clearParamOverrides();
                    setSystemProperty("persist.gammaos.shader.type", customShaderType);
                    setSystemProperty("persist.gammaos.shader.custom.preset", copied);
                    labelActivePreset.setText(friendlyPresetName(copied));
                    presetAdapter.setSelectedPath(copied);
                    scheduleParamLoad(copied);
                });
            }
        });
    }

    private String copyUriToCache(Uri uri) {
        try {
            File cacheDir = new File("/sdcard/GammaShader/.cache");
            cacheDir.mkdirs();
            // Determine filename from URI or display name
            String name = "imported.slangp";
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            }
            if (!matchesCustomExtension(name)) return null;
            File dst = new File(cacheDir, name);
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            return dst.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUriToPath(Uri uri) {
        // For content:// URIs from DocumentsUI, try to resolve the actual file path
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            String relative = split.length > 1 ? split[1] : "";
            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relative;
            }
        }
        // Fallback: try reading _data column
        try (Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex("_data");
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        // Last resort: use path from URI
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    // ---- Shader parameter sliders ----

    private static final String PARAM_META_PATH = "/sdcard/GammaShader/.shader_param_meta";
    private static final String PARAM_VALUES_PATH = "/sdcard/GammaShader/.shader_params";

    private void startParamMetaObserver() {
        File metaFile = new File(PARAM_META_PATH);
        File dir = metaFile.getParentFile();
        if (dir == null) return;
        dir.mkdirs();
        paramMetaObserver = new FileObserver(dir.getAbsolutePath(),
                FileObserver.CLOSE_WRITE | FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (".shader_param_meta".equals(path)) {
                    mainHandler.post(() -> loadShaderParams());
                }
            }
        };
        paramMetaObserver.startWatching();
    }

    private void loadShaderParams() {
        containerShaderParams.removeAllViews();
        File metaFile = new File(PARAM_META_PATH);
        if (!metaFile.exists() || !metaFile.canRead()) return;

        List<ShaderParamMeta> params = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(metaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 6);
                if (parts.length < 6) continue;
                ShaderParamMeta p = new ShaderParamMeta();
                p.id = parts[0];
                p.desc = parts[1];
                p.initial = Float.parseFloat(parts[2]);
                p.min = Float.parseFloat(parts[3]);
                p.max = Float.parseFloat(parts[4]);
                p.step = Float.parseFloat(parts[5]);
                p.current = p.initial;
                params.add(p);
            }
        } catch (Exception e) {
            return;
        }

        if (params.isEmpty()) return;

        // Read current overrides
        File valuesFile = new File(PARAM_VALUES_PATH);
        if (valuesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(valuesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String id = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    for (ShaderParamMeta p : params) {
                        if (p.id.equals(id)) {
                            try { p.current = Float.parseFloat(val); } catch (Exception ignored) {}
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Section title
        TextView title = new TextView(this);
        title.setText(R.string.section_shader_params);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColor(R.color.text_primary));
        title.setPadding(0, 24, 0, 8);
        containerShaderParams.addView(title);

        // Create a slider for each parameter
        for (ShaderParamMeta p : params) {
            addParamSlider(p, params);
        }
    }

    private void addParamSlider(ShaderParamMeta param, List<ShaderParamMeta> allParams) {
        // Label row: description + value
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 12, 0, 0);

        TextView label = new TextView(this);
        label.setText(param.desc);
        label.setTextSize(13);
        label.setTextColor(getColor(R.color.text_primary));
        label.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView valueText = new TextView(this);
        valueText.setTextSize(13);
        valueText.setTextColor(getColor(R.color.text_primary));
        valueText.setGravity(Gravity.END);
        valueText.setText(formatParamValue(param.current));
        row.addView(valueText);

        containerShaderParams.addView(row);

        // SeekBar
        SeekBar seekBar = new SeekBar(this);
        seekBar.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        seekBar.setFocusable(true);

        float range = param.max - param.min;
        int steps = (param.step > 0) ? Math.max(1, Math.round(range / param.step)) : 100;
        seekBar.setMax(steps);

        // Set current position
        int pos = Math.round((param.current - param.min) / param.step);
        seekBar.setProgress(Math.max(0, Math.min(steps, pos)));

        seekBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float value = param.min + progress * param.step;
                value = Math.max(param.min, Math.min(param.max, value));
                param.current = value;
                valueText.setText(formatParamValue(value));
                if (fromUser) {
                    writeParamOverrides(allParams);
                }
            }
        });
        containerShaderParams.addView(seekBar);
    }

    private String formatParamValue(float value) {
        if (value == (int) value) return String.valueOf((int) value);
        return String.format(Locale.US, "%.3f", value);
    }

    private void writeParamOverrides(List<ShaderParamMeta> params) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File dir = new File("/sdcard/GammaShader");
                dir.mkdirs();
                try (FileWriter writer = new FileWriter(PARAM_VALUES_PATH)) {
                    for (ShaderParamMeta p : params) {
                        writer.write(p.id + "=" + p.current + "\n");
                    }
                }
            } catch (IOException ignored) {}
        });
    }

    static class ShaderParamMeta {
        String id;
        String desc;
        float initial;
        float min;
        float max;
        float step;
        float current;
    }

    // ---- Preset data model ----

    static class PresetEntry {
        String absolutePath;
        String displayName;
        String source;
        boolean isFolder;
    }

    // ---- Preset list adapter ----

    interface OnPresetClickListener {
        void onPresetClicked(PresetEntry entry);
    }

    static class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.VH> {
        private final List<PresetEntry> items;
        private final OnPresetClickListener listener;
        private String selectedPath = "";

        PresetAdapter(List<PresetEntry> items, OnPresetClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setSelectedPath(String path) {
            String oldPath = this.selectedPath;
            this.selectedPath = path != null ? path : "";
            if (oldPath.equals(this.selectedPath)) return;
            // Use targeted updates to avoid resetting RecyclerView scroll position
            for (int i = 0; i < items.size(); i++) {
                String p = items.get(i).absolutePath;
                if (p.equals(oldPath) || p.equals(this.selectedPath)) {
                    notifyItemChanged(i);
                }
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(24, 20, 24, 20);
            tv.setTextSize(14);
            tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            tv.setFocusable(true);
            tv.setClickable(true);
            tv.setBackgroundResource(R.drawable.focusable_background);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PresetEntry entry = items.get(position);
            TextView tv = (TextView) holder.itemView;
            if (entry.isFolder) {
                tv.setText("\uD83D\uDCC1 " + entry.displayName);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setTextColor(0xFFFF8A2A); // accent color
            } else {
                boolean isSelected = entry.absolutePath.equals(selectedPath);
                tv.setText(entry.displayName);
                tv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
                tv.setTextColor(0xFFFFFFFF); // text_primary
            }
            tv.setOnClickListener(v -> {
                if (!entry.isFolder) {
                    int oldPos = -1;
                    String oldSel = selectedPath;
                    selectedPath = entry.absolutePath;
                    // Find and refresh the previously selected item
                    if (!oldSel.isEmpty() && !oldSel.equals(entry.absolutePath)) {
                        for (int i = 0; i < items.size(); i++) {
                            if (items.get(i).absolutePath.equals(oldSel)) {
                                oldPos = i;
                                break;
                            }
                        }
                    }
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                    if (oldPos >= 0) notifyItemChanged(oldPos);
                }
                listener.onPresetClicked(entry);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    // Simple helper to avoid implementing unused listener methods everywhere
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}

package com.gammaos.gammaeq;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GammaEQMain";

    private static final String PRESET_NAME = "Sparkle (Speakers)";
    private static final String CUSTOM_NAME = "Custom";

    private static final String PRESET_DIR_PATH = "/sdcard/GammaEQ";
    private static final String PRESET_EXT = ".txt";

    // Keys we persist into preset files and accept when loading
    private static final String[] PRESET_PROP_KEYS = new String[]{
            "persist.sys.gammaeq.spk_only",
            "persist.sys.gammaeq.force",
            "persist.sys.gammaeq.preamp_db",
            "persist.sys.gammaeq.postgain_db",

            "persist.sys.spk.cryst",
            "persist.sys.spk.cryst.amount",
            "persist.sys.spk.cryst.mix",
            "persist.sys.spk.cryst.hz",
            "persist.sys.spk.cryst.pregain_db",
            "persist.sys.spk.cryst.postgain_db",
            "persist.sys.spk.cryst.pre",
            "persist.sys.spk.cryst.fc",
            "persist.sys.spk.cryst.limit",

            "persist.sys.spk.lbp",
            "persist.sys.spk.lbp.fc",
            "persist.sys.spk.lbp.thr",
            "persist.sys.spk.lbp.atk",
            "persist.sys.spk.lbp.rel",

            "persist.sys.spk.mp",
            "persist.sys.spk.mp.hpf",
            "persist.sys.spk.mp.lpf",
            "persist.sys.spk.mp.thr",
            "persist.sys.spk.mp.atk",
            "persist.sys.spk.mp.rel",

            "persist.sys.spk.peq",
            "persist.sys.spk.peq.b0",
            "persist.sys.spk.peq.b1",
            "persist.sys.spk.peq.b2",
            "persist.sys.spk.peq.a1",
            "persist.sys.spk.peq.a2",
            "persist.sys.spk.peq.pregain",
            "persist.sys.spk.peq.keepheadroom",
            "persist.sys.spk.peq.limit",

            "persist.sys.spk.peq2",
            "persist.sys.spk.peq2.b0",
            "persist.sys.spk.peq2.b1",
            "persist.sys.spk.peq2.b2",
            "persist.sys.spk.peq2.a1",
            "persist.sys.spk.peq2.a2",

            "persist.sys.spk.wide",
            "persist.sys.spk.wide.mix",
            "persist.sys.spk.wide.hpf",
            "persist.sys.spk.wide.amount",
            "persist.sys.spk.wide.pre",
            "persist.sys.spk.wide.limit",
            "persist.sys.spk.wide.fc"
    };

    private static final Set<String> ALLOWED_PRESET_KEYS = new HashSet<>();

    static {
        for (String k : PRESET_PROP_KEYS) {
            ALLOWED_PRESET_KEYS.add(k);
        }
    }

    private SwitchMaterial switchEnable;
    private MaterialAutoCompleteTextView dropdownPreset;
    private MaterialButton btnApply, btnSaveCustom, btnReset, btnPreview;
    private Slider sliderPreamp, sliderF1, sliderQ1, sliderG1, sliderF2, sliderQ2, sliderG2;

    // Preview playback via fast-eligible AudioTrack (static decode+resample)
    private AudioTrack previewTrack;
    private byte[] previewPcmData48k;

    private SwitchMaterial switchCryst, switchLbp, switchWide;
    private Slider sliderCrystAmount, sliderCrystMix, sliderCrystHz, sliderCrystLimit, sliderCrystPreDb, sliderCrystPostDb;
    private Slider sliderLbpThr, sliderLbpAtk, sliderLbpRel, sliderLbpFc;
    private Slider sliderWideAmount, sliderWideMix, sliderWidePre, sliderWideLimit, sliderWideHpf, sliderWideFc;

    private boolean previewPlaying = false;

    // Labels for PEQ UI
    private TextView labelPreamp;
    private TextView labelF1, labelQ1, labelG1;
    private TextView labelF2, labelQ2, labelG2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            loadFromPropsIntoUI();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        switchEnable = findViewById(R.id.switchEnable);
        dropdownPreset = findViewById(R.id.dropdownPreset);
        btnApply = findViewById(R.id.btnApply);
        btnSaveCustom = findViewById(R.id.btnSaveCustom);
        btnReset = findViewById(R.id.btnReset);
        btnPreview = findViewById(R.id.btnPreview);

        sliderPreamp = findViewById(R.id.sliderPreamp);
        sliderF1 = findViewById(R.id.sliderF1);
        sliderQ1 = findViewById(R.id.sliderQ1);
        sliderG1 = findViewById(R.id.sliderG1);
        sliderF2 = findViewById(R.id.sliderF2);
        sliderQ2 = findViewById(R.id.sliderQ2);
        sliderG2 = findViewById(R.id.sliderG2);

        // PEQ labels
        labelPreamp = findViewById(R.id.labelPreamp);
        labelF1 = findViewById(R.id.labelF1);
        labelQ1 = findViewById(R.id.labelQ1);
        labelG1 = findViewById(R.id.labelG1);
        labelF2 = findViewById(R.id.labelF2);
        labelQ2 = findViewById(R.id.labelQ2);
        labelG2 = findViewById(R.id.labelG2);

        switchCryst = findViewById(R.id.switchCryst);
        switchLbp = findViewById(R.id.switchLbp);
        switchWide = findViewById(R.id.switchWide);

        sliderCrystAmount = findViewById(R.id.sliderCrystAmount);
        sliderCrystMix = findViewById(R.id.sliderCrystMix);
        sliderCrystHz = findViewById(R.id.sliderCrystHz);
        sliderCrystLimit = findViewById(R.id.sliderCrystLimit);
        sliderCrystPreDb = findViewById(R.id.sliderCrystPreDb);
        sliderCrystPostDb = findViewById(R.id.sliderCrystPostDb);

        sliderLbpThr = findViewById(R.id.sliderLbpThr);
        sliderLbpAtk = findViewById(R.id.sliderLbpAtk);
        sliderLbpRel = findViewById(R.id.sliderLbpRel);
        sliderLbpFc = findViewById(R.id.sliderLbpFc);

        sliderWideAmount = findViewById(R.id.sliderWideAmount);
        sliderWideMix = findViewById(R.id.sliderWideMix);
        sliderWidePre = findViewById(R.id.sliderWidePre);
        sliderWideLimit = findViewById(R.id.sliderWideLimit);
        sliderWideHpf = findViewById(R.id.sliderWideHpf);
        sliderWideFc = findViewById(R.id.sliderWideFc);

        // Continuous sliders, no snapping
        sliderPreamp.setStepSize(0f);
        sliderF1.setStepSize(0f);
        sliderQ1.setStepSize(0f);
        sliderG1.setStepSize(0f);
        sliderF2.setStepSize(0f);
        sliderQ2.setStepSize(0f);
        sliderG2.setStepSize(0f);

        sliderCrystAmount.setStepSize(0f);
        sliderCrystMix.setStepSize(0f);
        sliderCrystHz.setStepSize(0f);
        sliderCrystLimit.setStepSize(0f);
        sliderCrystPreDb.setStepSize(0f);
        sliderCrystPostDb.setStepSize(0f);

        sliderLbpThr.setStepSize(0f);
        sliderLbpAtk.setStepSize(0f);
        sliderLbpRel.setStepSize(0f);
        sliderLbpFc.setStepSize(0f);

        sliderWideAmount.setStepSize(0f);
        sliderWideMix.setStepSize(0f);
        sliderWidePre.setStepSize(0f);
        sliderWideLimit.setStepSize(0f);
        sliderWideHpf.setStepSize(0f);
        sliderWideFc.setStepSize(0f);

        // EQ enable (presets DO NOT set this)
        boolean enabled = "1".equals(getProp("persist.sys.gammaeq.enable", "0"));
        switchEnable.setChecked(enabled);
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setAudioProp("persist.sys.gammaeq.enable", isChecked ? "1" : "0");
            Toast.makeText(this, isChecked ? "EQ enabled" : "EQ disabled", Toast.LENGTH_SHORT).show();
        });

        // Preset dropdown
        reloadPresetDropdownItems();
        dropdownPreset.setKeyListener(null);
        dropdownPreset.setFocusable(false);
        dropdownPreset.setOnClickListener(v -> {
            reloadPresetDropdownItems();
            dropdownPreset.showDropDown();
        });
        dropdownPreset.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                reloadPresetDropdownItems();
                dropdownPreset.showDropDown();
            }
        });

        dropdownPreset.setOnItemClickListener((AdapterView<?> parent, android.view.View view, int position, long id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            if (PRESET_NAME.equals(selected)) {
                applySparklePreset();
                setSparkleSliders();
                updatePresetDropdownFromProps();
                Toast.makeText(this, "Applied " + PRESET_NAME, Toast.LENGTH_SHORT).show();
            } else if (CUSTOM_NAME.equals(selected)) {
                Toast.makeText(this, "Custom EQ", Toast.LENGTH_SHORT).show();
            } else {
                boolean ok = applyCustomPreset(selected);
                if (ok) {
                    // Props have changed; sliders will follow via poll, but we sync now too
                    loadFromPropsIntoUI();
                    dropdownPreset.setText(selected, false);
                    Toast.makeText(this, "Applied preset " + selected, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to load preset " + selected, Toast.LENGTH_SHORT).show();
                    dropdownPreset.setText(CUSTOM_NAME, false);
                }
            }
        });

        // Initial state
        loadFromPropsIntoUI();
        updatePeqLabels();

        // Apply: presets already pushed; custom is always live, so just show toast
        btnApply.setOnClickListener(v -> {
            String name = dropdownPreset.getText() != null ? dropdownPreset.getText().toString() : "";
            if (PRESET_NAME.equals(name)) {
                applySparklePreset();
                setSparkleSliders();
                updatePresetDropdownFromProps();
                Toast.makeText(this, "Applied " + PRESET_NAME, Toast.LENGTH_SHORT).show();
            } else if (CUSTOM_NAME.equals(name)) {
                Toast.makeText(this, "Applied custom", Toast.LENGTH_SHORT).show();
            } else if (name != null && !name.isEmpty()) {
                boolean ok = applyCustomPreset(name);
                if (ok) {
                    loadFromPropsIntoUI();
                    Toast.makeText(this, "Applied preset " + name, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Applied custom", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Applied custom", Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveCustom.setOnClickListener(v -> showSavePresetDialog());

        btnReset.setOnClickListener(v -> {
            applySparklePreset();
            setSparkleSliders();
            updatePresetDropdownFromProps();
            Toast.makeText(this, "Reset to " + PRESET_NAME, Toast.LENGTH_SHORT).show();
        });

        btnPreview.setOnClickListener(v -> togglePreview());

        // Real-time PEQ + Preamp → real props, no math
        Slider.OnChangeListener eqChangeListener = (slider, value, fromUser) -> {
            if (!fromUser) {
                updatePeqLabels();
                return;
            }
            int id = slider.getId();
            if (id == R.id.sliderPreamp) {
                setAudioProp("persist.sys.gammaeq.preamp_db", String.valueOf(value));
            } else if (id == R.id.sliderF1) {
                setAudioProp("persist.sys.spk.peq.b0", String.valueOf(value));
            } else if (id == R.id.sliderQ1) {
                setAudioProp("persist.sys.spk.peq.b1", String.valueOf(value));
            } else if (id == R.id.sliderG1) {
                setAudioProp("persist.sys.spk.peq.b2", String.valueOf(value));
            } else if (id == R.id.sliderF2) {
                setAudioProp("persist.sys.spk.peq2.b0", String.valueOf(value));
            } else if (id == R.id.sliderQ2) {
                setAudioProp("persist.sys.spk.peq2.b1", String.valueOf(value));
            } else if (id == R.id.sliderG2) {
                setAudioProp("persist.sys.spk.peq2.b2", String.valueOf(value));
            }
            updatePeqLabels();
        };
        sliderPreamp.addOnChangeListener(eqChangeListener);
        sliderF1.addOnChangeListener(eqChangeListener);
        sliderQ1.addOnChangeListener(eqChangeListener);
        sliderG1.addOnChangeListener(eqChangeListener);
        sliderF2.addOnChangeListener(eqChangeListener);
        sliderQ2.addOnChangeListener(eqChangeListener);
        sliderG2.addOnChangeListener(eqChangeListener);

        // Real-time module toggles → enable props only
        switchCryst.setOnCheckedChangeListener((buttonView, isChecked) ->
                setAudioProp("persist.sys.spk.cryst", isChecked ? "1" : "0"));
        switchLbp.setOnCheckedChangeListener((buttonView, isChecked) ->
                setAudioProp("persist.sys.spk.lbp", isChecked ? "1" : "0"));
        switchWide.setOnCheckedChangeListener((buttonView, isChecked) ->
                setAudioProp("persist.sys.spk.wide", isChecked ? "1" : "0"));

        // Real-time module sliders → only their own props
        Slider.OnChangeListener modulesChangeListener = (slider, value, fromUser) -> {
            if (!fromUser) return;
            int id = slider.getId();
            if (id == R.id.sliderCrystAmount) {
                setAudioProp("persist.sys.spk.cryst.amount", String.valueOf(value));
            } else if (id == R.id.sliderCrystMix) {
                setAudioProp("persist.sys.spk.cryst.mix", String.valueOf(value));
            } else if (id == R.id.sliderCrystHz) {
                setAudioProp("persist.sys.spk.cryst.hz", String.valueOf(value));
            } else if (id == R.id.sliderCrystLimit) {
                setAudioProp("persist.sys.spk.cryst.limit", String.valueOf(value));
            } else if (id == R.id.sliderCrystPreDb) {
                setAudioProp("persist.sys.spk.cryst.pregain_db", String.valueOf(value));
            } else if (id == R.id.sliderCrystPostDb) {
                setAudioProp("persist.sys.spk.cryst.postgain_db", String.valueOf(value));
            } else if (id == R.id.sliderLbpThr) {
                setAudioProp("persist.sys.spk.lbp.thr", String.valueOf(value));
            } else if (id == R.id.sliderLbpAtk) {
                setAudioProp("persist.sys.spk.lbp.atk", String.valueOf(value));
            } else if (id == R.id.sliderLbpRel) {
                setAudioProp("persist.sys.spk.lbp.rel", String.valueOf(value));
            } else if (id == R.id.sliderLbpFc) {
                setAudioProp("persist.sys.spk.lbp.fc", String.valueOf(value));
            } else if (id == R.id.sliderWideAmount) {
                setAudioProp("persist.sys.spk.wide.amount", String.valueOf(value));
            } else if (id == R.id.sliderWideMix) {
                setAudioProp("persist.sys.spk.wide.mix", String.valueOf(value));
            } else if (id == R.id.sliderWidePre) {
                setAudioProp("persist.sys.spk.wide.pre", String.valueOf(value));
            } else if (id == R.id.sliderWideLimit) {
                setAudioProp("persist.sys.spk.wide.limit", String.valueOf(value));
            } else if (id == R.id.sliderWideHpf) {
                setAudioProp("persist.sys.spk.wide.hpf", String.valueOf(value));
            } else if (id == R.id.sliderWideFc) {
                setAudioProp("persist.sys.spk.wide.fc", String.valueOf(value));
            }
        };
        sliderCrystAmount.addOnChangeListener(modulesChangeListener);
        sliderCrystMix.addOnChangeListener(modulesChangeListener);
        sliderCrystHz.addOnChangeListener(modulesChangeListener);
        sliderCrystLimit.addOnChangeListener(modulesChangeListener);
        sliderCrystPreDb.addOnChangeListener(modulesChangeListener);
        sliderCrystPostDb.addOnChangeListener(modulesChangeListener);

        sliderLbpThr.addOnChangeListener(modulesChangeListener);
        sliderLbpAtk.addOnChangeListener(modulesChangeListener);
        sliderLbpRel.addOnChangeListener(modulesChangeListener);
        sliderLbpFc.addOnChangeListener(modulesChangeListener);

        sliderWideAmount.addOnChangeListener(modulesChangeListener);
        sliderWideMix.addOnChangeListener(modulesChangeListener);
        sliderWidePre.addOnChangeListener(modulesChangeListener);
        sliderWideLimit.addOnChangeListener(modulesChangeListener);
        sliderWideHpf.addOnChangeListener(modulesChangeListener);
        sliderWideFc.addOnChangeListener(modulesChangeListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewTrack != null) {
            previewTrack.release();
            previewTrack = null;
        }
    }

    // ---- Preset file helpers ----

    private void ensurePresetDir() {
        File dir = new File(PRESET_DIR_PATH);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                Log.w(TAG, "Failed to create preset dir: " + PRESET_DIR_PATH);
            }
        }
    }

    private ArrayList<String> listCustomPresetNames() {
        ensurePresetDir();
        ArrayList<String> result = new ArrayList<>();
        File dir = new File(PRESET_DIR_PATH);
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            String lower = name.toLowerCase(Locale.US);
            if (!lower.endsWith(PRESET_EXT)) continue;
            int idx = name.lastIndexOf('.');
            if (idx <= 0) continue;
            String base = name.substring(0, idx).trim();
            if (base.isEmpty()) continue;
            if (PRESET_NAME.equalsIgnoreCase(base) || CUSTOM_NAME.equalsIgnoreCase(base)) continue;
            result.add(base);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private void reloadPresetDropdownItems() {
        ArrayList<String> itemsList = new ArrayList<>();
        itemsList.add(PRESET_NAME);
        itemsList.add(CUSTOM_NAME);
        itemsList.addAll(listCustomPresetNames());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                itemsList
        );
        dropdownPreset.setAdapter(adapter);
    }

    private void showSavePresetDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});

        String currentText = dropdownPreset.getText() != null ? dropdownPreset.getText().toString() : "";
        if (!PRESET_NAME.equals(currentText) && !CUSTOM_NAME.equals(currentText) && !currentText.isEmpty()) {
            input.setText(currentText);
            input.setSelection(currentText.length());
        }

        new AlertDialog.Builder(this)
                .setTitle("Save preset")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText() != null ? input.getText().toString() : "";
                    saveCurrentConfigAsPreset(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String sanitizePresetName(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString().trim();
        if (PRESET_NAME.equalsIgnoreCase(out) || CUSTOM_NAME.equalsIgnoreCase(out)) {
            out = out + "_1";
        }
        return out;
    }

    private void saveCurrentConfigAsPreset(String displayName) {
        String safe = sanitizePresetName(displayName);
        if (safe.isEmpty()) {
            Toast.makeText(this, "Invalid preset name", Toast.LENGTH_SHORT).show();
            return;
        }
        ensurePresetDir();
        File file = new File(PRESET_DIR_PATH, safe + PRESET_EXT);

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(file, false));
            bw.write("# GammaEQ preset\n");
            bw.write("# name=" + safe + "\n");
            for (String key : PRESET_PROP_KEYS) {
                String v = getProp(key, "");
                if (v == null) continue;
                v = v.trim();
                if (v.isEmpty()) continue;
                if (v.length() > 64) continue; // basic sanity clamp
                bw.write(key);
                bw.write("=");
                bw.write(v);
                bw.write("\n");
            }
            bw.flush();
            Toast.makeText(this, "Saved preset: " + safe, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save preset", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Failed to save preset " + safe + ": " + e.getMessage());
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException ignored) {
                }
            }
        }

        reloadPresetDropdownItems();
        dropdownPreset.setText(safe, false);
    }

    private Map<String, String> readPresetFile(File file) {
        Map<String, String> map = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line;
            int maxLines = 512; // simple guard
            int count = 0;
            while ((line = br.readLine()) != null && count < maxLines) {
                count++;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0 || eq == line.length() - 1) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (key.isEmpty() || value.isEmpty()) continue;
                if (!ALLOWED_PRESET_KEYS.contains(key)) continue;
                if (value.length() > 64) continue;
                map.put(key, value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading preset file " + file + ": " + e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
        return map;
    }

    private boolean applyCustomPreset(String name) {
        ensurePresetDir();
        File file = new File(PRESET_DIR_PATH, name + PRESET_EXT);
        if (!file.isFile()) {
            return false;
        }
        Map<String, String> kv = readPresetFile(file);
        if (kv.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, String> e : kv.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (!ALLOWED_PRESET_KEYS.contains(key)) continue;
            if (value == null) continue;
            value = value.trim();
            if (value.isEmpty()) continue;
            setAudioProp(key, value);
        }
        return true;
    }

    private boolean isNumericLike(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("[-+]?[0-9]*\\.?[0-9]+");
    }

    private boolean isConfigMatchingPresetMap(Map<String, String> preset) {
        for (Map.Entry<String, String> e : preset.entrySet()) {
            String key = e.getKey();
            String expected = e.getValue();
            if (expected == null) continue;
            String current = getProp(key, expected);
            if (isNumericLike(expected) && isNumericLike(current)) {
                try {
                    double a = Double.parseDouble(current);
                    double b = Double.parseDouble(expected);
                    if (Math.abs(a - b) > 1e-3) return false;
                } catch (Exception ex) {
                    if (!expected.equals(current)) return false;
                }
            } else {
                if (!expected.equals(current)) return false;
            }
        }
        return true;
    }

    private String findMatchingCustomPresetName() {
        ArrayList<String> names = listCustomPresetNames();
        if (names.isEmpty()) return null;
        for (String n : names) {
            File f = new File(PRESET_DIR_PATH, n + PRESET_EXT);
            if (!f.isFile()) continue;
            Map<String, String> map = readPresetFile(f);
            if (map.isEmpty()) continue;
            if (isConfigMatchingPresetMap(map)) {
                return n;
            }
        }
        return null;
    }

    // ---- Preview playback ----
    private void togglePreview() {
        try {
            if (previewTrack == null) {
                initPreviewTrack();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (previewTrack == null) return;

        if (!previewPlaying) {
            previewTrack.stop();
            previewTrack.reloadStaticData();
            previewTrack.play();
            previewPlaying = true;
            btnPreview.setText(getString(R.string.preview_stop));
        } else {
            previewTrack.pause();
            previewPlaying = false;
            btnPreview.setText(getString(R.string.preview_play));
        }
    }

    // Decode MP3 once and resample to 48k, PCM16, then load into MODE_STATIC AudioTrack
    private void initPreviewTrack() throws IOException {
        if (previewTrack != null) return;

        DecodeResult decode = decodePreviewToPcm();
        if (decode == null || decode.pcm == null || decode.pcm.length == 0) {
            return;
        }

        previewPcmData48k = resampleTo48k(decode.pcm, decode.sampleRate, decode.channelCount);
        if (previewPcmData48k == null || previewPcmData48k.length == 0) {
            return;
        }

        int outSampleRate = 48000;
        int channelMask = (decode.channelCount == 1)
                ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(outSampleRate)
                .setChannelMask(channelMask)
                .build();

        previewTrack = new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(previewPcmData48k.length)
                .build();

        previewTrack.write(previewPcmData48k, 0, previewPcmData48k.length);
    }

    private static class DecodeResult {
        byte[] pcm;
        int sampleRate;
        int channelCount;
    }

    private DecodeResult decodePreviewToPcm() throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.preview);
        extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();

        int trackIndex = -1;
        MediaFormat format = null;
        String mime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String m = f.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                trackIndex = i;
                format = f;
                mime = m;
                break;
            }
        }
        if (trackIndex < 0 || format == null || mime == null) {
            extractor.release();
            return null;
        }

        extractor.selectTrack(trackIndex);

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inIndex = codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    java.nio.ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                    if (inBuf != null) {
                        int sampleSize = extractor.readSampleData(inBuf, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            long ptsUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0);
                            extractor.advance();
                        }
                    }
                }
            }

            int outIndex = codec.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                java.nio.ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                if (outBuf != null && info.size > 0) {
                    byte[] chunk = new byte[info.size];
                    outBuf.get(chunk);
                    outBuf.clear();
                    out.write(chunk);
                }
                codec.releaseOutputBuffer(outIndex, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // ignore
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        DecodeResult res = new DecodeResult();
        res.pcm = out.toByteArray();
        res.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        res.channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        return res;
    }

    private byte[] resampleTo48k(byte[] pcmIn, int sampleRate, int channelCount) {
        if (pcmIn == null || pcmIn.length == 0) return pcmIn;
        if (sampleRate == 48000) return pcmIn;

        int bytesPerSample = 2;
        int bytesPerFrame = channelCount * bytesPerSample;
        int inFrames = pcmIn.length / bytesPerFrame;
        if (inFrames <= 1) return pcmIn;

        double ratio = 48000.0 / sampleRate;
        int outFrames = (int) Math.round(inFrames * ratio);
        byte[] out = new byte[outFrames * bytesPerFrame];

        for (int i = 0; i < outFrames; i++) {
            double inPos = i / ratio;
            int idx0 = (int) Math.floor(inPos);
            int idx1 = Math.min(idx0 + 1, inFrames - 1);
            double frac = inPos - idx0;

            for (int ch = 0; ch < channelCount; ch++) {
                int inOffset0 = (idx0 * channelCount + ch) * 2;
                int inOffset1 = (idx1 * channelCount + ch) * 2;

                short s0 = (short) ((pcmIn[inOffset0] & 0xff) |
                        (pcmIn[inOffset0 + 1] << 8));
                short s1 = (short) ((pcmIn[inOffset1] & 0xff) |
                        (pcmIn[inOffset1 + 1] << 8));

                int sample = (int) Math.round(s0 + (s1 - s0) * frac);
                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

                int outOffset = (i * channelCount + ch) * 2;
                out[outOffset] = (byte) (sample & 0xff);
                out[outOffset + 1] = (byte) ((sample >> 8) & 0xff);
            }
        }

        return out;
    }

    private void clearAllEqProps() {
        // Master controls
        setAudioProp("persist.sys.gammaeq.preamp_db", "0");
        setAudioProp("persist.sys.gammaeq.postgain_db", "0");
        // Coefficients intentionally left intact
    }

    private void loadFromPropsIntoUI() {
        // Preamp
        try {
            float pre = Float.parseFloat(getProp("persist.sys.gammaeq.preamp_db", "0"));
            sliderPreamp.setValue(pre);
        } catch (Exception ignored) {
        }

        // PEQ coefficients -> mapped to PEQ sliders
        setSliderFromProp(sliderF1, "persist.sys.spk.peq.b0", "1.30");
        setSliderFromProp(sliderQ1, "persist.sys.spk.peq.b1", "-1.40");
        setSliderFromProp(sliderG1, "persist.sys.spk.peq.b2", "0.60");

        setSliderFromProp(sliderF2, "persist.sys.spk.peq2.b0", "1.35");
        setSliderFromProp(sliderQ2, "persist.sys.spk.peq2.b1", "-0.95");
        setSliderFromProp(sliderG2, "persist.sys.spk.peq2.b2", "0.60");

        // Module toggles
        switchCryst.setChecked("1".equals(getProp("persist.sys.spk.cryst", "0")));
        switchLbp.setChecked("1".equals(getProp("persist.sys.spk.lbp", "0")));
        switchWide.setChecked("1".equals(getProp("persist.sys.spk.wide", "0")));

        // Crystalizer sliders
        setSliderFromProp(sliderCrystAmount, "persist.sys.spk.cryst.amount", "0");
        setSliderFromProp(sliderCrystMix, "persist.sys.spk.cryst.mix", "0");
        setSliderFromProp(sliderCrystHz, "persist.sys.spk.cryst.hz", "0");
        setSliderFromProp(sliderCrystLimit, "persist.sys.spk.cryst.limit", "0");
        setSliderFromProp(sliderCrystPreDb, "persist.sys.spk.cryst.pregain_db", "0");
        setSliderFromProp(sliderCrystPostDb, "persist.sys.spk.cryst.postgain_db", "0");

        // LBP sliders
        setSliderFromProp(sliderLbpThr, "persist.sys.spk.lbp.thr", "0");
        setSliderFromProp(sliderLbpAtk, "persist.sys.spk.lbp.atk", "0");
        setSliderFromProp(sliderLbpRel, "persist.sys.spk.lbp.rel", "0");
        setSliderFromProp(sliderLbpFc, "persist.sys.spk.lbp.fc", "0");

        // Wide sliders
        setSliderFromProp(sliderWideAmount, "persist.sys.spk.wide.amount", "0");
        setSliderFromProp(sliderWideMix, "persist.sys.spk.wide.mix", "0");
        setSliderFromProp(sliderWidePre, "persist.sys.spk.wide.pre", "0");
        setSliderFromProp(sliderWideLimit, "persist.sys.spk.wide.limit", "0");
        setSliderFromProp(sliderWideHpf, "persist.sys.spk.wide.hpf", "0");
        setSliderFromProp(sliderWideFc, "persist.sys.spk.wide.fc", "0");

        updatePresetDropdownFromProps();
        updatePeqLabels();
    }

    private void setSliderFromProp(Slider slider, String key, String def) {
        try {
            float value = Float.parseFloat(getProp(key, def));
            float from = slider.getValueFrom();
            float to = slider.getValueTo();
            if (value < from) value = from;
            if (value > to) value = to;
            slider.setValue(value);
        } catch (Exception ignored) {
        }
    }

    private void updatePeqLabels() {
        if (labelPreamp != null && sliderPreamp != null) {
            float v = sliderPreamp.getValue();
            float min = sliderPreamp.getValueFrom();
            float max = sliderPreamp.getValueTo();
            labelPreamp.setText(
                    String.format(
                            Locale.US,
                            "Preamp: %s dB (min %.1f, max %.1f)\n" +
                                    "Global headroom / loudness.",
                            formatSignedDb(v), min, max
                    )
            );
        }

        if (sliderF1 != null && labelF1 != null) {
            float v = sliderF1.getValue();
            labelF1.setText(
                    String.format(
                            Locale.US,
                            "PEQ1 b0: %.4f\nDirectly controls persist.sys.spk.peq.b0.",
                            v
                    )
            );
        }

        if (sliderQ1 != null && labelQ1 != null) {
            float v = sliderQ1.getValue();
            labelQ1.setText(
                    String.format(
                            Locale.US,
                            "PEQ1 b1: %.4f\nDirectly controls persist.sys.spk.peq.b1.",
                            v
                    )
            );
        }

        if (sliderG1 != null && labelG1 != null) {
            float v = sliderG1.getValue();
            labelG1.setText(
                    String.format(
                            Locale.US,
                            "PEQ1 b2: %.4f\nDirectly controls persist.sys.spk.peq.b2.",
                            v
                    )
            );
        }

        if (sliderF2 != null && labelF2 != null) {
            float v = sliderF2.getValue();
            labelF2.setText(
                    String.format(
                            Locale.US,
                            "PEQ2 b0: %.4f\nDirectly controls persist.sys.spk.peq2.b0.",
                            v
                    )
            );
        }

        if (sliderQ2 != null && labelQ2 != null) {
            float v = sliderQ2.getValue();
            labelQ2.setText(
                    String.format(
                            Locale.US,
                            "PEQ2 b1: %.4f\nDirectly controls persist.sys.spk.peq2.b1.",
                            v
                    )
            );
        }

        if (sliderG2 != null && labelG2 != null) {
            float v = sliderG2.getValue();
            labelG2.setText(
                    String.format(
                            Locale.US,
                            "PEQ2 b2: %.4f\nDirectly controls persist.sys.spk.peq2.b2.",
                            v
                    )
            );
        }
    }

    private String formatSignedDb(float v) {
        return String.format(Locale.US, v >= 0 ? "+%.2f" : "%.2f", v);
    }

    private void updatePresetDropdownFromProps() {
        String customMatch = findMatchingCustomPresetName();
        String target;
        if (customMatch != null) {
            target = customMatch;
        } else if (isCurrentStatePreset()) {
            target = PRESET_NAME;
        } else {
            target = CUSTOM_NAME;
        }

        String current = dropdownPreset.getText() != null ? dropdownPreset.getText().toString() : "";
        if (!target.equals(current)) {
            dropdownPreset.setText(target, false);
        }
    }

    // Sparkle preset: mirror your script exactly, no math, no changes to enable
    private void applySparklePreset() {
        setAudioProp("persist.sys.gammaeq.spk_only", "1");
        setAudioProp("persist.sys.gammaeq.force", "0");

        setAudioProp("persist.sys.gammaeq.preamp_db", "-1.0");
        setAudioProp("persist.sys.gammaeq.postgain_db", "0");

        setAudioProp("persist.sys.spk.cryst", "1");
        setAudioProp("persist.sys.spk.cryst.amount", "3.630137");
        setAudioProp("persist.sys.spk.cryst.mix", "0.25");
        setAudioProp("persist.sys.spk.cryst.hz", "11500");
        setAudioProp("persist.sys.spk.cryst.pregain_db", "-8");
        setAudioProp("persist.sys.spk.cryst.postgain_db", "9.363636");
        setAudioProp("persist.sys.spk.cryst.pre", "0.40");
        setAudioProp("persist.sys.spk.cryst.fc", "11500");
        setAudioProp("persist.sys.spk.cryst.limit", "0.30");

        setAudioProp("persist.sys.spk.lbp", "1");
        setAudioProp("persist.sys.spk.lbp.fc", "160");
        setAudioProp("persist.sys.spk.lbp.thr", "0.6934932");
        setAudioProp("persist.sys.spk.lbp.atk", "4");
        setAudioProp("persist.sys.spk.lbp.rel", "110");

        setAudioProp("persist.sys.spk.mp", "1");
        setAudioProp("persist.sys.spk.mp.hpf", "220");
        setAudioProp("persist.sys.spk.mp.lpf", "5800");
        setAudioProp("persist.sys.spk.mp.thr", "0.92");
        setAudioProp("persist.sys.spk.mp.atk", "2");
        setAudioProp("persist.sys.spk.mp.rel", "120");

        setAudioProp("persist.sys.spk.peq", "1");
        setAudioProp("persist.sys.spk.peq.b0", "1.30");
        setAudioProp("persist.sys.spk.peq.b1", "-1.40");
        setAudioProp("persist.sys.spk.peq.b2", "0.60");
        setAudioProp("persist.sys.spk.peq.a1", "0");
        setAudioProp("persist.sys.spk.peq.a2", "0");
        setAudioProp("persist.sys.spk.peq.pregain", "1.0");
        setAudioProp("persist.sys.spk.peq.keepheadroom", "1");
        setAudioProp("persist.sys.spk.peq.limit", "0");

        setAudioProp("persist.sys.spk.peq2", "1");
        setAudioProp("persist.sys.spk.peq2.b0", "2.9922945");
        setAudioProp("persist.sys.spk.peq2.b1", "-0.31506848");
        setAudioProp("persist.sys.spk.peq2.b2", "0.60");
        setAudioProp("persist.sys.spk.peq2.a1", "0");
        setAudioProp("persist.sys.spk.peq2.a2", "0");

        setAudioProp("persist.sys.spk.wide", "1");
        setAudioProp("persist.sys.spk.wide.mix", "0.91780823");
        setAudioProp("persist.sys.spk.wide.hpf", "5500");
        setAudioProp("persist.sys.spk.wide.amount", "1.6232877");
    }

    private void setSparkleSliders() {
        // Preamp
        setSliderFromProp(sliderPreamp, "persist.sys.gammaeq.preamp_db", "-1.0");

        // PEQ coefficients
        setSliderFromProp(sliderF1, "persist.sys.spk.peq.b0", "1.30");
        setSliderFromProp(sliderQ1, "persist.sys.spk.peq.b1", "-1.40");
        setSliderFromProp(sliderG1, "persist.sys.spk.peq.b2", "0.60");

        setSliderFromProp(sliderF2, "persist.sys.spk.peq2.b0", "2.9922945");
        setSliderFromProp(sliderQ2, "persist.sys.spk.peq2.b1", "-0.31506848");
        setSliderFromProp(sliderG2, "persist.sys.spk.peq2.b2", "0.60");

        // Crystalizer UI
        switchCryst.setChecked("1".equals(getProp("persist.sys.spk.cryst", "1")));
        setSliderFromProp(sliderCrystAmount, "persist.sys.spk.cryst.amount", "3.630137");
        setSliderFromProp(sliderCrystMix, "persist.sys.spk.cryst.mix", "0.25");
        setSliderFromProp(sliderCrystHz, "persist.sys.spk.cryst.hz", "11500");
        setSliderFromProp(sliderCrystPreDb, "persist.sys.spk.cryst.pregain_db", "-8");
        setSliderFromProp(sliderCrystPostDb, "persist.sys.spk.cryst.postgain_db", "9.363636");
        setSliderFromProp(sliderCrystLimit, "persist.sys.spk.cryst.limit", "0.30");

        // LBP UI
        switchLbp.setChecked("1".equals(getProp("persist.sys.spk.lbp", "1")));
        setSliderFromProp(sliderLbpFc, "persist.sys.spk.lbp.fc", "160");
        setSliderFromProp(sliderLbpThr, "persist.sys.spk.lbp.thr", "0.6934932");
        setSliderFromProp(sliderLbpAtk, "persist.sys.spk.lbp.atk", "4");
        setSliderFromProp(sliderLbpRel, "persist.sys.spk.lbp.rel", "110");

        // Wide UI
        switchWide.setChecked("1".equals(getProp("persist.sys.spk.wide", "1")));
        setSliderFromProp(sliderWideMix, "persist.sys.spk.wide.mix", "0.91780823");
        setSliderFromProp(sliderWideHpf, "persist.sys.spk.wide.hpf", "5500");
        setSliderFromProp(sliderWideAmount, "persist.sys.spk.wide.amount", "1.6232877");
        setSliderFromProp(sliderWidePre, "persist.sys.spk.wide.pre", "0.0");
        setSliderFromProp(sliderWideLimit, "persist.sys.spk.wide.limit", "0.0");

        updatePeqLabels();
    }

    private boolean isCurrentStatePreset() {
        // Only check Sparkle props; ignore enable
        if (!propEqualsStr("persist.sys.gammaeq.spk_only", "1")) return false;
        if (!propEqualsStr("persist.sys.gammaeq.force", "0")) return false;
        if (!propEqualsNum("persist.sys.gammaeq.preamp_db", -1.0)) return false;
        if (!propEqualsNum("persist.sys.gammaeq.postgain_db", 0.0)) return false;

        if (!propEqualsStr("persist.sys.spk.cryst", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.amount", 3.630137)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.mix", 0.25)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.hz", 11500.0)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.pregain_db", -8.0)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.postgain_db", 9.363636)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.pre", 0.40)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.fc", 11500.0)) return false;
        if (!propEqualsNum("persist.sys.spk.cryst.limit", 0.30)) return false;

        if (!propEqualsStr("persist.sys.spk.lbp", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.lbp.fc", 160.0)) return false;
        if (!propEqualsNum("persist.sys.spk.lbp.thr", 0.6934932)) return false;
        if (!propEqualsNum("persist.sys.spk.lbp.atk", 4.0)) return false;
        if (!propEqualsNum("persist.sys.spk.lbp.rel", 110.0)) return false;

        if (!propEqualsStr("persist.sys.spk.mp", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.mp.hpf", 220.0)) return false;
        if (!propEqualsNum("persist.sys.spk.mp.lpf", 5800.0)) return false;
        if (!propEqualsNum("persist.sys.spk.mp.thr", 0.92)) return false;
        if (!propEqualsNum("persist.sys.spk.mp.atk", 2.0)) return false;
        if (!propEqualsNum("persist.sys.spk.mp.rel", 120.0)) return false;

        if (!propEqualsStr("persist.sys.spk.peq", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.peq.b0", 1.30)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.b1", -1.40)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.b2", 0.60)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.a1", 0.0)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.a2", 0.0)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.pregain", 1.0)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.keepheadroom", 1.0)) return false;
        if (!propEqualsNum("persist.sys.spk.peq.limit", 0.0)) return false;

        if (!propEqualsStr("persist.sys.spk.peq2", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.peq2.b0", 2.9922945)) return false;
        if (!propEqualsNum("persist.sys.spk.peq2.b1", -0.31506848)) return false;
        if (!propEqualsNum("persist.sys.spk.peq2.b2", 0.60)) return false;
        if (!propEqualsNum("persist.sys.spk.peq2.a1", 0.0)) return false;
        if (!propEqualsNum("persist.sys.spk.peq2.a2", 0.0)) return false;

        if (!propEqualsStr("persist.sys.spk.wide", "1")) return false;
        if (!propEqualsNum("persist.sys.spk.wide.mix", 0.91780823)) return false;
        if (!propEqualsNum("persist.sys.spk.wide.hpf", 5500.0)) return false;
        if (!propEqualsNum("persist.sys.spk.wide.amount", 1.6232877)) return false;

        return true;
    }

    private boolean propEqualsStr(String key, String expected) {
        String v = getProp(key, "");
        return expected.equals(v);
    }

    private boolean propEqualsNum(String key, double expected) {
        try {
            String s = getProp(key, Double.toString(expected));
            double v = Double.parseDouble(s);
            return Math.abs(v - expected) < 1e-3;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method m = sp.getMethod("get", String.class, String.class);
            return (String) m.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static void setPropRaw(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method m = sp.getMethod("set", String.class, String.class);
            m.invoke(null, key, val);
        } catch (Exception ignored) {
        }
    }

    private void setAudioProp(String key, String val) {
        setPropRaw(key, val);
        bumpAllSeqs();
    }

    private void bumpAllSeqs() {
        bumpSeq("persist.sys.spk.peq.seq");
        bumpSeq("persist.sys.spk.cryst.seq");
        bumpSeq("persist.sys.spk.lbp.seq");
        bumpSeq("persist.sys.spk.mp.seq");
        bumpSeq("persist.sys.spk.wide.seq");
        bumpSeq("persist.sys.spk.peq2.seq");
    }

    private void bumpSeq(String key) {
        try {
            int cur = Integer.parseInt(getProp(key, "0"));
            setPropRaw(key, Integer.toString(cur + 1));
        } catch (Exception e) {
            setPropRaw(key, "1");
        }
    }
}

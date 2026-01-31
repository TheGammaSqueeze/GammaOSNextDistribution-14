package com.gammaos.gammaeq;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Html;
import android.text.method.LinkMovementMethod;
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
import java.io.InputStream;
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

    // Preview playback: keep the audio route hot (silent) and stream PCM from a WAV resource.
// This avoids codec bring-up (mp3 decode) and avoids first-start amplifier enable latency
// happening on the critical path of the Play button.
private AudioTrack previewTrack;
private Thread previewWriterThread;
private volatile boolean previewWriterRunning = false;

// WAV PCM payload (no header), in the exact format expected by AudioTrack.
private volatile byte[] previewPcm;
private volatile int previewSampleRate = 48000;
private volatile int previewChannelCount = 2;
private volatile int previewChannelMask = AudioFormat.CHANNEL_OUT_STEREO;

// When not previewing we keep output alive by writing small silent chunks at 0 volume.
private static final int PREVIEW_IDLE_SILENCE_MS = 40;
private static final int PREVIEW_WRITE_CHUNK_BYTES = 4096;
private volatile int previewWritePos = 0;
 
    // Guards to ensure preview init/threads are cancelled cleanly on lifecycle transitions.
    private final Object previewInitLock = new Object();
    private int previewInitGeneration = 0;
    private volatile boolean previewInitInFlight = false;

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

        TextView previewCredit = findViewById(R.id.previewCredit);
        if (previewCredit != null) {
            previewCredit.setText(Html.fromHtml(getString(R.string.preview_credit_html), Html.FROM_HTML_MODE_LEGACY));
            previewCredit.setMovementMethod(LinkMovementMethod.getInstance());
        }

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

        // Warm up preview audio path + load preview PCM so playback is instant when pressed.
        initPreviewAudioAsync();

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
        // Ensure we do not keep AudioFlinger/HAL active while backgrounded.
        suspendPreviewAudio(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Extra safety: if the activity is not visible, ensure all audio threads/tracks are gone.
        suspendPreviewAudio(true);

        // If you want GammaEQ to fully exit when leaving the activity, finish once we are stopped.
        // Avoid breaking rotations / config changes.
        if (!isChangingConfigurations()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        suspendPreviewAudio(true);
    }

    private boolean isActivityAlive() {
        return !(isFinishing() || isDestroyed());
    }

    /**
     * Stop any preview playback, terminate writer threads, and optionally release the AudioTrack.
     *
     * Must be safe to call repeatedly from lifecycle callbacks.
     */
    private void suspendPreviewAudio(boolean releaseTrack) {
        // Advance generation so any in-flight init thread will self-abort.
        synchronized (previewInitLock) {
            previewInitGeneration++;
        }

        previewPlaying = false;
        previewWritePos = 0;

        // Releasing the AudioTrack can unblock a writer thread stuck inside AudioTrack.write().
        if (releaseTrack) {
            releasePreviewTrackLocked();
        }

        stopPreviewWriter();

        if (!releaseTrack) {
            // If we keep the track around for any reason, ensure it is muted and idle.
            AudioTrack t = previewTrack;
            if (t != null) {
                try {
                    t.setVolume(0.0f);
                } catch (Exception ignored) {
                }
            }
        }

        // Reset UI if we are still alive.
        if (isActivityAlive()) {
            runOnUiThread(() -> btnPreview.setText(getString(R.string.preview_play)));
        }
    }

    private void releasePreviewTrackLocked() {
        AudioTrack t = previewTrack;
        previewTrack = null;
        if (t != null) {
            try {
                t.pause();
            } catch (Exception ignored) {
            }
            try {
                t.flush();
            } catch (Exception ignored) {
            }
            try {
                t.release();
            } catch (Exception ignored) {
            }
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
    private void initPreviewAudioAsync() {
        // Do not block UI thread: load WAV + start a silent priming track in the background.
        final int gen;
        synchronized (previewInitLock) {
            if (previewTrack != null || previewWriterRunning || previewInitInFlight) return;
            previewInitInFlight = true;
            gen = previewInitGeneration;
        }

        Thread initThread = new Thread(() -> {
            try {
                // 1) Load WAV PCM (no decode at runtime)
                WavPcm w = readWavPcmFromRaw(R.raw.preview_fast);

                // Abort if we got backgrounded/destroyed while loading.
                synchronized (previewInitLock) {
                    if (previewInitGeneration != gen) return;
                }

                if (w != null && w.pcm != null && w.pcm.length > 0) {
                    previewPcm = w.pcm;
                    previewSampleRate = w.sampleRate;
                    previewChannelCount = w.channelCount;
                    previewChannelMask = (w.channelCount == 1)
                            ? AudioFormat.CHANNEL_OUT_MONO
                            : AudioFormat.CHANNEL_OUT_STEREO;
                }
 
                // Abort again before touching AudioTrack.
                synchronized (previewInitLock) {
                    if (previewInitGeneration != gen) return;
                }

                // 2) Create AudioTrack and start the writer thread that keeps the route alive.
                initPreviewTrackLocked();
                startPreviewWriter();
            } catch (Throwable t) {
                Log.w(TAG, "Failed to init preview audio: " + t.getMessage());
            } finally {
                synchronized (previewInitLock) {
                    previewInitInFlight = false;
                }
            }
        }, "GammaEQ-PreviewInit");

        // Avoid keeping the process alive if we ever fail to stop this thread.
        initThread.setDaemon(true);
        initThread.start();
    }

    private void togglePreview() {
        if (previewPlaying) {
            stopPreview();
            return;
        }
        previewPlaying = true;
        previewWritePos = 0;

        // Ensure audio path is active now (writer thread will switch from silence to PCM).
        if (previewTrack == null) {
            initPreviewAudioAsync();
        } else {
            // Unmute immediately; writer thread will begin writing PCM on its next cycle.
            try {
                previewTrack.setVolume(1.0f);
            } catch (Exception ignored) {
            }
        }

        btnPreview.setText(getString(R.string.preview_stop));
    }

    private void stopPreview() {
        previewPlaying = false;
        btnPreview.setText(getString(R.string.preview_play));

        // Drop back to silent keepalive.
        if (previewTrack != null) {
            try {
                previewTrack.setVolume(0.0f);
            } catch (Exception ignored) {
            }
        }
    }

    private void initPreviewTrackLocked() {
        if (previewTrack != null) return;

        int sampleRate = previewSampleRate > 0 ? previewSampleRate : 48000;
        int channelMask = previewChannelMask != 0 ? previewChannelMask : AudioFormat.CHANNEL_OUT_STEREO;

        final int minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT);

        final int bufSize = Math.max(minBuf, 2 * PREVIEW_WRITE_CHUNK_BYTES);

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();

        previewTrack = new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(bufSize)
                .build();

        // Start muted: we keep the route hot with silence until the user presses Play.
        try {
            previewTrack.setVolume(0.0f);
        } catch (Exception ignored) {
        }
        previewTrack.play();
    }

    private void startPreviewWriter() {
        if (previewWriterRunning) return;
        previewWriterRunning = true;

        previewWriterThread = new Thread(() -> {
            // Audio thread: keep it off the UI and slightly elevated.
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            } catch (Throwable ignored) {
            }

            final byte[] silence = buildSilencePcm(previewSampleRate, previewChannelCount, PREVIEW_IDLE_SILENCE_MS);

            while (previewWriterRunning && !Thread.currentThread().isInterrupted()) {
                final AudioTrack t = previewTrack;
                if (t == null) {
                    sleepQuiet(50);
                    continue;
                }

                // Ensure track stays in PLAYING state so the HAL route + PA stays enabled.
                if (t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        t.play();
                    } catch (Exception ignored) {
                    }
                }

                if (!previewPlaying || previewPcm == null || previewPcm.length == 0) {
                    // Idle keepalive: write small silence chunks at 0 volume.
                    try {
                        t.setVolume(0.0f);
                    } catch (Exception ignored) {
                    }
                    int wrote = t.write(silence, 0, silence.length);
                    if (wrote <= 0) {
                        sleepQuiet(20);
                    } else {
                        sleepQuiet(PREVIEW_IDLE_SILENCE_MS);
                    }
                    continue;
                }

                // Preview playing: unmute + stream PCM.
                try {
                    t.setVolume(1.0f);
                } catch (Exception ignored) {
                }

                final byte[] pcm = previewPcm;
                if (previewWritePos >= pcm.length) {
                    // Stop at end rather than looping.
                    previewPlaying = false;
                    previewWritePos = 0;
                    if (isActivityAlive()) {
                        runOnUiThread(() -> btnPreview.setText(getString(R.string.preview_play)));
                    }
                    continue;
                }

                final int remaining = pcm.length - previewWritePos;
                final int toWrite = Math.min(remaining, PREVIEW_WRITE_CHUNK_BYTES);
                int wrote = t.write(pcm, previewWritePos, toWrite);
                if (wrote > 0) {
                    previewWritePos += wrote;
                } else {
                    // Avoid a tight loop on write errors/underruns.
                    sleepQuiet(10);
                }
            }
        }, "GammaEQ-PreviewWriter");

        // If anything goes wrong, avoid keeping the process alive due to a stray non-daemon thread.
        previewWriterThread.setDaemon(true);
        previewWriterThread.start();
    }

    private void stopPreviewWriter() {
        previewWriterRunning = false;
        Thread t = previewWriterThread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                Log.w(TAG, "Preview writer thread did not stop in time");
            }
        }
        previewWriterThread = null;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] buildSilencePcm(int sampleRate, int channels, int durationMs) {
        int sr = sampleRate > 0 ? sampleRate : 48000;
        int ch = channels > 0 ? channels : 2;
        int frames = (sr * durationMs) / 1000;
        int bytes = frames * ch * 2;
        return new byte[Math.max(bytes, 2 * ch * 2)];
    }

    private static final class WavPcm {
        final byte[] pcm;
        final int sampleRate;
        final int channelCount;

        WavPcm(byte[] pcm, int sampleRate, int channelCount) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
        }
    }

    /**
     * Minimal WAV parser for PCM16 LE.
     * Returns the raw PCM payload without the WAV container header.
     */
    private WavPcm readWavPcmFromRaw(int resId) throws IOException {
        InputStream in = getResources().openRawResource(resId);
        try {
            byte[] all = readFully(in);
            if (all.length < 44) return null;

            // RIFF header
            if (!asciiEq(all, 0, "RIFF") || !asciiEq(all, 8, "WAVE")) return null;

            int offset = 12;
            int fmtChannels = 0;
            int fmtSampleRate = 0;
            int fmtBits = 0;
            int fmtAudioFormat = 0;

            int dataOffset = -1;
            int dataSize = -1;

            while (offset + 8 <= all.length) {
                String chunkId = ascii4(all, offset);
                int chunkSize = le32(all, offset + 4);
                int chunkData = offset + 8;

                if ("fmt ".equals(chunkId) && chunkData + Math.min(chunkSize, 16) <= all.length) {
                    fmtAudioFormat = le16(all, chunkData);
                    fmtChannels = le16(all, chunkData + 2);
                    fmtSampleRate = le32(all, chunkData + 4);
                    fmtBits = le16(all, chunkData + 14);
                } else if ("data".equals(chunkId) && chunkData + chunkSize <= all.length) {
                    dataOffset = chunkData;
                    dataSize = chunkSize;
                    break;
                }

                // chunks are word-aligned (pad to even)
                offset = chunkData + chunkSize + (chunkSize & 1);
            }

            if (dataOffset < 0 || dataSize <= 0) return null;
            // PCM format 1, 16-bit
            if (fmtAudioFormat != 1 || fmtBits != 16 || fmtChannels <= 0 || fmtSampleRate <= 0) return null;

            byte[] pcm = new byte[dataSize];
            System.arraycopy(all, dataOffset, pcm, 0, dataSize);
            return new WavPcm(pcm, fmtSampleRate, fmtChannels);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static boolean asciiEq(byte[] b, int off, String s) {
        if (off + s.length() > b.length) return false;
        for (int i = 0; i < s.length(); i++) {
            if ((byte) s.charAt(i) != b[off + i]) return false;
        }
        return true;
    }

    private static String ascii4(byte[] b, int off) {
        if (off + 4 > b.length) return "";
        return "" + (char) b[off] + (char) b[off + 1] + (char) b[off + 2] + (char) b[off + 3];
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff)
                | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16)
                | ((b[off + 3] & 0xff) << 24);
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

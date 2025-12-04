package com.gammaos.gammaeq;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MaterialSwitch switchEnable;
    private MaterialAutoCompleteTextView dropdownPreset;
    private MaterialButton btnApply, btnSaveCustom, btnReset, btnPreview;
    private Slider sliderPreamp, sliderF1, sliderQ1, sliderG1, sliderF2, sliderQ2, sliderG2;

    private MediaPlayer player;
    private MaterialSwitch switchCryst, switchLbp, switchWide;
    private Slider sliderCrystAmount, sliderCrystMix, sliderCrystHz, sliderCrystLimit, sliderCrystPreDb, sliderCrystPostDb;
    private Slider sliderLbpThr, sliderLbpAtk, sliderLbpRel, sliderLbpFc;
    private Slider sliderWideAmount, sliderWideMix, sliderWidePre, sliderWideLimit, sliderWideHpf, sliderWideFc;
    
    private boolean previewPlaying = false;

    // Preset container: name -> config
    private final LinkedHashMap<String, Preset> presets = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind
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
    

        // Load current state
        boolean enabled = "1".equals(getProp("persist.sys.gammaeq.enable", "0"));
        switchEnable.setChecked(enabled);
        switchEnable.addOnCheckedChangeListener((buttonView, isChecked) -> {
            setProp("persist.sys.gammaeq.enable", isChecked ? "1" : "0");
            Toast.makeText(this, isChecked ? "EQ enabled" : "EQ disabled", Toast.LENGTH_SHORT).show();
        });

        // Define presets
        presets.put("Flat", new Preset(0f, 0f, 1000f, 1.0f, 0f, 3000f, 1.0f));
        presets.put("Bass Boost", new Preset(-2f, 6f, 100f, 1.0f, 3f, 60f, 1.0f));
        presets.put("Treble Boost", new Preset(-2f, 0f, 4000f, 1.0f, 6f, 9000f, 0.8f));
        presets.put("Vocal", new Preset(0f, 3f, 1000f, 1.0f, 4f, 3000f, 1.0f));
        presets.put("Rock", new Preset(-1f, 4f, 120f, 0.9f, 4f, 8000f, 0.9f));
        presets.put("Pop", new Preset(-1f, 3f, 150f, 1.1f, 2f, 4000f, 1.1f));
        presets.put("Classical", new Preset(0f, 0f, 250f, 0.7f, 2f, 6000f, 0.7f));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, presets.keySet().toArray(new String[0]));
        dropdownPreset.setAdapter(adapter);
        dropdownPreset.setText("Flat", false);

        // Initialize sliders from system properties if available
        loadFromPropsIntoUI();

        btnApply.setOnClickListener(v -> {
            // Apply currently selected preset or custom values
            String name = dropdownPreset.getText() != null ? dropdownPreset.getText().toString() : "";
            Preset p = presets.get(name);
            if (p != null) {
                // Set sliders according to preset, then push to system
                sliderPreamp.setValue(p.preamp);
                sliderG1.setValue(p.g1);
                sliderF1.setValue(p.f1);
                sliderQ1.setValue(p.q1);
                sliderG2.setValue(p.g2);
                sliderF2.setValue(p.f2);
                sliderQ2.setValue(p.q2);
            }
            pushCurrentUIToProps();
            pushModulesToProps();
    
            Toast.makeText(this, "Applied", Toast.LENGTH_SHORT).show();
        });

        btnSaveCustom.setOnClickListener(v -> {
            pushCurrentUIToProps();
            Toast.makeText(this, "Saved custom profile", Toast.LENGTH_SHORT).show();
        });

        btnReset.setOnClickListener(v -> {
            clearAllEqProps();
switchCryst.setChecked(false);
switchLbp.setChecked(false);
switchWide.setChecked(false);
            sliderPreamp.setValue(0f);
            sliderF1.setValue(1000f);
            sliderQ1.setValue(1.0f);
            sliderG1.setValue(0f);
            sliderF2.setValue(3000f);
            sliderQ2.setValue(1.0f);
            sliderG2.setValue(0f);
            Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show();
        });

        btnPreview.setOnClickListener(v -> togglePreview());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ---- Preview playback ----
    private void togglePreview() {
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.preview);
            if (player != null) {
                player.setLooping(true);
            }
        }
        if (player == null) return;

        if (!previewPlaying) {
            player.start();
            previewPlaying = true;
            btnPreview.setText(getString(R.string.preview_stop));
        } else {
            player.pause();
            previewPlaying = false;
            btnPreview.setText(getString(R.string.preview_play));
        }
    }

    // ---- Property application ----

    private void pushCurrentUIToProps() {
        float preamp = sliderPreamp.getValue();
        setProp("persist.sys.gammaeq.preamp_db", String.valueOf(preamp));

        // Two peaking filters
        applyPeqStage("persist.sys.spk.peq", sliderF1.getValue(), sliderQ1.getValue(), sliderG1.getValue());
        applyPeqStage("persist.sys.spk.peq2", sliderF2.getValue(), sliderQ2.getValue(), sliderG2.getValue());

        // Enable stages if gains are not zero
        boolean s1 = Math.abs(sliderG1.getValue()) > 0.05f;
        boolean s2 = Math.abs(sliderG2.getValue()) > 0.05f;
        setProp("persist.sys.spk.peq", s1 ? "1" : "0");
        setProp("persist.sys.spk.peq2", s2 ? "1" : "0");
        setProp("persist.sys.spk.peq.pregain", "1.0");
        setProp("persist.sys.spk.peq.keepheadroom", "1");
    }

    private void clearAllEqProps() {
        // Master controls
        setProp("persist.sys.gammaeq.preamp_db", "0");
        setProp("persist.sys.gammaeq.postgain_db", "0");

        // Disable modules
        setProp("persist.sys.spk.peq", "0");
        setProp("persist.sys.spk.peq2", "0");
        setProp("persist.sys.spk.cryst", "0");
        setProp("persist.sys.spk.lbp", "0");
        setProp("persist.sys.spk.wide", "0");
    }

    private void loadFromPropsIntoUI() {
        try {
            float pre = Float.parseFloat(getProp("persist.sys.gammaeq.preamp_db", "0"));
            sliderPreamp.setValue(pre);
        } catch (Exception ignored) {}

        // We cannot recover original freq/Q/gain from coefficients, so leave defaults.
        // Module defaults
        switchCryst.setChecked("1".equals(getProp("persist.sys.spk.cryst", "0")));
        switchLbp.setChecked("1".equals(getProp("persist.sys.spk.lbp", "0")));
        switchWide.setChecked("1".equals(getProp("persist.sys.spk.wide", "0")));
    
    }

    // Compute peaking EQ biquad at fs=48000 with a0 normalized to 1.0, then push props.
    private void applyPeqStage(String base, float freq, float q, float gainDb) {
        double fs = 48000.0;
        // Clamp ranges
        freq = clamp(freq, 20f, 20000f);
        q = clamp(q, 0.1f, 10f);

        double A = Math.pow(10.0, gainDb / 40.0); // amplitude from dB
        double w0 = 2.0 * Math.PI * (double)freq / fs;
        double alpha = Math.sin(w0) / (2.0 * (double)q);

        double b0 = 1 + alpha * A;
        double b1 = -2 * Math.cos(w0);
        double b2 = 1 - alpha * A;
        double a0 = 1 + alpha / A;
        double a1 = -2 * Math.cos(w0);
        double a2 = 1 - alpha / A;

        // normalize to a0=1
        b0 /= a0; b1 /= a0; b2 /= a0;
        a1 /= a0; a2 /= a0;

        setProp(base, Math.abs(gainDb) > 0.05f ? "1" : "0");
        setProp(base + ".b0", String.valueOf(b0));
        setProp(base + ".b1", String.valueOf(b1));
        setProp(base + ".b2", String.valueOf(b2));
        setProp(base + ".a1", String.valueOf(-a1)); // FastMixer expects direct form signs? ensure mapping
        setProp(base + ".a2", String.valueOf(-a2));
        setProp(base + ".seq", "1");
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- SystemProperties helpers ----
    private static String getProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method m = sp.getMethod("get", String.class, String.class);
            return (String) m.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static void setProp(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method m = sp.getMethod("set", String.class, String.class);
            m.invoke(null, key, val);
        } catch (Exception ignored) {}
    }

    static class Preset {
        float preamp;
        float g1, f1, q1;
        float g2, f2, q2;
        Preset(float preamp, float g1, float f1, float q1, float g2, float f2, float q2) {
            this.preamp = preamp;
            this.g1 = g1; this.f1 = f1; this.q1 = q1;
            this.g2 = g2; this.f2 = f2; this.q2 = q2;
        }
    }

    private void pushModulesToProps() {
        // Crystalizer
        setProp("persist.sys.spk.cryst", switchCryst.isChecked() ? "1" : "0");
        setProp("persist.sys.spk.cryst.amount", String.valueOf(sliderCrystAmount.getValue()));
        setProp("persist.sys.spk.cryst.mix", String.valueOf(sliderCrystMix.getValue()));
        setProp("persist.sys.spk.cryst.hz", String.valueOf(sliderCrystHz.getValue()));
        setProp("persist.sys.spk.cryst.limit", String.valueOf(sliderCrystLimit.getValue()));
        setProp("persist.sys.spk.cryst.pregain_db", String.valueOf(sliderCrystPreDb.getValue()));
        setProp("persist.sys.spk.cryst.postgain_db", String.valueOf(sliderCrystPostDb.getValue()));
        bumpSeq("persist.sys.spk.cryst.seq");

        // LBP
        setProp("persist.sys.spk.lbp", switchLbp.isChecked() ? "1" : "0");
        setProp("persist.sys.spk.lbp.thr", String.valueOf(sliderLbpThr.getValue()));
        setProp("persist.sys.spk.lbp.atk", String.valueOf(sliderLbpAtk.getValue()));
        setProp("persist.sys.spk.lbp.rel", String.valueOf(sliderLbpRel.getValue()));
        setProp("persist.sys.spk.lbp.fc", String.valueOf(sliderLbpFc.getValue()));
        bumpSeq("persist.sys.spk.lbp.seq");

        // Wide
        setProp("persist.sys.spk.wide", switchWide.isChecked() ? "1" : "0");
        setProp("persist.sys.spk.wide.amount", String.valueOf(sliderWideAmount.getValue()));
        setProp("persist.sys.spk.wide.mix", String.valueOf(sliderWideMix.getValue()));
        setProp("persist.sys.spk.wide.pre", String.valueOf(sliderWidePre.getValue()));
        setProp("persist.sys.spk.wide.limit", String.valueOf(sliderWideLimit.getValue()));
        setProp("persist.sys.spk.wide.hpf", String.valueOf(sliderWideHpf.getValue()));
        setProp("persist.sys.spk.wide.fc", String.valueOf(sliderWideFc.getValue()));
        bumpSeq("persist.sys.spk.wide.seq");
    }

    private void bumpSeq(String key) {
        try {
            int cur = Integer.parseInt(getProp(key, "0"));
            setProp(key, Integer.toString(cur + 1));
        } catch (Exception e) {
            setProp(key, "1");
        }
    }
    
}
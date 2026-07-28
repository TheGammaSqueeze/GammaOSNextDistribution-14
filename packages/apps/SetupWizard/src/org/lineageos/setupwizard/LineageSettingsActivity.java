package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.io.File;
import java.io.RandomAccessFile;

import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class LineageSettingsActivity extends Activity {

    private static final String PROP_RUN = "persist.gammaos.setupwizard_run";
    private static final String PROP_DONE = "persist.gammaos.setupwizard_done";
    private static final String PROP_EXIT_CODE = "persist.gammaos.setupwizard_exit_code";
    private static final long LOG_TAIL_INTERVAL_MS = 250;

    // New GammaPad daemon properties
    private static final String PROP_ENABLE = "persist.gammaos.gamepad.enable";
    private static final String PROP_INVERT_LEFT = "persist.gammaos.gamepad.invert_left";
    private static final String PROP_INVERT_RIGHT = "persist.gammaos.gamepad.invert_right";
    private static final String PROP_DEVICES = "persist.gammaos.gamepad.devices";
    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";

    // The controller / analog-calibration screen is suppressed by default; it is only shown
    // when this prop is enabled ("1"/"true"). When hidden, the system-configuration step
    // (setup.sh) still runs headlessly and the wizard advances as usual.
    private static final String PROP_SHOW_CONTROLLER = "persist.gammaos.setup.show_controller";

    private static final int REQUEST_CALIBRATION = 1001;

    private StickTestView leftStickView, rightStickView;
    private ScrollView controlScroll, scrollView;
    private TextView outputTextView, headingTextView;
    private Button continueButton;

    // Controller chooser
    private LinearLayout controllerList;
    private int mSelectedControllerBtnId = -1;

    // Button groups for highlight
    private final int[] invLGroup = { R.id.btn_invert_left_off, R.id.btn_invert_left_on };
    private final int[] invRGroup = { R.id.btn_invert_right_off, R.id.btn_invert_right_on };

    // Right stick auto-detection for live display
    private float mLastX, mLastY, mLastRX, mLastRY;
    private float mLastRX_std, mLastRY_std;
    private float mLastRX_alt, mLastRY_alt;
    private float mLastRX_z, mLastRY_z;
    private int mDetectedAndroidAxisRX = -1;
    private boolean mRightStickDetected = false;

    // Prevent predictive back on Android 13+ (T+) and gesture/virtual back.
    private OnBackInvokedCallback mNoOpBackCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on for the entire setup process
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Ensure content extends behind system bars.
        try {
            getWindow().setDecorFitsSystemWindows(false);
        } catch (Throwable ignored) {}
        applyImmersiveLocal();

        // Block BACK (gesture & button) on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) {
            mNoOpBackCallback = () -> { /* no-op */ };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNoOpBackCallback);
        }

        // Enable gamepad daemon on entry
        SystemProperties.set(PROP_ENABLE, "1");
        bumpConfigVersion();

        setContentView(R.layout.setup_lineage_settings);

        leftStickView = findViewById(R.id.left_stick_view);
        rightStickView = findViewById(R.id.right_stick_view);
        controlScroll = findViewById(R.id.control_scroll);
        scrollView = findViewById(R.id.scrollView);
        outputTextView = findViewById(R.id.script_output_text_view);
        headingTextView = findViewById(R.id.headingTextView);
        continueButton = findViewById(R.id.continue_button);

        // GammaOS: the controller / analog-calibration screen is suppressed by default and is
        // only shown when persist.gammaos.setup.show_controller is enabled. When hidden, run
        // the system-configuration step (setup.sh) headlessly and advance the wizard, so the
        // flow is unchanged for everyone who has not explicitly opted in.
        if (!SystemProperties.getBoolean(PROP_SHOW_CONTROLLER, false)) {
            runSetupScript();
            return;
        }

        // Controller chooser
        controllerList = findViewById(R.id.controller_list);
        populateControllerList();

        // Invert sticks
        bindPropToggle(R.id.btn_invert_left_off,  PROP_INVERT_LEFT,  "0", invLGroup);
        bindPropToggle(R.id.btn_invert_left_on,   PROP_INVERT_LEFT,  "1", invLGroup);
        bindPropToggle(R.id.btn_invert_right_off, PROP_INVERT_RIGHT, "0", invRGroup);
        bindPropToggle(R.id.btn_invert_right_on,  PROP_INVERT_RIGHT, "1", invRGroup);

        // Highlight current values from props
        highlightCurrentValues();

        // Calibrate button - launch the system Settings calibration activity
        findViewById(R.id.btn_calibrate).setOnClickListener(v -> {
            Intent intent = new Intent(
                    "org.lineageos.lineageparts.GAMEPAD_CALIBRATION");
            startActivityForResult(intent, REQUEST_CALIBRATION);
        });

        // Continue -> run setup.sh with root, stream output, then advance wizard
        continueButton.setOnClickListener(v -> runSetupScript());
    }

    // Hide the controller controls, show the config log, run setup.sh via init and advance the
    // wizard on completion. Used both by the Continue button and by the headless path taken
    // when the controller screen is suppressed (persist.gammaos.setup.show_controller off).
    private void runSetupScript() {
        headingTextView.setText("Configuring GammaOS Next...");
        controlScroll.setVisibility(View.GONE);
        continueButton.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);

        outputTextView.setText("");
        File logFile = new File(getFilesDir(), "gammaos_setup.log");
        if (logFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logFile.delete();
        }
        new RunSetupViaInitTask(logFile).execute();
    }

    private void populateControllerList() {
        controllerList.removeAllViews();

        // "All" button
        Button allBtn = new Button(this);
        allBtn.setText(R.string.gs_all_controllers);
        allBtn.setId(View.generateViewId());
        int allBtnId = allBtn.getId();
        allBtn.setOnClickListener(v -> {
            SystemProperties.set(PROP_DEVICES, "");
            bumpConfigVersion();
            highlightControllerSelection(allBtnId);
        });
        controllerList.addView(allBtn);

        // Enumerate connected gamepads (skip daemon's virtual device)
        String daemonDevName = SystemProperties.get(
                "persist.gammaos.gamepad.device_name", "Xbox Wireless Controller");
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null) continue;
            if ((dev.getSources() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
                continue;
            }
            String name = dev.getName();
            if (name == null || name.isEmpty()) continue;
            // Skip the daemon's virtual gamepad to avoid recursion
            if (name.equals(daemonDevName)) continue;

            Button btn = new Button(this);
            btn.setText(name);
            btn.setId(View.generateViewId());
            int btnId = btn.getId();
            btn.setOnClickListener(v -> {
                SystemProperties.set(PROP_DEVICES, name);
                bumpConfigVersion();
                highlightControllerSelection(btnId);
            });
            controllerList.addView(btn);
        }

        // Highlight based on current prop
        String currentDevices = SystemProperties.get(PROP_DEVICES, "");
        if (currentDevices.isEmpty()) {
            highlightControllerSelection(allBtnId);
        } else {
            // Try to find matching button
            for (int i = 1; i < controllerList.getChildCount(); i++) {
                Button btn = (Button) controllerList.getChildAt(i);
                if (currentDevices.equals(btn.getText().toString())) {
                    highlightControllerSelection(btn.getId());
                    break;
                }
            }
        }
    }

    private void highlightControllerSelection(int selectedId) {
        mSelectedControllerBtnId = selectedId;
        for (int i = 0; i < controllerList.getChildCount(); i++) {
            View child = controllerList.getChildAt(i);
            child.setAlpha(child.getId() == selectedId ? 1f : 0.5f);
        }
    }

    private void highlightCurrentValues() {
        // Invert left
        int invL = SystemProperties.getInt(PROP_INVERT_LEFT, 0);
        highlightSelection(invLGroup, invL == 1 ? R.id.btn_invert_left_on : R.id.btn_invert_left_off);

        // Invert right
        int invR = SystemProperties.getInt(PROP_INVERT_RIGHT, 0);
        highlightSelection(invRGroup, invR == 1 ? R.id.btn_invert_right_on : R.id.btn_invert_right_off);
    }

    // --- Right stick detection for live display ---

    private void detectRightStick() {
        if (mRightStickDetected) return;

        float magStd = mLastRX_std * mLastRX_std + mLastRY_std * mLastRY_std;
        float magAlt = mLastRX_alt * mLastRX_alt + mLastRY_alt * mLastRY_alt;
        float magZ = mLastRX_z * mLastRX_z + mLastRY_z * mLastRY_z;

        float maxMag = Math.max(magStd, Math.max(magAlt, magZ));
        if (maxMag < 0.04f) return;

        if (maxMag == magZ && magZ > 0.04f) {
            mDetectedAndroidAxisRX = 0;
            mRightStickDetected = true;
        } else if (maxMag == magAlt && magAlt > 0.04f) {
            mDetectedAndroidAxisRX = 1;
            mRightStickDetected = true;
        } else if (magStd > 0.04f) {
            mDetectedAndroidAxisRX = 2;
            mRightStickDetected = true;
        }
    }

    // --- Utility ---

    private void bumpConfigVersion() {
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }

    private void bindPropToggle(int btnId, String prop, String value, int[] group) {
        Button b = findViewById(btnId);
        b.setOnClickListener(v -> {
            SystemProperties.set(prop, value);
            bumpConfigVersion();
            highlightSelection(group, btnId);
        });
    }

    private void highlightSelection(int[] group, int selId) {
        for (int id : group) {
            View v = findViewById(id);
            if (v != null) v.setAlpha(id == selId ? 1f : 0.5f);
        }
    }

    // --- Lifecycle ---

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && mNoOpBackCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mNoOpBackCallback);
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveLocal();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveLocal();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        applyImmersiveLocal();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_BUTTON_B) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Block programmatic/system back as well.
    }

    private void applyImmersiveLocal() {
        final android.view.View decor = getWindow().getDecorView();
        try {
            final WindowInsetsController ic = decor.getWindowInsetsController();
            if (ic != null) {
                ic.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Throwable ignored) {}
        final int flags = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(flags);
    }

    // --- Motion events ---

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {

            mLastX = ev.getAxisValue(MotionEvent.AXIS_X);
            mLastY = ev.getAxisValue(MotionEvent.AXIS_Y);

            // Read all right stick candidates
            mLastRX_std = ev.getAxisValue(MotionEvent.AXIS_RX);
            mLastRY_std = ev.getAxisValue(MotionEvent.AXIS_RY);
            mLastRX_alt = ev.getAxisValue(MotionEvent.AXIS_GAS);
            mLastRY_alt = ev.getAxisValue(MotionEvent.AXIS_BRAKE);
            mLastRX_z = ev.getAxisValue(MotionEvent.AXIS_Z);
            mLastRY_z = ev.getAxisValue(MotionEvent.AXIS_RZ);

            // Auto-detect which axis pair the right stick uses (runs once)
            detectRightStick();

            // Use detected pair or default
            if (mRightStickDetected && mDetectedAndroidAxisRX == 0) {
                mLastRX = mLastRX_z; mLastRY = mLastRY_z;
            } else if (mRightStickDetected && mDetectedAndroidAxisRX == 1) {
                mLastRX = mLastRX_alt; mLastRY = mLastRY_alt;
            } else {
                mLastRX = mLastRX_std; mLastRY = mLastRY_std;
            }

            // Update stick views
            if (leftStickView != null) leftStickView.updateAxes(mLastX, mLastY);
            if (rightStickView != null) rightStickView.updateAxes(mLastRX, mLastRY);

            return true;
        }
        return super.onGenericMotionEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    // --- Setup script ---

    private class RunSetupViaInitTask extends AsyncTask<Void, String, Integer> {
        private final File logFile;

        RunSetupViaInitTask(File logFile) {
            this.logFile = logFile;
        }

        @Override
        protected void onPreExecute() {
            SystemProperties.set(PROP_DONE, "0");
            SystemProperties.set(PROP_EXIT_CODE, "0");
            SystemProperties.set(PROP_RUN, "0");
            // The trigger (PROP_RUN=1) is fired from doInBackground once external storage is
            // MOUNTED, so setup.sh never writes /sdcard before the emulated volume is served
            // (mirrors the nano native wizard's storage gate).
        }

        @Override
        protected Integer doInBackground(Void... ignored) {
            // Gate the setup.sh trigger on external storage being MOUNTED (not just vold's early
            // tmpfs placeholder). setup.sh does heavy /sdcard writes; firing it before the
            // emulated volume is served corrupts the install. Bounded (2 min) so a storage
            // failure still runs setup.sh degraded rather than hanging the wizard.
            long storageWaited = 0;
            while (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                    && storageWaited < 120000) {
                try { Thread.sleep(250); } catch (InterruptedException ignoredSleep) {}
                storageWaited += 250;
            }
            SystemProperties.set(PROP_RUN, "0");
            SystemProperties.set(PROP_RUN, "1");

            long pos = 0;
            try {
                while (true) {
                    if (logFile.exists()) {
                        long len = logFile.length();
                        if (len < pos) pos = 0;

                        if (len > pos) {
                            RandomAccessFile raf = new RandomAccessFile(logFile, "r");
                            raf.seek(pos);
                            String line;
                            while ((line = raf.readLine()) != null) {
                                publishProgress(line + "\n");
                            }
                            pos = raf.getFilePointer();
                            raf.close();
                        }
                    }

                    if ("1".equals(SystemProperties.get(PROP_DONE, "0"))) break;

                    try {
                        Thread.sleep(LOG_TAIL_INTERVAL_MS);
                    } catch (InterruptedException ignoredSleep) {}
                }
            } catch (Exception e) {
                publishProgress("Error: " + e.getMessage() + "\n");
            }

            String exitStr = SystemProperties.get(PROP_EXIT_CODE, "0");
            try {
                return Integer.parseInt(exitStr);
            } catch (NumberFormatException ignoredNum) {
                return 0;
            }
        }

        @Override
        protected void onProgressUpdate(String... vals) {
            outputTextView.append(vals[0]);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }

        @Override
        protected void onPostExecute(Integer exitCode) {
            outputTextView.append("Script completed (exit " + exitCode + ").\n");
            outputTextView.postDelayed(() -> {
                Intent intent = WizardManagerHelper.getNextIntent(getIntent(), Activity.RESULT_OK);
                startActivity(intent);
                finish();
            }, 5000);
        }
    }
}

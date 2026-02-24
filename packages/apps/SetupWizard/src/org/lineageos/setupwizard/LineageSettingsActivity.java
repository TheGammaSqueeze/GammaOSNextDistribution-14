package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
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

    private StickTestView leftStickView, rightStickView;
    private ScrollView controlScroll, scrollView;
    private TextView outputTextView, headingTextView;
    private Button continueButton;

    private final int[] sensGroup = {
            R.id.btn_sens_0, R.id.btn_sens_10, R.id.btn_sens_25, R.id.btn_sens_50
    };
    private final int[] deadGroup = {
            R.id.btn_dead_0, R.id.btn_dead_5, R.id.btn_dead_10, R.id.btn_dead_15, R.id.btn_dead_20
    };
    private final int[] invLGroup = {
            R.id.btn_invert_left_off, R.id.btn_invert_left_on
    };
    private final int[] invRGroup = {
            R.id.btn_invert_right_off, R.id.btn_invert_right_on
    };
    private final int[] calibGroup = {
            R.id.btn_calibration_off, R.id.btn_calibration_on
    };

    // Prevent predictive back on Android 13+ (T+) and gesture/virtual back.
    private OnBackInvokedCallback mNoOpBackCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        setContentView(R.layout.setup_lineage_settings);

        leftStickView = findViewById(R.id.left_stick_view);
        rightStickView = findViewById(R.id.right_stick_view);
        controlScroll = findViewById(R.id.control_scroll);
        scrollView = findViewById(R.id.scrollView);
        outputTextView = findViewById(R.id.script_output_text_view);
        headingTextView = findViewById(R.id.headingTextView);
        continueButton = findViewById(R.id.continue_button);

        // Bind property buttons
        bindSetting(R.id.btn_sens_0,  "persist.gammaos.analogsensitivity","0", sensGroup);
        bindSetting(R.id.btn_sens_10, "persist.gammaos.analogsensitivity","1", sensGroup);
        bindSetting(R.id.btn_sens_25, "persist.gammaos.analogsensitivity","2", sensGroup);
        bindSetting(R.id.btn_sens_50, "persist.gammaos.analogsensitivity","3", sensGroup);

        bindSetting(R.id.btn_dead_0,  "persist.gammaos.analogdeadzone","0", deadGroup);
        bindSetting(R.id.btn_dead_5,  "persist.gammaos.analogdeadzone","1", deadGroup);
        bindSetting(R.id.btn_dead_10, "persist.gammaos.analogdeadzone","2", deadGroup);
        bindSetting(R.id.btn_dead_15, "persist.gammaos.analogdeadzone","3", deadGroup);
        bindSetting(R.id.btn_dead_20, "persist.gammaos.analogdeadzone","4", deadGroup);

        bindSetting(R.id.btn_invert_left_off,  "persist.gammaos.leftstickinvert","0", invLGroup);
        bindSetting(R.id.btn_invert_left_on,   "persist.gammaos.leftstickinvert","1", invLGroup);
        bindSetting(R.id.btn_invert_right_off, "persist.gammaos.rightstickinvert","0", invRGroup);
        bindSetting(R.id.btn_invert_right_on,  "persist.gammaos.rightstickinvert","1", invRGroup);

        bindSetting(R.id.btn_calibration_off,  "persist.gammaos.calibrationmode","0", calibGroup);
        bindSetting(R.id.btn_calibration_on,   "persist.gammaos.calibrationmode","1", calibGroup);

        // Continue -> run setup.sh with root, stream output, then advance wizard
        continueButton.setOnClickListener(v -> {
            headingTextView.setText("Configuring GammaOS Next...");
            controlScroll.setVisibility(View.GONE);
            continueButton.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);

            // Kick off the init service and tail the log file for a live UI.
            outputTextView.setText("");
            File logFile = new File(getFilesDir(), "gammaos_setup.log");
            if (logFile.exists()) {
                // Best-effort cleanup; the service will also truncate.
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
            new RunSetupViaInitTask(logFile).execute();
        });
    }

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
        // Re-assert immersive after interactions that might reveal bars.
        applyImmersiveLocal();
    }

    // Consume BACK & B; keep A/Enter/Center behavior from Base (Next).
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_BUTTON_B) {
            return true; // swallow completely
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
        // Legacy flags fallback (covers edge cases)
        final int flags = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(flags);
    }

    private void bindSetting(int btnId, String key, String value, int[] group) {
        Button b = findViewById(btnId);
        b.setOnClickListener(v -> {
            SystemProperties.set(key, value);
            highlightSelection(group, btnId);
        });
    }

    private void highlightSelection(int[] group, int selId) {
        for (int id : group) {
            View v = findViewById(id);
            if (v != null) v.setAlpha(id == selId ? 1f : 0.5f);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (leftStickView != null) {
                leftStickView.updateAxes(
                        ev.getAxisValue(MotionEvent.AXIS_X),
                        ev.getAxisValue(MotionEvent.AXIS_Y));
            }
            if (rightStickView != null) {
                rightStickView.updateAxes(
                        ev.getAxisValue(MotionEvent.AXIS_Z),
                        ev.getAxisValue(MotionEvent.AXIS_RZ));
            }
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

    private class RunSetupViaInitTask extends AsyncTask<Void, String, Integer> {
        private final File logFile;

        RunSetupViaInitTask(File logFile) {
            this.logFile = logFile;
        }

        @Override
        protected void onPreExecute() {
            // Reset coordination properties and trigger init.
            SystemProperties.set(PROP_DONE, "0");
            SystemProperties.set(PROP_EXIT_CODE, "0");

            // Ensure a property edge so init triggers even if it was previously set.
            SystemProperties.set(PROP_RUN, "0");
            SystemProperties.set(PROP_RUN, "1");
        }

        @Override
        protected Integer doInBackground(Void... ignored) {
            long pos = 0;
            try {
                while (true) {
                    if (logFile.exists()) {
                        long len = logFile.length();
                        if (len < pos) pos = 0; // log truncated

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
                    } catch (InterruptedException ignoredSleep) {
                        // Ignore.
                    }
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

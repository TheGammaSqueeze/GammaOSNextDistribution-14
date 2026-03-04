/*
 * GammaOS Gamepad Vibration Bridge
 *
 * Listens on a Unix domain socket for vibration requests from the gammapad
 * native daemon and forwards them to VibratorManagerService using PWM
 * (pulse-width modulation) to emulate variable intensity on controllers
 * that lack native force feedback support.
 */

package com.android.server.gamepad;

import android.content.Context;
import android.os.SystemProperties;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Slog;

import com.android.server.SystemService;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GammapadVibrationBridge extends SystemService {
    private static final String TAG = "GammapadVibrationBridge";
    private static final String SOCKET_NAME = "gammapad_vibrate";
    private static final int VIBRATION_MAGIC = 0x47504144; // "GPAD"
    private static final int MSG_SIZE = 12;
    private static final long MAX_DURATION_MS = 5000;
    private static final int MAX_AMPLITUDE = 255;

    // PWM parameters (matching GammaPad's approach)
    private static final int PWM_PERIOD_MS = 8;
    // Threshold above which we use steady vibration (no PWM needed)
    private static final int PWM_STEADY_THRESHOLD = 60000; // ~92% of 65535

    private final Context mContext;
    private Vibrator mVibrator;
    private LocalServerSocket mServerSocket;

    // PWM thread state
    private volatile Thread mPwmThread;
    private volatile boolean mPwmShouldStop;

    private static final VibrationAttributes HAPTIC_ATTRS =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION)
                    .build();

    public GammapadVibrationBridge(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting GammapadVibrationBridge");
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            VibratorManager vm = mContext.getSystemService(VibratorManager.class);
            if (vm != null) {
                mVibrator = vm.getDefaultVibrator();
            }
            if (mVibrator == null) {
                Slog.w(TAG, "No vibrator available, bridge disabled");
                return;
            }

            Thread serverThread = new Thread(this::runServer, "GammapadVibServer");
            serverThread.setDaemon(true);
            serverThread.start();
        }
    }

    private void runServer() {
        try {
            mServerSocket = new LocalServerSocket(SOCKET_NAME);
            Slog.i(TAG, "Listening on socket: " + SOCKET_NAME);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to create server socket", e);
            return;
        }

        while (true) {
            try {
                LocalSocket client = mServerSocket.accept();
                Slog.i(TAG, "Client connected");
                Thread clientThread = new Thread(
                        () -> handleClient(client), "GammapadVibClient");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException e) {
                Slog.e(TAG, "Accept failed", e);
                break;
            }
        }
    }

    private void handleClient(LocalSocket client) {
        try (InputStream is = client.getInputStream()) {
            byte[] buf = new byte[MSG_SIZE];
            while (true) {
                int bytesRead = 0;
                while (bytesRead < MSG_SIZE) {
                    int n = is.read(buf, bytesRead, MSG_SIZE - bytesRead);
                    if (n < 0) {
                        Slog.i(TAG, "Client disconnected");
                        return;
                    }
                    bytesRead += n;
                }

                ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                int magic = bb.getInt();
                int strong = bb.getShort() & 0xFFFF;
                int weak = bb.getShort() & 0xFFFF;
                int duration = bb.getInt();

                if (magic != VIBRATION_MAGIC) {
                    Slog.w(TAG, "Bad magic: 0x" + Integer.toHexString(magic));
                    continue;
                }

                processVibration(strong, weak, duration);
            }
        } catch (IOException e) {
            Slog.w(TAG, "Client I/O error", e);
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void processVibration(int strong, int weak, int durationMs) {
        if (mVibrator == null) return;

        // Stop command
        if (durationMs == 0 || (strong == 0 && weak == 0)) {
            stopPwm();
            cancelVibrator();
            return;
        }

        // Clamp duration
        long clampedDuration = Math.min(durationMs, MAX_DURATION_MS);

        // Get intensity multiplier from settings (0-100, default 50)
        int intensityPct = SystemProperties.getInt(
                "persist.gammaos.gamepad.pwm_intensity", 50);
        float intensityMul = intensityPct / 100f;

        // Compute magnitude: use the stronger of the two, apply intensity
        int magnitude = Math.max(strong, weak);
        int adjusted = Math.min(65535, (int) (magnitude * intensityMul));

        if (adjusted <= 0) {
            stopPwm();
            cancelVibrator();
            return;
        }

        // High magnitude or short duration: use steady vibration (no PWM)
        if (adjusted >= PWM_STEADY_THRESHOLD || clampedDuration <= PWM_PERIOD_MS * 2) {
            stopPwm();
            int amplitude = Math.min(MAX_AMPLITUDE,
                    (adjusted * MAX_AMPLITUDE) / 65535);
            amplitude = Math.max(1, amplitude);
            vibrateOneShot(clampedDuration, amplitude);
            return;
        }

        // PWM: duty cycle based on magnitude
        int onMs = (adjusted * PWM_PERIOD_MS) / 65535;
        int offMs = PWM_PERIOD_MS - onMs;

        // Edge cases: avoid degenerate PWM
        if (onMs <= 0) {
            stopPwm();
            cancelVibrator();
            return;
        }
        if (offMs <= 0) {
            stopPwm();
            vibrateOneShot(clampedDuration, MAX_AMPLITUDE);
            return;
        }

        startPwm(onMs, offMs, clampedDuration);
    }

    /**
     * Start or update the PWM thread that rapidly toggles vibration on/off
     * to emulate variable intensity.
     */
    private synchronized void startPwm(int onMs, int offMs, long totalDurationMs) {
        stopPwm();

        mPwmShouldStop = false;
        mPwmThread = new Thread(() -> {
            long endTime = System.currentTimeMillis() + totalDurationMs;

            try {
                while (!mPwmShouldStop && System.currentTimeMillis() < endTime) {
                    // ON phase
                    vibrateOneShot(onMs + offMs + 2, MAX_AMPLITUDE);
                    sleepSafe(onMs);

                    if (mPwmShouldStop || System.currentTimeMillis() >= endTime) break;

                    // OFF phase
                    cancelVibrator();
                    sleepSafe(offMs);
                }
            } catch (Exception e) {
                Slog.w(TAG, "PWM thread error", e);
            } finally {
                cancelVibrator();
            }
        }, "GammapadPWM");
        mPwmThread.setDaemon(true);
        mPwmThread.start();
    }

    private synchronized void stopPwm() {
        mPwmShouldStop = true;
        Thread t = mPwmThread;
        if (t != null && t.isAlive()) {
            t.interrupt();
            try {
                t.join(50);
            } catch (InterruptedException ignored) {
            }
        }
        mPwmThread = null;
    }

    private void vibrateOneShot(long durationMs, int amplitude) {
        try {
            VibrationEffect effect = VibrationEffect.createOneShot(
                    durationMs, amplitude);
            mVibrator.vibrate(effect, HAPTIC_ATTRS);
        } catch (Exception e) {
            Slog.w(TAG, "Vibrate failed", e);
        }
    }

    private void cancelVibrator() {
        try {
            mVibrator.cancel();
        } catch (Exception e) {
            Slog.w(TAG, "Cancel vibrate failed", e);
        }
    }

    private static void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}

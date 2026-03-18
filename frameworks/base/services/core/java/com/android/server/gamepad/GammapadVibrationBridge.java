/*
 * GammaOS Gamepad Vibration Bridge
 *
 * Listens on a Unix domain socket for vibration requests from the gammapad
 * native daemon and forwards them to VibratorManagerService using PWM
 * (pulse-width modulation) to emulate variable intensity on controllers
 * that lack native force feedback support.
 */

package com.android.server.gamepad;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Slog;
import android.widget.Toast;

import com.android.server.SystemService;

import java.util.List;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GammapadVibrationBridge extends SystemService {
    private static final String TAG = "GammapadVibrationBridge";
    private static final String SOCKET_NAME = "gammapad_vibrate";
    private static final int VIBRATION_MAGIC = 0x47504144; // "GPAD"
    private static final int TOAST_MAGIC = 0x474D5347;    // "GMSG"
    private static final int MSG_SIZE = 12;
    private static final long MAX_DURATION_MS = 5000;
    private static final int MAX_AMPLITUDE = 255;

    // PWM parameters (matching GammaPad's approach)
    private static final int PWM_PERIOD_MS = 8;
    // Threshold above which we use steady vibration (no PWM needed)
    private static final int PWM_STEADY_THRESHOLD = 60000; // ~92% of 65535

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Vibrator mVibrator;
    private LocalServerSocket mServerSocket;
    private volatile String mLastFgPkg = "";

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
            // Register foreground app tracker for per-app gamepad profiles
            // (independent of vibrator availability)
            registerForegroundTracker();

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

    private void registerForegroundTracker() {
        try {
            ActivityTaskManager.getService().registerTaskStackListener(
                    new TaskStackListener() {
                        @Override
                        public void onTaskStackChanged() {
                            updateForegroundPackage();
                            // Re-check after a short delay since the task stack
                            // may not have fully settled yet
                            mMainHandler.postDelayed(
                                    () -> updateForegroundPackage(), 300);
                        }

                        @Override
                        public void onTaskMovedToFront(
                                ActivityManager.RunningTaskInfo info) {
                            if (info != null && info.topActivity != null) {
                                String pkg = info.topActivity.getPackageName();
                                if (pkg != null && !pkg.equals(mLastFgPkg)) {
                                    mLastFgPkg = pkg;
                                    SystemProperties.set(
                                            "sys.gammaos.gamepad.fg_pkg", pkg);
                                }
                            }
                            // Re-check in case the info was stale
                            mMainHandler.postDelayed(
                                    () -> updateForegroundPackage(), 300);
                        }
                    });
            Slog.i(TAG, "Foreground app tracker registered");

            // Periodic polling as a safety net to catch any missed transitions
            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateForegroundPackage();
                    mMainHandler.postDelayed(this, 1000);
                }
            }, 2000);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register TaskStackListener", e);
        }
    }

    private void updateForegroundPackage() {
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getTasks(1,
                            false /* filterOnlyVisibleRecents */,
                            false /* keepIntentExtra */,
                            -1 /* displayId */);
            if (!tasks.isEmpty() && tasks.get(0).topActivity != null) {
                String pkg = tasks.get(0).topActivity.getPackageName();
                if (pkg != null && !pkg.equals(mLastFgPkg)) {
                    mLastFgPkg = pkg;
                    SystemProperties.set("sys.gammaos.gamepad.fg_pkg", pkg);
                }
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get foreground package", e);
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
            byte[] magicBuf = new byte[4];
            while (true) {
                // Read 4-byte magic to determine message type
                readFully(is, magicBuf, 4);
                int magic = ByteBuffer.wrap(magicBuf)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (magic == VIBRATION_MAGIC) {
                    // Vibration message: 8 more bytes (strong + weak + duration)
                    byte[] vibBuf = new byte[8];
                    readFully(is, vibBuf, 8);
                    ByteBuffer bb = ByteBuffer.wrap(vibBuf)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    int strong = bb.getShort() & 0xFFFF;
                    int weak = bb.getShort() & 0xFFFF;
                    int duration = bb.getInt();
                    processVibration(strong, weak, duration);
                } else if (magic == TOAST_MAGIC) {
                    // Toast message: 2-byte length + UTF-8 text
                    byte[] lenBuf = new byte[2];
                    readFully(is, lenBuf, 2);
                    int len = ByteBuffer.wrap(lenBuf)
                            .order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    if (len > 0 && len <= 1024) {
                        byte[] textBuf = new byte[len];
                        readFully(is, textBuf, len);
                        String text = new String(textBuf, "UTF-8");
                        showToast(text);
                    }
                } else {
                    Slog.w(TAG, "Bad magic: 0x" + Integer.toHexString(magic));
                    return; // Desync — close this client
                }
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

    /**
     * Read exactly {@code count} bytes from the stream, blocking until all
     * bytes are available or EOF is reached.
     */
    private static void readFully(InputStream is, byte[] buf, int count)
            throws IOException {
        int offset = 0;
        while (offset < count) {
            int n = is.read(buf, offset, count - offset);
            if (n < 0) throw new IOException("EOF");
            offset += n;
        }
    }

    /**
     * Show a toast on the main thread.
     */
    private void showToast(String text) {
        Slog.i(TAG, "Toast: " + text);
        mMainHandler.post(() -> {
            try {
                Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Slog.w(TAG, "Failed to show toast", e);
            }
        });
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

        // Compute magnitude from the stronger of the two channels.
        // Note: intensity scaling is already applied by the native gammapad
        // daemon before sending over the bridge — do NOT scale again here.
        int magnitude = Math.max(strong, weak);

        if (magnitude <= 0) {
            stopPwm();
            cancelVibrator();
            return;
        }

        // High magnitude or short duration: use steady vibration (no PWM)
        if (magnitude >= PWM_STEADY_THRESHOLD || clampedDuration <= PWM_PERIOD_MS * 2) {
            stopPwm();
            int amplitude = Math.min(MAX_AMPLITUDE,
                    (magnitude * MAX_AMPLITUDE) / 65535);
            amplitude = Math.max(1, amplitude);
            vibrateOneShot(clampedDuration, amplitude);
            return;
        }

        // PWM: duty cycle based on magnitude
        int onMs = (magnitude * PWM_PERIOD_MS) / 65535;
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

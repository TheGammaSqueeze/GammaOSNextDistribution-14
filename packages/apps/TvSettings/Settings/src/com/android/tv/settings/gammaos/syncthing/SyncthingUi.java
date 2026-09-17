/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tv.settings.gammaos.syncthing;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;

import com.android.internal.gammaos.SyncthingClient;
import com.android.internal.gammaos.SyncthingClient.Snapshot;
import com.android.tv.settings.R;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Plumbing shared by the Syncthing screens: the one background thread every daemon call runs on,
 * delivery of results to the main thread, the dialogs the screens use (text entry, a choice list,
 * a confirmation, a notice) and the last snapshot so a screen has something to show the moment
 * it opens.
 *
 * <p>Every {@link SyncthingClient} call blocks on a loopback HTTP request, so nothing here runs
 * one on the main thread. A single thread keeps the calls in order: a change followed by a
 * refresh always sees its own result.
 */
final class SyncthingUi {

    private static final ExecutorService sExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "SyncthingUi"));
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final SyncthingClient sClient = new SyncthingClient();

    /** Last snapshot any screen loaded. Main thread only. */
    private static Snapshot sLast;
    /** The device's LAN address, refreshed with every snapshot on the background thread. */
    private static volatile String sDeviceIp = "";

    private SyncthingUi() { }

    /** A daemon call that produces a value. */
    interface Task<T> {
        T run(SyncthingClient c) throws IOException;
    }

    /** A daemon call that only has to succeed. */
    interface Action {
        void run(SyncthingClient c) throws IOException;
    }

    /** True while results may still be handed to the fragment. */
    static boolean alive(Fragment f) {
        return f != null && f.isAdded() && f.getContext() != null;
    }

    /**
     * Runs the task on the background thread and hands its result to the main thread. A failure
     * is shown in a dialog unless the caller handles it. Nothing is delivered once the fragment
     * has gone away.
     */
    static <T> void run(Fragment host, Task<T> task, Consumer<T> onResult,
            Consumer<IOException> onError) {
        sExecutor.execute(() -> {
            T result;
            try {
                result = task.run(sClient);
            } catch (IOException e) {
                sMain.post(() -> {
                    if (!alive(host)) return;
                    if (onError != null) onError.accept(e);
                    else showError(host, e.getMessage());
                });
                return;
            }
            sMain.post(() -> {
                if (alive(host) && onResult != null) onResult.accept(result);
            });
        });
    }

    static <T> void run(Fragment host, Task<T> task, Consumer<T> onResult) {
        run(host, task, onResult, null);
    }

    /** Runs an action; on success calls onDone, on failure shows the daemon's message. */
    static void act(Fragment host, Action action, Runnable onDone) {
        run(host, c -> { action.run(c); return null; }, v -> { if (onDone != null) onDone.run(); });
    }

    // ---- snapshot ------------------------------------------------------------------------------

    static Snapshot lastSnapshot() { return sLast; }

    static void setLastSnapshot(Snapshot s) { sLast = s; }

    static String deviceIp() { return sDeviceIp.isEmpty() ? "<device-ip>" : sDeviceIp; }

    /**
     * One refresh, on the background thread. Never throws: a daemon that is off or still starting
     * gives a snapshot with apiOk false and the reason in error, which the root screen shows as
     * its status.
     */
    static Snapshot loadSnapshot(SyncthingClient c) {
        Snapshot s;
        if (!SyncthingClient.isEnabled()) {
            s = new Snapshot();
            s.error = "off";
            return s;
        }
        try {
            s = c.fetchSnapshot();
        } catch (IOException | RuntimeException e) {
            // A RuntimeException would otherwise die silently inside the executor and leave the
            // screen on "Starting..." for good; report it like a daemon error.
            android.util.Log.w("SyncthingUi", "snapshot failed", e);
            s = new Snapshot();
            s.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return s;
        }
        // First run: Syncthing names a new device after the hostname, which on Android is
        // "localhost". Give it the product model instead, once; the user can rename it.
        if (s.apiOk && "localhost".equals(s.options.deviceName) && !s.myID.isEmpty()
                && Build.MODEL != null && !Build.MODEL.isEmpty()) {
            try {
                SyncthingClient.Options o = s.options;
                o.deviceName = Build.MODEL;
                c.setOptions(o, s.myID);
            } catch (IOException ignored) {
                // Purely cosmetic; the next refresh tries again.
            }
        }
        sDeviceIp = findDeviceIp();
        return s;
    }

    /** The first non-loopback IPv4 address, for the web interface hint. */
    private static String findDeviceIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    // ---- dialogs -------------------------------------------------------------------------------

    /** The daemon's own message, under the one title every Syncthing error uses. */
    static void showError(Fragment host, String message) {
        if (!alive(host)) return;
        new AlertDialog.Builder(host.getContext())
                .setTitle(R.string.syncthing_title)
                .setMessage(message == null || message.isEmpty()
                        ? host.getString(R.string.syncthing_error_unknown) : message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    static void info(Fragment host, CharSequence title, CharSequence message) {
        if (!alive(host)) return;
        new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Cancel / action confirmation; onConfirm runs only for the action. */
    static void confirm(Fragment host, CharSequence title, CharSequence message,
            CharSequence action, Runnable onConfirm) {
        if (!alive(host)) return;
        new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .show();
    }

    /**
     * A list of choices with the current one marked. Picking one closes the dialog and hands the
     * index back; moving away with Back changes nothing. Works with a d-pad alone.
     */
    static void choose(Fragment host, CharSequence title, CharSequence[] items, int checked,
            IntConsumer onPick) {
        if (!alive(host)) return;
        new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    onPick.accept(which);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * A set of choices with check boxes; OK hands back the final selection, Cancel changes
     * nothing.
     */
    static void chooseMany(Fragment host, CharSequence title, CharSequence[] items,
            boolean[] checked, Consumer<boolean[]> onOk) {
        if (!alive(host)) return;
        final boolean[] state = checked.clone();
        new AlertDialog.Builder(host.getContext())
                .setTitle(title)
                .setMultiChoiceItems(items, state, (d, which, on) -> state[which] = on)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onOk.accept(state))
                .show();
    }

    /**
     * One text field, prefilled, in a dialog: the same arrangement the other GammaOS screens use
     * for text entry, so the on-screen keyboard and d-pad behave the same way everywhere.
     */
    static void askText(Fragment host, CharSequence title, String prefill, int inputType,
            Consumer<String> onOk) {
        if (!alive(host)) return;
        Context ctx = host.getContext();
        final EditText input = new EditText(ctx);
        input.setInputType(inputType);
        input.setText(prefill == null ? "" : prefill);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);

        FrameLayout container = new FrameLayout(ctx);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        container.setPadding(pad, 0, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> onOk.accept(input.getText().toString()))
                .show();
    }

    static void askText(Fragment host, CharSequence title, String prefill, Consumer<String> onOk) {
        askText(host, title, prefill, InputType.TYPE_CLASS_TEXT, onOk);
    }

    /** A whole number at or above zero; anything else is treated as zero. */
    static void askNumber(Fragment host, CharSequence title, int current, IntConsumer onOk) {
        askText(host, title, Integer.toString(current), InputType.TYPE_CLASS_NUMBER, v -> {
            int n;
            try {
                n = Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
            onOk.accept(Math.max(0, n));
        });
    }

    // ---- small text helpers --------------------------------------------------------------------

    static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    /** Splits on commas and spaces, dropping empties. */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String tok : text.split("[, ]+")) {
            if (!tok.isEmpty()) out.add(tok);
        }
        return out;
    }

    static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    static int indexOf(String[] values, String value, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return fallback;
    }

    static int indexOf(int[] values, int value, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return fallback;
    }
}

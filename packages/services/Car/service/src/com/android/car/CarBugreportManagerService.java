/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car;

import static android.car.CarBugreportManager.CarBugreportManagerCallback.CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED;
import static android.car.CarBugreportManager.CarBugreportManagerCallback.CAR_BUGREPORT_DUMPSTATE_FAILED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.car.CarBugreportManager.CarBugreportManagerCallback;
import android.car.ICarBugreportCallback;
import android.car.ICarBugreportService;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.SystemPropertiesHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bugreport service for cars.
 */
public class CarBugreportManagerService extends ICarBugreportService.Stub implements
        CarServiceBase {

    private static final String TAG = CarLog.tagFor(CarBugreportManagerService.class);

    /**
     * {@code dumpstate} progress prefixes.
     *
     * <p>The protocol is described in {@code frameworks/native/cmds/bugreportz/readme.md}.
     */
    private static final String BEGIN_PREFIX = "BEGIN:";
    private static final String PROGRESS_PREFIX = "PROGRESS:";
    private static final String OK_PREFIX = "OK:";
    private static final String FAIL_PREFIX = "FAIL:";

    /**
     * The services are defined in {@code packages/services/Car/cpp/bugreport/carbugreportd.rc}.
     */
    @VisibleForTesting
    static final String BUGREPORTD_SERVICE = "carbugreportd";
    @VisibleForTesting
    static final String DUMPSTATEZ_SERVICE = "cardumpstatez";

    // The socket definitions must match the actual socket names defined in car_bugreportd service
    // definition.
    private static final String BUGREPORT_PROGRESS_SOCKET = "car_br_progress_socket";
    private static final String BUGREPORT_OUTPUT_SOCKET = "car_br_output_socket";
    private static final String BUGREPORT_EXTRA_OUTPUT_SOCKET = "car_br_extra_output_socket";

    private static final int SOCKET_CONNECTION_MAX_RETRY = 10;
    private static final int SOCKET_CONNECTION_RETRY_DELAY_IN_MS = 5000;

    private final Context mContext;
    private final boolean mIsUserBuild;
    private final Object mLock = new Object();

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    @VisibleForTesting
    final AtomicBoolean mIsServiceRunning = new AtomicBoolean(false);
    private boolean mIsDumpstateDryRun = false;

    /**
     * Create a CarBugreportManagerService instance.
     *
     * @param context the context
     */
    public CarBugreportManagerService(Context context) {
        // Per https://source.android.com/setup/develop/new-device, user builds are debuggable=0
        this(context, !BuildHelper.isDebuggableBuild());
    }

    @VisibleForTesting
    CarBugreportManagerService(Context context, boolean isUserBuild) {
        mContext = context;
        mIsUserBuild = isUserBuild;
    }

    @Override
    public void init() {
        // nothing to do
    }

    @Override
    public void release() {
        // To stop any pending tasks in HandlerThread
        mIsServiceRunning.set(false);
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void requestBugreport(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput,
            ICarBugreportCallback callback, boolean dumpstateDryRun) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DUMP, "requestBugreport");
        ensureTheCallerIsSignedWithPlatformKeys();
        ensureTheCallerIsDesignatedBugReportApp();
        synchronized (mLock) {
            if (mIsServiceRunning.getAndSet(true)) {
                Slogf.w(TAG, "Bugreport Service already running");
                reportError(callback, CarBugreportManagerCallback.CAR_BUGREPORT_IN_PROGRESS);
                return;
            }
            requestBugReportLocked(output, extraOutput, callback, dumpstateDryRun);
        }
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void cancelBugreport() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DUMP, "cancelBugreport");
        ensureTheCallerIsSignedWithPlatformKeys();
        ensureTheCallerIsDesignatedBugReportApp();
        synchronized (mLock) {
            if (!mIsServiceRunning.getAndSet(false)) {
                Slogf.i(TAG, "Ignoring cancelBugreport. Service is not running.");
                return;
            }
            Slogf.i(TAG, "Cancelling the running bugreport");
            mHandler.removeCallbacksAndMessages(/* token= */ null);
            // This tells init to cancel the services. Note that this is achieved through
            // setting a system property which is not thread-safe. So the lock here offers
            // thread-safety only among callers of the API.
            try {
                SystemPropertiesHelper.set("ctl.stop", BUGREPORTD_SERVICE);
            } catch (RuntimeException e) {
                Slogf.e(TAG, "Failed to stop " + BUGREPORTD_SERVICE, e);
            }
            try {
                // Stop DUMPSTATEZ_SERVICE service too, because stopping BUGREPORTD_SERVICE doesn't
                // guarantee stopping DUMPSTATEZ_SERVICE.
                SystemPropertiesHelper.set("ctl.stop", DUMPSTATEZ_SERVICE);
            } catch (RuntimeException e) {
                Slogf.e(TAG, "Failed to stop " + DUMPSTATEZ_SERVICE, e);
            }
            if (mIsDumpstateDryRun) {
                setDumpstateDryRun(false);
            }
        }
    }

    /** See {@code dumpstate} docs to learn about dry_run. */
    private void setDumpstateDryRun(boolean dryRun) {
        try {
            SystemPropertiesHelper.set("dumpstate.dry_run", dryRun ? "true" : null);
        } catch (RuntimeException e) {
            Slogf.e(TAG, "Failed to set dumpstate.dry_run", e);
        }
    }

    private void ensureTheCallerIsSignedWithPlatformKeys() {
        PackageManager pm = mContext.getPackageManager();
        int callingUid = Binder.getCallingUid();
        if (pm.checkSignatures(Process.myUid(), callingUid) != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("Caller " + pm.getNameForUid(callingUid)
                            + " does not have the right signature");
        }
    }

    /** Checks only on user builds. */
    private void ensureTheCallerIsDesignatedBugReportApp() {
        if (!mIsUserBuild) {
            return;
        }
        String defaultAppPkgName = mContext.getString(R.string.config_car_bugreport_application);
        int callingUid = Binder.getCallingUid();
        PackageManager pm = mContext.getPackageManager();
        String[] packageNamesForCallerUid = pm.getPackagesForUid(callingUid);
        if (packageNamesForCallerUid != null) {
            for (String packageName : packageNamesForCallerUid) {
                if (defaultAppPkgName.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Caller " + pm.getNameForUid(callingUid)
                + " is not a designated bugreport app");
    }

    @GuardedBy("mLock")
    private void requestBugReportLocked(
            ParcelFileDescriptor output,
            ParcelFileDescriptor extraOutput,
            ICarBugreportCallback callback,
            boolean dumpstateDryRun) {
        Slogf.i(TAG, "Starting " + BUGREPORTD_SERVICE);
        mIsDumpstateDryRun = dumpstateDryRun;
        if (mIsDumpstateDryRun) {
            setDumpstateDryRun(true);
        }
        try {
            // This tells init to start the service. Note that this is achieved through
            // setting a system property which is not thread-safe. So the lock here offers
            // thread-safety only among callers of the API.
            SystemPropertiesHelper.set("ctl.start", BUGREPORTD_SERVICE);
        } catch (RuntimeException e) {
            mIsServiceRunning.set(false);
            Slogf.e(TAG, "Failed to start " + BUGREPORTD_SERVICE, e);
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_FAILED);
            return;
        }
        mHandler.post(() -> {
            try {
                processBugreportSockets(output, extraOutput, callback);
            } finally {
                if (mIsDumpstateDryRun) {
                    setDumpstateDryRun(false);
                }
                mIsServiceRunning.set(false);
            }
        });
    }

    private void handleProgress(String line, ICarBugreportCallback callback) {
        String progressOverTotal = line.substring(PROGRESS_PREFIX.length());
        String[] parts = progressOverTotal.split("/");
        if (parts.length != 2) {
            Slogf.w(TAG, "Invalid progress line from bugreportz: " + line);
            return;
        }
        float progress;
        float total;
        try {
            progress = Float.parseFloat(parts[0]);
            total = Float.parseFloat(parts[1]);
        } catch (NumberFormatException e) {
            Slogf.w(TAG, "Invalid progress value: " + line, e);
            return;
        }
        if (total == 0) {
            Slogf.w(TAG, "Invalid progress total value: " + line);
            return;
        }
        try {
            callback.onProgress(100f * progress / total);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to call onProgress callback", e);
        }
    }

    private void handleFinished(ParcelFileDescriptor output, ParcelFileDescriptor extraOutput,
            ICarBugreportCallback callback) {
        Slogf.i(TAG, "Finished reading bugreport");
        // copysockettopfd calls callback.onError on error
        if (!copySocketToPfd(output, BUGREPORT_OUTPUT_SOCKET, callback)) {
            return;
        }
        if (!copySocketToPfd(extraOutput, BUGREPORT_EXTRA_OUTPUT_SOCKET, callback)) {
            return;
        }
        try {
            callback.onFinished();
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to call onFinished callback", e);
        }
    }

    /**
     * Reads from dumpstate progress and output sockets and invokes appropriate callbacks.
     *
     * <p>dumpstate prints {@code BEGIN:} right away, then prints {@code PROGRESS:} as it
     * progresses. When it finishes or fails it prints {@code OK:pathToTheZipFile} or
     * {@code FAIL:message} accordingly.
     */
    private void processBugreportSockets(
            ParcelFileDescriptor output, ParcelFileDescriptor extraOutput,
            ICarBugreportCallback callback) {
        LocalSocket localSocket = connectSocket(BUGREPORT_PROGRESS_SOCKET);
        if (localSocket == null) {
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED);
            return;
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(localSocket.getInputStream()))) {
            String line;
            while (mIsServiceRunning.get() && (line = reader.readLine()) != null) {
                if (line.startsWith(PROGRESS_PREFIX)) {
                    handleProgress(line, callback);
                } else if (line.startsWith(FAIL_PREFIX)) {
                    String errorMessage = line.substring(FAIL_PREFIX.length());
                    Slogf.e(TAG, "Failed to dumpstate: " + errorMessage);
                    reportError(callback, CAR_BUGREPORT_DUMPSTATE_FAILED);
                    return;
                } else if (line.startsWith(OK_PREFIX)) {
                    handleFinished(output, extraOutput, callback);
                    return;
                } else if (!line.startsWith(BEGIN_PREFIX)) {
                    Slogf.w(TAG, "Received unknown progress line from dumpstate: " + line);
                }
            }
            Slogf.e(TAG, "dumpstate progress unexpectedly ended");
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_FAILED);
        } catch (IOException | RuntimeException e) {
            Slogf.i(TAG, "Failed to read from progress socket", e);
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED);
        }
    }

    private boolean copySocketToPfd(
            ParcelFileDescriptor pfd, String remoteSocket, ICarBugreportCallback callback) {
        LocalSocket localSocket = connectSocket(remoteSocket);
        if (localSocket == null) {
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_CONNECTION_FAILED);
            return false;
        }

        try (
            DataInputStream in = new DataInputStream(localSocket.getInputStream());
            DataOutputStream out =
                    new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pfd))
        ) {
            rawCopyStream(out, in);
        } catch (IOException | RuntimeException e) {
            Slogf.e(TAG, "Failed to grab dump state from " + BUGREPORT_OUTPUT_SOCKET, e);
            reportError(callback, CAR_BUGREPORT_DUMPSTATE_FAILED);
            return false;
        }
        return true;
    }

    private void reportError(ICarBugreportCallback callback, int errorCode) {
        try {
            callback.onError(errorCode);
        } catch (RemoteException e) {
            Slogf.e(TAG, "onError() failed", e);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        // TODO(sgurun) implement
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @Nullable
    private LocalSocket connectSocket(@NonNull String socketName) {
        LocalSocket socket = new LocalSocket();
        // The dumpstate socket will be created by init upon receiving the
        // service request. It may not be ready by this point. So we will
        // keep retrying until success or reaching timeout.
        int retryCount = 0;
        while (true) {
            // There are a few factors impacting the socket delay:
            // 1. potential system slowness
            // 2. carbugreportd takes the screenshots early (before starting dumpstate). This
            //    should be taken into account as the socket opens after screenshots are
            //    captured.
            // Therefore we are generous in setting the timeout. Most cases should not even
            // come close to the timeouts, but since bugreports are taken when there is a
            // system issue, it is hard to guess.
            // The following lines waits for SOCKET_CONNECTION_RETRY_DELAY_IN_MS or until
            // mIsServiceRunning becomes false.
            for (int i = 0; i < SOCKET_CONNECTION_RETRY_DELAY_IN_MS / 50; i++) {
                if (!mIsServiceRunning.get()) {
                    Slogf.i(TAG, "Failed to connect to socket " + socketName
                            + ". The service is prematurely cancelled.");
                    return null;
                }
                SystemClock.sleep(50);  // Millis.
            }

            try {
                socket.connect(new LocalSocketAddress(socketName,
                        LocalSocketAddress.Namespace.RESERVED));
                return socket;
            } catch (IOException e) {
                if (++retryCount >= SOCKET_CONNECTION_MAX_RETRY) {
                    Slogf.i(TAG, "Failed to connect to dumpstate socket " + socketName
                            + " after " + retryCount + " retries", e);
                    return null;
                }
                Slogf.i(TAG, "Failed to connect to " + socketName + ". Will try again. "
                        + e.getMessage());
            }
        }
    }

    // does not close the reader or writer.
    private static void rawCopyStream(OutputStream writer, InputStream reader) throws IOException {
        int read;
        byte[] buf = new byte[8192];
        while ((read = reader.read(buf, 0, buf.length)) > 0) {
            writer.write(buf, 0, read);
        }
    }
}

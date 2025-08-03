/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.builtin.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;

import com.android.internal.util.FastPrintWriter;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Helper for Binder related usage
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class BinderHelper {

    /** Dumps given {@link RemoteCallbackList} for debugging. */
    public static void dumpRemoteCallbackList(@NonNull RemoteCallbackList<?> list,
            @NonNull PrintWriter pw) {
        list.dump(pw, /* prefix= */ "");
    }

    private BinderHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Listener for implementing shell command handling. Should be used with
     * {@link #onTransactForCmd(int, Parcel, Parcel, int, ShellCommandListener)}.
     */
    public interface ShellCommandListener {
        /**
         * Implements shell command
         * @param in input file
         * @param out output file
         * @param err error output
         * @param args args passed with the command
         *
         * @return linux error code for the binder call. {@code 0} means ok.
         */
        int onShellCommand(@NonNull FileDescriptor in, @NonNull FileDescriptor out,
                @NonNull FileDescriptor err, @NonNull String[] args);
    }

    /**
     * Handles {@link Binder#onTransact(int, Parcel, Parcel, int)} for shell command.
     *
     * <p>This is different from the default {@link Binder#onTransact(int, Parcel, Parcel, int)}
     * in that this does not check shell UID so that test apps not having shell UID can use it. Note
     * that underlying command still should do necessary permission checks so that only apps with
     * right permission can run that command.
     *
     * @param code Binder call code
     * @param data Input {@code Parcel}
     * @param reply Reply {@code Parcel}
     * @param flags Binder de-serialization flags
     * @param cmdListener Listener to implement the command.
     *
     * @return {@code true} if the transaction was handled (=if it is dump or cmd call).
     *
     * @throws RemoteException for binder call failure
     */
    public static boolean onTransactForCmd(int code, @NonNull Parcel data,
            @Nullable Parcel reply, int flags, @NonNull ShellCommandListener cmdListener)
            throws RemoteException {
        if (code == IBinder.SHELL_COMMAND_TRANSACTION) {
            ParcelFileDescriptor in = data.readFileDescriptor();
            ParcelFileDescriptor out = data.readFileDescriptor();
            ParcelFileDescriptor err = data.readFileDescriptor();
            String[] args = data.readStringArray();
            // not used but should read from Parcel to get the next one.
            ShellCallback.CREATOR.createFromParcel(data);
            ResultReceiver resultReceiver = ResultReceiver.CREATOR.createFromParcel(data);
            if (args == null) {
                args = new String[0];
            }

            FileDescriptor errFd;
            if (err == null) {
                // if no err, use out for err
                errFd = out.getFileDescriptor();
            } else {
                errFd = err.getFileDescriptor();
            }
            FileInputStream inputStream = null;
            try {
                FileDescriptor inFd;
                if (in == null) {
                    inputStream = new FileInputStream("/dev/null");
                    inFd = inputStream.getFD();
                } else {
                    inFd = in.getFileDescriptor();
                }
                if (out != null) {
                    int r = cmdListener.onShellCommand(inFd, out.getFileDescriptor(), errFd, args);
                    if (resultReceiver != null) {
                        resultReceiver.send(r, null);
                    }
                }
            } catch (Exception e) {
                sendFailureToCaller(errFd, resultReceiver,
                        "Cannot handle command with error:" + e.getMessage());
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    sendFailureToCaller(errFd, resultReceiver,
                            "I/O error:" + e.getMessage());
                    // Still continue and complete the command (return true}
                }
                IoUtils.closeQuietly(in);
                IoUtils.closeQuietly(out);
                IoUtils.closeQuietly(err);
                if (reply != null) {
                    reply.writeNoException();
                }
            }
            return true;
        }
        return false;
    }

    private static void sendFailureToCaller(FileDescriptor errFd, ResultReceiver receiver,
            String msg) {
        try (PrintWriter pw = new FastPrintWriter(new FileOutputStream(errFd))) {
            pw.println(msg);
            pw.flush();
        }
        receiver.send(/* resultCode= */ -1, /* resultData= */ null);
    }
}

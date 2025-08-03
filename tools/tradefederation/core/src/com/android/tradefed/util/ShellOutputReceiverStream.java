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

package com.android.tradefed.util;

import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.Nullable;

/** Utility subclass of OutputStream that writes into an IShellOutputReceiver. */
public final class ShellOutputReceiverStream extends OutputStream {
    private IShellOutputReceiver mReceiver;
    private FileOutputStream mFileOutput;

    /**
     * Create a new adapter for the given {@link IShellOutputReceiver}.
     *
     * <p>It is valid to provide a null receiver here to simplify code using the adapter, i.e. so
     * that it can use this with try-with-resources without checking for a null receiver itself.
     */
    public ShellOutputReceiverStream(@Nullable IShellOutputReceiver receiver) {
        mReceiver = receiver;
    }

    public ShellOutputReceiverStream(
            @Nullable IShellOutputReceiver receiver, @Nullable FileOutputStream fileOutput) {
        this(receiver);
        mFileOutput = fileOutput;
    }

    @Override
    public void write(int b) {
        if (mReceiver == null) {
            return;
        }
        final byte converted = (byte) (b & 0xFF);
        mReceiver.addOutput(new byte[] {converted}, 0, 1);
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (mReceiver == null) {
            return;
        }
        mReceiver.addOutput(b, off, len);
        if (mFileOutput != null) {
            try {
                mFileOutput.write(b, off, len);
            } catch (IOException e) {
                CLog.e(e);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        if (mReceiver == null) {
            return;
        }
        mReceiver.flush();
        if (mFileOutput != null) {
            mFileOutput.flush();
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        StreamUtil.close(mFileOutput);
    }
}

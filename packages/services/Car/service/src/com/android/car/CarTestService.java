/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.test.ICarTest;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.MessageQueue.OnFileDescriptorEventListener;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to allow testing / mocking vehicle HAL.
 * This service uses Vehicle HAL APIs directly (one exception) as vehicle HAL mocking anyway
 * requires accessing that level directly.
 */
class CarTestService extends ICarTest.Stub implements CarServiceBase {

    private static final String TAG = CarLog.tagFor(CarTestService.class);

    private final Context mContext;
    private final ICarImpl mICarImpl;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final Map<IBinder, TokenDeathRecipient> mTokens = new HashMap<>();

    CarTestService(Context context, ICarImpl carImpl) {
        mContext = context;
        mICarImpl = carImpl;
    }

    @Override
    public void init() {
        // nothing to do.
        // This service should not reset anything for init / release to maintain mocking.
    }

    @Override
    public void release() {
        // nothing to do
        // This service should not reset anything for init / release to maintain mocking.
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarTestService*");
        synchronized (mLock) {
            writer.println(" mTokens:" + Arrays.toString(mTokens.entrySet().toArray()));
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @Override
    public void stopCarService(IBinder token) throws RemoteException {
        Slogf.d(TAG, "stopCarService, token: " + token);
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);

        synchronized (mLock) {
            if (mTokens.containsKey(token)) {
                Slogf.w(TAG, "Calling stopCarService twice with the same token.");
                return;
            }

            TokenDeathRecipient deathRecipient = new TokenDeathRecipient(token);
            mTokens.put(token, deathRecipient);
            token.linkToDeath(deathRecipient, 0);

            if (mTokens.size() == 1) {
                CarServiceUtils.runOnMainSync(mICarImpl::release);
            }
        }
    }

    @Override
    public void startCarService(IBinder token) throws RemoteException {
        Slogf.d(TAG, "startCarService, token: " + token);
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
        releaseToken(token);
    }

    @Override
    public String dumpVhal(List<String> options, long waitTimeoutMs) throws RemoteException {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
        try (NativePipe pipe = NativePipe.newPipe()) {
            mICarImpl.dumpVhal(pipe.getFileDescriptor(), options);
            return pipe.getOutput(waitTimeoutMs);
        } catch (IOException | InterruptedException e) {
            throw new ServiceSpecificException(0,
                    "Error: fail to create or access pipe used for dumping VHAL, options: "
                    + options + ", error: " + e);
        }
    }

    @Override
    public boolean hasAidlVhal() throws RemoteException {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
        return mICarImpl.hasAidlVhal();
    }

    @Override
    public String getOemServiceName() {
        return mICarImpl.getOemServiceName();
    }

    private static class FdEventListener implements OnFileDescriptorEventListener {
        private static final int BUFFER_SIZE = 1024;
        private byte[] mBuffer = new byte[BUFFER_SIZE];
        private ByteArrayOutputStream mOutputStream = new ByteArrayOutputStream();
        private Looper mLooper;
        private IOException mException = null;

        FdEventListener(Looper looper) {
            mLooper = looper;
        }

        @Override
        public int onFileDescriptorEvents(FileDescriptor fd, int events) {
            if ((events & EVENT_INPUT) != 0) {
                try {
                    FileInputStream inputStream = new FileInputStream(fd);
                    while (inputStream.available() != 0) {
                        int size = inputStream.read(mBuffer);
                        mOutputStream.write(mBuffer, /* off= */ 0, size);
                    }
                } catch (IOException e) {
                    mException = e;
                    return 0;
                }
            }
            if ((events & EVENT_ERROR) != 0) {
                // The remote end closes the connection.
                mLooper.quit();
                return 0;
            }
            return EVENT_INPUT | EVENT_ERROR;
        }

        public String getOutput() throws IOException {
            if (mException != null) {
                throw mException;
            }
            return mOutputStream.toString();
        }
    }

    // A helper class to create a native pipe used in debug functions.
    /* package */ static class NativePipe implements AutoCloseable {
        private final ParcelFileDescriptor mWriter;
        private final ParcelFileDescriptor mReader;
        private Thread mThread;
        private Looper mLooper;
        private FdEventListener mEventListener;

        private NativePipe(ParcelFileDescriptor writer, ParcelFileDescriptor reader) {
            mWriter = writer;
            mReader = reader;

            // Start a new thread to read from pipe to prevent the writer blocking on write.
            mThread = new Thread(() -> {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mEventListener = new FdEventListener(mLooper);
                Looper.myQueue().addOnFileDescriptorEventListener(mReader.getFileDescriptor(),
                        OnFileDescriptorEventListener.EVENT_INPUT
                        | OnFileDescriptorEventListener.EVENT_ERROR, mEventListener);
                Looper.loop();
            }, "nativePipe_readThread");
            mThread.start();
        }

        public static NativePipe newPipe() throws IOException {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor reader = new ParcelFileDescriptor(pipe[0]);
            ParcelFileDescriptor writer = new ParcelFileDescriptor(pipe[1]);
            return new NativePipe(writer, reader);
        }

        public ParcelFileDescriptor getFileDescriptor() {
            return mWriter;
        }

        /**
         * Reads all the output data received from the pipe. This function should only be called
         * once for one pipe.
         */
        public String getOutput(long waitTimeoutMs) throws IOException, InterruptedException {
            // Close our side for the writer.
            mWriter.close();
            // Wait until we read all the data from the pipe.
            try {
                mThread.join(waitTimeoutMs);
                if (!mThread.isAlive()) {
                    return mEventListener.getOutput();
                }
            } catch (InterruptedException e) {
                mLooper.quit();
                throw e;
            }
            // If the other side don't close the writer FD within timeout, we would forcefully
            // quit the looper, causing the thread to end.
            mLooper.quit();
            throw new ServiceSpecificException(0,
                    "timeout while waiting for VHAL to close writer FD");
        }

        @Override
        public void close() throws IOException {
            mReader.close();
            // No need to close mOutputStream because close for ByteArrayOutputStream is no-op.
        }
    }

    private void releaseToken(IBinder token) {
        Slogf.d(TAG, "releaseToken, token: " + token);
        synchronized (mLock) {
            DeathRecipient deathRecipient = mTokens.remove(token);
            if (deathRecipient != null) {
                token.unlinkToDeath(deathRecipient, 0);
            }

            if (mTokens.size() == 0) {
                CarServiceUtils.runOnMainSync(() -> {
                    mICarImpl.priorityInit();
                    mICarImpl.init();
                });
            }
        }
    }

    private class TokenDeathRecipient implements DeathRecipient {
        private final IBinder mToken;

        TokenDeathRecipient(IBinder token) throws RemoteException {
            mToken = token;
        }

        @Override
        public void binderDied() {
            releaseToken(mToken);
        }
    }
}

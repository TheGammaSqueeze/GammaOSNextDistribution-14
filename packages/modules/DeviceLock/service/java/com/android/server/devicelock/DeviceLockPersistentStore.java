/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.devicelock;

import android.annotation.WorkerThread;
import android.os.Environment;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.Xml;

import com.android.devicelockcontroller.util.ThreadAsserts;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class that manages persisting device state data for the system service.
 */
public final class DeviceLockPersistentStore {
    private static final String TAG = DeviceLockPersistentStore.class.getSimpleName();
    private static final String SYSTEM_DIR = "system";
    private static final String DEVICE_LOCK_DIR = "device_lock";
    private static final String DEVICE_STATE_FILE = "device_state.xml";
    private static final String TAG_DEVICE_STATE = "device_state";
    private static final String ATTR_IS_DEVICE_FINALIZED = "is_device_finalized";

    private final Executor mBgExecutor = Executors.newSingleThreadExecutor();
    private final File mFile;

    DeviceLockPersistentStore() {
        final File systemDir = new File(Environment.getDataDirectory(), SYSTEM_DIR);
        final File deviceLockDir = new File(systemDir, DEVICE_LOCK_DIR);
        if (!deviceLockDir.exists()) {
            final boolean madeDirs = deviceLockDir.mkdirs();
            if (!madeDirs) {
                Slog.e(TAG, "Failed to make directory " + deviceLockDir.getAbsolutePath());
            }
        }
        mFile = new File(deviceLockDir, DEVICE_STATE_FILE);
    }

    /**
     * Schedule a write of the finalized state.
     *
     * @param finalized true if device is fully finalized
     */
    public void scheduleWrite(boolean finalized) {
        mBgExecutor.execute(() -> writeState(finalized));
    }

    /**
     * Read the finalized state from disk
     *
     * @param callback callback for when state is read
     * @param callbackExecutor executor to run callback on
     */
    public void readFinalizedState(DeviceStateCallback callback, Executor callbackExecutor) {
        mBgExecutor.execute(() -> {
            final boolean isFinalized = readState();
            callbackExecutor.execute(() -> callback.onDeviceStateRead(isFinalized));
        });
    }

    @WorkerThread
    private void writeState(boolean finalized) {
        ThreadAsserts.assertWorkerThread("writeState");
        synchronized (this) {
            AtomicFile atomicFile = new AtomicFile(mFile);

            try (FileOutputStream fileOutputStream = atomicFile.startWrite()) {
                try {
                    XmlSerializer serializer = Xml.newSerializer();
                    serializer.setOutput(fileOutputStream, Xml.Encoding.UTF_8.name());
                    serializer.startDocument(Xml.Encoding.UTF_8.name(), true /* standalone */);
                    writeToXml(serializer, finalized);
                    serializer.endDocument();
                    fileOutputStream.flush();
                    atomicFile.finishWrite(fileOutputStream);
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to write to XML", e);
                    atomicFile.failWrite(fileOutputStream);
                }
            } catch (IOException e) {
                Slog.e(TAG, "Failed to start write", e);
            }
        }
    }

    private void writeToXml(XmlSerializer serializer, boolean finalized) throws IOException {
        serializer.startTag(null /* namespace */, TAG_DEVICE_STATE);
        serializer.attribute(null /* namespace */,
                ATTR_IS_DEVICE_FINALIZED, Boolean.toString(finalized));
        serializer.endTag(null /* namespace */, TAG_DEVICE_STATE);
    }

    @WorkerThread
    private boolean readState() {
        ThreadAsserts.assertWorkerThread("readState");
        synchronized (this) {
            if (!mFile.exists()) {
                return false;
            }
            AtomicFile atomicFile = new AtomicFile(mFile);

            try (FileInputStream inputStream = atomicFile.openRead()) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(inputStream, Xml.Encoding.UTF_8.name());
                return getStateFromXml(parser);
            } catch (XmlPullParserException | IOException e) {
                Slog.e(TAG, "Failed to read XML", e);
                return false;
            }
        }
    }

    private boolean getStateFromXml(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        while (parser.getEventType() != XmlPullParser.START_TAG
                || !TAG_DEVICE_STATE.equals(parser.getName())) {
            if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                throw new XmlPullParserException("Malformed XML. Unable to find start of tag.");
            }
            parser.next();
        }
        return Boolean.parseBoolean(
                parser.getAttributeValue(null /* namespace */, ATTR_IS_DEVICE_FINALIZED));
    }

    /**
     * Callback for when state is read from disk.
     */
    interface DeviceStateCallback {
        /**
         * Callback for when state is finished reading from disk.
         *
         * @param isFinalized whether device is finalized
         */
        void onDeviceStateRead(boolean isFinalized);
    }
}

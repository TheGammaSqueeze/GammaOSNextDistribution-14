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

package android.alarmmanager.cts;

import android.util.Log;
import android.util.LongArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

/**
 * This class is a lightweight wrapper to store app-global history of events persisted to disk.
 * For this test app, this is used exclusively to record alarm events to facilitate
 * pending quota calculations. But this stores only timestamps, and so can be used to record any
 * kind of events.
 * This deliberately goes against the principle of test-case isolation, but quotas are managed per
 * app which makes them impossible to test without persisting some state across test-cases.
 */
public class PersistableEventHistory {
    private static final String TAG = PersistableEventHistory.class.getSimpleName();

    final LongArray mHistory;
    final File mFile;

    PersistableEventHistory(File file) {
        mFile = file;
        mHistory = (file.exists() ? readFromFile() : new LongArray());
    }

    synchronized LongArray readFromFile() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(mFile))) {
            final long[] restored = (long[]) in.readObject();
            Log.i(TAG, "Loaded existing event history " + Arrays.toString(restored)
                    + " from " + mFile.getName());
            return LongArray.wrap(restored);
        } catch (Exception e) {
            throw new RuntimeException("Could not load event history from " + mFile.getName(), e);
        }
    }

    synchronized void saveToFile() {
        final long[] historyArray = mHistory.toArray();
        Log.i(TAG, "Saving event history " + Arrays.toString(historyArray)
                + " to " + mFile.getName());

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(mFile))) {
            out.writeObject(historyArray);
        } catch (IOException e) {
            throw new RuntimeException("Could not save event history to " + mFile.getName(), e);
        }
    }

    synchronized long getNthLastEventTime(int n) {
        if (n <= 0 || n > mHistory.size()) {
            return 0;
        }
        return mHistory.get(mHistory.size() - n);
    }

    synchronized void recordLatestEvent(long timeOfReceipt) {
        if (mHistory.size() == 0 || mHistory.get(mHistory.size() - 1) < timeOfReceipt) {
            mHistory.add(timeOfReceipt);
            saveToFile();
        }
    }

    synchronized void dump(String prefix) {
        Log.i(TAG, prefix + ": " + Arrays.toString(mHistory.toArray()));
    }
}

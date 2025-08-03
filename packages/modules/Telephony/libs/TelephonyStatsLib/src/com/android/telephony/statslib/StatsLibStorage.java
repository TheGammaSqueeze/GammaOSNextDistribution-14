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

package com.android.telephony.statslib;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Stores and aggregates metrics for pulled atoms. */
class StatsLibStorage {

    private static final String LOG_TAG = StatsLibStorage.class.getSimpleName();
    private static final boolean DBG = true;
    private static final boolean TEST_DBG = true;

    private static final String STORAGE_FILE =
            StatsLibStorage.class.getSimpleName() + "_persist_ID.pb";
    private final Handler mHandler;

    private final Context mContext;
    private final ConcurrentHashMap<Integer, List<AtomsPushed>> mPushed;
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, AtomsPulled>> mPulled;

    /** Constructor of StatsLibStorage */
    StatsLibStorage(Context context) {
        mContext = context;
        mPushed = new ConcurrentHashMap<>();
        mPulled = new ConcurrentHashMap<>();
        HandlerThread handlerThread = new HandlerThread("StatsLibStorage");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        log("created StatsLibStorage.");
    }

    @VisibleForTesting
    protected StatsLibStorage(
            Context context,
            ConcurrentHashMap<Integer, List<AtomsPushed>> pushedMap,
            ConcurrentHashMap<Integer, ConcurrentHashMap<String, AtomsPulled>> pulledMap,
            Handler testHandler) {
        mContext = context;
        mPushed = pushedMap;
        mPulled = pulledMap;
        mHandler = testHandler;
        log("created StatsLibStorage for testing");
    }

    private void log(String s) {
        if (DBG) Log.d(LOG_TAG, s);
    }

    private void loge(String s) {
        Log.e(LOG_TAG, s);
    }

    private void logt(String s) {
        if (TEST_DBG) Log.d(LOG_TAG, s);
    }

    /** Initialize storage for the given statsId. */
    void init(int statsId) {
        List<AtomsPushed> pushed = mPushed.get(statsId);
        if (pushed != null) {
            pushed.clear();
        }
        ConcurrentHashMap<String, AtomsPulled> pulled = mPulled.get(statsId);
        if (pulled != null) {
            pulled.clear();
        }
    }

    /**
     * Append the Pushed Atoms
     *
     * @param info AtomsInfoBase
     */
    void appendPushedAtoms(AtomsPushed info) {
        List<AtomsPushed> atoms =
                mPushed.computeIfAbsent(info.getStatsId(), k -> new ArrayList<>());
        atoms.add(info.copy());
        logt("appendPushedAtoms, AtomsPushed:" + info);
    }

    /** Returns the array of the stored atoms and deletes them all. */
    AtomsPushed[] popPushedAtoms(int statsId) {
        List<AtomsPushed> atoms = mPushed.computeIfAbsent(statsId, k -> new ArrayList<>());
        AtomsPushed[] infos = atoms.toArray(new AtomsPushed[0]);
        logt("popPushedAtoms, AtomsPushed:" + atoms);
        atoms.clear();
        return infos;
    }

    /**
     * Append the Pulled Atoms
     *
     * @param info AtomsInfoBase
     */
    void appendPulledAtoms(AtomsPulled info) {
        ConcurrentHashMap<String, AtomsPulled> atoms =
                mPulled.computeIfAbsent(info.getStatsId(), k -> new ConcurrentHashMap<>());
        AtomsPulled alreadyExistPulled = atoms.get(info.getDimension());
        if (alreadyExistPulled == null) {
            atoms.put(info.getDimension(), info.copy());
            logt("appendPulledAtoms, AtomsPulled:" + info);
        } else {
            alreadyExistPulled.accumulate(info);
            logt("appendPulledAtoms, alreadyExistPulled:" + alreadyExistPulled);
        }
        if (isSerializable(info)) {
            saveToFile(info.getStatsId());
        }
    }

    /** Returns the array of the stored atoms and deletes them all. */
    AtomsPulled[] popPulledAtoms(int statsId) {
        ConcurrentHashMap<String, AtomsPulled> atoms = mPulled.get(statsId);
        if (atoms == null) {
            return null;
        }
        AtomsPulled[] infos = atoms.values().toArray(new AtomsPulled[0]);
        logt("popPulledAtoms, atoms:" + atoms);
        atoms.clear();
        saveToFile(statsId);
        return infos;
    }

    /** save serializable pulled atoms to a backup file for given statsId. */
    void saveToFile(int statsId) {
        mHandler.post(() -> saveToFileImmediately(statsId));
    }

    /** save atoms to file run as thread */
    private void saveToFileImmediately(int statsId) {
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        List<AtomsPulled> toFileList = getSerializablePulledAtoms(statsId);
        try {
            // TODO b/265727262 if possible, Requires changes to repositories that do not require
            //  selinux rule.
            String filename = getFileName(statsId);
            fileOutputStream = mContext.openFileOutput(filename, Context.MODE_PRIVATE);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(toFileList);
            objectOutputStream.flush();
            logt("saveToFileImmediately, " + filename + " saved, toFileList:" + toFileList);
        } catch (IOException e) {
            loge("cannot save atoms, e:" + e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    loge("exception in saveToFile filestream close, e:" + e);
                }
            }
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    loge("exception in saveToFile object stream close, e:" + e);
                }
            }
        }
    }

    private boolean isSerializable(AtomsPulled info) {
        return info instanceof Serializable;
    }

    private String getFileName(int statsId) {
        return (STORAGE_FILE).replace("ID", Integer.toString(statsId));
    }

    private List<AtomsPulled> getSerializablePulledAtoms(int statsId) {
        ConcurrentHashMap<String, AtomsPulled> pulls =
                mPulled.computeIfAbsent(statsId, k -> new ConcurrentHashMap<>());
        List<AtomsPulled> serializable = new ArrayList<>();
        for (AtomsPulled p : pulls.values()) {
            if (isSerializable(p)) {
                serializable.add(p);
            }
        }
        return serializable;
    }

    private void setSerializablePulledAtoms(
            int statsId, List<AtomsPulled> serializablePulledAtoms) {
        if (serializablePulledAtoms == null || serializablePulledAtoms.isEmpty()) {
            return;
        }
        ConcurrentHashMap<String, AtomsPulled> atoms =
                mPulled.computeIfAbsent(statsId, k -> new ConcurrentHashMap<>());
        for (AtomsPulled info : serializablePulledAtoms) {
            AtomsPulled alreadyExistPulled = atoms.get(info.getDimension());
            if (alreadyExistPulled == null) {
                atoms.put(info.getDimension(), info.copy());
            } else {
                alreadyExistPulled.accumulate(info);
            }
        }
    }


    /** save serializable pulled atoms to a backup file for given statsId. */
    void loadFromFile(int statsId) {
        mHandler.post(() -> loadFromFileImmediately(statsId));
    }

    /** load atoms from file run as thread */
    private void loadFromFileImmediately(int statsId) {
        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        List<AtomsPulled> fromFileList;
        try {
            // TODO b/265727262 if possible, Requires changes to repositories that do not require
            //  selinux rule.
            String filename = getFileName(statsId);
            fileInputStream = mContext.openFileInput(filename);
            objectInputStream = new ObjectInputStream(fileInputStream);
            fromFileList = (ArrayList<AtomsPulled>) objectInputStream.readObject();
            logt("loadFromFile, " + filename + " loaded, fromFileList:" + fromFileList);
            setSerializablePulledAtoms(statsId, fromFileList);
        } catch (IOException e) {
            loge("IOException cannot load atoms, e:" + e);
        } catch (ClassNotFoundException e) {
            loge("ClassNotFoundException cannot load atoms, e:" + e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    loge("exception in loadFromFile filestream close, e:" + e);
                }
            }
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    loge("exception in loadFromFile object stream close, e:" + e);
                }
            }
        }
    }
}

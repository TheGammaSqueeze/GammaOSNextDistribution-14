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

package com.android.systemui.car;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.ArrayMap;
import android.util.Dumpable;
import android.util.DumpableContainer;
import android.util.Log;

import com.android.systemui.dump.DumpManager;

import javax.annotation.concurrent.GuardedBy;

/**
 * Context Wrapper that is Dumpable.
 */
public class CarDumpableContext extends ContextWrapper implements DumpableContainer {
    private static final String TAG = CarDumpableContext.class.getName();
    private static final boolean DEBUG = false;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final ArrayMap<String, Dumpable> mDumpables = new ArrayMap<>();
    private final DumpManager mDumpManager;

    public CarDumpableContext(Context base, DumpManager dumpManager) {
        super(base);
        mDumpManager = dumpManager;
    }

    @Override
    public boolean addDumpable(Dumpable dumpable) {
        String name = dumpable.getDumpableName();
        synchronized (mLock) {
            if (mDumpables.containsKey(name)) {
                if (DEBUG) {
                    Log.d(TAG, "addDumpable(): ignoring " + dumpable
                            + " as there is already a dumpable with that name (" + name + "): "
                            + mDumpables.get(name));
                }
                return false;
            }
            if (DEBUG) Log.d(TAG, "addDumpable(): adding '" + name + "' = " + dumpable);
            mDumpables.put(name, dumpable);
            mDumpManager.registerNormalDumpable(dumpable.getDumpableName(), dumpable::dump);
        }
        return true;
    }

    @Override
    public boolean removeDumpable(Dumpable dumpable) {
        String name = dumpable.getDumpableName();
        synchronized (mLock) {
            mDumpManager.unregisterDumpable(name);
            mDumpables.remove(name);
        }
        return true;
    }
}

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

package com.android.adservices.service.topics;

import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;

/** Gets global blocked topics via Ph flag. */
public class GlobalBlockedTopicsManager {
    private static GlobalBlockedTopicsManager sSingleton;
    private static final Object SINGLETON_LOCK = new Object();
    private HashSet<Integer> mGlobalBlockedTopicIds;

    @VisibleForTesting
    GlobalBlockedTopicsManager(@NonNull HashSet<Integer> globalBlockedTopicIds) {
        mGlobalBlockedTopicIds = globalBlockedTopicIds;
    }

    /** Returns an instance of the {@link GlobalBlockedTopicsManager}. */
    public static GlobalBlockedTopicsManager getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                HashSet<Integer> globalBlockedTopicIds =
                        getGlobalBlockedTopicIdsFromFlag(FlagsFactory.getFlags());
                sSingleton = new GlobalBlockedTopicsManager(globalBlockedTopicIds);
            }
        }
        return sSingleton;
    }

    /** Returns global blocked topic Ids via Ph flag. */
    public HashSet<Integer> getGlobalBlockedTopicIds() {
        return mGlobalBlockedTopicIds;
    }

    @VisibleForTesting
    static HashSet<Integer> getGlobalBlockedTopicIdsFromFlag(@NonNull Flags flags) {
        HashSet<Integer> globalBlockedTopicIds = new HashSet<>();
        ImmutableList<Integer> blockedTopicIdsInteger = flags.getGlobalBlockedTopicIds();
        if (blockedTopicIdsInteger != null) {
            globalBlockedTopicIds.addAll(blockedTopicIdsInteger);
        }
        return globalBlockedTopicIds;
    }
}

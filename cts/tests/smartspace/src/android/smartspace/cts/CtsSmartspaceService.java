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

package android.smartspace.cts;

import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceSessionId;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.service.smartspace.SmartspaceService;
import android.util.ArrayMap;
import android.util.Log;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CtsSmartspaceService extends SmartspaceService {

    private static final boolean DEBUG = true;
    public static final String EXTRA_REPORTER = "extra_reporter";
    public static final String MY_PACKAGE = "android.smartspace.cts";
    public static final String SERVICE_NAME = MY_PACKAGE + "/."
            + CtsSmartspaceService.class.getSimpleName();
    private static final String TAG = CtsSmartspaceService.class.getSimpleName();

    private static Watcher sWatcher;

    private final ArrayMap<SmartspaceSessionId, List<SmartspaceTarget>> targets = new ArrayMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
//        Log.d(TAG, "onCreate mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG) Log.d(TAG, "onCreate");
    }

    @Override
    public void onCreateSmartspaceSession(SmartspaceConfig config, SmartspaceSessionId sessionId) {
//        Log.d(TAG, "onCreateSmartspaceSession mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG) Log.d(TAG, "onCreateSmartspaceSession");

        if (sWatcher.verifier != null) {
            Log.e(TAG, "onCreateSmartspaceSession, trying to set verifier when it already exists");
        } else {
            sWatcher.verifier = Mockito.mock(CtsSmartspaceService.class);
        }
        targets.put(sessionId, new ArrayList<>());
        sWatcher.created.countDown();
    }

    @Override
    public void notifySmartspaceEvent(SmartspaceSessionId sessionId, SmartspaceTargetEvent event) {
//        Log.d(TAG, "notifySmartspaceEvent mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG){
            Log.d(TAG, "notifySmartspaceEvent sessionId=" + sessionId + ", event=" + event.toString());
        }
        if(event.getSmartspaceTarget() != null) {
            targets.get(sessionId).add(event.getSmartspaceTarget());
        }
        sWatcher.verifier.notifySmartspaceEvent(sessionId, event);
    }

    @Override
    public void onRequestSmartspaceUpdate(SmartspaceSessionId sessionId) {
//        Log.d(TAG, "onRequestSmartspaceUpdate mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG){
            Log.d(TAG, "onRequestSmartspaceUpdate sessionId=" + sessionId);
        }
        List<SmartspaceTarget> returnList = targets.get(sessionId);
        if(returnList == null) {
            returnList = new ArrayList<>();
        }
        updateSmartspaceTargets(sessionId, returnList);
        sWatcher.verifier.onRequestSmartspaceUpdate(sessionId);
    }

    @Override
    public void onDestroySmartspaceSession(SmartspaceSessionId sessionId) {
//        Log.d(TAG, "onDestroySmartspaceSession mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG) Log.d(TAG, "onDestroySmartspaceSession");
        targets.remove(sessionId);
        super.onDestroy();
        sWatcher.destroyed.countDown();
    }

    @Override
    public void onDestroy(SmartspaceSessionId sessionId) {
//        Log.d(TAG, "onDestroy mSessionCallbacks: " + mSessionCallbacks);
        if (DEBUG) Log.d(TAG, "onDestroy");
        super.onDestroy();
        sWatcher.destroyed.countDown();
    }


    public static Watcher setWatcher() {
        if (DEBUG) {
            Log.d(TAG, "");
            Log.d(TAG, "----------------------------------------------");
            Log.d(TAG, " setWatcher");
        }
        if (sWatcher != null) {
            throw new IllegalStateException("Set watcher with watcher already set");
        }
        sWatcher = new Watcher();
        return sWatcher;
    }

    public static void clearWatcher() {
        if (DEBUG) Log.d(TAG, "clearWatcher");
        sWatcher = null;
    }

    public static final class Watcher {
        public CountDownLatch created = new CountDownLatch(1);
        public CountDownLatch destroyed = new CountDownLatch(1);
        public CountDownLatch queried = new CountDownLatch(1);
        public CountDownLatch queriedTwice = new CountDownLatch(2);

        /**
         * Can be used to verify that API specific service methods are called. Not a real mock as
         * the system isn't talking to this directly, it has calls proxied to it.
         */
        public CtsSmartspaceService verifier;

        public List<SmartspaceTarget> mSmartspaceTargets;

        public void setTargets(List<SmartspaceTarget> targets) {
            mSmartspaceTargets = targets;
        }
    }
}

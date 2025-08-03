/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.searchuiservice.cts;

import android.app.search.Query;
import android.app.search.SearchContext;
import android.app.search.SearchSessionId;
import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.content.Intent;
import android.service.search.SearchUiService;
import android.util.Log;

import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class CtsSearchUiService extends SearchUiService {

    private static final boolean DEBUG = false;
    private static final String TAG = CtsSearchUiService.class.getSimpleName();

    public static final String MY_PACKAGE = "android.searchuiservice.cts";
    public static final String SERVICE_NAME = MY_PACKAGE + "/."
            + CtsSearchUiService.class.getSimpleName();

    private static Watcher sWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");

    }

    @Override
    public void onDestroy(SearchSessionId sessionId) {
        if (DEBUG) Log.d(TAG, "onDestroy");
        super.onDestroy();
        sWatcher.destroyed.countDown();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) Log.d(TAG, "unbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onSearchSessionCreated(SearchContext context,
            SearchSessionId sessionId) {
        if (DEBUG) Log.d(TAG, "onCreateSearchSession");
        super.onSearchSessionCreated(context, sessionId);
        if (sWatcher.verifier != null) {
            Log.e(TAG, "onCreateSearchSession, trying to set verifier when it already exists");
        }
        sWatcher.verifier = Mockito.mock(CtsSearchUiService.class);
        sWatcher.created.countDown();
        sWatcher.searchContext = context;
    }

    @Override
    public void onNotifyEvent(SearchSessionId sessionId,
            Query input, SearchTargetEvent event) {
        if (DEBUG){
            Log.d(TAG, "onNotifyEvent query=" + input.getInput() + ", event="
                    + event.getTargetId());
        }
        sWatcher.verifier.onNotifyEvent(sessionId, input, event);
    }

    @Override
    public void onQuery(SearchSessionId sessionId, Query input,
            Consumer<List<SearchTarget>> callback) {
        if (DEBUG) Log.d(TAG, "onQuery query=" + input.getInput());
        if (sWatcher.searchTargets != null) {
            callback.accept(sWatcher.searchTargets);
        } else {
            sWatcher.verifier.onQuery(sessionId, input, callback);
        }
    }

    @Override
    public void onStartUpdateEmptyQueryResult() {
        super.onStartUpdateEmptyQueryResult();
        sWatcher.startedUpdateEmptyQueryResult.countDown();
        sWatcher.verifier.onStartUpdateEmptyQueryResult();
    }

    @Override
    public void onStopUpdateEmptyQueryResult() {
        super.onStopUpdateEmptyQueryResult();
        sWatcher.stoppedUpdateEmptyQueryResult.countDown();
        sWatcher.verifier.onStopUpdateEmptyQueryResult();
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
        public CountDownLatch startedUpdateEmptyQueryResult = new CountDownLatch(1);
        public CountDownLatch requestEmptyQueryResultUpdate = new CountDownLatch(1);
        public CountDownLatch stoppedUpdateEmptyQueryResult = new CountDownLatch(1);

        /**
         * Can be used to verify that API specific service methods are called. Not a real mock as
         * the system isn't talking to this directly, it has calls proxied to it.
         */
        public CtsSearchUiService verifier;

        public List<SearchTarget> searchTargets;

        public SearchContext searchContext;

        public void setTargets(List<SearchTarget> targets) {
            searchTargets = targets;
        }
    }
}

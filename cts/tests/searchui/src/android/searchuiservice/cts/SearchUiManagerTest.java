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

import static android.searchuiservice.cts.SearchUiUtils.QUERY_INPUT;
import static android.searchuiservice.cts.SearchUiUtils.QUERY_TIMESTAMP;
import static android.searchuiservice.cts.SearchUiUtils.RESULT_CORPUS1;
import static android.searchuiservice.cts.SearchUiUtils.RESULT_CORPUS2;
import static android.searchuiservice.cts.SearchUiUtils.RESULT_CORPUS3;
import static android.searchuiservice.cts.SearchUiUtils.generateQuery;
import static android.searchuiservice.cts.SearchUiUtils.generateSearchTargetList;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.search.Query;
import android.app.search.SearchContext;
import android.app.search.SearchSession;
import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.app.search.SearchUiManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tests for {@link SearchUiManager}
 *
 * atest CtsSearchUiServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SearchUiManagerTest {

    private static final String TAG = "SearchUiManagerTest";
    private static final boolean DEBUG = false;

    private static final long VERIFY_TIMEOUT_MS = 5_000;
    private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 20_000;
    private static final int RESULT_TYPES = RESULT_CORPUS1 | RESULT_CORPUS2 | RESULT_CORPUS3;
    private static final int TIMEOUT_MS = 150;
    private static final Bundle EXTRAS = new Bundle();

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.SEARCH_UI_SERVICE);

    private SearchUiManager mManager;
    private SearchSession mClient;
    private CtsSearchUiService.Watcher mWatcher;

    @Before
    public void setUp() throws Exception {
        mWatcher = CtsSearchUiService.setWatcher();
        mManager = getContext().getSystemService(SearchUiManager.class);
        setService(CtsSearchUiService.SERVICE_NAME);
        SearchContext searchContext = new SearchContext(RESULT_TYPES, TIMEOUT_MS, EXTRAS);
        mClient = mManager.createSearchSession(searchContext);
        await(mWatcher.created, "Waiting for onCreate()");
        reset(mWatcher.verifier);
    }

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "Starting tear down, watcher is: " + mWatcher);
        mClient.close();
        setService(null);
        await(mWatcher.destroyed, "Waiting for onDestroy()");

        mWatcher = null;
        CtsSearchUiService.clearWatcher();
    }

    @Test
    public void testCreateSearchSession() {
        assertNotNull(mClient);
        assertNotNull(mWatcher.verifier);
        assertEquals(mWatcher.searchContext.getPackageName(), getContext().getPackageName());
        assertEquals(mWatcher.searchContext.getResultTypes(), RESULT_TYPES);
        assertEquals(mWatcher.searchContext.getTimeoutMillis(), TIMEOUT_MS);
        assertTrue(equalBundles(mWatcher.searchContext.getExtras(), EXTRAS));
    }

    @Test
    public void testSearchContextCtor() {
        SearchContext searchContext = new SearchContext(RESULT_TYPES, TIMEOUT_MS);
        assertTrue(equalBundles(EXTRAS, searchContext.getExtras()));
    }

    @Test
    public void testQueryCtor() {
        Query query = new Query(QUERY_INPUT, QUERY_TIMESTAMP);
        assertTrue(equalBundles(EXTRAS, query.getExtras()));
    }

    public boolean equalBundles(Bundle one, Bundle two) {
        if (one.size() != two.size()) {
            return false;
        }

        Set<String> setOne = new HashSet<>(one.keySet());
        setOne.addAll(two.keySet());
        Object valueOne;
        Object valueTwo;

        for (String key : setOne) {
            if (!one.containsKey(key) || !two.containsKey(key)) {
                return false;
            }

            valueOne = one.get(key);
            valueTwo = two.get(key);
            if (valueOne instanceof Bundle && valueTwo instanceof Bundle
                    && !equalBundles((Bundle) valueOne, (Bundle) valueTwo)) {
                return false;
            } else if (valueOne == null) {
                if (valueTwo != null) {
                    return false;
                }
            } else if (!valueOne.equals(valueTwo)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testNotifyEvent() {
        String targetId = "sample target id";
        int action = SearchTargetEvent.ACTION_SURFACE_VISIBLE;
        SearchTargetEvent event = new SearchTargetEvent.Builder(targetId, action)
                .setFlags(SearchTargetEvent.FLAG_IME_SHOWN)
                .setLaunchLocation("1,0")
                .build();
        mClient.notifyEvent(generateQuery(new Bundle()), event);

        ArgumentCaptor<Query> queryArg = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<SearchTargetEvent> eventArg
                = ArgumentCaptor.forClass(SearchTargetEvent.class);

        verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS))
                .onNotifyEvent(any(), queryArg.capture(), eventArg.capture());

        assertTrue(queryArg.getValue().getInput().equals(QUERY_INPUT));
        assertEquals(queryArg.getValue().getTimestampMillis(), QUERY_TIMESTAMP);
        assertTrue(eventArg.getValue().getTargetId().equals(targetId));
        assertEquals(eventArg.getValue().getAction(), action);
    }

    @Test
    public void testQuery_realCallback() {
        Query query = SearchUiUtils.generateQuery(new Bundle());
        List<SearchTarget> targets = SearchUiUtils.generateSearchTargetList(3);

        final ConsumerVerifier callbackVerifier = new ConsumerVerifier(targets /* expected */);
        mWatcher.setTargets(targets /* actual */);
        mClient.query(query, Executors.newSingleThreadExecutor(), callbackVerifier);
    }

    // flaky: 8 failure out of 100
    @Ignore
    public void testQuery_mockCallback() {
        List<SearchTarget> targets = SearchUiUtils.generateSearchTargetList(2);
        Query query = SearchUiUtils.generateQuery(new Bundle());

        final ConsumerVerifier callbackVerifier = new ConsumerVerifier(targets);
        mClient.query(query, Executors.newSingleThreadExecutor(), callbackVerifier);

        doAnswer(answer -> {
            Consumer<List<SearchTarget>> consumer
                    = (Consumer<List<SearchTarget>>) answer.getArgument(2);
            consumer.accept(targets);
            return null;
        }).when(mWatcher.verifier).onQuery(any(), any(), any(Consumer.class));
    }

    @Test
    public void testRegisterPredictionUpdatesLifeCycle_realCallback() {
        List<SearchTarget> targets = SearchUiUtils.generateSearchTargetList(3);
        mWatcher.setTargets(targets /* actual */);

        final EmptyQueryResultCallbackVerifier callbackVerifier =
                new EmptyQueryResultCallbackVerifier(targets /* expected */);

        mClient.registerEmptyQueryResultUpdateCallback(Executors.newSingleThreadExecutor(),
                callbackVerifier);
        await(mWatcher.startedUpdateEmptyQueryResult,
                "Waiting for startedUpdateEmptyQueryResult");

        mClient.unregisterEmptyQueryResultUpdateCallback(callbackVerifier);
        await(mWatcher.stoppedUpdateEmptyQueryResult,
                "Waiting for stoppedUpdateEmptyQueryResult");
    }

    @Test
    public void testQuery_params() {
        List<SearchTarget> targets = generateSearchTargetList(2, true, false, false, false);
        Bundle extras = new Bundle();
        Query query = SearchUiUtils.generateQuery(extras);

        final ConsumerVerifier callbackVerifier = new ConsumerVerifier(targets);
        mClient.query(query, Executors.newSingleThreadExecutor(), callbackVerifier);

        ArgumentCaptor<Query> queryArg = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Consumer<List<SearchTarget>>> callbackArg
                = ArgumentCaptor.forClass(Consumer.class);

        verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS))
                .onQuery(any(), queryArg.capture(), callbackArg.capture());

        Query expectedQuery = queryArg.getValue();
        assertTrue(expectedQuery.getInput().equals(QUERY_INPUT));
        assertEquals(expectedQuery.getTimestampMillis(), QUERY_TIMESTAMP);
        assertTrue(equalBundles(expectedQuery.getExtras(), extras));
    }

    private void setService(String service) {
        Log.d(TAG, "Setting search ui service to " + service);
        int userId = Process.myUserHandle().getIdentifier();
        if (service != null) {
            runShellCommand("cmd search_ui set temporary-service "
                    + userId + " " + service + " 60000");
        } else {
            runShellCommand("cmd search_ui set temporary-service " + userId);
        }
    }

    private void await(@NonNull CountDownLatch latch, @NonNull String message) {
        try {
            assertWithMessage(message).that(
                    latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + message);
        }
    }

    private void runShellCommand(String command) {
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    public static class ConsumerVerifier implements
            Consumer<List<SearchTarget>> {

        private static List<SearchTarget> mExpectedTargets;

        public ConsumerVerifier(List<SearchTarget> targets) {
            mExpectedTargets = targets;
        }

        @Override
        public void accept(List<SearchTarget> actualTargets) {
            if (DEBUG) {
                Log.d(TAG, "ConsumerVerifier.accept targets.size= " + actualTargets.size());
                Log.d(TAG, "ConsumerVerifier.accept target(1).packageName=" + actualTargets.get(
                        0).getPackageName());
            }
            Assert.assertArrayEquals(actualTargets.toArray(), mExpectedTargets.toArray());
        }
    }

    public static class EmptyQueryResultCallbackVerifier implements
            SearchSession.Callback {

        private static List<SearchTarget> sExpectedTargets;

        public EmptyQueryResultCallbackVerifier(List<SearchTarget> targets) {
            sExpectedTargets = targets;
        }

        @Override
        public void onTargetsAvailable(List<SearchTarget> actualTargets) {
            if (DEBUG) {
                Log.d(TAG, "ZeroStateCallbackVerifier.onTargetsAvailable targets.size= "
                        + actualTargets.size());
                Log.d(TAG, "ZeroStateCallbackVerifier.onTargetsAvailable target(1).packageName="
                        + actualTargets.get(0).getPackageName());
            }
            Assert.assertArrayEquals(actualTargets.toArray(), sExpectedTargets.toArray());
        }
    }
}

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
package android.view.textclassifier.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.service.textclassifier.TextClassifierService;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.SelectionEvent;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationContext;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassificationSessionId;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import androidx.core.os.BuildCompat;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SafeCleanerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for TextClassifierService query related functions.
 *
 * <p>
 * We use a non-standard TextClassifierService for TextClassifierService-related CTS tests. A
 * non-standard TextClassifierService that is set via device config. This non-standard
 * TextClassifierService is not defined in the trust TextClassifierService, it should only receive
 * queries from clients in the same package.
 */
@RunWith(AndroidJUnit4.class)
@Ignore("b/318869191")
public class TextClassifierServiceSwapTest {
    // TODO: Add more tests to verify all the TC APIs call between caller and TCS.
    private static final String TAG = "TextClassifierServiceSwapTest";

    private final TextClassifierTestWatcher mTestWatcher =
            CtsTextClassifierService.getTestWatcher();
    private final SafeCleanerRule mSafeCleanerRule = mTestWatcher.newSafeCleaner();
    private final TextClassificationContext mTextClassificationContext =
            new TextClassificationContext.Builder(
                    ApplicationProvider.getApplicationContext().getPackageName(),
                    TextClassifier.WIDGET_TYPE_EDIT_WEBVIEW)
                    .build();
    private final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.TEXT_CLASSIFICATION_SERVICE);

    @Rule
    public final RuleChain mAllRules = RuleChain
            .outerRule(mRequiredServiceRule)
            .around(mTestWatcher)
            .around(mSafeCleanerRule);

    @Test
    public void testOutsideOfPackageActivity_noRequestReceived() throws Exception {
        // Start an Activity from another package to trigger a TextClassifier call
        runQueryTextClassifierServiceActivity();

        // Wait for the TextClassifierService to connect.
        // Note that the system requires a query to the TextClassifierService before it is
        // first connected.
        final CtsTextClassifierService service = mTestWatcher.getService();

        // Wait a delay for the query is delivered.
        service.awaitQuery();

        // Verify the request was not passed to the service.
        assertThat(service.getRequestSessions()).isEmpty();
    }

    @Test
    public void testMultipleActiveSessions() throws Exception {
        final TextClassification.Request request =
                new TextClassification.Request.Builder("Hello World", 0, 1).build();
        final TextClassificationManager tcm =
                ApplicationProvider.getApplicationContext().getSystemService(
                        TextClassificationManager.class);
        final TextClassifier firstSession =
                tcm.createTextClassificationSession(mTextClassificationContext);
        final TextClassifier secondSessionSession =
                tcm.createTextClassificationSession(mTextClassificationContext);

        firstSession.classifyText(request);
        final CtsTextClassifierService service = mTestWatcher.getService();
        service.awaitQuery();

        service.resetRequestLatch(1);
        secondSessionSession.classifyText(request);
        service.awaitQuery();

        final List<TextClassificationSessionId> sessionIds =
                service.getRequestSessions().get("onClassifyText");
        assertThat(sessionIds).hasSize(2);
        assertThat(sessionIds.get(0).getValue()).isNotEqualTo(sessionIds.get(1).getValue());

        service.resetRequestLatch(2);
        firstSession.destroy();
        secondSessionSession.destroy();
        service.awaitQuery();

        final List<TextClassificationSessionId> destroyedSessionIds =
                service.getRequestSessions().get("onDestroyTextClassificationSession");
        assertThat(destroyedSessionIds).hasSize(2);
        firstSession.isDestroyed();
        secondSessionSession.isDestroyed();
    }

    private void serviceOnSuggestConversationActions(CtsTextClassifierService service)
            throws Exception {
        ConversationActions.Request conversationActionRequest =
                new ConversationActions.Request.Builder(Collections.emptyList()).build();
        // TODO: add @TestApi for TextClassificationSessionId and use it
        SelectionEvent event =
                SelectionEvent.createSelectionStartedEvent(
                        SelectionEvent.INVOCATION_LINK, 1);
        final CountDownLatch onSuccessLatch = new CountDownLatch(1);
        service.onSuggestConversationActions(event.getSessionId(),
                conversationActionRequest, new CancellationSignal(),
                new TextClassifierService.Callback<ConversationActions>() {
                    @Override
                    public void onFailure(CharSequence charSequence) {
                        // do nothing
                    }

                    @Override
                    public void onSuccess(ConversationActions o) {
                        onSuccessLatch.countDown();
                    }
                });
        onSuccessLatch.await();
        final List<TextClassificationSessionId> sessionIds =
                service.getRequestSessions().get("onSuggestConversationActions");
        assertThat(sessionIds).hasSize(1);
    }

    @Test
    public void testTextClassifierServiceApiCoverage() throws Exception {
        // Implemented for API test coverage only
        // Any method that is better tested not be called here.
        // We already have tests for the TC APIs
        // We however need to directly query the TextClassifierService methods in the tests for
        // code coverage.
        CtsTextClassifierService service = new CtsTextClassifierService();

        service.onConnected();

        serviceOnSuggestConversationActions(service);

        service.onDisconnected();
    }

    @Test
    public void testResourceIconsRewrittenToContentUriIcons_classifyText() throws Exception {
        final TextClassifier tc = ApplicationProvider.getApplicationContext()
                .getSystemService(TextClassificationManager.class)
                .getTextClassifier();
        final TextClassification.Request request =
                new TextClassification.Request.Builder("0800 123 4567", 0, 12).build();

        final TextClassification classification = tc.classifyText(request);
        final Icon icon = classification.getActions().get(0).getIcon();
        assertThat(icon.getType()).isEqualTo(Icon.TYPE_URI);
        assertThat(icon.getUri()).isEqualTo(CtsTextClassifierService.ICON_URI.getUri());
    }

    @Test
    public void testResourceIconsRewrittenToContentUriIcons_suggestSelection() throws Exception {
        assumeTrue(BuildCompat.isAtLeastS());

        final TextClassifier tc = ApplicationProvider.getApplicationContext()
                .getSystemService(TextClassificationManager.class)
                .getTextClassifier();
        final TextSelection.Request request =
                new TextSelection.Request.Builder("0800 123 4567", 0, 12)
                        .setIncludeTextClassification(true)
                        .build();

        final TextSelection textSelection = tc.suggestSelection(request);
        final Icon icon = textSelection.getTextClassification().getActions().get(0).getIcon();
        assertThat(icon.getType()).isEqualTo(Icon.TYPE_URI);
        assertThat(icon.getUri()).isEqualTo(CtsTextClassifierService.ICON_URI.getUri());
    }

    /**
     * Start an Activity from another package that queries the device's TextClassifierService when
     * started and immediately terminates itself. When the Activity finishes, it sends broadcast, we
     * check that whether the finish broadcast is received.
     */
    private void runQueryTextClassifierServiceActivity() {
        final String actionQueryActivityFinish =
                "ACTION_QUERY_SERVICE_ACTIVITY_FINISH_" + SystemClock.uptimeMillis();
        final Context context = InstrumentationRegistry.getTargetContext();

        // register a activity finish receiver
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context,
                actionQueryActivityFinish);
        receiver.register();

        // Start an Activity from another package
        final Intent outsideActivity = new Intent();
        outsideActivity.setComponent(new ComponentName("android.textclassifier.cts2",
                "android.textclassifier.cts2.QueryTextClassifierServiceActivity"));
        outsideActivity.setFlags(FLAG_ACTIVITY_NEW_TASK);
        final Intent broadcastIntent = new Intent(actionQueryActivityFinish);
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    broadcastIntent,
                    PendingIntent.FLAG_IMMUTABLE);
        outsideActivity.putExtra("finishBroadcast", pendingIntent);
        context.startActivity(outsideActivity);

        TextClassifierTestWatcher.waitForIdle();

        // Verify the finish broadcast is received.
        final Intent intent = receiver.awaitForBroadcast();
        assertThat(intent).isNotNull();

        // unregister receiver
        receiver.unregisterQuietly();
    }
}

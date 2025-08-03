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

package android.translation.cts;

import static android.content.Context.CONTENT_CAPTURE_MANAGER_SERVICE;
import static android.content.Context.TRANSLATION_MANAGER_SERVICE;
import static android.provider.Settings.Global.ANIMATOR_DURATION_SCALE;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START;
import static android.translation.cts.Helper.ACTION_REGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.EXTRA_CALL_COUNT;
import static android.translation.cts.Helper.EXTRA_FINISH_COMMAND;
import static android.translation.cts.Helper.EXTRA_PACKAGE_NAME;
import static android.translation.cts.Helper.EXTRA_SOURCE_LOCALE;
import static android.translation.cts.Helper.EXTRA_TARGET_LOCALE;
import static android.view.translation.TranslationResponseValue.STATUS_SUCCESS;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.icu.util.ULocale;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.service.contentcapture.ContentCaptureService;
import android.service.translation.TranslationService;
import android.translation.cts.views.CustomTextView;
import android.translation.cts.views.ResponseNotSetTextView;
import android.translation.cts.views.VirtualContainerView;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.inputmethod.InputMethodManager;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.UiTranslationManager;
import android.view.translation.UiTranslationSpec;
import android.view.translation.UiTranslationStateCallback;
import android.view.translation.ViewTranslationCallback;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestNameUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Tests for {@link UiTranslationManager} related APIs.
 *
 * <p>
 * {@link UiTranslationManager} needs a token that reports by {@link ContentCaptureService}. We use
 * a non pre-configured {@link ContentCaptureService} and a {@link TranslationService} temporary
 * service for CTS tests that is set via shell command. The test will get the token from the
 * {@link ContentCaptureService} then uses this token in {@link UiTranslationManager} APIs.</p>
 */

@FlakyTest(bugId = 285979174)
@AppModeFull(reason = "TODO(b/182330968): disable instant mode. Re-enable after we decouple the "
        + "service from the test package.")
@RunWith(AndroidJUnit4.class)
public class UiTranslationManagerTest {

    private static final String TAG = "UiTranslationManagerTest";

    private static final long UI_WAIT_TIMEOUT = 2500;

    // TODO: Use fw definition when it becomes public or testapi
    private static final String ID_CONTENT_DESCRIPTION = "android:content_description";

    // TODO(b/225466478): This should have a different package from CtsTestIme
    // Should be whatever package this class is in
    private static final String CTS_TESTS_PACKAGE = "android.translation.cts";

    private static Context sContext;
    private static CtsTranslationService.TranslationReplier sTranslationReplier;

    private CtsContentCaptureService.ServiceWatcher mContentCaptureServiceWatcher;
    private CtsTranslationService.ServiceWatcher mTranslationServiceServiceWatcher;
    private ActivityScenario<SimpleActivity> mActivityScenario;
    private ActivityScenario<VirtualContainerViewActivity> mVirtualContainerViewActivityScenario;
    private ActivityScenario<CustomTextViewActivity> mCustomTextViewActivityScenario;

    private VirtualContainerView mVirtualContainerView;
    private ResponseNotSetTextView mResponseNotSetTextView;
    private CustomTextView mCustomTextView;
    private SimpleActivity mSimpleActivity;
    private TextView mTextView;
    private static String sOriginalLogTag;

    @Rule
    public final RequiredServiceRule mContentCaptureServiceRule =
            new RequiredServiceRule(CONTENT_CAPTURE_MANAGER_SERVICE);

    @Rule
    public final RequiredServiceRule mTranslationServiceRule =
            new RequiredServiceRule(TRANSLATION_MANAGER_SERVICE);

    @Rule
    public final TranslationTestWatcher mTranslationTestWatcher = new TranslationTestWatcher();

    @BeforeClass
    public static void oneTimeSetup() {
        sContext = ApplicationProvider.getApplicationContext();
        sTranslationReplier = CtsTranslationService.getTranslationReplier();
        sOriginalLogTag = Helper.enableDebugLog();

        Helper.allowSelfForContentCapture(sContext);
        Helper.setDefaultContentCaptureServiceEnabled(/* enabled= */ false);
    }

    @AfterClass
    public static void oneTimeReset() {
        Helper.unAllowSelfForContentCapture(sContext);
        Helper.setDefaultContentCaptureServiceEnabled(/* enabled= */ true);
        Helper.disableDebugLog(sOriginalLogTag);
    }

    @Before
    public void setup() throws Exception {
        CtsContentCaptureService.resetStaticState();
        CtsTranslationService.resetStaticState();
    }

    @After
    public void cleanup() throws Exception {
        if (mActivityScenario != null) {
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
        }
        if (mVirtualContainerViewActivityScenario != null) {
            mVirtualContainerViewActivityScenario.moveToState(Lifecycle.State.DESTROYED);
        }
        Helper.resetTemporaryContentCaptureService();
        Helper.resetTemporaryTranslationService();
    }

    @Test
    public void testTranslationAfterStartActivityOnSameTask() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        // Register callback
        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        try {
            final String translatedText = "success";
            // Set response
            final TranslationResponse response =
                createViewsTranslationResponse(views, translatedText);
            sTranslationReplier.addResponse(response);

            // Start an Activity in the same task then call translation APIs
            mSimpleActivity.startEmptyActivity();

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            assertScreenText(mTextView, translatedText);

            Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            pauseUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

            resumeUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            finishUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testUiTranslation() throws Throwable {
        try {
            final Pair<List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();

            final CharSequence originalText = mTextView.getText();
            final List<AutofillId> views = result.first;
            final ContentCaptureContext contentCaptureContext = result.second;

            final String translatedText = "success";
            // Set response
            final TranslationResponse response =
                    createViewsTranslationResponse(views, translatedText);
            sTranslationReplier.addResponse(response);

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            // Check request
            final TranslationRequest request = sTranslationReplier.getNextTranslationRequest();
            final List<ViewTranslationRequest> requests = request.getViewTranslationRequests();
            final ViewTranslationRequest viewRequest = requests.get(0);
            assertThat(viewRequest.getAutofillId()).isEqualTo(views.get(0));
            assertThat(viewRequest.getKeys().size()).isEqualTo(1);
            assertThat(viewRequest.getKeys()).containsExactly(ViewTranslationRequest.ID_TEXT);
            assertThat(viewRequest.getValue(ViewTranslationRequest.ID_TEXT).getText())
                    .isEqualTo(originalText.toString());
            CtsTranslationService translationService =
                    mTranslationServiceServiceWatcher.getService();
            TranslationContext translationContext = translationService.getTranslationContext();
            assertThat(translationContext).isNotNull();
            assertThat(translationContext.getActivityId()).isNotNull();
            assertThat(translationContext.getActivityId())
                    .isEqualTo(contentCaptureContext.getActivityId());

            assertScreenText(mTextView, translatedText);
            assertThat(mTextView.getViewTranslationResponse())
                    .isEqualTo(response.getViewTranslationResponses().get(0));

            pauseUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, originalText.toString());

            resumeUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, translatedText);

            finishUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, originalText.toString());

            // Check the Translation session is destroyed after calling finishTranslation()
            translationService.awaitSessionDestroyed();

            // Test re-translating.
            sTranslationReplier.addResponse(createViewsTranslationResponse(views, translatedText));

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            assertScreenText(mTextView, translatedText);

            // Also make sure pausing still works.
            pauseUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, originalText.toString());
        } catch (Throwable t) {
            Helper.takeScreenshotAndSave(sContext, TestNameUtils.getCurrentTestName(),
                    Helper.LOCAL_TEST_FILES_DIR);
            throw t;
        }
    }

    @Test
    public void testUiTranslationWithoutAnimation() throws Throwable {
        final float[] originalAnimationDurationScale = new float[1];
        try {
            // Disable animation
            SystemUtil.runWithShellPermissionIdentity(() -> {
                ContentResolver resolver =
                        ApplicationProvider.getApplicationContext().getContentResolver();
                originalAnimationDurationScale[0] =
                        Settings.Global.getFloat(resolver, ANIMATOR_DURATION_SCALE, 1f);
                Settings.Global.putFloat(resolver, ANIMATOR_DURATION_SCALE, 0);
            });

            final Pair<List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();

            final CharSequence originalText = mTextView.getText();
            final List<AutofillId> views = result.first;
            final ContentCaptureContext contentCaptureContext = result.second;

            final String translatedText = "success";
            // Set response
            final TranslationResponse response =
                    createViewsTranslationResponse(views, translatedText);
            sTranslationReplier.addResponse(response);

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            assertScreenText(mTextView, translatedText);

            pauseUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, originalText.toString());

            resumeUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, translatedText);

            finishUiTranslation(contentCaptureContext);

            assertScreenText(mTextView, originalText.toString());
        } finally {
            // restore animation
            SystemUtil.runWithShellPermissionIdentity(() -> {
                Settings.Global.putFloat(
                        ApplicationProvider.getApplicationContext().getContentResolver(),
                        ANIMATOR_DURATION_SCALE, originalAnimationDurationScale[0]);
            });
        }
    }

    @Test
    public void testPauseUiTranslationThenStartUiTranslation() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final CharSequence originalText = mTextView.getText();
        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        final String translatedText = "success";
        // Set response
        final TranslationResponse response =
                createViewsTranslationResponse(views, translatedText);
        sTranslationReplier.addResponse(response);

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        assertScreenText(mTextView, translatedText);

        pauseUiTranslation(contentCaptureContext);

        assertScreenText(mTextView, originalText.toString());

        sTranslationReplier.addResponse(createViewsTranslationResponse(views, translatedText));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        assertScreenText(mTextView, translatedText);
    }

    @Test
    public void testUiTranslation_CustomViewTranslationCallback() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();
        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        // Set ViewTranslationCallback
        ViewTranslationCallback mockCallback = Mockito.mock(ViewTranslationCallback.class);
        mTextView.setViewTranslationCallback(mockCallback);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        ArgumentCaptor<View> viewArgumentCaptor = ArgumentCaptor.forClass(View.class);
        Mockito.verify(mockCallback, Mockito.times(1)).onShowTranslation(
                viewArgumentCaptor.capture());
        TextView capturedView = (TextView) viewArgumentCaptor.getValue();
        assertThat(capturedView.getAutofillId()).isEqualTo(mTextView.getAutofillId());

        pauseUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onHideTranslation(
                viewArgumentCaptor.capture());
        capturedView = (TextView) viewArgumentCaptor.getValue();
        assertThat(capturedView.getAutofillId()).isEqualTo(mTextView.getAutofillId());

        resumeUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(2)).onShowTranslation(
                viewArgumentCaptor.capture());
        capturedView = (TextView) viewArgumentCaptor.getValue();
        assertThat(capturedView.getAutofillId()).isEqualTo(mTextView.getAutofillId());

        // Clear callback
        mTextView.clearViewTranslationCallback();

        finishUiTranslation(contentCaptureContext);

        // Verify callback does not be called, keep the latest state
        Mockito.verify(mockCallback, Mockito.never()).onClearTranslation(any(View.class));

        // Finally, verify that no unexpected interactions occurred. We cannot use
        // verifyNoMoreInteractions as the callback has some hidden methods.
        Mockito.verify(mockCallback, Mockito.times(2)).onShowTranslation(any());
        Mockito.verify(mockCallback, Mockito.times(1)).onHideTranslation(any());
    }


    @Test
    @ApiTest(apis = {"android.view.translation.UiTranslationManagerTest#startUiTranslation",
            "android.view.View#setViewTranslationCallback"})
    public void testUiTranslation_CustomViewTranslationCallback_DetachAndReattach()
            throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();
        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        // Set ViewTranslationCallback
        ViewTranslationCallback mockCallback = Mockito.mock(ViewTranslationCallback.class);
        mTextView.setViewTranslationCallback(mockCallback);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        mActivityScenario.onActivity(activity -> {
            // Toggling the visibility will cause detach and reattach. This test ensures the
            // custom callback is not cleared when this happens.
            mTextView.setVisibility(View.INVISIBLE);
            mTextView.setVisibility(View.VISIBLE);
        });
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        ArgumentCaptor<View> viewArgumentCaptor = ArgumentCaptor.forClass(View.class);
        Mockito.verify(mockCallback, Mockito.times(1)).onShowTranslation(
                viewArgumentCaptor.capture());
        TextView capturedView = (TextView) viewArgumentCaptor.getValue();
        assertThat(capturedView.getAutofillId()).isEqualTo(mTextView.getAutofillId());
    }

    @Test
    public void testUiTranslation_ViewTranslationCallback_paddingText() throws Throwable {
        try {
            final Pair<List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();
            final List<AutofillId> views = result.first;
            final ContentCaptureContext contentCaptureContext = result.second;

            // Set response
            final CharSequence originalText = mTextView.getText();
            final CharSequence translatedText = "Translated World";
            sTranslationReplier.addResponse(
                    createViewsTranslationResponse(views, translatedText.toString()));

            // Use TextView default ViewTranslationCallback implementation
            startUiTranslation(/* shouldPadContent */ true, views, contentCaptureContext);

            CharSequence currentText = mTextView.getText();
            assertThat(currentText.length()).isNotEqualTo(originalText.length());
            assertThat(currentText.length()).isEqualTo(translatedText.length());

            finishUiTranslation(contentCaptureContext);

            // Set Customized ViewTranslationCallback
            ViewTranslationCallback mockCallback = Mockito.mock(ViewTranslationCallback.class);
            mTextView.setViewTranslationCallback(mockCallback);

            startUiTranslation(/* shouldPadContent */ true, views, contentCaptureContext);

            assertThat(mTextView.getText().length()).isEqualTo(originalText.length());
        } catch (Throwable t) {
            Helper.takeScreenshotAndSave(sContext, TestNameUtils.getCurrentTestName(),
                    Helper.LOCAL_TEST_FILES_DIR);
            throw t;
        }
    }

    @Test
    public void testUiTranslation_hasContentDescription() throws Throwable {
        try {
            final Pair<List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();
            final List<AutofillId> views = result.first;
            final ContentCaptureContext contentCaptureContext = result.second;

            // Set response
            final CharSequence translatedText = "Translated World";
            final CharSequence originalDescription = "Hello Description";
            mActivityScenario.onActivity(activity -> {
                mTextView.setContentDescription(originalDescription);
            });
            sTranslationReplier.addResponse(
                    createViewsTranslationResponse(views, translatedText.toString()));

            // Use TextView default ViewTranslationCallback implementation
            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            assertThat(mTextView.getContentDescription().toString())
                    .isEqualTo(translatedText.toString());

            // Check request to make sure the content description key doesn't be changed
            final TranslationRequest request = sTranslationReplier.getNextTranslationRequest();
            final List<ViewTranslationRequest> requests = request.getViewTranslationRequests();
            final ViewTranslationRequest viewRequest = requests.get(0);
            assertThat(viewRequest.getAutofillId()).isEqualTo(views.get(0));
            assertThat(viewRequest.getKeys().size()).isEqualTo(2);
            assertThat(viewRequest.getKeys()).containsExactly(ID_CONTENT_DESCRIPTION,
                    ViewTranslationRequest.ID_TEXT);
            assertThat(viewRequest.getValue(ID_CONTENT_DESCRIPTION).getText())
                    .isEqualTo(originalDescription);

            pauseUiTranslation(contentCaptureContext);

            assertThat(mTextView.getContentDescription().toString())
                    .isEqualTo(originalDescription.toString());

            resumeUiTranslation(contentCaptureContext);

            assertThat(mTextView.getContentDescription().toString())
                    .isEqualTo(translatedText.toString());

            finishUiTranslation(contentCaptureContext);

            assertThat(mTextView.getContentDescription().toString())
                    .isEqualTo(originalDescription.toString());
        } catch (Throwable t) {
            Helper.takeScreenshotAndSave(sContext, TestNameUtils.getCurrentTestName(),
                    Helper.LOCAL_TEST_FILES_DIR);
            throw t;
        }
    }

    @Test
    public void testIMEUiTranslationStateCallback() throws Throwable {
        try (ImeSession imeSession = new ImeSession(
                new ComponentName(CtsTestIme.IME_SERVICE_PACKAGE, CtsTestIme.class.getName()))) {

            final Pair<List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();
            final List<AutofillId> views = result.first;
            final ContentCaptureContext contentCaptureContext = result.second;
            sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

            // Send broadcast to request IME to register callback
            BlockingBroadcastReceiver registerResultReceiver =
                    sendCommandToIme(ACTION_REGISTER_UI_TRANSLATION_CALLBACK, false);
            // Get result
            registerResultReceiver.awaitForBroadcast();
            registerResultReceiver.unregisterQuietly();

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            // Send broadcast to request IME to check the onStarted() result
            BlockingBroadcastReceiver onStartResultReceiver = sendCommandToIme(
                    ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START, true);
            // Get result to check the onStarted() was called
            Intent onStartIntent = onStartResultReceiver.awaitForBroadcast();
            ULocale receivedSource =
                    onStartIntent.getSerializableExtra(EXTRA_SOURCE_LOCALE, ULocale.class);
            ULocale receivedTarget =
                    onStartIntent.getSerializableExtra(EXTRA_TARGET_LOCALE, ULocale.class);
            int startedCallCount = onStartIntent.getIntExtra(EXTRA_CALL_COUNT, -999);
            String startedPackageName = onStartIntent.getStringExtra(EXTRA_PACKAGE_NAME);
            assertThat(receivedSource).isEqualTo(ULocale.ENGLISH);
            assertThat(receivedTarget).isEqualTo(ULocale.FRENCH);
            assertThat(startedCallCount).isEqualTo(1);
            assertThat(startedPackageName).isEqualTo(CTS_TESTS_PACKAGE);
            onStartResultReceiver.unregisterQuietly();

            pauseUiTranslation(contentCaptureContext);

            // Send broadcast to request IME to check the onPaused() result
            BlockingBroadcastReceiver onPausedResultReceiver = sendCommandToIme(
                    ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_PAUSE, true);
            // Get result to check the onPaused() was called
            Intent onPausedIntent = onPausedResultReceiver.awaitForBroadcast();
            int pausedCallCount = onPausedIntent.getIntExtra(EXTRA_CALL_COUNT, -999);
            String pausedPackageName = onPausedIntent.getStringExtra(EXTRA_PACKAGE_NAME);
            assertThat(pausedCallCount).isEqualTo(1);
            assertThat(pausedPackageName).isEqualTo(CTS_TESTS_PACKAGE);
            onPausedResultReceiver.unregisterQuietly();

            resumeUiTranslation(contentCaptureContext);

            // Send broadcast to request IME to check the onResumed result
            BlockingBroadcastReceiver onResumedResultReceiver = sendCommandToIme(
                    ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_RESUME, true);
            // Get result to check the onResumed was called
            Intent onResumedIntent = onResumedResultReceiver.awaitForBroadcast();
            int resumedCallCount = onResumedIntent.getIntExtra(EXTRA_CALL_COUNT, -999);
            String resumedPackageName = onResumedIntent.getStringExtra(EXTRA_PACKAGE_NAME);
            assertThat(resumedCallCount).isEqualTo(1);
            assertThat(resumedPackageName).isEqualTo(CTS_TESTS_PACKAGE);
            onResumedResultReceiver.unregisterQuietly();

            // Send broadcast to request IME to unregister callback
            BlockingBroadcastReceiver unRegisterResultReceiver
                    = sendCommandToIme(ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK, false);
            unRegisterResultReceiver.awaitForBroadcast();
            unRegisterResultReceiver.unregisterQuietly();

            finishUiTranslation(contentCaptureContext);

            BlockingBroadcastReceiver onFinishResultReceiver =
                    sendCommandToIme(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH, true);
            // Get result to check onFinish() isn't called.
            Intent onFinishIntent = onFinishResultReceiver.awaitForBroadcast();
            int finishedCallCount = onFinishIntent.getIntExtra(EXTRA_CALL_COUNT, -999);
            String finishedPackageName = onFinishIntent.getStringExtra(EXTRA_PACKAGE_NAME);
            assertThat(finishedCallCount).isEqualTo(0);
            assertThat(finishedPackageName).isNull();
            onFinishResultReceiver.unregisterQuietly();

            // TODO(b/191417938): add tests for the Activity destroyed for IME package callback
        }
    }

    @Test
    public void testNonIMEUiTranslationStateCallback() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        try {
            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            // TODO(b/191417938): add tests for the Activity isn't the same package of the
            //  registered callback app
            Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            pauseUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

            resumeUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            finishUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));

            // Make sure onFinished will not be called twice.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testActivityDestroyedWithoutFinishTranslation() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        try {
            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                    .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            pauseUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

            resumeUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                    .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            // Make sure onFinished will still be called if Activity is destroyed, even without a
            // finishTranslation call.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackRegisteredAfterTranslationStarted() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);
        SystemClock.sleep(UI_WAIT_TIMEOUT);

        try {
            // Callback should receive onStarted.
            Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            pauseUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

            resumeUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            finishUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));

            // Make sure onFinished will not be called twice.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackRegisteredAfterTranslationStartedAndPaused() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        pauseUiTranslation(contentCaptureContext);

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);
        SystemClock.sleep(UI_WAIT_TIMEOUT);

        try {
            // Callback should receive onStarted and onPaused events.
            Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

            resumeUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            finishUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));

            // Make sure onFinished will not be called twice.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackRegisteredAfterTranslationStartedPausedAndResumed() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        pauseUiTranslation(contentCaptureContext);

        resumeUiTranslation(contentCaptureContext);

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);
        SystemClock.sleep(UI_WAIT_TIMEOUT);

        try {
            // Callback should receive onStarted event but NOT an onPaused event, because
            // translation was already resumed prior to registration.
            Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            Mockito.verify(mockCallback, Mockito.never()).onPaused(any(String.class));

            Mockito.verify(mockCallback, Mockito.never())
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            finishUiTranslation(contentCaptureContext);

            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));

            // Make sure onFinished will not be called twice.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.times(1)).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackRegisteredAfterTranslationFinished() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        pauseUiTranslation(contentCaptureContext);

        resumeUiTranslation(contentCaptureContext);

        finishUiTranslation(contentCaptureContext);

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);
        SystemClock.sleep(UI_WAIT_TIMEOUT);

        try {
            // Callback should receive no events.
            Mockito.verify(mockCallback, Mockito.never())
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

            Mockito.verify(mockCallback, Mockito.never()).onPaused(any(String.class));

            Mockito.verify(mockCallback, Mockito.never())
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));

            Mockito.verify(mockCallback, Mockito.never()).onFinished(any(String.class));

            // Make sure onFinished will not be called at all.
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verify(mockCallback, Mockito.never()).onFinished(any(String.class));
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackRegisteredAfterActivityDestroyedWithoutFinishTranslation()
            throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        pauseUiTranslation(contentCaptureContext);

        resumeUiTranslation(contentCaptureContext);

        // Note: No finishTranslation call; Activity is destroyed.
        mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
        mActivityScenario = null;
        SystemClock.sleep(UI_WAIT_TIMEOUT);

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        try {
            // Callback should receive no events.
            SystemClock.sleep(UI_WAIT_TIMEOUT);
            Mockito.verifyZeroInteractions(mockCallback);
        } finally {
            manager.unregisterUiTranslationStateCallback(mockCallback);
        }
    }

    @Test
    public void testCallbackCalledOnceAfterDuplicateCalls() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        // Call startTranslation() multiple times; callback should only be called once.
        // Note: The locales don't change with each call.
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

        // Call pauseTranslation() multiple times; callback should only be called once.
        pauseUiTranslation(contentCaptureContext);
        pauseUiTranslation(contentCaptureContext);
        pauseUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

        // Call resumeUiTranslation() multiple times; callback should only be called once.
        resumeUiTranslation(contentCaptureContext);
        resumeUiTranslation(contentCaptureContext);
        resumeUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));
    }

    @Test
    public void testCallbackCalledForStartTranslationWithDifferentLocales() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext,
                ULocale.ENGLISH, ULocale.FRENCH);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext,
                ULocale.ENGLISH, ULocale.GERMAN);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext,
                ULocale.ITALIAN, ULocale.GERMAN);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext,
                ULocale.JAPANESE, ULocale.KOREAN);

        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(eq(ULocale.ENGLISH), eq(ULocale.FRENCH), any(String.class));
        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(eq(ULocale.ENGLISH), eq(ULocale.GERMAN), any(String.class));
        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(eq(ULocale.ITALIAN), eq(ULocale.GERMAN), any(String.class));
        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(eq(ULocale.JAPANESE), eq(ULocale.KOREAN), any(String.class));

        // Calling startTranslation() after pauseTranslation() should invoke the callback even if
        // the locales are the same as it was before.
        pauseUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext,
                ULocale.JAPANESE, ULocale.KOREAN);

        Mockito.verify(mockCallback, Mockito.times(2))
                .onStarted(eq(ULocale.JAPANESE), eq(ULocale.KOREAN), any(String.class));
    }

    @Test
    public void testCallbackCalledOnStartTranslationAfterPause() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

        pauseUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onPaused(any(String.class));

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        // Start after pause invokes onResumed(), NOT onStarted().
        Mockito.verify(mockCallback, Mockito.times(1))
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));
    }

    @Test
    public void testCallbackNotCalledOnResumeTranslationAfterStart() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1))
                .onStarted(any(ULocale.class), any(ULocale.class), any(String.class));

        resumeUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.never())
                .onResumed(any(ULocale.class), any(ULocale.class), any(String.class));
    }

    @Test
    public void testCallbackNotCalledOnPauseOrResumeTranslationWithoutStart() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        UiTranslationStateCallback mockCallback = Mockito.mock(UiTranslationStateCallback.class);
        manager.registerUiTranslationStateCallback(executor, mockCallback);

        pauseUiTranslation(contentCaptureContext);
        resumeUiTranslation(contentCaptureContext);

        Mockito.verifyZeroInteractions(mockCallback);
    }

    @Test
    public void testVirtualViewUiTranslation() throws Throwable {
        // Enable CTS ContentCaptureService
        CtsContentCaptureService contentcaptureService = enableContentCaptureService();

        // Start Activity and get needed information
        final List<AutofillId> views = startVirtualContainerViewActivityAndGetViewsForTranslation();
        ViewTranslationCallback mockCallback = Mockito.mock(ViewTranslationCallback.class);
        mVirtualContainerView.setViewTranslationCallback(mockCallback);

        // Wait session created and get the ContentCaptureContext from ContentCaptureService
        final ContentCaptureContext contentCaptureContext =
                getContentCaptureContextFromContentCaptureService(contentcaptureService);

        // enable CTS TranslationService
        mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

        final String translatedText = "success";
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        // Set response
        final TranslationResponse expectedResponse =
                createViewsTranslationResponse(views, translatedText);
        sTranslationReplier.addResponse(expectedResponse);

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        // Check request
        final TranslationRequest request = sTranslationReplier.getNextTranslationRequest();
        final List<ViewTranslationRequest> requests = request.getViewTranslationRequests();
        assertThat(requests.size()).isEqualTo(views.size());
        // 1st virtual child in container
        final ViewTranslationRequest viewRequest1 = requests.get(0);
        assertThat(viewRequest1.getAutofillId()).isEqualTo(views.get(0));
        assertThat(viewRequest1.getKeys()).containsExactly(ViewTranslationRequest.ID_TEXT);
        assertThat(viewRequest1.getValue(ViewTranslationRequest.ID_TEXT).getText().toString())
                .isEqualTo("Hello 0");
        // 2nd virtual child in container
        final ViewTranslationRequest viewRequest2 = requests.get(1);
        assertThat(viewRequest2.getAutofillId()).isEqualTo(views.get(1));
        assertThat(viewRequest2.getKeys()).containsExactly(ViewTranslationRequest.ID_TEXT);
        assertThat(viewRequest2.getValue(ViewTranslationRequest.ID_TEXT).getText().toString())
                .isEqualTo("Hello 1");

        // Check responses
        final LongSparseArray<ViewTranslationResponse> responses
                = mVirtualContainerView.getViewTranslationResponseForCustomView();
        assertThat(responses).isNotNull();
        assertThat(responses.size()).isEqualTo(2);
        assertThat(responses.valueAt(0))
                .isEqualTo(expectedResponse.getViewTranslationResponses().valueAt(0));
        assertThat(responses.valueAt(1))
                .isEqualTo(expectedResponse.getViewTranslationResponses().valueAt(1));

        ArgumentCaptor<View> viewArgumentCaptor = ArgumentCaptor.forClass(View.class);
        Mockito.verify(mockCallback, Mockito.times(1))
                .onShowTranslation(viewArgumentCaptor.capture());
        VirtualContainerView capturedView = (VirtualContainerView) viewArgumentCaptor.getValue();
        assertThat(capturedView.getAutofillId()).isEqualTo(mVirtualContainerView.getAutofillId());

        pauseUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onHideTranslation(any(View.class));

        finishUiTranslation(contentCaptureContext);

        Mockito.verify(mockCallback, Mockito.times(1)).onClearTranslation(any(View.class));
    }

    @Test
    public void testUiTranslation_translationResponseNotSetForCustomTextView() throws Throwable {
        try {
            // Enable CTS ContentCaptureService
            CtsContentCaptureService contentcaptureService = enableContentCaptureService();
            // Start Activity and get needed information
            final List<AutofillId> views = startCustomTextViewActivityAndGetViewsForTranslation();

            // Wait session created and get the ConttCaptureContext from ContentCaptureService
            final ContentCaptureContext contentCaptureContext =
                    getContentCaptureContextFromContentCaptureService(contentcaptureService);

            // enable CTS TranslationService
            mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
            Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

            // Set response
            final TranslationResponse expectedResponse =
                    createViewsTranslationResponse(views, "success");
            sTranslationReplier.addResponse(expectedResponse);

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            // Verify result. Translation response doesn't set, it should show original text
            assertScreenText(mResponseNotSetTextView, "Hello World 1");
        } catch (Throwable t) {
            Helper.takeScreenshotAndSave(sContext, TestNameUtils.getCurrentTestName(),
                    Helper.LOCAL_TEST_FILES_DIR);
            throw t;
        }
    }

    @Test
    public void testUiTranslation_customTextView() throws Throwable {
        try {
            // Enable CTS ContentCaptureService
            CtsContentCaptureService contentcaptureService = enableContentCaptureService();
            // Start Activity and get needed information
            final List<AutofillId> views = startCustomTextViewActivityAndGetViewsForTranslation();

            // Wait session created and get the ConttCaptureContext from ContentCaptureService
            final ContentCaptureContext contentCaptureContext =
                    getContentCaptureContextFromContentCaptureService(contentcaptureService);

            // enable CTS TranslationService
            mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
            Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

            final String translatedText = "success";
            // Set response
            final TranslationResponse expectedResponse =
                    createViewsTranslationResponse(views, translatedText);
            sTranslationReplier.addResponse(expectedResponse);

            startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

            // Verify result.
            assertThat(mCustomTextView.isMyTagTranslationSupported()).isTrue();
            assertScreenText(mCustomTextView, translatedText);

            finishUiTranslation(contentCaptureContext);

            assertScreenText(mCustomTextView, "Hello World 2");
        } catch (Throwable t) {
            Helper.takeScreenshotAndSave(sContext, TestNameUtils.getCurrentTestName(),
                    Helper.LOCAL_TEST_FILES_DIR);
            throw t;
        }
    }

    @Test
    public void testUiTranslation_selectableText() throws Throwable {
        final Pair<List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final CharSequence originalText = mTextView.getText();
        final List<AutofillId> views = result.first;
        final ContentCaptureContext contentCaptureContext = result.second;

        final String translatedText = "success";
        // Set responses
        final TranslationResponse response =
                createViewsTranslationResponse(views, translatedText);
        sTranslationReplier.addResponse(response);

        mActivityScenario.onActivity(activity -> {
            mTextView.setTextIsSelectable(true);

            // We want to check that these other flags can be restored after translation is
            // finished.
            mTextView.setClickable(false);
            mTextView.setLongClickable(false);
            mTextView.setFocusable(false);
            mTextView.setFocusableInTouchMode(false);
        });

        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        // TextView is not selectable while translated.
        assertScreenText(mTextView, translatedText);
        assertThat(mTextView.isTextSelectable()).isFalse();

        finishUiTranslation(contentCaptureContext);

        // Selectable and other flags are restored after translation is finished.
        assertScreenText(mTextView, originalText.toString());
        assertThat(mTextView.isTextSelectable()).isTrue();
        assertThat(mTextView.isClickable()).isFalse();
        assertThat(mTextView.isFocusable()).isFalse();
        assertThat(mTextView.isFocusableInTouchMode()).isFalse();
        assertThat(mTextView.isLongClickable()).isFalse();

        mActivityScenario.onActivity(activity -> {
            // We also want to check we can restore flags with true values.
            mTextView.setClickable(true);
            mTextView.setLongClickable(true);
            mTextView.setFocusable(true);
            mTextView.setFocusableInTouchMode(true);
        });

        sTranslationReplier.addResponse(response);
        startUiTranslation(/* shouldPadContent */ false, views, contentCaptureContext);

        // TextView is not selectable while translated.
        assertScreenText(mTextView, translatedText);
        assertThat(mTextView.isTextSelectable()).isFalse();

        finishUiTranslation(contentCaptureContext);

        // Selectable and other flags are restored after translation is finished.
        assertScreenText(mTextView, originalText.toString());
        assertThat(mTextView.isTextSelectable()).isTrue();
        assertThat(mTextView.isClickable()).isTrue();
        assertThat(mTextView.isFocusable()).isTrue();
        assertThat(mTextView.isFocusableInTouchMode()).isTrue();
        assertThat(mTextView.isLongClickable()).isTrue();
    }

    private void assertScreenText(TextView textView, String expected) {
        String screenText = textView.getLayout().getText().toString();
        assertThat(screenText).isEqualTo(expected);
    }

    private void startUiTranslation(boolean shouldPadContent, List<AutofillId> views,
            ContentCaptureContext contentCaptureContext) {
        startUiTranslation(shouldPadContent, views, contentCaptureContext, ULocale.ENGLISH,
                ULocale.FRENCH);
    }

    private void startUiTranslation(boolean shouldPadContent, List<AutofillId> views,
            ContentCaptureContext contentCaptureContext, ULocale sourceLocale,
            ULocale targetLocale) {
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        runWithShellPermissionIdentity(() -> {
            // Call startTranslation API
            manager.startTranslation(
                    new TranslationSpec(sourceLocale, TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(targetLocale, TranslationSpec.DATA_FORMAT_TEXT),
                    views, contentCaptureContext.getActivityId(),
                    shouldPadContent ? new UiTranslationSpec.Builder().setShouldPadContentForCompat(
                            true).build() : new UiTranslationSpec.Builder().build());
            SystemClock.sleep(UI_WAIT_TIMEOUT);
        });
    }

    private void pauseUiTranslation(ContentCaptureContext contentCaptureContext) {
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        runWithShellPermissionIdentity(() -> {
            // Call pauseTranslation API
            manager.pauseTranslation(contentCaptureContext.getActivityId());
            SystemClock.sleep(UI_WAIT_TIMEOUT);
        });
    }

    private void resumeUiTranslation(ContentCaptureContext contentCaptureContext) {
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        // Call resume Translation API
        runWithShellPermissionIdentity(() -> {
            manager.resumeTranslation(contentCaptureContext.getActivityId());
            SystemClock.sleep(UI_WAIT_TIMEOUT);
        });
    }

    private void finishUiTranslation(ContentCaptureContext contentCaptureContext) {
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        runWithShellPermissionIdentity(() -> {
            // Call finishTranslation API
            manager.finishTranslation(contentCaptureContext.getActivityId());
            SystemClock.sleep(UI_WAIT_TIMEOUT);
        });
    }

    private List<AutofillId> startCustomTextViewActivityAndGetViewsForTranslation() {
        // Start CustomTextViewActivity and get needed information
        Intent intent = new Intent(sContext, CustomTextViewActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicReference<List<AutofillId>> viewAutofillIdsRef = new AtomicReference<>();

        mCustomTextViewActivityScenario = ActivityScenario.launch(intent);
        mCustomTextViewActivityScenario.onActivity(activity -> {
            mResponseNotSetTextView = activity.getResponseNotSetText();
            mCustomTextView = activity.getCustomText();
            // Get the views that need to be translated.
            viewAutofillIdsRef.set(activity.getViewsForTranslation());
        });
        return viewAutofillIdsRef.get();
    }

    private List<AutofillId> startVirtualContainerViewActivityAndGetViewsForTranslation() {
        // Start VirtualContainerViewActivity and get needed information
        Intent intent = new Intent(sContext, VirtualContainerViewActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicReference<List<AutofillId>> viewAutofillIdsRef = new AtomicReference<>();

        mVirtualContainerViewActivityScenario = ActivityScenario.launch(intent);
        mVirtualContainerViewActivityScenario.onActivity(activity -> {
            mVirtualContainerView = activity.getVirtualContainerView();
            // Get the views that need to be translated.
            viewAutofillIdsRef.set(activity.getViewsForTranslation());
        });
        return viewAutofillIdsRef.get();
    }

    private BlockingBroadcastReceiver sendCommandToIme(String action, boolean mutable) {
        final String actionImeServiceCommandDone = action + "_" + SystemClock.uptimeMillis();
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(sContext,
                actionImeServiceCommandDone);
        receiver.register();
        final Intent commandIntent = new Intent(action);
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        sContext,
                        0,
                        new Intent(actionImeServiceCommandDone),
                        mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        commandIntent.putExtra(EXTRA_FINISH_COMMAND, pendingIntent);
        sContext.sendBroadcast(commandIntent);
        return receiver;
    }

    private CtsContentCaptureService enableContentCaptureService() throws Exception {
        mContentCaptureServiceWatcher = CtsContentCaptureService.setServiceWatcher();
        Helper.setTemporaryContentCaptureService(CtsContentCaptureService.SERVICE_NAME);
        mContentCaptureServiceWatcher.setAllowSelf();
        return mContentCaptureServiceWatcher.waitOnConnected();
    }

    private ContentCaptureContext getContentCaptureContextFromContentCaptureService(
            CtsContentCaptureService service) {
        service.awaitSessionCreated(CtsContentCaptureService.GENERIC_TIMEOUT_MS);
        final ContentCaptureContext contentCaptureContext = service.getContentCaptureContext();
        Log.d(TAG, "contentCaptureContext = " + contentCaptureContext);

        assertThat(contentCaptureContext).isNotNull();
        assertThat(contentCaptureContext.getActivityId()).isNotNull();

        return contentCaptureContext;
    }

    private TranslationResponse createViewsTranslationResponse(List<AutofillId> viewAutofillIds,
            String translatedText) {
        final TranslationResponse.Builder responseBuilder =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        for (int i = 0; i < viewAutofillIds.size(); i++) {
            ViewTranslationResponse.Builder responseDataBuilder =
                    new ViewTranslationResponse.Builder(viewAutofillIds.get(i))
                            .setValue(ViewTranslationRequest.ID_TEXT,
                                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                            .setText(translatedText).build())
                            .setValue(Helper.CUSTOM_TRANSLATION_ID_MY_TAG,
                                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                            .setText(translatedText).build())
                            .setValue(ID_CONTENT_DESCRIPTION,
                                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                            .setText(translatedText).build());
            responseBuilder.setViewTranslationResponse(i, responseDataBuilder.build());
        }
        return responseBuilder.build();
    }

    private Pair<List<AutofillId>, ContentCaptureContext>
            enableServicesAndStartActivityForTranslation() throws Exception {
        // Enable CTS ContentCaptureService
        CtsContentCaptureService contentcaptureService = enableContentCaptureService();

        // Start Activity and get needed information
        Intent intent = new Intent(sContext, SimpleActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicReference<CharSequence> originalTextRef = new AtomicReference<>();
        AtomicReference<List<AutofillId>> viewAutofillIdsRef = new AtomicReference<>();

        mActivityScenario = ActivityScenario.launch(intent);
        mActivityScenario.onActivity(activity -> {
            mSimpleActivity = activity;
            mTextView = activity.getHelloText();
            originalTextRef.set(activity.getHelloText().getText());
            viewAutofillIdsRef.set(activity.getViewsForTranslation());
        });
        CharSequence originalText = originalTextRef.get();
        // Get the views that need to be translated.
        List<AutofillId> views = viewAutofillIdsRef.get();

        // Wait session created and get the ContentCaptureContext from ContentCaptureService
        ContentCaptureContext contentCaptureContext =
                getContentCaptureContextFromContentCaptureService(contentcaptureService);

        // enable CTS TranslationService
        mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

        // TODO(b/184617863): use separate methods not use Pair here.
        return new Pair(views, contentCaptureContext);
    }

    private static class ImeSession implements AutoCloseable {

        private static final long TIMEOUT = 2000;
        private final ComponentName mImeName;

        ImeSession(ComponentName ime) throws Exception {
            mImeName = ime;
            runShellCommand("ime reset");
            // TODO(b/184617863): get IME component from InputMethodManager#getInputMethodList
            runShellCommand("ime enable " + ime.flattenToShortString());
            runShellCommand("ime set " + ime.flattenToShortString());
            PollingCheck.check("Make sure that MockIME becomes available", TIMEOUT,
                    () -> ime.equals(getCurrentInputMethodId()));
        }

        @Override
        public void close() throws Exception {
            runShellCommand("ime reset");
            PollingCheck.check("Make sure that MockIME becomes unavailable", TIMEOUT, () ->
                    sContext.getSystemService(InputMethodManager.class)
                            .getEnabledInputMethodList()
                            .stream()
                            .noneMatch(info -> mImeName.equals(info.getComponent())));
        }

        private ComponentName getCurrentInputMethodId() {
            return ComponentName.unflattenFromString(
                    Settings.Secure.getString(sContext.getContentResolver(),
                            Settings.Secure.DEFAULT_INPUT_METHOD));
        }
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.CancellationSignal;
import android.service.textclassifier.TextClassifierService;
import android.view.textclassifier.ConversationAction;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;
import android.view.textclassifier.TextLinks;
import android.view.textclassifier.TextLinks.TextLink;
import android.view.textclassifier.TextSelection;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.TextClassifierApiUsageReported;
import com.android.os.AtomsProto.TextClassifierApiUsageReported.ApiType;
import com.android.os.AtomsProto.TextClassifierApiUsageReported.ResultType;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.statsd.StatsdTestUtils;
import com.android.textclassifier.common.statsd.TextClassifierApiUsageLogger;
import com.android.textclassifier.downloader.ModelDownloadManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultTextClassifierServiceTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  /** A statsd config ID, which is arbitrary. */
  private static final long CONFIG_ID = 689777;

  private static final long SHORT_TIMEOUT_MS = 1000;

  private static final String SESSION_ID = "abcdef";

  private TestInjector testInjector;
  private DefaultTextClassifierService defaultTextClassifierService;
  @Mock private TextClassifierService.Callback<TextClassification> textClassificationCallback;
  @Mock private TextClassifierService.Callback<TextSelection> textSelectionCallback;
  @Mock private TextClassifierService.Callback<TextLinks> textLinksCallback;
  @Mock private TextClassifierService.Callback<ConversationActions> conversationActionsCallback;
  @Mock private TextClassifierService.Callback<TextLanguage> textLanguageCallback;
  @Mock private ModelFileManager testModelFileManager;

  @Before
  public void setup() throws IOException {
    testInjector =
        new TestInjector(ApplicationProvider.getApplicationContext(), testModelFileManager);
    defaultTextClassifierService = new DefaultTextClassifierService(testInjector);
    defaultTextClassifierService.onCreate();

    when(testModelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(TestDataUtils.getTestAnnotatorModelFileWrapped());
    when(testModelFileManager.findBestModelFile(eq(ModelType.LANG_ID), any(), any()))
        .thenReturn(TestDataUtils.getLangIdModelFileWrapped());
    when(testModelFileManager.findBestModelFile(eq(ModelType.ACTIONS_SUGGESTIONS), any(), any()))
        .thenReturn(TestDataUtils.getTestActionsModelFileWrapped());
  }

  @Before
  public void setupStatsdTestUtils() throws Exception {
    StatsdTestUtils.cleanup(CONFIG_ID);

    StatsdConfig.Builder builder =
        StatsdConfig.newBuilder()
            .setId(CONFIG_ID)
            .addAllowedLogSource(ApplicationProvider.getApplicationContext().getPackageName());
    StatsdTestUtils.addAtomMatcher(builder, Atom.TEXT_CLASSIFIER_API_USAGE_REPORTED_FIELD_NUMBER);
    StatsdTestUtils.pushConfig(builder.build());
  }

  @After
  public void tearDown() throws Exception {
    StatsdTestUtils.cleanup(CONFIG_ID);
  }

  @Test
  public void classifyText_success() throws Exception {
    String text = "www.android.com";
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, 0, text.length()).build();

    defaultTextClassifierService.onClassifyText(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        textClassificationCallback);

    ArgumentCaptor<TextClassification> captor = ArgumentCaptor.forClass(TextClassification.class);
    verify(textClassificationCallback).onSuccess(captor.capture());
    assertThat(captor.getValue().getEntityCount()).isGreaterThan(0);
    assertThat(captor.getValue().getEntity(0)).isEqualTo(TextClassifier.TYPE_URL);
    verifyApiUsageLog(ApiType.CLASSIFY_TEXT, ResultType.SUCCESS);
  }

  @Test
  public void suggestSelection_success() throws Exception {
    String text = "Visit http://www.android.com for more information";
    String selected = "http";
    String suggested = "http://www.android.com";
    int start = text.indexOf(selected);
    int end = start + suggested.length();
    TextSelection.Request request = new TextSelection.Request.Builder(text, start, end).build();

    defaultTextClassifierService.onSuggestSelection(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        textSelectionCallback);

    ArgumentCaptor<TextSelection> captor = ArgumentCaptor.forClass(TextSelection.class);
    verify(textSelectionCallback).onSuccess(captor.capture());
    assertThat(captor.getValue().getEntityCount()).isGreaterThan(0);
    assertThat(captor.getValue().getEntity(0)).isEqualTo(TextClassifier.TYPE_URL);
    verifyApiUsageLog(ApiType.SUGGEST_SELECTION, ResultType.SUCCESS);
  }

  @Test
  public void generateLinks_success() throws Exception {
    String text = "Visit http://www.android.com for more information";
    TextLinks.Request request = new TextLinks.Request.Builder(text).build();

    defaultTextClassifierService.onGenerateLinks(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        textLinksCallback);

    ArgumentCaptor<TextLinks> captor = ArgumentCaptor.forClass(TextLinks.class);
    verify(textLinksCallback).onSuccess(captor.capture());
    assertThat(captor.getValue().getLinks()).hasSize(1);
    TextLink textLink = captor.getValue().getLinks().iterator().next();
    assertThat(textLink.getEntityCount()).isGreaterThan(0);
    assertThat(textLink.getEntity(0)).isEqualTo(TextClassifier.TYPE_URL);
    verifyApiUsageLog(ApiType.GENERATE_LINKS, ResultType.SUCCESS);
  }

  @Test
  public void detectLanguage_success() throws Exception {
    String text = "ピカチュウ";
    TextLanguage.Request request = new TextLanguage.Request.Builder(text).build();

    defaultTextClassifierService.onDetectLanguage(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        textLanguageCallback);

    ArgumentCaptor<TextLanguage> captor = ArgumentCaptor.forClass(TextLanguage.class);
    verify(textLanguageCallback).onSuccess(captor.capture());
    assertThat(captor.getValue().getLocaleHypothesisCount()).isGreaterThan(0);
    assertThat(captor.getValue().getLocale(0).toLanguageTag()).isEqualTo("ja");
    verifyApiUsageLog(ApiType.DETECT_LANGUAGES, ResultType.SUCCESS);
  }

  @Test
  public void suggestConversationActions_success() throws Exception {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("Checkout www.android.com")
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(ImmutableList.of(message)).build();

    defaultTextClassifierService.onSuggestConversationActions(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        conversationActionsCallback);

    ArgumentCaptor<ConversationActions> captor = ArgumentCaptor.forClass(ConversationActions.class);
    verify(conversationActionsCallback).onSuccess(captor.capture());
    List<ConversationAction> conversationActions = captor.getValue().getConversationActions();
    assertThat(conversationActions.size()).isGreaterThan(0);
    assertThat(conversationActions.get(0).getType()).isEqualTo(ConversationAction.TYPE_OPEN_URL);
    verifyApiUsageLog(ApiType.SUGGEST_CONVERSATION_ACTIONS, ResultType.SUCCESS);
  }

  @Test
  public void missingModelFile_onFailureShouldBeCalled() throws Exception {
    when(testModelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(null);
    defaultTextClassifierService.onCreate();

    TextClassification.Request request = new TextClassification.Request.Builder("hi", 0, 2).build();
    defaultTextClassifierService.onClassifyText(
        TestingUtils.createTextClassificationSessionId(SESSION_ID),
        request,
        new CancellationSignal(),
        textClassificationCallback);

    verify(textClassificationCallback).onFailure(Mockito.anyString());
    verifyApiUsageLog(ApiType.CLASSIFY_TEXT, ResultType.FAIL);
  }

  private static void verifyApiUsageLog(
      AtomsProto.TextClassifierApiUsageReported.ApiType expectedApiType,
      AtomsProto.TextClassifierApiUsageReported.ResultType expectedResultApiType)
      throws Exception {
    ImmutableList<Atom> loggedAtoms = StatsdTestUtils.getLoggedAtoms(CONFIG_ID, SHORT_TIMEOUT_MS);
    ImmutableList<TextClassifierApiUsageReported> loggedEvents =
        ImmutableList.copyOf(
            loggedAtoms.stream()
                .map(Atom::getTextClassifierApiUsageReported)
                .collect(Collectors.toList()));
    assertThat(loggedEvents).hasSize(1);
    TextClassifierApiUsageReported loggedEvent = loggedEvents.get(0);
    assertThat(loggedEvent.getLatencyMillis()).isGreaterThan(0L);
    assertThat(loggedEvent.getApiType()).isEqualTo(expectedApiType);
    assertThat(loggedEvent.getResultType()).isEqualTo(expectedResultApiType);
    assertThat(loggedEvent.getSessionId()).isEqualTo(SESSION_ID);
  }

  private static final class TestInjector implements DefaultTextClassifierService.Injector {
    private final Context context;
    private ModelFileManager modelFileManager;

    private TestInjector(Context context, ModelFileManager modelFileManager) {
      this.context = Preconditions.checkNotNull(context);
      this.modelFileManager = Preconditions.checkNotNull(modelFileManager);
    }

    @Override
    public Context getContext() {
      return context;
    }

    @Override
    public ModelFileManager createModelFileManager(
        TextClassifierSettings settings, ModelDownloadManager modelDownloadManager) {
      return modelFileManager;
    }

    @Override
    public TextClassifierSettings createTextClassifierSettings() {
      return new TextClassifierSettings(getContext());
    }

    @Override
    public TextClassifierImpl createTextClassifierImpl(
        TextClassifierSettings settings, ModelFileManager modelFileManager) {
      return new TextClassifierImpl(context, settings, modelFileManager);
    }

    @Override
    public ListeningExecutorService createNormPriorityExecutor() {
      return MoreExecutors.newDirectExecutorService();
    }

    @Override
    public ListeningExecutorService createLowPriorityExecutor() {
      return MoreExecutors.newDirectExecutorService();
    }

    @Override
    public TextClassifierApiUsageLogger createTextClassifierApiUsageLogger(
        TextClassifierSettings settings, Executor executor) {
      return new TextClassifierApiUsageLogger(
          /* sampleRateSupplier= */ () -> 1, MoreExecutors.directExecutor());
    }
  }
}

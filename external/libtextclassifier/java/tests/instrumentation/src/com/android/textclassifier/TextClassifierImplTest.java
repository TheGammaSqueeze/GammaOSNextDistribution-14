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
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.textclassifier.ConversationAction;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;
import android.view.textclassifier.TextLinks;
import android.view.textclassifier.TextSelection;
import androidx.collection.LruCache;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.testing.FakeContextBuilder;
import com.android.textclassifier.testing.TestingDeviceConfig;
import com.google.android.textclassifier.AnnotatorModel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassifierImplTest {

  private static final String TYPE_COPY = "copy";
  private static final LocaleList LOCALES = LocaleList.forLanguageTags("en-US");
  private static final String NO_TYPE = null;

  @Mock private ModelFileManager modelFileManager;

  private Context context;
  private TestingDeviceConfig deviceConfig;
  private TextClassifierSettings settings;
  private LruCache<ModelFile, AnnotatorModel> annotatorModelCache;
  private TextClassifierImpl classifier;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    this.context =
        new FakeContextBuilder()
            .setAllIntentComponent(FakeContextBuilder.DEFAULT_COMPONENT)
            .setAppLabel(FakeContextBuilder.DEFAULT_COMPONENT.getPackageName(), "Test app")
            .build();
    this.deviceConfig = new TestingDeviceConfig();
    this.settings = new TextClassifierSettings(deviceConfig, /* isWear= */ false);
    this.annotatorModelCache = new LruCache<>(2);
    this.classifier =
        new TextClassifierImpl(context, settings, modelFileManager, annotatorModelCache);

    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(TestDataUtils.getTestAnnotatorModelFileWrapped());
    when(modelFileManager.findBestModelFile(eq(ModelType.LANG_ID), any(), any()))
        .thenReturn(TestDataUtils.getLangIdModelFileWrapped());
    when(modelFileManager.findBestModelFile(eq(ModelType.ACTIONS_SUGGESTIONS), any(), any()))
        .thenReturn(TestDataUtils.getTestActionsModelFileWrapped());
  }

  @Test
  public void testSuggestSelection() throws IOException {
    String text = "Contact me at droid@android.com";
    String selected = "droid";
    String suggested = "droid@android.com";
    int startIndex = text.indexOf(selected);
    int endIndex = startIndex + selected.length();
    int smartStartIndex = text.indexOf(suggested);
    int smartEndIndex = smartStartIndex + suggested.length();
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, startIndex, endIndex).build();

    TextSelection selection = classifier.suggestSelection(null, null, request);
    assertThat(
        selection, isTextSelection(smartStartIndex, smartEndIndex, TextClassifier.TYPE_EMAIL));
  }

  @Test
  public void testSuggestSelection_localePreferenceIsPassedToModelFileManager() throws IOException {
    String text = "Contact me at droid@android.com";
    String selected = "droid";
    String suggested = "droid@android.com";
    int startIndex = text.indexOf(selected);
    int endIndex = startIndex + selected.length();
    int smartStartIndex = text.indexOf(suggested);
    int smartEndIndex = smartStartIndex + suggested.length();
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, startIndex, endIndex)
            .setDefaultLocales(LOCALES)
            .build();

    classifier.suggestSelection(null, null, request);
    verify(modelFileManager).findBestModelFile(eq(ModelType.ANNOTATOR), eq(LOCALES), any());
  }

  @Test
  public void testSuggestSelection_url() throws IOException {
    String text = "Visit http://www.android.com for more information";
    String selected = "http";
    String suggested = "http://www.android.com";
    int startIndex = text.indexOf(selected);
    int endIndex = startIndex + selected.length();
    int smartStartIndex = text.indexOf(suggested);
    int smartEndIndex = smartStartIndex + suggested.length();
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, startIndex, endIndex).build();

    TextSelection selection = classifier.suggestSelection(null, null, request);
    assertThat(selection, isTextSelection(smartStartIndex, smartEndIndex, TextClassifier.TYPE_URL));
  }

  @Test
  public void testSmartSelection_withEmoji() throws IOException {
    String text = "\uD83D\uDE02 Hello.";
    String selected = "Hello";
    int startIndex = text.indexOf(selected);
    int endIndex = startIndex + selected.length();
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, startIndex, endIndex).build();

    TextSelection selection = classifier.suggestSelection(null, null, request);
    assertThat(selection, isTextSelection(startIndex, endIndex, NO_TYPE));
  }

  @SdkSuppress(minSdkVersion = 31, codeName = "S")
  @Test
  public void testSuggestSelection_includeTextClassification() throws IOException {
    String text = "Visit http://www.android.com for more information";
    String suggested = "http://www.android.com";
    int startIndex = text.indexOf(suggested);
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, startIndex, /* endIndex= */ startIndex + 1)
            .setIncludeTextClassification(true)
            .build();

    TextSelection selection = classifier.suggestSelection(null, null, request);

    assertThat(
        selection.getTextClassification(),
        isTextClassification(suggested, TextClassifier.TYPE_URL));
    assertThat(selection.getTextClassification(), containsIntentWithAction(Intent.ACTION_VIEW));
  }

  @SdkSuppress(minSdkVersion = 31, codeName = "S")
  @Test
  public void testSuggestSelection_notIncludeTextClassification() throws IOException {
    String text = "Visit http://www.android.com for more information";
    TextSelection.Request request =
        new TextSelection.Request.Builder(text, /* startIndex= */ 0, /* endIndex= */ 4)
            .setIncludeTextClassification(false)
            .build();

    TextSelection selection = classifier.suggestSelection(null, null, request);

    assertThat(selection.getTextClassification()).isNull();
  }

  @Test
  public void testClassifyText() throws IOException {
    String text = "Contact me at droid@android.com";
    String classifiedText = "droid@android.com";
    int startIndex = text.indexOf(classifiedText);
    int endIndex = startIndex + classifiedText.length();
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, startIndex, endIndex).build();

    TextClassification classification =
        classifier.classifyText(/* sessionId= */ null, null, request);
    assertThat(classification, isTextClassification(classifiedText, TextClassifier.TYPE_EMAIL));
  }

  @Test
  public void testClassifyText_url() throws IOException {
    String text = "Visit www.android.com for more information";
    String classifiedText = "www.android.com";
    int startIndex = text.indexOf(classifiedText);
    int endIndex = startIndex + classifiedText.length();
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, startIndex, endIndex).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    assertThat(classification, isTextClassification(classifiedText, TextClassifier.TYPE_URL));
    assertThat(classification, containsIntentWithAction(Intent.ACTION_VIEW));
  }

  @Test
  public void testClassifyText_address() throws IOException {
    String text = "Brandschenkestrasse 110, Zürich, Switzerland";
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, 0, text.length()).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    assertThat(classification, isTextClassification(text, TextClassifier.TYPE_ADDRESS));
  }

  @Test
  public void testClassifyText_url_inCaps() throws IOException {
    String text = "Visit HTTP://ANDROID.COM for more information";
    String classifiedText = "HTTP://ANDROID.COM";
    int startIndex = text.indexOf(classifiedText);
    int endIndex = startIndex + classifiedText.length();
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, startIndex, endIndex).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    assertThat(classification, isTextClassification(classifiedText, TextClassifier.TYPE_URL));
    assertThat(classification, containsIntentWithAction(Intent.ACTION_VIEW));
  }

  @Test
  public void testClassifyText_date() throws IOException {
    String text = "Let's meet on January 9, 2018.";
    String classifiedText = "January 9, 2018";
    int startIndex = text.indexOf(classifiedText);
    int endIndex = startIndex + classifiedText.length();
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, startIndex, endIndex).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    assertThat(classification, isTextClassification(classifiedText, TextClassifier.TYPE_DATE));
    Bundle extras = classification.getExtras();
    List<Bundle> entities = ExtrasUtils.getEntities(extras);
    assertThat(entities).hasSize(1);
    assertThat(ExtrasUtils.getEntityType(entities.get(0))).isEqualTo(TextClassifier.TYPE_DATE);
    ArrayList<Intent> actionsIntents = ExtrasUtils.getActionsIntents(classification);
    actionsIntents.forEach(TextClassifierImplTest::assertNoPackageInfoInExtras);
  }

  @Test
  public void testClassifyText_datetime() throws IOException {
    String text = "Let's meet 2018/01/01 10:30:20.";
    String classifiedText = "2018/01/01 10:30:20";
    int startIndex = text.indexOf(classifiedText);
    int endIndex = startIndex + classifiedText.length();
    TextClassification.Request request =
        new TextClassification.Request.Builder(text, startIndex, endIndex).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    assertThat(classification, isTextClassification(classifiedText, TextClassifier.TYPE_DATE_TIME));
  }

  @Test
  public void testClassifyText_foreignText() throws IOException {
    LocaleList originalLocales = LocaleList.getDefault();
    LocaleList.setDefault(LocaleList.forLanguageTags("en"));
    String japaneseText = "これは日本語のテキストです";
    TextClassification.Request request =
        new TextClassification.Request.Builder(japaneseText, 0, japaneseText.length()).build();

    TextClassification classification = classifier.classifyText(null, null, request);
    RemoteAction translateAction = classification.getActions().get(0);
    assertEquals(1, classification.getActions().size());
    assertEquals(Intent.ACTION_TRANSLATE, classification.getIntent().getAction());

    assertEquals(translateAction, ExtrasUtils.findTranslateAction(classification));
    Intent intent = ExtrasUtils.getActionsIntents(classification).get(0);
    assertNoPackageInfoInExtras(intent);
    assertEquals(Intent.ACTION_TRANSLATE, intent.getAction());
    Bundle foreignLanguageInfo = ExtrasUtils.getForeignLanguageExtra(classification);
    assertEquals("ja", ExtrasUtils.getEntityType(foreignLanguageInfo));
    assertTrue(ExtrasUtils.getScore(foreignLanguageInfo) >= 0);
    assertTrue(ExtrasUtils.getScore(foreignLanguageInfo) <= 1);
    assertTrue(intent.hasExtra(TextClassifier.EXTRA_FROM_TEXT_CLASSIFIER));
    assertEquals("ja", ExtrasUtils.getTopLanguage(intent).first);

    LocaleList.setDefault(originalLocales);
  }

  @Test
  public void testGenerateLinks_phone() throws IOException {
    String text = "The number is +12122537077. See you tonight!";
    TextLinks.Request request = new TextLinks.Request.Builder(text).build();
    assertThat(
        classifier.generateLinks(null, null, request),
        isTextLinksContaining(text, "+12122537077", TextClassifier.TYPE_PHONE));
  }

  @Test
  public void testGenerateLinks_exclude() throws IOException {
    String text = "The number is +12122537077. See you tonight!";
    List<String> hints = ImmutableList.of();
    List<String> included = ImmutableList.of();
    List<String> excluded = Arrays.asList(TextClassifier.TYPE_PHONE);
    TextLinks.Request request =
        new TextLinks.Request.Builder(text)
            .setEntityConfig(TextClassifier.EntityConfig.create(hints, included, excluded))
            .build();
    assertThat(
        classifier.generateLinks(null, null, request),
        not(isTextLinksContaining(text, "+12122537077", TextClassifier.TYPE_PHONE)));
  }

  @Test
  public void testGenerateLinks_explicit_address() throws IOException {
    String text = "The address is 1600 Amphitheater Parkway, Mountain View, CA. See you!";
    List<String> explicit = Arrays.asList(TextClassifier.TYPE_ADDRESS);
    TextLinks.Request request =
        new TextLinks.Request.Builder(text)
            .setEntityConfig(TextClassifier.EntityConfig.createWithExplicitEntityList(explicit))
            .build();
    assertThat(
        classifier.generateLinks(null, null, request),
        isTextLinksContaining(
            text, "1600 Amphitheater Parkway, Mountain View, CA", TextClassifier.TYPE_ADDRESS));
  }

  @Test
  public void testGenerateLinks_exclude_override() throws IOException {
    String text = "You want apple@banana.com. See you tonight!";
    List<String> hints = ImmutableList.of();
    List<String> included = Arrays.asList(TextClassifier.TYPE_EMAIL);
    List<String> excluded = Arrays.asList(TextClassifier.TYPE_EMAIL);
    TextLinks.Request request =
        new TextLinks.Request.Builder(text)
            .setEntityConfig(TextClassifier.EntityConfig.create(hints, included, excluded))
            .build();
    assertThat(
        classifier.generateLinks(null, null, request),
        not(isTextLinksContaining(text, "apple@banana.com", TextClassifier.TYPE_EMAIL)));
  }

  @Test
  public void testGenerateLinks_maxLength() throws IOException {
    char[] manySpaces = new char[classifier.getMaxGenerateLinksTextLength()];
    Arrays.fill(manySpaces, ' ');
    TextLinks.Request request = new TextLinks.Request.Builder(new String(manySpaces)).build();
    TextLinks links = classifier.generateLinks(null, null, request);
    assertTrue(links.getLinks().isEmpty());
  }

  @Test
  public void testApplyLinks_unsupportedCharacter() throws IOException {
    Spannable url = new SpannableString("\u202Emoc.diordna.com");
    TextLinks.Request request = new TextLinks.Request.Builder(url).build();
    assertEquals(
        TextLinks.STATUS_UNSUPPORTED_CHARACTER,
        classifier.generateLinks(null, null, request).apply(url, 0, null));
  }

  @Test
  public void testGenerateLinks_tooLong() {
    char[] manySpaces = new char[classifier.getMaxGenerateLinksTextLength() + 1];
    Arrays.fill(manySpaces, ' ');
    TextLinks.Request request = new TextLinks.Request.Builder(new String(manySpaces)).build();
    expectThrows(
        IllegalArgumentException.class, () -> classifier.generateLinks(null, null, request));
  }

  @Test
  public void testGenerateLinks_entityData() throws IOException {
    String text = "The number is +12122537077.";
    Bundle extras = new Bundle();
    ExtrasUtils.putIsSerializedEntityDataEnabled(extras, true);
    TextLinks.Request request = new TextLinks.Request.Builder(text).setExtras(extras).build();

    TextLinks textLinks = classifier.generateLinks(null, null, request);

    assertThat(textLinks.getLinks()).hasSize(1);
    TextLinks.TextLink textLink = textLinks.getLinks().iterator().next();
    List<Bundle> entities = ExtrasUtils.getEntities(textLink.getExtras());
    assertThat(entities).hasSize(1);
    Bundle entity = entities.get(0);
    assertThat(ExtrasUtils.getEntityType(entity)).isEqualTo(TextClassifier.TYPE_PHONE);
  }

  @Test
  public void testGenerateLinks_entityData_disabled() throws IOException {
    String text = "The number is +12122537077.";
    TextLinks.Request request = new TextLinks.Request.Builder(text).build();

    TextLinks textLinks = classifier.generateLinks(null, null, request);

    assertThat(textLinks.getLinks()).hasSize(1);
    TextLinks.TextLink textLink = textLinks.getLinks().iterator().next();
    List<Bundle> entities = ExtrasUtils.getEntities(textLink.getExtras());
    assertThat(entities).isNull();
  }

  @Test
  public void testDetectLanguage() throws IOException {
    String text = "This is English text";
    TextLanguage.Request request = new TextLanguage.Request.Builder(text).build();
    TextLanguage textLanguage = classifier.detectLanguage(null, null, request);
    assertThat(textLanguage, isTextLanguage("en"));
  }

  @Test
  public void testDetectLanguage_japanese() throws IOException {
    String text = "これは日本語のテキストです";
    TextLanguage.Request request = new TextLanguage.Request.Builder(text).build();
    TextLanguage textLanguage = classifier.detectLanguage(null, null, request);
    assertThat(textLanguage, isTextLanguage("ja"));
  }

  @Test
  public void testSuggestConversationActions_textReplyOnly_maxOne() throws IOException {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("Where are you?")
            .build();
    TextClassifier.EntityConfig typeConfig =
        new TextClassifier.EntityConfig.Builder()
            .includeTypesFromTextClassifier(false)
            .setIncludedTypes(Collections.singletonList(ConversationAction.TYPE_TEXT_REPLY))
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(Collections.singletonList(message))
            .setMaxSuggestions(1)
            .setTypeConfig(typeConfig)
            .build();

    ConversationActions conversationActions =
        classifier.suggestConversationActions(null, null, request);
    assertThat(conversationActions.getConversationActions()).hasSize(1);
    ConversationAction conversationAction = conversationActions.getConversationActions().get(0);
    assertThat(conversationAction.getType()).isEqualTo(ConversationAction.TYPE_TEXT_REPLY);
    assertThat(conversationAction.getTextReply()).isNotNull();
  }

  @Test
  public void testSuggestConversationActions_textReplyOnly_noMax() throws IOException {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("Where are you?")
            .build();
    TextClassifier.EntityConfig typeConfig =
        new TextClassifier.EntityConfig.Builder()
            .includeTypesFromTextClassifier(false)
            .setIncludedTypes(Collections.singletonList(ConversationAction.TYPE_TEXT_REPLY))
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(Collections.singletonList(message))
            .setTypeConfig(typeConfig)
            .build();

    ConversationActions conversationActions =
        classifier.suggestConversationActions(null, null, request);
    assertTrue(conversationActions.getConversationActions().size() > 1);
    for (ConversationAction conversationAction : conversationActions.getConversationActions()) {
      assertThat(conversationAction, isConversationAction(ConversationAction.TYPE_TEXT_REPLY));
    }
  }

  @Test
  public void testSuggestConversationActions_openUrl() throws IOException {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("Check this out: https://www.android.com")
            .build();
    TextClassifier.EntityConfig typeConfig =
        new TextClassifier.EntityConfig.Builder()
            .includeTypesFromTextClassifier(false)
            .setIncludedTypes(Collections.singletonList(ConversationAction.TYPE_OPEN_URL))
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(Collections.singletonList(message))
            .setMaxSuggestions(1)
            .setTypeConfig(typeConfig)
            .build();

    ConversationActions conversationActions =
        classifier.suggestConversationActions(null, null, request);
    assertThat(conversationActions.getConversationActions()).hasSize(1);
    ConversationAction conversationAction = conversationActions.getConversationActions().get(0);
    assertThat(conversationAction.getType()).isEqualTo(ConversationAction.TYPE_OPEN_URL);
    Intent actionIntent = ExtrasUtils.getActionIntent(conversationAction.getExtras());
    assertThat(actionIntent.getAction()).isEqualTo(Intent.ACTION_VIEW);
    assertThat(actionIntent.getData()).isEqualTo(Uri.parse("https://www.android.com"));
    assertNoPackageInfoInExtras(actionIntent);
  }

  @Test
  public void testSuggestConversationActions_copy() throws IOException {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("Authentication code: 12345")
            .build();
    TextClassifier.EntityConfig typeConfig =
        new TextClassifier.EntityConfig.Builder()
            .includeTypesFromTextClassifier(false)
            .setIncludedTypes(Collections.singletonList(TYPE_COPY))
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(Collections.singletonList(message))
            .setMaxSuggestions(1)
            .setTypeConfig(typeConfig)
            .build();

    ConversationActions conversationActions =
        classifier.suggestConversationActions(null, null, request);
    assertThat(conversationActions.getConversationActions()).hasSize(1);
    ConversationAction conversationAction = conversationActions.getConversationActions().get(0);
    assertThat(conversationAction.getType()).isEqualTo(TYPE_COPY);
    assertThat(conversationAction.getTextReply()).isAnyOf(null, "");
    assertThat(conversationAction.getAction()).isNull();
    String code = ExtrasUtils.getCopyText(conversationAction.getExtras());
    assertThat(code).isEqualTo("12345");
    assertThat(ExtrasUtils.getSerializedEntityData(conversationAction.getExtras())).isNotEmpty();
  }

  @Test
  public void testSuggestConversationActions_deduplicate() throws IOException {
    ConversationActions.Message message =
        new ConversationActions.Message.Builder(ConversationActions.Message.PERSON_USER_OTHERS)
            .setText("a@android.com b@android.com")
            .build();
    ConversationActions.Request request =
        new ConversationActions.Request.Builder(Collections.singletonList(message))
            .setMaxSuggestions(3)
            .build();

    ConversationActions conversationActions =
        classifier.suggestConversationActions(null, null, request);

    assertThat(conversationActions.getConversationActions()).isEmpty();
  }

  @Test
  public void testUseCachedAnnotatorModelDisabled() throws IOException {
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);

    String annotatorFilePath = TestDataUtils.getTestAnnotatorModelFile().getPath();
    ModelFile annotatorModelA =
        new ModelFile(ModelType.ANNOTATOR, annotatorFilePath, 701, "en", false);
    ModelFile annotatorModelB =
        new ModelFile(ModelType.ANNOTATOR, annotatorFilePath, 801, "en", false);

    String englishText = "You can reach me on +12122537077.";
    String classifiedText = "+12122537077";
    TextClassification.Request request =
        new TextClassification.Request.Builder(englishText, 0, englishText.length()).build();

    // Check modelFileA v701
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelA);
    TextClassification classificationA = classifier.classifyText(null, null, request);

    assertThat(classificationA.getId()).contains("v701");
    assertThat(classificationA.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {0, 0, 0, 0},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });

    // Check modelFileB v801
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelB);
    TextClassification classificationB = classifier.classifyText(null, null, request);

    assertThat(classificationB.getId()).contains("v801");
    assertThat(classificationB.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {0, 0, 0, 0},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });

    // Reload modelFileA v701
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelA);
    TextClassification classificationAcached = classifier.classifyText(null, null, request);

    assertThat(classificationAcached.getId()).contains("v701");
    assertThat(classificationAcached.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {0, 0, 0, 0},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });
  }

  @Test
  public void testUseCachedAnnotatorModelEnabled() throws IOException {
    deviceConfig.setConfig(TextClassifierSettings.MODEL_DOWNLOAD_MANAGER_ENABLED, true);
    deviceConfig.setConfig(TextClassifierSettings.MULTI_ANNOTATOR_CACHE_ENABLED, true);

    String annotatorFilePath = TestDataUtils.getTestAnnotatorModelFile().getPath();
    ModelFile annotatorModelA =
        new ModelFile(ModelType.ANNOTATOR, annotatorFilePath, 701, "en", false);
    ModelFile annotatorModelB =
        new ModelFile(ModelType.ANNOTATOR, annotatorFilePath, 801, "en", false);

    String englishText = "You can reach me on +12122537077.";
    String classifiedText = "+12122537077";
    TextClassification.Request request =
        new TextClassification.Request.Builder(englishText, 0, englishText.length()).build();

    // Check modelFileA v701
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelA);
    TextClassification classification = classifier.classifyText(null, null, request);

    assertThat(classification.getId()).contains("v701");
    assertThat(classification.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {1, 0, 0, 1},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });

    // Check modelFileB v801
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelB);
    TextClassification classificationB = classifier.classifyText(null, null, request);

    assertThat(classificationB.getId()).contains("v801");
    assertThat(classificationB.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {2, 0, 0, 2},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });

    // Reload modelFileA v701
    when(modelFileManager.findBestModelFile(eq(ModelType.ANNOTATOR), any(), any()))
        .thenReturn(annotatorModelA);
    TextClassification classificationAcached = classifier.classifyText(null, null, request);

    assertThat(classificationAcached.getId()).contains("v701");
    assertThat(classificationAcached.getText()).contains(classifiedText);
    assertArrayEquals(
        new int[] {2, 0, 1, 2},
        new int[] {
          annotatorModelCache.putCount(),
          annotatorModelCache.evictionCount(),
          annotatorModelCache.hitCount(),
          annotatorModelCache.missCount()
        });
  }

  private static void assertNoPackageInfoInExtras(Intent intent) {
    assertThat(intent.getComponent()).isNull();
    assertThat(intent.getPackage()).isNull();
  }

  private static Matcher<TextSelection> isTextSelection(
      final int startIndex, final int endIndex, final String type) {
    return new BaseMatcher<TextSelection>() {
      @Override
      public boolean matches(Object o) {
        if (o instanceof TextSelection) {
          TextSelection selection = (TextSelection) o;
          return startIndex == selection.getSelectionStartIndex()
              && endIndex == selection.getSelectionEndIndex()
              && typeMatches(selection, type);
        }
        return false;
      }

      private boolean typeMatches(TextSelection selection, String type) {
        return type == null
            || (selection.getEntityCount() > 0
                && type.trim().equalsIgnoreCase(selection.getEntity(0)));
      }

      @Override
      public void describeTo(Description description) {
        description.appendValue(String.format("%d, %d, %s", startIndex, endIndex, type));
      }
    };
  }

  private static Matcher<TextLinks> isTextLinksContaining(
      final String text, final String substring, final String type) {
    return new BaseMatcher<TextLinks>() {

      @Override
      public void describeTo(Description description) {
        description
            .appendText("text=")
            .appendValue(text)
            .appendText(", substring=")
            .appendValue(substring)
            .appendText(", type=")
            .appendValue(type);
      }

      @Override
      public boolean matches(Object o) {
        if (o instanceof TextLinks) {
          for (TextLinks.TextLink link : ((TextLinks) o).getLinks()) {
            if (text.subSequence(link.getStart(), link.getEnd()).toString().equals(substring)) {
              return type.equals(link.getEntity(0));
            }
          }
        }
        return false;
      }
    };
  }

  private static Matcher<TextClassification> isTextClassification(
      final String text, final String type) {
    return new BaseMatcher<TextClassification>() {
      @Override
      public boolean matches(Object o) {
        if (o instanceof TextClassification) {
          TextClassification result = (TextClassification) o;
          return text.equals(result.getText())
              && result.getEntityCount() > 0
              && type.equals(result.getEntity(0));
        }
        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("text=").appendValue(text).appendText(", type=").appendValue(type);
      }
    };
  }

  private static Matcher<TextClassification> containsIntentWithAction(final String action) {
    return new BaseMatcher<TextClassification>() {
      @Override
      public boolean matches(Object o) {
        if (o instanceof TextClassification) {
          TextClassification result = (TextClassification) o;
          return ExtrasUtils.findAction(result, action) != null;
        }
        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("intent action=").appendValue(action);
      }
    };
  }

  private static Matcher<TextLanguage> isTextLanguage(final String languageTag) {
    return new BaseMatcher<TextLanguage>() {
      @Override
      public boolean matches(Object o) {
        if (o instanceof TextLanguage) {
          TextLanguage result = (TextLanguage) o;
          return result.getLocaleHypothesisCount() > 0
              && languageTag.equals(result.getLocale(0).toLanguageTag());
        }
        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("locale=").appendValue(languageTag);
      }
    };
  }

  private static Matcher<ConversationAction> isConversationAction(String actionType) {
    return new BaseMatcher<ConversationAction>() {
      @Override
      public boolean matches(Object o) {
        if (!(o instanceof ConversationAction)) {
          return false;
        }
        ConversationAction conversationAction = (ConversationAction) o;
        if (!actionType.equals(conversationAction.getType())) {
          return false;
        }
        if (ConversationAction.TYPE_TEXT_REPLY.equals(actionType)) {
          if (conversationAction.getTextReply() == null) {
            return false;
          }
        }
        if (conversationAction.getConfidenceScore() < 0
            || conversationAction.getConfidenceScore() > 1) {
          return false;
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("actionType=").appendValue(actionType);
      }
    };
  }
}

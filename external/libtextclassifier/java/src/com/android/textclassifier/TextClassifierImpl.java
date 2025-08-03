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

import static java.util.stream.Collectors.toCollection;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Looper;
import android.util.ArrayMap;
import android.view.View.OnClickListener;
import android.view.textclassifier.ConversationAction;
import android.view.textclassifier.ConversationActions;
import android.view.textclassifier.SelectionEvent;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassification.Request;
import android.view.textclassifier.TextClassificationContext;
import android.view.textclassifier.TextClassificationSessionId;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextClassifierEvent;
import android.view.textclassifier.TextLanguage;
import android.view.textclassifier.TextLinks;
import android.view.textclassifier.TextSelection;
import androidx.annotation.GuardedBy;
import androidx.annotation.WorkerThread;
import androidx.collection.LruCache;
import androidx.core.util.Pair;
import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.common.TextClassifierSettings;
import com.android.textclassifier.common.TextSelectionCompat;
import com.android.textclassifier.common.base.TcLog;
import com.android.textclassifier.common.intent.LabeledIntent;
import com.android.textclassifier.common.intent.TemplateIntentFactory;
import com.android.textclassifier.common.logging.ResultIdUtils;
import com.android.textclassifier.common.logging.ResultIdUtils.ModelInfo;
import com.android.textclassifier.common.statsd.GenerateLinksLogger;
import com.android.textclassifier.common.statsd.SelectionEventConverter;
import com.android.textclassifier.common.statsd.TextClassificationSessionIdConverter;
import com.android.textclassifier.common.statsd.TextClassifierEventConverter;
import com.android.textclassifier.common.statsd.TextClassifierEventLogger;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.android.textclassifier.ActionsSuggestionsModel;
import com.google.android.textclassifier.ActionsSuggestionsModel.ActionSuggestions;
import com.google.android.textclassifier.AnnotatorModel;
import com.google.android.textclassifier.LangIdModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A text classifier that is running locally.
 *
 * <p>This class uses machine learning to recognize entities in text. Unless otherwise stated,
 * methods of this class are blocking operations and should most likely not be called on the UI
 * thread.
 */
final class TextClassifierImpl {

  private static final String TAG = "TextClassifierImpl";

  private final Context context;
  private final ModelFileManager modelFileManager;
  private final GenerateLinksLogger generateLinksLogger;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private ModelFile annotatorModelInUse;

  @GuardedBy("lock")
  private AnnotatorModel annotatorImpl;

  @GuardedBy("lock")
  private ModelFile langIdModelInUse;

  @GuardedBy("lock")
  private LangIdModel langIdImpl;

  @GuardedBy("lock")
  private ModelFile actionModelInUse;

  @GuardedBy("lock")
  private ActionsSuggestionsModel actionsImpl;

  @GuardedBy("lock")
  private final LruCache<ModelFile, AnnotatorModel> annotatorModelCache;

  private final TextClassifierEventLogger textClassifierEventLogger =
      new TextClassifierEventLogger();

  private final TextClassifierSettings settings;

  private final TemplateIntentFactory templateIntentFactory;

  TextClassifierImpl(
      Context context, TextClassifierSettings settings, ModelFileManager modelFileManager) {
    this(
        context, settings, modelFileManager, new LruCache<>(settings.getMultiAnnotatorCacheSize()));
  }

  @VisibleForTesting
  public TextClassifierImpl(
      Context context,
      TextClassifierSettings settings,
      ModelFileManager modelFileManager,
      LruCache<ModelFile, AnnotatorModel> annotatorModelCache) {
    this.context = Preconditions.checkNotNull(context);
    this.settings = Preconditions.checkNotNull(settings);
    this.modelFileManager = Preconditions.checkNotNull(modelFileManager);
    this.annotatorModelCache = annotatorModelCache;
    generateLinksLogger = new GenerateLinksLogger(this.settings.getGenerateLinksLogSampleRate());
    templateIntentFactory = new TemplateIntentFactory();
  }

  @WorkerThread
  TextSelection suggestSelection(
      @Nullable TextClassificationSessionId sessionId,
      @Nullable TextClassificationContext textClassificationContext,
      TextSelection.Request request)
      throws IOException {
    Preconditions.checkNotNull(request);
    checkMainThread();
    final int rangeLength = request.getEndIndex() - request.getStartIndex();
    final String string = request.getText().toString();
    Preconditions.checkArgument(!string.isEmpty(), "input string should not be empty");
    Preconditions.checkArgument(
        rangeLength <= settings.getClassifyTextMaxRangeLength(), "range is too large");
    final String localesString = concatenateLocales(request.getDefaultLocales());
    final LangIdModel langIdModel = getLangIdImpl();
    final String detectLanguageTags =
        String.join(",", detectLanguageTags(langIdModel, request.getText()));
    final ZonedDateTime refTime = ZonedDateTime.now(ZoneId.systemDefault());
    final LocaleList detectedLocaleList = LocaleList.forLanguageTags(detectLanguageTags);
    final ModelFile annotatorModelInUse =
        getAnnotatorModelFile(request.getDefaultLocales(), detectedLocaleList);
    final AnnotatorModel annotatorImpl = loadAnnotatorModelFile(annotatorModelInUse);
    final int[] startEnd =
        annotatorImpl.suggestSelection(
            string,
            request.getStartIndex(),
            request.getEndIndex(),
            AnnotatorModel.SelectionOptions.builder()
                .setLocales(localesString)
                .setDetectedTextLanguageTags(detectLanguageTags)
                .build());
    final int start = startEnd[0];
    final int end = startEnd[1];
    if (start >= end
        || start < 0
        || start > request.getStartIndex()
        || end > string.length()
        || end < request.getEndIndex()) {
      throw new IllegalArgumentException("Got bad indices for input text. Ignoring result.");
    }
    final TextSelection.Builder tsBuilder = new TextSelection.Builder(start, end);
    final boolean shouldIncludeTextClassification =
        TextSelectionCompat.shouldIncludeTextClassification(request);
    final AnnotatorModel.ClassificationResult[] results =
        annotatorImpl.classifyText(
            string,
            start,
            end,
            AnnotatorModel.ClassificationOptions.builder()
                .setReferenceTimeMsUtc(refTime.toInstant().toEpochMilli())
                .setReferenceTimezone(refTime.getZone().getId())
                .setLocales(localesString)
                .setDetectedTextLanguageTags(detectLanguageTags)
                .setAnnotationUsecase(AnnotatorModel.AnnotationUsecase.SMART.getValue())
                .setUserFamiliarLanguageTags(LocaleList.getDefault().toLanguageTags())
                .setEnableAddContactIntent(false)
                .setEnableSearchIntent(shouldEnableSearchIntent(textClassificationContext))
                .build(),
            // Passing null here to suppress intent generation.
            // TODO: Use an explicit flag to suppress it.
            shouldIncludeTextClassification ? context : null,
            getResourceLocalesString());
    final int size = results.length;
    for (int i = 0; i < size; i++) {
      tsBuilder.setEntityType(results[i].getCollection(), results[i].getScore());
    }
    final String resultId =
        createAnnotatorId(string, request.getStartIndex(), request.getEndIndex());
    if (shouldIncludeTextClassification) {
      TextClassification textClassification =
          createClassificationResult(results, string, start, end, langIdModel);
      TextSelectionCompat.setTextClassification(tsBuilder, textClassification);
    }
    return tsBuilder.setId(resultId).build();
  }

  @WorkerThread
  TextClassification classifyText(
      @Nullable TextClassificationSessionId sessionId,
      @Nullable TextClassificationContext textClassificationContext,
      Request request)
      throws IOException {
    Preconditions.checkNotNull(request);
    checkMainThread();
    LangIdModel langId = getLangIdImpl();
    List<String> detectLanguageTags = detectLanguageTags(langId, request.getText());
    final int rangeLength = request.getEndIndex() - request.getStartIndex();
    final String string = request.getText().toString();
    Preconditions.checkArgument(!string.isEmpty(), "input string should not be empty");
    Preconditions.checkArgument(
        rangeLength <= settings.getClassifyTextMaxRangeLength(), "range is too large");

    final String localesString = concatenateLocales(request.getDefaultLocales());
    final ZonedDateTime refTime =
        request.getReferenceTime() != null
            ? request.getReferenceTime()
            : ZonedDateTime.now(ZoneId.systemDefault());
    final LocaleList detectedLocaleList =
        LocaleList.forLanguageTags(String.join(",", detectLanguageTags));
    final AnnotatorModel.ClassificationResult[] results =
        getAnnotatorImpl(request.getDefaultLocales(), detectedLocaleList)
            .classifyText(
                string,
                request.getStartIndex(),
                request.getEndIndex(),
                AnnotatorModel.ClassificationOptions.builder()
                    .setReferenceTimeMsUtc(refTime.toInstant().toEpochMilli())
                    .setReferenceTimezone(refTime.getZone().getId())
                    .setLocales(localesString)
                    .setDetectedTextLanguageTags(String.join(",", detectLanguageTags))
                    .setAnnotationUsecase(AnnotatorModel.AnnotationUsecase.SMART.getValue())
                    .setUserFamiliarLanguageTags(LocaleList.getDefault().toLanguageTags())
                    .setEnableAddContactIntent(false)
                    .setEnableSearchIntent(shouldEnableSearchIntent(textClassificationContext))
                    .build(),
                context,
                getResourceLocalesString());
    if (results.length == 0) {
      throw new IllegalStateException("Empty text classification. Something went wrong.");
    }
    return createClassificationResult(
        results, string, request.getStartIndex(), request.getEndIndex(), langId);
  }

  @WorkerThread
  TextLinks generateLinks(
      @Nullable TextClassificationSessionId sessionId,
      @Nullable TextClassificationContext textClassificationContext,
      TextLinks.Request request)
      throws IOException {
    Preconditions.checkNotNull(request);
    Preconditions.checkArgument(
        request.getText().length() <= getMaxGenerateLinksTextLength(),
        "text.length() cannot be greater than %s",
        getMaxGenerateLinksTextLength());
    checkMainThread();

    final String textString = request.getText().toString();
    final TextLinks.Builder builder = new TextLinks.Builder(textString);

    final long startTimeMs = System.currentTimeMillis();
    final ZonedDateTime refTime = ZonedDateTime.now(ZoneId.systemDefault());
    final Collection<String> entitiesToIdentify =
        request.getEntityConfig() != null
            ? request
                .getEntityConfig()
                .resolveEntityListModifications(
                    getEntitiesForHints(request.getEntityConfig().getHints()))
            : settings.getEntityListDefault();
    final String localesString = concatenateLocales(request.getDefaultLocales());
    LangIdModel langId = getLangIdImpl();
    ImmutableList<String> detectLanguageTags = detectLanguageTags(langId, request.getText());
    final LocaleList detectedLocaleList =
        LocaleList.forLanguageTags(String.join(",", detectLanguageTags));
    final AnnotatorModel annotatorImpl =
        getAnnotatorImpl(request.getDefaultLocales(), detectedLocaleList);
    final boolean isSerializedEntityDataEnabled =
        ExtrasUtils.isSerializedEntityDataEnabled(request);
    final AnnotatorModel.AnnotatedSpan[] annotations =
        annotatorImpl.annotate(
            textString,
            AnnotatorModel.AnnotationOptions.builder()
                .setReferenceTimeMsUtc(refTime.toInstant().toEpochMilli())
                .setReferenceTimezone(refTime.getZone().getId())
                .setLocales(localesString)
                .setDetectedTextLanguageTags(String.join(",", detectLanguageTags))
                .setEntityTypes(entitiesToIdentify)
                .setAnnotationUsecase(AnnotatorModel.AnnotationUsecase.SMART.getValue())
                .setIsSerializedEntityDataEnabled(isSerializedEntityDataEnabled)
                .build());
    for (AnnotatorModel.AnnotatedSpan span : annotations) {
      final AnnotatorModel.ClassificationResult[] results = span.getClassification();
      if (results.length == 0 || !entitiesToIdentify.contains(results[0].getCollection())) {
        continue;
      }
      final Map<String, Float> entityScores = new ArrayMap<>();
      for (AnnotatorModel.ClassificationResult result : results) {
        entityScores.put(result.getCollection(), result.getScore());
      }
      Bundle extras = new Bundle();
      if (isSerializedEntityDataEnabled) {
        ExtrasUtils.putEntities(extras, results);
      }
      builder.addLink(span.getStartIndex(), span.getEndIndex(), entityScores, extras);
    }
    final TextLinks links = builder.build();
    final long endTimeMs = System.currentTimeMillis();
    final String callingPackageName =
        request.getCallingPackageName() == null
            ? context.getPackageName() // local (in process) TC.
            : request.getCallingPackageName();
    Optional<ModelInfo> annotatorModelInfo;
    Optional<ModelInfo> langIdModelInfo;
    synchronized (lock) {
      annotatorModelInfo =
          Optional.fromNullable(annotatorModelInUse).transform(ModelFile::toModelInfo);
      langIdModelInfo = Optional.fromNullable(langIdModelInUse).transform(ModelFile::toModelInfo);
    }
    generateLinksLogger.logGenerateLinks(
        sessionId,
        textClassificationContext,
        request.getText(),
        links,
        callingPackageName,
        endTimeMs - startTimeMs,
        annotatorModelInfo,
        langIdModelInfo);
    return links;
  }

  int getMaxGenerateLinksTextLength() {
    return settings.getGenerateLinksMaxTextLength();
  }

  private Collection<String> getEntitiesForHints(Collection<String> hints) {
    final boolean editable = hints.contains(TextClassifier.HINT_TEXT_IS_EDITABLE);
    final boolean notEditable = hints.contains(TextClassifier.HINT_TEXT_IS_NOT_EDITABLE);

    // Use the default if there is no hint, or conflicting ones.
    final boolean useDefault = editable == notEditable;
    if (useDefault) {
      return settings.getEntityListDefault();
    } else if (editable) {
      return settings.getEntityListEditable();
    } else { // notEditable
      return settings.getEntityListNotEditable();
    }
  }

  void onSelectionEvent(@Nullable TextClassificationSessionId sessionId, SelectionEvent event) {
    TextClassifierEvent textClassifierEvent = SelectionEventConverter.toTextClassifierEvent(event);
    if (textClassifierEvent == null) {
      return;
    }
    onTextClassifierEvent(event.getSessionId(), textClassifierEvent);
  }

  void onTextClassifierEvent(
      @Nullable TextClassificationSessionId sessionId, TextClassifierEvent event) {
    textClassifierEventLogger.writeEvent(
        TextClassificationSessionIdConverter.fromPlatform(sessionId),
        TextClassifierEventConverter.fromPlatform(event));
  }

  TextLanguage detectLanguage(
      @Nullable TextClassificationSessionId sessionId,
      @Nullable TextClassificationContext textClassificationContext,
      TextLanguage.Request request)
      throws IOException {
    Preconditions.checkNotNull(request);
    checkMainThread();
    final TextLanguage.Builder builder = new TextLanguage.Builder();
    LangIdModel langIdImpl = getLangIdImpl();
    final LangIdModel.LanguageResult[] langResults =
        langIdImpl.detectLanguages(request.getText().toString());
    for (LangIdModel.LanguageResult langResult : langResults) {
      builder.putLocale(ULocale.forLanguageTag(langResult.getLanguage()), langResult.getScore());
    }
    return builder.build();
  }

  ConversationActions suggestConversationActions(
      @Nullable TextClassificationSessionId sessionId,
      @Nullable TextClassificationContext textClassificationContext,
      ConversationActions.Request request)
      throws IOException {
    Preconditions.checkNotNull(request);
    checkMainThread();
    ActionsSuggestionsModel actionsImpl = getActionsImpl();
    LangIdModel langId = getLangIdImpl();
    ActionsSuggestionsModel.ConversationMessage[] nativeMessages =
        ActionsSuggestionsHelper.toNativeMessages(
            request.getConversation(), text -> detectLanguageTags(langId, text));
    if (nativeMessages.length == 0) {
      return new ConversationActions(ImmutableList.of(), /* id= */ null);
    }
    ActionsSuggestionsModel.Conversation nativeConversation =
        new ActionsSuggestionsModel.Conversation(nativeMessages);

    ActionSuggestions nativeSuggestions =
        actionsImpl.suggestActionsWithIntents(
            nativeConversation,
            null,
            context,
            getResourceLocalesString(),
            getAnnotatorImpl(LocaleList.getDefault(), /* detectedLocaleList= */ null));
    return createConversationActionResult(request, nativeSuggestions);
  }

  /**
   * Returns the {@link ConversationAction} result, with a non-null extras.
   *
   * <p>Whenever the RemoteAction is non-null, you can expect its corresponding intent with a
   * non-null component name is in the extras.
   */
  private ConversationActions createConversationActionResult(
      ConversationActions.Request request, ActionSuggestions nativeSuggestions) {
    Collection<String> expectedTypes = resolveActionTypesFromRequest(request);
    List<ConversationAction> conversationActions = new ArrayList<>();
    for (ActionsSuggestionsModel.ActionSuggestion nativeSuggestion :
        nativeSuggestions.actionSuggestions) {
      String actionType = nativeSuggestion.getActionType();
      if (!expectedTypes.contains(actionType)) {
        continue;
      }
      LabeledIntent.Result labeledIntentResult =
          ActionsSuggestionsHelper.createLabeledIntentResult(
              context, templateIntentFactory, nativeSuggestion);
      RemoteAction remoteAction = null;
      Bundle extras = new Bundle();
      if (labeledIntentResult != null) {
        remoteAction = labeledIntentResult.remoteAction.toRemoteAction();
        ExtrasUtils.putActionIntent(
            extras, stripPackageInfoFromIntent(labeledIntentResult.resolvedIntent));
      }
      ExtrasUtils.putSerializedEntityData(extras, nativeSuggestion.getSerializedEntityData());
      ExtrasUtils.putEntitiesExtras(
          extras, TemplateIntentFactory.nameVariantsToBundle(nativeSuggestion.getEntityData()));
      conversationActions.add(
          new ConversationAction.Builder(actionType)
              .setConfidenceScore(nativeSuggestion.getScore())
              .setTextReply(nativeSuggestion.getResponseText())
              .setAction(remoteAction)
              .setExtras(extras)
              .build());
    }
    conversationActions = ActionsSuggestionsHelper.removeActionsWithDuplicates(conversationActions);
    if (request.getMaxSuggestions() >= 0
        && conversationActions.size() > request.getMaxSuggestions()) {
      conversationActions = conversationActions.subList(0, request.getMaxSuggestions());
    }
    synchronized (lock) {
      String resultId =
          ActionsSuggestionsHelper.createResultId(
              context,
              request.getConversation(),
              Optional.fromNullable(actionModelInUse),
              Optional.fromNullable(annotatorModelInUse),
              Optional.fromNullable(langIdModelInUse));
      return new ConversationActions(conversationActions, resultId);
    }
  }

  private Collection<String> resolveActionTypesFromRequest(ConversationActions.Request request) {
    List<String> defaultActionTypes =
        request.getHints().contains(ConversationActions.Request.HINT_FOR_NOTIFICATION)
            ? settings.getNotificationConversationActionTypes()
            : settings.getInAppConversationActionTypes();
    return request.getTypeConfig().resolveEntityListModifications(defaultActionTypes);
  }

  private ModelFile getAnnotatorModelFile(
      LocaleList requestLocaleList, LocaleList detectedLocaleList) throws IOException {
    final ModelFile bestModel =
        modelFileManager.findBestModelFile(
            ModelType.ANNOTATOR, requestLocaleList, detectedLocaleList);
    if (bestModel == null) {
      throw new IllegalStateException("Failed to find the best annotator model");
    }
    return bestModel;
  }

  private AnnotatorModel loadAnnotatorModelFile(ModelFile annotatorModelFile) throws IOException {
    synchronized (lock) {
      if (settings.getMultiAnnotatorCacheEnabled()
          && !Objects.equals(annotatorModelInUse, annotatorModelFile)) {
        TcLog.v(TAG, "Attempting to reload cached annotator model....");
        annotatorImpl = annotatorModelCache.get(annotatorModelFile);
        if (annotatorImpl != null) {
          annotatorModelInUse = annotatorModelFile;
          TcLog.v(TAG, "Successfully reloaded cached annotator model: " + annotatorModelFile);
        }
      }
      if (annotatorImpl == null || !Objects.equals(annotatorModelInUse, annotatorModelFile)) {
        TcLog.d(TAG, "Loading " + annotatorModelFile);
        // The current annotator model may be still used by another thread / model.
        // Do not call close() here, and let the GC to clean it up when no one else
        // is using it.
        try (AssetFileDescriptor afd = annotatorModelFile.open(context.getAssets())) {
          annotatorImpl = new AnnotatorModel(afd);
          annotatorImpl.setLangIdModel(getLangIdImpl());
          annotatorModelInUse = annotatorModelFile;
          if (settings.getMultiAnnotatorCacheEnabled()) {
            annotatorModelCache.put(annotatorModelFile, annotatorImpl);
          }
        }
      }
      return annotatorImpl;
    }
  }

  private AnnotatorModel getAnnotatorImpl(
      LocaleList requestLocaleList, LocaleList detectedLocaleList) throws IOException {
    ModelFile annotatorModelFile = getAnnotatorModelFile(requestLocaleList, detectedLocaleList);
    return loadAnnotatorModelFile(annotatorModelFile);
  }

  private LangIdModel getLangIdImpl() throws IOException {
    synchronized (lock) {
      final ModelFile bestModel =
          modelFileManager.findBestModelFile(
              ModelType.LANG_ID, /* localePreferences= */ null, /* detectedLocales= */ null);
      if (bestModel == null) {
        throw new IllegalStateException("Failed to find the best LangID model.");
      }
      if (langIdImpl == null || !Objects.equals(langIdModelInUse, bestModel)) {
        TcLog.d(TAG, "Loading " + bestModel);
        try (AssetFileDescriptor afd = bestModel.open(context.getAssets())) {
          langIdImpl = new LangIdModel(afd);
          langIdModelInUse = bestModel;
        }
      }
      return langIdImpl;
    }
  }

  private ActionsSuggestionsModel getActionsImpl() throws IOException {
    synchronized (lock) {
      // TODO: Use LangID to determine the locale we should use here?
      final ModelFile bestModel =
          modelFileManager.findBestModelFile(
              ModelType.ACTIONS_SUGGESTIONS, LocaleList.getDefault(), /* detectedLocales= */ null);
      if (bestModel == null) {
        throw new IllegalStateException("Failed to find the best actions model");
      }
      if (actionsImpl == null || !Objects.equals(actionModelInUse, bestModel)) {
        TcLog.d(TAG, "Loading " + bestModel);
        try (AssetFileDescriptor afd = bestModel.open(context.getAssets())) {
          actionsImpl = new ActionsSuggestionsModel(afd);
          actionModelInUse = bestModel;
        }
      }
      return actionsImpl;
    }
  }

  private String createAnnotatorId(String text, int start, int end) {
    synchronized (lock) {
      return ResultIdUtils.createId(
          context,
          text,
          start,
          end,
          ModelFile.toModelInfos(
              Optional.fromNullable(annotatorModelInUse), Optional.fromNullable(langIdModelInUse)));
    }
  }

  private static String concatenateLocales(@Nullable LocaleList locales) {
    return (locales == null) ? "" : locales.toLanguageTags();
  }

  private TextClassification createClassificationResult(
      AnnotatorModel.ClassificationResult[] classifications,
      String text,
      int start,
      int end,
      LangIdModel langId) {
    final String classifiedText = text.substring(start, end);
    final TextClassification.Builder builder =
        new TextClassification.Builder().setText(classifiedText);

    final int typeCount = classifications.length;
    AnnotatorModel.ClassificationResult highestScoringResult =
        typeCount > 0 ? classifications[0] : null;
    for (int i = 0; i < typeCount; i++) {
      builder.setEntityType(classifications[i].getCollection(), classifications[i].getScore());
      if (classifications[i].getScore() > highestScoringResult.getScore()) {
        highestScoringResult = classifications[i];
      }
    }

    boolean isPrimaryAction = true;
    final ImmutableList<LabeledIntent> labeledIntents =
        highestScoringResult == null
            ? ImmutableList.of()
            : templateIntentFactory.create(highestScoringResult.getRemoteActionTemplates());
    final LabeledIntent.TitleChooser titleChooser =
        (labeledIntent, resolveInfo) -> labeledIntent.titleWithoutEntity;

    ArrayList<Intent> actionIntents = new ArrayList<>();
    for (LabeledIntent labeledIntent : labeledIntents) {
      final LabeledIntent.Result result = labeledIntent.resolve(context, titleChooser);
      if (result == null) {
        continue;
      }

      final Intent intent = result.resolvedIntent;
      final RemoteAction action = result.remoteAction.toRemoteAction();
      if (isPrimaryAction) {
        // For O backwards compatibility, the first RemoteAction is also written to the
        // legacy API fields.
        builder.setIcon(action.getIcon().loadDrawable(context));
        builder.setLabel(action.getTitle().toString());
        builder.setIntent(intent);
        builder.setOnClickListener(
            createIntentOnClickListener(
                createPendingIntent(context, intent, labeledIntent.requestCode)));
        isPrimaryAction = false;
      }
      builder.addAction(action);
      actionIntents.add(intent);
    }
    Bundle extras = new Bundle();
    Optional<Bundle> foreignLanguageExtra = maybeCreateExtrasForTranslate(actionIntents, langId);
    if (foreignLanguageExtra.isPresent()) {
      ExtrasUtils.putForeignLanguageExtra(extras, foreignLanguageExtra.get());
    }
    if (actionIntents.stream().anyMatch(Objects::nonNull)) {
      ArrayList<Intent> strippedIntents =
          actionIntents.stream()
              .map(TextClassifierImpl::stripPackageInfoFromIntent)
              .collect(toCollection(ArrayList::new));
      ExtrasUtils.putActionsIntents(extras, strippedIntents);
    }
    ExtrasUtils.putEntities(extras, classifications);
    builder.setExtras(extras);
    String resultId = createAnnotatorId(text, start, end);
    return builder.setId(resultId).build();
  }

  private static OnClickListener createIntentOnClickListener(final PendingIntent intent) {
    Preconditions.checkNotNull(intent);
    return v -> {
      try {
        intent.send();
      } catch (PendingIntent.CanceledException e) {
        TcLog.e(TAG, "Error sending PendingIntent", e);
      }
    };
  }

  private static Optional<Bundle> maybeCreateExtrasForTranslate(
      List<Intent> intents, LangIdModel langId) {
    Optional<Intent> translateIntent =
        FluentIterable.from(intents)
            .filter(Objects::nonNull)
            .filter(intent -> Intent.ACTION_TRANSLATE.equals(intent.getAction()))
            .first();
    if (!translateIntent.isPresent()) {
      return Optional.absent();
    }
    Pair<String, Float> topLanguageWithScore = ExtrasUtils.getTopLanguage(translateIntent.get());
    if (topLanguageWithScore == null) {
      return Optional.absent();
    }
    return Optional.of(
        ExtrasUtils.createForeignLanguageExtra(
            topLanguageWithScore.first, topLanguageWithScore.second, langId.getVersion()));
  }

  private ImmutableList<String> detectLanguageTags(LangIdModel langId, CharSequence text) {
    float threshold = getLangIdThreshold(langId);
    EntityConfidence languagesConfidence = detectLanguages(langId, text, threshold);
    return ImmutableList.copyOf(languagesConfidence.getEntities());
  }

  /**
   * Detects languages for the specified text. Only returns languages with score that is higher than
   * or equal to the specified threshold.
   */
  private static EntityConfidence detectLanguages(
      LangIdModel langId, CharSequence text, float threshold) {
    final LangIdModel.LanguageResult[] langResults = langId.detectLanguages(text.toString());
    final Map<String, Float> languagesMap = new ArrayMap<>();
    for (LangIdModel.LanguageResult langResult : langResults) {
      if (langResult.getScore() >= threshold) {
        languagesMap.put(langResult.getLanguage(), langResult.getScore());
      }
    }
    return new EntityConfidence(languagesMap);
  }

  private float getLangIdThreshold(LangIdModel langId) {
    return settings.getLangIdThresholdOverride() >= 0
        ? settings.getLangIdThresholdOverride()
        : langId.getLangIdThreshold();
  }

  void dump(IndentingPrintWriter printWriter) {
    synchronized (lock) {
      printWriter.println("TextClassifierImpl:");

      printWriter.increaseIndent();
      modelFileManager.dump(printWriter);
      printWriter.decreaseIndent();

      printWriter.println();
      settings.dump(printWriter);
      printWriter.println();
    }
  }

  /** Returns the locales string for the current resources configuration. */
  private String getResourceLocalesString() {
    try {
      return context.getResources().getConfiguration().getLocales().toLanguageTags();
    } catch (NullPointerException e) {

      // NPE is unexpected. Erring on the side of caution.
      return LocaleList.getDefault().toLanguageTags();
    }
  }

  private static void checkMainThread() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      TcLog.e(TAG, "TCS TextClassifier called on main thread", new Exception());
    }
  }

  private static PendingIntent createPendingIntent(
      final Context context, final Intent intent, int requestCode) {
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  @Nullable
  private static Intent stripPackageInfoFromIntent(@Nullable Intent intent) {
    if (intent == null) {
      return null;
    }
    Intent strippedIntent = new Intent(intent);
    strippedIntent.setPackage(null);
    strippedIntent.setComponent(null);
    return strippedIntent;
  }

  private static boolean shouldEnableSearchIntent(
      @Nullable TextClassificationContext textClassificationContext) {
    if (textClassificationContext == null) {
      return false;
    }
    String widgetType = textClassificationContext.getWidgetType();
    // Exclude WebView because there is already a *Web Search* chip there.
    return !(TextClassifier.WIDGET_TYPE_WEBVIEW.equals(widgetType)
        || TextClassifier.WIDGET_TYPE_EDIT_WEBVIEW.equals(widgetType));
  }
}

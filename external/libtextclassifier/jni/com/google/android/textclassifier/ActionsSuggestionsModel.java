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

package com.google.android.textclassifier;

import android.content.res.AssetFileDescriptor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Java wrapper for ActionsSuggestions native library interface. This library is used to suggest
 * actions and replies in a given conversation.
 *
 * @hide
 */
public final class ActionsSuggestionsModel implements AutoCloseable {
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  static {
    System.loadLibrary("textclassifier");
  }

  private long actionsModelPtr;

  /**
   * Creates a new instance of Actions predictor, using the provided model image, given as a file
   * descriptor.
   */
  public ActionsSuggestionsModel(int fileDescriptor, @Nullable byte[] serializedPreconditions) {
    actionsModelPtr = nativeNewActionsModel(fileDescriptor, serializedPreconditions);
    if (actionsModelPtr == 0L) {
      throw new IllegalArgumentException("Couldn't initialize actions model from file descriptor.");
    }
  }

  public ActionsSuggestionsModel(int fileDescriptor) {
    this(fileDescriptor, /* serializedPreconditions= */ null);
  }

  /**
   * Creates a new instance of Actions predictor, using the provided model image, given as a file
   * path.
   */
  public ActionsSuggestionsModel(String path, @Nullable byte[] serializedPreconditions) {
    actionsModelPtr = nativeNewActionsModelFromPath(path, serializedPreconditions);
    if (actionsModelPtr == 0L) {
      throw new IllegalArgumentException("Couldn't initialize actions model from given file.");
    }
  }

  public ActionsSuggestionsModel(String path) {
    this(path, /* serializedPreconditions= */ null);
  }

  /**
   * Creates a new instance of Actions predictor, using the provided model image, given as an {@link
   * AssetFileDescriptor}).
   */
  public ActionsSuggestionsModel(
      AssetFileDescriptor assetFileDescriptor, @Nullable byte[] serializedPreconditions) {
    actionsModelPtr =
        nativeNewActionsModelWithOffset(
            assetFileDescriptor.getParcelFileDescriptor().getFd(),
            assetFileDescriptor.getStartOffset(),
            assetFileDescriptor.getLength(),
            serializedPreconditions);
    if (actionsModelPtr == 0L) {
      throw new IllegalArgumentException("Couldn't initialize actions model from file descriptor.");
    }
  }

  public ActionsSuggestionsModel(AssetFileDescriptor assetFileDescriptor) {
    this(assetFileDescriptor, /* serializedPreconditions= */ null);
  }

  /** Suggests actions / replies to the given conversation. */
  public ActionSuggestions suggestActions(
      Conversation conversation, ActionSuggestionOptions options, AnnotatorModel annotator) {
    return nativeSuggestActions(
        actionsModelPtr,
        conversation,
        options,
        (annotator != null ? annotator.getNativeAnnotatorPointer() : 0),
        /* appContext= */ null,
        /* deviceLocales= */ null,
        /* generateAndroidIntents= */ false);
  }

  public ActionSuggestions suggestActionsWithIntents(
      Conversation conversation,
      ActionSuggestionOptions options,
      Object appContext,
      String deviceLocales,
      AnnotatorModel annotator) {
    return nativeSuggestActions(
        actionsModelPtr,
        conversation,
        options,
        (annotator != null ? annotator.getNativeAnnotatorPointer() : 0),
        appContext,
        deviceLocales,
        /* generateAndroidIntents= */ true);
  }

  /** Frees up the allocated memory. */
  @Override
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      nativeCloseActionsModel(actionsModelPtr);
      actionsModelPtr = 0L;
    }
  }

  @Override
  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }

  /** Returns a comma separated list of locales supported by the model as BCP 47 tags. */
  public static String getLocales(int fd) {
    return nativeGetLocales(fd);
  }

  /** Returns a comma separated list of locales supported by the model as BCP 47 tags. */
  public static String getLocales(AssetFileDescriptor assetFileDescriptor) {
    return nativeGetLocalesWithOffset(
        assetFileDescriptor.getParcelFileDescriptor().getFd(),
        assetFileDescriptor.getStartOffset(),
        assetFileDescriptor.getLength());
  }

  /** Returns the version of the model. */
  public static int getVersion(int fd) {
    return nativeGetVersion(fd);
  }

  /** Returns the version of the model. */
  public static int getVersion(AssetFileDescriptor assetFileDescriptor) {
    return nativeGetVersionWithOffset(
        assetFileDescriptor.getParcelFileDescriptor().getFd(),
        assetFileDescriptor.getStartOffset(),
        assetFileDescriptor.getLength());
  }

  /** Returns the name of the model. */
  public static String getName(int fd) {
    return nativeGetName(fd);
  }

  /** Returns the name of the model. */
  public static String getName(AssetFileDescriptor assetFileDescriptor) {
    return nativeGetNameWithOffset(
        assetFileDescriptor.getParcelFileDescriptor().getFd(),
        assetFileDescriptor.getStartOffset(),
        assetFileDescriptor.getLength());
  }

  /** Initializes conversation intent detection, passing the given serialized config to it. */
  public void initializeConversationIntentDetection(byte[] serializedConfig) {
    if (!nativeInitializeConversationIntentDetection(actionsModelPtr, serializedConfig)) {
      throw new IllegalArgumentException("Couldn't initialize conversation intent detection");
    }
  }

  /** Represents a list of suggested actions of a given conversation. */
  public static final class ActionSuggestions {
    /** A list of suggested actionsm sorted by score descendingly. */
    public final ActionSuggestion[] actionSuggestions;
    /** Whether the input conversation is considered as sensitive. */
    public final boolean isSensitive;

    public ActionSuggestions(ActionSuggestion[] actionSuggestions, boolean isSensitive) {
      this.actionSuggestions = actionSuggestions;
      this.isSensitive = isSensitive;
    }
  }

  /** Action suggestion that contains a response text and the type of the response. */
  public static final class ActionSuggestion {
    @Nullable private final String responseText;
    private final String actionType;
    private final float score;
    @Nullable private final NamedVariant[] entityData;
    @Nullable private final byte[] serializedEntityData;
    @Nullable private final RemoteActionTemplate[] remoteActionTemplates;
    @Nullable private final Slot[] slots;

    public ActionSuggestion(
        @Nullable String responseText,
        String actionType,
        float score,
        @Nullable NamedVariant[] entityData,
        @Nullable byte[] serializedEntityData,
        @Nullable RemoteActionTemplate[] remoteActionTemplates,
        @Nullable Slot[] slots) {
      this.responseText = responseText;
      this.actionType = actionType;
      this.score = score;
      this.entityData = entityData;
      this.serializedEntityData = serializedEntityData;
      this.remoteActionTemplates = remoteActionTemplates;
      this.slots = slots;
    }

    @Nullable
    public String getResponseText() {
      return responseText;
    }

    public String getActionType() {
      return actionType;
    }

    /** Confidence score between 0 and 1 */
    public float getScore() {
      return score;
    }

    @Nullable
    public NamedVariant[] getEntityData() {
      return entityData;
    }

    @Nullable
    public byte[] getSerializedEntityData() {
      return serializedEntityData;
    }

    @Nullable
    public RemoteActionTemplate[] getRemoteActionTemplates() {
      return remoteActionTemplates;
    }

    @Nullable
    public Slot[] getSlots() {
      return slots;
    }
  }

  /** Represents a single message in the conversation. */
  public static final class ConversationMessage {
    private final int userId;
    @Nullable private final String text;
    private final long referenceTimeMsUtc;
    @Nullable private final String referenceTimezone;
    @Nullable private final String detectedTextLanguageTags;

    public ConversationMessage(
        int userId,
        @Nullable String text,
        long referenceTimeMsUtc,
        @Nullable String referenceTimezone,
        @Nullable String detectedTextLanguageTags) {
      this.userId = userId;
      this.text = text;
      this.referenceTimeMsUtc = referenceTimeMsUtc;
      this.referenceTimezone = referenceTimezone;
      this.detectedTextLanguageTags = detectedTextLanguageTags;
    }

    /** The identifier of the sender */
    public int getUserId() {
      return userId;
    }

    @Nullable
    public String getText() {
      return text;
    }

    /**
     * Return the reference time of the message, for example, it could be compose time or send time.
     * {@code 0} means unspecified.
     */
    public long getReferenceTimeMsUtc() {
      return referenceTimeMsUtc;
    }

    @Nullable
    public String getReferenceTimezone() {
      return referenceTimezone;
    }

    /** Returns a comma separated list of BCP 47 language tags. */
    @Nullable
    public String getDetectedTextLanguageTags() {
      return detectedTextLanguageTags;
    }
  }

  /** Represents conversation between multiple users. */
  public static final class Conversation {
    public final ConversationMessage[] conversationMessages;

    public Conversation(ConversationMessage[] conversationMessages) {
      this.conversationMessages = conversationMessages;
    }

    public ConversationMessage[] getConversationMessages() {
      return conversationMessages;
    }
  }

  /** Represents options for the SuggestActions call. */
  public static final class ActionSuggestionOptions {
    public ActionSuggestionOptions() {}
  }

  /** Represents a slot for an {@link ActionSuggestion}. */
  public static final class Slot {

    public final String type;
    public final int messageIndex;
    public final int startIndex;
    public final int endIndex;
    public final float confidenceScore;

    public Slot(
        String type, int messageIndex, int startIndex, int endIndex, float confidenceScore) {
      this.type = type;
      this.messageIndex = messageIndex;
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.confidenceScore = confidenceScore;
    }
  }

  /**
   * Retrieves the pointer to the native object. Note: Need to keep the {@code
   * ActionsSuggestionsModel} alive as long as the pointer is used.
   */
  long getNativeModelPointer() {
    return nativeGetNativeModelPtr(actionsModelPtr);
  }

  private static native long nativeNewActionsModel(int fd, byte[] serializedPreconditions);

  private static native long nativeNewActionsModelFromPath(
      String path, byte[] preconditionsOverwrite);

  private static native long nativeNewActionsModelWithOffset(
      int fd, long offset, long size, byte[] preconditionsOverwrite);

  private native boolean nativeInitializeConversationIntentDetection(
      long actionsModelPtr, byte[] serializedConfig);

  private static native String nativeGetLocales(int fd);

  private static native String nativeGetLocalesWithOffset(int fd, long offset, long size);

  private static native int nativeGetVersion(int fd);

  private static native int nativeGetVersionWithOffset(int fd, long offset, long size);

  private static native String nativeGetName(int fd);

  private static native String nativeGetNameWithOffset(int fd, long offset, long size);

  private native ActionSuggestions nativeSuggestActions(
      long context,
      Conversation conversation,
      ActionSuggestionOptions options,
      long annotatorPtr,
      Object appContext,
      String deviceLocales,
      boolean generateAndroidIntents);

  private native void nativeCloseActionsModel(long ptr);

  private native long nativeGetNativeModelPtr(long context);
}

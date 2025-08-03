// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.icing;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;

/**
 * Java wrapper to access native APIs in external/icing/icing/icing-search-engine.h
 *
 * <p>If this instance has been closed, the instance is no longer usable.
 *
 * <p>Keep this class to be non-Final so that it can be mocked in AppSearch.
 *
 * <p>NOTE: This class is NOT thread-safe.
 */
public class IcingSearchEngineImpl implements Closeable {

  private static final String TAG = "IcingSearchEngineImpl";

  private long nativePointer;

  private boolean closed = false;

  static {
    // NOTE: This can fail with an UnsatisfiedLinkError
    System.loadLibrary("icing");
  }

  /**
   * @throws IllegalStateException if IcingSearchEngineImpl fails to be created
   */
  public IcingSearchEngineImpl(@NonNull byte[] optionsBytes) {
    nativePointer = nativeCreate(optionsBytes);
    if (nativePointer == 0) {
      Log.e(TAG, "Failed to create IcingSearchEngineImpl.");
      throw new IllegalStateException("Failed to create IcingSearchEngineImpl.");
    }
  }

  private void throwIfClosed() {
    if (closed) {
      throw new IllegalStateException("Trying to use a closed IcingSearchEngineImpl instance.");
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }

    if (nativePointer != 0) {
      nativeDestroy(this);
    }
    nativePointer = 0;
    closed = true;
  }

  @Nullable
  public byte[] initialize() {
    throwIfClosed();
    return nativeInitialize(this);
  }

  @Nullable
  public byte[] setSchema(@NonNull byte[] schemaBytes) {
    return setSchema(schemaBytes, /* ignoreErrorsAndDeleteDocuments= */ false);
  }

  @Nullable
  public byte[] setSchema(@NonNull byte[] schemaBytes, boolean ignoreErrorsAndDeleteDocuments) {
    throwIfClosed();
    return nativeSetSchema(this, schemaBytes, ignoreErrorsAndDeleteDocuments);
  }

  @Nullable
  public byte[] getSchema() {
    throwIfClosed();
    return nativeGetSchema(this);
  }

  @Nullable
  public byte[] getSchemaType(@NonNull String schemaType) {
    throwIfClosed();
    return nativeGetSchemaType(this, schemaType);
  }

  @Nullable
  public byte[] put(@NonNull byte[] documentBytes) {
    throwIfClosed();
    return nativePut(this, documentBytes);
  }

  @Nullable
  public byte[] get(
      @NonNull String namespace, @NonNull String uri, @NonNull byte[] getResultSpecBytes) {
    throwIfClosed();
    return nativeGet(this, namespace, uri, getResultSpecBytes);
  }

  @Nullable
  public byte[] reportUsage(@NonNull byte[] usageReportBytes) {
    throwIfClosed();
    return nativeReportUsage(this, usageReportBytes);
  }

  @Nullable
  public byte[] getAllNamespaces() {
    throwIfClosed();
    return nativeGetAllNamespaces(this);
  }

  @Nullable
  public byte[] search(
      @NonNull byte[] searchSpecBytes,
      @NonNull byte[] scoringSpecBytes,
      @NonNull byte[] resultSpecBytes) {
    throwIfClosed();

    // Note that on Android System.currentTimeMillis() is the standard "wall" clock and can be set
    // by the user or the phone network so the time may jump backwards or forwards unpredictably.
    // This could lead to inaccurate final JNI latency calculations or unexpected negative numbers
    // in the case where the phone time is changed while sending data across JNI layers.
    // However these occurrences should be very rare, so we will keep usage of
    // System.currentTimeMillis() due to the lack of better time functions that can provide a
    // consistent timestamp across all platforms.
    long javaToNativeStartTimestampMs = System.currentTimeMillis();
    return nativeSearch(
        this, searchSpecBytes, scoringSpecBytes, resultSpecBytes, javaToNativeStartTimestampMs);
  }

  @Nullable
  public byte[] getNextPage(long nextPageToken) {
    throwIfClosed();
    return nativeGetNextPage(this, nextPageToken, System.currentTimeMillis());
  }

  @NonNull
  public void invalidateNextPageToken(long nextPageToken) {
    throwIfClosed();
    nativeInvalidateNextPageToken(this, nextPageToken);
  }

  @Nullable
  public byte[] delete(@NonNull String namespace, @NonNull String uri) {
    throwIfClosed();
    return nativeDelete(this, namespace, uri);
  }

  @Nullable
  public byte[] searchSuggestions(@NonNull byte[] suggestionSpecBytes) {
    throwIfClosed();
    return nativeSearchSuggestions(this, suggestionSpecBytes);
  }

  @Nullable
  public byte[] deleteByNamespace(@NonNull String namespace) {
    throwIfClosed();
    return nativeDeleteByNamespace(this, namespace);
  }

  @Nullable
  public byte[] deleteBySchemaType(@NonNull String schemaType) {
    throwIfClosed();
    return nativeDeleteBySchemaType(this, schemaType);
  }

  @Nullable
  public byte[] deleteByQuery(@NonNull byte[] searchSpecBytes) {
    return deleteByQuery(searchSpecBytes, /* returnDeletedDocumentInfo= */ false);
  }

  @Nullable
  public byte[] deleteByQuery(@NonNull byte[] searchSpecBytes, boolean returnDeletedDocumentInfo) {
    throwIfClosed();
    return nativeDeleteByQuery(this, searchSpecBytes, returnDeletedDocumentInfo);
  }

  @Nullable
  public byte[] persistToDisk(int persistTypeCode) {
    throwIfClosed();
    return nativePersistToDisk(this, persistTypeCode);
  }

  @Nullable
  public byte[] optimize() {
    throwIfClosed();
    return nativeOptimize(this);
  }

  @Nullable
  public byte[] getOptimizeInfo() {
    throwIfClosed();
    return nativeGetOptimizeInfo(this);
  }

  @Nullable
  public byte[] getStorageInfo() {
    throwIfClosed();
    return nativeGetStorageInfo(this);
  }

  @Nullable
  public byte[] getDebugInfo(int verbosityCode) {
    throwIfClosed();
    return nativeGetDebugInfo(this, verbosityCode);
  }

  @Nullable
  public byte[] reset() {
    throwIfClosed();
    return nativeReset(this);
  }

  public static boolean shouldLog(short severity) {
    return shouldLog(severity, (short) 0);
  }

  public static boolean shouldLog(short severity, short verbosity) {
    return nativeShouldLog(severity, verbosity);
  }

  public static boolean setLoggingLevel(short severity) {
    return setLoggingLevel(severity, (short) 0);
  }

  public static boolean setLoggingLevel(short severity, short verbosity) {
    return nativeSetLoggingLevel(severity, verbosity);
  }

  @Nullable
  public static String getLoggingTag() {
    String tag = nativeGetLoggingTag();
    if (tag == null) {
      Log.e(TAG, "Received null logging tag from native.");
    }
    return tag;
  }

  private static native long nativeCreate(byte[] icingSearchEngineOptionsBytes);

  private static native void nativeDestroy(IcingSearchEngineImpl instance);

  private static native byte[] nativeInitialize(IcingSearchEngineImpl instance);

  private static native byte[] nativeSetSchema(
      IcingSearchEngineImpl instance, byte[] schemaBytes, boolean ignoreErrorsAndDeleteDocuments);

  private static native byte[] nativeGetSchema(IcingSearchEngineImpl instance);

  private static native byte[] nativeGetSchemaType(
      IcingSearchEngineImpl instance, String schemaType);

  private static native byte[] nativePut(IcingSearchEngineImpl instance, byte[] documentBytes);

  private static native byte[] nativeGet(
      IcingSearchEngineImpl instance, String namespace, String uri, byte[] getResultSpecBytes);

  private static native byte[] nativeReportUsage(
      IcingSearchEngineImpl instance, byte[] usageReportBytes);

  private static native byte[] nativeGetAllNamespaces(IcingSearchEngineImpl instance);

  private static native byte[] nativeSearch(
      IcingSearchEngineImpl instance,
      byte[] searchSpecBytes,
      byte[] scoringSpecBytes,
      byte[] resultSpecBytes,
      long javaToNativeStartTimestampMs);

  private static native byte[] nativeGetNextPage(
      IcingSearchEngineImpl instance, long nextPageToken, long javaToNativeStartTimestampMs);

  private static native void nativeInvalidateNextPageToken(
      IcingSearchEngineImpl instance, long nextPageToken);

  private static native byte[] nativeDelete(
      IcingSearchEngineImpl instance, String namespace, String uri);

  private static native byte[] nativeDeleteByNamespace(
      IcingSearchEngineImpl instance, String namespace);

  private static native byte[] nativeDeleteBySchemaType(
      IcingSearchEngineImpl instance, String schemaType);

  private static native byte[] nativeDeleteByQuery(
      IcingSearchEngineImpl instance, byte[] searchSpecBytes, boolean returnDeletedDocumentInfo);

  private static native byte[] nativePersistToDisk(IcingSearchEngineImpl instance, int persistType);

  private static native byte[] nativeOptimize(IcingSearchEngineImpl instance);

  private static native byte[] nativeGetOptimizeInfo(IcingSearchEngineImpl instance);

  private static native byte[] nativeGetStorageInfo(IcingSearchEngineImpl instance);

  private static native byte[] nativeReset(IcingSearchEngineImpl instance);

  private static native byte[] nativeSearchSuggestions(
      IcingSearchEngineImpl instance, byte[] suggestionSpecBytes);

  private static native byte[] nativeGetDebugInfo(IcingSearchEngineImpl instance, int verbosity);

  private static native boolean nativeShouldLog(short severity, short verbosity);

  private static native boolean nativeSetLoggingLevel(short severity, short verbosity);

  private static native String nativeGetLoggingTag();
}

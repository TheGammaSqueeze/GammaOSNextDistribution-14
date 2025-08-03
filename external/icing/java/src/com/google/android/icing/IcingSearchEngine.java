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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.icing.proto.DebugInfoResultProto;
import com.google.android.icing.proto.DebugInfoVerbosity;
import com.google.android.icing.proto.DeleteByNamespaceResultProto;
import com.google.android.icing.proto.DeleteByQueryResultProto;
import com.google.android.icing.proto.DeleteBySchemaTypeResultProto;
import com.google.android.icing.proto.DeleteResultProto;
import com.google.android.icing.proto.DocumentProto;
import com.google.android.icing.proto.GetAllNamespacesResultProto;
import com.google.android.icing.proto.GetOptimizeInfoResultProto;
import com.google.android.icing.proto.GetResultProto;
import com.google.android.icing.proto.GetResultSpecProto;
import com.google.android.icing.proto.GetSchemaResultProto;
import com.google.android.icing.proto.GetSchemaTypeResultProto;
import com.google.android.icing.proto.IcingSearchEngineOptions;
import com.google.android.icing.proto.InitializeResultProto;
import com.google.android.icing.proto.LogSeverity;
import com.google.android.icing.proto.OptimizeResultProto;
import com.google.android.icing.proto.PersistToDiskResultProto;
import com.google.android.icing.proto.PersistType;
import com.google.android.icing.proto.PutResultProto;
import com.google.android.icing.proto.ReportUsageResultProto;
import com.google.android.icing.proto.ResetResultProto;
import com.google.android.icing.proto.ResultSpecProto;
import com.google.android.icing.proto.SchemaProto;
import com.google.android.icing.proto.ScoringSpecProto;
import com.google.android.icing.proto.SearchResultProto;
import com.google.android.icing.proto.SearchSpecProto;
import com.google.android.icing.proto.SetSchemaResultProto;
import com.google.android.icing.proto.StorageInfoResultProto;
import com.google.android.icing.proto.SuggestionResponse;
import com.google.android.icing.proto.SuggestionSpecProto;
import com.google.android.icing.proto.UsageReport;

/**
 * Java wrapper to access {@link IcingSearchEngineImpl}.
 *
 * <p>It converts byte array from {@link IcingSearchEngineImpl} to corresponding protos.
 *
 * <p>If this instance has been closed, the instance is no longer usable.
 *
 * <p>Keep this class to be non-Final so that it can be mocked in AppSearch.
 *
 * <p>NOTE: This class is NOT thread-safe.
 */
public class IcingSearchEngine implements IcingSearchEngineInterface {

  private static final String TAG = "IcingSearchEngine";
  private final IcingSearchEngineImpl icingSearchEngineImpl;

  /**
   * @throws IllegalStateException if IcingSearchEngine fails to be created
   */
  public IcingSearchEngine(@NonNull IcingSearchEngineOptions options) {
    icingSearchEngineImpl = new IcingSearchEngineImpl(options.toByteArray());
  }

  @Override
  public void close() {
    icingSearchEngineImpl.close();
  }

  @NonNull
  @Override
  public InitializeResultProto initialize() {
    return IcingSearchEngineUtils.byteArrayToInitializeResultProto(
        icingSearchEngineImpl.initialize());
  }

  @NonNull
  @Override
  public SetSchemaResultProto setSchema(@NonNull SchemaProto schema) {
    return setSchema(schema, /*ignoreErrorsAndDeleteDocuments=*/ false);
  }

  @NonNull
  @Override
  public SetSchemaResultProto setSchema(
      @NonNull SchemaProto schema, boolean ignoreErrorsAndDeleteDocuments) {
    return IcingSearchEngineUtils.byteArrayToSetSchemaResultProto(
        icingSearchEngineImpl.setSchema(schema.toByteArray(), ignoreErrorsAndDeleteDocuments));
  }

  @NonNull
  @Override
  public GetSchemaResultProto getSchema() {
    return IcingSearchEngineUtils.byteArrayToGetSchemaResultProto(
        icingSearchEngineImpl.getSchema());
  }

  @NonNull
  @Override
  public GetSchemaTypeResultProto getSchemaType(@NonNull String schemaType) {
    return IcingSearchEngineUtils.byteArrayToGetSchemaTypeResultProto(
        icingSearchEngineImpl.getSchemaType(schemaType));
  }

  @NonNull
  @Override
  public PutResultProto put(@NonNull DocumentProto document) {
    return IcingSearchEngineUtils.byteArrayToPutResultProto(
        icingSearchEngineImpl.put(document.toByteArray()));
  }

  @NonNull
  @Override
  public GetResultProto get(
      @NonNull String namespace, @NonNull String uri, @NonNull GetResultSpecProto getResultSpec) {
    return IcingSearchEngineUtils.byteArrayToGetResultProto(
        icingSearchEngineImpl.get(namespace, uri, getResultSpec.toByteArray()));
  }

  @NonNull
  @Override
  public ReportUsageResultProto reportUsage(@NonNull UsageReport usageReport) {
    return IcingSearchEngineUtils.byteArrayToReportUsageResultProto(
        icingSearchEngineImpl.reportUsage(usageReport.toByteArray()));
  }

  @NonNull
  @Override
  public GetAllNamespacesResultProto getAllNamespaces() {
    return IcingSearchEngineUtils.byteArrayToGetAllNamespacesResultProto(
        icingSearchEngineImpl.getAllNamespaces());
  }

  @NonNull
  @Override
  public SearchResultProto search(
      @NonNull SearchSpecProto searchSpec,
      @NonNull ScoringSpecProto scoringSpec,
      @NonNull ResultSpecProto resultSpec) {
    return IcingSearchEngineUtils.byteArrayToSearchResultProto(
        icingSearchEngineImpl.search(
            searchSpec.toByteArray(), scoringSpec.toByteArray(), resultSpec.toByteArray()));
  }

  @NonNull
  @Override
  public SearchResultProto getNextPage(long nextPageToken) {
    return IcingSearchEngineUtils.byteArrayToSearchResultProto(
        icingSearchEngineImpl.getNextPage(nextPageToken));
  }

  @NonNull
  @Override
  public void invalidateNextPageToken(long nextPageToken) {
    icingSearchEngineImpl.invalidateNextPageToken(nextPageToken);
  }

  @NonNull
  @Override
  public DeleteResultProto delete(@NonNull String namespace, @NonNull String uri) {
    return IcingSearchEngineUtils.byteArrayToDeleteResultProto(
        icingSearchEngineImpl.delete(namespace, uri));
  }

  @NonNull
  @Override
  public SuggestionResponse searchSuggestions(@NonNull SuggestionSpecProto suggestionSpec) {
    return IcingSearchEngineUtils.byteArrayToSuggestionResponse(
        icingSearchEngineImpl.searchSuggestions(suggestionSpec.toByteArray()));
  }

  @NonNull
  @Override
  public DeleteByNamespaceResultProto deleteByNamespace(@NonNull String namespace) {
    return IcingSearchEngineUtils.byteArrayToDeleteByNamespaceResultProto(
        icingSearchEngineImpl.deleteByNamespace(namespace));
  }

  @NonNull
  @Override
  public DeleteBySchemaTypeResultProto deleteBySchemaType(@NonNull String schemaType) {
    return IcingSearchEngineUtils.byteArrayToDeleteBySchemaTypeResultProto(
        icingSearchEngineImpl.deleteBySchemaType(schemaType));
  }

  @NonNull
  @Override
  public DeleteByQueryResultProto deleteByQuery(@NonNull SearchSpecProto searchSpec) {
    return deleteByQuery(searchSpec, /*returnDeletedDocumentInfo=*/ false);
  }

  @NonNull
  @Override
  public DeleteByQueryResultProto deleteByQuery(
      @NonNull SearchSpecProto searchSpec, boolean returnDeletedDocumentInfo) {
    return IcingSearchEngineUtils.byteArrayToDeleteByQueryResultProto(
        icingSearchEngineImpl.deleteByQuery(searchSpec.toByteArray(), returnDeletedDocumentInfo));
  }

  @NonNull
  @Override
  public PersistToDiskResultProto persistToDisk(@NonNull PersistType.Code persistTypeCode) {
    return IcingSearchEngineUtils.byteArrayToPersistToDiskResultProto(
        icingSearchEngineImpl.persistToDisk(persistTypeCode.getNumber()));
  }

  @NonNull
  @Override
  public OptimizeResultProto optimize() {
    return IcingSearchEngineUtils.byteArrayToOptimizeResultProto(icingSearchEngineImpl.optimize());
  }

  @NonNull
  @Override
  public GetOptimizeInfoResultProto getOptimizeInfo() {
    return IcingSearchEngineUtils.byteArrayToGetOptimizeInfoResultProto(
        icingSearchEngineImpl.getOptimizeInfo());
  }

  @NonNull
  @Override
  public StorageInfoResultProto getStorageInfo() {
    return IcingSearchEngineUtils.byteArrayToStorageInfoResultProto(
        icingSearchEngineImpl.getStorageInfo());
  }

  @NonNull
  @Override
  public DebugInfoResultProto getDebugInfo(DebugInfoVerbosity.Code verbosity) {
    return IcingSearchEngineUtils.byteArrayToDebugInfoResultProto(
        icingSearchEngineImpl.getDebugInfo(verbosity.getNumber()));
  }

  @NonNull
  @Override
  public ResetResultProto reset() {
    return IcingSearchEngineUtils.byteArrayToResetResultProto(icingSearchEngineImpl.reset());
  }

  public static boolean shouldLog(LogSeverity.Code severity) {
    return shouldLog(severity, (short) 0);
  }

  public static boolean shouldLog(LogSeverity.Code severity, short verbosity) {
    return IcingSearchEngineImpl.shouldLog((short) severity.getNumber(), verbosity);
  }

  public static boolean setLoggingLevel(LogSeverity.Code severity) {
    return setLoggingLevel(severity, (short) 0);
  }

  public static boolean setLoggingLevel(LogSeverity.Code severity, short verbosity) {
    return IcingSearchEngineImpl.setLoggingLevel((short) severity.getNumber(), verbosity);
  }

  @Nullable
  public static String getLoggingTag() {
    return IcingSearchEngineImpl.getLoggingTag();
  }
}

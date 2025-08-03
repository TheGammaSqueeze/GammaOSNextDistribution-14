package com.google.android.icing;

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
import com.google.android.icing.proto.InitializeResultProto;
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
import java.io.Closeable;

/** A common user-facing interface to expose the funcationalities provided by Icing Library. */
public interface IcingSearchEngineInterface extends Closeable {
  /**
   * Initializes the current IcingSearchEngine implementation.
   *
   * <p>Internally the icing instance will be initialized.
   */
  InitializeResultProto initialize();

  /** Sets the schema for the icing instance. */
  SetSchemaResultProto setSchema(SchemaProto schema);

  /**
   * Sets the schema for the icing instance.
   *
   * @param ignoreErrorsAndDeleteDocuments force to set the schema and delete documents in case of
   *     incompatible schema change.
   */
  SetSchemaResultProto setSchema(SchemaProto schema, boolean ignoreErrorsAndDeleteDocuments);

  /** Gets the schema for the icing instance. */
  GetSchemaResultProto getSchema();

  /**
   * Gets the schema for the icing instance.
   *
   * @param schemaType type of the schema.
   */
  GetSchemaTypeResultProto getSchemaType(String schemaType);

  /** Puts the document. */
  PutResultProto put(DocumentProto document);

  /**
   * Gets the document.
   *
   * @param namespace namespace of the document.
   * @param uri uri of the document.
   * @param getResultSpec the spec for getting the document.
   */
  GetResultProto get(String namespace, String uri, GetResultSpecProto getResultSpec);

  /** Reports usage. */
  ReportUsageResultProto reportUsage(UsageReport usageReport);

  /** Gets all namespaces. */
  GetAllNamespacesResultProto getAllNamespaces();

  /**
   * Searches over the documents.
   *
   * <p>Documents need to be retrieved on the following {@link #getNextPage} calls on the returned
   * {@link SearchResultProto}.
   */
  SearchResultProto search(
      SearchSpecProto searchSpec, ScoringSpecProto scoringSpec, ResultSpecProto resultSpec);

  /** Gets the next page. */
  SearchResultProto getNextPage(long nextPageToken);

  /** Invalidates the next page token. */
  void invalidateNextPageToken(long nextPageToken);

  /**
   * Deletes the document.
   *
   * @param namespace the namespace the document to be deleted belong to.
   * @param uri the uri for the document to be deleted.
   */
  DeleteResultProto delete(String namespace, String uri);

  /** Returns the suggestions for the search query. */
  SuggestionResponse searchSuggestions(SuggestionSpecProto suggestionSpec);

  /** Deletes documents by the namespace. */
  DeleteByNamespaceResultProto deleteByNamespace(String namespace);

  /** Deletes documents by the schema type. */
  DeleteBySchemaTypeResultProto deleteBySchemaType(String schemaType);

  /** Deletes documents by the search query. */
  DeleteByQueryResultProto deleteByQuery(SearchSpecProto searchSpec);

  /**
   * Deletes document by the search query
   *
   * @param returnDeletedDocumentInfo whether additional information about deleted documents will be
   *     included in {@link DeleteByQueryResultProto}.
   */
  DeleteByQueryResultProto deleteByQuery(
      SearchSpecProto searchSpec, boolean returnDeletedDocumentInfo);

  /** Makes sure every update/delete received till this point is flushed to disk. */
  PersistToDiskResultProto persistToDisk(PersistType.Code persistTypeCode);

  /** Makes the icing instance run tasks that are too expensive to be run in real-time. */
  OptimizeResultProto optimize();

  /** Gets information about the optimization. */
  GetOptimizeInfoResultProto getOptimizeInfo();

  /** Gets information about the storage. */
  StorageInfoResultProto getStorageInfo();

  /** Gets the debug information for the current icing instance. */
  DebugInfoResultProto getDebugInfo(DebugInfoVerbosity.Code verbosity);

  /** Clears all data from the current icing instance, and reinitializes it. */
  ResetResultProto reset();

  /** Closes the current icing instance. */
  @Override
  void close();
}

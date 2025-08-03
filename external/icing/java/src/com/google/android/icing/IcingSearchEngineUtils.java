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
import com.google.android.icing.proto.DebugInfoResultProto;
import com.google.android.icing.proto.DeleteByNamespaceResultProto;
import com.google.android.icing.proto.DeleteByQueryResultProto;
import com.google.android.icing.proto.DeleteBySchemaTypeResultProto;
import com.google.android.icing.proto.DeleteResultProto;
import com.google.android.icing.proto.GetAllNamespacesResultProto;
import com.google.android.icing.proto.GetOptimizeInfoResultProto;
import com.google.android.icing.proto.GetResultProto;
import com.google.android.icing.proto.GetSchemaResultProto;
import com.google.android.icing.proto.GetSchemaTypeResultProto;
import com.google.android.icing.proto.InitializeResultProto;
import com.google.android.icing.proto.OptimizeResultProto;
import com.google.android.icing.proto.PersistToDiskResultProto;
import com.google.android.icing.proto.PutResultProto;
import com.google.android.icing.proto.ReportUsageResultProto;
import com.google.android.icing.proto.ResetResultProto;
import com.google.android.icing.proto.SearchResultProto;
import com.google.android.icing.proto.SetSchemaResultProto;
import com.google.android.icing.proto.StatusProto;
import com.google.android.icing.proto.StorageInfoResultProto;
import com.google.android.icing.proto.SuggestionResponse;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Contains utility methods for IcingSearchEngine to convert byte arrays to the corresponding
 * protos.
 *
 * <p>It is also being used by AppSearch dynamite 0p client APIs to convert byte arrays to the
 * protos.
 */
public final class IcingSearchEngineUtils {
  private static final String TAG = "IcingSearchEngineUtils";
  private static final ExtensionRegistryLite EXTENSION_REGISTRY_LITE =
      ExtensionRegistryLite.getEmptyRegistry();

  private IcingSearchEngineUtils() {}

  // TODO(b/240333360) Check to see if we can use one template function to replace those
  @NonNull
  public static InitializeResultProto byteArrayToInitializeResultProto(
      @Nullable byte[] initializeResultBytes) {
    if (initializeResultBytes == null) {
      Log.e(TAG, "Received null InitializeResult from native.");
      return InitializeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return InitializeResultProto.parseFrom(initializeResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing InitializeResultProto.", e);
      return InitializeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static SetSchemaResultProto byteArrayToSetSchemaResultProto(
      @Nullable byte[] setSchemaResultBytes) {
    if (setSchemaResultBytes == null) {
      Log.e(TAG, "Received null SetSchemaResultProto from native.");
      return SetSchemaResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return SetSchemaResultProto.parseFrom(setSchemaResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing SetSchemaResultProto.", e);
      return SetSchemaResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static GetSchemaResultProto byteArrayToGetSchemaResultProto(
      @Nullable byte[] getSchemaResultBytes) {
    if (getSchemaResultBytes == null) {
      Log.e(TAG, "Received null GetSchemaResultProto from native.");
      return GetSchemaResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return GetSchemaResultProto.parseFrom(getSchemaResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetSchemaResultProto.", e);
      return GetSchemaResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static GetSchemaTypeResultProto byteArrayToGetSchemaTypeResultProto(
      @Nullable byte[] getSchemaTypeResultBytes) {
    if (getSchemaTypeResultBytes == null) {
      Log.e(TAG, "Received null GetSchemaTypeResultProto from native.");
      return GetSchemaTypeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return GetSchemaTypeResultProto.parseFrom(getSchemaTypeResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetSchemaTypeResultProto.", e);
      return GetSchemaTypeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static PutResultProto byteArrayToPutResultProto(@Nullable byte[] putResultBytes) {
    if (putResultBytes == null) {
      Log.e(TAG, "Received null PutResultProto from native.");
      return PutResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return PutResultProto.parseFrom(putResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing PutResultProto.", e);
      return PutResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static GetResultProto byteArrayToGetResultProto(@Nullable byte[] getResultBytes) {
    if (getResultBytes == null) {
      Log.e(TAG, "Received null GetResultProto from native.");
      return GetResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return GetResultProto.parseFrom(getResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetResultProto.", e);
      return GetResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static ReportUsageResultProto byteArrayToReportUsageResultProto(
      @Nullable byte[] reportUsageResultBytes) {
    if (reportUsageResultBytes == null) {
      Log.e(TAG, "Received null ReportUsageResultProto from native.");
      return ReportUsageResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return ReportUsageResultProto.parseFrom(reportUsageResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing ReportUsageResultProto.", e);
      return ReportUsageResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static GetAllNamespacesResultProto byteArrayToGetAllNamespacesResultProto(
      @Nullable byte[] getAllNamespacesResultBytes) {
    if (getAllNamespacesResultBytes == null) {
      Log.e(TAG, "Received null GetAllNamespacesResultProto from native.");
      return GetAllNamespacesResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return GetAllNamespacesResultProto.parseFrom(
          getAllNamespacesResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetAllNamespacesResultProto.", e);
      return GetAllNamespacesResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static SearchResultProto byteArrayToSearchResultProto(@Nullable byte[] searchResultBytes) {
    if (searchResultBytes == null) {
      Log.e(TAG, "Received null SearchResultProto from native.");
      return SearchResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      SearchResultProto.Builder searchResultProtoBuilder =
          SearchResultProto.newBuilder().mergeFrom(searchResultBytes, EXTENSION_REGISTRY_LITE);
      setNativeToJavaJniLatency(searchResultProtoBuilder);
      return searchResultProtoBuilder.build();
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing SearchResultProto.", e);
      return SearchResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  private static void setNativeToJavaJniLatency(
      SearchResultProto.Builder searchResultProtoBuilder) {
    int nativeToJavaLatencyMs =
        (int)
            (System.currentTimeMillis()
                - searchResultProtoBuilder.getQueryStats().getNativeToJavaStartTimestampMs());
    searchResultProtoBuilder.setQueryStats(
        searchResultProtoBuilder.getQueryStats().toBuilder()
            .setNativeToJavaJniLatencyMs(nativeToJavaLatencyMs));
  }

  @NonNull
  public static DeleteResultProto byteArrayToDeleteResultProto(@Nullable byte[] deleteResultBytes) {
    if (deleteResultBytes == null) {
      Log.e(TAG, "Received null DeleteResultProto from native.");
      return DeleteResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return DeleteResultProto.parseFrom(deleteResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing DeleteResultProto.", e);
      return DeleteResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static SuggestionResponse byteArrayToSuggestionResponse(
      @Nullable byte[] suggestionResponseBytes) {
    if (suggestionResponseBytes == null) {
      Log.e(TAG, "Received null suggestionResponseBytes from native.");
      return SuggestionResponse.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return SuggestionResponse.parseFrom(suggestionResponseBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing suggestionResponseBytes.", e);
      return SuggestionResponse.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static DeleteByNamespaceResultProto byteArrayToDeleteByNamespaceResultProto(
      @Nullable byte[] deleteByNamespaceResultBytes) {
    if (deleteByNamespaceResultBytes == null) {
      Log.e(TAG, "Received null DeleteByNamespaceResultProto from native.");
      return DeleteByNamespaceResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return DeleteByNamespaceResultProto.parseFrom(
          deleteByNamespaceResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing DeleteByNamespaceResultProto.", e);
      return DeleteByNamespaceResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static DeleteBySchemaTypeResultProto byteArrayToDeleteBySchemaTypeResultProto(
      @Nullable byte[] deleteBySchemaTypeResultBytes) {
    if (deleteBySchemaTypeResultBytes == null) {
      Log.e(TAG, "Received null DeleteBySchemaTypeResultProto from native.");
      return DeleteBySchemaTypeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return DeleteBySchemaTypeResultProto.parseFrom(
          deleteBySchemaTypeResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing DeleteBySchemaTypeResultProto.", e);
      return DeleteBySchemaTypeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static DeleteByQueryResultProto byteArrayToDeleteByQueryResultProto(
      @Nullable byte[] deleteResultBytes) {
    if (deleteResultBytes == null) {
      Log.e(TAG, "Received null DeleteResultProto from native.");
      return DeleteByQueryResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return DeleteByQueryResultProto.parseFrom(deleteResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing DeleteResultProto.", e);
      return DeleteByQueryResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static PersistToDiskResultProto byteArrayToPersistToDiskResultProto(
      @Nullable byte[] persistToDiskResultBytes) {
    if (persistToDiskResultBytes == null) {
      Log.e(TAG, "Received null PersistToDiskResultProto from native.");
      return PersistToDiskResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return PersistToDiskResultProto.parseFrom(persistToDiskResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing PersistToDiskResultProto.", e);
      return PersistToDiskResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static OptimizeResultProto byteArrayToOptimizeResultProto(
      @Nullable byte[] optimizeResultBytes) {
    if (optimizeResultBytes == null) {
      Log.e(TAG, "Received null OptimizeResultProto from native.");
      return OptimizeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return OptimizeResultProto.parseFrom(optimizeResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing OptimizeResultProto.", e);
      return OptimizeResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static GetOptimizeInfoResultProto byteArrayToGetOptimizeInfoResultProto(
      @Nullable byte[] getOptimizeInfoResultBytes) {
    if (getOptimizeInfoResultBytes == null) {
      Log.e(TAG, "Received null GetOptimizeInfoResultProto from native.");
      return GetOptimizeInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return GetOptimizeInfoResultProto.parseFrom(
          getOptimizeInfoResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetOptimizeInfoResultProto.", e);
      return GetOptimizeInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static StorageInfoResultProto byteArrayToStorageInfoResultProto(
      @Nullable byte[] storageInfoResultProtoBytes) {
    if (storageInfoResultProtoBytes == null) {
      Log.e(TAG, "Received null StorageInfoResultProto from native.");
      return StorageInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return StorageInfoResultProto.parseFrom(storageInfoResultProtoBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing GetOptimizeInfoResultProto.", e);
      return StorageInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static DebugInfoResultProto byteArrayToDebugInfoResultProto(
      @Nullable byte[] debugInfoResultProtoBytes) {
    if (debugInfoResultProtoBytes == null) {
      Log.e(TAG, "Received null DebugInfoResultProto from native.");
      return DebugInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return DebugInfoResultProto.parseFrom(debugInfoResultProtoBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing DebugInfoResultProto.", e);
      return DebugInfoResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }

  @NonNull
  public static ResetResultProto byteArrayToResetResultProto(@Nullable byte[] resetResultBytes) {
    if (resetResultBytes == null) {
      Log.e(TAG, "Received null ResetResultProto from native.");
      return ResetResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }

    try {
      return ResetResultProto.parseFrom(resetResultBytes, EXTENSION_REGISTRY_LITE);
    } catch (InvalidProtocolBufferException e) {
      Log.e(TAG, "Error parsing ResetResultProto.", e);
      return ResetResultProto.newBuilder()
          .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.INTERNAL))
          .build();
    }
  }
}

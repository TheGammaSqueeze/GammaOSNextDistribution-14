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

package com.android.textclassifier.downloader;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.DatabaseView;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import androidx.room.Transaction;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Database storing info about models downloaded by model downloader */
@Database(
    entities = {
      DownloadedModelDatabase.Model.class,
      DownloadedModelDatabase.Manifest.class,
      DownloadedModelDatabase.ManifestModelCrossRef.class,
      DownloadedModelDatabase.ManifestEnrollment.class
    },
    views = {DownloadedModelDatabase.ModelView.class},
    version = 1,
    exportSchema = true)
abstract class DownloadedModelDatabase extends RoomDatabase {
  public static final String TAG = "DownloadedModelDatabase";

  /** Rpresents a downloaded model file. */
  @AutoValue
  @Entity(
      tableName = "model",
      primaryKeys = {"model_url"})
  abstract static class Model {
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "model_url")
    @NonNull
    public abstract String getModelUrl();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "model_path")
    @NonNull
    public abstract String getModelPath();

    public static Model create(String modelUrl, String modelPath) {
      return new AutoValue_DownloadedModelDatabase_Model(modelUrl, modelPath);
    }
  }

  /** Rpresents a manifest we processed. */
  @AutoValue
  @Entity(
      tableName = "manifest",
      primaryKeys = {"manifest_url"})
  abstract static class Manifest {
    // TODO(licha): Consider using Enum here
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATUS_UNKNOWN, STATUS_FAILED, STATUS_SUCCEEDED})
    @interface StatusDef {}

    public static final int STATUS_UNKNOWN = 0;
    /** Failed to download this manifest. Could be retried in the future. */
    public static final int STATUS_FAILED = 1;
    /** Downloaded this manifest successfully and it's currently in storage. */
    public static final int STATUS_SUCCEEDED = 2;

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "manifest_url")
    @NonNull
    public abstract String getManifestUrl();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "status")
    @StatusDef
    public abstract int getStatus();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "failure_counts")
    public abstract int getFailureCounts();

    public static Manifest create(String manifestUrl, @StatusDef int status, int failureCounts) {
      return new AutoValue_DownloadedModelDatabase_Manifest(manifestUrl, status, failureCounts);
    }
  }

  /**
   * Represents the relationship between manfiests and downloaded models.
   *
   * <p>A manifest can include multiple models, a model can also be included in multiple manifests.
   * In different manifests, a model may have different configurations (e.g. primary model in
   * manfiest A but dark model in B).
   */
  @AutoValue
  @Entity(
      tableName = "manifest_model_cross_ref",
      primaryKeys = {"manifest_url", "model_url"},
      foreignKeys = {
        @ForeignKey(
            entity = Manifest.class,
            parentColumns = "manifest_url",
            childColumns = "manifest_url",
            onDelete = ForeignKey.CASCADE),
        @ForeignKey(
            entity = Model.class,
            parentColumns = "model_url",
            childColumns = "model_url",
            onDelete = ForeignKey.CASCADE),
      },
      indices = {
        @Index(value = {"manifest_url"}),
        @Index(value = {"model_url"}),
      })
  abstract static class ManifestModelCrossRef {
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "manifest_url")
    @NonNull
    public abstract String getManifestUrl();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "model_url")
    @NonNull
    public abstract String getModelUrl();

    public static ManifestModelCrossRef create(String manifestUrl, String modelUrl) {
      return new AutoValue_DownloadedModelDatabase_ManifestModelCrossRef(manifestUrl, modelUrl);
    }
  }

  /**
   * Represents the relationship between user scenarios and manifests.
   *
   * <p>For each unique user scenario (i.e. modelType + localTag), we store the manifest we should
   * use. The same manifest can be used for different scenarios.
   */
  @AutoValue
  @Entity(
      tableName = "manifest_enrollment",
      primaryKeys = {"model_type", "locale_tag"},
      foreignKeys = {
        @ForeignKey(
            entity = Manifest.class,
            parentColumns = "manifest_url",
            childColumns = "manifest_url",
            onDelete = ForeignKey.CASCADE)
      },
      indices = {@Index(value = {"manifest_url"})})
  abstract static class ManifestEnrollment {
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "model_type")
    @NonNull
    @ModelTypeDef
    public abstract String getModelType();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "locale_tag")
    @NonNull
    public abstract String getLocaleTag();

    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "manifest_url")
    @NonNull
    public abstract String getManifestUrl();

    public static ManifestEnrollment create(
        @ModelTypeDef String modelType, String localeTag, String manifestUrl) {
      return new AutoValue_DownloadedModelDatabase_ManifestEnrollment(
          modelType, localeTag, manifestUrl);
    }
  }

  /** Represents the mapping from manfiest enrollments to models. */
  @AutoValue
  @DatabaseView(
      value =
          "SELECT manifest_enrollment.*, model.* "
              + "FROM manifest_enrollment "
              + "INNER JOIN manifest_model_cross_ref "
              + "ON manifest_enrollment.manifest_url = manifest_model_cross_ref.manifest_url "
              + "INNER JOIN model "
              + "ON manifest_model_cross_ref.model_url = model.model_url",
      viewName = "model_view")
  abstract static class ModelView {
    @AutoValue.CopyAnnotations
    @Embedded
    @NonNull
    public abstract ManifestEnrollment getManifestEnrollment();

    @AutoValue.CopyAnnotations
    @Embedded
    @NonNull
    public abstract Model getModel();

    public static ModelView create(ManifestEnrollment manifestEnrollment, Model model) {
      return new AutoValue_DownloadedModelDatabase_ModelView(manifestEnrollment, model);
    }
  }

  @Dao
  abstract static class DownloadedModelDatabaseDao {
    // Full table scan
    @Query("SELECT * FROM model")
    abstract List<Model> queryAllModels();

    @Query("SELECT * FROM manifest")
    abstract List<Manifest> queryAllManifests();

    @Query("SELECT * FROM manifest_model_cross_ref")
    abstract List<ManifestModelCrossRef> queryAllManifestModelCrossRefs();

    @Query("SELECT * FROM manifest_enrollment")
    abstract List<ManifestEnrollment> queryAllManifestEnrollments();

    @Query("SELECT * FROM model_view")
    abstract List<ModelView> queryAllModelViews();

    // Single table query
    @Query("SELECT * FROM model WHERE model_url = :modelUrl")
    abstract List<Model> queryModelWithModelUrl(String modelUrl);

    @Query("SELECT * FROM manifest WHERE manifest_url = :manifestUrl")
    abstract List<Manifest> queryManifestWithManifestUrl(String manifestUrl);

    @Query(
        "SELECT * FROM manifest_enrollment WHERE model_type = :modelType "
            + "AND locale_tag = :localeTag")
    abstract List<ManifestEnrollment> queryManifestEnrollmentWithModelTypeAndLocaleTag(
        @ModelTypeDef String modelType, String localeTag);

    // Helpers for clean up
    @Query(
        "SELECT manifest.* FROM manifest "
            + "LEFT JOIN model_view "
            + "ON manifest.manifest_url = model_view.manifest_url "
            + "WHERE model_view.manifest_url IS NULL "
            + "AND manifest.status = 2")
    abstract List<Manifest> queryUnusedManifests();

    @Query(
        "SELECT * FROM manifest WHERE manifest.status = 1 "
            + "AND manifest.manifest_url NOT IN (:manifestUrlsToKeep)")
    abstract List<Manifest> queryUnusedManifestFailureRecords(List<String> manifestUrlsToKeep);

    @Query(
        "SELECT model.* FROM model LEFT JOIN model_view "
            + "ON model.model_url = model_view.model_url "
            + "WHERE model_view.model_url IS NULL")
    abstract List<Model> queryUnusedModels();

    // Insertion
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(Model model);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(Manifest manifest);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(ManifestModelCrossRef manifestModelCrossRef);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(ManifestEnrollment manifestEnrollment);

    @Transaction
    void insertManifestAndModelCrossRef(String manifestUrl, String modelUrl) {
      insert(Manifest.create(manifestUrl, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0));
      insert(ManifestModelCrossRef.create(manifestUrl, modelUrl));
    }

    @Transaction
    void increaseManifestFailureCounts(String manifestUrl) {
      List<Manifest> manifests = queryManifestWithManifestUrl(manifestUrl);
      if (manifests.isEmpty()) {
        insert(Manifest.create(manifestUrl, Manifest.STATUS_FAILED, /* failureCounts= */ 1));
      } else {
        Manifest prevManifest = Iterables.getOnlyElement(manifests);
        insert(
            Manifest.create(
                manifestUrl, Manifest.STATUS_FAILED, prevManifest.getFailureCounts() + 1));
      }
    }

    // Deletion
    @Delete
    abstract void deleteModels(List<Model> models);

    @Delete
    abstract void deleteManifests(List<Manifest> manifests);

    @Delete
    abstract void deleteManifestModelCrossRefs(List<ManifestModelCrossRef> manifestModelCrossRefs);

    @Delete
    abstract void deleteManifestEnrollments(List<ManifestEnrollment> manifestEnrollments);

    @Transaction
    void deleteUnusedManifestsAndModels() {
      // Because Manifest table is the parent table of ManifestModelCrossRef table, related cross
      // ref row in that table will be deleted automatically
      deleteManifests(queryUnusedManifests());
      deleteModels(queryUnusedModels());
    }

    @Transaction
    void deleteUnusedManifestFailureRecords(List<String> manifestUrlsToKeep) {
      deleteManifests(queryUnusedManifestFailureRecords(manifestUrlsToKeep));
    }
  }

  abstract DownloadedModelDatabaseDao dao();

  /** Dump the database for debugging. */
  void dump(IndentingPrintWriter printWriter, ExecutorService executorService) {
    printWriter.println("DownloadedModelDatabase");
    printWriter.increaseIndent();
    printWriter.println("Model Table:");
    printWriter.increaseIndent();
    List<Model> models = dao().queryAllModels();
    for (Model model : models) {
      printWriter.println(model.toString());
    }
    printWriter.decreaseIndent();
    printWriter.println("Manifest Table:");
    printWriter.increaseIndent();
    List<Manifest> manifests = dao().queryAllManifests();
    for (Manifest manifest : manifests) {
      printWriter.println(manifest.toString());
    }
    printWriter.decreaseIndent();
    printWriter.println("ManifestModelCrossRef Table:");
    printWriter.increaseIndent();
    List<ManifestModelCrossRef> manifestModelCrossRefs = dao().queryAllManifestModelCrossRefs();
    for (ManifestModelCrossRef manifestModelCrossRef : manifestModelCrossRefs) {
      printWriter.println(manifestModelCrossRef.toString());
    }
    printWriter.decreaseIndent();
    printWriter.println("ManifestEnrollment Table:");
    printWriter.increaseIndent();
    List<ManifestEnrollment> manifestEnrollments = dao().queryAllManifestEnrollments();
    for (ManifestEnrollment manifestEnrollment : manifestEnrollments) {
      printWriter.println(manifestEnrollment.toString());
    }
    printWriter.decreaseIndent();
    printWriter.decreaseIndent();
  }
}

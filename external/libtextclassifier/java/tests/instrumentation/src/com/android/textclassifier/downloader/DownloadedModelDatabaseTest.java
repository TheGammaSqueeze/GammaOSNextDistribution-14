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

import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.expectThrows;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.textclassifier.common.ModelType;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Manifest;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestEnrollment;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ManifestModelCrossRef;
import com.android.textclassifier.downloader.DownloadedModelDatabase.Model;
import com.android.textclassifier.downloader.DownloadedModelDatabase.ModelView;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DownloadedModelDatabaseTest {
  private static final String MODEL_URL = "https://model.url";
  private static final String MODEL_URL_2 = "https://model2.url";
  private static final String MODEL_PATH = "/data/test.model";
  private static final String MODEL_PATH_2 = "/data/test.model2";
  private static final String MANIFEST_URL = "https://manifest.url";
  private static final String MANIFEST_URL_2 = "https://manifest2.url";
  private static final String MODEL_TYPE = ModelType.ANNOTATOR;
  private static final String MODEL_TYPE_2 = ModelType.ACTIONS_SUGGESTIONS;
  private static final String LOCALE_TAG = "zh";

  private DownloadedModelDatabase db;

  @Before
  public void createDb() {
    Context context = ApplicationProvider.getApplicationContext();
    db = Room.inMemoryDatabaseBuilder(context, DownloadedModelDatabase.class).build();
  }

  @After
  public void closeDb() throws IOException {
    db.close();
  }

  @Test
  public void insertModelAndRead() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    List<Model> models = db.dao().queryAllModels();
    assertThat(models).containsExactly(model);
  }

  @Test
  public void insertModelAndDelete() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    db.dao().deleteModels(ImmutableList.of(model));
    List<Model> models = db.dao().queryAllModels();
    assertThat(models).isEmpty();
  }

  @Test
  public void insertManifestAndRead() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    List<Manifest> manifests = db.dao().queryAllManifests();
    assertThat(manifests).containsExactly(manifest);
  }

  @Test
  public void insertManifestAndDelete() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    db.dao().deleteManifests(ImmutableList.of(manifest));
    List<Manifest> manifests = db.dao().queryAllManifests();
    assertThat(manifests).isEmpty();
  }

  @Test
  public void insertManifestModelCrossRefAndRead() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    db.dao().insert(manifestModelCrossRef);
    List<ManifestModelCrossRef> manifestModelCrossRefs = db.dao().queryAllManifestModelCrossRefs();
    assertThat(manifestModelCrossRefs).containsExactly(manifestModelCrossRef);
  }

  @Test
  public void insertManifestModelCrossRefAndDelete() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    db.dao().insert(manifestModelCrossRef);
    db.dao().deleteManifestModelCrossRefs(ImmutableList.of(manifestModelCrossRef));
    List<ManifestModelCrossRef> manifestModelCrossRefs = db.dao().queryAllManifestModelCrossRefs();
    assertThat(manifestModelCrossRefs).isEmpty();
  }

  @Test
  public void insertManifestModelCrossRefAndDeleteManifest() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    db.dao().insert(manifestModelCrossRef);
    db.dao().deleteManifests(ImmutableList.of(manifest)); // ON CASCADE
    List<ManifestModelCrossRef> manifestModelCrossRefs = db.dao().queryAllManifestModelCrossRefs();
    assertThat(manifestModelCrossRefs).isEmpty();
  }

  @Test
  public void insertManifestModelCrossRefAndDeleteModel() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    db.dao().insert(manifestModelCrossRef);
    db.dao().deleteModels(ImmutableList.of(model)); // ON CASCADE
    List<ManifestModelCrossRef> manifestModelCrossRefs = db.dao().queryAllManifestModelCrossRefs();
    assertThat(manifestModelCrossRefs).isEmpty();
  }

  @Test
  public void insertManifestModelCrossRefWithoutManifest() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    expectThrows(Throwable.class, () -> db.dao().insert(manifestModelCrossRef));
  }

  @Test
  public void insertManifestModelCrossRefWithoutModel() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    expectThrows(Throwable.class, () -> db.dao().insert(manifestModelCrossRef));
  }

  @Test
  public void insertManifestEnrollmentAndRead() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);
    List<ManifestEnrollment> manifestEnrollments = db.dao().queryAllManifestEnrollments();
    assertThat(manifestEnrollments).containsExactly(manifestEnrollment);
  }

  @Test
  public void insertManifestEnrollmentAndDelete() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);
    db.dao().deleteManifestEnrollments(ImmutableList.of(manifestEnrollment));
    List<ManifestEnrollment> manifestEnrollments = db.dao().queryAllManifestEnrollments();
    assertThat(manifestEnrollments).isEmpty();
  }

  @Test
  public void insertManifestEnrollmentAndDeleteManifest() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);
    db.dao().deleteManifests(ImmutableList.of(manifest));
    List<ManifestEnrollment> manifestEnrollments = db.dao().queryAllManifestEnrollments();
    assertThat(manifestEnrollments).isEmpty();
  }

  @Test
  public void insertManifestEnrollmentWithoutManifest() throws Exception {
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    expectThrows(Throwable.class, () -> db.dao().insert(manifestEnrollment));
  }

  @Test
  public void insertModelViewAndRead() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    ManifestModelCrossRef manifestModelCrossRef =
        ManifestModelCrossRef.create(MANIFEST_URL, MODEL_URL);
    db.dao().insert(manifestModelCrossRef);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);

    List<ModelView> modelViews = db.dao().queryAllModelViews();
    ModelView modelView = Iterables.getOnlyElement(modelViews);
    assertThat(modelView.getManifestEnrollment()).isEqualTo(manifestEnrollment);
    assertThat(modelView.getModel()).isEqualTo(model);
  }

  @Test
  public void queryModelWithModelUrl() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Model model2 = Model.create(MODEL_URL_2, MODEL_PATH_2);
    db.dao().insert(model2);

    assertThat(db.dao().queryModelWithModelUrl(MODEL_URL)).containsExactly(model);
    assertThat(db.dao().queryModelWithModelUrl(MODEL_URL_2)).containsExactly(model2);
  }

  @Test
  public void queryManifestWithManifestUrl() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    Manifest manifest2 =
        Manifest.create(MANIFEST_URL_2, Manifest.STATUS_FAILED, /* failureCounts= */ 1);
    db.dao().insert(manifest2);

    assertThat(db.dao().queryManifestWithManifestUrl(MANIFEST_URL)).containsExactly(manifest);
    assertThat(db.dao().queryManifestWithManifestUrl(MANIFEST_URL_2)).containsExactly(manifest2);
  }

  @Test
  public void queryManifestEnrollmentWithModelTypeAndLocaleTag() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    Manifest manifest2 =
        Manifest.create(MANIFEST_URL_2, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest2);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);
    ManifestEnrollment manifestEnrollment2 =
        ManifestEnrollment.create(MODEL_TYPE_2, LOCALE_TAG, MANIFEST_URL_2);
    db.dao().insert(manifestEnrollment2);

    assertThat(db.dao().queryManifestEnrollmentWithModelTypeAndLocaleTag(MODEL_TYPE, LOCALE_TAG))
        .containsExactly(manifestEnrollment);
    assertThat(db.dao().queryManifestEnrollmentWithModelTypeAndLocaleTag(MODEL_TYPE_2, LOCALE_TAG))
        .containsExactly(manifestEnrollment2);
  }

  @Test
  public void insertManifestAndModelCrossRef() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL, MODEL_URL);

    assertThat(db.dao().queryAllModels()).containsExactly(model);
    assertThat(db.dao().queryAllManifests()).containsExactly(manifest);
  }

  @Test
  public void increaseManifestFailureCounts() throws Exception {
    db.dao().increaseManifestFailureCounts(MODEL_URL);
    Manifest manifest = Iterables.getOnlyElement(db.dao().queryManifestWithManifestUrl(MODEL_URL));
    assertThat(manifest.getStatus()).isEqualTo(Manifest.STATUS_FAILED);
    assertThat(manifest.getFailureCounts()).isEqualTo(1);
    db.dao().increaseManifestFailureCounts(MODEL_URL);
    manifest = Iterables.getOnlyElement(db.dao().queryManifestWithManifestUrl(MODEL_URL));
    assertThat(manifest.getStatus()).isEqualTo(Manifest.STATUS_FAILED);
    assertThat(manifest.getFailureCounts()).isEqualTo(2);
  }

  @Test
  public void deleteUnusedManifestsAndModels_unusedManifestAndUnusedModel() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Model model2 = Model.create(MODEL_URL_2, MODEL_PATH_2);
    db.dao().insert(model2);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL, MODEL_URL);
    Manifest manifest2 =
        Manifest.create(MANIFEST_URL_2, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest2);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL_2, MODEL_URL_2);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);

    db.dao().deleteUnusedManifestsAndModels();
    assertThat(db.dao().queryAllManifests()).containsExactly(manifest);
    assertThat(db.dao().queryAllModels()).containsExactly(model);
  }

  @Test
  public void deleteUnusedManifestsAndModels_unusedManifestAndSharedModel() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL, MODEL_URL);
    Manifest manifest2 =
        Manifest.create(MANIFEST_URL_2, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest2);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL_2, MODEL_URL);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);

    db.dao().deleteUnusedManifestsAndModels();
    assertThat(db.dao().queryAllManifests()).containsExactly(manifest);
    assertThat(db.dao().queryAllModels()).containsExactly(model);
  }

  @Test
  public void deleteUnusedManifestsAndModels_failedManifest() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_FAILED, /* failureCounts= */ 1);
    db.dao().insert(manifest);

    db.dao().deleteUnusedManifestsAndModels();
    assertThat(db.dao().queryAllManifests()).containsExactly(manifest);
  }

  @Test
  public void deleteUnusedManifestsAndModels_unusedModels() throws Exception {
    Model model = Model.create(MODEL_URL, MODEL_PATH);
    db.dao().insert(model);
    Model model2 = Model.create(MODEL_URL_2, MODEL_PATH_2);
    db.dao().insert(model2);
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_SUCCEEDED, /* failureCounts= */ 0);
    db.dao().insert(manifest);
    db.dao().insertManifestAndModelCrossRef(MANIFEST_URL, MODEL_URL);
    ManifestEnrollment manifestEnrollment =
        ManifestEnrollment.create(MODEL_TYPE, LOCALE_TAG, MANIFEST_URL);
    db.dao().insert(manifestEnrollment);

    db.dao().deleteUnusedManifestsAndModels();
    assertThat(db.dao().queryAllModels()).containsExactly(model);
  }

  @Test
  public void deleteUnusedManifestFailureRecords() throws Exception {
    Manifest manifest =
        Manifest.create(MANIFEST_URL, Manifest.STATUS_FAILED, /* failureCounts= */ 1);
    db.dao().insert(manifest);
    Manifest manifest2 =
        Manifest.create(MANIFEST_URL_2, Manifest.STATUS_FAILED, /* failureCounts= */ 1);
    db.dao().insert(manifest2);

    db.dao().deleteUnusedManifestFailureRecords(ImmutableList.of(MANIFEST_URL));
    assertThat(db.dao().queryAllManifests()).containsExactly(manifest);
  }
}

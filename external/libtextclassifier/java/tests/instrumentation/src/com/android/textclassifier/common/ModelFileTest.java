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

package com.android.textclassifier.common;

import static com.android.textclassifier.common.ModelFile.LANGUAGE_INDEPENDENT;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.android.textclassifier.common.ModelType.ModelTypeDef;
import com.android.textclassifier.common.logging.ResultIdUtils.ModelInfo;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ModelFileTest {
  @ModelTypeDef private static final String MODEL_TYPE = ModelType.ANNOTATOR;

  @Test
  public void modelFileEquals() {
    ModelFile modelA =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 1, "ja", /* isAsset= */ false);
    ModelFile modelB =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 1, "ja", /* isAsset= */ false);

    assertThat(modelA).isEqualTo(modelB);
  }

  @Test
  public void modelFile_different() {
    ModelFile modelA =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 1, "ja", /* isAsset= */ false);
    ModelFile modelB =
        new ModelFile(MODEL_TYPE, "/path/b", /* version= */ 1, "ja", /* isAsset= */ false);

    assertThat(modelA).isNotEqualTo(modelB);
  }

  @Test
  public void modelFile_isPreferredTo_languageDependentIsBetter() {
    ModelFile modelA =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 1, "ja", /* isAsset= */ false);
    ModelFile modelB =
        new ModelFile(
            MODEL_TYPE, "/path/b", /* version= */ 2, LANGUAGE_INDEPENDENT, /* isAsset= */ false);

    assertThat(modelA.isPreferredTo(modelB)).isTrue();
  }

  @Test
  public void modelFile_isPreferredTo_version() {
    ModelFile modelA =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 2, "ja", /* isAsset= */ false);
    ModelFile modelB =
        new ModelFile(MODEL_TYPE, "/path/b", /* version= */ 1, "ja", /* isAsset= */ false);

    assertThat(modelA.isPreferredTo(modelB)).isTrue();
  }

  @Test
  public void modelFile_toModelInfo() {
    ModelFile modelFile =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 2, "ja", /* isAsset= */ false);

    ModelInfo modelInfo = modelFile.toModelInfo();

    assertThat(modelInfo.toModelName()).isEqualTo("ja_v2");
  }

  @Test
  public void modelFile_toModelInfo_universal() {
    ModelFile modelFile =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 2, "*", /* isAsset= */ false);

    ModelInfo modelInfo = modelFile.toModelInfo();

    assertThat(modelInfo.toModelName()).isEqualTo("*_v2");
  }

  @Test
  public void modelFile_toModelInfos() {
    ModelFile englishModelFile =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 1, "en", /* isAsset= */ false);
    ModelFile japaneseModelFile =
        new ModelFile(MODEL_TYPE, "/path/a", /* version= */ 2, "ja", /* isAsset= */ false);

    ImmutableList<Optional<ModelInfo>> modelInfos =
        ModelFile.toModelInfos(Optional.of(englishModelFile), Optional.of(japaneseModelFile));

    assertThat(
            modelInfos.stream()
                .map(modelFile -> modelFile.transform(ModelInfo::toModelName).or(""))
                .collect(Collectors.toList()))
        .containsExactly("en_v1", "ja_v2")
        .inOrder();
  }
}

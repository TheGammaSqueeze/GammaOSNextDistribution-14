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

import com.android.textclassifier.common.ModelFile;
import com.android.textclassifier.common.ModelType;
import java.io.File;
import java.io.IOException;

/** Utils to access test data files. */
public final class TestDataUtils {
  private static final String TEST_ANNOTATOR_MODEL_PATH = "testdata/annotator.model";
  private static final String TEST_ACTIONS_MODEL_PATH = "testdata/actions.model";
  private static final String TEST_LANGID_MODEL_PATH = "testdata/langid.model";

  /** Returns the root folder that contains the test data. */
  private static File getTestDataFolder() {
    return new File("/data/local/tmp/TextClassifierServiceTest/");
  }

  public static File getTestAnnotatorModelFile() {
    return new File(getTestDataFolder(), TEST_ANNOTATOR_MODEL_PATH);
  }

  public static ModelFile getTestAnnotatorModelFileWrapped() throws IOException {
    return ModelFile.createFromRegularFile(getTestAnnotatorModelFile(), ModelType.ANNOTATOR);
  }

  public static File getTestActionsModelFile() {
    return new File(getTestDataFolder(), TEST_ACTIONS_MODEL_PATH);
  }

  public static ModelFile getTestActionsModelFileWrapped() throws IOException {
    return ModelFile.createFromRegularFile(
        getTestActionsModelFile(), ModelType.ACTIONS_SUGGESTIONS);
  }

  public static File getLangIdModelFile() {
    return new File(getTestDataFolder(), TEST_LANGID_MODEL_PATH);
  }

  public static ModelFile getLangIdModelFileWrapped() throws IOException {
    return ModelFile.createFromRegularFile(getLangIdModelFile(), ModelType.LANG_ID);
  }

  private TestDataUtils() {}
}

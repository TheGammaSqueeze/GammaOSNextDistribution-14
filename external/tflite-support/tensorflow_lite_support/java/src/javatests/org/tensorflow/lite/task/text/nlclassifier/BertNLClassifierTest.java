/* Copyright 2022 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.task.text.nlclassifier;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.TestUtils;

/** Test for {@link BertNLClassifier}. */
public class BertNLClassifierTest {
    private static final String MODEL_FILE = "bert_nl_classifier.tflite";
    // A classifier model with dynamic input tensors. Provided by the Android Rubidium team.
    private static final String DYNAMIC_INPUT_MODEL_FILE = "rb_v4_model.tflite";

    Category findCategoryWithLabel(List<Category> list, String label) {
        return list.stream()
                .filter(category -> label.equals(category.getLabel()))
                .findAny()
                .orElse(null);
    }

    @Test
    public void createFromPath_verifyResults() throws IOException {
        verifyResults(
                BertNLClassifier.createFromFile(ApplicationProvider.getApplicationContext(),
                        MODEL_FILE));
    }

    @Test
    public void createFromFile_verifyResults() throws IOException {
        verifyResults(
                BertNLClassifier.createFromFile(
                        TestUtils.loadFile(ApplicationProvider.getApplicationContext(),
                                MODEL_FILE)));
    }

    @Test
    public void classify_succeedsWithModelFile() throws IOException {
        verifyResults(
                BertNLClassifier.createFromFile(
                        ApplicationProvider.getApplicationContext(), MODEL_FILE));
    }

    @Test
    public void classify_succeedsWithModelBuffer() throws IOException {
        verifyResults(
                BertNLClassifier.createFromBuffer(
                        TestUtils.loadToDirectByteBuffer(
                                ApplicationProvider.getApplicationContext(), MODEL_FILE)));
    }

    @Test
    public void classify_succeedsWithDynamicInputModelBuffer() throws IOException {
        verifyDynamicInputResults(
                BertNLClassifier.createFromBuffer(
                        TestUtils.loadToDirectByteBuffer(
                                ApplicationProvider.getApplicationContext(),
                                DYNAMIC_INPUT_MODEL_FILE)));
    }

    @Test
    public void getModelVersion_succeedsWithVersionInMetadata() throws IOException {
        BertNLClassifier classifier = BertNLClassifier.createFromFile(
                ApplicationProvider.getApplicationContext(), MODEL_FILE);

        assertThat(classifier.getModelVersion()).isEqualTo("v1");
    }

    @Test
    public void getModelVersion_succeedsWithDynamicInputModelVersion() throws IOException {
        BertNLClassifier classifier = BertNLClassifier.createFromFile(
                ApplicationProvider.getApplicationContext(), DYNAMIC_INPUT_MODEL_FILE);

        assertThat(classifier.getModelVersion()).isEqualTo("4");
    }

    @Test
    public void getLabelsVersion_succeedsWithNoVersionInMetadata() throws IOException {
        BertNLClassifier classifier = BertNLClassifier.createFromFile(
                ApplicationProvider.getApplicationContext(), MODEL_FILE);

        assertThat(classifier.getLabelsVersion()).isEqualTo("NO_VERSION_INFO");
    }

    @Test
    public void getLabelsVersion_succeedsWithDynamicInputLabelsVersion() throws IOException {
        BertNLClassifier classifier = BertNLClassifier.createFromFile(
                ApplicationProvider.getApplicationContext(), DYNAMIC_INPUT_MODEL_FILE);

        assertThat(classifier.getLabelsVersion()).isEqualTo("2");
    }

    private void verifyResults(BertNLClassifier classifier) {
        List<Category> negativeResults = classifier.classify("unflinchingly bleak and desperate");
        assertThat(findCategoryWithLabel(negativeResults, "negative").getScore())
                .isGreaterThan(findCategoryWithLabel(negativeResults, "positive").getScore());

        List<Category> positiveResults =
                classifier.classify("it's a charming and often affecting journey");
        assertThat(findCategoryWithLabel(positiveResults, "positive").getScore())
                .isGreaterThan(findCategoryWithLabel(positiveResults, "negative").getScore());
    }

    private void verifyDynamicInputResults(BertNLClassifier classifier) {
        List<Category> topics = classifier.classify("FooBarBaz");
        assertThat(topics.size()).isEqualTo(446);
    }
}

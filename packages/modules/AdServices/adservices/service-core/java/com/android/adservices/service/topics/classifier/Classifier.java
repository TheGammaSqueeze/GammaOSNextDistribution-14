/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.topics.classifier;

import android.annotation.NonNull;

import com.android.adservices.data.topics.Topic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for all Topics Classifiers.
 */
public interface Classifier {

    /**
     * This method should return a map from the app to the list of its classification topics. If
     * there are no classifications for the app, an empty list of topics should be assigned for that
     * app.
     *
     * @param apps The set of apps
     * @return {@code appClassificationTopicsMap = Map<App, List<Topic>>}
     */
    @NonNull
    Map<String, List<Topic>> classify(@NonNull Set<String> apps);

    /**
     * This method should generate numberOfTopTopics of top topics followed by numberOfRandomTopics
     * random topics.
     *
     * <p>In the case we don't have enough topics to generate numberOfTopTopics of top topics, we
     * should pad them with random topics.
     *
     * <p>The result of this function is a list of numberOfTopTopics + numberOfRandomTopics topics.
     *
     * @param appTopics A map that represents the user's app and their topics
     * @param numberOfTopTopics The number of top Topics to be returned
     * @param numberOfRandomTopics The number of random Topics to be returned
     * @return A list of topics where Top Topics precede the random topics.
     */
    @NonNull
    List<Topic> getTopTopics(
            @NonNull Map<String, List<Topic>> appTopics,
            int numberOfTopTopics,
            int numberOfRandomTopics);
}

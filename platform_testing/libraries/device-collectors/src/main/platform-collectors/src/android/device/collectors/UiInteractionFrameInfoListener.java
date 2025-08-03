/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.device.collectors;

import android.os.Bundle;
import android.platform.test.annotations.ForJankMetrics;

import com.android.helpers.MetricUtility;
import com.android.helpers.UiInteractionFrameInfoHelper;
import com.android.internal.jank.InteractionJankMonitor;

import org.junit.runner.Description;
import org.junit.runner.Result;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A listener that captures jank for various system interactions.
 *
 * <p>Do NOT throw exception anywhere in this class. We don't want to halt the test when metrics
 * collection fails.
 */
public class UiInteractionFrameInfoListener extends BaseCollectionListener<StringBuilder> {
    private static final String TAG = UiInteractionFrameInfoListener.class.getSimpleName();

    public UiInteractionFrameInfoListener() {
        createHelperInstance(new UiInteractionFrameInfoHelper());
    }

    @Override
    public void onTestRunStart(DataRecord runData, Description description) {
        super.onTestRunStart(runData, description);
    }

    @Override
    public void onTestRunEnd(DataRecord runData, Result result) {
        super.onTestRunEnd(runData, result);
    }

    @Override
    protected Function<String, Boolean> getFilter(Description description) {
        return interactionTypeName -> {
            if (description == null) {
                return false;
            }

            ForJankMetrics annotation = description.getAnnotation(ForJankMetrics.class);
            if (annotation == null || annotation.value() == null) {
                return false;
            }

            // Although UiInteractionFrameInfoHelper uses interactionType (defined in Atoms.Proto),
            // the annotation @ForJankMetrics uses cujType (defined in InteractionJankMonitor).
            // We should use InteractionJankMonitor::getNameOfCuj to get the interactionTypeName.
            List<String> filters =
                    Arrays.stream(annotation.value())
                            .mapToObj(InteractionJankMonitor::getNameOfCuj)
                            .collect(Collectors.toList());
            return filters.size() > 0 && !filters.contains(interactionTypeName);
        };
    }

    @Override
    protected void collectMetrics(DataRecord data) {
        DataRecord tmpData = new DataRecord();
        super.collectMetrics(tmpData);
        if (tmpData.hasMetrics()) {
            Bundle bundle = tmpData.createBundleFromMetrics();
            for (String key : bundle.keySet()) {
                reduceMetrics(data, key, bundle.getString(key));
            }
        }
    }

    private void reduceMetrics(DataRecord data, String key, String value) {
        if (data == null || key.isEmpty() || value.isEmpty()) return;

        double result = 0;
        String[] tokens = value.split(MetricUtility.METRIC_SEPARATOR);
        for (String token : tokens) {
            if (key.endsWith(UiInteractionFrameInfoHelper.SUFFIX_MAX_FRAME_MS)) {
                result = Double.max(result, Double.parseDouble(token));
            } else {
                result += Double.parseDouble(token);
            }
        }
        data.addStringMetric(key, Double.toString(result));
    }
}

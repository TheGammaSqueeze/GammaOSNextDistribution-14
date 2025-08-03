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
package android.platform.test.rule;

import android.app.Instrumentation;
import android.device.collectors.BaseMetricListener;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;

/**
 * A rule that collects class-level metrics using a supplied list of metric collectors.
 *
 * <p>The metric collectors are passed in using the "class-metric-collectors" option, and the rule
 * works by invoking the correct test-level callbacks on them at the corresponding stages of the
 * test lifecycle. The metric collectors must be subclasses of {@link BaseMetricListener}, and can
 * be passed in by their fully qualified class name, or simple class name if they are under the
 * {@code android.device.collectors} package (but not subpackages).
 *
 * <p>Multiple metric collectors are supported as comma-separated values, The order they are
 * triggered follows this example: for {@code -e class-metric-collectors Collector1,Collector2}, the
 * evaluation order would be {@code Collector1#testStarted()}, {@code Collector2#testStarted()}, the
 * entire test class, {@code Collector1#testFinished()}, {@code Collector1#testFinished()}.
 *
 * <p>The above FIFO ordering of listeners can be changed to LIFO if the {@code
 * class-metric-collectors-fifo-order} option is set to {@code false}.
 *
 * <p>For {@code Microbenchmark}s, this rule can be dynamically injected either inside or outside
 * hardcoded rules (see {@code Microbenchmark})'s JavaDoc).
 *
 * <p>Note that metrics collected from this rule are reported as run metrics. Therefore, there is
 * the risk of metric key collision if a run contains multiple classes that report metrics under the
 * same key. At the moment, it's the responsibility of the metric collector to prevent collision
 * across test classes.
 *
 * <p>Exceptions from metric listeners are silently logged. This behavior is in accordance with the
 * approach taken by {@link BaseMetricListener}.
 */
public class ClassMetricRule extends TestMetricRule {
    @VisibleForTesting static final String METRIC_COLLECTORS_OPTION = "class-metric-collectors";
    @VisibleForTesting static final String FIFO_ORDER_OPTION = "class-metric-collectors-fifo-order";

    public ClassMetricRule() {
        this(InstrumentationRegistry.getArguments(), InstrumentationRegistry.getInstrumentation());
    }

    @VisibleForTesting
    ClassMetricRule(Bundle args, Instrumentation instrumentation) {
        super(
                args,
                instrumentation,
                METRIC_COLLECTORS_OPTION,
                FIFO_ORDER_OPTION,
                ClassMetricRule.class.getSimpleName());
        for (BaseMetricListener listener : mMetricListeners) {
            listener.setReportAsInstrumentationResults(true);
        }
    }
}

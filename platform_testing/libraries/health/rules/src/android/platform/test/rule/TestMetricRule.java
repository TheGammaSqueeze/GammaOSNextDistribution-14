/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.test.InstrumentationRegistry;

import com.google.common.collect.Lists;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A rule that collects test-level metrics using a supplied list of metric collectors.
 *
 * <p>The metric collectors are passed in using the "test-metric-collectors" option, and the rule
 * works by invoking the correct callbacks on them at the corresponding stages of the test
 * lifecycle. The metric collectors must be subclasses of {@link BaseMetricListener}, and can be
 * passed in by their fully qualified class name, or simple class name if they are under the {@code
 * android.device.collectors} package (but not subpackages).
 *
 * <p>Multiple metric collectors are supported as comma-separated values, The order they are
 * triggered follows this example: for {@code -e test-metric-collectors Collector1,Collector2}, the
 * evaluation order would be {@code Collector1#testStarted()}, {@code Collector2#testStarted()},
 * {@code @Test}, {@code Collector1#testFinished()}, {@code Collector1#testFinished()}.
 *
 * <p>The above FIFO ordering of listeners can be changed to LIFO if the {@code
 * test-metric-collectors-fifo-order} option is set to {@code false}.
 *
 * <p>For {@code Microbenchmark}s, this rule can be dynamically injected either inside or outside
 * hardcoded rules (see {@code Microbenchmark})'s JavaDoc).
 *
 * <p>Exceptions from metric listeners are silently logged. This behavior is in accordance with the
 * approach taken by {@link BaseMetricListener}.
 */
public class TestMetricRule extends TestWatcher {
    @VisibleForTesting static final String METRIC_COLLECTORS_OPTION = "test-metric-collectors";
    @VisibleForTesting static final String FIFO_ORDER_OPTION = "test-metric-collectors-fifo-order";
    @VisibleForTesting static final String METRIC_COLLECTORS_PACKAGE = "android.device.collectors";

    protected List<BaseMetricListener> mMetricListeners = new ArrayList<>();
    // Listeners invoked when finishing or failing a test. Will be a view on mMetricListeners.
    protected List<BaseMetricListener> mFinishingListeners;
    protected boolean mFifoOrder = true;
    private final String mLogTag;

    public TestMetricRule() {
        this(InstrumentationRegistry.getArguments());
    }

    @VisibleForTesting
    TestMetricRule(Bundle args) {
        this(
                args,
                InstrumentationRegistry.getInstrumentation(),
                METRIC_COLLECTORS_OPTION,
                FIFO_ORDER_OPTION,
                TestMetricRule.class.getSimpleName());
    }

    /**
     * A constructor that allows subclasses to change out various components used at initialization
     * time.
     */
    protected TestMetricRule(
            Bundle args,
            Instrumentation instrumentation,
            String collectorsOptionName,
            String fifoOrderOptionName,
            String logTag) {
        mLogTag = logTag;
        mFifoOrder =
                Boolean.parseBoolean(args.getString(fifoOrderOptionName, String.valueOf(true)));
        List<String> listenerNames =
                Arrays.asList(args.getString(collectorsOptionName, "").split(","));
        for (String listenerName : listenerNames) {
            if (listenerName.isEmpty()) {
                continue;
            }
            BaseMetricListener listener = null;
            // We could use a regex here, but this is simpler and should work just as well.
            if (listenerName.contains(".")) {
                Log.i(
                        mLogTag,
                        String.format(
                                "Attempting to dynamically load metric collector with fully "
                                        + "qualified name %s.",
                                listenerName));
                try {
                    listener = loadListenerByFullyQualifiedName(listenerName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Failed to dynamically load metric collector with fully "
                                            + "qualified name %s.",
                                    listenerName),
                            e);
                }
            } else {
                String fullName = String.format("%s.%s", METRIC_COLLECTORS_PACKAGE, listenerName);
                Log.i(
                        mLogTag,
                        String.format(
                                "Attempting to dynamically load metric collector with simple class "
                                        + "name %s (fully qualified name: %s).",
                                listenerName, fullName));
                try {
                    listener = loadListenerByFullyQualifiedName(fullName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Failed to dynamically load metric collector with simple class "
                                            + "name %s (attempted fully qualified name: %s).",
                                    listenerName, fullName),
                            e);
                }
            }
            mMetricListeners.add(listener);
        }
        // Initialize each listener.
        for (BaseMetricListener listener : mMetricListeners) {
            listener.setInstrumentation(instrumentation);
        }
        mFinishingListeners = mFifoOrder ? mMetricListeners : Lists.reverse(mMetricListeners);
    }

    @Override
    protected void starting(Description description) {
        for (BaseMetricListener listener : mMetricListeners) {
            listener.setUp();
        }
        for (BaseMetricListener listener : mMetricListeners) {
            try {
                listener.testStarted(description);
            } catch (Exception e) {
                Log.e(
                        mLogTag,
                        String.format(
                                "Exception from listener %s during starting().",
                                listener.getClass().getCanonicalName()),
                        e);
            }
        }
    }

    @Override
    protected void finished(Description description) {
        for (BaseMetricListener listener : mFinishingListeners) {
            try {
                listener.testFinished(description);
            } catch (Exception e) {
                Log.e(
                        mLogTag,
                        String.format(
                                "Exception from listener %s during finished().",
                                listener.getClass().getCanonicalName()),
                        e);
            }
        }
        for (BaseMetricListener listener : mFinishingListeners) {
            listener.cleanUp();
        }
    }

    @Override
    protected void failed(Throwable t, Description description) {
        Failure failure = new Failure(description, t);
        for (BaseMetricListener listener : mFinishingListeners) {
            try {
                listener.testFailure(failure);
            } catch (Exception e) {
                Log.e(
                        mLogTag,
                        String.format(
                                "Exception from listener %s during failed().",
                                listener.getClass().getCanonicalName()),
                        e);
            }
        }
    }

    private BaseMetricListener loadListenerByFullyQualifiedName(String name) throws Exception {
        // Load the metric collector class using reflection.
        Class<?> loadedClass = null;
        try {
            loadedClass = TestMetricRule.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    String.format("Could not find class with fully qualified name %s.", name));
        }
        // Ensure that the class found is a BaseMetricListener.
        if (loadedClass == null || (!BaseMetricListener.class.isAssignableFrom(loadedClass))) {
            throw new IllegalArgumentException(
                    String.format("Class %s is not a BaseMetricListener.", loadedClass));
        }
        // Use the default constructor to create a metric collector instance.
        try {
            Constructor<?> constructor = loadedClass.getConstructor();
            // Cast is safe as we have vetted that loadedClass is a BaseMetricListener.
            return (BaseMetricListener) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Metric collector %s cannot be instantiated with an empty constructor",
                            loadedClass),
                    e);
        }
    }
}

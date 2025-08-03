/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.tools.collectors;

import android.content.Context;
import android.device.collectors.PerfettoListener;
import android.device.collectors.PerfettoTracingStrategy;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link PerfettoListener} that captures the perfetto trace for UI traces during each test method
 * and save the perfetto trace files under
 * <root>/<test_name>/PerfettoListener/<test_name>-<invocation_count>.perfetto-trace
 */
public class DefaultUITraceListener extends PerfettoListener {
    private static final String LOG_TAG = "UITraceListener";
    private static final String DEFAULT_TEXT_CONFIG_FILE = "trace_config.textproto";
    private static final String DEFAULT_FILE_PREFIX = "uiTrace_";

    @SuppressWarnings("unused")
    public DefaultUITraceListener() {
        super();
    }

    /**
     * Constructor to simulate receiving the instrumentation arguments. Should not be used except
     * for testing.
     */
    @VisibleForTesting
    DefaultUITraceListener(Bundle args, PerfettoTracingStrategy strategy) {
        super(args, strategy);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();
        args.putString(PerfettoTracingStrategy.PERFETTO_CONFIG_TEXT_PROTO, "true");
        String protoConfig;
        try {
            protoConfig = readDefaultConfig();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read config asset", e);
        }
        args.putString(
                PerfettoTracingStrategy.PERFETTO_CONFIG_OUTPUT_FILE_PREFIX, DEFAULT_FILE_PREFIX);
        args.putString(PerfettoTracingStrategy.PERFETTO_CONFIG_TEXT_CONTENT, protoConfig);
        super.setupAdditionalArgs();
    }

    private String readDefaultConfig() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        try (InputStream inputStream = context.getAssets().open(DEFAULT_TEXT_CONFIG_FILE)) {
            Log.v(LOG_TAG, "context.assets");
            return new String(inputStream.readAllBytes());
        }
    }
}

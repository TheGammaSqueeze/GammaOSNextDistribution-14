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

package com.android.federatedcompute.services.statsd;

import android.annotation.NonNull;

import com.android.adservices.shared.errorlogging.AbstractAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLoggerImpl;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

public final class ClientErrorLogger extends AbstractAdServicesErrorLogger {
    private final Flags mFlags;

    private static class LazyInstanceHolder {
        static final ClientErrorLogger LAZY_INSTANCE =
                new ClientErrorLogger(
                        FlagsFactory.getFlags(), StatsdAdServicesErrorLoggerImpl.getInstance());
    }

    /** Returns the instance of {@link ClientErrorLogger}. */
    @NonNull
    public static ClientErrorLogger getInstance() {
        return ClientErrorLogger.LazyInstanceHolder.LAZY_INSTANCE;
    }

    @VisibleForTesting
    ClientErrorLogger(Flags flags, StatsdAdServicesErrorLogger statsdAdServicesErrorLogger) {
        super(statsdAdServicesErrorLogger);
        mFlags = Objects.requireNonNull(flags);
    }

    @Override
    protected boolean isEnabled(int errorCode) {
        return mFlags.getEnableClientErrorLogging();
    }
}

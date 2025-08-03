/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.errorlogging;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.errorlogging.AbstractAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** AdServices implementation of {@link AbstractAdServicesErrorLogger}. */
public final class AdServicesErrorLoggerImpl extends AbstractAdServicesErrorLogger {
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile AdServicesErrorLoggerImpl sSingleton;
    private final Flags mFlags;

    public static AdServicesErrorLoggerImpl getInstance() {
        if (sSingleton == null) {
            synchronized (SINGLETON_LOCK) {
                if (sSingleton == null) {
                    sSingleton =
                            new AdServicesErrorLoggerImpl(
                                    FlagsFactory.getFlags(),
                                    StatsdAdServicesErrorLoggerImpl.getInstance());
                }
            }
        }
        return sSingleton;
    }

    @VisibleForTesting
    AdServicesErrorLoggerImpl(
            Flags flags, StatsdAdServicesErrorLogger statsdAdServicesErrorLogger) {
        super(statsdAdServicesErrorLogger);
        mFlags = Objects.requireNonNull(flags);
    }

    @Override
    protected boolean isEnabled(int errorCode) {
        return mFlags.getAdServicesErrorLoggingEnabled()
                && !mFlags.getErrorCodeLoggingDenyList().contains(errorCode);
    }
}

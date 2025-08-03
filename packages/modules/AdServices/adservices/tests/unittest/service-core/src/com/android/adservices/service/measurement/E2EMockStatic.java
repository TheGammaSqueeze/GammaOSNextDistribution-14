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

package com.android.adservices.service.measurement;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.mockito.stubbing.Answer;

/**
 * Combines TestableDeviceConfig with other needed static mocks.
 */
public final class E2EMockStatic implements StaticMockFixture {

    private final E2ETest.ParamsProvider mParams;

    public E2EMockStatic(E2ETest.ParamsProvider paramsProvider) {
        mParams = paramsProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StaticMockitoSessionBuilder setUpMockedClasses(
            StaticMockitoSessionBuilder sessionBuilder) {
        sessionBuilder.spyStatic(PrivacyParams.class);
        sessionBuilder.spyStatic(AppManifestConfigHelper.class);
        return sessionBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUpMockBehaviors() {
        // Privacy params
        doAnswer((Answer<Integer>) invocation -> mParams.getNavigationTriggerDataCardinality())
                .when(() -> PrivacyParams.getNavigationTriggerDataCardinality());
        // Pass manifest checks
        doReturn(true)
                .when(() -> AppManifestConfigHelper.isAllowedAttributionAccess(any(), anyString()));
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown() {}

    public static class E2EMockStaticRule extends AdServicesExtendedMockitoRule {
        public E2EMockStaticRule(E2ETest.ParamsProvider paramsProvider) {
            super(TestableDeviceConfig::new, () -> new E2EMockStatic(paramsProvider));
        }
    }
}

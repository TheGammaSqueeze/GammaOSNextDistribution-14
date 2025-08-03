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

package com.android.adservices.adid;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;

/** Unit test for {@link com.android.adservices.adid.AdIdService}. */
public class AdIdServiceTest {
    private static final String TAG = "AdIdServiceTest";

    @Mock Flags mMockFlags;
    @Mock AdIdWorker mMockAdIdWorker;
    @Mock AdServicesLoggerImpl mMockAdServicesLoggerImpl;
    @Mock AppImportanceFilter mMockAppImportanceFilter;
    @Mock PackageManager mMockPackageManager;

    /** AdIdService test initial setup. */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test adId api level behavior with killswitch off. */
    @Test
    public void testBindableAdIdService_killswitchOff() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(AdIdWorker.class)
                        .spyStatic(AdServicesLoggerImpl.class)
                        .spyStatic(AppImportanceFilter.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Killswitch is off.
            doReturn(false).when(mMockFlags).getAdIdKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

            ExtendedMockito.doReturn(mMockAdIdWorker).when(() -> AdIdWorker.getInstance());

            AdIdService spyAdIdService = spy(new AdIdService());
            doReturn(mMockPackageManager).when(spyAdIdService).getPackageManager();
            ExtendedMockito.doReturn(mMockAppImportanceFilter)
                    .when(
                            () ->
                                    AppImportanceFilter.create(
                                            any(Context.class), anyInt(), any(Supplier.class)));

            spyAdIdService.onCreate();
            IBinder binder = spyAdIdService.onBind(getIntentForAdIdService());
            assertNotNull(binder);

            final StringWriter writer = new StringWriter();
            spyAdIdService.dump(null, new PrintWriter(writer), null);
            assertTrue(writer.toString().contains("nothing to dump"));
        } finally {
            session.finishMocking();
        }
    }

    /** Test adId api level behavior with killswitch on. */
    @Test
    public void testBindableAdIdService_killswitchOn() {
        // Start a mockitoSession to mock static method
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(FlagsFactory.class).startMocking();

        try {
            // Killswitch is on.
            doReturn(true).when(mMockFlags).getAdIdKillSwitch();

            // Mock static method FlagsFactory.getFlags() to return Mock Flags.
            ExtendedMockito.doReturn(mMockFlags).when(() -> FlagsFactory.getFlags());

            AdIdService adidService = new AdIdService();
            adidService.onCreate();
            IBinder binder = adidService.onBind(getIntentForAdIdService());
            assertNull(binder);
        } finally {
            session.finishMocking();
        }
    }

    private Intent getIntentForAdIdService() {
        return new Intent(ApplicationProvider.getApplicationContext(), AdIdService.class);
    }
}

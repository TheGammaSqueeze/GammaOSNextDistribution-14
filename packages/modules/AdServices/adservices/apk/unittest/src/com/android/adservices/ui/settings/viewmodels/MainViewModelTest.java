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

package com.android.adservices.ui.settings.viewmodels;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

/** Tests for {@link MainViewModel}. */
@SpyStatic(FlagsFactory.class)
@SpyStatic(ConsentManager.class)
public final class MainViewModelTest extends AdServicesExtendedMockitoTestCase {

    private MainViewModel mMainViewModel;
    @Mock private ConsentManager mConsentManager;
    @Mock private Flags mMockFlags;

    /** Setup needed before every test in this class. */
    @Before
    public void setup() {
        doReturn(true).when(mMockFlags).getRecordManualInteractionEnabled();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        mMainViewModel =
                new MainViewModel(ApplicationProvider.getApplicationContext(), mConsentManager);
    }

    /** Test if getConsent returns true if the {@link ConsentManager} always returns true. */
    @Test
    public void testGetConsentReturnsTrue() {
        assertTrue(mMainViewModel.getConsent().getValue());
    }

    /** Test if getConsent returns false if the {@link ConsentManager} always returns false. */
    @Test
    public void testGetConsentReturnsFalse() {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent();
        mMainViewModel =
                new MainViewModel(ApplicationProvider.getApplicationContext(), mConsentManager);

        assertFalse(mMainViewModel.getConsent().getValue());
    }

    /** Test if setConsent enables consent with a call to {@link ConsentManager}. */
    @Test
    public void testSetConsentTrue() {
        mMainViewModel.setConsent(true);

        verify(mConsentManager, times(1)).enable(any(Context.class));
    }

    /** Test if setConsent revokes consent with a call to {@link ConsentManager}. */
    @Test
    public void testSetConsentFalse() {
        mMainViewModel.setConsent(false);

        verify(mConsentManager, times(1)).disable(any(Context.class));
    }
}


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

package com.android.adservices.service.customaudience;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceQuantityCheckerTest {
    private static final Flags FLAGS = FlagsFactory.getFlagsForTest();

    @Mock private CustomAudienceDao mCustomAudienceDao;

    private CustomAudienceQuantityChecker mChecker;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mChecker = new CustomAudienceQuantityChecker(mCustomAudienceDao, FLAGS);
    }

    @Test
    public void testNullCustomAudience_throwNPE() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.check(null, CustomAudienceFixture.VALID_OWNER));
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testNullCallerPackageName_throwNPE() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.check(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                null));
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testExistOwnerAndOwnerReachMax_success() {
        when(mCustomAudienceDao.getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(1L)
                                .setTotalOwnerCount(FLAGS.getFledgeCustomAudienceMaxOwnerCount())
                                .build());

        mChecker.check(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceDao).getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testOwnerExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(FLAGS.getFledgeCustomAudienceMaxOwnerCount())
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_OWNER_ALLOWED_FOR_THE_DEVICE_HAD_REACHED);

        verify(mCustomAudienceDao).getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testTotalCountExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(
                                        FLAGS.getFledgeCustomAudienceMaxCount())
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(1L)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED);
        verify(mCustomAudienceDao).getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testPerOwnerCountExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(
                                        FLAGS.getFledgeCustomAudiencePerAppMaxCount())
                                .setTotalOwnerCount(1L)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED);

        verify(mCustomAudienceDao).getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testAllGood() {
        when(mCustomAudienceDao.getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(0L)
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(0L)
                                .build());
        mChecker.check(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                CustomAudienceFixture.VALID_OWNER);

        verify(mCustomAudienceDao).getCustomAudienceStats(CustomAudienceFixture.VALID_OWNER);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    private void assertViolations(Exception exception, String... violations) {
        assertEquals(
                String.format(
                        CustomAudienceQuantityChecker.CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED,
                        List.of(violations)),
                exception.getMessage());
    }
}

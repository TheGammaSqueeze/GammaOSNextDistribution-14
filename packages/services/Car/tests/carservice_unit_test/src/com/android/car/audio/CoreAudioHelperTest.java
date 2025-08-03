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

package com.android.car.audio;

import static com.android.car.audio.CoreAudioRoutingUtils.INVALID_CONTEXT_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.INVALID_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.INVALID_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.INVALID_STRATEGY;
import static com.android.car.audio.CoreAudioRoutingUtils.INVALID_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_CONTEXT_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_GROUP;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_STRATEGY;
import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_CONTEXT_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_GROUP;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_STRATEGY;
import static com.android.car.audio.CoreAudioRoutingUtils.NAV_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_CONTEXT_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_GROUP;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_GROUP_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_GROUP_NAME;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_STRATEGY;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_STRATEGY_ID;
import static com.android.car.audio.CoreAudioRoutingUtils.UNSUPPORTED_ATTRIBUTES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.media.audiopolicy.AudioVolumeGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CoreAudioHelperTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = CoreAudioHelperTest.class.getSimpleName();

    public CoreAudioHelperTest() {
        super(CoreAudioHelper.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioManager.class);
    }

    @Before
    public void setUp() throws Exception {
        List<AudioVolumeGroup> groups = CoreAudioRoutingUtils.getVolumeGroups();
        List<AudioProductStrategy> strategies = CoreAudioRoutingUtils.getProductStrategies();
        doReturn(strategies).when(AudioManager::getAudioProductStrategies);
        doReturn(groups).when(AudioManager::getAudioVolumeGroups);
    }

    @Test
    public void getStrategyForAudioAttributes_withValidAttributes_succeeds() {
        expectWithMessage("Music attributes of music strategy id (%s)", MUSIC_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributes(MUSIC_ATTRIBUTES))
                .isEqualTo(MUSIC_STRATEGY_ID);
        expectWithMessage("Navigation attributes of navigation strategy id (%s)", NAV_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributes(NAV_ATTRIBUTES))
                .isEqualTo(NAV_STRATEGY_ID);
        expectWithMessage("OEM attributes of OEM strategy id (%s)", OEM_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributes(OEM_ATTRIBUTES))
                .isEqualTo(OEM_STRATEGY_ID);
    }

    @Test
    public void getStrategyForAudioAttributes_withInvalidAttributes_returnsInvalidStrategy() {
        expectWithMessage("Unsupported attributes (%s) of none strategy",
                        UNSUPPORTED_ATTRIBUTES)
                .that(CoreAudioHelper.getStrategyForAudioAttributes(UNSUPPORTED_ATTRIBUTES))
                .isEqualTo(CoreAudioHelper.INVALID_STRATEGY);
    }

    @Test
    public void getStrategyForAudioAttributesOrDefault_withValidAttributes_succeeds() {
        expectWithMessage("Music attributes of Music strategy id (%s)", MUSIC_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributesOrDefault(MUSIC_ATTRIBUTES))
                .isEqualTo(MUSIC_STRATEGY_ID);
        expectWithMessage("Navigation attributes of navigation strategy id (%s)", NAV_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributesOrDefault(NAV_ATTRIBUTES))
                .isEqualTo(NAV_STRATEGY_ID);
        expectWithMessage("OEM attributes of OEM strategy id (%s)", OEM_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategyForAudioAttributesOrDefault(OEM_ATTRIBUTES))
                .isEqualTo(OEM_STRATEGY_ID);
    }

    @Test
    public void getStrategyForAudioAttributesOrDefault_withInvalidAttributes_fallbacksOnDefault() {
        expectWithMessage("Unsupported attributes (%s) fallbacks on default strategy",
                UNSUPPORTED_ATTRIBUTES)
                .that(CoreAudioHelper.getStrategyForAudioAttributesOrDefault(
                        UNSUPPORTED_ATTRIBUTES))
                .isEqualTo(MUSIC_STRATEGY_ID);
    }

    @Test
    public void getProductStrategyForAudioAttributes_withValidAttributes_succeeds() {
        expectWithMessage("Music product strategy")
                .that(CoreAudioHelper.getProductStrategyForAudioAttributes(MUSIC_ATTRIBUTES))
                .isEqualTo(MUSIC_STRATEGY);
        expectWithMessage("Navigation product strategy")
                .that(CoreAudioHelper.getProductStrategyForAudioAttributes(NAV_ATTRIBUTES))
                .isEqualTo(NAV_STRATEGY);
        expectWithMessage("OEM product strategy")
                .that(CoreAudioHelper.getProductStrategyForAudioAttributes(OEM_ATTRIBUTES))
                .isEqualTo(OEM_STRATEGY);
    }

    @Test
    public void getProductStrategyForAudioAttributes_withInvalidAttributes_returnsNull() {
        expectWithMessage("Null product strategy for invalid audio attribute")
                .that(CoreAudioHelper.getProductStrategyForAudioAttributes(UNSUPPORTED_ATTRIBUTES))
                .isNull();
    }

    @Test
    public void getProductStrategyForAudioAttributes_withNullAttributes_fails() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                CoreAudioHelper.getProductStrategyForAudioAttributes(null));

        expectWithMessage("Null audio attributes exception").that(exception).hasMessageThat()
                .contains("Audio attributes");
    }

    @Test
    public void getStrategyForContextName_succeeds() {
        expectWithMessage("Music strategy for context name (%s)", MUSIC_CONTEXT_NAME)
                .that(CoreAudioHelper.getStrategyForContextName(MUSIC_CONTEXT_NAME))
                .isEqualTo(MUSIC_STRATEGY_ID);
        expectWithMessage("Navigation strategy for context name (%s)", NAV_CONTEXT_NAME)
                .that(CoreAudioHelper.getStrategyForContextName(NAV_CONTEXT_NAME))
                .isEqualTo(NAV_STRATEGY_ID);
        expectWithMessage("OEM strategy for context name (%s)", OEM_CONTEXT_NAME)
                .that(CoreAudioHelper.getStrategyForContextName(OEM_CONTEXT_NAME))
                .isEqualTo(OEM_STRATEGY_ID);
    }

    @Test
    public void getStrategyForContextName_withInvalidContextName_returnsInvalidStrategy() {
        expectWithMessage("Strategy for invalid context name (%s)", INVALID_CONTEXT_NAME)
                .that(CoreAudioHelper.getStrategyForContextName(INVALID_CONTEXT_NAME))
                .isEqualTo(INVALID_STRATEGY);
    }

    @Test
    public void getStrategy_withValidId_succeeds() {
        expectWithMessage("Music strategy for id (%s)", MUSIC_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategy(MUSIC_STRATEGY_ID))
                .isEqualTo(MUSIC_STRATEGY);
        expectWithMessage("Navigation strategy for id (%s)", NAV_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategy(NAV_STRATEGY_ID))
                .isEqualTo(NAV_STRATEGY);
        expectWithMessage("OEM strategy for id (%s)", OEM_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategy(OEM_STRATEGY_ID))
                .isEqualTo(OEM_STRATEGY);
    }

    @Test
    public void getStrategy_withInvalidId_returnsNull() {
        expectWithMessage("Invalid strategy id(%s)", INVALID_STRATEGY_ID)
                .that(CoreAudioHelper.getStrategy(INVALID_STRATEGY_ID))
                .isNull();
    }

    @Test
    public void getVolumeGroup_withValidName_succeeds() {
        expectWithMessage("Music group for name (%s)", MUSIC_GROUP_NAME)
                .that(CoreAudioHelper.getVolumeGroup(MUSIC_GROUP_NAME))
                .isEqualTo(MUSIC_GROUP);
        expectWithMessage("Navigation group for name (%s)", NAV_GROUP_NAME)
                .that(CoreAudioHelper.getVolumeGroup(NAV_GROUP_NAME))
                .isEqualTo(NAV_GROUP);
        expectWithMessage("OEM group for name (%s)", OEM_GROUP_NAME)
                .that(CoreAudioHelper.getVolumeGroup(OEM_GROUP_NAME))
                .isEqualTo(OEM_GROUP);
    }

    @Test
    public void getVolumeGroup_withInvalidName_returnsNull() {
        expectWithMessage("Invalid group for name (%s)", INVALID_GROUP_NAME)
                .that(CoreAudioHelper.getVolumeGroup(INVALID_GROUP_NAME))
                .isNull();
    }

    @Test
    public void selectAttributesForVolumeGroupName_withValidName_succeeds() {
        expectWithMessage("Music best attributes for music group name (%s)", MUSIC_GROUP_NAME)
                .that(CoreAudioHelper.selectAttributesForVolumeGroupName(MUSIC_GROUP_NAME))
                .isEqualTo(MUSIC_ATTRIBUTES);
        expectWithMessage("Navigation best attributes for nav group name (%s)", NAV_GROUP_NAME)
                .that(CoreAudioHelper.selectAttributesForVolumeGroupName(NAV_GROUP_NAME))
                .isEqualTo(NAV_ATTRIBUTES);
        expectWithMessage("OEM best attributes for oem group name (%s)", OEM_GROUP_NAME)
                .that(CoreAudioHelper.selectAttributesForVolumeGroupName(OEM_GROUP_NAME))
                .isEqualTo(OEM_ATTRIBUTES);
        expectWithMessage("Best attributes for invalid group for name (%s)", INVALID_GROUP_NAME)
                .that(CoreAudioHelper.selectAttributesForVolumeGroupName(INVALID_GROUP_NAME))
                .isEqualTo(CoreAudioHelper.DEFAULT_ATTRIBUTES);
    }

    @Test
    public void selectAttributesForVolumeGroupName_withInvalidName_returnsDefaultAttributes() {
        expectWithMessage("Best attributes for Invalid group for name (%s)", INVALID_GROUP_NAME)
                .that(CoreAudioHelper.selectAttributesForVolumeGroupName(INVALID_GROUP_NAME))
                .isEqualTo(CoreAudioHelper.DEFAULT_ATTRIBUTES);
    }

    @Test
    public void getVolumeGroupNameForAudioAttributes_withSupportedAttributes_succeeds() {
        expectWithMessage("Music group name (%s) for music attributes (%s)", MUSIC_GROUP_NAME,
                        MUSIC_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupNameForAudioAttributes(MUSIC_ATTRIBUTES))
                .isEqualTo(MUSIC_GROUP_NAME);
        expectWithMessage("Navigation group name (%s) for nav attributes (%s)", NAV_ATTRIBUTES,
                        NAV_GROUP_NAME)
                .that(CoreAudioHelper.getVolumeGroupNameForAudioAttributes(NAV_ATTRIBUTES))
                .isEqualTo(NAV_GROUP_NAME);
        expectWithMessage("OEM group name (%s) for oem attributes (%s)", OEM_GROUP_NAME,
                        OEM_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupNameForAudioAttributes(OEM_ATTRIBUTES))
                .isEqualTo(OEM_GROUP_NAME);
    }

    @Test
    public void getVolumeGroupNameForAudioAttributes_withUnsupportedAttributes_returnsNull() {
        expectWithMessage("Null group name for invalid attributes (%s)", UNSUPPORTED_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupNameForAudioAttributes(UNSUPPORTED_ATTRIBUTES))
                .isNull();
    }

    @Test
    public void getVolumeGroupIdForAudioAttributes_withSupportedAttributes_succeeds() {
        expectWithMessage("Music group id (%s) for music attributes (%s)", MUSIC_GROUP_ID,
                        MUSIC_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupIdForAudioAttributes(MUSIC_ATTRIBUTES))
                .isEqualTo(MUSIC_GROUP_ID);
        expectWithMessage("Navigation group id (%s) for navigation attributes (%s)", NAV_GROUP_ID,
                        NAV_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupIdForAudioAttributes(NAV_ATTRIBUTES))
                .isEqualTo(NAV_GROUP_ID);
        expectWithMessage("OEM group id (%s) for oem attributes (%s)", OEM_GROUP_ID,
                        OEM_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupIdForAudioAttributes(OEM_ATTRIBUTES))
                .isEqualTo(OEM_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForAudioAttributes_withUnsupportedAttributes_returnsNull() {
        expectWithMessage("Invalid group id for invalid attributes (%s)", UNSUPPORTED_ATTRIBUTES)
                .that(CoreAudioHelper.getVolumeGroupIdForAudioAttributes(UNSUPPORTED_ATTRIBUTES))
                .isEqualTo(CoreAudioHelper.INVALID_GROUP_ID);
    }

    @Test
    public void getVolumeGroupNameFromCoreId_withValidGroupId_succeeds() {
        expectWithMessage("Music group name for id (%s)", MUSIC_GROUP_ID)
                .that(CoreAudioHelper.getVolumeGroupNameFromCoreId(MUSIC_GROUP_ID))
                .isEqualTo(MUSIC_GROUP_NAME);
        expectWithMessage("Navigation group name for id (%s)", NAV_GROUP_ID)
                .that(CoreAudioHelper.getVolumeGroupNameFromCoreId(NAV_GROUP_ID))
                .isEqualTo(NAV_GROUP_NAME);
        expectWithMessage("OEM group name for id (%s)", OEM_GROUP_ID)
                .that(CoreAudioHelper.getVolumeGroupNameFromCoreId(OEM_GROUP_ID))
                .isEqualTo(OEM_GROUP_NAME);
    }

    @Test
    public void getVolumeGroupNameFromCoreId_withInvalidGroupId_returnsNull() {
        expectWithMessage("Null group name for group invalid id (%s)", INVALID_GROUP_ID)
                .that(CoreAudioHelper.getVolumeGroupNameFromCoreId(INVALID_GROUP_ID))
                .isNull();
    }

    @Test
    public void isDefaultStrategy() {
        expectWithMessage("Default strategy for music")
                .that(CoreAudioHelper.isDefaultStrategy(MUSIC_STRATEGY_ID))
                .isTrue();
        expectWithMessage("Non-default strategy for navigation")
                .that(CoreAudioHelper.isDefaultStrategy(NAV_STRATEGY_ID))
                .isFalse();
        expectWithMessage("Non-default strategy for oem")
                .that(CoreAudioHelper.isDefaultStrategy(OEM_STRATEGY_ID))
                .isFalse();
        expectWithMessage("Non-default strategy for invalid id")
                .that(CoreAudioHelper.isDefaultStrategy(INVALID_STRATEGY_ID))
                .isFalse();
    }
}

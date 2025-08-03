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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class IwlanCarrierConfigTest {
    private static final int DEFAULT_SUB_ID = 0;
    private static final int DEFAULT_SLOT_ID = 1;

    private static final String KEY_NON_EXISTING = "non_existing_key";
    private static final String KEY_CONFIG_IN_SUB_INT = "iwlan.key_config_in_sub_int";
    private static final String KEY_CONFIG_IN_DEFAULT_INT = "iwlan.key_config_in_default_int";
    private static final String KEY_CONFIG_IN_SUB_LONG = "iwlan.key_config_in_sub_long";
    private static final String KEY_CONFIG_IN_DEFAULT_LONG = "iwlan.key_config_in_default_long";
    private static final String KEY_CONFIG_IN_SUB_DOUBLE = "iwlan.key_config_in_sub_double";
    private static final String KEY_CONFIG_IN_DEFAULT_DOUBLE = "iwlan.key_config_in_default_double";
    private static final String KEY_CONFIG_IN_SUB_BOOLEAN = "iwlan.key_config_in_sub_boolean";
    private static final String KEY_CONFIG_IN_DEFAULT_BOOLEAN =
            "iwlan.key_config_in_default_boolean";
    private static final String KEY_CONFIG_IN_SUB_STRING = "iwlan.key_config_in_sub_string";
    private static final String KEY_CONFIG_IN_DEFAULT_STRING = "iwlan.key_config_in_default_string";
    private static final String KEY_CONFIG_IN_SUB_INT_ARRAY = "iwlan.key_config_in_sub_int_array";
    private static final String KEY_CONFIG_IN_DEFAULT_INT_ARRAY =
            "iwlan.key_config_in_default_int_array";
    private static final String KEY_CONFIG_IN_SUB_LONG_ARRAY = "iwlan.key_config_in_sub_long_array";
    private static final String KEY_CONFIG_IN_DEFAULT_LONG_ARRAY =
            "iwlan.key_config_in_default_long_array";
    private static final String KEY_CONFIG_IN_SUB_DOUBLE_ARRAY =
            "iwlan.key_config_in_sub_double_array";
    private static final String KEY_CONFIG_IN_DEFAULT_DOUBLE_ARRAY =
            "iwlan.key_config_in_default_double_array";
    private static final String KEY_CONFIG_IN_SUB_BOOLEAN_ARRAY =
            "iwlan.key_config_in_sub_boolean_array";
    private static final String KEY_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY =
            "iwlan.key_config_in_default_boolean_array";
    private static final String KEY_CONFIG_IN_SUB_STRING_ARRAY =
            "iwlan.key_config_in_sub_string_array";
    private static final String KEY_CONFIG_IN_DEFAULT_STRING_ARRAY =
            "iwlan.key_config_in_default_string_array";

    private static final int VALUE_CONFIG_IN_SUB_INT = 10;
    private static final int VALUE_CONFIG_IN_DEFAULT_INT = 20;
    private static final long VALUE_CONFIG_IN_SUB_LONG = 10;
    private static final long VALUE_CONFIG_IN_DEFAULT_LONG = 20;
    private static final double VALUE_CONFIG_IN_SUB_DOUBLE = 10.0;
    private static final double VALUE_CONFIG_IN_DEFAULT_DOUBLE = 20.0;
    private static final boolean VALUE_CONFIG_IN_SUB_BOOLEAN = true;
    private static final boolean VALUE_CONFIG_IN_DEFAULT_BOOLEAN = false;
    private static final String VALUE_CONFIG_IN_SUB_STRING = "value_config_in_sub_string";
    private static final String VALUE_CONFIG_IN_DEFAULT_STRING = "value_config_in_default_string";
    private static final int[] VALUE_CONFIG_IN_SUB_INT_ARRAY = new int[] {10, 20};
    private static final int[] VALUE_CONFIG_IN_DEFAULT_INT_ARRAY = new int[] {30, 40};
    private static final long[] VALUE_CONFIG_IN_SUB_LONG_ARRAY = new long[] {10, 20};
    private static final long[] VALUE_CONFIG_IN_DEFAULT_LONG_ARRAY = new long[] {30, 40};
    private static final double[] VALUE_CONFIG_IN_SUB_DOUBLE_ARRAY = new double[] {10, 20};
    private static final double[] VALUE_CONFIG_IN_DEFAULT_DOUBLE_ARRAY = new double[] {30, 40};
    private static final boolean[] VALUE_CONFIG_IN_SUB_BOOLEAN_ARRAY = new boolean[] {true, true};
    private static final boolean[] VALUE_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY =
            new boolean[] {false, false};
    private static final String[] VALUE_CONFIG_IN_SUB_STRING_ARRAY =
            new String[] {"value_config_in_sub_string", "second_value_config_in_sub_string"};
    private static final String[] VALUE_CONFIG_IN_DEFAULT_STRING_ARRAY =
            new String[] {
                "value_config_in_default_string", "second_value_config_in_default_string"
            };

    @Mock private Context mMockContext;
    @Mock private CarrierConfigManager mMockCarrierConfigManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;

    private PersistableBundle mBundleForSub;
    private PersistableBundle mBundleForDefault;

    MockitoSession mStaticMockSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(CarrierConfigManager.class)
                        .startMocking();

        when(mMockContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mMockCarrierConfigManager);
        when(mMockContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);
        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUB_ID);

        mBundleForSub = createCarrierConfigForSub();
        lenient()
                .when(mMockCarrierConfigManager.getConfigForSubId(anyInt(), anyString()))
                .thenReturn(mBundleForSub);

        mBundleForDefault = createCarrierConfigForDefault();
        lenient().when(CarrierConfigManager.getDefaultConfig()).thenReturn(mBundleForDefault);
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    private static PersistableBundle createCarrierConfigForSub() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_CONFIG_IN_SUB_INT, VALUE_CONFIG_IN_SUB_INT);
        bundle.putLong(KEY_CONFIG_IN_SUB_LONG, VALUE_CONFIG_IN_SUB_LONG);
        bundle.putDouble(KEY_CONFIG_IN_SUB_DOUBLE, VALUE_CONFIG_IN_SUB_DOUBLE);
        bundle.putBoolean(KEY_CONFIG_IN_SUB_BOOLEAN, VALUE_CONFIG_IN_SUB_BOOLEAN);
        bundle.putString(KEY_CONFIG_IN_SUB_STRING, VALUE_CONFIG_IN_SUB_STRING);
        bundle.putIntArray(KEY_CONFIG_IN_SUB_INT_ARRAY, VALUE_CONFIG_IN_SUB_INT_ARRAY);
        bundle.putLongArray(KEY_CONFIG_IN_SUB_LONG_ARRAY, VALUE_CONFIG_IN_SUB_LONG_ARRAY);
        bundle.putDoubleArray(KEY_CONFIG_IN_SUB_DOUBLE_ARRAY, VALUE_CONFIG_IN_SUB_DOUBLE_ARRAY);
        bundle.putBooleanArray(KEY_CONFIG_IN_SUB_BOOLEAN_ARRAY, VALUE_CONFIG_IN_SUB_BOOLEAN_ARRAY);
        bundle.putStringArray(KEY_CONFIG_IN_SUB_STRING_ARRAY, VALUE_CONFIG_IN_SUB_STRING_ARRAY);
        return bundle;
    }

    private static PersistableBundle createCarrierConfigForDefault() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_CONFIG_IN_DEFAULT_INT, VALUE_CONFIG_IN_DEFAULT_INT);
        bundle.putLong(KEY_CONFIG_IN_DEFAULT_LONG, VALUE_CONFIG_IN_DEFAULT_LONG);
        bundle.putDouble(KEY_CONFIG_IN_DEFAULT_DOUBLE, VALUE_CONFIG_IN_DEFAULT_DOUBLE);
        bundle.putBoolean(KEY_CONFIG_IN_DEFAULT_BOOLEAN, VALUE_CONFIG_IN_DEFAULT_BOOLEAN);
        bundle.putString(KEY_CONFIG_IN_DEFAULT_STRING, VALUE_CONFIG_IN_DEFAULT_STRING);
        bundle.putIntArray(KEY_CONFIG_IN_DEFAULT_INT_ARRAY, VALUE_CONFIG_IN_DEFAULT_INT_ARRAY);
        bundle.putLongArray(KEY_CONFIG_IN_DEFAULT_LONG_ARRAY, VALUE_CONFIG_IN_DEFAULT_LONG_ARRAY);
        bundle.putDoubleArray(
                KEY_CONFIG_IN_DEFAULT_DOUBLE_ARRAY, VALUE_CONFIG_IN_DEFAULT_DOUBLE_ARRAY);
        bundle.putBooleanArray(
                KEY_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY, VALUE_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY);
        bundle.putStringArray(
                KEY_CONFIG_IN_DEFAULT_STRING_ARRAY, VALUE_CONFIG_IN_DEFAULT_STRING_ARRAY);
        return bundle;
    }

    @Test
    public void testGetConfig_ValidRetrieval() {
        int configInt =
                IwlanCarrierConfig.getConfigInt(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_INT);
        assertEquals(VALUE_CONFIG_IN_SUB_INT, configInt);

        long configLong =
                IwlanCarrierConfig.getConfigLong(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_LONG);
        assertEquals(VALUE_CONFIG_IN_SUB_LONG, configLong);

        double configDouble =
                IwlanCarrierConfig.getConfigDouble(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_DOUBLE);
        assertEquals(VALUE_CONFIG_IN_SUB_DOUBLE, configDouble, 0);

        boolean configBoolean =
                IwlanCarrierConfig.getConfigBoolean(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_BOOLEAN);
        assertEquals(VALUE_CONFIG_IN_SUB_BOOLEAN, configBoolean);

        String configString =
                IwlanCarrierConfig.getConfigString(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_STRING);
        assertEquals(VALUE_CONFIG_IN_SUB_STRING, configString);

        int[] configIntArray =
                IwlanCarrierConfig.getConfigIntArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_INT_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_SUB_INT_ARRAY, configIntArray);

        long[] configLongArray =
                IwlanCarrierConfig.getConfigLongArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_LONG_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_SUB_LONG_ARRAY, configLongArray);

        double[] configDoubleArray =
                IwlanCarrierConfig.getConfigDoubleArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_DOUBLE_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_SUB_DOUBLE_ARRAY, configDoubleArray, 0);

        boolean[] configBooleanArray =
                IwlanCarrierConfig.getConfigBooleanArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_BOOLEAN_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_SUB_BOOLEAN_ARRAY, configBooleanArray);

        String[] configStringArray =
                IwlanCarrierConfig.getConfigStringArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_SUB_STRING_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_SUB_STRING_ARRAY, configStringArray);
    }

    @Test
    public void testGetConfig_KeyNotFound() {
        // Default value from getDefaultConfig
        int configInt =
                IwlanCarrierConfig.getConfigInt(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_INT);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_INT, configInt);

        long configLong =
                IwlanCarrierConfig.getConfigLong(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_LONG);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_LONG, configLong);

        double configDouble =
                IwlanCarrierConfig.getConfigDouble(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_DOUBLE);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_DOUBLE, configDouble, 0);

        boolean configBoolean =
                IwlanCarrierConfig.getConfigBoolean(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_BOOLEAN);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_BOOLEAN, configBoolean);

        String configString =
                IwlanCarrierConfig.getConfigString(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_STRING);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_STRING, configString);

        int[] configIntArray =
                IwlanCarrierConfig.getConfigIntArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_INT_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_INT_ARRAY, configIntArray);

        long[] configLongArray =
                IwlanCarrierConfig.getConfigLongArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_LONG_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_LONG_ARRAY, configLongArray);

        double[] configDoubleArray =
                IwlanCarrierConfig.getConfigDoubleArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_DOUBLE_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_DOUBLE_ARRAY, configDoubleArray, 0);

        boolean[] configBooleanArray =
                IwlanCarrierConfig.getConfigBooleanArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY, configBooleanArray);

        String[] configStringArray =
                IwlanCarrierConfig.getConfigStringArray(
                        mMockContext, DEFAULT_SLOT_ID, KEY_CONFIG_IN_DEFAULT_STRING_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_STRING_ARRAY, configStringArray);
    }

    @Test
    public void testGetDefaultConfig_KeyFound() {
        int configInt = IwlanCarrierConfig.getDefaultConfigInt(KEY_CONFIG_IN_DEFAULT_INT);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_INT, configInt);

        long configLong = IwlanCarrierConfig.getDefaultConfigLong(KEY_CONFIG_IN_DEFAULT_LONG);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_LONG, configLong);

        double configDouble =
                IwlanCarrierConfig.getDefaultConfigDouble(KEY_CONFIG_IN_DEFAULT_DOUBLE);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_DOUBLE, configDouble, 0);

        boolean configBoolean =
                IwlanCarrierConfig.getDefaultConfigBoolean(KEY_CONFIG_IN_DEFAULT_BOOLEAN);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_BOOLEAN, configBoolean);

        String configString =
                IwlanCarrierConfig.getDefaultConfigString(KEY_CONFIG_IN_DEFAULT_STRING);
        assertEquals(VALUE_CONFIG_IN_DEFAULT_STRING, configString);

        int[] configIntArray =
                IwlanCarrierConfig.getDefaultConfigIntArray(KEY_CONFIG_IN_DEFAULT_INT_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_INT_ARRAY, configIntArray);

        long[] configLongArray =
                IwlanCarrierConfig.getDefaultConfigLongArray(KEY_CONFIG_IN_DEFAULT_LONG_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_LONG_ARRAY, configLongArray);

        double[] configDoubleArray =
                IwlanCarrierConfig.getDefaultConfigDoubleArray(KEY_CONFIG_IN_DEFAULT_DOUBLE_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_DOUBLE_ARRAY, configDoubleArray, 0);

        boolean[] configBooleanArray =
                IwlanCarrierConfig.getDefaultConfigBooleanArray(
                        KEY_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_BOOLEAN_ARRAY, configBooleanArray);

        String[] configStringArray =
                IwlanCarrierConfig.getDefaultConfigStringArray(KEY_CONFIG_IN_DEFAULT_STRING_ARRAY);
        assertArrayEquals(VALUE_CONFIG_IN_DEFAULT_STRING_ARRAY, configStringArray);
    }

    @Test
    public void testGetDefaultConfig_KeyInHiddenDefault() {
        int result =
                IwlanCarrierConfig.getDefaultConfigInt(
                        IwlanCarrierConfig.KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT);
        assertEquals(IwlanCarrierConfig.DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDefaultConfig_KeyNotFound() {
        IwlanCarrierConfig.getDefaultConfigInt(KEY_NON_EXISTING);
    }
}

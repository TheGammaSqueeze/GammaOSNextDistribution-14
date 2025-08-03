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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ConfigUpdate;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Test;

public class ConfigUpdateIntentsTest {

    public static final String[] CONFIG_UPDATE_INTENT = {
            "android.intent.action.UPDATE_PINS",
            "android.intent.action.UPDATE_INTENT_FIREWALL",
            "android.intent.action.UPDATE_SMS_SHORT_CODES",
            "android.intent.action.UPDATE_CARRIER_PROVISIONING_URLS",
            "android.intent.action.UPDATE_CT_LOGS",
            "android.intent.action.UPDATE_LANG_ID",
            "android.intent.action.UPDATE_SMART_SELECTION",
            "android.intent.action.UPDATE_CONVERSATION_ACTIONS",
            "android.intent.action.UPDATE_NETWORK_WATCHLIST",
            "android.os.action.UPDATE_CARRIER_ID_DB",
            "android.os.action.UPDATE_EMERGENCY_NUMBER_DB",
            "android.os.action.UPDATE_CONFIG"
    };

    public static final String[] CONFIG_UPDATE_EXTRA = {
            "android.os.extra.VERSION",
            "android.os.extra.REQUIRED_HASH",
            "android.os.extra.DOMAIN"
    };
    private static final int INDEX_INTENT_UPDATE_PINS = 0;
    private static final int INDEX_INTENT_FIREWALL = 1;
    private static final int INDEX_INTENT_SMS_SHORT_CODES = 2;
    private static final int INDEX_INTENT_CARRIER_PROVISIONING_URLS = 3;
    private static final int INDEX_INTENT_CT_LOGS = 4;
    private static final int INDEX_INTENT_LANG_ID = 5;
    private static final int INDEX_INTENT_SMART_SELECTION = 6;
    private static final int INDEX_INTENT_CONVERSATION_ACTIONS = 7;
    private static final int INDEX_INTENT_NETWORK_WATCHLIST = 8;
    private static final int INDEX_INTENT_CARRIER_ID_DB = 9;
    private static final int INDEX_INTENT_EMERGENCY_NUMBER_DB = 10;
    private static final int INDEX_INTENT_CONFIG = 11;

    private static final int INDEX_EXTRA_VERSION = 0;
    private static final int INDEX_EXTRA_REQUIRED_HASH = 1;
    private static final int INDEX_EXTRA_DOMAIN = 2;

    @Before
    public void setUp() throws Exception {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    @RequiresFlagsEnabled(Flags.FLAG_OEM_ENABLED_SATELLITE_FLAG)
    @Test
    public void testConfigUpdateReceiversIntent() {
        Intent configUpdateIntent = new Intent();

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_PINS);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_UPDATE_PINS]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_INTENT_FIREWALL);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_FIREWALL]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_SMS_SHORT_CODES);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_SMS_SHORT_CODES]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_CARRIER_PROVISIONING_URLS);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_CARRIER_PROVISIONING_URLS]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_CT_LOGS);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_CT_LOGS]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_LANG_ID);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_LANG_ID]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_SMART_SELECTION);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_SMART_SELECTION]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_CONVERSATION_ACTIONS);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_CONVERSATION_ACTIONS]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_NETWORK_WATCHLIST);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_NETWORK_WATCHLIST]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_CARRIER_ID_DB);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_CARRIER_ID_DB]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_EMERGENCY_NUMBER_DB);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_EMERGENCY_NUMBER_DB]);

        configUpdateIntent.setAction(ConfigUpdate.ACTION_UPDATE_CONFIG);
        assertEquals(configUpdateIntent.getAction(),
                CONFIG_UPDATE_INTENT[INDEX_INTENT_CONFIG]);

        configUpdateIntent.putExtra(ConfigUpdate.EXTRA_VERSION, "version");
        assertTrue(configUpdateIntent.getExtras()
                .containsKey(CONFIG_UPDATE_EXTRA[INDEX_EXTRA_VERSION]));

        configUpdateIntent.putExtra(ConfigUpdate.EXTRA_REQUIRED_HASH, "hash");
        assertTrue(configUpdateIntent.getExtras()
                .containsKey(CONFIG_UPDATE_EXTRA[INDEX_EXTRA_REQUIRED_HASH]));

        configUpdateIntent.putExtra(ConfigUpdate.EXTRA_DOMAIN, "domain");
        assertTrue(configUpdateIntent.getExtras()
                .containsKey(CONFIG_UPDATE_EXTRA[INDEX_EXTRA_DOMAIN]));
    }
}

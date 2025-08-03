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

package com.android.adservices.service.consent;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Locale;

@SmallTest
public class DeviceRegionProviderTest {

    @Mock private Context mContext;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Flags mMockFlags;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // return the default EEA countries list for most test cases
        doReturn(Flags.UI_EEA_COUNTRIES).when(mMockFlags).getUiEeaCountries();
        Locale.setDefault(Locale.US);
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testGbEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("gb").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void testChEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void testUsEmptyEeaCountriesList() {
        // simulate the case where we update the default list to empty.
        doReturn("").when(mMockFlags).getUiEeaCountries();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void isGbDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("gb").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void isChDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void isEuDeviceTrue() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void isEuDeviceFalse() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void noSimCardInstalledTest() {
        doReturn(Flags.IS_EEA_DEVICE).when(mMockFlags).isEeaDevice();
        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void telephonyManagerDoesntExistTest() {
        doReturn(false).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void telephonyManagerNotAccessibleTest() {
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(null).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceNoSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceUsSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_isEeaDeviceGbSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void deviceRegionFlagOnTest_notEeaDeviceUsSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(false).when(mMockFlags).isEeaDevice();

        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void deviceRegionFlagOnTest_notEeaDeviceChSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(false).when(mMockFlags).isEeaDevice();

        doReturn("ch").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isFalse();
    }

    @Test
    public void deviceRegionFlagOnTest_gbFlagNoSim() {
        doReturn(true).when(mMockFlags).isEeaDeviceFeatureEnabled();
        doReturn(true).when(mMockFlags).isEeaDevice();

        doReturn("").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);

        assertThat(DeviceRegionProvider.isEuDevice(mContext, mMockFlags)).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_defaultList() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(Flags.UI_EEA_COUNTRIES)).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_nullString() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(null)).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_emptyString() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(" ")).isFalse();
    }

    @Test
    public void validEeaCountriesStringTest_singleCountry() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL")).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_multipleCountries() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL,GB,CH")).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_extraComma() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("US,")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_missingComma() {
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PLGB")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_nullString_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(null)).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_emptyString_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString(" ")).isFalse();
    }

    @Test
    public void validEeaCountriesStringTest_singleCountry_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL")).isTrue();
    }

    @Test
    public void validEeaCountriesStringTest_multipleCountries_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PL,GB,CH")).isTrue();
    }

    @Test
    public void invalidEeaCountriesStringTest_extraComma_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("US,")).isFalse();
    }

    @Test
    public void invalidEeaCountriesStringTest_missingComma_arabicAffectedLocale() {
        Locale.setDefault(new Locale("ar", ""));
        assertThat(DeviceRegionProvider.isValidEeaCountriesString("PLGB")).isFalse();
    }

    @Test
    public void eeaCountriesForAllLocales_defaultList() {
        for (Locale locale : Locale.getAvailableLocales()) {
            Locale.setDefault(locale);
            assertThat(DeviceRegionProvider.isValidEeaCountriesString(Flags.UI_EEA_COUNTRIES))
                    .isTrue();
        }
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.SoftApConfiguration;

import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Store data for SoftAp
 */
public class SoftApStoreData implements WifiConfigStore.StoreData {
    private static final String TAG = "SoftApStoreData";
    private static final String XML_TAG_SECTION_HEADER_SOFTAP = "SoftAp";

    private final Context mContext;
    private final SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    private final DataSource mDataSource;

    /**
     * Interface define the data source for the notifier store data.
     */
    public interface DataSource {
        /**
         * Retrieve the SoftAp configuration from the data source to serialize them to disk.
         *
         * @return {@link SoftApConfiguration} Instance of SoftApConfiguration.
         */
        SoftApConfiguration toSerialize();

        /**
         * Set the SoftAp configuration in the data source after serializing them from disk.
         *
         * @param config {@link SoftApConfiguration} Instance of SoftApConfiguration.
         */
        void fromDeserialized(SoftApConfiguration config);

        /**
         * Clear internal data structure in preparation for user switch or initial store read.
         */
        void reset();

        /**
         * Indicates whether there is new data to serialize.
         */
        boolean hasNewDataToSerialize();
    }

    /**
     * Creates the SSID Set store data.
     *
     * @param dataSource The DataSource that implements the update and retrieval of the SSID set.
     */
    SoftApStoreData(Context context, SettingsMigrationDataHolder settingsMigrationDataHolder,
            DataSource dataSource) {
        mContext = context;
        mSettingsMigrationDataHolder = settingsMigrationDataHolder;
        mDataSource = dataSource;
    }

    @Override
    public void serializeData(XmlSerializer out,
            @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        SoftApConfiguration softApConfig = mDataSource.toSerialize();
        if (softApConfig != null) {
            XmlUtil.SoftApConfigurationXmlUtil.writeSoftApConfigurationToXml(out, softApConfig,
                    encryptionUtil);
        }
    }

    @Override
    public void deserializeData(XmlPullParser in, int outerTagDepth,
            @WifiConfigStore.Version int version,
            @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        // Ignore empty reads.
        if (in == null) {
            return;
        }

        SoftApConfiguration softApConfig = XmlUtil.SoftApConfigurationXmlUtil.parseFromXml(
                in, outerTagDepth, mSettingsMigrationDataHolder,
                version >= WifiConfigStore.ENCRYPT_CREDENTIALS_CONFIG_STORE_DATA_VERSION,
                encryptionUtil);
        if (softApConfig != null) {
            mDataSource.fromDeserialized(softApConfig);
        }
    }

    @Override
    public void resetData() {
        mDataSource.reset();
    }

    @Override
    public boolean hasNewDataToSerialize() {
        return mDataSource.hasNewDataToSerialize();
    }

    @Override
    public String getName() {
        return XML_TAG_SECTION_HEADER_SOFTAP;
    }

    @Override
    public @WifiConfigStore.StoreFileId int getStoreFileId() {
        return WifiConfigStore.STORE_FILE_SHARED_SOFTAP; // Shared softap store.
    }
}

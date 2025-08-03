/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.cts

import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY
import com.android.net.module.util.NetworkStackConstants
import com.android.testutils.DeviceConfigRule
import com.android.testutils.runAsShell

/**
 * Collection of utility methods for configuring network validation.
 */
internal object NetworkValidationTestUtil {
    val TAG = NetworkValidationTestUtil::class.simpleName

    /**
     * Clear the test network validation URLs.
     */
    @JvmStatic fun clearValidationTestUrlsDeviceConfig() {
        runAsShell(WRITE_DEVICE_CONFIG) {
            DeviceConfig.setProperty(NAMESPACE_CONNECTIVITY,
                    NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTPS_URL, null, false)
            DeviceConfig.setProperty(NAMESPACE_CONNECTIVITY,
                    NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTP_URL, null, false)
            DeviceConfig.setProperty(NAMESPACE_CONNECTIVITY,
                    NetworkStackConstants.TEST_URL_EXPIRATION_TIME, null, false)
        }
    }

    /**
     * Set the test validation HTTPS URL.
     *
     * @see NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTPS_URL
     */
    @JvmStatic
    fun setHttpsUrlDeviceConfig(rule: DeviceConfigRule, url: String?) =
            rule.setConfig(NAMESPACE_CONNECTIVITY,
                NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTPS_URL, url)

    /**
     * Set the test validation HTTP URL.
     *
     * @see NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTP_URL
     */
    @JvmStatic
    fun setHttpUrlDeviceConfig(rule: DeviceConfigRule, url: String?) =
            rule.setConfig(NAMESPACE_CONNECTIVITY,
                NetworkStackConstants.TEST_CAPTIVE_PORTAL_HTTP_URL, url)

    /**
     * Set the test validation URL expiration.
     *
     * @see NetworkStackConstants.TEST_URL_EXPIRATION_TIME
     */
    @JvmStatic
    fun setUrlExpirationDeviceConfig(rule: DeviceConfigRule, timestamp: Long?) =
            rule.setConfig(NAMESPACE_CONNECTIVITY,
                NetworkStackConstants.TEST_URL_EXPIRATION_TIME, timestamp?.toString())
}

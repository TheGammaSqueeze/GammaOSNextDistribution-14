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
package com.android.tradefed.config.proxy;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Object that allows pointing to a remote configuration to execute. */
public final class ProxyConfiguration {

    public static final String PROXY_CONFIG_TYPE_KEY = "proxy-config";
    private static final String PROXY_CONFIG_OPTION_NAME = "proxy-configuration";

    @Option(
            name = PROXY_CONFIG_OPTION_NAME,
            description = "Point to an external configuration to be run instead.")
    private File mProxyConfig;

    /** Returns whether or not a proxy config value is set. */
    public boolean isProxySet() {
        return mProxyConfig != null;
    }

    /** Returns the current proxy configuration to use. */
    public File getProxyConfig() {
        if (mProxyConfig == null || !mProxyConfig.exists()) {
            CLog.e("No proxy configuration is configured: %s", mProxyConfig);
            return null;
        }
        if (mProxyConfig.isDirectory()) {
            CLog.e("Proxy configuration must be a file, found a directory: %s", mProxyConfig);
            return null;
        }
        return mProxyConfig;
    }

    public static String[] clearCommandline(String[] originalCommand)
            throws ConfigurationException {
        List<String> argsList = new ArrayList<>(Arrays.asList(originalCommand));
        try {
            while (argsList.contains("--" + PROXY_CONFIG_OPTION_NAME)) {
                int index = argsList.indexOf("--" + PROXY_CONFIG_OPTION_NAME);
                if (index != -1) {
                    argsList.remove(index + 1);
                    argsList.remove(index);
                }
            }
        } catch (RuntimeException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
        return argsList.toArray(new String[0]);
    }
}

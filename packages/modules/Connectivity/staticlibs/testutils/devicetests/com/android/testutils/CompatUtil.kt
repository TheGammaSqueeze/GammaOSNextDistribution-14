/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.testutils

import android.net.NetworkSpecifier
import com.android.modules.utils.build.SdkLevel.isAtLeastS

/**
 * Test utility to create [NetworkSpecifier]s on different SDK versions.
 */
object CompatUtil {
    @JvmStatic
    fun makeTestNetworkSpecifier(ifName: String): NetworkSpecifier {
        // Until R, there was no TestNetworkSpecifier, StringNetworkSpecifier was used instead
        if (!isAtLeastS()) {
            return makeNetworkSpecifierInternal("android.net.StringNetworkSpecifier", ifName)
        }
        // TestNetworkSpecifier is not part of the SDK in some branches using this utility
        // TODO: replace with a direct call to the constructor
        return makeNetworkSpecifierInternal("android.net.TestNetworkSpecifier", ifName)
    }

    @JvmStatic
    fun makeEthernetNetworkSpecifier(ifName: String): NetworkSpecifier {
        // Until R, there was no EthernetNetworkSpecifier, StringNetworkSpecifier was used instead
        if (!isAtLeastS()) {
            return makeNetworkSpecifierInternal("android.net.StringNetworkSpecifier", ifName)
        }
        // EthernetNetworkSpecifier is not part of the SDK in some branches using this utility
        // TODO: replace with a direct call to the constructor
        return makeNetworkSpecifierInternal("android.net.EthernetNetworkSpecifier", ifName)
    }

    private fun makeNetworkSpecifierInternal(clazz: String, specifier: String): NetworkSpecifier {
        // StringNetworkSpecifier was removed after R (and was hidden API before that)
        return Class.forName(clazz)
                .getConstructor(String::class.java).newInstance(specifier) as NetworkSpecifier
    }
}

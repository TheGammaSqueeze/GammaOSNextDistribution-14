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

package com.android.cts.packagemanager.verify.domain.java

object DomainUtils {
    private const val BASE_PACKAGE = "com.android.cts.packagemanager.verify.domain"

    const val DOMAIN_TLD = "pmctstesting"
    const val DOMAIN_1 = "$BASE_PACKAGE.1.$DOMAIN_TLD"
    const val DOMAIN_2 = "$BASE_PACKAGE.2.$DOMAIN_TLD"
    const val DOMAIN_3 = "$BASE_PACKAGE.3.$DOMAIN_TLD"
    const val DOMAIN_7 = "$BASE_PACKAGE.7.$DOMAIN_TLD"
    const val DOMAIN_UNHANDLED = "$BASE_PACKAGE.unhandled.$DOMAIN_TLD"

    const val CALLING_PKG_NAME = "$BASE_PACKAGE.callingapp"

    const val DECLARING_PKG_NAME_BASE = "$BASE_PACKAGE.declaringapp"
    private const val DECLARING_PKG_APK_BASE = "CtsDomainVerificationTestDeclaringApp"

    const val DECLARING_PKG_NAME_1 = "${DECLARING_PKG_NAME_BASE}1"
    val DECLARING_PKG_APK_1 = ApkName("${DECLARING_PKG_APK_BASE}1.apk")

    const val DECLARING_PKG_NAME_2 = "${DECLARING_PKG_NAME_BASE}2"
    val DECLARING_PKG_APK_2 = ApkName("${DECLARING_PKG_APK_BASE}2.apk")

    inline class ApkName(val value: String)

    val ALL_PACKAGES = listOf(CALLING_PKG_NAME, DECLARING_PKG_NAME_1, DECLARING_PKG_NAME_2)

    fun resetAppLinks(packageName: String) = "pm reset-app-links $packageName"

    fun setAppLinks(packageName: String, state: String, vararg domains: String) =
        "pm set-app-links --package $packageName $state " +
                domains.joinToString(separator = " ")

    fun setAppLinksAllowed(packageName: String, userId: Int, enabled: Boolean) =
        "pm set-app-links-allowed --package $packageName --user $userId $enabled"

    fun setAppLinksUserSelection(
        packageName: String,
        userId: Int,
        enabled: Boolean,
        vararg domains: String
    ) = "pm set-app-links-user-selection --package $packageName --user $userId $enabled " +
            domains.joinToString(separator = " ")
}

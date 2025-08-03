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

package android.packageinstaller.contentprovider.cts.withvisibility

import android.packageinstaller.contentprovider.cts.ContentProviderAppVisibilityTestsBase
import org.junit.Test

class WithVisibilityContentProviderTest : ContentProviderAppVisibilityTestsBase() {
    @Test
    fun whenUnprotectedContentProviderAccessed_TestCanFindIt() {
        runTest(TYPE_UNPROTECTED_CONTENT_PROVIDER)
        assertErrorDialogVisible()
        assertResultFirstUser()
    }

    @Test
    fun whenExportedContentProviderIsAccessed_WithInvalidUriPermissionPattern_TestCanFindIt() {
        runTest(TYPE_EXPORTED_CONTENT_PROVIDER, permissionPattern = "/foo/baz")
        assertErrorDialogVisible()
        assertResultFirstUser()
    }

    @Test
    fun whenExportedContentProviderIsAccessed_WithValidUriPermissionPattern_TestCanFindIt() {
        runTest(TYPE_EXPORTED_CONTENT_PROVIDER, permissionPattern = "/foo/bar/baz")
        assertErrorDialogVisible()
        assertResultFirstUser()
    }

    @Test
    fun whenUnexportedContentProviderIsAccessed_WithInvalidUriPermissionPattern_TestCantFindIt() {
        runTest(TYPE_UNEXPORTED_CONTENT_PROVIDER, permissionPattern = "/foo")
        assertErrorDialogNotVisible()
        assertResultFirstUser()
    }

    @Test
    fun whenUnexportedContentProviderIsAccessed_WithValidUriPermissionPattern_TestCantFindIt() {
        runTest(TYPE_UNEXPORTED_CONTENT_PROVIDER, permissionPattern = "/foo/bar/baz")
        assertErrorDialogNotVisible()
        assertResultFirstUser()
    }
}

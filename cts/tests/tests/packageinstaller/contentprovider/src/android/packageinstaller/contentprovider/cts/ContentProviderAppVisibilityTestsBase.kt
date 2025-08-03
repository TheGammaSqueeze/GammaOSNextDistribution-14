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

package android.packageinstaller.contentprovider.cts

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import kotlin.test.junit.JUnitAsserter.fail

open class ContentProviderAppVisibilityTestsBase {

    private var mScenario: ActivityScenario<TestActivity>? = null
    private val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mContext: Context = mInstrumentation.context
    private val mUiDevice: UiDevice = UiDevice.getInstance(mInstrumentation)
    private var mDialog: UiObject2? = null
    private var mButton: UiObject2? = null

    val UNEXPORTED_CONTENT_PROVIDER_AUTH = "android.packageinstaller.unexportedcontentprovider"
    val EXPORTED_CONTENT_PROVIDER_AUTH = "android.packageinstaller.exportedcontentprovider"
    val UNPROTECTED_CONTENT_PROVIDER_AUTH = "android.packageinstaller.unprotectedcontentprovider"

    val PACKAGE_INSTALLER = getPackageInstallerPackageName()
    val INSTALL_START_CLASS = "com.android.packageinstaller.InstallStart"

    private val INSTALL_FAIL_DIALOG_TEXT = "There was a problem parsing the package."
    private val DEFAULT_TIMEOUT: Long = 5000

    val TYPE_EXPORTED_CONTENT_PROVIDER = 1
    val TYPE_UNEXPORTED_CONTENT_PROVIDER = 2
    val TYPE_UNPROTECTED_CONTENT_PROVIDER = 3

    class TestActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val contentSchemeIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT,
                                                                Intent::class.java)
            startActivityForResult(contentSchemeIntent, 1)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            setResult(resultCode, data)
            finish()
        }
    }

    private fun getPackageInstallerPackageName(): String {
        val installerIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        installerIntent.setDataAndType(Uri.parse("content://com.example/"),
                "application/vnd.android.package-archive")
        return installerIntent.resolveActivityInfo(mContext.packageManager, MATCH_DEFAULT_ONLY)
                .packageName
    }

    fun runTest(contentProviderType: Int){
        runTest(contentProviderType, ""/* permissionPattern */)
    }

    fun runTest(contentProviderType: Int, permissionPattern: String) {
        val intent = Intent(mContext, TestActivity::class.java)
        intent.putExtra(Intent.EXTRA_INTENT, getContentSchemeIntent(contentProviderType,
                                                                    permissionPattern))

        mScenario = ActivityScenario.launchActivityForResult(intent)
        mUiDevice.wait(Until.findObject(By.text(INSTALL_FAIL_DIALOG_TEXT)), DEFAULT_TIMEOUT)
        mDialog = mUiDevice.findObject(By.text(INSTALL_FAIL_DIALOG_TEXT))
        mButton = mUiDevice.findObject(By.text("OK"))
    }

    private fun getContentSchemeIntent(contentProviderType: Int, permissionPattern: String): Intent {
        val intent = Intent()
        intent.setPackage(PACKAGE_INSTALLER)
        intent.component = ComponentName(PACKAGE_INSTALLER, INSTALL_START_CLASS)
        val builder = Uri.Builder()
        builder.scheme(ContentResolver.SCHEME_CONTENT)
        when (contentProviderType) {
            TYPE_EXPORTED_CONTENT_PROVIDER -> {
                builder.authority(EXPORTED_CONTENT_PROVIDER_AUTH)
            }
            TYPE_UNEXPORTED_CONTENT_PROVIDER -> {
                builder.authority(UNEXPORTED_CONTENT_PROVIDER_AUTH)
            }
            else -> {
                builder.authority(UNPROTECTED_CONTENT_PROVIDER_AUTH)
            }
        }
        builder.path(permissionPattern)
        intent.data = builder.build()
        return intent
    }

    fun assertErrorDialogVisible() {
        if (mDialog != null && mButton != null && mButton!!.isEnabled) {
            mButton!!.click()
        } else {
            fail("Package installer error dialog should be shown.")
        }
    }

    fun assertErrorDialogNotVisible() {
        if (mDialog != null || mButton != null) {
            fail("Package installer error dialog should not be shown.")
        }
    }

    fun assertResultFirstUser() {
        assertThat(mScenario!!.result.resultCode).isEqualTo(Activity.RESULT_FIRST_USER)
    }
}

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

package com.android.permissioncontroller.permission.util

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SHOW_APP_INFO
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/** Unit tests for [KotlinUtils]. */
@RunWith(AndroidJUnit4::class)
class KotlinUtilsTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun convertToBitmap_argb888BitmapDrawable_returnsSameBitmap() {
        val bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 10, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(targetContext.resources, bitmap)

        assertThat(KotlinUtils.convertToBitmap(drawable).sameAs(bitmap)).isTrue()
    }

    @Test
    fun convertToBitmap_noIntrinsicSize_throws() {
        val drawable = FakeDrawable(intrinsicSize = 0)
        assertFailsWith<IllegalArgumentException> { KotlinUtils.convertToBitmap(drawable) }
    }

    class FakeDrawable(private val intrinsicSize: Int) : Drawable() {
        override fun getIntrinsicWidth() = intrinsicSize
        override fun getIntrinsicHeight() = intrinsicSize

        override fun draw(canvas: Canvas) = Unit // no-op
        override fun getOpacity() = throw UnsupportedOperationException()
        override fun setAlpha(alpha: Int) = throw UnsupportedOperationException()
        override fun setColorFilter(colorFilter: ColorFilter?) =
            throw UnsupportedOperationException()
    }

    @Test
    fun getAppStoreIntent_returnsResolvedIntent() {
        val installerPackage = "installer"
        val installerActivity = "activity"
        val appPackage = "app"
        val mockContext = mock(Context::class.java)
        val mockPackageManager = mock(PackageManager::class.java)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        val installerIntent = Intent(ACTION_SHOW_APP_INFO).setPackage(installerPackage)
        whenever(
                mockPackageManager.resolveActivity(
                    argThat { intent -> intent.filterEquals(installerIntent) },
                    /* flags= */ anyInt()
                )
            )
            .thenReturn(
                ResolveInfo().apply {
                    activityInfo =
                        ActivityInfo().apply {
                            packageName = installerPackage
                            name = installerActivity
                        }
                }
            )

        val intent = KotlinUtils.getAppStoreIntent(mockContext, installerPackage, appPackage)

        assertThat(intent).isNotNull()
        with(intent!!) {
            assertThat(action).isEqualTo(ACTION_SHOW_APP_INFO)
            assertThat(component?.packageName).isEqualTo(installerPackage)
            assertThat(component?.className).isEqualTo(installerActivity)
        }
    }

    @Test
    fun getAppStoreIntent_returnsAppPackageInExtras() {
        val appPackage = "app"
        val mockContext = mock(Context::class.java)
        val mockPackageManager = mock(PackageManager::class.java)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.resolveActivity(any(), /* flags= */ anyInt()))
            .thenReturn(
                ResolveInfo().apply {
                    activityInfo =
                        ActivityInfo().apply {
                            packageName = ""
                            name = ""
                        }
                }
            )

        val intent = KotlinUtils.getAppStoreIntent(mockContext, "com.installer", appPackage)

        assertThat(intent).isNotNull()
        assertThat(intent?.extras?.getString(EXTRA_PACKAGE_NAME)).isEqualTo(appPackage)
    }

    @Test
    fun getAppStoreIntent_returnsNullWhenInstallerNotResolved() {
        val mockContext = mock(Context::class.java)
        whenever(mockContext.packageManager).thenReturn(mock(PackageManager::class.java))
        // Un-stubbed activity resolution will return null.

        assertThat(KotlinUtils.getAppStoreIntent(mockContext, "com.installer", "com.app")).isNull()
    }

    @Test
    fun getMimeTypeForPermissions_onlyReadMediaImages_returnsImage() {
        assertThat(KotlinUtils.getMimeTypeForPermissions(listOf(READ_MEDIA_IMAGES, "read memes")))
            .isEqualTo("image/*")
    }

    @Test
    fun getMimeTypeForPermissions_onlyReadMediaVideo_returnsVideo() {
        assertThat(KotlinUtils.getMimeTypeForPermissions(listOf("write memes", READ_MEDIA_VIDEO)))
            .isEqualTo("video/*")
    }

    @Test
    fun getMimeTypeForPermissions_bothReadMediaPermissions_returnsNull() {
        assertThat(
                KotlinUtils.getMimeTypeForPermissions(listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO))
            )
            .isNull()
    }

    @Test
    fun getMimeTypeForPermissions_noReadMediaPermissions_returnsNull() {
        assertThat(KotlinUtils.getMimeTypeForPermissions(listOf("amazing permission"))).isNull()
    }

    @Test
    fun getMimeTypeForPermissions_emptyList_returnsNull() {
        assertThat(KotlinUtils.getMimeTypeForPermissions(emptyList())).isNull()
    }
}

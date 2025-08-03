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

package com.android.intentresolver

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.SelectableTargetInfo
import com.android.intentresolver.chooser.TargetInfo
import com.android.intentresolver.icons.TargetDataLoader
import com.android.intentresolver.logging.EventLogImpl
import com.android.intentresolver.widget.BadgeTextView
import com.android.internal.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class ChooserListAdapterTest {
    private val userHandle: UserHandle =
        InstrumentationRegistry.getInstrumentation().targetContext.user

    private val packageManager =
        mock<PackageManager> {
            whenever(resolveActivity(any(), any<ResolveInfoFlags>())).thenReturn(mock())
        }
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val resolverListController = mock<ResolverListController>()
    private val appLabel = "App"
    private val targetLabel = "Target"
    private val mEventLog = mock<EventLogImpl>()
    private val mTargetDataLoader = mock<TargetDataLoader>()
    private val mPackageChangeCallback = mock<ChooserListAdapter.PackageChangeCallback>()
    private val featureFlags = FeatureFlagsImpl()

    private val testSubject by lazy {
        ChooserListAdapter(
            context,
            emptyList(),
            emptyArray(),
            emptyList(),
            false,
            resolverListController,
            userHandle,
            Intent(),
            Intent(),
            mock(),
            packageManager,
            mEventLog,
            0,
            null,
            mTargetDataLoader,
            mPackageChangeCallback,
            featureFlags,
        )
    }

    @Before
    fun setup() {
        // ChooserListAdapter reads DeviceConfig and needs a permission for that.
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .adoptShellPermissionIdentity("android.permission.READ_DEVICE_CONFIG")
    }

    @Test
    fun testDirectShareTargetLoadingIconIsStarted() {
        val view = createView()
        val viewHolder = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolder
        val targetInfo = createSelectableTargetInfo()
        testSubject.onBindView(view, targetInfo, 0)

        verify(mTargetDataLoader, times(1)).loadDirectShareIcon(any(), any(), any())
    }

    @Test
    fun onBindView_DirectShareTargetIconAndLabelLoadedOnlyOnce() {
        val view = createView()
        val viewHolderOne = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderOne
        val targetInfo = createSelectableTargetInfo()
        testSubject.onBindView(view, targetInfo, 0)

        val viewHolderTwo = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderTwo

        testSubject.onBindView(view, targetInfo, 0)

        verify(mTargetDataLoader, times(1)).loadDirectShareIcon(any(), any(), any())
    }

    @Test
    fun onBindView_AppTargetIconAndLabelLoadedOnlyOnce() {
        val view = createView()
        val viewHolderOne = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderOne
        val targetInfo =
            DisplayResolveInfo.newDisplayResolveInfo(
                Intent(),
                ResolverDataProvider.createResolveInfo(2, 0, userHandle),
                null,
                "extended info",
                Intent()
            )
        testSubject.onBindView(view, targetInfo, 0)

        val viewHolderTwo = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderTwo

        testSubject.onBindView(view, targetInfo, 0)

        verify(mTargetDataLoader, times(1)).loadAppTargetIcon(any(), any(), any())
    }

    @Test
    fun onBindView_contentDescription() {
        val view = createView()
        val viewHolder = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolder
        val targetInfo = createSelectableTargetInfo()
        testSubject.onBindView(view, targetInfo, 0)

        assertThat(view.contentDescription).isEqualTo("$targetLabel  $appLabel")
    }

    @Test
    fun onBindView_contentDescriptionPinned() {
        val view = createView()
        val viewHolder = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolder
        val targetInfo = createSelectableTargetInfo(true)
        testSubject.onBindView(view, targetInfo, 0)

        assertThat(view.contentDescription).isEqualTo("$targetLabel  $appLabel. Pinned")
    }

    @Test
    fun onBindView_displayInfoContentDescriptionPinned() {
        val view = createView()
        val viewHolder = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolder
        val targetInfo = createDisplayResolveInfo(isPinned = true)
        testSubject.onBindView(view, targetInfo, 0)

        assertThat(view.contentDescription).isEqualTo("$appLabel. Pinned")
    }

    @Test
    fun handlePackagesChanged_invokesCallback() {
        testSubject.handlePackagesChanged()
        verify(mPackageChangeCallback, times(1)).beforeHandlingPackagesChanged()
    }

    private fun createSelectableTargetInfo(isPinned: Boolean = false): TargetInfo {
        val shortcutInfo =
            createShortcutInfo("id-1", ComponentName("pkg", "Class"), 1).apply {
                if (isPinned) {
                    addFlags(ShortcutInfo.FLAG_PINNED)
                }
            }
        return SelectableTargetInfo.newSelectableTargetInfo(
            /* sourceInfo = */ createDisplayResolveInfo(isPinned),
            /* backupResolveInfo = */ mock(),
            /* resolvedIntent = */ Intent(),
            /* chooserTarget = */ createChooserTarget(
                targetLabel,
                0.5f,
                ComponentName("pkg", "Class"),
                "id-1"
            ),
            /* modifiedScore = */ 1f,
            shortcutInfo,
            /* appTarget */ null,
            /* referrerFillInIntent = */ Intent()
        )
    }

    private fun createDisplayResolveInfo(isPinned: Boolean = false): DisplayResolveInfo =
        DisplayResolveInfo.newDisplayResolveInfo(
                Intent(),
                ResolverDataProvider.createResolveInfo(2, 0, userHandle),
                appLabel,
                "extended info",
                Intent(),
            )
            .apply {
                if (isPinned) {
                    setPinned(true)
                }
            }

    private fun createView(): View {
        val view = FrameLayout(context)
        if (featureFlags.bespokeLabelView()) {
                BadgeTextView(context)
            } else {
                TextView(context)
            }
            .apply {
                id = R.id.text1
                view.addView(this)
            }
        TextView(context).apply {
            id = R.id.text2
            view.addView(this)
        }
        ImageView(context).apply {
            id = R.id.icon
            view.addView(this)
        }
        return view
    }
}

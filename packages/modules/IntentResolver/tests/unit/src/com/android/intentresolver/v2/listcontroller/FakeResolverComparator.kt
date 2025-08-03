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

package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Message
import android.os.UserHandle
import com.android.intentresolver.ResolvedComponentInfo
import com.android.intentresolver.chooser.TargetInfo
import com.android.intentresolver.model.AbstractResolverComparator
import com.android.intentresolver.whenever
import java.util.Locale
import org.mockito.Mockito

class FakeResolverComparator(
    context: Context =
        Mockito.mock(Context::class.java).also {
            val mockResources = Mockito.mock(Resources::class.java)
            whenever(it.resources).thenReturn(mockResources)
            whenever(mockResources.configuration)
                .thenReturn(Configuration().apply { setLocale(Locale.US) })
        },
    targetIntent: Intent = Intent("TestAction"),
    resolvedActivityUserSpaceList: List<UserHandle> = emptyList(),
    promoteToFirst: ComponentName? = null,
) :
    AbstractResolverComparator(
        context,
        targetIntent,
        resolvedActivityUserSpaceList,
        promoteToFirst,
    ) {
    var lastUpdateModel: TargetInfo? = null
        private set
    var lastUpdateChooserCounts: Triple<String, UserHandle, String>? = null
        private set
    var destroyCalled = false
        private set

    override fun compare(lhs: ResolveInfo?, rhs: ResolveInfo?): Int =
        lhs!!.activityInfo.packageName.compareTo(rhs!!.activityInfo.packageName)

    override fun doCompute(targets: MutableList<ResolvedComponentInfo>?) {}

    override fun getScore(targetInfo: TargetInfo?): Float = 1.23f

    override fun handleResultMessage(message: Message?) {}

    override fun updateModel(targetInfo: TargetInfo?) {
        lastUpdateModel = targetInfo
    }

    override fun updateChooserCounts(
        packageName: String,
        user: UserHandle,
        action: String,
    ) {
        lastUpdateChooserCounts = Triple(packageName, user, action)
    }

    override fun destroy() {
        destroyCalled = true
    }
}

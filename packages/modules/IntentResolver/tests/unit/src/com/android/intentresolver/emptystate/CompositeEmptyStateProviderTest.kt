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

package com.android.intentresolver.emptystate

import com.android.intentresolver.ResolverListAdapter
import com.android.intentresolver.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompositeEmptyStateProviderTest {
    val listAdapter = mock<ResolverListAdapter>()

    val emptyState1 = object : EmptyState {}
    val emptyState2 = object : EmptyState {}

    val positiveEmptyStateProvider1 =
        object : EmptyStateProvider {
            override fun getEmptyState(listAdapter: ResolverListAdapter) = emptyState1
        }
    val positiveEmptyStateProvider2 =
        object : EmptyStateProvider {
            override fun getEmptyState(listAdapter: ResolverListAdapter) = emptyState2
        }
    val nullEmptyStateProvider =
        object : EmptyStateProvider {
            override fun getEmptyState(listAdapter: ResolverListAdapter) = null
        }

    @Test
    fun testComposedProvider_returnsFirstEmptyStateInOrder() {
        val provider =
            CompositeEmptyStateProvider(
                nullEmptyStateProvider,
                positiveEmptyStateProvider1,
                positiveEmptyStateProvider2
            )
        assertThat(provider.getEmptyState(listAdapter)).isSameInstanceAs(emptyState1)
    }

    @Test
    fun testComposedProvider_allProvidersReturnNull_composedResultIsNull() {
        val provider = CompositeEmptyStateProvider(nullEmptyStateProvider)
        assertThat(provider.getEmptyState(listAdapter)).isNull()
    }

    @Test
    fun testComposedProvider_noEmptyStateIfNoDelegateProviders() {
        val provider = CompositeEmptyStateProvider()
        assertThat(provider.getEmptyState(listAdapter)).isNull()
    }
}

/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.v2

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.service.chooser.ChooserAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.ChooserRequestParameters
import com.android.intentresolver.logging.EventLog
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertWithMessage
import java.util.Optional
import java.util.function.Consumer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChooserMutableActionFactoryTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val logger = mock<EventLog>()
    private val testAction = "com.android.intentresolver.testaction"
    private val resultConsumer =
        object : Consumer<Int> {
            var latestReturn = Integer.MIN_VALUE

            override fun accept(resultCode: Int) {
                latestReturn = resultCode
            }
        }

    private val scope = TestScope()

    @Test
    fun testInitialValue() =
        scope.runTest {
            val actions = createChooserActions(2)
            val actionFactory = createFactory(actions)
            val testSubject = ChooserMutableActionFactory(actionFactory)

            val createdActions = testSubject.createCustomActions()
            val observedActions = testSubject.customActionsFlow.first()

            assertWithMessage("Unexpected actions")
                .that(createdActions.map { it.label })
                .containsExactlyElementsIn(actions.map { it.label })
                .inOrder()
            assertWithMessage("Initially created and initially observed actions should be the same")
                .that(createdActions)
                .containsExactlyElementsIn(observedActions)
                .inOrder()
        }

    @Test
    fun testUpdateActions_newActionsPublished() =
        scope.runTest {
            val initialActions = createChooserActions(2)
            val updatedActions = createChooserActions(3)
            val actionFactory = createFactory(initialActions)
            val testSubject = ChooserMutableActionFactory(actionFactory)

            testSubject.updateCustomActions(updatedActions)
            val observedActions = testSubject.customActionsFlow.first()

            assertWithMessage("Unexpected updated actions")
                .that(observedActions.map { it.label })
                .containsAtLeastElementsIn(updatedActions.map { it.label })
                .inOrder()
        }

    private fun createFactory(actions: List<ChooserAction>): ChooserActionFactory {
        val targetIntent = Intent()
        val chooserRequest = mock<ChooserRequestParameters>()
        whenever(chooserRequest.targetIntent).thenReturn(targetIntent)
        whenever(chooserRequest.chooserActions).thenReturn(ImmutableList.copyOf(actions))

        return ChooserActionFactory(
            /* context = */ context,
            /* targetIntent = */ chooserRequest.targetIntent,
            /* referrerPackageName = */ chooserRequest.referrerPackageName,
            /* chooserActions = */ chooserRequest.chooserActions,
            /* modifyShareAction = */ chooserRequest.modifyShareAction,
            /* imageEditor = */ Optional.empty(),
            /* log = */ logger,
            /* onUpdateSharedTextIsExcluded = */ {},
            /* firstVisibleImageQuery = */ { null },
            /* activityStarter = */ mock(),
            /* shareResultSender = */ null,
            /* finishCallback = */ resultConsumer,
            mock()
        )
    }

    private fun createChooserActions(count: Int): List<ChooserAction> {
        return buildList(count) {
            for (i in 1..count) {
                val testPendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        i,
                        Intent(testAction),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                val action =
                    ChooserAction.Builder(
                            Icon.createWithResource("", Resources.ID_NULL),
                            "Label $i",
                            testPendingIntent
                        )
                        .build()
                add(action)
            }
        }
    }
}

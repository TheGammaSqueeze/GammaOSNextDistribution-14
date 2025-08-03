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

package android.tools.parsers.view

import android.tools.traces.parsers.view.ViewFrameBuilder
import com.android.app.viewcapture.data.ViewNode
import com.google.common.truth.Truth
import org.junit.Test

/** Tests for [ViewFrameBuilder] */
class ViewFrameBuilderTest {
    @Test
    fun canCreateViewFrame() {
        val frame = ViewFrameBuilder().forSystemUptime(1).fromRootNode(TEST_NODE).build()

        Truth.assertWithMessage("Unable to parse timestamp")
            .that(frame.timestamp.systemUptimeNanos)
            .isGreaterThan(0)

        Truth.assertWithMessage("Unable to parse timestamp").that(frame.root.id).isEqualTo("TEST")
    }

    @Test(expected = IllegalArgumentException::class)
    fun failCreateFieldFrameWithoutTimestamp() {
        ViewFrameBuilder().fromRootNode(TEST_NODE).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun failCreateFieldFrameWithoutNode() {
        ViewFrameBuilder().forSystemUptime(1).build()
    }

    companion object {
        private val TEST_NODE = ViewNode.newBuilder().setId("TEST").build()
    }
}

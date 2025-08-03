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

package android.tools.datatypes

class ActiveBufferTest : DatatypeTest<ActiveBuffer>() {
    override val valueEmpty = ActiveBuffer.EMPTY
    override val valueTest = ActiveBuffer.from(1, 2, 3, 4)
    override val valueEqual = ActiveBuffer.from(1, 2, 3, 4)
    override val valueDifferent = ActiveBuffer.from(5, 6, 7, 8)
    override val expectedValueAString = "w:1, h:2, stride:3, format:4"
}

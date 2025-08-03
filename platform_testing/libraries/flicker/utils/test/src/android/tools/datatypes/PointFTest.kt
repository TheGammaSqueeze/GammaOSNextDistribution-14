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

class PointFTest : DatatypeTest<PointF>() {
    override val valueEmpty = PointF.EMPTY
    override val valueTest = PointF.from(0.1f, 1.1f)
    override val valueEqual = PointF.from(0.1f, 1.1f)
    override val valueDifferent = PointF.from(2f, 3f)
    override val expectedValueAString = "(0.1, 1.1)"
}

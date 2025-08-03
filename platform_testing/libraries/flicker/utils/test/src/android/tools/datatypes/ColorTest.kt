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

class ColorTest : DatatypeTest<Color>() {
    override val valueEmpty = Color.EMPTY
    override val valueTest = Color.from(0.1f, 1.1f, 2.1f, 3.1f)
    override val valueEqual = Color.from(0.1f, 1.1f, 2.1f, 3.1f)
    override val valueDifferent = Color.from(4f, 5f, 6f, 7f)
    override val expectedValueAString = "r:0.1 g:1.1 b:2.1 a:3.1"
}

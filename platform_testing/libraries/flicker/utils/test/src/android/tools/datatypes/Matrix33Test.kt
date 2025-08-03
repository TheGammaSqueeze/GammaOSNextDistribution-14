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

import org.junit.Assert
import org.junit.Test

class Matrix33Test : DatatypeTest<Matrix33>() {
    override val valueEmpty = Matrix33.EMPTY
    override val valueTest = Matrix33.from(0f, 1f, 2f, 3f, 4f, 5f)
    override val valueEqual = Matrix33.from(0f, 1f, 2f, 3f, 4f, 5f)
    override val valueDifferent = Matrix33.from(6f, 7f, 8f, 9f, 10f, 11f)
    override val expectedValueAString =
        "dsdx:0.0   dtdx:1.0   dsdy:3.0   dtdy:4.0   tx:2.0   ty:5.0"

    @Test
    fun createIdentityTest() {
        validateCreation(
            createManualMatrix = { tx, ty ->
                Matrix33.from(dsdx = 1f, dtdx = 0f, tx, dsdy = 0f, dtdy = 1f, ty)
            },
            createMatrix = { tx, ty -> Matrix33.identity(tx, ty) }
        )
    }

    @Test
    fun createRot90Test() {
        validateCreation(
            createManualMatrix = { tx, ty ->
                Matrix33.from(dsdx = 0f, dtdx = 1f, tx, dsdy = -1f, dtdy = 0f, ty)
            },
            createMatrix = { tx, ty -> Matrix33.rot90(tx, ty) }
        )
    }

    @Test
    fun createRot180Test() {
        validateCreation(
            createManualMatrix = { tx, ty ->
                Matrix33.from(dsdx = -1f, dtdx = 0f, tx, dsdy = 0f, dtdy = -1f, ty)
            },
            createMatrix = { tx, ty -> Matrix33.rot180(tx, ty) }
        )
    }

    @Test
    fun createRot270Test() {
        validateCreation(
            createManualMatrix = { tx, ty ->
                Matrix33.from(dsdx = 0f, dtdx = -1f, tx, dsdy = 1f, dtdy = 0f, ty)
            },
            createMatrix = { tx, ty -> Matrix33.rot270(tx, ty) }
        )
    }

    private fun validateCreation(
        createManualMatrix: (tx: Float, ty: Float) -> Matrix33,
        createMatrix: (tx: Float, ty: Float) -> Matrix33
    ) {
        val tx = 1f
        val ty = 2f
        val valueManual = createManualMatrix(0f, 0f)
        val valueDefault = createMatrix(0f, 0f)
        val valueWithOffset = createMatrix(tx, ty)
        Assert.assertSame(valueManual, valueDefault)
        Assert.assertEquals(tx, valueWithOffset.tx)
        Assert.assertEquals(ty, valueWithOffset.ty)
    }
}

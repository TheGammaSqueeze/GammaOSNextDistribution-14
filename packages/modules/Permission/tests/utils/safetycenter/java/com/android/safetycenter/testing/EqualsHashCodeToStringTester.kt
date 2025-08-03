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

package com.android.safetycenter.testing

import android.os.Bundle
import android.os.Parcelable
import androidx.test.core.os.Parcelables.forceParcel
import com.google.common.base.Equivalence
import com.google.common.testing.EqualsTester
import com.google.common.testing.EquivalenceTester

/**
 * A class similar to [EqualsTester] that also optionally checks that the [Object.hashCode],
 * [Object.toString] and [Parcelable] implementations are consistent with equality groups.
 *
 * Note: this class assumes that [Object.hashCode] does not create a collision for equality groups,
 * however this can be disabled by setting [ignoreHashCode] to `true`.
 *
 * Note: this class assumes that [Object.toString] only represents the state that is used in
 * [Object.equals] and [Object.hashCode] implementation. Objects with [Bundle] fields may break it.
 * This can be disabled by setting [ignoreToString] to `true`.
 *
 * @param parcelRoundTripEqualsEquivalence optionally provide an equivalence that also checks that
 *   the [Parcelable] implementation is consistent with equality groups by recreating equal items
 *   from their [Parcelable] implementation
 * @param createCopy optionally provide a custom method to create an equal copy that will be applied
 *   to all the items in provided in an equality group
 */
class EqualsHashCodeToStringTester<T>
private constructor(
    private val ignoreHashCode: Boolean = false,
    private val ignoreToString: Boolean = false,
    private val parcelRoundTripEqualsEquivalence: Equivalence<T>? = null,
    private val createCopy: ((T) -> T)? = null
) {

    private val equalsTester = EqualsTester()
    private val hashCodeTester =
        EquivalenceTester.of<T>(hashCodeEquivalence()).takeIf { !ignoreHashCode }
    private val toStringTester =
        EquivalenceTester.of<T>(toStringEquivalence()).takeIf { !ignoreToString }
    private val parcelableTester =
        parcelRoundTripEqualsEquivalence?.let { EquivalenceTester.of(it) }

    fun addEqualityGroup(vararg equalItems: T): EqualsHashCodeToStringTester<T> {
        val equalItemsWithCopiesIfNeeded = equalItems.toList().withCopiesIfNeeded(createCopy)
        equalsTester.addEqualityGroup(*equalItemsWithCopiesIfNeeded.toAnyArray())
        hashCodeTester?.addEquivalenceGroup(equalItemsWithCopiesIfNeeded)
        toStringTester?.addEquivalenceGroup(equalItemsWithCopiesIfNeeded)
        parcelableTester?.addEquivalenceGroup(equalItemsWithCopiesIfNeeded)
        return this
    }

    fun test() {
        equalsTester.testEquals()
        hashCodeTester?.test()
        toStringTester?.test()
        parcelableTester?.test()
    }

    companion object {

        /**
         * Returns an [EqualsHashCodeToStringTester] that also checks that the [Parcelable]
         * implementation: i.e. recreating an instance from its [Parcelable] implementation returns
         * an object that's consistent with its equality group.
         *
         * @see EqualsHashCodeToStringTester
         */
        fun <T : Parcelable> ofParcelable(
            parcelableCreator: Parcelable.Creator<T>,
            ignoreHashCode: Boolean = false,
            ignoreToString: Boolean = false,
            createCopy: ((T) -> T)? = null
        ): EqualsHashCodeToStringTester<T> =
            EqualsHashCodeToStringTester(
                ignoreHashCode,
                ignoreToString,
                parcelRoundTripEqualsEquivalence(parcelableCreator),
                createCopy
            )

        /**
         * Returns an [EqualsHashCodeToStringTester] that does not check the [Parcelable]
         * implementation of the class, typically if the class doesn't implement [Parcelable].
         *
         * @see EqualsHashCodeToStringTester
         */
        fun <T> of(
            ignoreHashCode: Boolean = false,
            ignoreToString: Boolean = false,
            createCopy: ((T) -> T)? = null
        ): EqualsHashCodeToStringTester<T> =
            EqualsHashCodeToStringTester(
                ignoreHashCode,
                ignoreToString,
                parcelRoundTripEqualsEquivalence = null,
                createCopy
            )

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff they are still
         * equal when one of them is recreated from their [Parcelable] implementation.
         */
        private fun <T : Parcelable> parcelRoundTripEqualsEquivalence(
            parcelableCreator: Parcelable.Creator<T>
        ) =
            object : Equivalence<T>() {

                override fun doEquivalent(a: T, b: T): Boolean {
                    return a.recreateFromParcel() == b && a == b.recreateFromParcel()
                }

                override fun doHash(o: T): Int {
                    return o.recreateFromParcel().hashCode()
                }

                private fun T.recreateFromParcel(): T = forceParcel(this, parcelableCreator)
            }

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.toString]
         * return the same value.
         */
        private fun <T> toStringEquivalence() =
            object : Equivalence<T>() {

                override fun doEquivalent(a: T, b: T): Boolean {
                    return a.toString() == b.toString()
                }

                override fun doHash(o: T): Int {
                    return o.toString().hashCode()
                }
            }

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.hashCode]
         * return the same value.
         */
        private fun <T> hashCodeEquivalence() =
            object : Equivalence<T>() {

                override fun doEquivalent(a: T, b: T): Boolean {
                    return a.hashCode() == b.hashCode()
                }

                override fun doHash(o: T): Int {
                    return o.hashCode()
                }
            }

        private fun <T> List<T>.toAnyArray(): Array<*> = Array(size) { this[it] as Any }

        private fun <T> List<T>.withCopiesIfNeeded(createCopy: ((T) -> T)? = null): List<T> =
            createCopy?.let { this + this.map(it) } ?: this
    }
}

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

package com.android.managedprovisioning.util

import android.content.Context
import android.util.Log
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes


/** A container for a [StringRes] with format arguments that can be resolved lazily */
sealed class LazyStringResource : Parcelable {
    /** Android string resource ID */
    @get:StringRes
    abstract val resId: Int

    /** String resource format arguments to be used when resolving */
    abstract val formatArgs: Array<out CharSequence>

    /** Resolve the lazy value of this resource within a given [context] */
    open fun value(context: Context): String =
        runCatching { context.getString(resId, *formatArgs) }
            .onFailure {
                val args = formatArgs.joinToString(",")
                Log.e(
                    TAG,
                    "Unable to resolve LazyStringResource: resId=$resId formatArgs=[$args]",
                    it
                )
            }
            .getOrThrow()


    /**
     * Resolve the lazy value of this resource within a given [context] or return null if it cannot be
     * resolved
     */
    fun valueOrNull(context: Context): String? = runCatching { value(context) }.getOrNull()

    /** Check if the string resource is null or blank within a given [context] */
    fun isBlank(context: Context): Boolean = valueOrNull(context).isNullOrBlank()

    override fun equals(other: Any?): Boolean =
        when {
            this === other -> true
            other !is LazyStringResource -> false
            resId != other.resId -> false
            !formatArgs.contentEquals(other.formatArgs) -> false
            else -> true
        }

    override fun hashCode(): Int {
        var result = resId
        result = 31 * result + formatArgs.contentHashCode()
        return result
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(resId)
        dest.writeInt(formatArgs.size)
        dest.writeStringArray(formatArgs.map(CharSequence::toString).toTypedArray())
    }

    /**
     * Converts this resource to a [Bundle]
     *
     * @see LazyStringResource.invoke
     */
    fun toBundle(): Bundle =
        Bundle().apply {
            putInt(BUNDLE_RES_ID, resId)
            putCharSequenceArray(BUNDLE_ARGS, formatArgs)
        }

    override fun toString(): String =
        "${this::class.simpleName}(resId=$resId, formatArgs=${formatArgs.contentToString()})"

    companion object {
        private const val TAG = "LazyStringResource"
        const val BUNDLE_RES_ID = "resId"
        const val BUNDLE_ARGS = "args"

        /** Construct a [LazyStringResource]. Returns [Raw] wrapper or [Empty] if the [resId] is `0` */
        @JvmStatic
        @JvmName("of")
        operator fun invoke(@StringRes resId: Int, vararg formatArgs: CharSequence) =
            if (resId == 0) Empty else Raw(resId, formatArgs = formatArgs)

        /**
         * Construct a [LazyStringResource] from a given [Bundle] produced by
         * [LazyStringResource.toBundle]. Returns [Raw] wrapper or [Empty] if the [resId] is `0`
         */
        @JvmStatic
        @JvmName("of")
        operator fun invoke(bundle: Bundle): LazyStringResource =
            invoke(
                bundle.getInt(BUNDLE_RES_ID),
                formatArgs = bundle.getCharSequenceArray(BUNDLE_ARGS)
                    ?: error("Unable to read formatArgs from a bundle")
            )

        /** Construct a [LazyStringResource]. Returns [Static] wrapper */
        @JvmStatic
        @JvmName("of")
        operator fun invoke(value: String) = Static(value)

        @JvmField
        val CREATOR: Parcelable.Creator<LazyStringResource> =
            object : Parcelable.Creator<LazyStringResource> {
                override fun createFromParcel(parcel: Parcel): LazyStringResource {
                    return LazyStringResource(
                        resId = parcel.readInt(),
                        formatArgs =
                        arrayOfNulls<String>(parcel.readInt())
                            .apply(parcel::readStringArray)
                            .filterNotNull()
                            .toTypedArray()
                    )
                }

                override fun newArray(size: Int): Array<LazyStringResource?> = arrayOfNulls(size)
            }
    }

    /** A [LazyStringResource] that can be resolved within a given [Context] */
    class Raw
    internal constructor(
        @StringRes override val resId: Int,
        override vararg val formatArgs: CharSequence,
    ) : LazyStringResource()

    /** A [LazyStringResource] that has a static string value */
    class Static internal constructor(val value: String) : LazyStringResource() {
        override val resId: Int = 0
        override val formatArgs: Array<out CharSequence> = emptyArray()
        override fun value(context: Context): String = value
    }

    /** A [LazyStringResource] that has an empty hard-coded value */
    object Empty : LazyStringResource() {
        override val resId: Int = 0
        override val formatArgs: Array<out CharSequence> = emptyArray()
        override fun value(context: Context): String = ""
    }
}

/** Resolves [LazyStringResource] within a current context. */
fun Context.getString(resource: LazyStringResource): String = resource.value(this)

package com.android.intentresolver.v2.listcontroller

import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.intentresolver.ResolvedComponentInfo

/** A class for translating [Intent]s to [ResolvedComponentInfo]s. */
interface IntentResolver {
    /**
     * Get data about all the ways the user with the specified handle can resolve any of the
     * provided `intents`.
     */
    fun getResolversForIntentAsUser(
        shouldGetResolvedFilter: Boolean,
        shouldGetActivityMetadata: Boolean,
        shouldGetOnlyDefaultActivities: Boolean,
        intents: List<Intent>,
        userHandle: UserHandle,
    ): List<ResolvedComponentInfo>
}

/** Resolves [Intent]s using the [packageManager], deduping using the given [ResolveListDeduper]. */
class IntentResolverImpl(
    private val packageManager: PackageManager,
    resolveListDeduper: ResolveListDeduper,
) : IntentResolver, ResolveListDeduper by resolveListDeduper {
    override fun getResolversForIntentAsUser(
        shouldGetResolvedFilter: Boolean,
        shouldGetActivityMetadata: Boolean,
        shouldGetOnlyDefaultActivities: Boolean,
        intents: List<Intent>,
        userHandle: UserHandle,
    ): List<ResolvedComponentInfo> {
        val baseFlags =
            ((if (shouldGetOnlyDefaultActivities) PackageManager.MATCH_DEFAULT_ONLY else 0) or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
                (if (shouldGetResolvedFilter) PackageManager.GET_RESOLVED_FILTER else 0) or
                (if (shouldGetActivityMetadata) PackageManager.GET_META_DATA else 0) or
                PackageManager.MATCH_CLONE_PROFILE)
        return getResolversForIntentAsUserInternal(
            intents,
            userHandle,
            baseFlags,
        )
    }

    private fun getResolversForIntentAsUserInternal(
        intents: List<Intent>,
        userHandle: UserHandle,
        baseFlags: Int,
    ): List<ResolvedComponentInfo> = buildList {
        for (intent in intents) {
            var flags = baseFlags
            if (intent.isWebIntent || intent.flags and Intent.FLAG_ACTIVITY_MATCH_EXTERNAL != 0) {
                flags = flags or PackageManager.MATCH_INSTANT
            }
            // Because of AIDL bug, queryIntentActivitiesAsUser can't accept subclasses of Intent.
            val fixedIntent =
                if (intent.javaClass != Intent::class.java) {
                    Intent(intent)
                } else {
                    intent
                }
            val infos = packageManager.queryIntentActivitiesAsUser(fixedIntent, flags, userHandle)
            addToResolveListWithDedupe(this, fixedIntent, infos)
        }
    }
}

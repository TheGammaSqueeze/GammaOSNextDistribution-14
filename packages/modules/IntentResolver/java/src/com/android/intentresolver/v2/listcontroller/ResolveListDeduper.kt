package com.android.intentresolver.v2.listcontroller

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import com.android.intentresolver.ResolvedComponentInfo

/** A class for adding [ResolveInfo]s to a list of [ResolvedComponentInfo]s without duplicates. */
interface ResolveListDeduper {
    /**
     * Adds [ResolveInfo]s in [from] to [ResolvedComponentInfo]s in [into], creating new
     * [ResolvedComponentInfo]s when there is not already a corresponding one.
     *
     * This method may be destructive to both the given [into] list and the underlying
     * [ResolvedComponentInfo]s.
     */
    fun addToResolveListWithDedupe(
        into: MutableList<ResolvedComponentInfo>,
        intent: Intent,
        from: List<ResolveInfo>,
    )
}

/**
 * Default implementation for adding [ResolveInfo]s to a list of [ResolvedComponentInfo]s without
 * duplicates. Uses the given [PinnableComponents] to determine the pinning state of newly created
 * [ResolvedComponentInfo]s.
 */
class ResolveListDeduperImpl(pinnableComponents: PinnableComponents) :
    ResolveListDeduper, PinnableComponents by pinnableComponents {
    override fun addToResolveListWithDedupe(
        into: MutableList<ResolvedComponentInfo>,
        intent: Intent,
        from: List<ResolveInfo>,
    ) {
        from.forEach { newInfo ->
            if (newInfo.userHandle == null) {
                Log.w(TAG, "Skipping ResolveInfo with no userHandle: $newInfo")
                return@forEach
            }
            val oldInfo = into.firstOrNull { isSameResolvedComponent(newInfo, it) }
            // If existing resolution found, add to existing and filter out
            if (oldInfo != null) {
                oldInfo.add(intent, newInfo)
            } else {
                with(newInfo.activityInfo) {
                    into.add(
                        ResolvedComponentInfo(
                                ComponentName(packageName, name),
                                intent,
                                newInfo,
                            )
                            .apply { isPinned = isComponentPinned(name) },
                    )
                }
            }
        }
    }

    private fun isSameResolvedComponent(a: ResolveInfo, b: ResolvedComponentInfo): Boolean {
        val ai = a.activityInfo
        return ai.packageName == b.name.packageName && ai.name == b.name.className
    }

    companion object {
        const val TAG = "ResolveListDeduper"
    }
}

package com.android.intentresolver.v2.listcontroller

import android.content.pm.PackageManager
import android.util.Log
import com.android.intentresolver.ResolvedComponentInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Provides filtering methods for lists of [ResolvedComponentInfo]. */
interface ResolvedComponentFiltering {
    /**
     * Returns a list with all the [ResolvedComponentInfo] in [inputList], less the ones that are
     * not eligible.
     */
    suspend fun filterIneligibleActivities(
        inputList: List<ResolvedComponentInfo>,
    ): List<ResolvedComponentInfo>

    /** Filter out any low priority items. */
    fun filterLowPriority(inputList: List<ResolvedComponentInfo>): List<ResolvedComponentInfo>
}

/**
 * Default instantiation of the filtering methods for lists of [ResolvedComponentInfo].
 *
 * Binder calls are performed on the given [bgDispatcher] and permissions are checked as if launched
 * from the given [launchedFromUid] UID. Component filtering is handled by the given
 * [FilterableComponents] and permission checking is handled by the given [PermissionChecker].
 */
class ResolvedComponentFilteringImpl(
    private val launchedFromUid: Int,
    filterableComponents: FilterableComponents,
    permissionChecker: PermissionChecker,
) :
    ResolvedComponentFiltering,
    PermissionChecker by permissionChecker,
    FilterableComponents by filterableComponents {
    constructor(
        bgDispatcher: CoroutineDispatcher,
        launchedFromUid: Int,
        filterableComponents: FilterableComponents,
    ) : this(
        launchedFromUid = launchedFromUid,
        filterableComponents = filterableComponents,
        permissionChecker = ActivityManagerPermissionChecker(bgDispatcher),
    )

    /**
     * Filter out items that are filtered by [FilterableComponents] or do not have the necessary
     * permissions.
     */
    override suspend fun filterIneligibleActivities(
        inputList: List<ResolvedComponentInfo>,
    ): List<ResolvedComponentInfo> = coroutineScope {
        inputList
            .map {
                val activityInfo = it.getResolveInfoAt(0).activityInfo
                if (isComponentFiltered(activityInfo.componentName)) {
                    CompletableDeferred(value = null)
                } else {
                    // Do all permission checks in parallel
                    async {
                        val granted =
                            checkComponentPermission(
                                activityInfo.permission,
                                launchedFromUid,
                                activityInfo.applicationInfo.uid,
                                activityInfo.exported,
                            ) == PackageManager.PERMISSION_GRANTED
                        if (granted) it else null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Filters out all elements starting with the first elements with a different priority or
     * default status than the first element.
     */
    override fun filterLowPriority(
        inputList: List<ResolvedComponentInfo>,
    ): List<ResolvedComponentInfo> {
        val firstResolveInfo = inputList[0].getResolveInfoAt(0)
        // Only display the first matches that are either of equal
        // priority or have asked to be default options.
        val firstDiffIndex =
            inputList.indexOfFirst { resolvedComponentInfo ->
                val resolveInfo = resolvedComponentInfo.getResolveInfoAt(0)
                if (firstResolveInfo == resolveInfo) {
                    false
                } else {
                    if (DEBUG) {
                        Log.v(
                            TAG,
                            "${firstResolveInfo?.activityInfo?.name}=" +
                                "${firstResolveInfo?.priority}/${firstResolveInfo?.isDefault}" +
                                " vs ${resolveInfo?.activityInfo?.name}=" +
                                "${resolveInfo?.priority}/${resolveInfo?.isDefault}"
                        )
                    }
                    firstResolveInfo!!.priority != resolveInfo!!.priority ||
                        firstResolveInfo.isDefault != resolveInfo.isDefault
                }
            }
        return if (firstDiffIndex == -1) {
            inputList
        } else {
            inputList.subList(0, firstDiffIndex)
        }
    }

    companion object {
        private const val TAG = "ResolvedComponentFilter"
        private const val DEBUG = false
    }
}

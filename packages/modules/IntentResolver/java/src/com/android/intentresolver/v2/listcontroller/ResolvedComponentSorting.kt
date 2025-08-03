package com.android.intentresolver.v2.listcontroller

import android.os.UserHandle
import android.util.Log
import com.android.intentresolver.ResolvedComponentInfo
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.TargetInfo
import com.android.intentresolver.model.AbstractResolverComparator
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Provides sorting methods for lists of [ResolvedComponentInfo]. */
interface ResolvedComponentSorting {
    /** Returns the a copy of the [inputList] sorted by app share score. */
    suspend fun sorted(inputList: List<ResolvedComponentInfo>?): List<ResolvedComponentInfo>?

    /** Returns the app share score of the [target]. */
    fun getScore(target: DisplayResolveInfo): Float

    /** Returns the app share score of the [targetInfo]. */
    fun getScore(targetInfo: TargetInfo): Float

    /** Updates the model about [targetInfo]. */
    suspend fun updateModel(targetInfo: TargetInfo)

    /** Updates the model about Activity selection. */
    suspend fun updateChooserCounts(packageName: String, user: UserHandle, action: String)

    /** Cleans up resources. Nothing should be called after calling this. */
    fun destroy()
}

/**
 * Provides sorting methods using the given [resolverComparator].
 *
 * Long calculations and binder calls are performed on the given [bgDispatcher].
 */
class ResolvedComponentSortingImpl(
    private val bgDispatcher: CoroutineDispatcher,
    private val resolverComparator: AbstractResolverComparator,
) : ResolvedComponentSorting {

    private val computeComplete = AtomicReference<CompletableDeferred<Unit>?>(null)

    @Throws(InterruptedException::class)
    private suspend fun computeIfNeeded(inputList: List<ResolvedComponentInfo>) {
        if (computeComplete.compareAndSet(null, CompletableDeferred())) {
            resolverComparator.setCallBack { computeComplete.get()!!.complete(Unit) }
            resolverComparator.compute(inputList)
        }
        with(computeComplete.get()!!) { if (isCompleted) return else return await() }
    }

    override suspend fun sorted(
        inputList: List<ResolvedComponentInfo>?,
    ): List<ResolvedComponentInfo>? {
        if (inputList.isNullOrEmpty()) return inputList

        return withContext(bgDispatcher) {
            try {
                val beforeRank = System.currentTimeMillis()
                computeIfNeeded(inputList)
                val sorted = inputList.sortedWith(resolverComparator)
                val afterRank = System.currentTimeMillis()
                if (DEBUG) {
                    Log.d(TAG, "Time Cost: ${afterRank - beforeRank}")
                }
                sorted
            } catch (e: InterruptedException) {
                Log.e(TAG, "Compute & Sort was interrupted: $e")
                null
            }
        }
    }

    override fun getScore(target: DisplayResolveInfo): Float {
        return resolverComparator.getScore(target)
    }

    override fun getScore(targetInfo: TargetInfo): Float {
        return resolverComparator.getScore(targetInfo)
    }

    override suspend fun updateModel(targetInfo: TargetInfo) {
        withContext(bgDispatcher) { resolverComparator.updateModel(targetInfo) }
    }

    override suspend fun updateChooserCounts(
        packageName: String,
        user: UserHandle,
        action: String,
    ) {
        withContext(bgDispatcher) {
            resolverComparator.updateChooserCounts(packageName, user, action)
        }
    }

    override fun destroy() {
        resolverComparator.destroy()
    }

    companion object {
        private const val TAG = "ResolvedComponentSort"
        private const val DEBUG = false
    }
}

package com.android.intentresolver.v2.data.repository

import android.content.Context
import androidx.core.content.getSystemService
import com.android.intentresolver.v2.shared.model.User

/**
 * Provides cached instances of a [system service][Context.getSystemService] created with
 * [the context of a specified user][Context.createContextAsUser].
 *
 * System services which have only `@UserHandleAware` APIs operate on the user id available from
 * [Context.getUser], the context used to retrieve the service. This utility helps adapt a per-user
 * API model to work in multi-user manner.
 *
 * Example usage:
 * ```
 * val usageStats = userScopedService<UsageStatsManager>(context)
 *
 * fun getStatsForUser(
 *     user: User,
 *     from: Long,
 *     to: Long
 * ): UsageStats {
 *     return usageStats.forUser(user)
 *        .queryUsageStats(INTERVAL_BEST, from, to)
 * }
 * ```
 */
interface UserScopedService<T> {
    fun forUser(user: User): T
}

inline fun <reified T> userScopedService(context: Context): UserScopedService<T> {
    return object : UserScopedService<T> {
        private val map = mutableMapOf<User, T>()

        override fun forUser(user: User): T {
            return synchronized(this) {
                map.getOrPut(user) {
                    val userContext = context.createContextAsUser(user.handle, 0)
                    requireNotNull(userContext.getSystemService())
                }
            }
        }
    }
}

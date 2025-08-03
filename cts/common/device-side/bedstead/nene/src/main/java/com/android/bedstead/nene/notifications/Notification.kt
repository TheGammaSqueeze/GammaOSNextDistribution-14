package com.android.bedstead.nene.notifications

import android.service.notification.StatusBarNotification
import com.android.bedstead.nene.annotations.Experimental
import java.lang.IllegalStateException

class Notification internal constructor(val statusBarNotification: StatusBarNotification) {

    @Experimental
    fun cancel() {
        val service = NeneNotificationListenerService.instance.get() ?: throw IllegalStateException("Cannot dismiss notification after all listeners are closed")
        service.cancelNotification(statusBarNotification.key)
    }

    val packageName get() = statusBarNotification.packageName
    val notification get() = statusBarNotification.notification

    override fun toString(): String = "Notification{packageName=$packageName,notification=$notification}"
}
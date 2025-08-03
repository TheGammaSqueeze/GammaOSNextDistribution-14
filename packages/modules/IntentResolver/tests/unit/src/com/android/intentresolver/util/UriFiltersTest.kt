package com.android.intentresolver.util

import android.app.PendingIntent
import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import android.service.chooser.ChooserAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UriFiltersTest {

    @Test
    fun uri_ownedByCurrentUser_noUserId() {
        val uri = Uri.parse("content://media/images/12345")
        assertTrue("Uri without userId should always return true", uri.ownedByCurrentUser)
    }

    @Test
    fun uri_ownedByCurrentUser_selfUserId() {
        val uri = Uri.parse("content://${UserHandle.myUserId()}@media/images/12345")
        assertTrue("Uri with own userId should return true", uri.ownedByCurrentUser)
    }

    @Test
    fun uri_ownedByCurrentUser_otherUserId() {
        val otherUserId = UserHandle.myUserId() + 10
        val uri = Uri.parse("content://${otherUserId}@media/images/12345")
        assertFalse("Uri with other userId should return false", uri.ownedByCurrentUser)
    }

    @Test
    fun chooserAction_hasValidIcon_bitmap() =
        smallBitmap().use {
            val icon = Icon.createWithBitmap(it)
            val action = actionWithIcon(icon)
            assertTrue("No uri, assumed valid", hasValidIcon(action))
        }

    @Test
    fun chooserAction_hasValidIcon_uri() {
        val icon = Icon.createWithContentUri("content://provider/content/12345")
        assertTrue("No userId in uri, uri is valid", hasValidIcon(actionWithIcon(icon)))
    }
    @Test
    fun chooserAction_hasValidIcon_uri_unowned() {
        val userId = UserHandle.myUserId() + 10
        val icon = Icon.createWithContentUri("content://${userId}@provider/content/12345")
        assertFalse("uri userId references a different user", hasValidIcon(actionWithIcon(icon)))
    }

    private fun smallBitmap() = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    private fun mockAction(): PendingIntent {
        return PendingIntent(
            object : IIntentSender {
                override fun asBinder(): IBinder = Binder()
                override fun send(
                    code: Int,
                    intent: Intent?,
                    resolvedType: String?,
                    whitelistToken: IBinder?,
                    finishedReceiver: IIntentReceiver?,
                    requiredPermission: String?,
                    options: Bundle?
                ) {
                    /* empty */
                }
            }
        )
    }

    private fun actionWithIcon(icon: Icon): ChooserAction {
        return ChooserAction.Builder(icon, "", mockAction()).build()
    }

    /** Unconditionally recycles the [Bitmap] after running the given block */
    private fun Bitmap.use(block: (Bitmap) -> Unit) =
        try {
            block(this)
        } finally {
            recycle()
        }
}

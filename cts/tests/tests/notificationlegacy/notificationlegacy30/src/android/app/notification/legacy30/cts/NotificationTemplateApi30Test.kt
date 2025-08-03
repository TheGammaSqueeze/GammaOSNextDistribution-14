/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.notification.legacy30.cts

import android.R
import android.app.Notification
import android.app.cts.NotificationTemplateTestBase
import android.content.pm.PackageManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTemplateApi30Test : NotificationTemplateTestBase() {

    @Before
    public fun setUp() {
        assertThat(context.applicationInfo.targetSdkVersion).isEqualTo(30)
    }

    @Test
    fun testWideIcon_inCollapsedState_isSquareForLegacyApps() {
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    @Test
    fun testWideIcon_inBigBaseState_isSquareForLegacyApps() {
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    @Test
    fun testWideIcon_inBigPicture_isSquareForLegacyApps() {
        if (skipIfPlatformDoesNotSupportNotificationStyles()) {
            return
        }

        val picture = createBitmap(40, 30)
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle().bigPicture(picture))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    @Test
    fun testWideIcon_inBigText_isSquareForLegacyApps() {
        val bitmap = createBitmap(200, 100)
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(bitmap)
                .setStyle(Notification.BigTextStyle().bigText("Big\nText\nContent"))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    @Test
    fun testPromoteBigPicture_withoutLargeIcon() {
                if (skipIfPlatformDoesNotSupportNotificationStyles()) {
            return
        }

        val picture = createBitmap(40, 30)
        val builder = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )
        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.scaleType).isEqualTo(ImageView.ScaleType.CENTER_CROP)
        }
        // there should be no icon in the large state
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun testPromoteBigPicture_withLargeIcon() {
        if (skipIfPlatformDoesNotSupportNotificationStyles()) {
            return
        }

        val picture = createBitmap(40, 30)
        val icon = createBitmap(80, 65)
        val builder = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )

        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
        // because it doesn't target S, the icon is still shown in a square
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
            assertThat(iconView.scaleType).isEqualTo(ImageView.ScaleType.CENTER_CROP)
        }
    }

    @Test
    fun testPromoteBigPicture_withBigLargeIcon() {
        if (skipIfPlatformDoesNotSupportNotificationStyles()) {
            return
        }

        val picture = createBitmap(40, 30)
        val inputWidth = 400
        val inputHeight = 300
        val bigIcon = createBitmap(inputWidth, inputHeight)
        val builder = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .bigLargeIcon(bigIcon)
                        .showBigPictureWhenCollapsed(true)
                )

        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.scaleType).isEqualTo(ImageView.ScaleType.CENTER_CROP)
        }
        // because it doesn't target S, the icon is still shown in a square
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
            assertThat(iconView.scaleType).isEqualTo(ImageView.ScaleType.CENTER_CROP)
        }
    }

    fun testBaseTemplate_hasExpandedStateWithoutActions() {
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .createBigContentView()
        assertThat(views).isNotNull()
    }

    @Test
    fun testDecoratedCustomViewStyle_collapsedState() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomContentView(customContent)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .createContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>("icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    @Test
    fun testDecoratedCustomViewStyle_expandedState() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomBigContentView(customContent)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .createBigContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text shows
            val appNameView = requireViewByIdName<TextView>("app_name_text")
            assertThat(appNameView.visibility).isEqualTo(View.VISIBLE)

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>("icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    @Test
    fun testCustomViewNotification_collapsedState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomContentView(customContent)
                .createContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    @Test
    fun testCustomViewNotification_expandedState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomBigContentView(customContent)
                .createBigContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text is not present
            val appNameView = findViewByIdName<TextView>("app_name_text")
            assertThat(appNameView).isNull()

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    @Test
    fun testCustomViewNotification_headsUpState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomHeadsUpContentView(customContent)
                .createHeadsUpContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    /**
     * Assume that we're running on the platform that supports styled notifications.
     *
     * If the current platform does not support notification styles, skip this test without failure.
     */
    private fun skipIfPlatformDoesNotSupportNotificationStyles(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    companion object {
        val TAG = NotificationTemplateApi30Test::class.java.simpleName
        const val NOTIFICATION_CHANNEL_ID = "NotificationTemplateApi30Test"
    }
}

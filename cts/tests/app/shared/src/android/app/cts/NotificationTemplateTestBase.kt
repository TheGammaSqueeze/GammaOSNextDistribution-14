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
package android.app.cts

import android.R
import android.app.stubs.shared.NotificationHostActivity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.BoolRes
import androidx.annotation.DimenRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.reflect.KClass
import org.junit.Before

open class NotificationTemplateTestBase {

    // Used to give time to visually inspect or attach a debugger before the checkViews block
    protected var waitBeforeCheckingViews: Long = 0
    protected var context: Context =
            InstrumentationRegistry.getInstrumentation().getTargetContext()

    @Before
    public fun baseSetUp() {
        CtsAppTestUtils.turnScreenOn(InstrumentationRegistry.getInstrumentation(), context)
    }

    protected fun checkIconView(views: RemoteViews, iconCheck: (ImageView) -> Unit) {
        checkViews(views) {
            iconCheck(requireViewByIdName("right_icon"))
        }
    }

    protected fun checkViews(
        views: RemoteViews,
        @DimenRes heightDimen: Int? = null,
        checker: NotificationHostActivity.() -> Unit
    ) {
        val activityIntent = Intent(context, NotificationHostActivity::class.java)
        activityIntent.putExtra(NotificationHostActivity.EXTRA_REMOTE_VIEWS, views)
        heightDimen?.also {
            activityIntent.putExtra(
                NotificationHostActivity.EXTRA_HEIGHT,
                    context.resources.getDimensionPixelSize(it)
            )
        }
        ActivityScenario.launch<NotificationHostActivity>(activityIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            if (waitBeforeCheckingViews > 0) {
                Thread.sleep(waitBeforeCheckingViews)
            }
            scenario.onActivity { activity ->
                activity.checker()
            }
        }
    }

    protected fun createBitmap(width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                // IMPORTANT: Pass current DisplayMetrics when creating a Bitmap, so that it
                // receives the correct density. Otherwise, the Bitmap may get the default density
                // (DisplayMetrics.DENSITY_DEVICE), which in some cases (eg. for apps running in
                // compat mode) may be different from the actual density the app is rendered with.
                // This would lead to the Bitmap eventually being rendered with different sizes,
                // than the ones passed here.
                density = context.resources.displayMetrics.densityDpi

                eraseColor(Color.GRAY)
            }

    protected fun makeCustomContent(): RemoteViews {
        val customContent = RemoteViews(context.packageName, R.layout.simple_list_item_1)
        val textId = getAndroidRId("text1")
        customContent.setTextViewText(textId, "Example Text")
        return customContent
    }

    protected fun <T : View> NotificationHostActivity.requireViewByIdName(idName: String): T {
        val viewId = getAndroidRId(idName)
        return notificationRoot.findViewById<T>(viewId)
                ?: throw NullPointerException("No view with id: android.R.id.$idName ($viewId)")
    }

    protected fun <T : View> NotificationHostActivity.findViewByIdName(idName: String): T? =
            notificationRoot.findViewById<T>(getAndroidRId(idName))

    /** [Sequence] that yields all of the direct children of this [ViewGroup] */
    private val ViewGroup.children
        get() = sequence { for (i in 0 until childCount) yield(getChildAt(i)) }

    private fun <T : View> collectViews(
        view: View,
        type: KClass<T>,
        mutableList: MutableList<T>,
        requireVisible: Boolean = true,
        predicate: (T) -> Boolean
    ) {
        if (requireVisible && view.visibility != View.VISIBLE) {
            return
        }
        if (type.java.isInstance(view)) {
            if (predicate(view as T)) {
                mutableList.add(view)
            }
        }
        if (view is ViewGroup) {
            for (child in view.children) {
                collectViews(child, type, mutableList, requireVisible, predicate)
            }
        }
    }

    protected fun NotificationHostActivity.requireViewWithText(text: String): TextView =
            findViewWithText(text) ?: throw RuntimeException("Unable to find view with text: $text")

    protected fun NotificationHostActivity.requireViewWithTextContaining(
        substring: String
    ): TextView =
        findViewWithTextContaining(substring)
            ?: throw RuntimeException("Unable to find view with text containing: $substring")

    protected fun NotificationHostActivity.findViewWithText(text: String): TextView? {
        val views: MutableList<TextView> = ArrayList()
        collectViews(notificationRoot, TextView::class, views) { it.text?.toString() == text }
        when (views.size) {
            0 -> return null
            1 -> return views[0]
            else -> throw RuntimeException("Found multiple views with text: $text")
        }
    }

    protected fun NotificationHostActivity.findViewWithTextContaining(
        substring: String
    ): TextView? {
        val views: MutableList<TextView> = ArrayList()
        collectViews(notificationRoot, TextView::class, views) {
            (it.text?.toString() ?: "").contains(substring)
        }
        when (views.size) {
            0 -> return null
            1 -> return views[0]
            else -> throw RuntimeException("Found multiple views with text containing: $substring")
        }
    }

    private fun getAndroidRes(resType: String, resName: String): Int =
            context.resources.getIdentifier(resName, resType, "android")

    @IdRes
    protected fun getAndroidRId(idName: String): Int = getAndroidRes("id", idName)

    @StringRes
    protected fun getAndroidRString(stringName: String): Int = getAndroidRes("string", stringName)

    @BoolRes
    protected fun getAndroidRBool(boolName: String): Int = getAndroidRes("bool", boolName)

    @DimenRes
    protected fun getAndroidRDimen(dimenName: String): Int = getAndroidRes("dimen", dimenName)
}

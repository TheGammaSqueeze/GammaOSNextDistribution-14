package com.android.car.docklib

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.android.car.docklib.data.DockAppItem
import com.android.car.docklib.data.DockItemId
import java.util.UUID
import org.mockito.kotlin.mock

object TestUtils {
    /** Create a hardcoded dock item with optional fields */
    fun createAppItem(
            id: @DockItemId UUID = UUID.randomUUID(),
            type: DockAppItem.Type = DockAppItem.Type.DYNAMIC,
            app: String = "app",
            pkg: String = app,
            clazz: String = app,
            name: String = "app",
            component: ComponentName = ComponentName(pkg, clazz),
            icon: Drawable = mock<Drawable>(),
            @ColorInt iconColor: Int = Color.WHITE,
            @ColorInt iconColorScrim: Int? = null,
            isDrivingOptimized: Boolean = true,
            isMediaApp: Boolean = false,
    ): DockAppItem {
        if (iconColorScrim != null) {
            return DockAppItem(
                    id = id,
                    type = type,
                    component = component,
                    name = name,
                    icon = icon,
                    iconColor = iconColor,
                    iconColorScrim = iconColorScrim,
                    isDistractionOptimized = isDrivingOptimized,
                    isMediaApp = isMediaApp,
            )
        }
        return DockAppItem(
                id = id,
                type = type,
                component = component,
                name = name,
                icon = icon,
                iconColor = iconColor,
                isDistractionOptimized = isDrivingOptimized,
                isMediaApp = isMediaApp,
        )
    }
}

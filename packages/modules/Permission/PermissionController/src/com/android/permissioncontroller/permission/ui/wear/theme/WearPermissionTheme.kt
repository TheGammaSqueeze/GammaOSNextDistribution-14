package com.android.permissioncontroller.permission.ui.wear.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** The Material 3 Theme Wrapper for Supporting RRO. */
@Composable
fun WearPermissionTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            overlayColors(context)
                .copy(error = MaterialTheme.colors.error, onError = MaterialTheme.colors.onError)
        } else {
            MaterialTheme.colors
        }
    MaterialTheme(colors = colors, content = content)
}

/**
 * Creates a dynamic color maps that can be overlaid. 100 - Lightest shade; 0 - Darkest Shade; In
 * wear we only support dark theme for the time being. Thus the fill colors and variants are dark
 * and anything on top is light. We will use this custom redirection until wear compose material
 * supports color scheming.
 *
 * The mapping is best case match on wear material color tokens from
 * /android/clockwork/common/wearable/wearmaterial/color/res/values/color-tokens.xml
 *
 * @param context The context required to get system resource data.
 */
@RequiresApi(Build.VERSION_CODES.S)
@VisibleForTesting
internal fun overlayColors(context: Context): Colors {
    val tonalPalette = dynamicTonalPalette(context)
    return Colors(
        background = Color.Black,
        onBackground = Color.White,
        primary = tonalPalette.primary90,
        primaryVariant = tonalPalette.primary80,
        onPrimary = tonalPalette.primary10,
        secondary = tonalPalette.tertiary90,
        secondaryVariant = tonalPalette.tertiary60,
        onSecondary = tonalPalette.tertiary10,
        surface = tonalPalette.neutral20,
        onSurface = tonalPalette.neutral95,
        onSurfaceVariant = tonalPalette.neutralVariant80,
    )
}

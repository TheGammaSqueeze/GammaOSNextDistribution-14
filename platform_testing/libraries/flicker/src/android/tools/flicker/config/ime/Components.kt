package android.tools.flicker.config.ime

import android.tools.flicker.assertors.ComponentTemplate
import android.tools.traces.component.ComponentNameMatcher

object Components {
    val IME = ComponentTemplate("IME") { ComponentNameMatcher.IME }
}

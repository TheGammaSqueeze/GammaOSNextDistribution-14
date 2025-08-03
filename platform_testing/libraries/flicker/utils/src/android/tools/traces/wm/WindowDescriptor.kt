package android.tools.traces.wm

class WindowDescriptor(window: WindowContainer) {
    val id = window.id
    val name = window.name
    val isAppWindow: Boolean =
        (window is Task) || (window.parent?.let { WindowDescriptor(it).isAppWindow } ?: false)
}

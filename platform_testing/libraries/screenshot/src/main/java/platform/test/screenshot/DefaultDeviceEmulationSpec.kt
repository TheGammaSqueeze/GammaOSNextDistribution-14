package platform.test.screenshot

/**
 * The emulations specs for all 8 permutations of:
 * - phone or tablet.
 * - dark of light mode.
 * - portrait or landscape.
 */
val DeviceEmulationSpec.Companion.PhoneAndTabletFull
    get() = PhoneAndTabletFullSpec

private val PhoneAndTabletFullSpec =
    DeviceEmulationSpec.forDisplays(Displays.Phone, Displays.Tablet)

/**
 * The emulations specs of:
 * - phone + light mode + portrait.
 * - phone + light mode + landscape.
 * - tablet + dark mode + portrait.
 *
 * This allows to test the most important permutations of a screen/layout with only 3
 * configurations.
 */
val DeviceEmulationSpec.Companion.PhoneAndTabletMinimal
    get() = PhoneAndTabletMinimalSpec

private val PhoneAndTabletMinimalSpec =
    DeviceEmulationSpec.forDisplays(Displays.Phone, isDarkTheme = false) +
        DeviceEmulationSpec.forDisplays(Displays.Tablet, isDarkTheme = true, isLandscape = false)

val DeviceEmulationSpec.Companion.externalDisplaysMinimal
    get() = externalDisplaysMinimalSpec

private val externalDisplaysMinimalSpec: List<DeviceEmulationSpec> =
    DeviceEmulationSpec.forDisplays(
        Displays.External480p,
        Displays.External720p,
        Displays.External1080p,
        Displays.External4k,
        isDarkTheme = false,
        isLandscape = true
    )

object Displays {
    val Phone =
        DisplaySpec(
            "phone",
            width = 1440,
            height = 3120,
            densityDpi = 560,
        )

    val Tablet =
        DisplaySpec(
            "tablet",
            width = 2560,
            height = 1600,
            densityDpi = 320,
        )

    val FoldableOuter =
        DisplaySpec(
            "foldable_outer",
            width = 1080,
            height = 2092,
            densityDpi = 420,
        )

    val FoldableInner =
        DisplaySpec(
            "foldable_inner",
            width = 2208,
            height = 1840,
            densityDpi = 420,
        )

    val TallerFoldableOuter =
        DisplaySpec(
            "taller_foldable_outer",
            width = 1080,
            height = 2424,
            densityDpi = 395,
        )

    val TallerFoldableInner =
        DisplaySpec(
            "taller_foldable_inner",
            width = 2076,
            height = 2152,
            densityDpi = 360,
        )

    val External480p =
        DisplaySpec(
            "external480p",
            width = 720,
            height = 480,
            densityDpi = 142,
        )

    val External720p =
        DisplaySpec(
            "external720p",
            width = 1280,
            height = 720,
            densityDpi = 213,
        )

    val External1080p =
        DisplaySpec(
            "external1080p",
            width = 1920,
            height = 1080,
            densityDpi = 320,
        )

    val External4k =
        DisplaySpec(
            "external4k",
            width = 3840,
            height = 2160,
            densityDpi = 320,
        )
}

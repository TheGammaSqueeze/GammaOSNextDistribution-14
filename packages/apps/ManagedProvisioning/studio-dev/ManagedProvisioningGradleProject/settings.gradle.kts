pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

val ANDROID_RELATIVE_TOP = extra["ANDROID_RELATIVE_TOP"].toString()
val ACETONE_LIB_CITC_CLIENT = extra["ACETONE_LIB_CITC_CLIENT"].toString()

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  rulesMode = RulesMode.FAIL_ON_PROJECT_RULES

  repositories {
    maven {
      name = "prebuilts/sdk/current/androidx/m2repository"
      url = uri("${ANDROID_RELATIVE_TOP}/prebuilts/sdk/current/androidx/m2repository")
    }
    maven {
      name = "prebuilts/sdk/current/androidx-legacy/m2repository"
      url = uri("${ANDROID_RELATIVE_TOP}/prebuilts/sdk/current/androidx-legacy/m2repository")
    }
    maven {
      name = "prebuilts/misc/common/androidx-test"
      url = uri("${ANDROID_RELATIVE_TOP}/prebuilts/misc/common/androidx-test")
    }
    maven {
      name = "prebuilts/sdk/current/extras/material-design-x"
      url = uri("${ANDROID_RELATIVE_TOP}/prebuilts/sdk/current/extras/material-design-x")
    }
    maven {
      name = "vendor/unbundled_google/packages/PrebuiltGmsCore/v17/m2repo-1p"
      url =
        uri("${ANDROID_RELATIVE_TOP}/vendor/unbundled_google/packages/PrebuiltGmsCore/v17/m2repo-1p")
    }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }

    mavenCentral {
      content {
        excludeGroupByRegex("androidx(\\..*)?")
        excludeGroupByRegex("android(\\..*)?")
        excludeGroupByRegex("com\\.android(\\..*)?")
      }
    }

    google {
      content {
        // Needed by :WallpaperPickerGoogle:assembleGoogleDebug
        includeVersion("androidx.legacy", "legacy-support-v4", "1.0.0")
        includeVersion("android.arch.lifecycle", "runtime", "1.0.0")
        includeVersion("android.arch.lifecycle", "common", "1.0.0")
        includeVersion("android.arch.core", "common", "1.0.0")
        // Needed by WallpaperPickerGoogle tests
        includeModule("androidx.multidex", "multidex")
        // Needed by ComposeGalleryLib:assemble
        includeGroup("androidx.compose.compiler")
        // Needed for compiling in Android Studio
        includeGroupByRegex("com\\.android(\\..*)?")
        includeGroupByRegex("com\\.google(\\..*)?")
      }
    }
  }
}

val ANDROID_ROOT_DIR = file(rootDir).resolve("../../../../../")
val androidTop = file(rootDir.getParent()).resolve("../../../../").getCanonicalPath()

/**
 * Includes a module with a custom dir. Ignores the module if the directory does not exist.
 *
 * **Heads-up:** the project will **NOT** load if the project is not in the repo manifest of the
 * small branch.
 *
 * @param name: the project name to be included.
 * @params dir: the project directory child path, where parent is ANDROID_ROOT_DIR.
 */
fun includeAndroidProject(name: String, dir: String) {
  val projectRoot = file(ANDROID_ROOT_DIR).resolve(dir)
  if (projectRoot.exists()) {
    include(name)
    project(name).projectDir = projectRoot
  } else {
    logger.lifecycle("Android project \"$name\" not found. Did you checkout it with repo? Expected location is \"${projectRoot.path}\".")
  }
}

/**
 * Includes a module with a modified root where the build.gradle.kts file is still stored near the
 * root of this project. This is useful so that you don"t need to specify relative paths in the
 * project"s build.gradle.kts file.

 * @param projectName = The name of a module that has a build.gradle.kts in an empty dir, for example
 *                 "AiAi" would represent AiAi/build.gradle.kts
 * @param projectRoot = The path to the project relative to ANDROID_BUILD_TOP. This will be set as
 *                 the project"s projectDir
 */
fun includeProject(projectName: String, projectRoot: String) {
  val projectId = ":$projectName"
  include(projectId)
  val projectDescriptor = project(projectId)
  val pathToBuildFile = projectDescriptor.buildFile.canonicalFile
  projectDescriptor.projectDir = file(androidTop).resolve(projectRoot)
  projectDescriptor.buildFileName = pathToBuildFile.toRelativeString(projectDescriptor.projectDir)
}

// Enable the project below if trying to debug platform code
// include ":PlatformCode"

// includeAndroidProject(":IconLoader", "frameworks/libs/systemui/iconloaderlib")
//
// include(":SettingsLibDeviceState")
// include(":SettingsLibWidget")
// include(":SettingsLibColor")
// include(":SettingsLibUtils")
// include(":SettingsLib")
// include(":WifiTrackerLib")
include(":ModuleUtils")
//
// include(":PlatformAnimationLibrary")
// include(":ComposeCoreLib")
// include(":ComposeFeaturesLib")
// include(":ComposeGalleryLib")
// include(":ComposeGallery")
// include(":ComposeTestingLib")
// include(":ComposeSceneTransitionLayoutLib")
// include(":ComposeSceneTransitionLayoutDemoLib")
// include(":ComposeSceneTransitionLayoutDemo")
// include(":CustomizationLib")
// include(":SharedLib")
// include(":SharedTestLib")
// include(":tracinglib")
include(":platform-compat")
//
// include(":SettingsLibActionBarShadow")
// include(":SettingsLibActionButtonsPreference")
// include(":SettingsLibActivityEmbedding")
// include(":SettingsLibAdaptiveIcon")
// include(":SettingsLibAppPreference")
// include(":SettingsLibBannerMessagePreference")
// include(":SettingsLibBarChartPreference")
// include(":SettingsLibButtonPreference")
// include(":SettingsLibCollapsingToolbarBaseActivity")
// include(":SettingsLibEntityHeaderWidgets")
// include(":SettingsLibFooterPreference")
// include(":SettingsLibHelpUtils")
// include(":SettingsLibIllustrationPreference")
// include(":SettingsLibLayoutPreference")
// include(":SettingsLibMainSwitchPreference")
// include(":SettingsLibProfileSelector")
// include(":SettingsLibProgressBar")
// include(":SettingsLibRestrictedLockUtils")
// include(":SettingsLibSearchWidget")
// include(":SettingsLibSelectorWithWidgetPreference")
// include(":SettingsLibSettingsSpinner")
// include(":SettingsLibSettingsTheme")
// include(":SettingsLibSettingsTransition")
// include(":SettingsLibTile")
// include(":SettingsLibTopIntroPreference")
// include(":SettingsLibTwoTargetPreference")
// include(":SettingsLibUsageProgressBarPreference")
//
// include(":SystemUI-res")
// include(":sysuig-resources")
//
// includeProject("SystemUISharedLib-Keyguard", "frameworks/base/packages/SystemUI/shared/keyguard")
// include(":UiAutomatorHelpersLib")
// include(":UnfoldLib")
// include(":PixelAtoms")
// include(":WMShell")
// include(":WMShellFlags")
// include(":WMShellFlicker")
// include(":PlatformProtosNano")
// include(":FlickerLibParsers")
// include(":PerfettoProtos")
// include(":LayersProtosLight")
// include(":AiAiUi")
// include(":LowLightDreamLib")
// include(":BcSmartspace")
// include(":SystemUIChecks")
// include(":SystemUIClocks")
// include(":SystemUICommon")
// include(":SystemUIPlugins")
include(":ManagedProvisioning")
// include(":SystemUILogLib")
// include(":PlatformParameterizedLib")
// include(":SystemUIScreenshotLib")
// include(":SystemUIScreenshotViewUtilsLib")
// include(":SystemUIScreenshotBiometricsTestsLib")
// include(":SystemUITestUtils")
// include(":SystemUI")
// include(":SystemUIFlags")
// include(":NotificationFlags")
// include(":SystemUISharedFlags")
// include(":SystemUI-statsd")
// include(":BiometricsSharedLib")
//
// //Robolectric Locations:
include(":RobolectricLib")       // Contains a Robolectric android-all SdkProvider that downloads from the Google CI systems
//
include(":setupcompat")
include(":setupdesign")
include(":setupdesign-strings")
include(":setupdesign-lottie-loading-layout")
//
// includeAndroidProject(":LauncherQsTiles", "vendor/unbundled_google/libraries/launcherqstiles")
// includeAndroidProject(":SearchUi", "vendor/unbundled_google/libraries/searchuilib")
// includeAndroidProject(":ViewCaptureLib", "frameworks/libs/systemui/viewcapturelib")
// includeAndroidProject(":MotionToolLib", "frameworks/libs/systemui/motiontoollib")
// includeAndroidProject(":AnimationLibrary", "frameworks/libs/systemui/animationlib")
// includeAndroidProject(":NexusLauncher", "vendor/unbundled_google/packages/NexusLauncher")
// includeAndroidProject(":WallpaperPickerGoogle", "vendor/unbundled_google/packages/WallpaperPickerGoogle")
//
// include(":Monet")
// include(":MonetLib")
// include(":ScreenshotLib")
//
// if (ACETONE_LIB_CITC_CLIENT.isNotBlank()) {
//     includeAndroidProject(":OverlayLib", "vendor/unbundled_google/packages/Launcher3/overlaylib")
// }
//
// includeAndroidProject(":TitanSysuiConfigOverlay", "vendor/google/nexus_overlay/TitanSysuiConfigOverlay")
//
// // Sysui and launcher e2e tests projects:
// include(":PlatformScenarioTestsLib")
// include(":PlatformScenarioTests")
// include(":PlatformTestingAnnotations")
// include(":UiTestsLibLauncher")
//
// include(":SystemUIRoboScreenshotLib")
//
// // For trunk stable flags testing.
// include(":PlatformTestingFlagHelper")
//
// // Uncomment this for DockSetup app
// // include(":DockSetup")
//
// // Uncomment this for OneSearch Plugin app
// // include(":OneSearch")
// // project(":OneSearch").projectDir = file(rootDir).resolve("../../../NexusLauncher/plugins/OneSearchPlugin")
//
// // Uncomment this for Launcher Support app.
// // When enabling this also set enableJetifier to false in gradle.properties
// // include(":SupportApp")
// // project(":SupportApp").projectDir = file(rootDir).resolve("../../../Launcher3/supportApp")
//
// // Slim Launcher used for developing an alternate Launcher+Overview experience
// include(":SlimLauncher")
// project(":SlimLauncher").projectDir = file(rootDir).resolve("../../../Launcher3/SlimLauncher")

include(":android_onboarding.common")
include(":android_onboarding.contracts")
include(":android_onboarding.contracts.annotations")
include(":android_onboarding.contracts.provisioning")
include(":android_onboarding.contracts.setupwizard")
include(":android_onboarding.contracts.testing")
include(":android_onboarding.flags")
include(":android_onboarding.flags_hilt")
include(":android_onboarding.nodes")
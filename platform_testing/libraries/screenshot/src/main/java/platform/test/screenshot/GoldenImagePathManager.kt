/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.content.Context
import android.os.Build
import java.io.File

private const val BRAND_TAG = "brand"
private const val MODEL_TAG = "model"
private const val API_TAG = "api"
private const val SIZE_TAG = "size"
private const val RESOLUTION_TAG = "resolution"
private const val DISPLAY_TAG = "display"
private const val THEME_TAG = "theme"
private const val ORIENTATION_TAG = "orientation"

/**
 * Class to manage Directory structure of golden images.
 *
 * When you run a AR Diff test, different attributes/dimensions of the platform you are running on,
 * such as build, screen resolution, orientation etc. will/may render differently and therefore may
 * require a different golden image to compare against. You can manage these multiple golden images
 * related to your test using this utility class. It supports both device-less or device based
 * configurations. Please see GoldenImagePathManagerTest for detailed examples.
 *
 * You can configure where to find the golden images repo and local cache using [locationConfig].
 *
 * There are two ways to modify how the golden images are stored and retrieved for your test: A.
 * (Recommended) Create your own PathConfig object which takes a series of [PathElement]s Each path
 * element represents a dimension such as screen resolution that affects the golden image. This
 * dimension will be embedded either into the directory structure or into the filename itself. Your
 * test can also provide its own custom implementation of [PathElement] if the dimension your test
 * needs to rely on, is not supported. B. If you have a completely unique way of managing your
 * golden image repository and corresponding local cache, implement a derived class and override the
 * goldenIdentifierResolver function.
 *
 * NOTE: This class does not determine what combinations of attributes / dimensions your test code
 * will run for. That decision/configuration is part of your test configuration.
 *
 * @see GoldenPathManager use the file-type agnostic version instead.
 */
open class GoldenImagePathManager
@JvmOverloads
constructor(
    open val appContext: Context,
    open val assetsPathRelativeToBuildRoot: String = "assets",
    open var deviceLocalPath: String = getDeviceOutputDirectory(appContext),
    open val pathConfig: PathConfig = getSimplePathConfig()
) {

    init {
        val robolectricOverride = System.getProperty("robolectric.artifacts.dir")
        if (Build.FINGERPRINT.contains("robolectric") && !robolectricOverride.isNullOrEmpty()) {
            deviceLocalPath = robolectricOverride
        }
    }

    val imageExtension = "png"

    /*
     * Uses [pathConfig] and [testName] to construct the full path to the golden image.
     */
    open fun goldenIdentifierResolver(testName: String): String {
        val relativePath = pathConfig.resolveRelativePath(appContext)
        return "$relativePath$testName.$imageExtension"
    }
}

/**
 * Class to manage directory structure of golden files.
 *
 * Same as [GoldenImagePathManager], but without the builtin assumption about images.
 *
 * TODO(b/322324387): fold GoldenImagePathManager into this class.
 */
open class GoldenPathManager
@JvmOverloads
constructor(
    appContext: Context,
    assetsPathRelativeToBuildRoot: String = "assets",
    deviceLocalPath: String = getDeviceOutputDirectory(appContext),
    pathConfig: PathConfig = getSimplePathConfig()
) : GoldenImagePathManager(appContext, assetsPathRelativeToBuildRoot, deviceLocalPath, pathConfig) {

    final override fun goldenIdentifierResolver(testName: String) =
        goldenIdentifierResolver(testName, imageExtension)

    /*
     * Uses [pathConfig] and [testName] to construct the full path to the golden image.
     */
    open fun goldenIdentifierResolver(testName: String, extension: String): String {
        val relativePath = pathConfig.resolveRelativePath(appContext)
        return "$relativePath$testName.$extension"
    }
}

/*
 * Every dimension that impacts the golden image needs to be a part of the path/filename
 * that is used to access the golden. There are two types of attributes / dimensions.
 * One that depend on the device context and the once that are context agnostic.
 */
sealed class PathElementBase {
    abstract val attr: String
    abstract val isDir: Boolean
}

/*
 * For dimensions that do not need access to the device context e.g.
 * Build.MODEL, please instantiate the no context class.
 */
data class PathElementNoContext(
    override val attr: String,
    override val isDir: Boolean,
    val func: (() -> String)
) : PathElementBase()

/*
 * For dimensions that do not need to the device context e.g.
 * and / or can change during run-time, please instantiate this class.
 * e.g. screen orientation.
 */
data class PathElementWithContext(
    override val attr: String,
    override val isDir: Boolean,
    val func: ((Context) -> String)
) : PathElementBase()

/*
 * Converts an ordered list of PathElements into a relative path on filesystem.
 * The relative path is then combined with either repo path of local cache path
 * to get the full path to golden image.
 */
class PathConfig(vararg elems: PathElementBase) {
    val data = listOf(*elems)

    public fun resolveRelativePath(context: Context): String {
        return data
            .map {
                when (it) {
                    is PathElementWithContext -> it.func(context)
                    is PathElementNoContext -> it.func()
                } + if (it.isDir) "/" else "_"
            }
            .joinToString("")
    }
}

/*
 * This is the PathConfig that will be used by default.
 * An example directory structure using this config would be
 *  /google/pixel6/api32/600_400/
 */
fun getDefaultPathConfig(): PathConfig {
    return PathConfig(
        PathElementNoContext(BRAND_TAG, true, ::getDeviceBrand),
        PathElementNoContext(MODEL_TAG, true, ::getDeviceModel),
        PathElementNoContext(API_TAG, true, ::getAPIVersion),
        PathElementWithContext(SIZE_TAG, true, ::getScreenSize),
        PathElementWithContext(RESOLUTION_TAG, true, ::getScreenResolution)
    )
}

fun getSimplePathConfig(): PathConfig {
    return PathConfig(PathElementNoContext(MODEL_TAG, true, ::getDeviceModel))
}

/** The [PathConfig] that should be used when emulating a device using the [DeviceEmulationRule]. */
fun getEmulatedDevicePathConfig(emulationSpec: DeviceEmulationSpec): PathConfig {
    // Returns a path of the form
    // "/display_name/(light|dark)_(portrait|landscape)_golden_identifier.png".
    return PathConfig(
        PathElementNoContext(DISPLAY_TAG, isDir = true) { emulationSpec.display.name },
        PathElementNoContext(THEME_TAG, isDir = false) {
            if (emulationSpec.isDarkTheme) "dark" else "light"
        },
        PathElementNoContext(ORIENTATION_TAG, isDir = false) {
            if (emulationSpec.isLandscape) "landscape" else "portrait"
        },
    )
}

/*
 * Default output directory where all images generated as part of the test are stored.
 */
fun getDeviceOutputDirectory(context: Context) =
    File(context.filesDir, "platform_screenshots").toString()

/* Standard implementations for the usual list of dimensions that affect a golden image. */
fun getDeviceModel(): String {
    var model = Build.MODEL.lowercase()
    arrayOf("phone", "x86_64", "x86", "x64", "gms", "wear").forEach {
        model = model.replace(it, "")
    }
    return model.trim().replace(" ", "_")
}

fun getDeviceBrand(): String {
    var brand = Build.BRAND.lowercase()
    arrayOf("phone", "x86_64", "x86", "x64", "gms", "wear").forEach {
        brand = brand.replace(it, "")
    }
    return brand.trim().replace(" ", "_")
}

fun getAPIVersion() = "API" + Build.VERSION.SDK_INT.toString()

fun getScreenResolution(context: Context) =
    context.resources.displayMetrics.densityDpi.toString() + "dpi"

fun getScreenOrientation(context: Context) = context.resources.configuration.orientation.toString()

fun getScreenSize(context: Context): String {
    val heightdp = context.resources.configuration.screenHeightDp.toString()
    val widthdp = context.resources.configuration.screenWidthDp.toString()
    return "${heightdp}_$widthdp"
}

/*
 * If the dimension that your golden depends on, is not supported,
 * Please add its implementations here.
 */

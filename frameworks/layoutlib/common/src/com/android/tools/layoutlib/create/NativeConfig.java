/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.layoutlib.create;

/**
 * Stores data needed for native JNI registration, and possibly the framework bytecode
 * instrumentation.
 */
public class NativeConfig {

    private NativeConfig() {}

    public final static String[] DEFERRED_STATIC_INITIALIZER_CLASSES = new String [] {
            "android.graphics.ColorSpace",
            "android.graphics.FontFamily",
            "android.graphics.Matrix",
            "android.graphics.Path",
            // Order is important! Fonts and FontFamily have to be initialized before Typeface
            "android.graphics.fonts.Font",
            "android.graphics.fonts.FontFamily$Builder",
            "android.graphics.Typeface",
            "android.graphics.text.PositionedGlyphs",
            "android.graphics.text.LineBreaker",
    };

    public static final String[] DELEGATE_METHODS = new String[] {
            "android.app.ActivityManager#getService",
            "android.app.Fragment#instantiate", //(Landroid/content/Context;Ljava/lang/String;Landroid/os/Bundle;)Landroid/app/Fragment;",
            "android.content.res.AssetManager#createSystemAssetsInZygoteLocked",
            "android.content.res.AssetManager#getAssignedPackageIdentifiers",
            "android.content.res.AssetManager#nativeCreate",
            "android.content.res.AssetManager#nativeDestroy",
            "android.content.res.AssetManager#nativeGetThemeFreeFunction",
            "android.content.res.AssetManager#nativeThemeCreate",
            "android.content.res.Resources#getAnimation",
            "android.content.res.Resources#getAttributeSetSourceResId",
            "android.content.res.Resources#getBoolean",
            "android.content.res.Resources#getColor",
            "android.content.res.Resources#getColorStateList",
            "android.content.res.Resources#getDimension",
            "android.content.res.Resources#getDimensionPixelOffset",
            "android.content.res.Resources#getDimensionPixelSize",
            "android.content.res.Resources#getDrawable",
            "android.content.res.Resources#getFloat",
            "android.content.res.Resources#getFont",
            "android.content.res.Resources#getIdentifier",
            "android.content.res.Resources#getIntArray",
            "android.content.res.Resources#getInteger",
            "android.content.res.Resources#getLayout",
            "android.content.res.Resources#getQuantityString",
            "android.content.res.Resources#getQuantityText",
            "android.content.res.Resources#getResourceEntryName",
            "android.content.res.Resources#getResourceName",
            "android.content.res.Resources#getResourcePackageName",
            "android.content.res.Resources#getResourceTypeName",
            "android.content.res.Resources#getString",
            "android.content.res.Resources#getStringArray",
            "android.content.res.Resources#getText",
            "android.content.res.Resources#getTextArray",
            "android.content.res.Resources#getValue",
            "android.content.res.Resources#getValueForDensity",
            "android.content.res.Resources#getXml",
            "android.content.res.Resources#loadXmlResourceParser",
            "android.content.res.Resources#obtainAttributes",
            "android.content.res.Resources#obtainTypedArray",
            "android.content.res.Resources#openRawResource",
            "android.content.res.Resources#openRawResourceFd",
            "android.content.res.Resources$Theme#obtainStyledAttributes",
            "android.content.res.Resources$Theme#resolveAttribute",
            "android.content.res.Resources$Theme#resolveAttributes",
            "android.content.res.TypedArray#getValueAt",
            "android.content.res.TypedArray#obtain",
            "android.graphics.Canvas#getClipBounds",
            "android.graphics.ImageDecoder#decodeBitmapImpl",
            "android.graphics.Typeface#create",
            "android.graphics.drawable.AdaptiveIconDrawable#draw",
            "android.graphics.drawable.AnimatedVectorDrawable#draw",
            "android.graphics.drawable.AnimatedVectorDrawable$VectorDrawableAnimatorUI#onDraw",
            "android.graphics.drawable.DrawableInflater#inflateFromClass",
            "android.graphics.drawable.NinePatchDrawable#getOpacity",
            "android.graphics.fonts.Font$Builder#createBuffer",
            "android.graphics.fonts.SystemFonts#getSystemFontConfigInternal",
            "android.graphics.fonts.SystemFonts#mmap",
            "android.os.Binder#getNativeBBinderHolder",
            "android.os.Binder#getNativeFinalizer",
            "android.os.Handler#sendMessageAtFrontOfQueue",
            "android.os.Handler#sendMessageAtTime",
            "android.os.HandlerThread#run",
            "android.os.SystemProperties#find",
            "android.permission.PermissionManager#checkPermission",
            "android.preference.Preference#getView",
            "android.provider.DeviceConfig#getBoolean",
            "android.provider.DeviceConfig#getFloat",
            "android.provider.DeviceConfig#getInt",
            "android.provider.DeviceConfig#getLong",
            "android.provider.DeviceConfig#getProperty",
            "android.provider.DeviceConfig#getString",
            "android.provider.Settings$Config#getContentResolver",
            "android.text.format.DateFormat#is24HourFormat",
            "android.util.Xml#newPullParser",
            "android.view.Choreographer#getFrameTimeNanos",
            "android.view.Choreographer#getRefreshRate",
            "android.view.Choreographer#postCallbackDelayedInternal",
            "android.view.Choreographer#removeCallbacksInternal",
            "android.view.Display#getWindowManager",
            "android.view.Display#updateDisplayInfoLocked",
            "android.view.DisplayEventReceiver#nativeGetDisplayEventReceiverFinalizer",
            "android.view.DisplayEventReceiver#nativeInit",
            "android.view.HandlerActionQueue#postDelayed",
            "android.view.LayoutInflater#parseInclude",
            "android.view.LayoutInflater#rInflate",
            "android.view.MenuInflater#registerMenu",
            "android.view.PointerIcon#loadResource",
            "android.view.PointerIcon#registerDisplayListener",
            "android.view.SurfaceControl#nativeCreateTransaction",
            "android.view.SurfaceControl#nativeGetNativeTransactionFinalizer",
            "android.view.VelocityTracker#obtain",
            "android.view.View#dispatchDetachedFromWindow",
            "android.view.View#draw",
            "android.view.View#getWindowToken",
            "android.view.View#isInEditMode",
            "android.view.View#layout",
            "android.view.View#measure",
            "android.view.ViewRootImpl#performHapticFeedback",
            "android.view.WindowManagerGlobal#getWindowManagerService",
            "android.view.accessibility.AccessibilityManager#getInstance",
            "android.view.accessibility.AccessibilityManager#getWindowTransformationSpec",
            "android.view.inputmethod.InputMethodManager#hideSoftInputFromWindow",
            "android.view.inputmethod.InputMethodManager#isInEditMode",
            "android.view.inputmethod.InputMethodManager#showSoftInput",
            "android.widget.AbsListView#setupDeviceConfigProperties",
            "android.widget.Magnifier#show",
            "android.widget.RemoteViews#getApplicationInfo",
            "com.android.internal.util.XmlUtils#convertValueToInt",
            "com.android.internal.view.menu.MenuBuilder#createNewMenuItem",
            "dalvik.system.VMRuntime#getNotifyNativeInterval",
            "dalvik.system.VMRuntime#is64Bit",
            "dalvik.system.VMRuntime#newUnpaddedArray",
            "libcore.io.MemoryMappedFile#bigEndianIterator",
            "libcore.io.MemoryMappedFile#close",
            "libcore.io.MemoryMappedFile#mmapRO",
            "libcore.util.NativeAllocationRegistry#applyFreeFunction",
    };

    public final static String[] DELEGATE_CLASS_NATIVES = new String[] {
            "android.os.SystemClock",
            "android.view.Display",
            "libcore.icu.ICU",
    };

    /**
     * The list of core classes to register with JNI
     */
    public final static String[] CORE_CLASS_NATIVES = new String[] {
            "android.animation.PropertyValuesHolder",
            "android.content.res.StringBlock",
            "android.content.res.XmlBlock",
            "android.os.SystemProperties",
            "android.os.Trace",
            "android.text.AndroidCharacter",
            "android.util.Log",
            "android.view.MotionEvent",
            "com.android.internal.util.VirtualRefBasePtr",
            "libcore.util.NativeAllocationRegistry_Delegate",
    };

    /**
     * The list of graphics classes to register with JNI
     */
    public final static String[] GRAPHICS_CLASS_NATIVES = new String[] {
            "android.graphics.Bitmap",
            "android.graphics.BitmapFactory",
            "android.graphics.ByteBufferStreamAdaptor",
            "android.graphics.Camera",
            "android.graphics.Canvas",
            "android.graphics.CanvasProperty",
            "android.graphics.ColorFilter",
            "android.graphics.ColorSpace",
            "android.graphics.CreateJavaOutputStreamAdaptor",
            "android.graphics.DrawFilter",
            "android.graphics.FontFamily",
            "android.graphics.Graphics",
            "android.graphics.ImageDecoder",
            "android.graphics.Interpolator",
            "android.graphics.MaskFilter",
            "android.graphics.Matrix",
            "android.graphics.NinePatch",
            "android.graphics.Paint",
            "android.graphics.Path",
            "android.graphics.PathEffect",
            "android.graphics.PathMeasure",
            "android.graphics.Picture",
            "android.graphics.RecordingCanvas",
            "android.graphics.Region",
            "android.graphics.RenderEffect",
            "android.graphics.RenderNode",
            "android.graphics.Shader",
            "android.graphics.Typeface",
            "android.graphics.YuvImage",
            "android.graphics.animation.NativeInterpolatorFactory",
            "android.graphics.animation.RenderNodeAnimator",
            "android.graphics.drawable.AnimatedVectorDrawable",
            "android.graphics.drawable.VectorDrawable",
            "android.graphics.fonts.Font",
            "android.graphics.fonts.FontFamily",
            "android.graphics.text.LineBreaker",
            "android.graphics.text.MeasuredText",
            "android.graphics.text.TextRunShaper",
            "android.util.PathParser",
    };
}

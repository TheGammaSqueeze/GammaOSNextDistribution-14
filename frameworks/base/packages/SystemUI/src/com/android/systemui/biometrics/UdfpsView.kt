/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.biometrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.FileObserver
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams
import com.android.systemui.doze.DozeReceiver
import java.io.File
import java.io.FileNotFoundException

import vendor.goodix.hardware.biometrics.fingerprint.V2_1.IGoodixFingerprintDaemon

import vendor.xiaomi.hw.touchfeature.V1_0.ITouchFeature
import vendor.xiaomi.hardware.fingerprintextension.V1_0.IXiaomiFingerprint

import android.os.Handler
import android.os.HandlerThread

import vendor.nubia.ifaa.V1_0.IIfaa

private const val TAG = "UdfpsView"

/**
 * The main view group containing all UDFPS animations.
 */
class UdfpsView(
    context: Context,
    attrs: AttributeSet?
) : FrameLayout(context, attrs), DozeReceiver {
    private var currentOnIlluminatedRunnable: Runnable? = null
    private val mySurfaceView = SurfaceView(context)
    init {
        mySurfaceView.setVisibility(INVISIBLE)
        mySurfaceView.setZOrderOnTop(true)
        addView(mySurfaceView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mySurfaceView.holder.addCallback(object: SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {
                Log.d("PHH", "Surface created!")
                val paint = Paint(0 /* flags */);
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL);
                val colorStr = android.os.SystemProperties.get("persist.sys.phh.fod_color", "00ff00");
                try {
                    val parsedColor = Color.parseColor("#" + colorStr);
                    val r = (parsedColor shr 16) and 0xff;
                    val g = (parsedColor shr  8) and 0xff;
                    val b = (parsedColor shr  0) and 0xff;
                    paint.setARGB(255, r, g, b);
                } catch(t: Throwable) {
                    Log.d("PHH", "Failed parsing color #" + colorStr, t);
                }
                var canvas: Canvas? = null
                try {
                    canvas = p0.lockCanvas();
                    Log.d("PHH", "Surface dimensions ${canvas.getWidth()*1.0f} ${canvas.getHeight()*1.0f}")
                    canvas.drawOval(RectF(overlayParams.sensorBounds), paint);
                } finally {
                    // Make sure the surface is never left in a bad state.
                    if (canvas != null) {
                        p0.unlockCanvasAndPost(canvas);
                    }
                }

                currentOnIlluminatedRunnable?.run()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.d("PHH", "Got surface size $p1 $p2 $p3")
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                Log.d("PHH", "Surface destroyed!")
            }
        })
        mySurfaceView.holder.setFormat(PixelFormat.RGBA_8888)

    }

    // sensorRect may be bigger than the sensor. True sensor dimensions are defined in
    // overlayParams.sensorBounds
    var sensorRect = Rect()
    private var mUdfpsDisplayMode: UdfpsDisplayModeProvider? = null
    private val debugTextPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLUE
        textSize = 32f
    }

    /** View controller (can be different for enrollment, BiometricPrompt, Keyguard, etc.). */
    var animationViewController: UdfpsAnimationViewController<*>? = null

    /** Parameters that affect the position and size of the overlay. */
    var overlayParams = UdfpsOverlayParams()

    var dimUpdate: (Float) -> Unit = {}

    /** Debug message. */
    var debugMessage: String? = null
        set(value) {
            field = value
            postInvalidate()
        }

    /** True after the call to [configureDisplay] and before the call to [unconfigureDisplay]. */
    var isDisplayConfigured: Boolean = false
        private set

    fun setUdfpsDisplayModeProvider(udfpsDisplayModeProvider: UdfpsDisplayModeProvider?) {
        mUdfpsDisplayMode = udfpsDisplayModeProvider
    }

    // Don't propagate any touch events to the child views.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return (animationViewController == null || !animationViewController!!.shouldPauseAuth())
    }

    override fun dozeTimeTick() {
        animationViewController?.dozeTimeTick()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Updates sensor rect in relation to the overlay view
        animationViewController?.onSensorRectUpdated(RectF(sensorRect))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.v(TAG, "onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.v(TAG, "onDetachedFromWindow")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isDisplayConfigured) {
            if (!debugMessage.isNullOrEmpty()) {
                canvas.drawText(debugMessage!!, 0f, 160f, debugTextPaint)
            }
        }
    }

    fun goodixCmd(id: Int) {
        val goodixSvc = IGoodixFingerprintDaemon.getService()
        if(goodixSvc != null) {
            goodixSvc.sendCommand(id, ArrayList(), { returnCode, resultData -> {
                Log.e("PHH-Enroll", "Goodix send command returned code "+ returnCode);
            }});
        }
    }

    val asusGhbmOnAchieved = "/sys/class/drm/ghbm_on_achieved"
    var hasAsusGhbm = File(asusGhbmOnAchieved).exists()
    var samsungActualMaskBrightness = "/sys/class/lcd/panel/actual_mask_brightness"
    val hasSamsungMask = File(samsungActualMaskBrightness).exists()
    var fodFileObserver: FileObserver? = null

    val xiaomiDispParam = "/sys/class/mi_display/disp-DSI-0/disp_param"
    var hasXiaomiLhbm = try {
            val xiaomiFingerprintService = IXiaomiFingerprint.getService()
            File(xiaomiDispParam).exists() && xiaomiFingerprintService != null
        } catch(e: Exception) {
            false
        }

    private val handlerThread = HandlerThread("UDFPS").also { it.start() }
    val myHandler = Handler(handlerThread.looper)

    // This file contain current hbm value
    val nubiaHbmState = "/sys/kernel/lcd_enhance/hbm_state"
    var hasNubiaHbm = File(nubiaHbmState).exists()

    fun configureDisplay(onDisplayConfigured: Runnable) {
        isDisplayConfigured = true
        animationViewController?.onDisplayConfiguring()
        mUdfpsDisplayMode?.enable(onDisplayConfigured)

        mySurfaceView.setVisibility(VISIBLE)
        Log.d("PHH", "setting surface visible!")

        val brightnessFiles = listOf(
            File("/sys/class/backlight/panel/brightness"),
            File("/sys/class/backlight/panel0-backlight/brightness"),
            File("/sys/devices/platform/soc/soc:mtk_leds/leds/lcd-backlight/brightness")
        )
        val maxBrightnessFiles = listOf(
            File("/sys/class/backlight/panel/max_brightness"),
            File("/sys/class/backlight/panel0-backlight/max_brightness"),
            File("/sys/devices/platform/soc/soc:mtk_leds/leds/lcd-backlight/max_brightness")
        )

        var brightness: Double = 0.0
        var maxBrightness: Double = 0.0
        var bmFilesExist: Boolean = false

        brightnessFiles.zip(maxBrightnessFiles) {bFile, mFile ->
            if (bFile.exists() && mFile.exists()) {
                bmFilesExist = true
                brightness = bFile.readText().toDouble()
                maxBrightness = mFile.readText().toDouble()
            }
        }

        val dim = if (bmFilesExist) {
            1.0 - Math.pow( (brightness / maxBrightness), 1/2.3);
        } else {
            0.0
        }

        // Assume HBM is max brightness
        Log.d("PHH-Enroll", "Brightness is $brightness / $maxBrightness, setting dim to $dim")
        if (hasAsusGhbm) {
            dimUpdate(dim.toFloat())
        }
        if (hasSamsungMask) {
            dimUpdate(dim.toFloat())
        }

        if(android.os.SystemProperties.get("ro.vendor.build.fingerprint").contains("ASUS")) {
            goodixCmd(200001)
        }

        if(hasXiaomiLhbm){
            Log.d("PHH-Enroll", "Xiaomi scenario in UdfpsView reached!")
            mySurfaceView.setVisibility(INVISIBLE)

            try {
                IXiaomiFingerprint.getService().extCmd(android.os.SystemProperties.getInt("persist.phh.xiaomi.fod.enrollment.id", 4), 1);
            } catch(t: Throwable) {}
            var res = try { ITouchFeature.getService().setTouchMode(0, 10, 1) } catch(t: Throwable){ -1 }
            if(res != 0){
                Log.d("PHH-Enroll", "SetTouchMode 10,1 was NOT executed successfully. Res is " + res)
            }

            myHandler.postDelayed({
                var ret200 = try { ITouchFeature.getService().setTouchMode(0, 10, 1); } catch(t: Throwable) { -1 }

                if(ret200 != 0){
                    Log.d("PHH-Enroll", "myHandler.postDelayed 200ms -SetTouchMode was NOT executed successfully. Ret is " + ret200)
                }

                myHandler.postDelayed({
                    Log.d("PHH-Enroll", "myHandler.postDelayed 600ms - line prior to setTouchMode 10,0")
                    var ret600 = try { ITouchFeature.getService().setTouchMode(0, 10, 0); } catch(t: Throwable) { -1 }

                    if(ret600 != 0){
                        Log.d("PHH-Enroll", "myHandler.postDelayed 600ms -SetTouchMode 10,0 was NOT executed successfully. Ret is " + ret600)
                    }
                }, 600)
            }, 200)
        }
          if(hasNubiaHbm) {
            Log.d("PHH-Enroll", "Nubia scenario in UdfpsView reached!")
            File(nubiaHbmState).writeText("4095")
          }

    }

    fun unconfigureDisplay() {
        isDisplayConfigured = false
        animationViewController?.onDisplayUnconfigured()
        mUdfpsDisplayMode?.disable(null /* onDisabled */)

        if (hasAsusGhbm) {
            fodFileObserver = object: FileObserver(asusGhbmOnAchieved, FileObserver.MODIFY) {
                override fun onEvent(event: Int, path: String?): Unit {
                    if (path == null) return
                    Log.d("PHH-Enroll", "Asus ghbm event")
                    try {
                        val spotOn = File(asusGhbmOnAchieved).readText().toInt()
                        if(spotOn == 0) {
                            dimUpdate(0.0f)
                            fodFileObserver?.stopWatching()
                            fodFileObserver = null
                        }
                    } catch(e: Exception) {
                        Log.d("PHH-Enroll", "Failed dimpdate off", e)
                    }
                }
            };
            fodFileObserver?.startWatching();
        } else if (hasSamsungMask) {
            fodFileObserver = object: FileObserver(samsungActualMaskBrightness, FileObserver.MODIFY) {
                override fun onEvent(event: Int, path: String?): Unit {
                    if (path == null) return
                    Log.d("PHH-Enroll", "samsung mask brightness event")
                    try {
                        val spotOn = File(samsungActualMaskBrightness).readText().toInt()
                        if(spotOn == 0) {
                            dimUpdate(0.0f)
                            fodFileObserver?.stopWatching()
                            fodFileObserver = null
                        }
                    } catch(e: Exception) {
                        Log.d("PHH-Enroll", "Failed dimpdate off", e)
                    }
                }
            };
            fodFileObserver?.startWatching();
        } else if(hasXiaomiLhbm) {
            IXiaomiFingerprint.getService().extCmd(android.os.SystemProperties.getInt("persist.phh.xiaomi.fod.enrollment.id", 4), 0);
            ITouchFeature.getService().setTouchMode(0, 10, 0);
        } else if(hasNubiaHbm) {
            Log.d("PHH-Enroll", "Nubia Restore brightness")
            File(nubiaHbmState).writeText(File("/sys/class/backlight/panel0-backlight/brightness").readText())
        } else {
            dimUpdate(0.0f)
        }

        mySurfaceView.setVisibility(INVISIBLE)
        Log.d("PHH", "setting surface invisible!")
        if(android.os.SystemProperties.get("ro.vendor.build.fingerprint").contains("ASUS")) {
            goodixCmd(200003)
        }
    }
}

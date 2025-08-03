/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wallpaper.picker.preview.data.repository

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.util.Log
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.effects.Effect
import com.android.wallpaper.effects.EffectContract
import com.android.wallpaper.effects.EffectsController
import com.android.wallpaper.picker.data.LiveWallpaperData
import com.android.wallpaper.picker.data.WallpaperId
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.widget.floatingsheetcontent.WallpaperEffectsView2.EffectTextRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

@Singleton
class EffectsRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val effectsController: EffectsController,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) {
    enum class EffectStatus {
        EFFECT_DISABLE,
        EFFECT_READY,
        EFFECT_DOWNLOAD_READY,
        EFFECT_DOWNLOAD_IN_PROGRESS,
        EFFECT_APPLY_IN_PROGRESS,
        EFFECT_APPLIED,
    }

    private val _effectStatus = MutableStateFlow(EffectStatus.EFFECT_DISABLE)
    val effectStatus = _effectStatus.asStateFlow()
    val wallpaperEffect = MutableStateFlow<Effect?>(null)
    // This StaticWallpaperModel is set when initializing the repository and used for
    // 1. Providing essential data to construct LiveWallpaperData when effect is enabled
    // 2. Reverting back to the original static image wallpaper when effect is disabled
    private lateinit var staticWallpaperModel: StaticWallpaperModel
    private lateinit var onWallpaperUpdated: (wallpaper: WallpaperModel) -> Unit

    suspend fun initializeEffect(
        staticWallpaperModel: StaticWallpaperModel,
        onWallpaperModelUpdated: (wallpaper: WallpaperModel) -> Unit
    ) {
        this.staticWallpaperModel = staticWallpaperModel
        onWallpaperUpdated = onWallpaperModelUpdated
        withContext(bgDispatcher) {
            val listener =
                EffectsController.EffectsServiceListener {
                    _,
                    bundle,
                    resultCode,
                    originalStatusCode,
                    errorMessage ->
                    when (resultCode) {
                        EffectsController.RESULT_PROBE_SUCCESS -> {
                            _effectStatus.value = EffectStatus.EFFECT_READY
                        }
                        EffectsController.RESULT_PROBE_ERROR -> {
                            // Do nothing intended
                        }
                        EffectsController.RESULT_ERROR_PROBE_SUPPORT_FOREGROUND -> {
                            if (BaseFlags.get().isWallpaperEffectModelDownloadEnabled()) {
                                _effectStatus.value = EffectStatus.EFFECT_DOWNLOAD_READY
                            }
                        }
                        EffectsController.RESULT_PROBE_FOREGROUND_DOWNLOADING -> {
                            _effectStatus.value = EffectStatus.EFFECT_DOWNLOAD_IN_PROGRESS
                        }
                        EffectsController.RESULT_FOREGROUND_DOWNLOAD_SUCCEEDED -> {
                            // TODO logger.logEffectForegroundDownload
                            _effectStatus.value = EffectStatus.EFFECT_READY
                        }
                        EffectsController.RESULT_FOREGROUND_DOWNLOAD_FAILED -> {
                            // TODO logger.logEffectForegroundDownload
                            _effectStatus.value = EffectStatus.EFFECT_DOWNLOAD_READY
                        }
                        EffectsController.RESULT_SUCCESS -> {
                            _effectStatus.value = EffectStatus.EFFECT_APPLIED
                            // TODO logger.logEffectApply
                            bundle.getCinematicWallpaperModel()?.let {
                                onWallpaperUpdated.invoke(it)
                            }
                        }
                        EffectsController.RESULT_SUCCESS_WITH_GENERATION_ERROR -> {
                            _effectStatus.value = EffectStatus.EFFECT_APPLIED
                            // TODO logger.logEffectApply
                            bundle.getCinematicWallpaperModel()?.let {
                                onWallpaperUpdated.invoke(it)
                            }
                        }
                        EffectsController.RESULT_SUCCESS_REUSED -> {
                            _effectStatus.value = EffectStatus.EFFECT_APPLIED
                            bundle.getCinematicWallpaperModel()?.let {
                                onWallpaperUpdated.invoke(it)
                            }
                        }
                        else -> {
                            // TODO onImageEffectFailed
                            _effectStatus.value = EffectStatus.EFFECT_READY
                        }
                    }
                }
            // TODO remove listener when destroy
            effectsController.setListener(listener)

            effectsController.contentUri.let { uri ->
                if (Uri.EMPTY.equals(uri)) {
                    return@withContext
                }

                // Query effect provider
                context.contentResolver
                    .query(
                        uri,
                        /* projection= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* sortOrder= */ null
                    )
                    ?.use {
                        while (it.moveToNext()) {
                            val titleRow: Int = it.getColumnIndex(EffectContract.KEY_EFFECT_TITLE)
                            val idRow: Int = it.getColumnIndex(EffectContract.KEY_EFFECT_ID)
                            wallpaperEffect.value =
                                Effect(
                                    it.getInt(idRow),
                                    it.getString(titleRow),
                                    effectsController.targetEffect
                                )
                        }
                    }
            }

            if (effectsController.isEffectTriggered) {
                _effectStatus.value = EffectStatus.EFFECT_READY
            } else {
                effectsController.triggerEffect(context)
            }
        }
    }

    private fun Bundle.getCinematicWallpaperModel(): LiveWallpaperModel? {
        val componentName =
            if (containsKey(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT)) {
                getParcelable<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT)
            } else {
                null
            }
                ?: return null

        val assetId =
            if (containsKey(EffectContract.ASSET_ID)) {
                getInt(EffectContract.ASSET_ID).toString()
            } else null

        val resolveInfos =
            Intent(WallpaperService.SERVICE_INTERFACE)
                .apply { setClassName(componentName.packageName, componentName.className) }
                .let {
                    context.packageManager.queryIntentServices(it, PackageManager.GET_META_DATA)
                }
        if (resolveInfos.isEmpty()) {
            Log.w(TAG, "Couldn't find live wallpaper for " + componentName.className)
            return null
        }

        try {
            val wallpaperInfo = WallpaperInfo(context, resolveInfos[0])
            val commonWallpaperData =
                staticWallpaperModel.commonWallpaperData.copy(
                    id =
                        WallpaperId(
                            componentName = componentName,
                            uniqueId =
                                if (assetId != null) "${wallpaperInfo.serviceName}_$assetId"
                                else wallpaperInfo.serviceName,
                            collectionId = staticWallpaperModel.commonWallpaperData.id.collectionId,
                        ),
                )
            val liveWallpaperData =
                LiveWallpaperData(
                    groupName = "",
                    systemWallpaperInfo = wallpaperInfo,
                    isTitleVisible = false,
                    isApplied = false,
                    effectNames = null,
                )
            return LiveWallpaperModel(
                commonWallpaperData = commonWallpaperData,
                liveWallpaperData = liveWallpaperData,
                creativeWallpaperData = null,
                internalLiveWallpaperData = null,
            )
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "Skipping wallpaper " + resolveInfos[0].serviceInfo, e)
            return null
        } catch (e: IOException) {
            Log.w(TAG, "Skipping wallpaper " + resolveInfos[0].serviceInfo, e)
            return null
        }
    }

    fun enableImageEffect(effect: EffectsController.EffectEnumInterface) {
        _effectStatus.value = EffectStatus.EFFECT_APPLY_IN_PROGRESS
        // TODO: Maybe we should call reconnect wallpaper if we have created a LiveWallpaperModel
        //       if (mLiveWallpaperInfo != null) {
        //           mCinematicViewModel.reconnectWallpaper()
        //           return
        //       }
        val uri = staticWallpaperModel.imageWallpaperData?.uri ?: return
        effectsController.generateEffect(effect, uri)
        // TODO: Implement time out
    }

    fun disableImageEffect() {
        // TODO implement disabling effect
        _effectStatus.value = EffectStatus.EFFECT_READY
        onWallpaperUpdated.invoke(staticWallpaperModel)
    }

    fun destroy() {
        effectsController.removeListener()
    }

    fun isTargetEffect(effect: EffectsController.EffectEnumInterface): Boolean {
        return effectsController.isTargetEffect(effect)
    }

    fun getEffectTextRes(): EffectTextRes {
        return EffectTextRes(
            effectsController.effectTitle,
            effectsController.effectFailedTitle,
            effectsController.effectSubTitle,
            effectsController.retryInstruction,
            effectsController.noEffectInstruction,
        )
    }

    companion object {
        private const val TAG = "EffectsRepository"
    }
}

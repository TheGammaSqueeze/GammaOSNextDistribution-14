package com.android.intentresolver.v2.platform

import android.content.ComponentName
import android.content.res.Resources
import androidx.annotation.StringRes
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

internal fun Resources.componentName(@StringRes resId: Int): ComponentName? {
    check(getResourceTypeName(resId) == "string") { "resId must be a string" }
    return ComponentName.unflattenFromString(getString(resId))
}

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class ImageEditor

@Module
@InstallIn(SingletonComponent::class)
object ImageEditorModule {
    /**
     * The name of the preferred Activity to launch for editing images. This is added to Intents to
     * edit images using Intent.ACTION_EDIT.
     */
    @Provides
    @Singleton
    @ImageEditor
    fun imageEditorComponent(@ApplicationOwned resources: Resources) =
        Optional.ofNullable(resources.componentName(R.string.config_systemImageEditor))
}

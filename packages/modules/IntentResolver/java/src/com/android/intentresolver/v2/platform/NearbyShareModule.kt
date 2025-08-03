package com.android.intentresolver.v2.platform

import android.content.ComponentName
import android.content.res.Resources
import android.provider.Settings.Secure.NEARBY_SHARING_COMPONENT
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class NearbyShare

@Module
@InstallIn(SingletonComponent::class)
object NearbyShareModule {

    @Provides
    @Singleton
    @NearbyShare
    fun nearbyShareComponent(@ApplicationOwned resources: Resources, settings: SecureSettings) =
        Optional.ofNullable(
            ComponentName.unflattenFromString(
                settings.getString(NEARBY_SHARING_COMPONENT)?.ifEmpty { null }
                    ?: resources.getString(R.string.config_defaultNearbySharingComponent),
            )
        )
}

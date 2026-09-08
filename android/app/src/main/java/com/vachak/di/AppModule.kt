package com.vachak.di

import android.content.Context
import com.vachak.content.ContentEngine
import com.vachak.engine.EngineProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Minimal Hilt module — binds EngineProvider so UI can @Inject it.
 * Keeps EngineProvider as-is (no breakage); Hilt is additive.
 * Future: bind each engine interface separately for finer test doubles.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEngineProvider(@ApplicationContext context: Context): EngineProvider {
        return EngineProvider.real(context)
    }

    @Provides
    fun provideContentEngine(@ApplicationContext context: Context): ContentEngine {
        return ContentEngine(context)
    }

    @Provides
    fun provideTranslationEngine(engineProvider: EngineProvider) = engineProvider.translation

    @Provides
    fun provideAsrEngine(engineProvider: EngineProvider) = engineProvider.asr

    @Provides
    fun provideTtsEngine(engineProvider: EngineProvider) = engineProvider.tts
}

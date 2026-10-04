package com.rikkahub.deepseeklocal.di

import android.content.Context
import androidx.room.Room
import com.rikkahub.deepseeklocal.data.local.db.LogDao
import com.rikkahub.deepseeklocal.data.local.db.LogDatabase
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import com.rikkahub.deepseeklocal.data.remote.deepseek.DeepSeekClient
import com.rikkahub.deepseeklocal.server.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Global singletons: stores, db, HTTP clients, session map. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context) = SettingsDataStore(ctx)

    @Provides @Singleton
    fun provideTokenStore(@ApplicationContext ctx: Context) = TokenStore(ctx)

    @Provides @Singleton
    fun provideLogDatabase(@ApplicationContext ctx: Context): LogDatabase =
        Room.databaseBuilder(ctx, LogDatabase::class.java, "deepseek_logs.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideLogDao(db: LogDatabase): LogDao = db.logDao()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideDeepSeekClient(okHttp: OkHttpClient) = DeepSeekClient(okHttp)

    @Provides @Singleton
    fun provideSessionManager() = SessionManager()
}

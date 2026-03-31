package com.musiqq.stockscreener.di

import android.content.Context
import androidx.room.Room
import com.musiqq.stockscreener.BuildConfig
import com.musiqq.stockscreener.data.local.AppDatabase
import com.musiqq.stockscreener.data.local.ScoreWeightPreferences
import com.musiqq.stockscreener.data.local.ThemePreferences
import com.musiqq.stockscreener.data.local.dao.WatchlistDao
import com.musiqq.stockscreener.data.remote.SupabaseApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSupabaseApi(client: OkHttpClient): SupabaseApi {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "stockscreener.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    @Singleton
    fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences {
        return ThemePreferences(context)
    }

    @Provides
    @Singleton
    fun provideScoreWeightPreferences(@ApplicationContext context: Context): ScoreWeightPreferences {
        return ScoreWeightPreferences(context)
    }
}

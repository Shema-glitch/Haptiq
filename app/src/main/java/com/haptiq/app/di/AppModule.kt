package com.haptiq.app.di

import android.content.Context
import com.haptiq.app.data.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideHaptiqDao(database: AppDatabase): HaptiqDao {
        return database.haptiqDao()
    }

    @Provides
    @Singleton
    fun providePlayerManager(@ApplicationContext context: Context): PlayerManager {
        return HaptiqPlayerManager.getInstance(context)
    }
}

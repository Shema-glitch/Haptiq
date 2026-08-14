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
abstract class AppModule {

    // Plain interface-to-implementation binding — @Binds, not @Provides: no
    // instantiation logic is needed, Hilt just needs to know which impl satisfies
    // the PlayerManager type. Also lets Hilt skip generating a factory method body.
    @Binds
    @Singleton
    abstract fun providePlayerManager(playerManager: HaptiqPlayerManager): PlayerManager

    companion object {
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
    }
}

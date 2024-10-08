package org.technoserve.cherieapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.technoserve.cherieapp.database.converters.BitmapConverter
import org.technoserve.cherieapp.database.converters.DateConverter


@Database(entities = [Prediction::class], version = 4, exportSchema = true)
@TypeConverters(BitmapConverter::class, DateConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun predictionsDAO(): PredictionDAO

    companion object {

        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "cherie_database"
                    ).fallbackToDestructiveMigration()
                    .build()

                    INSTANCE = instance
                }

                return instance
            }
        }
    }

}



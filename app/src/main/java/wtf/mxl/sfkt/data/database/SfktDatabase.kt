package wtf.mxl.sfkt.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Server::class], version = 1, exportSchema = false)
abstract class SfktDatabase : RoomDatabase() {

    abstract fun serverDao(): ServerDao

    companion object {
        @Volatile
        private var INSTANCE: SfktDatabase? = null

        fun getDatabase(context: Context): SfktDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SfktDatabase::class.java,
                    "sfkt_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

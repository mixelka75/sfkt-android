package wtf.mxl.sfkt.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY ping ASC NULLS LAST, name ASC")
    fun getAllServers(): Flow<List<Server>>

    @Query("SELECT * FROM servers ORDER BY ping ASC NULLS LAST, name ASC")
    suspend fun getAllServersList(): List<Server>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): Server?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: Server): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(servers: List<Server>)

    @Update
    suspend fun update(server: Server)

    @Query("UPDATE servers SET ping = :ping, lastPingTime = :time WHERE id = :id")
    suspend fun updatePing(id: Long, ping: Int?, time: Long)

    @Delete
    suspend fun delete(server: Server)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getCount(): Int
}

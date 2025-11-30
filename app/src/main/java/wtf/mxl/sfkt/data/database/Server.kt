package wtf.mxl.sfkt.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class Server(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String,
    val originalUrl: String,
    val ping: Int? = null,
    val lastPingTime: Long? = null
)

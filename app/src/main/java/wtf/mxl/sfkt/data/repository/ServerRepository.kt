package wtf.mxl.sfkt.data.repository

import kotlinx.coroutines.flow.Flow
import wtf.mxl.sfkt.data.database.Server
import wtf.mxl.sfkt.data.database.ServerDao

class ServerRepository(private val serverDao: ServerDao) {

    val allServers: Flow<List<Server>> = serverDao.getAllServers()

    suspend fun getAllServersList(): List<Server> = serverDao.getAllServersList()

    suspend fun getById(id: Long): Server? = serverDao.getById(id)

    suspend fun insert(server: Server): Long = serverDao.insert(server)

    suspend fun insertAll(servers: List<Server>) = serverDao.insertAll(servers)

    suspend fun update(server: Server) = serverDao.update(server)

    suspend fun updatePing(id: Long, ping: Int?) {
        serverDao.updatePing(id, ping, System.currentTimeMillis())
    }

    suspend fun delete(server: Server) = serverDao.delete(server)

    suspend fun deleteAll() = serverDao.deleteAll()

    suspend fun replaceAll(servers: List<Server>) {
        serverDao.deleteAll()
        serverDao.insertAll(servers)
    }

    suspend fun getCount(): Int = serverDao.getCount()
}

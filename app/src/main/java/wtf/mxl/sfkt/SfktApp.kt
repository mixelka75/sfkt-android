package wtf.mxl.sfkt

import android.app.Application
import com.google.android.material.color.DynamicColors
import wtf.mxl.sfkt.data.database.SfktDatabase
import wtf.mxl.sfkt.data.repository.ServerRepository

class SfktApp : Application() {

    val database by lazy { SfktDatabase.getDatabase(this) }
    val serverRepository by lazy { ServerRepository(database.serverDao()) }

    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic colors
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

package com.iparsan.maprog

import android.app.Application
import com.iparsan.maprog.data.Repo
import com.iparsan.maprog.recording.RecordingService
import org.osmdroid.config.Configuration

class MaprogApp : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // osmdroid: identificador obligatorio para los servidores de teselas de OpenStreetMap
        Configuration.getInstance().apply {
            userAgentValue = "Maprog/${BuildConfig.VERSION_NAME} (com.iparsan.maprog)"
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir.resolve("tiles")
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024 // caché de mapas de 300 MB para usar sin conexión
        }
        repo = Repo(this)
        RecordingService.createChannel(this)
    }

    companion object {
        lateinit var instance: MaprogApp
            private set
    }
}

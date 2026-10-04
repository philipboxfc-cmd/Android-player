package app.fca.player

import android.app.Application
import app.fca.player.di.AppContainer

class FcaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

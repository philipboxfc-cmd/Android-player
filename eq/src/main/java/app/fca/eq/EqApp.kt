package app.fca.eq

import android.app.Application
import app.fca.eq.data.Store

class EqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

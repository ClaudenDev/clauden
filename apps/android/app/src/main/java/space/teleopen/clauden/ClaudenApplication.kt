package space.teleopen.clauden

import android.app.Application
import space.teleopen.clauden.proxy.ProxyController

class ClaudenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProxyController.attach(this)
    }
}

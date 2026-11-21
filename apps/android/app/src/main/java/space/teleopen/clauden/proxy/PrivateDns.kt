package space.teleopen.clauden.proxy

import android.content.Context
import android.provider.Settings

object PrivateDns {

    fun mode(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, "private_dns_mode", 0)
    } catch (_: Throwable) {
        0
    }

    fun specifier(context: Context): String = try {
        Settings.Global.getString(context.contentResolver, "private_dns_specifier").orEmpty()
    } catch (_: Throwable) {
        ""
    }

    fun enabled(context: Context): Boolean = mode(context) != 0

    fun strict(context: Context): Boolean = mode(context) == 2 && specifier(context).isNotBlank()

    fun describe(context: Context): String {
        val host = specifier(context)
        return when {
            host.isNotBlank() -> host
            mode(context) == 1 -> "включён (DoT по умолчанию)"
            else -> "не настроен"
        }
    }
}

package de.austin_bee.reboot

import android.content.Context
import androidx.core.content.edit

object RebootPrefs {
    private const val FN = "reboot_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_HOUR = "hour"
    private const val K_MIN  = "minute"

    fun setEnabled(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(FN, 0).edit { putBoolean(K_ENABLED, enabled) }

    fun setTime(ctx: Context, hour: Int, minute: Int) =
        ctx.getSharedPreferences(FN, 0).edit { putInt(K_HOUR, hour); putInt(K_MIN, minute) }

    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(FN, 0).getBoolean(K_ENABLED, false)

    fun getTime(ctx: Context): Pair<Int,Int>? {
        val sp = ctx.getSharedPreferences(FN, 0)
        return if (sp.contains(K_HOUR) && sp.contains(K_MIN))
            sp.getInt(K_HOUR, 3) to sp.getInt(K_MIN, 30) else null
    }
}

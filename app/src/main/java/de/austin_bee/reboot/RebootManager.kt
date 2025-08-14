package de.austin_bee.reboot

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Reschedules on every app-open; does nothing if prerequisites aren't met.
 */
object RebootManager {

    /** Call from your first Activity.onCreate(). Safe to call repeatedly. */
    fun onAppOpen(ctx: Context) {
        if (!RebootPrefs.isEnabled(ctx)) return
        val time = RebootPrefs.getTime(ctx) ?: return

        if (Build.VERSION.SDK_INT >= 31) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) return
        }

        if (!isA11yEnabled(ctx, ComponentName(ctx, A11yRebooterService::class.java))) return
        RebootScheduler.scheduleDailyExact(ctx, time.first, time.second)
    }

    /** User turned feature ON and picked time. */
    fun enableScheduling(ctx: Context, hour: Int, minute: Int) {
        RebootPrefs.setEnabled(ctx, true)
        RebootPrefs.setTime(ctx, hour, minute)
        onAppOpen(ctx)
    }

    /** User turned feature OFF. */
    fun disableScheduling(ctx: Context) {
        RebootPrefs.setEnabled(ctx, false)
        RebootScheduler.cancel(ctx)
    }

    private fun isA11yEnabled(ctx: Context, cn: ComponentName): Boolean {
        val enabled = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val target = cn.flattenToString()
        return list.split(':').any { it.equals(target, ignoreCase = true) }
    }
}

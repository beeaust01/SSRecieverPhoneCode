package de.austin_bee.reboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires at scheduled time and asks the AccessibilityService to reboot.
 * If service isn't enabled/running, this is a no-op.
 */
class RebootAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        A11yRebooterService.instance?.rebootNow()
        // INTENTIONALLY no auto-reschedule here; you reschedule on app open.
    }
}

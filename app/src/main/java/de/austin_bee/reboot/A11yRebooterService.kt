package de.austin_bee.reboot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper

/**
 * Opens the power dialog and clicks "Restart"/"Reboot".
 * No confirmations handled by design (your device has none).
 */
class A11yRebooterService : AccessibilityService() {

    private val ui = Handler(Looper.getMainLooper())
    private val labels = listOf(
        "Restart","RESTART","Reboot",   // EN
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Call to trigger a real reboot via the power menu. */
    fun rebootNow() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        tryClickRestart(8, 200L)
    }

    private fun tryClickRestart(retries: Int, intervalMs: Long) {
        val first = clickRestart()               // attempt #1

        // Always try a second tap shortly after to catch late-rendered dialogs
        ui.postDelayed({ clickRestart() }, 1000)  // ~300ms gap is usually enough

        if (first) return                        // we queued the 2nd tap already
        if (retries <= 0) return
        ui.postDelayed({
            tryClickRestart(retries - 1, intervalMs)
        }, intervalMs)
    }

    private fun clickRestart(): Boolean {
        val root = rootInActiveWindow ?: return false
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val visible = nodes.firstOrNull { it.isVisibleToUser } ?: continue
            val target = if (visible.isClickable) visible else visible.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    companion object {
        @Volatile var instance: A11yRebooterService? = null
    }

    override fun onServiceConnected() { instance = this; super.onServiceConnected() }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null; return super.onUnbind(intent)
    }
}

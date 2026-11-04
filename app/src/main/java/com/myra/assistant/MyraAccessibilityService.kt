package com.myra.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lets MYRA (1) take screenshots of whatever is on screen, (2) tap "Send" in WhatsApp,
 * (3) press Home. Must be enabled by the user in Settings -> Accessibility.
 */
class MyraAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: MyraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun screenshot(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY, mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        r.hardwareBuffer.close()
                        cb(bmp)
                    }
                    override fun onFailure(errorCode: Int) { cb(null) }
                }
            )
        } catch (e: Exception) { cb(null) }
    }

    /** Finds a "Send" button in the current window (WhatsApp etc.) and taps it. */
    fun clickSend(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = find(root) {
            val d = it.contentDescription?.toString()?.trim()?.lowercase()
            val t = it.text?.toString()?.trim()?.lowercase()
            d == "send" || t == "send"
        } ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private fun find(n: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (pred(n)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            find(c, pred)?.let { return it }
        }
        return null
    }
}

package pl.killaseo.macroclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import pl.killaseo.macroclicker.engine.MacroEngine

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MacroAccessibilityService? = null
            private set

        const val ACTION_RUN_MACRO = "pl.killaseo.macroclicker.RUN_MACRO"
        const val EXTRA_MACRO_ID   = "macro_id"
        const val EXTRA_PROJECT_ID = "project_id"
    }

    // Engine dostępny dla innych klas w module (np. ShortcutRunnerActivity)
    internal val engine: MacroEngine by lazy { MacroEngine(this) }

    // ── Cykl życia serwisu ─────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        engine.stop()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        engine.stop()
    }

    // ── Odbiór zdarzeń UI ──────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        engine.onEvent(event)
    }

    // ── Odbiór Intentów (ze skrótów / widgetów) ───────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUN_MACRO) {
            val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
            val macroId   = intent.getStringExtra(EXTRA_MACRO_ID)   ?: return START_NOT_STICKY
            engine.runMacro(projectId, macroId)
        }
        return START_NOT_STICKY
    }

    /** Publiczne API — odpalenie makra z zewnątrz (np. ShortcutRunnerActivity) */
    fun runMacroById(projectId: String, macroId: String) {
        engine.runMacro(projectId, macroId)
    }

    fun stopMacro() {
        engine.stop()
    }

    // ── Akcje na elementach UI ─────────────────────────────────────

    fun clickNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun longClickNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

    fun scrollNode(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val action = if (forward)
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return node.performAction(action)
    }

    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ── Gesty ──────────────────────────────────────────────────────

    fun tapAt(x: Float, y: Float, durationMs: Long = 50) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ── Znajdowanie węzłów UI ──────────────────────────────────────

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()
    }

    // ── Dump wszystkich tekstów z ekranu ──────────────────────────

    fun dumpAllText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        traverseNode(root, sb)
        return sb.toString().trim()
    }

    private fun traverseNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
        node.contentDescription?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, sb)
                // recycle() jest deprecated od API 33 — system zarządza pamięcią sam
            }
        }
    }

    // ── Globalne akcje systemowe ───────────────────────────────────

    fun pressBack()   = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome()   = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
}

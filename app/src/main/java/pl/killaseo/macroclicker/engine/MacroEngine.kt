package pl.killaseo.macroclicker.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.killaseo.macroclicker.model.Macro
import pl.killaseo.macroclicker.model.MacroStep
import pl.killaseo.macroclicker.model.NodeTarget
import pl.killaseo.macroclicker.model.RangeMarker
import pl.killaseo.macroclicker.model.StepCondition
import pl.killaseo.macroclicker.model.StepType
import pl.killaseo.macroclicker.service.MacroAccessibilityService
import pl.killaseo.macroclicker.storage.ClipboardStorage
import pl.killaseo.macroclicker.storage.ProjectStorage

class MacroEngine(private val service: MacroAccessibilityService) {

    private val scope             = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val storage           = ProjectStorage(service)
    internal val clipboardStorage = ClipboardStorage(service)

    // Stan wykonania
    private var activeProjectId = ""
    private var activeMacroId   = ""
    private var running         = false
    private val variables       = mutableMapOf<String, String>()

    // Stos wywołań — zabezpieczenie przed nieskończoną rekurencją CALL_MACRO
    private val callStack       = mutableListOf<String>()
    private val maxCallDepth    = 8

    // ── Publiczne API ──────────────────────────────────────────────

    fun runMacro(projectId: String, macroId: String) {
        val project = storage.loadProject(projectId) ?: return
        val macro   = project.macros.find { it.id == macroId } ?: return
        activeProjectId = projectId
        activeMacroId   = macroId
        clipboardStorage.autoCleanup(projectId)
        runMacro(macro)
    }

    fun runMacro(macro: Macro) {
        if (running) return
        running = true
        variables.clear()
        callStack.clear()
        callStack.add(macro.id)
        scope.launch {
            try {
                executeSteps(macro.steps)
            } finally {
                running = false
                callStack.clear()
            }
        }
    }

    fun stop() {
        running = false
        scope.coroutineContext.cancelChildren()
    }

    fun onEvent(event: AccessibilityEvent) {
        // Hook dla przyszłych funkcji - reagowanie na zdarzenia podczas działania makra
    }

    // ── Wykonywanie kroków ─────────────────────────────────────────

    private suspend fun executeSteps(steps: List<MacroStep>) {
        for (step in steps) {
            if (!running) break
            executeStep(step)
            if (step.delayAfterMs > 0) delay(step.delayAfterMs)
        }
    }

    private suspend fun executeStep(step: MacroStep) {
        when (step.type) {

            StepType.LAUNCH_APP -> {
                val pkg = step.packageName ?: return
                val intent = service.packageManager
                    .getLaunchIntentForPackage(pkg)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                intent?.let { service.startActivity(it) }
                delay(1500)
            }

            StepType.CLICK_NODE -> {
                val target = step.nodeTarget ?: return
                findNode(target)?.let { service.clickNode(it) }
            }

            StepType.CLICK_XY -> {
                service.tapAt(step.x, step.y)
            }

            StepType.SCROLL -> {
                val (dx, dy) = scrollVector(step.scrollDirection, step.scrollDistance)
                val cx = service.resources.displayMetrics.widthPixels / 2f
                val cy = service.resources.displayMetrics.heightPixels / 2f
                service.swipe(cx, cy, cx + dx, cy + dy, step.scrollDurationMs)
                delay(step.scrollDurationMs + 100)
            }

            StepType.SWIPE -> {
                service.swipe(step.x, step.y, step.x2, step.y2, step.swipeDurationMs)
                delay(step.swipeDurationMs + 100)
            }

            StepType.DUMP_ALL -> {
                val text = service.dumpAllText()
                val varName = step.outputVariable ?: "dump_all"
                clipboardStorage.save(
                    activeProjectId, varName, text,
                    macroId = activeMacroId, stepId = step.id
                )
                variables[varName] = text
                if (text.length < CLIPBOARD_SOFT_LIMIT) copyToClipboard(text)
            }

            StepType.RANGE_COPY -> {
                val result = executeRangeCopy(step)
                if (result != null) {
                    val varName = step.outputVariable ?: "range_copy"
                    clipboardStorage.save(
                        activeProjectId, varName, result,
                        macroId = activeMacroId, stepId = step.id
                    )
                    variables[varName] = result
                    if (result.length < CLIPBOARD_SOFT_LIMIT) copyToClipboard(result)
                } else {
                    when (step.ifNotFound) {
                        "screenshot_ocr" -> executeScreenshotOcr(step)
                        "stop"           -> stop()
                        else             -> { /* skip */ }
                    }
                }
            }

            StepType.SCREENSHOT_OCR -> {
                executeScreenshotOcr(step)
            }

            StepType.PASTE -> {
                val text = resolveTextForPaste(step)
                pasteToFocusedField(text)
            }

            StepType.TYPE_TEXT -> {
                val text = resolveTextForType(step) ?: return
                pasteToFocusedField(text)
            }

            StepType.WAIT -> {
                if (step.waitForElement != null) {
                    waitForElement(step.waitForElement, step.waitTimeoutMs)
                } else {
                    delay(step.waitMs)
                }
            }

            StepType.IF_ELSE -> {
                val condition = step.condition ?: return
                if (evaluateCondition(condition)) {
                    executeSteps(step.thenSteps)
                } else {
                    executeSteps(step.elseSteps)
                }
            }

            StepType.CALL_MACRO -> {
                val targetId = step.targetMacroId ?: return
                // Zabezpieczenie przed nieskończoną rekurencją
                if (callStack.size >= maxCallDepth) return
                if (callStack.contains(targetId)) return  // bezpośrednia rekurencja

                storage.findMacroById(targetId)?.let { macro ->
                    callStack.add(targetId)
                    try {
                        executeSteps(macro.steps)
                    } finally {
                        callStack.removeAt(callStack.size - 1)
                    }
                }
            }

            StepType.REPEAT -> {
                repeat(step.repeatCount) {
                    if (running) executeSteps(step.repeatSteps)
                }
            }

            StepType.STOP -> stop()

            StepType.OPEN_FILE -> {
                val path = step.filePath ?: return
                val uri = android.net.Uri.parse(path)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, step.fileMimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    service.startActivity(intent)
                    delay(2000)
                } catch (e: Exception) {
                    // Brak apki obsługującej ten typ — pomiń
                }
            }
        }
    }

    // ── Wyznaczenie tekstu dla PASTE/TYPE_TEXT ────────────────────

    private fun resolveTextForPaste(step: MacroStep): String {
        // 1. Spróbuj z RAM (zmienna)
        step.outputVariable?.let { varName ->
            variables[varName]?.let { return it }
            // 2. Z SQLite (długie teksty)
            clipboardStorage.load(activeProjectId, varName)?.let { return it }
        }
        // 3. Schowek systemowy jako fallback
        return getClipboardText()
    }

    private fun resolveTextForType(step: MacroStep): String? {
        if (step.text != null) return step.text
        step.outputVariable?.let { varName ->
            variables[varName]?.let { return it }
            clipboardStorage.load(activeProjectId, varName)?.let { return it }
        }
        return null
    }

    private fun pasteToFocusedField(text: String) {
        val root = service.rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focused?.let { service.setNodeText(it, text) }
    }

    // ── Range Copy ─────────────────────────────────────────────────

    private fun executeRangeCopy(step: MacroStep): String? {
        val start = step.startMarker ?: return service.dumpAllText()
        val stop  = step.stopMarker
        val fullText = service.dumpAllText()

        val startIdx = findMarkerIndex(fullText, start)
        if (startIdx < 0) return null

        val endIdx = if (stop != null) {
            findMarkerIndex(fullText, stop, startIdx + start.value.length)
        } else {
            fullText.length
        }
        if (endIdx < 0 || endIdx < startIdx) return null

        return fullText.substring(startIdx, endIdx).trim()
    }

    private fun findMarkerIndex(text: String, marker: RangeMarker, from: Int = 0): Int {
        return when (marker.type) {
            "regex" -> {
                try {
                    Regex(marker.value).find(text, from)?.range?.first ?: -1
                } catch (e: Exception) { -1 }
            }
            else -> text.indexOf(marker.value, from)
        }
    }

    // ── OCR (placeholder — wymaga ML Kit) ─────────────────────────

    private fun executeScreenshotOcr(step: MacroStep) {
        // TODO: implementacja z ML Kit Text Recognition
        // Na razie dump tekstu przez accessibility jako fallback
        val text = service.dumpAllText()
        val varName = step.outputVariable ?: "ocr_result"
        if (activeProjectId.isNotEmpty()) {
            clipboardStorage.save(
                activeProjectId, varName, text,
                macroId = activeMacroId, stepId = step.id
            )
        }
        variables[varName] = text
        if (text.length < CLIPBOARD_SOFT_LIMIT) copyToClipboard(text)
    }

    // ── Czekanie na element ────────────────────────────────────────

    private suspend fun waitForElement(target: NodeTarget, timeoutMs: Long) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!running) return
            if (findNode(target) != null) return
            delay(200)
        }
    }

    // ── Warunek IF/ELSE ────────────────────────────────────────────

    private fun evaluateCondition(condition: StepCondition): Boolean {
        return when (condition.type) {
            "element_visible"   -> condition.target?.let { findNode(it) != null } ?: false
            "text_equals"       -> {
                val varName = condition.variable ?: return false
                variables[varName] == condition.value
            }
            "variable_contains" -> {
                val varName = condition.variable ?: return false
                val needle  = condition.value ?: return false
                variables[varName]?.contains(needle) ?: false
            }
            else -> false
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun findNode(target: NodeTarget): AccessibilityNodeInfo? {
        return when {
            target.resourceId != null -> service.findNodeByResourceId(target.resourceId)
            target.text != null       -> service.findNodeByText(target.text)
            else -> null
        }
    }

    private fun scrollVector(direction: String, distance: Int): Pair<Float, Float> {
        return when (direction) {
            "up"    -> Pair(0f, distance.toFloat())
            "down"  -> Pair(0f, -distance.toFloat())
            "left"  -> Pair(distance.toFloat(), 0f)
            "right" -> Pair(-distance.toFloat(), 0f)
            else    -> Pair(0f, -distance.toFloat())
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MacroClicker", text))
    }

    private fun getClipboardText(): String {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    companion object {
        // Limit dla schowka systemowego — większe teksty tylko do SQLite
        private const val CLIPBOARD_SOFT_LIMIT = 65536
    }
}

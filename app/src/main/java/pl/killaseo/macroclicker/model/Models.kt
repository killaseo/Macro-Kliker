package pl.killaseo.macroclicker.model

// ── Typy kroków makra ──────────────────────────────────────────────

enum class StepType {
    LAUNCH_APP,
    CLICK_NODE,
    CLICK_XY,
    SCROLL,
    SWIPE,
    DUMP_ALL,
    RANGE_COPY,
    SCREENSHOT_OCR,
    OPEN_FILE,
    PASTE,
    TYPE_TEXT,
    WAIT,
    IF_ELSE,
    CALL_MACRO,
    REPEAT,
    STOP
}

// ── Cel kliknięcia (element UI) ────────────────────────────────────

data class NodeTarget(
    val text: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val contentDescription: String? = null,
    val index: Int = 0            // jeśli jest kilka pasujących elementów
)

// ── Znacznik zakresu (dla range_copy) ─────────────────────────────

data class RangeMarker(
    val type: String = "text",    // "text" | "regex" | "tag"
    val value: String = ""
)

// ── Pojedynczy krok makra ──────────────────────────────────────────

data class MacroStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: StepType,

    // LAUNCH_APP
    val packageName: String? = null,

    // CLICK_NODE / CLICK_XY
    val nodeTarget: NodeTarget? = null,
    val x: Float = 0f,
    val y: Float = 0f,

    // SWIPE — osobne pola dla pełnej kontroli
    val x2: Float = 0f,
    val y2: Float = 0f,
    val swipeDurationMs: Long = 300,

    // SCROLL
    val scrollDirection: String = "down",     // up | down | left | right
    val scrollDurationMs: Long = 1000,
    val scrollDistance: Int = 500,

    // RANGE_COPY
    val startMarker: RangeMarker? = null,
    val stopMarker: RangeMarker? = null,
    val ifNotFound: String = "screenshot_ocr",  // screenshot_ocr | skip | stop

    // OPEN_FILE
    val filePath: String? = null,
    val fileMimeType: String = "*/*",

    // TYPE_TEXT / PASTE / inne tekstowe
    val text: String? = null,

    // WAIT
    val waitMs: Long = 500,
    val waitForElement: NodeTarget? = null,
    val waitTimeoutMs: Long = 5000,

    // IF_ELSE
    val condition: StepCondition? = null,
    val thenSteps: List<MacroStep> = emptyList(),
    val elseSteps: List<MacroStep> = emptyList(),

    // CALL_MACRO
    val targetMacroId: String? = null,        // ← przemianowane (było macroId — myliło się z step.id)

    // REPEAT
    val repeatCount: Int = 1,
    val repeatSteps: List<MacroStep> = emptyList(),

    // Wspólne
    val delayAfterMs: Long = 0,
    val outputVariable: String? = null        // zmienna do zapisu wyniku
)

// ── Warunek (dla IF_ELSE) ──────────────────────────────────────────

data class StepCondition(
    val type: String,                         // element_visible | text_equals | variable_contains
    val target: NodeTarget? = null,
    val value: String? = null,
    val variable: String? = null
)

// ── Makro ──────────────────────────────────────────────────────────

data class Macro(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Nowe makro",
    val icon: String = "🔁",
    val steps: MutableList<MacroStep> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ── Projekt (zbiór makr) ───────────────────────────────────────────

data class Project(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Nowy projekt",
    val macros: MutableList<Macro> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

// ── Skrót ─────────────────────────────────────────────────────────

data class MacroShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val projectId: String,
    val macroId: String,
    val shortcutType: String = "launcher"     // launcher | widget | tile
)

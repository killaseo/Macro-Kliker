package pl.killaseo.macroclicker.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import pl.killaseo.macroclicker.service.MacroAccessibilityService

/**
 * Activity bez UI — odbiera Intent ze skrótu pulpitu / widgetu / kafelka QS
 * i przekazuje go do AccessibilityService. Natychmiast się kończy.
 */
class ShortcutRunnerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectId = intent.getStringExtra(MacroAccessibilityService.EXTRA_PROJECT_ID)
        val macroId   = intent.getStringExtra(MacroAccessibilityService.EXTRA_MACRO_ID)

        if (projectId == null || macroId == null) {
            finish()
            return
        }

        val svc = MacroAccessibilityService.instance
        if (svc != null) {
            // Serwis aktywny — odpal makro przez publiczne API
            svc.runMacroById(projectId, macroId)
        } else {
            // Serwis nieaktywny — poinformuj użytkownika
            Toast.makeText(
                this,
                "Włącz MacroClicker w Ustawienia → Dostępność",
                Toast.LENGTH_LONG
            ).show()
        }

        finish()
    }
}

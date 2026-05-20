package pl.killaseo.macroclicker.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import pl.killaseo.macroclicker.R
import pl.killaseo.macroclicker.model.MacroShortcut
import pl.killaseo.macroclicker.service.MacroAccessibilityService

object MacroShortcutManager {

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun createLauncherShortcut(context: Context, shortcut: MacroShortcut) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return

        val intent = Intent(context, ShortcutRunnerActivity::class.java).apply {
            action = MacroAccessibilityService.ACTION_RUN_MACRO
            putExtra(MacroAccessibilityService.EXTRA_PROJECT_ID, shortcut.projectId)
            putExtra(MacroAccessibilityService.EXTRA_MACRO_ID, shortcut.macroId)
        }

        val info = ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setLongLabel(shortcut.label)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
            .setIntent(intent)
            .build()

        // Dodaj do listy dynamicznych skrótów
        val existing = manager.dynamicShortcuts.toMutableList()
        existing.removeAll { it.id == shortcut.id }
        existing.add(info)

        // Android pozwala max 5 skrótów dynamicznych
        manager.dynamicShortcuts = existing.take(5)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun pinShortcut(context: Context, shortcut: MacroShortcut) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (!manager.isRequestPinShortcutSupported) return

        val intent = Intent(context, ShortcutRunnerActivity::class.java).apply {
            action = MacroAccessibilityService.ACTION_RUN_MACRO
            putExtra(MacroAccessibilityService.EXTRA_PROJECT_ID, shortcut.projectId)
            putExtra(MacroAccessibilityService.EXTRA_MACRO_ID, shortcut.macroId)
        }

        val info = ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.label)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
            .setIntent(intent)
            .build()

        // Wyświetla dialog "Dodaj do ekranu głównego"
        manager.requestPinShortcut(info, null)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun removeShortcut(context: Context, shortcutId: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        manager.removeDynamicShortcuts(listOf(shortcutId))
    }
}

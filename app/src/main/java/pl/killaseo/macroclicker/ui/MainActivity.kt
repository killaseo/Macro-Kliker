package pl.killaseo.macroclicker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import pl.killaseo.macroclicker.R
import pl.killaseo.macroclicker.service.MacroAccessibilityService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    // ── Sprawdzenie uprawnień ──────────────────────────────────────

    private fun checkPermissions() {
        // 1. Overlay (wyświetlanie nad innymi apkami)
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }

        // 2. Accessibility Service — użytkownik musi włączyć ręcznie
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this,
            "Wymagane: zezwól na wyświetlanie nad innymi apkami",
            Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY)
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this,
            "Włącz MacroClicker Service w Ustawienia → Dostępność",
            Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return MacroAccessibilityService.instance != null
    }

    private fun updateServiceStatus() {
        // TODO: zaktualizować UI — ikona zielona/czerwona w zależności od statusu serwisu
    }

    companion object {
        const val REQUEST_OVERLAY = 1001
    }
}

package pl.killaseo.macroclicker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Accessibility service uruchamia się automatycznie przez system
            // jeśli użytkownik go wcześniej włączył w Ustawienia → Dostępność
            // Tu możemy np. pokazać powiadomienie przypominające o włączeniu
        }
    }
}

# MacroClicker

Aplikacja Android do automatyzacji akcji na ekranie — klikanie elementów UI,
scrollowanie, kopiowanie tekstu, uruchamianie aplikacji, zapisywanie makr
jako skróty na pulpicie.

## Wymagania

- Android 8.0+ (API 26)
- Włączony AccessibilityService w Ustawienia → Dostępność
- Uprawnienie do wyświetlania nad innymi apkami

## Budowanie

```bash
./gradlew assembleDebug
```

APK trafia do `app/build/outputs/apk/debug/`

Lub w AFS: Code Mode → Build from GitHub → wybierz repo.

## Architektura

```
service/
  MacroAccessibilityService.kt  ← rdzeń: akcje UI, gesty, drzewo widoków
  BootReceiver.kt               ← autostart

engine/
  MacroEngine.kt                ← executor kroków makra

model/
  Models.kt                     ← data classes: Macro, Step, Project

storage/
  ProjectStorage.kt             ← zapis/odczyt JSON projektów
  ClipboardStorage.kt           ← SQLite buffer z chunkingiem

shortcut/
  ShortcutRunnerActivity.kt     ← uruchamianie makra ze skrótu (bez UI)
  MacroShortcutManager.kt       ← tworzenie skrótów pulpitu

ui/
  MainActivity.kt               ← główne UI
```

## Typy kroków makra

| Typ              | Opis                                          |
|------------------|-----------------------------------------------|
| `LAUNCH_APP`     | Uruchom aplikację po package name             |
| `CLICK_NODE`     | Kliknij element UI (po tekście/resource-id)   |
| `CLICK_XY`       | Kliknij współrzędne X,Y                       |
| `SCROLL`         | Scroll w kierunku (up/down/left/right)        |
| `SWIPE`          | Swipe od (x,y) do (x2,y2)                     |
| `DUMP_ALL`       | Zbierz cały tekst z ekranu                    |
| `RANGE_COPY`     | Kopiuj między znacznikami start/stop          |
| `SCREENSHOT_OCR` | OCR fallback (ML Kit)                         |
| `OPEN_FILE`      | Otwórz plik przez Intent                      |
| `PASTE`          | Wklej tekst do aktywnego pola                 |
| `TYPE_TEXT`      | Wpisz tekst literalny lub ze zmiennej         |
| `WAIT`           | Czekaj ms lub na element                      |
| `IF_ELSE`        | Warunkowe wykonanie kroków                    |
| `CALL_MACRO`     | Wywołaj inne makro (zabezp. przed rekurencją) |
| `REPEAT`         | Powtórz N razy listę kroków                   |
| `STOP`           | Zatrzymaj makro                               |

## Skróty

- Ikona na pulpicie (ShortcutManager)
- Kafelek Quick Settings (TileService) — TODO
- Widget (AppWidgetProvider) — TODO

# 🐍 Python Runner (Android)

Eine leistungsstarke, native Android-Anwendung zum **Schreiben, Ausführen und Verwalten von Python-Skripten** mit interaktivem **Terminal (REPL)** und einem eingebetteten **Localhost-Webserver (`http://127.0.0.1:8080`)** zur Anzeige dynamischer Web-UIs.

---

## 🌟 Hauptfunktionen

### 1. 💻 Python Code Editor
- **Syntax Highlighting**: Automatische Farbcodierung von Python-Schlüsselwörtern, Built-ins, Strings, Zahlen und Kommentaren.
- **Zeilennummern & Monospace-Ansicht**: Übersichtlicher Editor für mobile Bildschirme.
- **Schnellzugriffsleiste**: Shortcuts für Einrückungen (`TAB`), Sonderzeichen (`:`, `()`, `[]`, `{}`) und Snippets (`def`, `import`, `print(`, `web`).
- **Speichern & Organisieren**: Speichere eigene Skripte mit Titeln und Kategorien in einer lokalen Room-Datenbank.

### 2. 📟 Interaktives Terminal & Live-REPL
- **Farbcodierte CLI-Ausgabe**: Grüne Standardausgabe (`stdout`), rote Fehlermeldungen (`stderr` mit Traceback) und Systemmeldungen.
- **Interaktives `input()`**: Wenn ein Python-Skript `input()` aufruft, öffnet sich ein hervorgehobener Eingabedialog mit Tastaturunterstützung.
- **Integrierte Python-Shell (`>>>`)**: Führe beliebige Python-Ausdrücke oder Befehle direkt im Terminal aus (z. B. `2 + 2`, `math.sqrt(16)`).
- **Terminal-Werkzeuge**: Text kopieren, Terminal leeren, Suchfunktion und anpassbare Schriftgröße.

### 3. 🌐 Localhost Web-Server & In-App Web-UI
- **Eingebetteter HTTP-Server**: Startet einen lokalen Multi-Threaded Server auf `http://127.0.0.1:8080`.
- **`web`-Modul**:
  ```python
  import web
  html = "<h1>Hallo von Python!</h1>"
  web.serve_html(html, port=8080)
  ```
- **In-App WebView & Browser-Sync**: Betrachte die vom Python-Skript generierte HTML5/CSS/JavaScript-Oberfläche direkt im App-Tab *Web UI* oder öffne sie im Standard-Webbrowser deines Smartphones.
- **Live HTTP-Logs**: Echtzeit-Protokollierung von HTTP-Anfragen (Methoden, Routen, Statuscodes).

### 4. 📚 Skript-Bibliothek & Vorlagen
- Vorinstallierte, sofort ausführbare Skripte:
  - 🚀 **Flask-Style Localhost Web Dashboard** (Interaktiver Counter mit CSS/JS)
  - 🎨 **HTML5 Canvas Partikel-Animation** (Visuelle Simulation im Web UI)
  - 🎮 **Interaktives Text-Adventure** (Rollenspiel mit `input()` Entscheidungen)
  - 🧮 **Mathematik & Primzahl-Generator** (Fibonacci, Benchmarking)
  - 📊 **Statistik & ASCII-Balkendiagramme** im Terminal
  - 📝 **Virtuelles Dateisystem & JSON-Verarbeitung** (`open()`, `json.dumps()`)
- **Kategorien & Favoriten**: Filter nach *Web UI*, *Terminal*, *Games*, *Data & Math*, *System*.

---

## 🛠️ Unterstützte Python-Sprachfeatures

- **Datentypen**: `int`, `float`, `str`, `bool`, `list`, `dict`, `set`, `tuple`, `None`
- **Kontrollstrukturen**: `if` / `elif` / `else`, `while`, `for ... in`, `try` / `except` / `finally`, `raise`, `assert`
- **Funktionen & OOP**: `def`, Standardparameter, `*args`, `lambda`, `class`, Vererbung, `__init__`, `self`
- **Comprehensions**: List Comprehensions (`[x*2 for x in items if x > 0]`), Dict Comprehensions
- **String-Operationen**: Slicing (`s[1:5]`, `s[::-1]`), F-Strings (`f"Ergebnis: {x}"`), Formatierung (`%s`, `%d`), Methoden (`.split()`, `.join()`, `.replace()`, `.upper()`, etc.)
- **Integrierte Module**:
  - `web` (`serve_html()`, `route()`, `start()`, `stop()`)
  - `math` (`sqrt()`, `sin()`, `cos()`, `pi()`, `e`, `pow()`, `factorial()`, etc.)
  - `time` (`time()`, `sleep()`)
  - `random` (`randint()`, `choice()`, `random()`, `shuffle()`)
  - `json` (`dumps()`, `loads()`)
  - `datetime` (`now()`, `strftime()`)
  - `os` & `sys` (`listdir()`, `remove()`, `version`, `argv`)
  - Virtuelles Dateisystem mit `open(file, mode)` (`read()`, `write()`, `close()`)

---

## 🏗️ Technische Architektur

| Komponente | Technologie / Bibliothek |
|---|---|
| **Programmiersprache** | Kotlin 2.0+ |
| **UI-Framework** | Jetpack Compose mit Material Design 3 (M3) |
| **Architektur** | MVVM (Model-View-ViewModel) mit StateFlow & Coroutines |
| **Lokale Datenbank** | Room Database mit KSP |
| **Python-Engine** | Eigener Lexer, Parser & AST-Interpreter (Kotlin) |
| **Webserver** | Multi-Threaded Java ServerSocket HTTP Engine |
| **Web-Anzeige** | Android WebKit WebView mit JavaScript-Unterstützung |
| **Testing** | Robolectric & Roborazzi Screenshot Tests |

---

## 🚀 Bauen und Ausführen

### Voraussetzungen
- Android Studio Ladybug (oder neuer)
- Android SDK 34 / 35 (Mindestversion: Android 8.0 / API 26)
- JDK 17 oder JDK 21

### Installation
1. Repository klonen:
   ```bash
   git clone https://github.com/dein-nutzername/python-runner-android.git
   cd python-runner-android
   ```
2. Projekt in Android Studio öffnen.
3. Gradle Sync durchführen.
4. Auf einem Android-Gerät oder Emulator starten:
   ```bash
   gradle assembleDebug
   ```

### Tests ausführen
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 Lizenz
Dieses Projekt ist unter der MIT-Lizenz lizenziert.

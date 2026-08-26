# 🐍 Python Runner (Android)

Eine leistungsstarke Android-Anwendung zum **Schreiben, Ausführen und Verwalten von Python-Skripten** mit vollständiger **CPython 3.11 Runtime (Pyodide / WebAssembly)**, **Pip-Paketverwaltung (`micropip`)**, interaktivem **ANSI-Terminal (REPL)**, **nativer Socket-/Netzwerk-Bridge** und einem eingebetteten **Localhost-Webserver (`http://127.0.0.1:8080`)** zur Anzeige dynamischer Web-UIs.

---

## 🌟 Hauptfunktionen

### 1. 🐍 Vollständige CPython 3.11 Engine (Pyodide WebAssembly)
- **Echtes CPython 3.11**: Unterstützung für komplexe Python-Sprachstrukturen, Standardbibliotheken, Generatoren, Dekoratoren, Async/Await, Typhinweise und vollständiges OOP.
- **Pip Paket-Manager (`micropip`)**: Installiere und verwalte Python-Pakete aus PyPI direkt auf dem Smartphone (z. B. `pysolarmanv5`, `requests`, `numpy`, `pandas`, `sympy`, `beautifulsoup4`, `tabulate` u. v. m.).
- **Native Android Network Socket Bridge**: Echte TCP/UDP Socket-Kommunikation für Hardware- und IoT-Integrationen (z. B. Deye/Solarman Wechselrichter, Modbus, MQTT, HTTP).

### 2. 💻 Python Code Editor
- **Syntax Highlighting**: Automatische Farbcodierung von Python-Schlüsselwörtern, Built-ins, Strings, Zahlen und Kommentaren.
- **Zeilennummern & Monospace-Ansicht**: Ergonomischer Editor für mobile Bildschirme.
- **Schnellzugriffsleiste**: Shortcuts für Einrückungen (`TAB`), Sonderzeichen (`:`, `()`, `[]`, `{}`) und Code-Snippets (`def`, `import`, `print(`, `requests`).
- **Skriptverwaltung**: Speichere, bearbeite und kategorisiere Skripte in einer lokalen Room-Datenbank.

### 3. 📟 Interaktives ANSI-Terminal & Live-REPL
- **Echte ANSI-Farbunterstützung**: Formatierte Ausgaben mit Farb- und Textattributen (Grün, Rot, Blau, Gelb, Fett etc.) werden sauber im Terminal gerendert.
- **Interaktive Python-Shell (`>>>`)**: Führe beliebige Python-Befehle oder Ausdrücke direkt im Terminal aus.
- **Terminal-Tools**: Ein-Klick-Kopieren, Löschen, Suchfilter und anpassbare Schriftgrößen.

### 4. 🌐 Localhost Web-Server & In-App Web-UI
- **Eingebetteter HTTP-Server**: Starte lokale Webserver auf `http://127.0.0.1:8080` (mit `http.server`, `socketserver` oder benutzerdefinierten Handlern).
- **In-App WebView**: Betrachte generierte HTML5/CSS/JavaScript-Dashboards direkt im App-Tab *Web UI* oder im Standardbrowser.
- **Live HTTP-Logs**: Echtzeit-Protokollierung von HTTP-Anfragen.

### 5. 📚 Skript-Bibliothek & Vorlagen
- Vorinstallierte, sofort einsatzbereite Vorlagen:
  - ☀️ **Deye / Solarman Inverter Modbus Reader** (Hardware-Monitoring via Sockets)
  - 🚀 **Flask-Style Localhost Web Dashboard** (Interaktiver Counter mit CSS/JS)
  - 🎨 **HTML5 Canvas Partikel-Animation** (Visuelle Simulation im Web UI)
  - 🧮 **Mathematik & Data Science** (`numpy`, Matrizen, Statistik)
  - 🌐 **HTTP & REST-API Abfragen** (`requests`, `json`)
  - 📊 **ASCII-Diagramme & Konsolenformatierung**

---

## 🏗️ Technische Architektur

| Komponente | Technologie / Bibliothek |
|---|---|
| **Programmiersprache** | Kotlin 2.0+ & Python 3.11 |
| **UI-Framework** | Jetpack Compose mit Material Design 3 (M3) |
| **Architektur** | MVVM (Model-View-ViewModel) mit StateFlow & Coroutines |
| **Python Engine** | Pyodide (CPython 3.11 WebAssembly Runtime) |
| **Paketverwaltung** | `micropip` mit PyPI-Repository-Anbindung |
| **Netzwerk-Bridge** | Bidirektionale Android Java Socket Bridge (TCP/UDP) |
| **Lokale Datenbank** | Room Database mit KSP |
| **Web-Anzeige** | Android WebKit WebView mit JavaScript-Interface |
| **Terminal-Formatierung** | Eigener ANSI Escape Code Parser & Spannable Formatter |
| **Testing** | Robolectric Unit- & UI-Tests |

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

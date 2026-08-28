# 🐍 Python Runner (Android)

[![Latest Release](https://img.shields.io/github/v/release/juergen874/PyWeb-Script-Runner?style=for-the-badge&logo=github&color=blue)](https://github.com/juergen874/PyWeb-Script-Runner/releases/latest)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.0)-success?style=for-the-badge&logo=android&logoColor=white)](https://github.com/juergen874/PyWeb-Script-Runner/releases/download/v1.0.0/Python.Runner.1.0.apk)
[![Total Downloads](https://img.shields.io/github/downloads/juergen874/PyWeb-Script-Runner/total?style=for-the-badge&logo=github&color=orange)](https://github.com/juergen874/PyWeb-Script-Runner/releases)
[![Python 3.11](https://img.shields.io/badge/Python-3.11%20(Pyodide)-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://pyodide.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

Eine leistungsstarke Android-Anwendung zum **Schreiben, Ausführen und Verwalten von Python-Skripten** mit vollständiger **CPython 3.11 Runtime (Pyodide / WebAssembly)**, **Pip-Paketverwaltung (`micropip`)**, interaktivem **ANSI-Terminal (REPL)**, **nativer Socket-/Netzwerk-Bridge** und einem eingebetteten **Localhost-Webserver (`http://127.0.0.1:8080`)** zur Anzeige dynamischer Web-UIs.

---

## 📥 Direkter Download & APK-Installation

Du musst das Projekt nicht selbst kompilieren – die fertige Android-App steht als **APK-Download** bereit:

* ⬇️ **[Direkter Download: Python.Runner.1.0.apk (v1.0.0)](https://github.com/juergen874/PyWeb-Script-Runner/releases/download/v1.0.0/Python.Runner.1.0.apk)** *(ca. 22 MB)*
* 📦 **[Alle Versionen & Release Notes ansehen](https://github.com/juergen874/PyWeb-Script-Runner/releases/latest)**

> **Hinweis zur Installation:** Nach dem Herunterladen die APK auf dem Android-Gerät antippen. Falls gefragt, die Installation aus deinem Browser/Dateimanager erlauben (*„Unbekannte Apps installieren“*).

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

## 🚀 Bauen und Ausführen (Für Entwickler)

### Voraussetzungen
- Android Studio Ladybug (oder neuer)
- Android SDK 34 / 35 (Mindestversion: Android 8.0 / API 26)
- JDK 17 oder JDK 21

### Installation
1. Repository klonen:
   ```bash
   git clone https://github.com/juergen874/PyWeb-Script-Runner.git
   cd PyWeb-Script-Runner
   ```
2. Projekt in Android Studio öffnen.
3. Gradle Sync durchführen.
4. Auf einem Android-Gerät oder Emulator starten:
   ```bash
   ./gradlew assembleDebug
   ```

### Tests ausführen
```bash
./gradlew :app:testDebugUnitTest
```

---

## 📄 Lizenz
Dieses Projekt ist unter der [MIT-Lizenz](LICENSE) lizenziert.

package com.example.data

import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val scriptDao: ScriptDao) {

    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    suspend fun insert(script: ScriptEntity): Long = scriptDao.insertScript(script)

    suspend fun update(script: ScriptEntity) = scriptDao.updateScript(script)

    suspend fun delete(script: ScriptEntity) = scriptDao.deleteScript(script)

    suspend fun deleteById(id: Long) = scriptDao.deleteById(id)

    suspend fun ensureDefaultTemplates() {
        if (scriptDao.getCount() == 0) {
            scriptDao.insertAll(getDefaultTemplates())
        }
    }

    companion object {
        fun getDefaultTemplates(): List<ScriptEntity> {
            val tq = "\"\"\""

            val flaskCode = """
# ==========================================
# 🚀 Python Flask-Style Localhost Web Server
# ==========================================
import web
import time
import datetime

print("🌐 Starte Localhost Web-Server...")

html_page = $tq<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Python Web Dashboard</title>
    <style>
        * { margin:0; padding:0; box-sizing:border-box; font-family: -apple-system, sans-serif; }
        body { background: #0b0f19; color: #f1f5f9; padding: 24px; }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
        .logo { font-size: 20px; font-weight: 700; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
        .badge { background: rgba(34, 197, 94, 0.2); color: #4ade80; padding: 4px 12px; border-radius: 20px; font-size: 13px; font-weight: 600; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; margin-bottom: 24px; }
        .card { background: #1e293b; border: 1px solid #334155; border-radius: 14px; padding: 20px; }
        .card h3 { font-size: 14px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
        .card .value { font-size: 28px; font-weight: 700; color: #fff; }
        .interactive-box { background: #1e293b; border: 1px solid #38bdf8; border-radius: 14px; padding: 24px; text-align: center; }
        button { background: #0284c7; color: white; border: none; padding: 12px 24px; font-size: 16px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: 0.2s; }
        button:hover { background: #0369a1; }
        .counter-display { font-size: 48px; font-weight: 800; color: #38bdf8; margin: 16px 0; }
    </style>
</head>
<body>
    <div class="header">
        <div class="logo">🐍 Python Localhost Web-App</div>
        <div class="badge">● Online: 127.0.0.1:8080</div>
    </div>
    
    <div class="grid">
        <div class="card">
            <h3>Server Status</h3>
            <div class="value" style="color: #4ade80;">Aktiv (200 OK)</div>
        </div>
        <div class="card">
            <h3>Engine</h3>
            <div class="value">Python 3.11</div>
        </div>
        <div class="card">
            <h3>Host</h3>
            <div class="value">Localhost:8080</div>
        </div>
    </div>

    <div class="interactive-box">
        <h2>Interaktiver JavaScript & Python Counter</h2>
        <p style="color:#94a3b8; margin-top:6px;">Diese Seite wird live vom eingebetteten Python-Server ausgeliefert.</p>
        <div class="counter-display" id="count">0</div>
        <div style="display:flex; gap:12px; justify-content:center;">
            <button onclick="changeCount(-1)">- Verringern</button>
            <button onclick="changeCount(1)">+ Erhöhen</button>
            <button onclick="resetCount()" style="background:#475569;">Reset</button>
        </div>
    </div>

    <script>
        let count = 0;
        function changeCount(delta) {
            count += delta;
            document.getElementById('count').innerText = count;
        }
        function resetCount() {
            count = 0;
            document.getElementById('count').innerText = count;
        }
    </script>
</body>
</html>$tq

# HTML auf localhost:8080 bereitstellen
url = web.serve_html(html_page, port=8080)
print(f"✅ Web-UI erfolgreich gestartet auf {url}")
print("Öffne den Tab 'Web UI' um die Live-Seite zu sehen!")
""".trimIndent()

            val canvasCode = """
# ==========================================
# 🎨 HTML5 Canvas Partikel-Visualisierung
# ==========================================
import web

print("Generiere Canvas Visualisierung...")

canvas_html = $tq<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Python Canvas Particles</title>
    <style>
        body { margin: 0; overflow: hidden; background: #050811; }
        canvas { display: block; width: 100vw; height: 100vh; }
        .info { position: absolute; top: 16px; left: 16px; color: #38bdf8; font-family: monospace; background: rgba(15,23,42,0.8); padding: 8px 16px; border-radius: 8px; }
    </style>
</head>
<body>
    <div class="info">Python Localhost Canvas: 80 Partikel aktiv (Tippe/Ziehe)</div>
    <canvas id="c"></canvas>
    <script>
        const canvas = document.getElementById('c');
        const ctx = canvas.getContext('2d');
        let width = canvas.width = window.innerWidth;
        let height = canvas.height = window.innerHeight;

        const particles = [];
        for (let i = 0; i < 80; i++) {
            particles.push({
                x: Math.random() * width,
                y: Math.random() * height,
                vx: (Math.random() - 0.5) * 2,
                vy: (Math.random() - 0.5) * 2,
                radius: Math.random() * 3 + 1.5,
                color: `hsl(${'$'}{Math.random() * 60 + 190}, 90%, 65%)`
            });
        }

        function draw() {
            ctx.fillStyle = 'rgba(5, 8, 17, 0.2)';
            ctx.fillRect(0, 0, width, height);

            for (let i = 0; i < particles.length; i++) {
                let p = particles[i];
                p.x += p.vx;
                p.y += p.vy;

                if (p.x < 0 || p.x > width) p.vx *= -1;
                if (p.y < 0 || p.y > height) p.vy *= -1;

                ctx.beginPath();
                ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
                ctx.fillStyle = p.color;
                ctx.fill();

                for (let j = i + 1; j < particles.length; j++) {
                    let p2 = particles[j];
                    let dist = Math.hypot(p.x - p2.x, p.y - p2.y);
                    if (dist < 100) {
                        ctx.beginPath();
                        ctx.moveTo(p.x, p.y);
                        ctx.lineTo(p2.x, p2.y);
                        ctx.strokeStyle = `rgba(56, 189, 248, ${'$'}{1 - dist / 100})`;
                        ctx.lineWidth = 0.6;
                        ctx.stroke();
                    }
                }
            }
            requestAnimationFrame(draw);
        }
        draw();
    </script>
</body>
</html>$tq

web.serve_html(canvas_html, port=8080)
print("🚀 Canvas Animation bereit auf http://127.0.0.1:8080")
""".trimIndent()

            val textAdventureCode = """
# ==========================================
# 🎮 Python Text-Adventure mit Terminal I/O
# ==========================================
import random
import time

print("=" * 45)
print("   🏰 DAS VERGESSENE SCHLOSS VON PYTHON")
print("=" * 45)

name = input("Wie lautet dein Heldenname? ")
print(f"\nSei gegrüßt, edler {name}! Dein Abenteuer beginnt...")

inventory = ["Fackel", "Heiltrank"]
gold = 50
hp = 100

print(f"Du stehst vor einem alten Portal. Du trägst {gold} Gold und dein Inventar: {inventory}")
print("1: Durch das große Haupttor gehen")
print("2: Durch das Kellerfenster schleichen")
print("3: Inventar ansehen")

choice = input("Wähle deinen Weg (1/2/3): ")

if choice == "1":
    print("\nDu öffnest das knarrende Haupttor...")
    time.sleep(0.5)
    monster_hp = 30
    print("Ein wilder Goblin (HP: 30) springt hervor!")
    
    while monster_hp > 0 and hp > 0:
        action = input("Willst du (A)ngreifen oder (H)eiltrank nutzen? ").upper()
        if action == "A":
            dmg = random.randint(10, 20)
            monster_hp -= dmg
            print(f"⚔️ Du triffst den Goblin für {dmg} Schaden! Goblin HP: {max(0, monster_hp)}")
        elif action == "H":
            if "Heiltrank" in inventory:
                inventory.remove("Heiltrank")
                hp = min(100, hp + 40)
                print(f"🧪 Du trinkst den Heiltrank! Deine HP: {hp}")
            else:
                print("Du hast keine Heiltränke mehr!")
        
        if monster_hp > 0:
            goblin_dmg = random.randint(5, 12)
            hp -= goblin_dmg
            print(f"💥 Der Goblin schlägt zurück für {goblin_dmg} Schaden! Deine HP: {hp}")

    if hp > 0:
        found_gold = random.randint(20, 50)
        gold += found_gold
        print(f"\n🎉 Sieg! Der Goblin lässt {found_gold} Gold fallen. Total Gold: {gold}")
    else:
        print("\n☠️ Du wurdest besiegt!")

elif choice == "2":
    print("\nDu kriechst durch das feuchte Kellerfenster...")
    time.sleep(0.5)
    print("Du findest eine Schatzkiste mit einem magischen Schwert und 100 Gold!")
    inventory.append("Magisches Schwert")
    gold += 100
    print(f"Aktualisiertes Inventar: {inventory}, Gold: {gold}")

else:
    print(f"\nDein Status - HP: {hp}, Gold: {gold}, Items: {inventory}")

print("\nDanke fürs Spielen!")
""".trimIndent()

            val mathCode = """
# ==========================================
# 🧮 Mathematik, Primzahlen & Benchmarking
# ==========================================
import math
import time

print("🔍 Starte Primzahl-Berechnung im Bereich bis 100...")

def is_prime(n):
    if n < 2:
        return False
    for i in range(2, int(math.sqrt(n)) + 1):
        if n % i == 0:
            return False
    return True

t0 = time.time()
primes = [x for x in range(2, 101) if is_prime(x)]
t1 = time.time()

print(f"Gefundene Primzahlen ({len(primes)}): {primes}")
print(f"Berechnungsdauer: {round((t1 - t0) * 1000, 2)} ms")

print("\n📈 Fibonacci-Zahlen:")
def fibonacci(count):
    seq = [0, 1]
    for _ in range(count - 2):
        seq.append(seq[-1] + seq[-2])
    return seq

fib15 = fibonacci(15)
print(f"Erste 15 Fibonacci-Zahlen: {fib15}")
print(f"Summe der Zahlen: {sum(fib15)}")
print(f"Maximum: {max(fib15)}, Minimum: {min(fib15)}")
""".trimIndent()

            val statsCode = """
# ==========================================
# 📊 Statistik & ASCII-Diagramme im Terminal
# ==========================================
import math

data = {
    "Python": 88,
    "JavaScript": 72,
    "Kotlin": 65,
    "Rust": 54,
    "Go": 48,
    "C++": 40
}

print("=" * 45)
print("   📊 PROGRAMMIERSPRACHEN POPULARITÄT")
print("=" * 45)

values = list(data.values())
mean_val = sum(values) / len(values)
max_val = max(values)

print(f"Anzahl Einträge: {len(data)}")
print(f"Durchschnitt (Mean): {round(mean_val, 1)}")
print(f"Höchstwert: {max_val}\n")

print("BALKENDIAGRAMM:")
for lang, score in data.items():
    bar_len = int((score / max_val) * 25)
    bar = "█" * bar_len + "░" * (25 - bar_len)
    print(f"{lang:12} | {bar} | {score}%")

print("=" * 45)
""".trimIndent()

            val fileCode = """
# ==========================================
# 📝 Virtuelle Dateiverwaltung & JSON
# ==========================================
import json
import os

print("📂 Virtuelles Dateisystem:")
print(f"Vorhandene Dateien: {os.listdir()}")

# Datei erstellen und schreiben
user_data = {
    "project": "PyRunner Mobile",
    "version": "1.0",
    "features": ["Terminal", "Localhost Web UI", "Room Database"],
    "active": True
}

f = open("my_config.json", "w")
json_string = json.dumps(user_data)
f.write(json_string)
f.close()
print("✅ 'my_config.json' erfolgreich geschrieben!")

# Datei lesen und dekodieren
f_read = open("my_config.json", "r")
content = f_read.read()
f_read.close()

loaded_obj = json.loads(content)
print(f"📖 Gelesene Daten: {loaded_obj}")
print(f"Projektname: {loaded_obj.get('project')}")
print(f"Features: {', '.join(loaded_obj.get('features'))}")
""".trimIndent()

            return listOf(
                ScriptEntity(
                    title = "Flask Localhost Web Dashboard",
                    description = "Startet ein interaktives Web-UI auf localhost:8080 mit Counter, Status und CSS Styling",
                    category = "Web UI",
                    isFavorite = true,
                    code = flaskCode
                ),
                ScriptEntity(
                    title = "Interaktives Terminal Text-Adventure",
                    description = "Klassisches interaktives Spiel mit input() Entscheidungen und Inventarverwaltung",
                    category = "Games",
                    isFavorite = true,
                    code = textAdventureCode
                ),
                ScriptEntity(
                    title = "Canvas Partikel-Animation Web-App",
                    description = "Erzeugt eine dynamische HTML5 Canvas Partikel-Simulation auf Localhost",
                    category = "Web UI",
                    isFavorite = false,
                    code = canvasCode
                ),
                ScriptEntity(
                    title = "Mathematik & Primzahl-Generator",
                    description = "Algorithmen, Fibonacci-Folge und Primzahlen-Berechnung mit Zeitmessung",
                    category = "Data & Math",
                    isFavorite = false,
                    code = mathCode
                ),
                ScriptEntity(
                    title = "ASCII Balkendiagramm & Statistik",
                    description = "Statistische Auswertung und grafische ASCII-Balkendiagramme direkt im Terminal",
                    category = "Data & Math",
                    isFavorite = false,
                    code = statsCode
                ),
                ScriptEntity(
                    title = "Virtual File I/O & JSON Datenverarbeitung",
                    description = "Erstellen, Schreiben, Lesen und Parsen von virtuellen JSON-Dateien",
                    category = "System",
                    isFavorite = false,
                    code = fileCode
                )
            )
        }
    }
}

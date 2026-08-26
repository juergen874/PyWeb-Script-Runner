package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.EngineState
import com.example.engine.InstalledPackage
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun PipManagerView(
    viewModel: MainViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val pyodideEngine = viewModel.pyodideEngine
    val engineState by pyodideEngine.engineState.collectAsState()
    val installedPackages by pyodideEngine.installedPackages.collectAsState()
    var customPackageInput by remember { mutableStateOf("") }

    val popularPackages = listOf(
        InstalledPackage("pysolarmanv5", "0.3.0", "Solarman V5 & Deye Wechselrichter Modbus Protokoll"),
        InstalledPackage("requests", "2.31.0", "HTTP Bibliothek für Python (REST APIs, Web Scraping)"),
        InstalledPackage("numpy", "1.26.4", "Numerische Mathematik & High-Performance Arrays"),
        InstalledPackage("pandas", "2.2.0", "Leistungsstarke Datenanalyse & Tabellenverarbeitung"),
        InstalledPackage("beautifulsoup4", "4.12.3", "HTML & XML Parser für Datenextraktion"),
        InstalledPackage("cryptography", "42.0.0", "Kryptographie & SSL Verschlüsselung"),
        InstalledPackage("jinja2", "3.1.3", "Moderne Template-Engine für Python"),
        InstalledPackage("urllib3", "2.1.0", "HTTP-Client mit Thread-Sicherheit und Connection Pooling"),
        InstalledPackage("scipy", "1.12.0", "Wissenschaftliche Berechnungen & Signalverarbeitung")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card: Engine Status & Selector
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (engineState) {
                                            EngineState.READY -> TermGreen
                                            EngineState.LOADING -> TermYellow
                                            EngineState.ERROR -> TermRed
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Python Engine & Ökosystem",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (engineState) {
                                EngineState.READY -> TermGreen.copy(alpha = 0.15f)
                                EngineState.LOADING -> TermYellow.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Text(
                                text = when (engineState) {
                                    EngineState.READY -> "CPython 3.11 Bereit"
                                    EngineState.LOADING -> "Lädt WASM..."
                                    EngineState.ERROR -> "Ladefehler"
                                    else -> "Inaktiv"
                                },
                                color = when (engineState) {
                                    EngineState.READY -> TermGreen
                                    EngineState.LOADING -> TermYellow
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Vollständige CPython 3.11 Runtime auf Basis von WebAssembly mit micropip Paketverwaltung, Modbus, Sockets und PyPI-Unterstützung.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Install Custom Package
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📦 Pip Paket installieren",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Installiere jedes beliebige Pure-Python oder C-WASM Paket via PyPI / micropip:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customPackageInput,
                            onValueChange = { customPackageInput = it },
                            placeholder = { Text("z.B. pysolarmanv5, requests, sympy", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Text(
                                    "pip install",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pip_custom_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (customPackageInput.isNotBlank()) {
                                    viewModel.installPipPackage(customPackageInput.trim())
                                    customPackageInput = ""
                                }
                            },
                            enabled = !uiState.isInstallingPip && customPackageInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("pip_install_button")
                        ) {
                            if (uiState.isInstallingPip) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Installieren")
                            }
                        }
                    }
                }
            }
        }

        // Section: Installed Packages
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Installierte Pakete (${installedPackages.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "PyPI / Micropip",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (installedPackages.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Noch keine zusätzlichen Pakete geladen.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Wähle unten ein Paket oder gib oben einen Namen ein.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(installedPackages) { pkg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PyDarkSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🐍", fontSize = 18.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = pkg.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = pkg.summary.ifEmpty { "Python Modul" },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TermGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "v${pkg.version}",
                                color = TermGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Popular Packages for 1-Click Install
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Beliebte Pakete (1-Klick Installation)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        items(popularPackages) { pkg ->
            val isAlreadyInstalled = installedPackages.any { it.name.equals(pkg.name, ignoreCase = true) }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pkg.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "v${pkg.version}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pkg.summary,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (isAlreadyInstalled) {
                        FilledTonalButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = TermGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Aktiv", fontSize = 12.sp, color = TermGreen)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.installPipPackage(pkg.name) },
                            enabled = !uiState.isInstallingPip,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Installieren", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

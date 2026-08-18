package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.server.HttpRequestLog
import com.example.ui.ServerState
import com.example.ui.UiState
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewView(
    uiState: UiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(uiState.serverState.url) }
    var isLoading by remember { mutableStateOf(false) }
    var showServerLogs by remember { mutableStateOf(false) }

    // Keep url in sync
    LaunchedEffect(uiState.serverState.url, uiState.serverState.refreshCounter) {
        currentUrl = uiState.serverState.url
        webViewRef?.loadUrl(uiState.serverState.url)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Browser Address & Action Header
        Surface(
            color = PyDarkSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status badge
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (uiState.serverState.isRunning) TermGreen else TermRed,
                                shape = RoundedCornerShape(50)
                            )
                    )

                    // Address Bar
                    Surface(
                        color = PyDarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Localhost",
                                tint = TermGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = {
                            onRefresh()
                            webViewRef?.reload()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_web_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Neu laden",
                            tint = Color.White
                        )
                    }

                    // Open in external browser
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Konnte Browser nicht öffnen", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("open_external_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Im Browser öffnen",
                            tint = PyBlue
                        )
                    }

                    // Toggle logs
                    IconButton(
                        onClick = { showServerLogs = !showServerLogs },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Server-Logs",
                            tint = if (showServerLogs) PyBlue else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Quick Route Navigator Chips
                if (uiState.serverState.registeredRoutes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Text(
                                text = "Routen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(uiState.serverState.registeredRoutes) { route ->
                            AssistChip(
                                onClick = {
                                    val fullRouteUrl = "http://127.0.0.1:${uiState.serverState.port}$route"
                                    currentUrl = fullRouteUrl
                                    webViewRef?.loadUrl(fullRouteUrl)
                                },
                                label = {
                                    Text(
                                        text = route,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // Server Logs Expandable Panel
        AnimatedVisibility(visible = showServerLogs) {
            Surface(
                color = PyDarkSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Localhost Server Access Logs (${uiState.serverState.requestLogs.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = PyBlue
                        )
                        Text(
                            text = "Port: ${uiState.serverState.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TermGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (uiState.serverState.requestLogs.isEmpty()) {
                        Text(
                            text = "Noch keine Anfragen verzeichnet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.serverState.requestLogs, key = { it.id }) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = log.timestamp, fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text(text = log.method, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (log.method == "GET") TermGreen else PyYellow, fontFamily = FontFamily.Monospace)
                                    Text(text = log.path, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    Text(text = "${log.statusCode} OK", fontSize = 10.sp, color = TermGreen, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(uiState.serverState.url)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("webview_preview")
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = PyBlue
                )
            }
        }
    }
}

package io.devide.qrtx

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File
import java.net.ServerSocket

@Composable
fun WebViewScreen(
    contentDirectory: File,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Find an available port
    val serverPort = remember(contentDirectory.absolutePath) { findAvailablePort() }
    val server = remember(contentDirectory.absolutePath) {
        LocalWebServer(serverPort, contentDirectory).also {
            if (it.startServer()) {
                Log.d("WebViewScreen", "Server started on port $serverPort serving ${contentDirectory.absolutePath}")
            } else {
                Log.e("WebViewScreen", "Failed to start server on port $serverPort")
            }
        }
    }
    
    // Use 127.0.0.1 instead of localhost for better Android compatibility
    val serverUrl = remember(serverPort) { "http://127.0.0.1:$serverPort" }
    
    // Stop the server when leaving the screen
    DisposableEffect(contentDirectory.absolutePath) {
        onDispose {
            server.stopServer()
            Log.d("WebViewScreen", "Server stopped on dispose")
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Web Content",
                style = typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // WebView to display HTML content
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowUniversalAccessFromFileURLs = true
                        
                        // Wait for server to start, then load the URL
                        serverUrl?.let { url ->
                            loadUrl(url)
                        } ?: run {
                            loadUrl("about:blank")
                        }
                    }
                },
                update = { webView ->
                    // Update the WebView URL if server URL changes
                    serverUrl?.let { url ->
                        if (webView.url != url && url != "about:blank") {
                            webView.loadUrl(url)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Find an available port for the local web server
 */
private fun findAvailablePort(): Int {
    return try {
        ServerSocket(0).use { socket ->
            socket.localPort
        }
    } catch (e: Exception) {
        // Fallback to a default port if we can't find one
        8080
    }
}


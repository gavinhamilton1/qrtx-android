package io.devide.qrtx

import android.graphics.Bitmap
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    val serverPort = remember(contentDirectory.absolutePath) { 
        val port = findAvailablePort()
        Log.d("WebViewScreen", "Selected port: $port for directory: ${contentDirectory.absolutePath}")
        port
    }
    
    // Log directory contents for debugging
    LaunchedEffect(contentDirectory.absolutePath) {
        Log.d("WebViewScreen", "Content directory: ${contentDirectory.absolutePath}")
        Log.d("WebViewScreen", "Directory exists: ${contentDirectory.exists()}")
        Log.d("WebViewScreen", "Is directory: ${contentDirectory.isDirectory}")
        if (contentDirectory.exists() && contentDirectory.isDirectory) {
            val files = contentDirectory.listFiles()?.map { it.name } ?: emptyList()
            Log.d("WebViewScreen", "Directory contents (${files.size} items): ${files.take(20).joinToString(", ")}")
        }
    }
    
    val server = remember(contentDirectory.absolutePath) {
        Log.d("WebViewScreen", "Creating LocalWebServer on port $serverPort")
        LocalWebServer(serverPort, contentDirectory).also {
            if (it.startServer()) {
                Log.d("WebViewScreen", "Server started successfully on port $serverPort serving ${contentDirectory.absolutePath}")
            } else {
                Log.e("WebViewScreen", "Failed to start server on port $serverPort")
            }
        }
    }
    
    // Use 127.0.0.1 instead of localhost for better Android compatibility
    val serverUrl = remember(serverPort) { 
        val url = "http://127.0.0.1:$serverPort"
        Log.d("WebViewScreen", "Server URL: $url")
        url
    }
    
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
                    Log.d("WebViewScreen", "Creating WebView, serverUrl: $serverUrl")
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                Log.d("WebViewScreen", "Page started loading: $url")
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("WebViewScreen", "Page finished loading: $url")
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                Log.e("WebViewScreen", "WebView error: ${error?.description} (code: ${error?.errorCode}) for URL: ${request?.url}")
                            }
                            
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString()
                                Log.d("WebViewScreen", "Navigation request: $url")
                                return false
                            }
                            
                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                Log.d("WebViewScreen", "Loading resource: $url")
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowUniversalAccessFromFileURLs = true
                        
                        // Add console message logging
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val level = when (consoleMessage?.messageLevel()) {
                                    ConsoleMessage.MessageLevel.LOG -> "LOG"
                                    ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                    ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                    ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                                    else -> "INFO"
                                }
                                Log.d("WebViewConsole", "[$level] ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                return true
                            }
                        }
                        
                        Log.d("WebViewScreen", "WebView settings configured, attempting to load URL: $serverUrl")
                        
                        // Wait for server to start, then load the URL
                        serverUrl?.let { url ->
                            Log.d("WebViewScreen", "Loading URL: $url")
                            loadUrl(url)
                        } ?: run {
                            Log.w("WebViewScreen", "No server URL available, loading blank page")
                            loadUrl("about:blank")
                        }
                    }
                },
                update = { webView ->
                    // Update the WebView URL if server URL changes
                    serverUrl?.let { url ->
                        val currentUrl = webView.url
                        Log.d("WebViewScreen", "WebView update - current URL: $currentUrl, target URL: $url")
                        if (currentUrl != url && url != "about:blank") {
                            Log.d("WebViewScreen", "Loading URL in WebView: $url")
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


package io.devide.qrtx

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class LocalWebServer(private val port: Int, private val rootDirectory: File) : NanoHTTPD(port) {
    
    private val mimeTypes = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "json" to "application/json",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
        "ico" to "image/x-icon",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "eot" to "application/vnd.ms-fontobject",
        "otf" to "font/otf",
        "xml" to "application/xml",
        "txt" to "text/plain",
        "pdf" to "application/pdf",
        "zip" to "application/zip"
    )
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        var requestedPath = uri
        
        // Remove query parameters
        if (requestedPath.contains('?')) {
            requestedPath = requestedPath.substringBefore('?')
        }
        
        // Default to index.html if root is requested
        if (requestedPath == "/" || requestedPath.isEmpty()) {
            requestedPath = "/index.html"
        }
        
        // Remove leading slash
        if (requestedPath.startsWith("/")) {
            requestedPath = requestedPath.substring(1)
        }
        
        val file = File(rootDirectory, requestedPath)
        
        // Security check: ensure the requested file is within the root directory
        if (!file.canonicalPath.startsWith(rootDirectory.canonicalPath)) {
            Log.w("LocalWebServer", "Attempted path traversal: $requestedPath")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "text/plain",
                "Forbidden"
            )
        }
        
        if (!file.exists() || file.isDirectory) {
            // If it's a directory, try index.html
            val indexFile = File(file, "index.html")
            if (indexFile.exists()) {
                return serveFile(indexFile)
            }
            Log.w("LocalWebServer", "File not found: $requestedPath")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html",
                "<html><body><h1>404 - File Not Found</h1><p>Requested: $requestedPath</p></body></html>"
            )
        }
        
        return serveFile(file)
    }
    
    private fun serveFile(file: File): Response {
        return try {
            val extension = file.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
            val mimeType = mimeTypes[extension] ?: "application/octet-stream"
            val fileLength = file.length()
            
            // For text-based files, read as string; for binary, use input stream
            val isTextFile = mimeType.startsWith("text/") || 
                            mimeType == "application/javascript" ||
                            mimeType == "application/json" ||
                            mimeType == "application/xml"
            
            val response = if (isTextFile && fileLength < 10 * 1024 * 1024) { // Only for text files under 10MB
                val content = file.readText(Charset.defaultCharset())
                newFixedLengthResponse(
                    Response.Status.OK,
                    mimeType,
                    content
                )
            } else {
                // For binary files or large text files, use input stream
                newChunkedResponse(
                    Response.Status.OK,
                    mimeType,
                    FileInputStream(file)
                )
            }
            
            response.apply {
                addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                addHeader("Pragma", "no-cache")
                addHeader("Expires", "0")
            }
        } catch (e: IOException) {
            Log.e("LocalWebServer", "Error serving file: ${file.absolutePath}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Error reading file: ${e.message}"
            )
        }
    }
    
    fun startServer(): Boolean {
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalWebServer", "Server started on port $port serving ${rootDirectory.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e("LocalWebServer", "Failed to start server", e)
            false
        }
    }
    
    fun stopServer() {
        stop()
        Log.d("LocalWebServer", "Server stopped")
    }
}


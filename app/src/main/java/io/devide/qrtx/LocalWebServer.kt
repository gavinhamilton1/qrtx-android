package io.devide.qrtx

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class LocalWebServer(private val port: Int, private val rootDirectory: File) : NanoHTTPD(port) {
    
    // Detect the actual content directory (where index.html is located)
    private val contentDirectory: File = detectContentDirectory()
    
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
        val method = session.method.name
        Log.d("LocalWebServer", "Received request: $method $uri from ${session.remoteIpAddress}")
        
        var requestedPath = uri
        
        // Remove query parameters
        if (requestedPath.contains('?')) {
            requestedPath = requestedPath.substringBefore('?')
        }
        
        // Default to index.html if root is requested
        if (requestedPath == "/" || requestedPath.isEmpty()) {
            requestedPath = "/index.html"
            Log.d("LocalWebServer", "Root request, defaulting to index.html")
        }
        
        // Remove leading slash
        if (requestedPath.startsWith("/")) {
            requestedPath = requestedPath.substring(1)
        }
        
        Log.d("LocalWebServer", "Resolving path: $requestedPath (content dir: ${contentDirectory.absolutePath})")
        
        // Use contentDirectory instead of rootDirectory for file resolution
        val file = File(contentDirectory, requestedPath)
        Log.d("LocalWebServer", "Looking for file: ${file.absolutePath} (exists: ${file.exists()}, isDir: ${file.isDirectory})")
        
        // Security check: ensure the requested file is within the root directory
        if (!file.canonicalPath.startsWith(rootDirectory.canonicalPath)) {
            Log.w("LocalWebServer", "Attempted path traversal: $requestedPath")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "text/plain",
                "Forbidden"
            )
        }
        
        if (!file.exists()) {
            Log.w("LocalWebServer", "File not found: $requestedPath (looking in ${contentDirectory.absolutePath})")
            // List available files for debugging
            if (contentDirectory.exists() && contentDirectory.isDirectory) {
                val availableFiles = contentDirectory.listFiles()?.map { it.name }?.take(10) ?: emptyList()
                Log.d("LocalWebServer", "Available files in content directory: ${availableFiles.joinToString(", ")}")
            }
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html",
                "<html><body><h1>404 - File Not Found</h1><p>Requested: $requestedPath</p><p>Content dir: ${contentDirectory.absolutePath}</p></body></html>"
            )
        }
        
        if (file.isDirectory) {
            // If it's a directory, try index.html in that directory
            val indexFile = File(file, "index.html")
            Log.d("LocalWebServer", "Requested path is a directory, checking for index.html: ${indexFile.absolutePath}")
            if (indexFile.exists()) {
                Log.d("LocalWebServer", "Found index.html in directory, serving it")
                return serveFile(indexFile)
            }
            Log.w("LocalWebServer", "Directory found but no index.html: $requestedPath")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/html",
                "<html><body><h1>404 - Directory Not Found</h1><p>Requested: $requestedPath</p></body></html>"
            )
        }
        
        Log.d("LocalWebServer", "Serving file: ${file.absolutePath} (size: ${file.length()} bytes)")
        return serveFile(file)
    }
    
    /**
     * Detect the content directory by finding where index.html is located
     * Returns the directory containing index.html, or rootDirectory if not found
     */
    private fun detectContentDirectory(): File {
        Log.d("LocalWebServer", "Detecting content directory from root: ${rootDirectory.absolutePath}")
        Log.d("LocalWebServer", "Root directory exists: ${rootDirectory.exists()}, isDirectory: ${rootDirectory.isDirectory}")
        
        // Check root directory first
        val rootIndex = File(rootDirectory, "index.html")
        Log.d("LocalWebServer", "Checking root for index.html: ${rootIndex.absolutePath} (exists: ${rootIndex.exists()})")
        if (rootIndex.exists()) {
            Log.d("LocalWebServer", "Found index.html in root directory")
            return rootDirectory
        }
        
        // List root directory contents for debugging
        if (rootDirectory.exists() && rootDirectory.isDirectory) {
            val rootContents = rootDirectory.listFiles()?.map { "${it.name} (${if (it.isDirectory) "dir" else "file"})" } ?: emptyList()
            Log.d("LocalWebServer", "Root directory contents: ${rootContents.joinToString(", ")}")
        }
        
        // Check common subdirectories
        val commonSubdirs = listOf("dist", "build", "public", "www", "web", "app")
        Log.d("LocalWebServer", "Checking common subdirectories: ${commonSubdirs.joinToString(", ")}")
        for (subdir in commonSubdirs) {
            val subdirPath = File(rootDirectory, subdir)
            val subdirIndex = File(subdirPath, "index.html")
            Log.d("LocalWebServer", "Checking $subdir/: ${subdirPath.absolutePath} (exists: ${subdirPath.exists()}, index.html exists: ${subdirIndex.exists()})")
            if (subdirIndex.exists()) {
                Log.d("LocalWebServer", "Found index.html in $subdir/ subdirectory, using it as content root")
                return subdirPath
            }
        }
        
        // Recursively search for index.html
        Log.d("LocalWebServer", "Recursively searching for index.html...")
        val foundIndex = findIndexHtmlRecursive(rootDirectory)
        if (foundIndex != null) {
            val contentDir = foundIndex.parentFile
            Log.d("LocalWebServer", "Found index.html at: ${foundIndex.absolutePath}, using ${contentDir?.absolutePath} as content root")
            return contentDir ?: rootDirectory
        }
        
        Log.w("LocalWebServer", "No index.html found, using root directory as content root")
        return rootDirectory
    }
    
    /**
     * Recursively search for index.html in the directory tree
     */
    private fun findIndexHtmlRecursive(directory: File): File? {
        if (!directory.isDirectory) {
            return null
        }
        
        // Check current directory
        val indexFile = File(directory, "index.html")
        if (indexFile.exists() && indexFile.isFile) {
            return indexFile
        }
        
        // Recursively check subdirectories
        val subdirs = directory.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (subdir in subdirs) {
            val found = findIndexHtmlRecursive(subdir)
            if (found != null) {
                return found
            }
        }
        
        return null
    }
    
    private fun serveFile(file: File): Response {
        return try {
            val extension = file.name.substringAfterLast('.', "").lowercase(Locale.getDefault())
            val mimeType = mimeTypes[extension] ?: "application/octet-stream"
            val fileLength = file.length()
            Log.d("LocalWebServer", "Serving file: ${file.name}, type: $mimeType, size: $fileLength bytes")
            
            // For text-based files, read as string; for binary, use input stream
            val isTextFile = mimeType.startsWith("text/") || 
                            mimeType == "application/javascript" ||
                            mimeType == "application/json" ||
                            mimeType == "application/xml"
            
            val response = if (isTextFile && fileLength < 10 * 1024 * 1024) { // Only for text files under 10MB
                val content = file.readText(Charset.defaultCharset())
                Log.d("LocalWebServer", "Serving text file, content length: ${content.length} characters")
                
                // Log HTML content preview for debugging
                if (mimeType == "text/html") {
                    val preview = content.take(1000)
                    Log.d("LocalWebServer", "HTML content preview (first ${preview.length} chars): $preview")
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    mimeType,
                    content
                )
            } else {
                // For binary files or large text files, use input stream
                Log.d("LocalWebServer", "Serving binary/large file via stream")
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
                // Add CORS headers for WebView compatibility
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "*")
            }
            
            Log.d("LocalWebServer", "Successfully created response for: ${file.name} (${response.status})")
            response
        } catch (e: IOException) {
            Log.e("LocalWebServer", "Error serving file: ${file.absolutePath}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Error reading file: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e("LocalWebServer", "Unexpected error serving file: ${file.absolutePath}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Unexpected error: ${e.message}"
            )
        }
    }
    
    fun startServer(): Boolean {
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalWebServer", "Server started on port $port serving from ${contentDirectory.absolutePath} (root: ${rootDirectory.absolutePath})")
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


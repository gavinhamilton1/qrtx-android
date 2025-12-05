package io.devide.qrtx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.devide.qrtx.ui.theme.QrtxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QrtxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val receivedDroplets = remember { mutableStateMapOf<Long, Droplet>() }
                    var numBlocks by remember { mutableStateOf(0) }
                    var blockSize by remember { mutableStateOf(0) }
                    var fileSize by remember { mutableStateOf(0L) }
                    var reconstructedFile by remember { mutableStateOf<File?>(null) }
                    var isReconstructing by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var reconstructionProgress by remember { mutableStateOf(0.0) }
                    var numSolved by remember { mutableStateOf(0) }
                    var decoder by remember { mutableStateOf<FountainCodeDecoder?>(null) }
                    var processedSeeds by remember { mutableStateOf(setOf<Long>()) }
                    var isCapturing by remember { mutableStateOf(false) }
                    var unzippedContentDir by remember { mutableStateOf<File?>(null) }
                    var showWebView by remember { mutableStateOf(false) }

                    when {
                        showWebView && unzippedContentDir != null -> {
                            WebViewScreen(
                                contentDirectory = unzippedContentDir!!,
                                onBack = { showWebView = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        
                        reconstructedFile != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                reconstructedFile?.let { file ->
                                    ReconstructedFileView(
                                        file = file,
                                        onOpenWithOtherApps = { fileToOpen ->
                                            openFileWithOtherApps(fileToOpen)
                                        },
                                        onReset = {
                                            reconstructedFile = null
                                            isReconstructing = false
                                            decoder = null
                                            processedSeeds = emptySet()
                                            errorMessage = null
                                            isCapturing = false // Return to home screen
                                            receivedDroplets.clear()
                                            numBlocks = 0
                                            blockSize = 0
                                            fileSize = 0L
                                            numSolved = 0
                                            reconstructionProgress = 0.0
                                        }
                                    )
                                }
                            }
                        }
                        
                        isCapturing -> {
                            CaptureScreen(
                                onBack = { isCapturing = false },
                                onQrCodeScanned = { qrCode ->
                                    try {
                                        val droplet = Droplet.fromString(qrCode)
                                        if (droplet != null) {
                                            errorMessage = null // Clear error on successful parse
                                            if (!receivedDroplets.containsKey(droplet.seed)) {
                                                if (receivedDroplets.isEmpty()) {
                                                    numBlocks = droplet.numBlocks
                                                    fileSize = droplet.fileSize
                                                    blockSize = droplet.blockSize
                                                    // Initialize decoder immediately to start processing
                                                    if (decoder == null && numBlocks > 0 && blockSize > 0) {
                                                        decoder = FountainCodeDecoder(numBlocks, blockSize)
                                                        isReconstructing = true
                                                        processedSeeds = emptySet()
                                                    }
                                                }
                                                receivedDroplets[droplet.seed] = droplet
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (receivedDroplets.isEmpty()) {
                                            errorMessage = e.message ?: "Error processing droplet"
                                        }
                                        Log.e("MainActivity", "Error processing droplet", e)
                                    }
                                },
                                onCameraError = {
                                    errorMessage = it.message ?: "An unknown camera error occurred"
                                },
                                numBlocks = numBlocks,
                                numSolved = numSolved,
                                receivedDropletsCount = receivedDroplets.size,
                                fileSize = fileSize,
                                isReconstructing = isReconstructing,
                                reconstructionProgress = reconstructionProgress,
                                errorMessage = errorMessage,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        
                        else -> {
                            HomeScreen(
                                onStartCapture = { isCapturing = true },
                                onOpenWebView = {
                                    if (unzippedContentDir != null) {
                                        showWebView = true
                                    }
                                },
                                hasWebContent = unzippedContentDir != null,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }

                    // Initialize decoder as soon as we know the block parameters (process-as-you-go approach)
                    LaunchedEffect(numBlocks, blockSize) {
                        if (numBlocks > 0 && blockSize > 0 && decoder == null && reconstructedFile == null) {
                            decoder = FountainCodeDecoder(numBlocks, blockSize)
                            isReconstructing = true
                            processedSeeds = emptySet()
                            Log.d("MainActivity", "Initialized decoder for $numBlocks blocks, blockSize: $blockSize (process-as-you-go)")
                        }
                    }

                    // Continuously processes droplets as they arrive (no waiting threshold)
                    LaunchedEffect(receivedDroplets.size, numBlocks, blockSize) {
                        if (numBlocks > 0 && blockSize > 0 && decoder != null && reconstructedFile == null) {
                            withContext(Dispatchers.Default) {
                                try {
                                    val currentDecoder = decoder ?: return@withContext
                                    
                                    // Find new droplets that haven't been processed yet
                                    val newDroplets = receivedDroplets.values.filter { !processedSeeds.contains(it.seed) }
                                    
                                    if (newDroplets.isNotEmpty()) {
                                        Log.d("MainActivity", "Processing ${newDroplets.size} new droplets. Total received: ${receivedDroplets.size}, Solved: ${currentDecoder.getNumSolved()}/${numBlocks}")
                                    }
                                    
                                    // Process each new droplet immediately as it arrives
                                    newDroplets.forEach { droplet ->
                                        try {
                                            currentDecoder.addDroplet(droplet.toMap())
                                            // Track this seed as processed
                                            processedSeeds = processedSeeds + droplet.seed
                                        } catch (e: Exception) {
                                            // Only log error but don't fail completely
                                            Log.w("MainActivity", "Error adding droplet (seed: ${droplet.seed}): ${e.message}")
                                        }
                                    }
                                    
                                    // After processing new droplets, try to process pending ones aggressively
                                    if (newDroplets.isNotEmpty()) {
                                        val beforeSolved = currentDecoder.getNumSolved()
                                        currentDecoder.processPendingDropletsAggressively()
                                        val afterSolved = currentDecoder.getNumSolved()
                                        
                                        if (afterSolved > beforeSolved) {
                                            Log.d("MainActivity", "Made progress: solved ${afterSolved - beforeSolved} more blocks (${afterSolved}/${numBlocks} total)")
                                        }
                                    }
                                    
                                    // Update progress and solved count
                                    reconstructionProgress = currentDecoder.getProgress()
                                    numSolved = currentDecoder.getNumSolved()
                                    
                                    // Check if complete - verify all blocks are present
                                    if (currentDecoder.isComplete()) {
                                        try {
                                            // Double-check all blocks are present before reconstructing
                                            val missingBlocks = mutableListOf<Int>()
                                            for (i in 0 until numBlocks) {
                                                if (!currentDecoder.hasBlock(i)) {
                                                    missingBlocks.add(i)
                                                }
                                            }
                                            
                                            if (missingBlocks.isNotEmpty()) {
                                                Log.e("MainActivity", "Missing blocks: $missingBlocks")
                                                errorMessage = "Reconstruction incomplete: missing blocks ${missingBlocks.joinToString(", ")}. " +
                                                        "Please scan more QR codes."
                                                // Don't stop reconstruction, allow more droplets
                                            } else {
                                                val result = currentDecoder.getResult()
                                                val fileSize = result.size
                                                Log.d("MainActivity", "Reconstruction complete! Reconstructed file size: $fileSize bytes")
                                                
                                                // Validate file size is reasonable (at least 100 bytes)
                                                if (fileSize < 100) {
                                                    errorMessage = "Reconstructed file is too small ($fileSize bytes). " +
                                                            "This suggests the reconstruction is incomplete. Please scan more QR codes."
                                                    // Continue reconstruction
                                                } else {
                                                    val savedFile = saveFile(result, "reconstructed.png")
                                                    
                                                    // Check if it's a ZIP file and extract it
                                                    val fileType = detectFileType(result)
                                                    if (fileType == "zip") {
                                                        unzippedContentDir = extractZipFile(savedFile)
                                                        Log.d("MainActivity", "ZIP file extracted to: ${unzippedContentDir?.absolutePath}")
                                                        // For ZIP files, skip file view and go directly to home screen
                                                        reconstructedFile = null
                                                        isCapturing = false
                                                        Log.d("MainActivity", "ZIP file processed, returning to home screen")
                                                    } else {
                                                        // For non-ZIP files, show the file view
                                                        reconstructedFile = savedFile
                                                        Log.d("MainActivity", "File saved successfully: ${savedFile.absolutePath}")
                                                    }
                                                    
                                                    isReconstructing = false
                                                    decoder = null
                                                    processedSeeds = emptySet()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Error reconstructing file: ${e.message}. Please scan more QR codes."
                                            Log.e("MainActivity", "Error getting result", e)
                                            // Don't stop reconstruction - allow more droplets
                                        }
                                    } else if (receivedDroplets.size >= numBlocks * 3) {
                                        // If we've received 3x as many droplets and still can't solve, there's likely an issue
                                        errorMessage = "Reconstruction failed. Progress: ${String.format("%.1f", currentDecoder.getProgress())}%. " +
                                                "Received ${receivedDroplets.size} droplets but only solved ${currentDecoder.getNumSolved()}/${currentDecoder.getNumBlocks()} blocks. " +
                                                "Pending droplets: ${currentDecoder.getNumPendingDroplets()}. " +
                                                "This may indicate a problem with the droplet data or encoding."
                                        isReconstructing = false
                                        decoder = null
                                    }
                                    // Otherwise, continue accepting more droplets - the effect will re-run when new droplets arrive
                                } catch (e: Exception) {
                                    errorMessage = "Reconstruction failed: ${e.message}"
                                    isReconstructing = false
                                    decoder = null
                                    Log.e("MainActivity", "Reconstruction failed", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Detect file type from magic bytes (file header)
     */
    private fun detectFileType(data: ByteArray): String {
        if (data.size < 4) return "bin"
        
        // Check common file signatures
        val header = data.sliceArray(0 until minOf(16, data.size))
        
        return when {
            // Images
            header.size >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> "png"
            header.size >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "jpg"
            header.size >= 6 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && 
            header[2] == 0x46.toByte() && header[3] == 0x38.toByte() -> "gif"
            header.size >= 12 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && 
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() -> "webp"
            
            // Documents
            header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && 
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte()) && 
            (header[3] == 0x04.toByte() || header[3] == 0x06.toByte() || header[3] == 0x08.toByte()) -> "zip"
            header.size >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && 
            header[2] == 0x44.toByte() && header[3] == 0x46.toByte() -> "pdf"
            
            // Archives
            header.size >= 3 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte() && 
            header[2] == 0x08.toByte() -> "gz"
            
            // Text files (try to detect UTF-8/BOM)
            header.size >= 3 && header[0] == 0xEF.toByte() && header[1] == 0xBB.toByte() && 
            header[2] == 0xBF.toByte() -> "txt"
            
            // Default to binary if we can't detect
            else -> "bin"
        }
    }
    
    private fun saveFile(data: ByteArray, defaultFileName: String): File {
        // Detect file type and use appropriate extension
        val fileType = detectFileType(data)
        val extension = if (fileType == "bin") {
            // If binary, try to extract extension from default filename, or use .bin
            defaultFileName.substringAfterLast('.', "bin")
        } else {
            fileType
        }
        
        val fileName = if (defaultFileName.contains('.')) {
            defaultFileName.substringBeforeLast('.') + ".$extension"
        } else {
            "reconstructed.$extension"
        }
        
        val file = File(filesDir, fileName)
        try {
            file.writeBytes(data)
            Log.d("MainActivity", "File saved to ${file.absolutePath}, type: $fileType")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving file", e)
        }
        return file
    }
    
    /**
     * Extract ZIP file to a directory
     */
    private fun extractZipFile(zipFile: File): File? {
        try {
            val extractDir = File(filesDir, "extracted_${System.currentTimeMillis()}")
            extractDir.mkdirs()
            
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val entryFile = File(extractDir, entry.name)
                    
                    // Create parent directories if needed
                    entryFile.parentFile?.mkdirs()
                    
                    // Skip if it's a directory
                    if (!entry.isDirectory) {
                        FileOutputStream(entryFile).use { outputStream ->
                            zipInputStream.copyTo(outputStream)
                        }
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            
            Log.d("MainActivity", "ZIP extracted to: ${extractDir.absolutePath}")
            return extractDir
        } catch (e: Exception) {
            Log.e("MainActivity", "Error extracting ZIP file", e)
            return null
        }
    }
    
    /**
     * Open file with other apps using Intent
     */
    private fun openFileWithOtherApps(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Open with..."))
            } else {
                Log.e("MainActivity", "No app found to open file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening file", e)
        }
    }
    
    /**
     * Get MIME type for file
     */
    private fun getMimeType(file: File): String {
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }

    private fun Droplet.toMap(): Map<String, Any> {
        return mapOf(
            "seed" to this.seed.toInt(),
            "data" to this.data,
            "num_blocks" to this.numBlocks,
            "file_size" to this.fileSize.toInt(),
            "block_size" to this.blockSize
        )
    }
}

/**
 * Format file size in human-readable format
 */
fun formatFileSizeHelper(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes bytes"
    }
}

@Composable
fun ReconstructedFileView(
    file: File,
    onOpenWithOtherApps: (File) -> Unit,
    onReset: () -> Unit
) {
    val imageBitmap = remember(file) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }
    
    if (imageBitmap != null) {
        // Display image
        Image(
            bitmap = imageBitmap,
            contentDescription = "Reconstructed Image",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Display file info for non-image files
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "File Reconstructed",
                style = typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "File Name:",
                style = typography.titleSmall,
                color = Color.Gray
            )
            Text(
                text = file.name,
                style = typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            
            Text(
                text = "File Size:",
                style = typography.titleSmall,
                color = Color.Gray
            )
            Text(
                text = formatFileSizeHelper(file.length()),
                style = typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            
            Button(
                onClick = { onOpenWithOtherApps(file) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
            ) {
                Text("Open with Other Apps")
            }
            
            Button(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
            ) {
                Text("Scan Another File")
            }
        }
    }
}


@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
        }
    )

    // Request permission once, when the composable enters the composition
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (hasCamPermission) {
            val previewView = remember { PreviewView(context) }

            // Use DisposableEffect to bind and unbind the camera
            DisposableEffect(lifecycleOwner) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, QrCodeAnalyzer(
                                onQrCodeScanned = onQrCodeScanned,
                                onError = onError
                            ))
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        // Must be called on the main thread
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        onError(exc)
                        Log.e("CameraPreview", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))

                onDispose {
                    // Shut down the executor and unbind all use cases
                    cameraExecutor.shutdown()
                    cameraProviderFuture.addListener({
                        cameraProviderFuture.get().unbindAll()
                    }, ContextCompat.getMainExecutor(context))
                }
            }
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

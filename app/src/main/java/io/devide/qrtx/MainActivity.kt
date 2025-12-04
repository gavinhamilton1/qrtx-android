package io.devide.qrtx

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import io.devide.qrtx.ui.theme.QrtxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

                    when {
                        reconstructedFile != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                reconstructedFile?.let { file ->
                                    val imageBitmap = remember(file) {
                                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                    }
                                    if (imageBitmap != null) {
                                        Image(
                                            bitmap = imageBitmap,
                                            contentDescription = "Reconstructed Image",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Text(
                                                text = "Could not display image",
                                                style = typography.headlineSmall,
                                                color = Color.Red
                                            )
                                            Text(
                                                text = "The reconstructed file may be corrupted or incomplete.",
                                                style = typography.bodyMedium,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                            Text(
                                                text = "File size: ${file.length()} bytes",
                                                style = typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    reconstructedFile = null
                                                    isReconstructing = false
                                                    decoder = null
                                                    processedSeeds = emptySet()
                                                    errorMessage = "Previous reconstruction failed. Please scan QR codes again."
                                                },
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                Text("Try Again")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Show camera and reconstruction status overlay
                            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                CameraPreview(
                                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
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
                                    onError = {
                                        errorMessage = it.message ?: "An unknown camera error occurred"
                                    }
                                )
                                
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Show reconstruction progress if reconstructing
                                    if (isReconstructing) {
                                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                        Text(
                                            text = "Reconstructing file...",
                                            style = typography.titleMedium
                                        )
                                        Text(
                                            text = String.format("%.1f%% complete", reconstructionProgress),
                                            style = typography.bodyMedium
                                        )
                                        Text(
                                            text = "$numSolved/$numBlocks blocks solved",
                                            style = typography.bodySmall
                                        )
                                        Text(
                                            text = "Keep scanning QR codes to add more droplets",
                                            style = typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    errorMessage?.let {
                                        Text(
                                            text = "Error: $it",
                                            color = Color.Red,
                                            style = typography.bodyMedium
                                        )
                                    }
                                    
                                    if (numBlocks > 0) {
                                        if (!isReconstructing) {
                                            Text("Status", style = typography.headlineSmall)
                                        }
                                        LinearProgressIndicator(
                                            progress = receivedDroplets.size.toFloat() / numBlocks.toFloat(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(text = "${receivedDroplets.size} droplets received")
                                        decoder?.let { d ->
                                            val solved = d.getNumSolved()
                                            Text(
                                                text = "$solved/$numBlocks blocks solved (${String.format("%.1f", d.getProgress())}%)",
                                                style = typography.bodyMedium
                                            )
                                        }
                                        Text(text = "File size: ${fileSize / 1024} KB")
                                        if (!isReconstructing && decoder != null) {
                                            Text(
                                                text = "Processing droplets as they arrive...",
                                                style = typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    } else {
                                        if (errorMessage == null) {
                                            Text("Scanning for QR code...")
                                        }
                                    }
                                }
                            }
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
                                                    reconstructedFile = saveFile(result, "reconstructed.png")
                                                    isReconstructing = false
                                                    decoder = null
                                                    processedSeeds = emptySet()
                                                    Log.d("MainActivity", "File saved successfully: ${reconstructedFile?.absolutePath}")
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

    private fun saveFile(data: ByteArray, fileName: String): File {
        val file = File(filesDir, fileName)
        try {
            file.writeBytes(data)
            Log.d("MainActivity", "File saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving file", e)
        }
        return file
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

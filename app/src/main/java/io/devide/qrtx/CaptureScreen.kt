package io.devide.qrtx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onQrCodeScanned: (String) -> Unit,
    onCameraError: (Exception) -> Unit,
    numBlocks: Int,
    numSolved: Int,
    receivedDropletsCount: Int,
    fileSize: Long,
    isReconstructing: Boolean,
    reconstructionProgress: Double,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
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
                text = "Capture",
                style = typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        CameraPreview(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f),
            onQrCodeScanned = onQrCodeScanned,
            onError = onCameraError
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
                    progress = receivedDropletsCount.toFloat() / numBlocks.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "$receivedDropletsCount droplets received")
                Text(
                    text = "$numSolved/$numBlocks blocks solved (${String.format("%.1f", reconstructionProgress)}%)",
                    style = typography.bodyMedium
                )
                Text(text = "File size: ${fileSize / 1024} KB")
                if (!isReconstructing) {
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

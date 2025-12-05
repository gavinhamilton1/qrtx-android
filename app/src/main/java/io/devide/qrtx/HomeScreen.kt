package io.devide.qrtx

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStartCapture: () -> Unit,
    onOpenWebView: () -> Unit,
    hasWebContent: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "QR Code Receiver",
                style = typography.headlineLarge
            )
            Text(
                text = "Scan QR codes to receive files",
                style = typography.bodyLarge,
                color = Color.Gray
            )
            
            Button(
                onClick = onStartCapture
            ) {
                Text("Start Capture")
            }
            
            if (hasWebContent) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Available Content",
                    style = typography.titleMedium,
                    color = Color.Gray
                )
                
                // App launcher button
                Card(
                    modifier = Modifier
                        .width(120.dp)
                        .height(120.dp)
                        .clickable(onClick = onOpenWebView),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF6200EE)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "W",
                                style = typography.displayMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Web",
                                style = typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


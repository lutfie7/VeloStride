package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.attemptAutoSignIn
import com.example.core.onGoogleSignInClicked
import com.example.ui.components.VeloStrideWordmark
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    initialErrorMessage: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember(initialErrorMessage) { mutableStateOf(initialErrorMessage) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_auth_hero),
            contentDescription = "Mountain road cycling and trail running sunrise banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.25f),
                            0.35f to ObsidianBg.copy(alpha = 0.85f),
                            0.55f to ObsidianBg
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(140.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_velostride_logo),
                        contentDescription = "VeloStride Crest",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        VeloStrideWordmark(
                            fontSize = 34.sp,
                            onDarkSurface = true
                        )
                        Text(
                            text = "GPS • Telemetry • Wearable Sync",
                            style = MaterialTheme.typography.labelMedium,
                            color = VeloOrange
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Train Smarter. Every Kilometer Synced.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Track Run, Hike, Walk, Trail Run, Cycle & Workout sessions with live VO2 Max, training load, recovery readiness, and native HUAWEI Band 11 Pro pairing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB8C2D1)
                )

                Spacer(Modifier.height(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Watch,
                        title = "Native HUAWEI Band 11 Pro Pairing",
                        subtitle = "Real-time BLE Heart Rate, HRV, Steps, Calories & Sleep Sync",
                        accent = SignalCyan
                    )
                    FeatureHighlightRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "VO2 Max, Training Load & AI Coach",
                        subtitle = "CTL/ATL/TSB Form charts & personalized Gemini coaching",
                        accent = VeloOrange
                    )
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Route,
                        title = "Routes, Segments & Beacon Safety",
                        subtitle = "Custom route planner, PR leaderboards & gear mileage",
                        accent = Color(0xFF22C55E)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VeloOrange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothConnected,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sign_in_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
) {
    Surface(
        color = Color(0xFF171C24),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9BA6B8)
                )
            }
        }
    }
}

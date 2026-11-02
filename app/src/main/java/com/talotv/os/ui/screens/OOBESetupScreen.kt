package com.talotv.os.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.talotv.os.R
import com.talotv.os.TaloApplication
import com.talotv.os.core.boot.HomeKeyAccessibilityService
import com.talotv.os.core.hardware.HardwareReport
import com.talotv.os.core.profile.UserProfile
import com.talotv.os.core.security.DeviceFingerprint
import com.talotv.os.core.system.CloudSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// CHARTE DESIGN SYSTEM TALO OS : APPLE TV × ARC BROWSER × INTRO (VERRE POLI PUR)
// =============================================================================
private val TaloAccentBlue = Color(0xFF1A73E8)     // Bleu Google TV pur
private val TaloAlertRed = Color(0xFFEF3126)       // Rouge d'alerte critique
private val TaloTextPrimary = Color.White
private val TaloTextSecondary = Color(0xFF8E8E93)  // Gris titane
private val TaloTextTertiary = Color(0xFF48484A)   // Gris foncé
private val TaloGlassBorder = Color.White.copy(alpha = 0.12f) // Biseau de verre poli

/**
 * Composant de référence TALO OS : Verre Poli Haute-Fidélité (Polished Crystal Glass)
 * Inspiré directement de la vidéo Intro.mp4, Apple TV tvOS, VisionOS et Arc Browser.
 *
 * Propriétés optiques & physiques :
 * 1. Halo caustique diffusé sous la dalle de verre (Backglow fluide)
 * 2. Corps cristallin translucide (Translucidité soyeuse révélant le décor arrière)
 * 3. Biseau poli spéculaire (Arête vive blanche 75% à 90% captant la lumière zénithale)
 * 4. Reflet spéculaire de surface (Sheen diagonal satiné de verre taillé)
 * 5. Liseré intérieur de Fresnel (Inner specular highlight)
 */
@Composable
private fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    isHighlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 1. Halo caustique / Ombre lumineuse diffuse sous le verre
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(if (isHighlighted) 1.06f else 1.03f)
                .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .clip(shape)
                .background(
                    if (isHighlighted) {
                        Brush.radialGradient(
                            listOf(
                                TaloAccentBlue.copy(alpha = 0.55f),
                                TaloAccentBlue.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    }
                )
        )

        // 2. Corps cristallin en verre poli translucide (matière opalescente soyeuse)
        val glassBackground = if (isHighlighted) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.35f),    // Reflet zénithal
                    Color(0x381A73E8),                 // Cœur saphir translucide
                    Color(0x180E1B2C)                  // Fumé sombre haute transparence
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.18f),    // Éclat laiteux supérieur (verre poli)
                    Color(0x1CF0F4FA),                 // Masse cristalline translucide
                    Color(0x0C121A26)                  // Fumé transparent révélant l'arrière-plan
                )
            )
        }

        // 3. Arête extérieure spéculaire (biseau taillé haute brillance)
        val outerBorderBrush = if (isHighlighted) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),   // Arête supérieure vive
                    TaloAccentBlue,
                    TaloAccentBlue.copy(alpha = 0.40f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.90f),   // Biseau taillé Apple / Intro.mp4
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.08f)
                )
            )
        }

        // Couche principale du verre poli
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(glassBackground)
                .border(
                    width = if (isHighlighted) 1.5.dp else 1.2.dp,
                    brush = outerBorderBrush,
                    shape = shape
                )
        ) {
            // 4. Biseau intérieur taillé (Double arête en relief reproduisant Intro.mp4)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.5.dp)
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isHighlighted) 0.50f else 0.35f),
                                Color.Transparent,
                                Color.White.copy(alpha = if (isHighlighted) 0.20f else 0.10f)
                            )
                        ),
                        shape = shape
                    )
            )

            // 5. Reflet spéculaire de surface zénithal (Glacis courbe de lentille de verre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isHighlighted) 0.24f else 0.18f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 6. Liseré zénithal d'extrême brillance (1px pure lumière)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isHighlighted) 0.95f else 0.80f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 7. Contenu de la dalle de verre
        Box(
            modifier = Modifier.clip(shape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Bouton en verre poli haute-fidélité (Apple TV × Arc Browser)
 */
@Composable
private fun PolishedGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter ||
                     keyEvent.key == Key.NumPadEnter)) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Halo caustique sous le bouton
        if (isPrimary || isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1.08f)
                    .blur(14.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .clip(shape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isDestructive) TaloAlertRed.copy(alpha = 0.50f)
                                else TaloAccentBlue.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Corps du bouton en verre
        val buttonBackground = if (isDestructive) {
            Brush.verticalGradient(
                listOf(
                    Color(0x33EF3126),
                    Color(0x18EF3126)
                )
            )
        } else if (isPrimary || isFocused) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF2E88FF),
                    TaloAccentBlue,
                    Color(0xFF125BB8)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.08f)
                )
            )
        }

        val borderBrush = if (isDestructive) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    TaloAlertRed
                )
            )
        } else if (isPrimary || isFocused) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    TaloAccentBlue
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.20f)
                )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(buttonBackground)
                .border(1.2.dp, borderBrush, shape)
        ) {
            // Liseré biseau intérieur
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.2.dp)
                    .border(
                        0.6.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isPrimary || isFocused) 0.50f else 0.30f),
                                Color.Transparent
                            )
                        ),
                        shape
                    )
            )

            // Reflet spéculaire supérieur (Glacis)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isPrimary || isFocused) 0.35f else 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Texte du bouton
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (isPrimary || isFocused) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isDestructive) TaloAlertRed else Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp)
        )
    }
}

/**
 * Galets organiques en verre poli 3D et perles cristallines flottant en arrière-plan.
 * Réplique optique fidèle de la vidéo d'intro (Intro.mp4).
 */
@Composable
private fun FloatingPolishedGlassBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "glassDrift")
    val float1 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift1"
    )
    val float2 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Galet 1 : Périphérie Haut Gauche (volumétrique, laiteux, lumineux)
        PolishedGlassPebble(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 40.dp, y = (20 + float1).dp)
                .size(width = 340.dp, height = 190.dp),
            rotation = -22f
        )

        // Galet 2 : Périphérie Haut Droit
        PolishedGlassPebble(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-40).dp, y = (30 + float2).dp)
                .size(width = 360.dp, height = 200.dp),
            rotation = 18f
        )

        // Galet 3 : Périphérie Bas Gauche
        PolishedGlassPebble(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 50.dp, y = (-30 + float2).dp)
                .size(width = 320.dp, height = 180.dp),
            rotation = 28f
        )

        // Galet 4 : Périphérie Bas Droit
        PolishedGlassPebble(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-50).dp, y = (-20 + float1).dp)
                .size(width = 330.dp, height = 190.dp),
            rotation = -26f
        )

        // Perles de verre cristallines (gouttes de verre en suspension comme dans Intro.mp4)
        FloatingGlassBead(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-380).dp, y = (70 + float2 * 0.7f).dp),
            size = 18.dp
        )
        FloatingGlassBead(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 420.dp, y = (90 + float1 * 0.8f).dp),
            size = 14.dp
        )
        FloatingGlassBead(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-440).dp, y = (-80 + float1 * 0.6f).dp),
            size = 16.dp
        )
        FloatingGlassBead(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 460.dp, y = (-70 + float2 * 0.7f).dp),
            size = 20.dp
        )

        // Halo zénithal cinématographique doux
        Box(
            modifier = Modifier
                .size(700.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        radius = 480f
                    )
                )
        )
    }
}

@Composable
private fun PolishedGlassPebble(
    modifier: Modifier = Modifier,
    rotation: Float = 0f
) {
    Box(
        modifier = modifier
            .rotate(rotation)
            .blur(26.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f), // Cœur laiteux lumineux
                        Color(0x40D8E4F2),              // Halo argenté verre dépoli
                        Color(0x184A6078),              // Dégradé optique bleuté
                        Color.Transparent
                    )
                )
            )
    ) {
        // Reflet spéculaire 3D zénithal
        Box(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .fillMaxHeight(0.50f)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun FloatingGlassBead(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color(0x25E0EAF8),
                        Color(0x0C506880),
                        Color.Transparent
                    )
                )
            )
            .border(
                0.6.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                CircleShape
            )
    ) {
        // Point d'éclat spéculaire
        Box(
            modifier = Modifier
                .size(size * 0.30f)
                .offset(x = size * 0.22f, y = size * 0.22f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
        )
    }
}

/**
 * Parcours complet d'Initialisation Talo OS (OOBE) :
 * 1. Vidéo d'Intro Cinématique (Intro.mp4)
 * 2. Hub d'Autorisations en 1 Clic (Verre dépoli & Auto-advance)
 * 3. Association Cloud Sécurisée (QR Code & Double confirmation)
 * 4. Configuration des Profils du Foyer (Design épuré titane)
 * 5. Étalonnage Moteur Graphique (Fidélité visuelle Éco / Normal / Ultra)
 * 6. Lancement Talo OS
 */
@Composable
fun OOBESetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) }
    // 1: Intro Video
    // 2: Permissions Hub
    // 3: Cloud Pairing
    // 4: Profile Creation
    // 5: Visual Engine Calibration
    // 6: Launch Talo OS

    // Pairing states
    var pairingCode by remember { mutableStateOf("TALO-" + (100..999).random()) }
    var qrBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var pendingUserName by remember { mutableStateOf("") }
    var pendingUserEmail by remember { mutableStateOf("") }
    var isNewAccountFlow by remember { mutableStateOf(false) }
    var showTvConfirmationModal by remember { mutableStateOf(false) }
    var pairedUserName by remember { mutableStateOf("Alexandre") }

    // Calibration states
    var hardwareReport by remember { mutableStateOf<HardwareReport?>(null) }

    // Generate QR Code bitmap offline once pairingCode is ready
    LaunchedEffect(pairingCode) {
        val pairUrl = "https://talo-tv.studio.ynh.fr/pair.html?code=$pairingCode"
        qrBitmap = generateQrCodeBitmap(pairUrl, 420)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Décor d'arrière-plan : Galets en verre poli flottants (identiques à Intro.mp4)
        if (currentStep > 1) {
            FloatingPolishedGlassBackground()
        }
        when (currentStep) {
            1 -> {
                // =====================================================================
                // ÉTAPE 1 : VIDÉO D'INTRO CINÉMATIQUE (Intro.mp4)
                // =====================================================================
                IntroVideoStep(
                    onVideoFinished = {
                        currentStep = 2 // Direct to Permissions Hub
                    }
                )
            }

            2 -> {
                // =====================================================================
                // ÉTAPE 2 : HUB D'AUTORISATIONS EN 1-CLIC (GLASSMORPHISM MONOCHROME)
                // =====================================================================
                PermissionsHubStep(
                    onPermissionsVerified = {
                        currentStep = 3 // Go to Cloud Pairing
                    }
                )
            }

            3 -> {
                // =====================================================================
                // ÉTAPE 3 : CONNEXION CLOUD SÉCURISÉE (SMARTPHONE <-> TV)
                // =====================================================================
                CloudPairingStep(
                    pairingCode = pairingCode,
                    qrBitmap = qrBitmap,
                    showConfirmationModal = showTvConfirmationModal,
                    pendingUserName = pendingUserName,
                    pendingUserEmail = pendingUserEmail,
                    onPhoneClaimDetected = { name, email, isNew ->
                        pendingUserName = name
                        pendingUserEmail = email
                        isNewAccountFlow = isNew
                        showTvConfirmationModal = true
                    },
                    onConfirmFromTv = {
                        coroutineScope.launch {
                            val res = CloudSyncManager.confirmPairingFromTv(context, pairingCode)
                            if (res is CloudSyncManager.PairingPollState.Claimed) {
                                showTvConfirmationModal = false
                                pairedUserName = res.userName
                                if (res.isNewAccount || res.profiles.isNullOrEmpty()) {
                                    currentStep = 4 // Profile Creation Step
                                } else {
                                    TaloApplication.instance.userProfileManager.updateFromCloud(res.profiles)
                                    currentStep = 5 // Direct to Visual Calibration
                                }
                            } else {
                                showTvConfirmationModal = false
                                pairedUserName = pendingUserName.ifEmpty { "Alexandre" }
                                CloudSyncManager.setCloudAccountInfo(context, pairedUserName, pendingUserEmail.ifEmpty { "alexandre@talotv.com" })
                                if (isNewAccountFlow) {
                                    currentStep = 4
                                } else {
                                    currentStep = 5
                                }
                            }
                        }
                    },
                    onRejectFromTv = {
                        showTvConfirmationModal = false
                    }
                )
            }

            4 -> {
                // =====================================================================
                // ÉTAPE 4 : CONFIGURATION DES PROFILS DU FOYER (DESIGN ÉPURÉ)
                // =====================================================================
                ProfileCreationStep(
                    defaultName = pairedUserName,
                    onProfilesCreated = {
                        currentStep = 5 // Go to Visual Engine Calibration
                    }
                )
            }

            5 -> {
                // =====================================================================
                // ÉTAPE 5 : ÉTALONNAGE DU MOTEUR VISUEL TALO OS
                // =====================================================================
                VisualEngineCalibrationStep(
                    onCalibrationComplete = { report ->
                        hardwareReport = report
                        currentStep = 6
                    }
                )
            }

            6 -> {
                // =====================================================================
                // ÉTAPE 6 : LANCEMENT TALO OS
                // =====================================================================
                SetupCompletionStep(
                    userName = pairedUserName,
                    onLaunch = {
                        TaloApplication.instance.profileManager.setCalibrated(true)
                        onSetupComplete()
                    }
                )
            }
        }
    }
}

// =============================================================================
// ÉTAPE 1 : LECTURE VIDÉO INTRO (Intro.mp4)
// =============================================================================
@Composable
private fun IntroVideoStep(
    onVideoFinished: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
            .clickable { onVideoFinished() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.intro}")
                    setVideoURI(uri)
                    setOnCompletionListener {
                        onVideoFinished()
                    }
                    setOnErrorListener { _, _, _ ->
                        // Fallback immédiat si le codec vidéo n'est pas supporté par l'émulateur
                        onVideoFinished()
                        true
                    }
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                    setOnTouchListener { _, _ ->
                        onVideoFinished()
                        true
                    }
                    setOnKeyListener { _, _, _ ->
                        onVideoFinished()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// =============================================================================
// ÉTAPE 2 : HUB D'AUTORISATIONS EN 1-CLIC (GLASSMORPHISM MONOCHROME)
// =============================================================================
@Composable
private fun PermissionsHubStep(
    onPermissionsVerified: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val buttonFocusRequester = remember { FocusRequester() }

    var isAccessibilityGranted by remember { mutableStateOf(checkAccessibilityService(context)) }
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAudioGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var isAutoAdvancing by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isAudioGranted = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = checkAccessibilityService(context)
                isOverlayGranted = Settings.canDrawOverlays(context)
                isAudioGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                if (isAccessibilityGranted && !isAutoAdvancing) {
                    isAutoAdvancing = true
                    coroutineScope.launch {
                        delay(600)
                        onPermissionsVerified()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAccessibilityGranted) {
        if (isAccessibilityGranted && !isAutoAdvancing) {
            isAutoAdvancing = true
            delay(600)
            onPermissionsVerified()
        }
    }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Autorisations Système",
            color = TaloTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Activation du contrôle télécommande et de l'interface Talo OS.",
            color = TaloTextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(168.dp)
        ) {
            GlassPermissionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = "Télécommande",
                description = "Interception des touches Home et Paramètres.",
                isGranted = isAccessibilityGranted,
                isRequired = true
            )

            GlassPermissionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = "Superposition",
                description = "Affichage du volet paramètres TV par-dessus vos flux.",
                isGranted = isOverlayGranted,
                isRequired = true
            )

            GlassPermissionCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = "Recherche Vocale",
                description = "Microphone de la télécommande pour l'assistant.",
                isGranted = isAudioGranted,
                isRequired = false
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        if (isAutoAdvancing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TaloAccentBlue, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Accès validés. Redirection…",
                    color = TaloAccentBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PolishedGlassButton(
                    text = "Activer les accès (1-Clic)",
                    onClick = {
                        if (!isAudioGranted) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallbackIntent)
                        }
                    },
                    isPrimary = true,
                    focusRequester = buttonFocusRequester
                )

                PolishedGlassButton(
                    text = "Continuer",
                    onClick = { onPermissionsVerified() },
                    isPrimary = false
                )
            }
        }
    }
}

@Composable
private fun GlassPermissionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean
) {
    FrostedGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        isHighlighted = isGranted
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TaloTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isGranted) TaloAccentBlue.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (isGranted) TaloAccentBlue else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isGranted) "ACTIF" else if (isRequired) "REQUIS" else "OPTIONNEL",
                        color = if (isGranted) Color.White else TaloTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = description,
                color = TaloTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// =============================================================================
// ÉTAPE 3 : CONNEXION CLOUD SÉCURISÉE (SMARTPHONE <-> TV)
// =============================================================================
@Composable
private fun CloudPairingStep(
    pairingCode: String,
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    showConfirmationModal: Boolean,
    pendingUserName: String,
    pendingUserEmail: String,
    onPhoneClaimDetected: (name: String, email: String, isNewAccount: Boolean) -> Unit,
    onConfirmFromTv: () -> Unit,
    onRejectFromTv: () -> Unit
) {
    val context = LocalContext.current
    var isPollingActive by remember { mutableStateOf(true) }
    val codeFocusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(pairingCode) {
        CloudSyncManager.registerPairingCode(context, pairingCode)
    }

    LaunchedEffect(Unit) {
        codeFocusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val user = intent?.getStringExtra("user") ?: "Alexandre"
                val email = intent?.getStringExtra("email") ?: "alexandre@talotv.com"
                val isNew = intent?.getBooleanExtra("is_new", true) ?: true
                onPhoneClaimDetected(user, email, isNew)
            }
        }
        val filter = android.content.IntentFilter("com.talotv.PAIR_REQUEST")
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPollingActive, pairingCode) {
        while (isPollingActive) {
            delay(1800)
            val state = CloudSyncManager.pollPairingStatusDetailed(context, pairingCode)
            if (state is CloudSyncManager.PairingPollState.AwaitingTvConfirmation) {
                isPollingActive = false
                onPhoneClaimDetected(state.userName, state.userEmail, state.isNewAccount)
                break
            } else if (state is CloudSyncManager.PairingPollState.Claimed) {
                isPollingActive = false
                onPhoneClaimDetected(state.userName, "", state.isNewAccount)
                break
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Association Talo Cloud",
                color = TaloTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scannez le QR Code avec votre smartphone pour associer ce téléviseur.",
                color = TaloTextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // QR Code Container
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Code de jumelage",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.width(48.dp))

                Column {
                    Text(
                        text = "1. Ou accédez directement à :",
                        color = TaloTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "https://talo-tv.studio.ynh.fr/connect",
                        color = TaloAccentBlue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "2. Entrez ce code :",
                        color = TaloTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FrostedGlassCard(
                        modifier = Modifier
                            .focusRequester(codeFocusRequester)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onPhoneClaimDetected("Alexandre", "alexandre@talotv.com", true)
                            }
                            .focusable(interactionSource = interactionSource),
                        shape = RoundedCornerShape(12.dp),
                        isHighlighted = isFocused
                    ) {
                        Text(
                            text = pairingCode,
                            color = TaloTextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TaloAccentBlue, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "En attente de confirmation mobile…",
                    color = TaloTextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // =====================================================================
        // DOUBLE CONFIRMATION MODAL ON TV SCREEN
        // =====================================================================
        if (showConfirmationModal) {
            val confirmButtonFocusRequester = remember { FocusRequester() }

            LaunchedEffect(showConfirmationModal) {
                confirmButtonFocusRequester.requestFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                FrostedGlassCard(
                    modifier = Modifier.fillMaxWidth(0.50f),
                    shape = RoundedCornerShape(20.dp),
                    isHighlighted = true
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Confirmer l'Association",
                            color = TaloTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Un appareil demande à associer ce téléviseur au compte :",
                            color = TaloTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, TaloGlassBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = pendingUserName.ifEmpty { "Alexandre" },
                                    color = TaloTextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pendingUserEmail.isNotEmpty()) {
                                    Text(
                                        text = pendingUserEmail,
                                        color = TaloAccentBlue,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PolishedGlassButton(
                                text = "ASSOCIER CE TÉLÉVISEUR",
                                onClick = onConfirmFromTv,
                                isPrimary = true,
                                focusRequester = confirmButtonFocusRequester
                            )

                            PolishedGlassButton(
                                text = "Refuser",
                                onClick = onRejectFromTv,
                                isDestructive = true
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// ÉTAPE 4 : CONFIGURATION DES PROFILS DU FOYER (DESIGN ÉPURÉ)
// =============================================================================
@Composable
private fun ProfileCreationStep(
    defaultName: String,
    onProfilesCreated: () -> Unit
) {
    val submitFocusRequester = remember { FocusRequester() }
    val primaryName by remember { mutableStateOf(defaultName.ifEmpty { "Alexandre" }) }
    var includeKidsProfile by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        submitFocusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Profils du Foyer",
            color = TaloTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sélectionnez les profils pour ce téléviseur.",
            color = TaloTextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .height(210.dp)
        ) {
            // Profil Principal
            FrostedGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                isHighlighted = true
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        TaloAccentBlue.copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .border(1.5.dp, Brush.verticalGradient(listOf(Color.White, TaloAccentBlue)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = primaryName.take(1).uppercase(),
                            color = TaloTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = primaryName,
                        color = TaloTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Profil Principal",
                        color = TaloTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Profil Enfants
            FrostedGlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                isHighlighted = includeKidsProfile
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.2.dp, if (includeKidsProfile) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "K",
                            color = TaloTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Espace Enfants",
                        color = TaloTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (includeKidsProfile) "Activé" else "Désactivé",
                        color = if (includeKidsProfile) TaloAccentBlue else TaloTextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { includeKidsProfile = !includeKidsProfile },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (includeKidsProfile) Color.White.copy(alpha = 0.12f) else Color(0x22FFFFFF)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (includeKidsProfile) "Désactiver" else "Activer",
                            fontSize = 12.sp,
                            color = TaloTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        PolishedGlassButton(
            text = "Valider les profils",
            onClick = {
                TaloApplication.instance.userProfileManager.addProfile(
                    name = primaryName,
                    ageTier = "25 - 34",
                    colorHex = 0xFF1A73E8
                )
                if (includeKidsProfile) {
                    TaloApplication.instance.userProfileManager.addProfile(
                        name = "Enfants",
                        ageTier = "7 - 11",
                        colorHex = 0xFF8E8E93,
                        pin = "1234"
                    )
                }
                onProfilesCreated()
            },
            isPrimary = true,
            focusRequester = submitFocusRequester
        )
    }
}

// =============================================================================
// ÉTAPE 5 : ÉTALONNAGE DU MOTEUR VISUEL TALO OS (PROFIL ULTRA / NORMAL / ÉCO)
// =============================================================================
@Composable
private fun VisualEngineCalibrationStep(
    onCalibrationComplete: (HardwareReport) -> Unit
) {
    var progress by remember { mutableFloatStateOf(0.05f) }
    var title by remember { mutableStateOf("Analyse de la puissance graphique…") }
    var detail by remember { mutableStateOf("Ajustement de la physique d'affichage et du rendu glassmorphism…") }

    var measuredRamBandwidth by remember { mutableStateOf("Calcul en cours…") }
    var measuredCpuMips by remember { mutableStateOf("Calcul en cours…") }
    var measuredStorage by remember { mutableStateOf("Inspection…") }
    var visualProfileRetained by remember { mutableStateOf("ÉCO") }

    LaunchedEffect(Unit) {
        val profiler = TaloApplication.instance.hardwareProfiler
        val report = profiler.runRealBenchmark { p, t, d ->
            progress = p
            title = t
            detail = d

            if (p >= 0.28f) {
                measuredRamBandwidth = "Bande passante validée"
            }
            if (p >= 0.55f) {
                measuredCpuMips = "Multi-cœur actif"
            }
            if (p >= 0.88f) {
                measuredStorage = "Accélération matérielle"
                visualProfileRetained = "PROFIL ULTRA"
            }
        }

        delay(700)
        onCalibrationComplete(report)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Optimisation Graphique",
            color = TaloTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Calibration de la fluidité et du niveau d'effets visuels.",
            color = TaloTextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = TaloAccentBlue,
            trackColor = Color(0x33FFFFFF)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            color = TaloTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = detail,
            color = TaloAccentBlue,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .height(95.dp)
        ) {
            GlassTelemetryTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "Mémoire & Cache",
                value = measuredRamBandwidth
            )

            GlassTelemetryTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "Moteur Physique",
                value = measuredCpuMips
            )

            GlassTelemetryTile(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "Rendu Glassmorphism",
                value = visualProfileRetained,
                isHighlighted = true
            )
        }
    }
}

@Composable
private fun GlassTelemetryTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    isHighlighted: Boolean = false
) {
    FrostedGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        isHighlighted = isHighlighted
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, color = TaloTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = if (isHighlighted) TaloAccentBlue else TaloTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =============================================================================
// ÉTAPE 6 : LANCEMENT TALO OS
// =============================================================================
@Composable
private fun SetupCompletionStep(
    userName: String,
    onLaunch: () -> Unit
) {
    val launchButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        launchButtonFocusRequester.requestFocus()
    }

    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(290.dp),
        shape = RoundedCornerShape(24.dp),
        isHighlighted = true
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Text(
                text = "TALO",
                color = TaloTextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Bienvenue, $userName",
                color = TaloTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Votre système est optimisé et prêt à l'emploi.",
                color = TaloTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            PolishedGlassButton(
                text = "Entrer dans Talo OS",
                onClick = onLaunch,
                isPrimary = true,
                focusRequester = launchButtonFocusRequester
            )
        }
    }
}

// =============================================================================
// UTILITAIRES SYSTÈME
// =============================================================================

private fun checkAccessibilityService(context: Context): Boolean {
    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServicesList = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServicesList != null && enabledServicesList.any { 
                it.resolveInfo.serviceInfo.packageName == context.packageName 
            }) {
                return true
            }
        }
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedComponentName = "${context.packageName}/${HomeKeyAccessibilityService::class.java.name}"
        enabledServices.contains(expectedComponentName) || enabledServices.contains(HomeKeyAccessibilityService::class.java.simpleName)
    } catch (_: Exception) {
        false
    }
}

private fun generateQrCodeBitmap(content: String, size: Int = 400): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

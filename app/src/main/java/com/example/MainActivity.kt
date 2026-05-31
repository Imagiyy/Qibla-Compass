package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import android.graphics.Color as AndroidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IslamicGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: QiblaSensorManager
    private lateinit var locationTracker: QiblaLocationTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = QiblaSensorManager(this)
        locationTracker = QiblaLocationTracker(this)

        setContent {
            MyApplicationTheme {
                val trueHeading by sensorManager.headingFlow.collectAsState()
                val magAccuracy by sensorManager.accuracyFlow.collectAsState()
                val locationState by locationTracker.locationFlow.collectAsState()
                val gpsEnabledState by locationTracker.isGpsEnabled.collectAsState()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("qibla_app_scaffold")
                ) { innerPadding ->
                    QiblaScreen(
                        modifier = Modifier.padding(innerPadding),
                        trueHeading = trueHeading,
                        magAccuracy = magAccuracy,
                        hasMagnetometer = sensorManager.hasMagnetometer,
                        locationState = locationState,
                        gpsEnabledState = gpsEnabledState,
                        onDeclinationChanged = { declination ->
                            sensorManager.setDeclination(declination)
                        },
                        onRefreshLocation = {
                            locationTracker.startLocationUpdates(forceRefresh = true)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.start()
        locationTracker.checkGpsStatus()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationTracker.startLocationUpdates(forceRefresh = false)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.stop()
        locationTracker.stopLocationUpdates()
    }
}

@Composable
fun QiblaScreen(
    modifier: Modifier = Modifier,
    trueHeading: Float,
    magAccuracy: Int,
    hasMagnetometer: Boolean,
    locationState: Location?,
    gpsEnabledState: Boolean,
    onDeclinationChanged: (Float) -> Unit,
    onRefreshLocation: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val hasCoordinates = locationState != null
    val userLatitude = locationState?.latitude ?: 0.0
    val userLongitude = locationState?.longitude ?: 0.0
    val userAltitude = locationState?.altitude ?: 0.0

    // Permissions and GPS states
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val isLocatingActive = locationPermissionGranted && !hasCoordinates

    // Declination and Qibla Bearing variables
    val magneticDeclination = remember(userLatitude, userLongitude, userAltitude, hasCoordinates) {
        if (hasCoordinates) {
            QiblaMath.getMagneticDeclination(userLatitude, userLongitude, userAltitude)
        } else {
            0f
        }
    }

    LaunchedEffect(magneticDeclination) {
        onDeclinationChanged(magneticDeclination)
    }

    val qiblaBearing = remember(userLatitude, userLongitude, hasCoordinates) {
        if (hasCoordinates) {
            QiblaMath.calculateQiblaBearing(userLatitude, userLongitude)
        } else {
            0.0
        }
    }
    val distanceToKaaba = remember(userLatitude, userLongitude, hasCoordinates) {
        if (hasCoordinates) {
            QiblaMath.calculateDistanceToKaaba(userLatitude, userLongitude)
        } else {
            0.0
        }
    }

    // Shortest path angle accumulation to prevent backward spin jitter on degree wraps (359 -> 0)
    var lastHeadingRaw by remember { mutableFloatStateOf(0f) }
    var accumulatedHeading by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trueHeading) {
        val delta = trueHeading - lastHeadingRaw
        var shortestDelta = delta
        if (shortestDelta < -180f) {
            shortestDelta += 360f
        } else if (shortestDelta > 180f) {
            shortestDelta -= 360f
        }
        if (!shortestDelta.isNaN() && !shortestDelta.isInfinite()) {
            accumulatedHeading += shortestDelta
            lastHeadingRaw = trueHeading
        }
    }

    // Smooth Compose spring animator for fluidity
    val animatedHeadingState by animateFloatAsState(
        targetValue = accumulatedHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "compass_rotation_anim"
    )

    // Calculate relative Qibla angle from the top of the handset
    val relativeQiblaAngle = remember(qiblaBearing, trueHeading) {
        (qiblaBearing - trueHeading + 360.0) % 360.0
    }

    // Align checklist (When user is pointing precisely to Kaaba within ±2 degrees)
    val isAligned = remember(relativeQiblaAngle) {
        relativeQiblaAngle < 2.0 || relativeQiblaAngle > 358.0
    }

    // De-jittered haptic feedback mechanism to prevent continuous vibrating at alignment boundaries
    var lastHapticVibeTime by remember { mutableLongStateOf(0L) }
    var wasVibratedInCurrentLock by remember { mutableStateOf(false) }

    LaunchedEffect(isAligned) {
        val now = System.currentTimeMillis()
        if (isAligned && hasCoordinates) {
            if (!wasVibratedInCurrentLock && (now - lastHapticVibeTime > 1500L)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                lastHapticVibeTime = now
                wasVibratedInCurrentLock = true
            }
        } else {
            wasVibratedInCurrentLock = false
        }
    }

    // Permission Request Handler
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            locationPermissionGranted = fineGranted || coarseGranted
            if (locationPermissionGranted) {
                onRefreshLocation()
            } else {
                Toast.makeText(
                    context,
                    "Location permission is required to calculate Qibla direction.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    // Auto-prompt permission exactly once safely across recreations
    var hasPromptedForPermission by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted && !hasPromptedForPermission) {
            hasPromptedForPermission = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // UI Structure
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title and header
        Text(
            text = "QIBLA COMPASS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = GoldPrimary,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp).testTag("qibla_title")
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (hasCoordinates) IslamicGreen else Color.Red)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLocatingActive) "FETCHING CURRENT POSITION..."
                       else if (hasCoordinates) "CURRENT POSITION DETECTED"
                       else "GPS PERMISSION REQUIRED",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Premium Compass Rendering Frame
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            // Active outer pulsing glow rings when pointing accurately aligning to Mecca
            if (isAligned && hasCoordinates) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "p_scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "p_alpha"
                )

                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .border(
                            width = 2.dp,
                            color = IslamicGreen.copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    IslamicGreen.copy(alpha = pulseAlpha * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Central rotating custom Canvas Compass dial
            Canvas(
                modifier = Modifier
                    .size(280.dp)
                    .testTag("compass_canvas")
            ) {
                val centerOffset = size.minDimension / 2f
                val centerPt = Offset(centerOffset, centerOffset)
                val dialRadius = centerOffset

                // ROTATE COMPASS DIAL TO MATCH PHONE ROTATION (Counter True-North Angle)
                rotate(degrees = -animatedHeadingState, pivot = centerPt) {
                    
                    // Draw dial disk base
                    drawCircle(
                        color = SurfaceCard,
                        radius = dialRadius - 4.dp.toPx(),
                        style = Fill
                    )
                    drawCircle(
                        color = SlateMuted.copy(alpha = 0.6f),
                        radius = dialRadius,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw divisions ticks around the circular dial
                    for (degree in 0 until 360 step 5) {
                        val isMajor = degree % 30 == 0
                        val isQuarter = degree % 90 == 0
                        
                        val tickLength = if (isQuarter) 16.dp.toPx() else if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                        val tickWidth = if (isQuarter) 2.5f.dp.toPx() else if (isMajor) 1.5f.dp.toPx() else 0.75f.dp.toPx()
                        val tickColor = if (isQuarter) GoldPrimary else if (isMajor) GoldPrimary.copy(alpha = 0.6f) else SlateMuted

                        val rad = Math.toRadians(degree.toDouble())
                        val startX = centerPt.x + (dialRadius - tickLength) * kotlin.math.sin(rad).toFloat()
                        val startY = centerPt.y - (dialRadius - tickLength) * kotlin.math.cos(rad).toFloat()
                        val endX = centerPt.x + dialRadius * kotlin.math.sin(rad).toFloat()
                        val endY = centerPt.y - dialRadius * kotlin.math.cos(rad).toFloat()

                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = tickWidth
                        )
                    }

                    // Draw Cardinal text points: N, E, S, W
                    val labels = listOf(
                        Pair("N", 0),
                        Pair("E", 90),
                        Pair("S", 180),
                        Pair("W", 270)
                    )
                    
                    labels.forEach { (text, degree) ->
                        val rad = Math.toRadians(degree.toDouble())
                        val dMargin = 22.dp.toPx()
                        val textX = centerPt.x + (dialRadius - dMargin) * kotlin.math.sin(rad).toFloat()
                        val textY = centerPt.y - (dialRadius - dMargin) * kotlin.math.cos(rad).toFloat()

                        val paint = Paint().apply {
                            color = if (text == "N") AndroidColor.RED else GoldPrimary.toArgb()
                            textSize = 15.sp.toPx()
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                        }
                        
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                text,
                                textX,
                                textY + paint.textSize / 3f,
                                paint
                            )
                        }
                    }

                    // Draw the physical Kaaba positioning indicator lock inside the dial ring
                    if (hasCoordinates) {
                        val qiblaRadian = Math.toRadians(qiblaBearing)
                        val iconRadius = dialRadius - 48.dp.toPx()
                        val iconX = centerPt.x + iconRadius * kotlin.math.sin(qiblaRadian).toFloat()
                        val iconY = centerPt.y - iconRadius * kotlin.math.cos(qiblaRadian).toFloat()

                        val paintEm = Paint().apply {
                            textSize = 22.sp.toPx()
                            textAlign = Paint.Align.CENTER
                        }

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "🕋",
                                iconX,
                                iconY + paintEm.textSize / 3f,
                                paintEm
                            )
                        }

                        // Connect Mecca marker to dial center with a subtle gold laser dot line
                        val dotRadius = iconRadius - 16.dp.toPx()
                        for (dist in 10..dotRadius.toInt() step 12) {
                            val dotX = centerPt.x + dist * kotlin.math.sin(qiblaRadian).toFloat()
                            val dotY = centerPt.y - dist * kotlin.math.cos(qiblaRadian).toFloat()
                            drawCircle(
                                color = GoldPrimary.copy(alpha = 0.35f),
                                radius = 1.5f.dp.toPx(),
                                center = Offset(dotX, dotY)
                            )
                        }
                    }
                }

                // ROTATING PRECISE GOLDEN COMPASS ARROW NEEDLE pointing relative to Mecca
                if (hasCoordinates) {
                    val needleAngle = relativeQiblaAngle.toFloat()
                    rotate(degrees = needleAngle, pivot = centerPt) {
                        
                        val arrowLength = dialRadius - 38.dp.toPx()
                        val arrowPath = Path().apply {
                            moveTo(centerPt.x, centerPt.y - arrowLength) // Needle Arrowhead Tip
                            lineTo(centerPt.x - 12.dp.toPx(), centerPt.y - arrowLength + 24.dp.toPx()) // Left point
                            lineTo(centerPt.x - 4.dp.toPx(), centerPt.y - arrowLength + 19.dp.toPx()) // Inner left notch
                            lineTo(centerPt.x - 3.dp.toPx(), centerPt.y + 12.dp.toPx()) // Base shaft left
                            lineTo(centerPt.x + 3.dp.toPx(), centerPt.y + 12.dp.toPx()) // Base shaft right
                            lineTo(centerPt.x + 4.dp.toPx(), centerPt.y - arrowLength + 19.dp.toPx()) // Inner right notch
                            lineTo(centerPt.x + 12.dp.toPx(), centerPt.y - arrowLength + 24.dp.toPx()) // Right point
                            close()
                        }

                        val needleColor = if (isAligned) IslamicGreen else GoldPrimary

                        // 3D Shadow depth offsetting
                        drawPath(
                            path = arrowPath,
                            color = Color.Black.copy(alpha = 0.45f),
                            style = Fill
                        )
                        // Foreground glossy color layout
                        drawPath(
                            path = arrowPath,
                            color = needleColor,
                            style = Fill
                        )

                        // Split glossy highlight facet
                        val highlightPath = Path().apply {
                            moveTo(centerPt.x, centerPt.y - arrowLength)
                            lineTo(centerPt.x - 12.dp.toPx(), centerPt.y - arrowLength + 24.dp.toPx())
                            lineTo(centerPt.x - 4.dp.toPx(), centerPt.y - arrowLength + 19.dp.toPx())
                            lineTo(centerPt.x, centerPt.y - arrowLength + 17.dp.toPx())
                            close()
                        }
                        
                        drawPath(
                            path = highlightPath,
                            color = Color.White.copy(alpha = 0.2f),
                            style = Fill
                        )
                    }
                }

                // Center brass cap pivot cover
                drawCircle(
                    color = SlateMuted,
                    radius = 20.dp.toPx(),
                    center = centerPt,
                    style = Fill
                )
                drawCircle(
                    color = if (isAligned && hasCoordinates) IslamicGreen else GoldAccent,
                    radius = 12.dp.toPx(),
                    center = centerPt,
                    style = Fill
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 6.dp.toPx(),
                    center = centerPt,
                    style = Fill
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Aligned Glowing HUD Notification Label
        AnimatedVisibility(
            visible = isAligned && hasCoordinates,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.height(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(IslamicGreen.copy(alpha = 0.2f))
                    .border(1.dp, IslamicGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("align_hud_badge")
            ) {
                Text(
                    text = "🕋 ALIGNED WITH KAABA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IslamicGreen,
                    letterSpacing = 1.sp
                )
            }
        }

        if (!isAligned || !hasCoordinates) {
            Spacer(modifier = Modifier.height(36.dp))
        }

        // 1. Missing Magnetic Sensor Hardware Warning Banner
        if (!hasMagnetometer) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF3333)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF3333))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "HARDWARE COMPASS MISSING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3333)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your device lacks a magnetic sensor (magnetometer) required to rotate the dial dynamically. Bearing text calculations will still render below.",
                            fontSize = 11.sp,
                            color = TextLight,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 2. GPS Settings Disabled Prompt Warning Banner
        AnimatedVisibility(
            visible = locationPermissionGranted && !gpsEnabledState,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x22FF9900)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9900))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📡",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "GPS SERVICES DISABLED",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9900)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Your system's GPS / Location Services are turned off. Please toggle them on to retrieve coordinates.",
                                fontSize = 11.sp,
                                color = TextLight,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to open settings. Please enable GPS manually.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9900)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ENABLE GPS IN SYSTEM SETTINGS", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Magnetometer Calibration Alert Banner
        AnimatedVisibility(
            visible = hasMagnetometer && magAccuracy <= 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x22FF3333)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF4444))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔄",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "COMPASS CALIBRATION REQUIRED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5555)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "High magnetic interference detected. Please wave your phone in a figure-8 motion to calibrate the sensor.",
                            fontSize = 11.sp,
                            color = TextLight,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Precision Telemetry Metadata Dashboard Info Cards
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("telemetry_card"),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE TELEMETRY HUD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 1.5.sp
                    )
                    
                    IconButton(
                        onClick = onRefreshLocation,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Location",
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (hasCoordinates) {
                    // Qibla direction details
                    TelemetryRow(
                        label = "Mecca Bearing",
                        value = String.format("%.2f°", qiblaBearing) + " (${getCardinalDirection(qiblaBearing)})"
                    )
                    
                    TelemetryRow(
                        label = "Distance to Mecca",
                        value = String.format("%,.0f km", distanceToKaaba)
                    )

                    // Compass True North heading detail explanation
                    val phoneHeadingRounded = (trueHeading + 360f) % 360f
                    TelemetryRow(
                        label = "Device Heading",
                        value = String.format("%.1f°", phoneHeadingRounded) + " (${getCardinalDirection(phoneHeadingRounded.toDouble())})"
                    )

                    TelemetryRow(
                        label = "Magnetic Declination",
                        value = String.format("%+.2f°", magneticDeclination) + " (" + (if (magneticDeclination >= 0) "East" else "West") + ")"
                    )

                    TelemetryRow(
                        label = "User Position",
                        value = String.format("%.4f°, %.4f°", userLatitude, userLongitude)
                    )

                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "info_icon",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Waiting for coordinates. Please authorize GPS location permission to track your position in real-time.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            lineHeight = 18.sp
                        )
                    }
                    
                    if (!locationPermissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "GRANT LOCATION PERMISSION",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = SlateMuted
                )

                // Calibration feedback matching Requirement 3 sensor fusion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Magnetometer Health",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    val (calibLabel, calibColor) = when {
                        !hasMagnetometer -> Pair("HARDWARE ABSENT", Color.Red)
                        magAccuracy == 3 -> Pair("HIGHLY ACCURATE", IslamicGreen)
                        magAccuracy == 2 -> Pair("MEDIUM ACCURACY", GoldPrimary)
                        magAccuracy == 1 -> Pair("LOW ACCURACY", GoldAccent)
                        else -> Pair("UNRELIABLE / RECALIB", Color.Red)
                    }

                    Text(
                        text = calibLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = calibColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Geomagnetic math disclosure text explaining magnetic declination risk correction (Requirement 4)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "⚙️ HOW DECLINATION CORRECTION WORKS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "A regular compass points to Magnetic North, which varies based on geography. To achieve high accuracy, this app retrieves your position and applies the NOAA World Magnetic Model declination vector (via GeomagneticField) to compute True North. Bearing calculations are determined using double-precision great-circle spherical cotangents.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }

        Text(
            text = "Developed by Abrar M",
            fontSize = 11.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TelemetryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextMuted,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = TextLight,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Converts angle to cardinal direction defensively
fun getCardinalDirection(angle: Double): String {
    if (angle.isNaN() || angle.isInfinite()) return "N"
    val normalizedAngle = ((angle % 360.0) + 360.0) % 360.0
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val index = ((normalizedAngle / 45.0) + 0.5).toInt()
    return directions[index.coerceIn(0, 8)]
}

@Preview(showBackground = true)
@Composable
fun QiblaScreenPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.background(DarkBackground).fillMaxSize()) {
            QiblaScreen(
                trueHeading = 45f,
                magAccuracy = 3,
                hasMagnetometer = true,
                locationState = Location("gps").apply {
                    latitude = 21.4225
                    longitude = 39.8262
                    altitude = 0.0
                },
                gpsEnabledState = true,
                onDeclinationChanged = {},
                onRefreshLocation = {}
            )
        }
    }
}

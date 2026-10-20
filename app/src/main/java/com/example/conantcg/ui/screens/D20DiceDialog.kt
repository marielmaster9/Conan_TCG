
package com.example.conantcg.ui.screens

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

private val D20Bg = Color(0xFF0A1120)
private val D20Surface = Color(0xFF14213A)
private val D20Ivory = Color(0xFFFFF5DD)
private val D20Gold = Color(0xFFF3CD86)
private val D20Dim = Color(0xFF9EB1D1)
private val D20Accent = Color(0xFFDC3F58)

@Composable
fun D20DiceDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val motion = remember { Animatable(1f) }
    val recent = remember { mutableStateListOf<Int>() }

    var rolling by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var startOrientation by remember {
        mutableStateOf(d20FaceToCamera(20))
    }
    var finalOrientation by remember {
        mutableStateOf(d20FaceToCamera(20))
    }

    fun rollDice() {
        if (rolling) return

        val next = Random.nextInt(1, 21)
        startOrientation = finalOrientation

        val twist = Random.nextFloat() * (2f * PI.toFloat())
        finalOrientation = d20FaceToCamera(next, twist)

        result = null
        rolling = true

        scope.launch {
            try {
                motion.snapTo(0f)
                motion.animateTo(
                    1f,
                    tween(
                        durationMillis = 1550,
                        easing = FastOutSlowInEasing
                    )
                )

                result = next
                recent.add(0, next)

                if (recent.size > 5) {
                    recent.removeAt(recent.lastIndex)
                }
            } finally {
                rolling = false
            }
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp
    val smallScreen = screenHeight < 710

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 450.dp)
                .heightIn(max = (screenHeight * 0.93f).dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.13f)
            ),
            colors = CardDefaults.cardColors(
                containerColor = D20Bg
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 12.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF17284B),
                                Color(0xFF0C1730),
                                D20Bg
                            )
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        D20Accent,
                                        Color(0xFF9D2543)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(13.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Casino,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "CONAN TCG  /  TABLETOP",
                            color = D20Gold,
                            fontSize = 9.sp,
                            letterSpacing = 1.25.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "D20 Dice Roller",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(35.dp)
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(alpha = 0.08f)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dice roller",
                            tint = Color.White.copy(alpha = 0.86f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(15.dp))

                // DICE STAGE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            if (smallScreen) 222.dp else 264.dp
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF244B85),
                                    Color(0xFF122847),
                                    Color(0xFF0B1830)
                                ),
                                radius = 750f
                            )
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                Color(0xFF6983B7)
                                    .copy(alpha = 0.32f)
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable(
                            enabled = !rolling,
                            role = Role.Button
                        ) {
                            rollDice()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    D20DiceCanvas(
                        progress = motion.value,
                        rolling = rolling,
                        from = startOrientation,
                        to = finalOrientation,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (rolling) D20Accent
                                    else Color(0xFF80E4BA),
                                    CircleShape
                                )
                        )

                        Spacer(Modifier.width(7.dp))

                        Text(
                            if (rolling) "ROLLING"
                            else "READY TO ROLL",
                            color = D20Ivory.copy(alpha = 0.86f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )
                    }

                    Text(
                        "D20  /  20 FACES",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(13.dp),
                        color = D20Gold.copy(alpha = 0.78f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )

                    Text(
                        if (rolling) "SPINNING..."
                        else "TAP DICE TO ROLL",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        color = D20Dim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp
                    )
                }

                Spacer(Modifier.height(13.dp))

                // RESULT CARD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(D20Surface)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "LAST RESULT",
                            color = D20Dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            when {
                                rolling -> "Rolling the D20..."
                                result == 20 -> "NATURAL 20"
                                result == 1 -> "NATURAL 1"
                                result != null -> "Roll complete"
                                else -> "Ready when you are"
                            },
                            color = if (result == 20 && !rolling)
                                D20Gold else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 57.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (result == 20 && !rolling)
                                    Color(0xFF4B3927)
                                else if (result == 1 && !rolling)
                                    Color(0xFF4A2734)
                                else Color(0xFF243757)
                            )
                            .border(
                                1.dp,
                                if (result == 20 && !rolling)
                                    D20Gold.copy(alpha = 0.78f)
                                else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(13.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (rolling) "···"
                            else result?.toString() ?: "—",
                            color = if (result == 20 && !rolling)
                                D20Gold else D20Ivory,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(13.dp))

                // ROLL BUTTON
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (rolling) {
                                    listOf(
                                        Color(0xFF6C3B4C),
                                        Color(0xFF64344B)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFEE5168),
                                        Color(0xFFC93253)
                                    )
                                }
                            )
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(
                            enabled = !rolling,
                            role = Role.Button
                        ) {
                            rollDice()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (rolling) Icons.Default.Casino
                            else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(Modifier.width(9.dp))

                        Text(
                            if (rolling) "ROLLING..."
                            else "ROLL D20",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ROLL HISTORY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RECENT ROLLS",
                        color = D20Dim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        "UNIFORM 1–20",
                        color = D20Gold.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(9.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { i ->
                        val n = recent.getOrNull(i)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(39.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF192943))
                                .border(
                                    1.dp,
                                    if (n == 20)
                                        D20Gold.copy(alpha = 0.7f)
                                    else Color.White.copy(alpha = 0.11f),
                                    RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                n?.toString() ?: "–",
                                fontSize = 15.sp,
                                color = if (n == 20) D20Gold
                                else if (n == null)
                                    D20Dim.copy(alpha = 0.55f)
                                else Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

// 3D DICE CANVAS
@Composable
private fun D20DiceCanvas(
    progress: Float,
    rolling: Boolean,
    from: D20Rotation,
    to: D20Rotation,
    modifier: Modifier = Modifier
) {
    val fillPaint = remember {
        NativePaint(
            NativePaint.ANTI_ALIAS_FLAG or
                    NativePaint.SUBPIXEL_TEXT_FLAG
        ).apply {
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            textAlign = NativePaint.Align.CENTER
            style = NativePaint.Style.FILL
            color = android.graphics.Color.rgb(
                255, 247, 224
            )
        }
    }

    val outlinePaint = remember {
        NativePaint(
            NativePaint.ANTI_ALIAS_FLAG or
                    NativePaint.SUBPIXEL_TEXT_FLAG
        ).apply {
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            textAlign = NativePaint.Align.CENTER
            style = NativePaint.Style.STROKE
            strokeJoin = NativePaint.Join.ROUND
            color = android.graphics.Color.rgb(
                7, 19, 38
            )
        }
    }

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val center = Offset(
            size.width / 2f,
            size.height * 0.495f
        )
        val outerRadius = side * 0.46f

        // BACKGROUND HALO
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x554E92E2),
                    Color(0x22316BAE),
                    Color.Transparent
                ),
                center = center,
                radius = side * 0.52f
            ),
            center = center,
            radius = side * 0.52f
        )

        drawCircle(
            Color.White.copy(alpha = 0.11f),
            radius = outerRadius,
            center = center,
            style = Stroke(1.dp.toPx())
        )

        drawCircle(
            D20Gold.copy(alpha = 0.09f),
            radius = outerRadius * 0.85f,
            center = center,
            style = Stroke(1.dp.toPx())
        )

        repeat(16) { i ->
            val theta = i * (
                    2f * PI.toFloat() / 16f
                    )
            val x = cos(theta)
            val y = sin(theta)

            drawLine(
                color = D20Gold.copy(
                    alpha = if (i % 4 == 0) 0.32f
                    else 0.13f
                ),
                start = center + Offset(
                    x * outerRadius * 0.965f,
                    y * outerRadius * 0.965f
                ),
                end = center + Offset(
                    x * outerRadius * 1.03f,
                    y * outerRadius * 1.03f
                ),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // SHADOW
        drawOval(
            color = Color.Black.copy(alpha = 0.29f),
            topLeft = Offset(
                center.x - side * 0.285f,
                center.y + side * 0.325f
            ),
            size = Size(
                side * 0.57f,
                side * 0.09f
            )
        )

        val p = progress.coerceIn(0f, 1f)
        val orientation =
            if (!rolling || p >= 1f) to
            else d20AnimatedRotation(from, to, p)

        val scale = side * 0.256f

        val corners = D20_POINTS.map { point ->
            val v = orientation.rotate(point)
            val perspective = 6.1f / (6.1f - v.z)

            D20ScreenPoint(
                Offset(
                    center.x + v.x * scale * perspective,
                    center.y - v.y * scale * perspective
                ),
                v.z
            )
        }

        val light = D20Vec(
            -0.55f, 0.68f, 0.79f
        ).unit()

        val visible = D20_TRIANGLES.mapIndexedNotNull {
                index, triangle ->

            val normal = orientation.rotate(
                D20_FACE_NORMALS[index]
            )

            if (normal.z <= 0.005f) {
                return@mapIndexedNotNull null
            }

            val a = corners[triangle.a]
            val b = corners[triangle.b]
            val c = corners[triangle.c]

            D20RenderedFace(
                number = triangle.number,
                vertices = listOf(
                    a.position,
                    b.position,
                    c.position
                ),
                depth = (a.z + b.z + c.z) / 3f,
                normal = normal
            )
        }.sortedBy { it.depth }

        // DRAW TRIANGULAR FACES
        for (face in visible) {
            val a = face.vertices[0]
            val b = face.vertices[1]
            val c = face.vertices[2]

            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
                close()
            }

            val lit = (
                    (face.normal.dot(light) + 1f) / 2f
                    ).coerceIn(0f, 1f)

            val front = face.normal.z.coerceIn(0f, 1f)

            val blueLight = Color(
                red = 0.09f + lit * 0.11f,
                green = 0.21f + lit * 0.19f,
                blue = 0.41f + lit * 0.30f
            )

            val blueShade = Color(
                red = 0.035f + lit * 0.065f,
                green = 0.095f + lit * 0.11f,
                blue = 0.22f + lit * 0.19f
            )

            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        blueLight,
                        blueShade
                    ),
                    start = Offset(
                        a.x - side * 0.08f,
                        a.y - side * 0.10f
                    ),
                    end = Offset(
                        c.x + side * 0.04f,
                        c.y + side * 0.10f
                    )
                )
            )

            // DARK SEAM
            drawPath(
                path,
                color = Color(0xFF07152D),
                style = Stroke(
                    width = 2.8.dp.toPx(),
                    join = StrokeJoin.Round
                )
            )

            // GOLDEN EDGE
            drawPath(
                path,
                color = D20Gold.copy(
                    alpha = 0.31f +
                            lit * 0.28f +
                            front * 0.12f
                ),
                style = Stroke(
                    width = 1.dp.toPx(),
                    join = StrokeJoin.Round
                )
            )
        }

        // DRAW UPRIGHT NUMBERS
        for (face in visible) {
            val front = face.normal.z

            if (front < 0.52f) continue

            val a = face.vertices[0]
            val b = face.vertices[1]
            val c = face.vertices[2]

            val minEdge = min(
                (a - b).getDistance(),
                min(
                    (b - c).getDistance(),
                    (c - a).getDistance()
                )
            )

            if (minEdge < 20.dp.toPx()) continue

            val middle = Offset(
                (a.x + b.x + c.x) / 3f,
                (a.y + b.y + c.y) / 3f
            )

            drawCrispFaceNumber(
                value = face.number,
                position = middle,
                edgePixels = minEdge,
                frontal = front,
                fill = fillPaint,
                outline = outlinePaint
            )
        }
    }
}

// FONT AND NUMBERS
private fun DrawScope.drawCrispFaceNumber(
    value: Int,
    position: Offset,
    edgePixels: Float,
    frontal: Float,
    fill: NativePaint,
    outline: NativePaint
) {
    val text = value.toString()
    val maxTextWidth = edgePixels * 0.67f

    var fontPx = edgePixels * (
            if (frontal > 0.94f) 0.39f
            else 0.31f
            )

    fontPx = fontPx.coerceIn(
        11.sp.toPx(),
        39.sp.toPx()
    )

    fill.textSize = fontPx

    val measured = fill.measureText(text)

    if (measured > maxTextWidth && measured > 0f) {
        fontPx *= maxTextWidth / measured
    }

    fill.textSize = fontPx
    outline.textSize = fontPx

    outline.strokeWidth = (
            fontPx * 0.055f
            ).coerceIn(
            1f,
            3.1.dp.toPx()
        )

    val labelAlpha =
        if (frontal > 0.93f) 255
        else (190f + 65f * frontal)
            .toInt()
            .coerceIn(0, 255)

    fill.alpha = labelAlpha
    outline.alpha = labelAlpha

    val baseline = position.y -
            (fill.ascent() + fill.descent()) / 2f

    val canvas = drawContext.canvas.nativeCanvas

    canvas.drawText(
        text,
        position.x,
        baseline,
        outline
    )

    canvas.drawText(
        text,
        position.x,
        baseline,
        fill
    )
}

// ROTATION ANIMATION
private fun d20AnimatedRotation(
    from: D20Rotation,
    to: D20Rotation,
    progress: Float
): D20Rotation {
    val handoff = 0.67f

    fun spinning(p: Float): D20Rotation {
        val x = D20Rotation.axisAngle(
            D20Vec(1f, 0f, 0f),
            p * 7f * PI.toFloat()
        )
        val y = D20Rotation.axisAngle(
            D20Vec(0f, 1f, 0f),
            p * 9f * PI.toFloat()
        )
        val z = D20Rotation.axisAngle(
            D20Vec(0f, 0f, 1f),
            p * 2f * PI.toFloat()
        )

        return (z * y * x * from).unit()
    }

    if (progress < handoff) {
        return spinning(progress)
    }

    val t = (
            (progress - handoff) / (1f - handoff)
            ).coerceIn(0f, 1f)

    val eased = t * t * (3f - 2f * t)

    return D20Rotation.slerp(
        spinning(handoff),
        to,
        eased
    )
}

// 3D VECTOR
private data class D20Vec(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(v: D20Vec) = D20Vec(
        x + v.x, y + v.y, z + v.z
    )

    operator fun minus(v: D20Vec) = D20Vec(
        x - v.x, y - v.y, z - v.z
    )

    operator fun times(f: Float) = D20Vec(
        x * f, y * f, z * f
    )

    fun dot(v: D20Vec) =
        x * v.x + y * v.y + z * v.z

    fun cross(v: D20Vec) = D20Vec(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun unit(): D20Vec {
        val length = sqrt(dot(this))
        return if (length < 0.000001f)
            D20Vec(0f, 0f, 1f)
        else this * (1f / length)
    }
}

// 3D QUATERNION
private data class D20Rotation(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun times(q: D20Rotation) = D20Rotation(
        w * q.w - x * q.x - y * q.y - z * q.z,
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w
    )

    fun unit(): D20Rotation {
        val l = sqrt(
            w * w + x * x + y * y + z * z
        )

        return if (l < 0.000001f) {
            D20Rotation(1f, 0f, 0f, 0f)
        } else {
            D20Rotation(
                w / l,
                x / l,
                y / l,
                z / l
            )
        }
    }

    fun rotate(v: D20Vec): D20Vec {
        val u = D20Vec(x, y, z)
        val t = u.cross(v) * 2f
        return v + t * w + u.cross(t)
    }

    companion object {
        fun axisAngle(
            axis: D20Vec,
            angle: Float
        ): D20Rotation {
            val v = axis.unit()
            val half = angle / 2f
            val s = sin(half)

            return D20Rotation(
                cos(half),
                v.x * s,
                v.y * s,
                v.z * s
            )
        }

        fun fromTo(
            from: D20Vec,
            to: D20Vec
        ): D20Rotation {
            val a = from.unit()
            val b = to.unit()
            val d = a.dot(b)

            if (d < -0.9999f) {
                val aux = if (abs(a.x) < 0.8f)
                    D20Vec(1f, 0f, 0f)
                else D20Vec(0f, 1f, 0f)

                return axisAngle(
                    a.cross(aux),
                    PI.toFloat()
                )
            }

            val c = a.cross(b)

            return D20Rotation(
                1f + d,
                c.x,
                c.y,
                c.z
            ).unit()
        }

        fun slerp(
            start: D20Rotation,
            end: D20Rotation,
            progress: Float
        ): D20Rotation {
            val t = progress.coerceIn(0f, 1f)
            var b = end

            var dot =
                start.w * b.w +
                        start.x * b.x +
                        start.y * b.y +
                        start.z * b.z

            if (dot < 0f) {
                dot = -dot
                b = D20Rotation(
                    -b.w, -b.x, -b.y, -b.z
                )
            }

            if (dot > 0.9995f) {
                return D20Rotation(
                    start.w + (b.w - start.w) * t,
                    start.x + (b.x - start.x) * t,
                    start.y + (b.y - start.y) * t,
                    start.z + (b.z - start.z) * t
                ).unit()
            }

            val theta = acos(
                dot.coerceIn(-1f, 1f)
            )
            val sine = sin(theta)
            val wa = sin(
                (1f - t) * theta
            ) / sine
            val wb = sin(
                t * theta
            ) / sine

            return D20Rotation(
                start.w * wa + b.w * wb,
                start.x * wa + b.x * wb,
                start.y * wa + b.y * wb,
                start.z * wa + b.z * wb
            ).unit()
        }
    }
}

// D20 GEOMETRY
private data class D20Triangle(
    val a: Int,
    val b: Int,
    val c: Int,
    val number: Int
)

private data class D20ScreenPoint(
    val position: Offset,
    val z: Float
)

private data class D20RenderedFace(
    val number: Int,
    val vertices: List<Offset>,
    val depth: Float,
    val normal: D20Vec
)

private val D20_POINTS: List<D20Vec> = run {
    val phi = (
            (1.0 + sqrt(5.0)) / 2.0
            ).toFloat()

    listOf(
        D20Vec(-1f, phi, 0f),
        D20Vec(1f, phi, 0f),
        D20Vec(-1f, -phi, 0f),
        D20Vec(1f, -phi, 0f),
        D20Vec(0f, -1f, phi),
        D20Vec(0f, 1f, phi),
        D20Vec(0f, -1f, -phi),
        D20Vec(0f, 1f, -phi),
        D20Vec(phi, 0f, -1f),
        D20Vec(phi, 0f, 1f),
        D20Vec(-phi, 0f, -1f),
        D20Vec(-phi, 0f, 1f)
    )
}

// 20 triangular faces
private val D20_TRIANGLES = listOf(
    D20Triangle(0, 11, 5, 20),
    D20Triangle(0, 5, 1, 5),
    D20Triangle(0, 1, 7, 17),
    D20Triangle(0, 7, 10, 9),
    D20Triangle(0, 10, 11, 14),
    D20Triangle(1, 5, 9, 2),
    D20Triangle(5, 11, 4, 18),
    D20Triangle(11, 10, 2, 13),
    D20Triangle(10, 7, 6, 11),
    D20Triangle(7, 1, 8, 6),
    D20Triangle(3, 9, 4, 12),
    D20Triangle(3, 4, 2, 4),
    D20Triangle(3, 2, 6, 16),
    D20Triangle(3, 6, 8, 1),
    D20Triangle(3, 8, 9, 7),
    D20Triangle(4, 9, 5, 10),
    D20Triangle(2, 4, 11, 15),
    D20Triangle(6, 2, 10, 19),
    D20Triangle(8, 6, 7, 3),
    D20Triangle(9, 8, 1, 8)
)

private val D20_FACE_NORMALS =
    D20_TRIANGLES.map { face ->
        val a = D20_POINTS[face.a]
        val b = D20_POINTS[face.b]
        val c = D20_POINTS[face.c]

        (b - a).cross(c - a).unit()
    }

// ROTATE SELECTED FACE TOWARD CAMERA
private fun d20FaceToCamera(
    number: Int,
    twist: Float = 0f
): D20Rotation {
    val faceIndex = D20_TRIANGLES
        .indexOfFirst { it.number == number }
        .coerceAtLeast(0)

    val align = D20Rotation.fromTo(
        D20_FACE_NORMALS[faceIndex],
        D20Vec(0f, 0f, 1f)
    )

    return (
            D20Rotation.axisAngle(
                D20Vec(0f, 0f, 1f),
                twist
            ) * align
            ).unit()
}

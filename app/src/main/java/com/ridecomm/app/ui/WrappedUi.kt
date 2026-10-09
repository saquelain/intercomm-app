package com.ridecomm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import com.ridecomm.app.score.Score
import com.ridecomm.app.score.ScoreBook
import com.ridecomm.app.score.WrappedLogic
import com.ridecomm.app.score.WrappedYear
import com.ridecomm.app.trip.RideHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun n(v: Number) = NumberFormat.getIntegerInstance(Locale.US).format(v.toLong())

/** Each slide's colours: deep night into a bright glow. */
private val SLIDE_COLOURS = listOf(
    listOf(Color(0xFF2B1C70), Color(0xFFFF3D81)),
    listOf(Color(0xFF0B3B4F), Color(0xFF22D3EE)),
    listOf(Color(0xFF3A1A00), Color(0xFFFF8A1F)),
    listOf(Color(0xFF1B1340), Color(0xFF7C5CFF)),
    listOf(Color(0xFF05301F), Color(0xFF34E89E)),
    listOf(Color(0xFF3B2A00), Color(0xFFFFC93C)),
    listOf(Color(0xFF3A0A2A), Color(0xFFF326A9)),
    listOf(Color(0xFF0E0B22), Color(0xFF4F46E5)),
    listOf(Color(0xFF2B1C70), Color(0xFFFF8A1F)),
)

/** "Your 2026 is ready" (home screen, December to mid-January), or "Your 2026 so far" (Points & badges). */
@Composable
fun WrappedCard(year: Int, ready: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF3D81), Color(0xFFFF8A1F))))
            .clickable(onClick = onOpen).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RIDE WRAPPED", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                FitText(if (ready) "Your $year is ready" else "Your $year so far", MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Your km, your crew, your rider type", style = MaterialTheme.typography.bodyMedium, color = Color(0xE6FFFFFF))
            }
            Ico(R.drawable.ms_play_arrow, 40.dp, Color.White)
        }
    }
}

/** Loads the year from the score book and the ride history, then plays it. */
@Composable
fun WrappedScreen(year: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val w by produceState<WrappedYear?>(null, year) {
        value = withContext(Dispatchers.IO) {
            val entries = Score.load(context)
            val routes = runCatching { RideHistory.list(context) }.getOrDefault(emptyList()).map { it.startedAtMs to it.route }
            WrappedLogic.build(entries, routes, year)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0B22))) {
            w?.let { WrappedStory(it, onClose = onClose, onShare = { WrappedPicture.share(context, it) }) }
        }
    }
}

/** The slides, tap the right side for the next and the left for the previous one. */
@Composable
fun WrappedStory(w: WrappedYear, onClose: () -> Unit, onShare: () -> Unit, start: Int = 0) {
    val slides = remember(w) { slidesOf(w) }
    var at by remember(w) { mutableIntStateOf(start.coerceIn(0, slides.lastIndex)) }
    val colours = SLIDE_COLOURS[at % SLIDE_COLOURS.size]
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(colours[0], colours[0], colours[1]), start = Offset(0f, 0f), end = Offset(400f, 2400f)))
            .pointerInput(slides.size) {
                detectTapGestures { p -> at = if (p.x < size.width / 3) (at - 1).coerceAtLeast(0) else (at + 1).coerceAtMost(slides.lastIndex) }
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                slides.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= at) Color.White else Color(0x4DFFFFFF)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RIDECOMM · ${w.year} WRAPPED", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Ico(R.drawable.ms_close, 22.dp, Color.White, "Close")
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) { slides[at](w, onShare) { at = 0 } }
            Text(if (at < slides.lastIndex) "Tap for more" else "", style = MaterialTheme.typography.labelSmall, color = Color(0x99FFFFFF), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private typealias Slide = @Composable (WrappedYear, () -> Unit, () -> Unit) -> Unit

@Composable
private fun Kicker(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), color = Color(0xCCFFFFFF))

@Composable
private fun Big(text: String) = FitText(text, MaterialTheme.typography.displayMedium.copy(fontSize = 60.sp, lineHeight = 66.sp, letterSpacing = 0.sp), color = Color.White, min = 24.sp)

@Composable
private fun Line(text: String, strong: Boolean = false) = Text(
    text,
    style = if (strong) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
    color = if (strong) Color.White else Color(0xE6FFFFFF),
)

private fun slidesOf(w: WrappedYear): List<Slide> = buildList<Slide> {
    add { y, _, _ ->
        Kicker("Your ${y.year} on RideComm")
        Big("${n(y.km)} km")
        Line(y.comparison + ".", strong = true)
        Line("That's ${y.earth}.")
    }
    add { y, _, _ ->
        Kicker("Rides")
        Big("${y.rides} ${if (y.rides == 1) "ride" else "rides"}")
        Line("${y.hours} ${if (y.hours == 1) "hour" else "hours"} in the saddle", strong = true)
        Line("Your favourite day: ${WrappedLogic.dayName(y.favouriteDay)}")
        Line("You usually set off ${WrappedLogic.hourText(y.usualHour)}")
    }
    add { y, _, _ ->
        Kicker("Your longest ride")
        Big("${n(y.longest.km)} km")
        Line(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(y.longest.atMs)), strong = true)
        if (y.longest.names.isNotEmpty()) Line("With ${y.longest.names.joinToString(", ")}") else Line("Just you and the road")
        if (y.longestRoute.size >= 2) RouteShape(y.longestRoute, Modifier.fillMaxWidth().height(200.dp), width = 9f)
    }
    add { y, _, _ ->
        Kicker("Your best month")
        Big(WrappedLogic.MONTHS[y.bestMonth])
        Line("${n(y.monthKm[y.bestMonth])} km", strong = true)
        MonthChart(y.monthKm, y.bestMonth)
    }
    add { y, _, _ ->
        Kicker("Your crew")
        if (y.riders == 0) {
            Big("Solo")
            Line("Every ride just you. Invite a friend next year!", strong = true)
        } else {
            Big("${y.riders} ${if (y.riders == 1) "rider" else "riders"}")
            Line("rode with you this year", strong = true)
            y.buddies.forEachIndexed { i, (name, count) -> Line("${i + 1}. $name · $count ${if (count == 1) "ride" else "rides"}") }
        }
    }
    add { y, _, _ ->
        Kicker("Points & badges")
        Big("${n(y.points)} points")
        Line("Level now: ${y.level.name}", strong = true)
        if (y.badges.isEmpty()) Line("No new badges this year") else {
            Line("${y.badges.size} new ${if (y.badges.size == 1) "badge" else "badges"}:")
            Line(y.badges.joinToString(" · ") { it.title })
        }
    }
    add { y, _, _ ->
        Kicker("Your rider type")
        Big(y.type.title)
        Line(y.type.line, strong = true)
    }
    if (w.routes.isNotEmpty()) add { y, _, _ ->
        Kicker("Every route")
        Line("${y.routes.size} ${if (y.routes.size == 1) "route" else "routes"} on your phone", strong = true)
        RouteGrid(y.routes.take(30))
    }
    add { y, share, again ->
        Kicker("That was ${y.year}")
        Big("${n(y.km)} km")
        Line("${y.rides} rides · ${y.hours} h · ${y.type.title}", strong = true)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Share my year", R.drawable.ms_share, Modifier.fillMaxWidth(), height = 56.dp, onClick = share)
        GlassButton("Watch again", R.drawable.ms_replay, Modifier.fillMaxWidth(), height = 52.dp, onClick = again)
    }
}

@Composable
private fun MonthChart(km: List<Double>, best: Int) {
    val max = km.maxOrNull()?.takeIf { it > 0 } ?: return
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 12
            val w = slot * 0.62f
            km.forEachIndexed { i, v ->
                val h = ((v / max).toFloat() * size.height).coerceAtLeast(5f)
                drawRoundRect(if (i == best) Color.White else Color(0x66FFFFFF), Offset(slot * i + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 3))
            }
        }
        Row { "JFMAMJJASOND".forEach { Text(it.toString(), Modifier.weight(1f), color = Color(0xB3FFFFFF), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun RouteGrid(routes: List<List<com.ridecomm.app.trip.RoutePoint>>) {
    val cols = 5
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        routes.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { r -> Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x26FFFFFF))) { RouteShape(r, Modifier.fillMaxSize(), width = 3f) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The year as a story-sized picture (1080 × 1920) to share. */
object WrappedPicture {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    fun render(context: Context, w: WrappedYear): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val bold = ResourcesCompat.getFont(context, R.font.outfit_bold) ?: Typeface.DEFAULT_BOLD
        val regular = ResourcesCompat.getFont(context, R.font.outfit_regular) ?: Typeface.DEFAULT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 400f, HEIGHT.toFloat(), 0xFF2B1C70.toInt(), 0xFF0E0B22.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        fun glow(x: Float, y: Float, r: Float, color: Int) {
            paint.shader = RadialGradient(x, y, r, color, 0x00000000, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, paint)
        }
        glow(120f, 160f, 700f, 0x777C5CFF)
        glow(1040f, 900f, 650f, 0x55FF3D81)
        glow(200f, 1800f, 600f, 0x4422D3EE)
        paint.shader = null
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, spacing: Float = 0f, maxWidth: Float = WIDTH - 144f) {
            paint.typeface = face
            paint.color = color
            paint.textAlign = Paint.Align.LEFT
            paint.letterSpacing = spacing
            var px = size
            paint.textSize = px
            while (px > 20f && paint.measureText(s) > maxWidth) {
                px -= 2f
                paint.textSize = px
            }
            c.drawText(s, x, y, paint)
            paint.letterSpacing = 0f
        }
        text("RIDECOMM · ${w.year} WRAPPED", 72f, 130f, 34f, 0xB3FFFFFF.toInt(), bold, spacing = 0.16f)
        text("${n(w.km)} km", 72f, 300f, 150f, 0xFFFFFFFF.toInt(), bold)
        text(w.comparison, 72f, 370f, 42f, 0xE6FFFFFF.toInt(), regular)
        val tiles = listOf(
            "${w.rides}" to "Rides",
            "${w.hours} h" to "Riding",
            "${n(w.longest.km)} km" to "Longest ride",
            WrappedLogic.MONTHS[w.bestMonth].take(3) to "Best month",
            "${w.riders}" to "Riders with me",
            n(w.points) to "Points",
        )
        val tileW = (WIDTH - 72f * 2 - 24f * 2) / 3
        tiles.forEachIndexed { i, (value, label) ->
            val x = 72f + (i % 3) * (tileW + 24f)
            val y = 440f + (i / 3) * 190f
            paint.color = 0x1FFFFFFF
            c.drawRoundRect(RectF(x, y, x + tileW, y + 166f), 34f, 34f, paint)
            text(value, x + 28f, y + 82f, 58f, 0xFFFFFFFF.toInt(), bold, maxWidth = tileW - 56f)
            text(label, x + 28f, y + 132f, 30f, 0xB3FFFFFF.toInt(), regular, maxWidth = tileW - 56f)
        }
        text("RIDER TYPE", 72f, 900f, 30f, 0xB3FFFFFF.toInt(), bold, spacing = 0.16f)
        text(w.type.title, 72f, 980f, 76f, 0xFFFFC93C.toInt(), bold)
        text(w.type.line, 72f, 1036f, 36f, 0xE6FFFFFF.toInt(), regular)
        if (w.buddies.isNotEmpty()) text("Rode most with " + w.buddies.joinToString(", ") { it.first }, 72f, 1100f, 36f, 0xE6FFFFFF.toInt(), regular)
        // Every route, in a grid.
        val routes = w.routes.take(24)
        // Bigger drawings when there are only a few.
        val cols = if (routes.size <= 12) 4 else 6
        val gap = 20f
        // As big as fits between the rider type and the footer.
        val rows = (routes.size + cols - 1) / cols
        val cell = minOf((WIDTH - 144f - gap * (cols - 1)) / cols, if (rows > 0) (1800f - 1170f - gap * (rows - 1)) / rows else 0f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val left = (WIDTH - (cell * cols + gap * (cols - 1))) / 2
        routes.forEachIndexed { i, r ->
            val x = left + (i % cols) * (cell + gap)
            val y = 1170f + (i / cols) * (cell + gap)
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0x1AFFFFFF
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), 24f, 24f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = 0xFFFFFFFF.toInt()
            paint.shader = LinearGradient(x, y, x + cell, y + cell, 0xFFFF8A1F.toInt(), 0xFFFF3D81.toInt(), Shader.TileMode.CLAMP)
            paint.strokeWidth = if (cols == 4) 9f else 7f
            paint.maskFilter = null
            c.drawPath(RidePicture.routePath(r, RectF(x + 18f, y + 18f, x + cell - 18f, y + cell - 18f)), paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        text("Made with RideComm", 72f, HEIGHT - 70f, 32f, 0x80FFFFFF.toInt(), regular)
        return bitmap
    }

    fun share(context: Context, w: WrappedYear) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ridecomm-${w.year}-wrapped.png")
        file.outputStream().use { render(context, w).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "My ${w.year} on RideComm: ${n(w.km)} km, ${w.rides} rides")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share my year"))
    }
}

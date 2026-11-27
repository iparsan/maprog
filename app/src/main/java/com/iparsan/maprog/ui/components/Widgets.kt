package com.iparsan.maprog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iparsan.maprog.ui.theme.PositionBlue
import com.iparsan.maprog.ui.theme.TrackOrange
import com.iparsan.maprog.util.Activity
import com.iparsan.maprog.util.Difficulty
import com.iparsan.maprog.util.Fmt
import com.iparsan.maprog.util.TrackMath
import com.iparsan.maprog.util.TrackPt

/**
 * Perfil de elevación. Si se pasa [progressIndex], la parte recorrida se pinta en naranja
 * y un punto azul marca dónde estás (como al seguir una ruta).
 */
@Composable
fun ElevationProfile(points: List<TrackPt>, modifier: Modifier = Modifier, progressIndex: Int? = null) {
    if (points.size < 2) {
        Box(modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Sin datos de altitud todavía", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val cum = TrackMath.cumulative(points)
    val total = cum.last().coerceAtLeast(1.0)
    val maxE = points.maxOf { it.ele }
    val minE = points.minOf { it.ele }
    val range = (maxE - minE).coerceAtLeast(20.0)
    val green = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${maxE.toInt()} m", style = MaterialTheme.typography.labelSmall)
            Text("Perfil de elevación", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(Fmt.km(total), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = (cum[i] / total * w).toFloat()
            fun y(i: Int) = (h - ((points[i].ele - minE) / range * h * 0.9) - h * 0.05).toFloat()
            for (k in 1..3) drawLine(grid, Offset(0f, h * k / 4), Offset(w, h * k / 4), 1f)

            val area = Path().apply {
                moveTo(0f, h)
                for (i in points.indices) lineTo(x(i), y(i))
                lineTo(w, h); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(green.copy(alpha = 0.45f), green.copy(alpha = 0.05f))))
            val line = Path().apply {
                moveTo(x(0), y(0))
                for (i in 1 until points.size) lineTo(x(i), y(i))
            }
            drawPath(line, green, style = Stroke(width = 3f))

            if (progressIndex != null && progressIndex > 0) {
                val done = Path().apply {
                    moveTo(x(0), y(0))
                    for (i in 1..progressIndex.coerceAtMost(points.size - 1)) lineTo(x(i), y(i))
                }
                drawPath(done, TrackOrange, style = Stroke(width = 5f))
                val pi = progressIndex.coerceAtMost(points.size - 1)
                drawCircle(Color.White, 9f, Offset(x(pi), y(pi)))
                drawCircle(PositionBlue, 6f, Offset(x(pi), y(pi)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${minE.toInt()} m", style = MaterialTheme.typography.labelSmall)
            Text("0 km", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Una casilla de estadística: valor grande y etiqueta pequeña. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 16.sp, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun DifficultyChip(level: Int) {
    val colors = listOf(Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFB8C00), Color(0xFFE53935), Color(0xFF212121))
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(colors.getOrElse(level - 1) { colors[1] }).padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Text(Difficulty.label(level), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun TrailRankBadge(rank: Int) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 2.dp)
    ) { Text("TrailRank $rank", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
fun Avatar(name: String?, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text((name?.firstOrNull() ?: '?').uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.2).sp)
    }
}

/** Tarjeta de ruta del listado (Explora, Mis rutas, Guardadas...). */
@Composable
fun TrailCard(
    name: String,
    activity: String,
    distanceM: Double,
    elevGainM: Double,
    difficulty: Int,
    author: String?,
    trailRank: Int?,
    claps: Int?,
    coverUrl: String?,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val act = Activity.of(activity)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl != null) AsyncImage(model = coverUrl, contentDescription = null, modifier = Modifier.size(64.dp))
                else Text(act.emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${act.label} · ${Fmt.km(distanceM)} · ↗ ${elevGainM.toInt()} m",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyChip(difficulty)
                    if (trailRank != null) TrailRankBadge(trailRank)
                    if (claps != null && claps > 0) Text("👏 $claps", fontSize = 12.sp)
                }
                val line = listOfNotNull(author?.let { "por $it" }, subtitle).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

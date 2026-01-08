package com.calypsan.listenup.client.features.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.repository.DailyListening
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 7-day bar chart showing daily listening time.
 *
 * Always shows the previous 7 days (today and 6 days before), filling in
 * zeros for days without listening data. This ensures consistent spacing.
 *
 * @param dailyListening List of daily listening data from server
 * @param maxListenTimeMs Maximum listen time for scaling (unused with auto-scale)
 * @param modifier Modifier from parent
 */
@Composable
fun DailyListeningChart(
    dailyListening: List<DailyListening>,
    @Suppress("UnusedParameter") maxListenTimeMs: Long,
    modifier: Modifier = Modifier,
) {
    // Generate the last 7 days (6 days ago through today)
    val last7Days =
        remember {
            val today = LocalDate.now()
            (6 downTo 0).map { daysAgo -> today.minusDays(daysAgo.toLong()) }
        }

    // Create a map of date -> listenTimeMs for quick lookup
    val listeningByDate =
        remember(dailyListening) {
            dailyListening.associate { it.date to it.listenTimeMs }
        }

    // Build data for all 7 days, filling zeros for missing days
    val minutesData =
        remember(last7Days, listeningByDate) {
            last7Days.map { date ->
                val dateStr = date.toString() // Format: YYYY-MM-DD
                val listenTimeMs = listeningByDate[dateStr] ?: 0L
                (listenTimeMs / 60_000).toFloat().coerceAtLeast(0f)
            }
        }

    // Extract day labels for all 7 days (e.g., "M", "T", "W")
    val dayLabels =
        remember(last7Days) {
            last7Days.map { date ->
                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            }
        }

    // Create model producer with data
    val modelProducer =
        remember(minutesData) {
            CartesianChartModelProducer().apply {
                runBlocking {
                    runTransaction {
                        columnSeries { series(minutesData) }
                    }
                }
            }
        }

    // Use Material 3 primary color for bars
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Column layer with Material 3 themed colors
    // Use fixed corner radius (not percentage) to avoid pill shape on tall bars
    val columnLayer =
        rememberColumnCartesianLayer(
            columnProvider =
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill =
                            com.patrykandpatrick.vico.core.common
                                .Fill(barColor.toArgb()),
                        shape = CorneredShape.rounded(topLeftDp = 4f, topRightDp = 4f),
                        thickness = 20.dp,
                    ),
                ),
        )

    // Bottom axis with day labels
    val bottomAxis =
        HorizontalAxis.rememberBottom(
            label =
                rememberAxisLabelComponent(
                    color = labelColor,
                ),
            valueFormatter =
                CartesianValueFormatter { _, x, _ ->
                    dayLabels.getOrNull(x.toInt()) ?: ""
                },
        )

    val chart =
        rememberCartesianChart(
            columnLayer,
            bottomAxis = bottomAxis,
        )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier =
            modifier
                .fillMaxWidth()
                .height(100.dp),
    )
}

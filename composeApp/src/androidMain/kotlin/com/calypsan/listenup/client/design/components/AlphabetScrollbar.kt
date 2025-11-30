package com.calypsan.listenup.client.design.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Data class representing the alphabet index for a list of items.
 */
data class AlphabetIndex(
    val letters: List<Char>,
    val letterToIndex: Map<Char, Int>
) {
    companion object {
        fun <T> build(items: List<T>, keySelector: (T) -> String): AlphabetIndex {
            val letterToIndex = mutableMapOf<Char, Int>()

            items.forEachIndexed { index, item ->
                val key = keySelector(item)
                if (key.isNotEmpty()) {
                    val firstChar = key.first().uppercaseChar()
                    if (firstChar !in letterToIndex) {
                        letterToIndex[firstChar] = index
                    }
                }
            }

            val letters = letterToIndex.keys
                .sortedWith(compareBy({ !it.isLetter() }, { it }))

            return AlphabetIndex(letters, letterToIndex)
        }
    }
}

/**
 * Google Contacts-style alphabet scrollbar.
 *
 * - Floats over content with no background when idle
 * - Shows background and popup when touched
 * - Haptic feedback on letter changes
 */
@Composable
fun AlphabetScrollbar(
    alphabetIndex: AlphabetIndex,
    onLetterSelected: (itemIndex: Int) -> Unit,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (alphabetIndex.letters.isEmpty()) return

    val view = LocalView.current

    var isInteracting by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var selectedLetterIndex by remember { mutableStateOf<Int?>(null) }
    var lastHapticLetter by remember { mutableStateOf<Char?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    var columnHeight by remember { mutableIntStateOf(0) }
    var selectedLetterY by remember { mutableStateOf(0f) }

    LaunchedEffect(isScrolling, isInteracting) {
        if (isScrolling || isInteracting) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    // Confident bounce spring - perceptible but not performative
    val expressiveSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val alpha by animateFloatAsState(
        targetValue = when {
            isInteracting -> 1f
            isVisible -> 0.9f
            else -> 0f
        },
        animationSpec = expressiveSpring,
        label = "alpha"
    )

    // Slide in/out from right edge with confident bounce
    val slideOffset by animateDpAsState(
        targetValue = if (isVisible || isInteracting) 0.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "slide"
    )

    data class LetterPosition(val letter: Char, val index: Int, val centerY: Float)

    fun letterAtPosition(y: Float): LetterPosition? {
        if (columnHeight <= 0 || alphabetIndex.letters.isEmpty()) return null
        val letterHeight = columnHeight.toFloat() / alphabetIndex.letters.size
        val index = (y / letterHeight).toInt().coerceIn(0, alphabetIndex.letters.size - 1)
        val letterCenterY = (index + 0.5f) * letterHeight
        return LetterPosition(alphabetIndex.letters[index], index, letterCenterY)
    }

    fun selectLetter(y: Float, isInitialTouch: Boolean = false) {
        letterAtPosition(y)?.let { (letter, index, centerY) ->
            if (letter != selectedLetter) {
                selectedLetter = letter
                selectedLetterIndex = index
                selectedLetterY = centerY
                alphabetIndex.letterToIndex[letter]?.let(onLetterSelected)

                // Haptic feedback - stronger for initial touch, tick for subsequent
                if (isInitialTouch) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                } else if (letter != lastHapticLetter) {
                    view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                }
                lastHapticLetter = letter
            }
        }
    }

    Box(
        modifier = modifier
            .offset(x = slideOffset)
            .alpha(alpha)
    ) {
        // Popup indicator - positioned at the selected letter's Y
        AnimatedVisibility(
            visible = isInteracting && selectedLetter != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            selectedLetter?.let { letter ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = -72.dp.roundToPx(),
                                y = (selectedLetterY - 24.dp.toPx()).toInt()
                            )
                        }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // The scrollbar - always has background for visibility over content
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isInteracting)
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    else
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
                )
                .onSizeChanged { columnHeight = it.height }
                .pointerInput(alphabetIndex) {
                    detectTapGestures(
                        onPress = { offset ->
                            isInteracting = true
                            selectLetter(offset.y, isInitialTouch = true)
                            tryAwaitRelease()
                            isInteracting = false
                            selectedLetter = null
                            selectedLetterIndex = null
                            lastHapticLetter = null
                        }
                    )
                }
                .pointerInput(alphabetIndex) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isInteracting = true
                            selectLetter(offset.y, isInitialTouch = true)
                        },
                        onDragEnd = {
                            isInteracting = false
                            selectedLetter = null
                            selectedLetterIndex = null
                            lastHapticLetter = null
                        },
                        onDragCancel = {
                            isInteracting = false
                            selectedLetter = null
                            selectedLetterIndex = null
                            lastHapticLetter = null
                        },
                        onDrag = { change, _ ->
                            selectLetter(change.position.y)
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            alphabetIndex.letters.forEachIndexed { index, letter ->
                AnimatedLetterItem(
                    letter = letter,
                    index = index,
                    selectedIndex = selectedLetterIndex,
                    isSelected = letter == selectedLetter
                )
            }
        }
    }
}

/**
 * Individual letter with animated scale based on proximity to selection.
 * Tight gradient: touched = 1.3x, ±1 neighbors = 1.1x, rest = 1.0x
 */
@Composable
private fun AnimatedLetterItem(
    letter: Char,
    index: Int,
    selectedIndex: Int?,
    isSelected: Boolean
) {
    val targetScale = when {
        selectedIndex == null -> 1f
        index == selectedIndex -> 1.3f
        kotlin.math.abs(index - selectedIndex) == 1 -> 1.1f
        else -> 1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "letterScale"
    )

    Text(
        text = letter.toString(),
        fontSize = 16.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

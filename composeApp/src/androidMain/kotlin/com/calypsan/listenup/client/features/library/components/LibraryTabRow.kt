package com.calypsan.listenup.client.features.library.components

import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.calypsan.listenup.client.features.library.LibraryTab

/**
 * Tab row for switching between Library content types.
 *
 * Displays Books, Series, Authors, Narrators tabs with icons.
 * Syncs with HorizontalPager for swipe navigation.
 *
 * @param selectedTabIndex Currently selected tab index
 * @param onTabSelected Callback when a tab is selected
 * @param modifier Optional modifier
 */
@Composable
fun LibraryTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier
    ) {
        LibraryTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(tab.title) },
                icon = { Icon(tab.icon, contentDescription = null) }
            )
        }
    }
}

@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.admin.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.presentation.admin.AdminCategoriesUiState
import com.calypsan.listenup.client.presentation.admin.AdminCategoriesViewModel
import com.calypsan.listenup.client.presentation.admin.GenreTreeNode

/**
 * Admin screen for viewing the category (genre) tree.
 *
 * Displays a hierarchical tree view of all system genres with:
 * - Expandable/collapsible nodes
 * - Book count per category
 * - Visual hierarchy through indentation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCategoriesScreen(
    viewModel: AdminCategoriesViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Expand/collapse all toggle
                    if (state.tree.isNotEmpty()) {
                        val allExpanded = state.expandedIds.size >= state.genres.count { 
                            state.tree.any { root -> hasChildren(root, it.id) }
                        }
                        IconButton(
                            onClick = {
                                if (allExpanded) viewModel.collapseAll() else viewModel.expandAll()
                            }
                        ) {
                            Icon(
                                imageVector = if (allExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                contentDescription = if (allExpanded) "Collapse All" else "Expand All",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading && state.tree.isEmpty()) {
            FullScreenLoadingIndicator()
        } else {
            CategoriesContent(
                state = state,
                onToggleExpanded = viewModel::toggleExpanded,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * Check if a node or any of its descendants has the given ID.
 */
private fun hasChildren(node: GenreTreeNode, id: String): Boolean {
    if (node.genre.id == id) return node.children.isNotEmpty()
    return node.children.any { hasChildren(it, id) }
}

@Composable
private fun CategoriesContent(
    state: AdminCategoriesUiState,
    onToggleExpanded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.tree.isEmpty()) {
        EmptyCategoriesMessage(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "${state.genres.size} categories • ${state.totalBookCount} books",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column {
                        state.tree.forEachIndexed { index, rootNode ->
                            CategoryTreeNode(
                                node = rootNode,
                                expandedIds = state.expandedIds,
                                onToggleExpanded = onToggleExpanded,
                                isLast = index == state.tree.lastIndex,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryTreeNode(
    node: GenreTreeNode,
    expandedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val isExpanded = expandedIds.contains(node.genre.id)
    val hasChildren = node.children.isNotEmpty()

    Column(modifier = modifier) {
        CategoryRow(
            node = node,
            isExpanded = isExpanded,
            hasChildren = hasChildren,
            onToggleExpanded = { onToggleExpanded(node.genre.id) },
        )

        // Show divider if not last item at root level, or if expanded with children
        if (!isLast || (isExpanded && hasChildren)) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = (24 + node.depth * 24).dp),
            )
        }

        // Animated children expansion
        AnimatedVisibility(
            visible = isExpanded && hasChildren,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                node.children.forEachIndexed { index, child ->
                    CategoryTreeNode(
                        node = child,
                        expandedIds = expandedIds,
                        onToggleExpanded = onToggleExpanded,
                        isLast = index == node.children.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    node: GenreTreeNode,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ChevronRotation",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasChildren, onClick = onToggleExpanded)
            .padding(
                start = (16 + node.depth * 24).dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Expand/collapse icon or spacer
        if (hasChildren) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }

        // Category icon
        Icon(
            imageVector = Icons.Outlined.Category,
            contentDescription = null,
            tint = if (node.depth == 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )

        // Category name
        Text(
            text = node.genre.name,
            style = if (node.depth == 0) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Book count badge
        if (node.genre.bookCount > 0) {
            Text(
                text = "${node.genre.bookCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCategoriesMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Category,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Categories",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Categories are managed on the server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

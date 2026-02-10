package com.calypsan.listenup.client.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.BrandLogo
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.presentation.auth.PendingApprovalStatus
import com.calypsan.listenup.client.presentation.auth.PendingApprovalViewModel
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.auth_approved
import listenup.composeapp.generated.resources.auth_cancel_registration
import listenup.composeapp.generated.resources.auth_signing_you_in
import listenup.composeapp.generated.resources.auth_waiting_for_approval
import listenup.composeapp.generated.resources.auth_youll_be_automatically_signed_in
import listenup.composeapp.generated.resources.auth_your_registration_request_has_been

/**
 * Screen shown while waiting for admin approval after registration.
 *
 * Features:
 * - Shows user's email and pending status
 * - Automatic SSE connection for real-time approval notification
 * - Fallback to polling if SSE fails
 * - Auto-login when approved
 * - Cancel option to return to login
 */
@Composable
fun PendingApprovalScreen(
    viewModel: PendingApprovalViewModel,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle status changes
    LaunchedEffect(state.status) {
        when (val status = state.status) {
            is PendingApprovalStatus.ApprovedManualLogin -> {
                snackbarHostState.showSnackbar(status.message)
                onNavigateToLogin()
            }

            is PendingApprovalStatus.Denied -> {
                snackbarHostState.showSnackbar(status.message)
                onNavigateToLogin()
            }

            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandLogo(size = 120.dp)

            Spacer(modifier = Modifier.height(48.dp))

            ElevatedCard(
                modifier =
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    when (state.status) {
                        PendingApprovalStatus.Waiting -> {
                            ListenUpLoadingIndicator()

                            Text(
                                text = stringResource(Res.string.auth_waiting_for_approval),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Text(
                                text = stringResource(Res.string.auth_your_registration_request_has_been),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                text = viewModel.email,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = stringResource(Res.string.auth_youll_be_automatically_signed_in),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        PendingApprovalStatus.LoggingIn -> {
                            ListenUpLoadingIndicator()

                            Text(
                                text = stringResource(Res.string.auth_approved),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = stringResource(Res.string.auth_signing_you_in),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        PendingApprovalStatus.LoginSuccess -> {
                            // Will navigate automatically via AuthState
                            ListenUpLoadingIndicator()
                        }

                        is PendingApprovalStatus.ApprovedManualLogin,
                        is PendingApprovalStatus.Denied,
                        -> {
                            // Handled via snackbar and navigation
                        }
                    }

                    // Cancel button (only show when waiting)
                    if (state.status == PendingApprovalStatus.Waiting) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.cancelRegistration()
                                onNavigateToLogin()
                            },
                        ) {
                            Text(stringResource(Res.string.auth_cancel_registration))
                        }
                    }
                }
            }
        }
    }
}

@file:Suppress("LongMethod")

package com.calypsan.listenup.client.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.presentation.auth.RegisterStatus
import com.calypsan.listenup.client.presentation.auth.RegisterViewModel
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.common_back
import listenup.composeapp.generated.resources.auth_confirm_password
import listenup.composeapp.generated.resources.auth_create_account
import listenup.composeapp.generated.resources.auth_create_an_account_request_an
import listenup.composeapp.generated.resources.auth_first_name
import listenup.composeapp.generated.resources.auth_last_name
import listenup.composeapp.generated.resources.auth_request_account
import listenup.composeapp.generated.resources.auth_request_an_account

/**
 * Register screen for new user account creation.
 *
 * Only shown when open registration is enabled on the server.
 * After successful registration:
 * 1. AuthState transitions to PendingApproval
 * 2. Navigation automatically shows PendingApprovalScreen
 * 3. PendingApprovalScreen handles waiting for approval and auto-login
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle status changes
    LaunchedEffect(state.status) {
        when (val status = state.status) {
            is RegisterStatus.Error -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.clearError()
            }

            // Success triggers AuthState change, navigation handles the rest
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.auth_create_account)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

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
                RegisterForm(
                    state = state,
                    onSubmit = viewModel::onRegisterSubmit,
                    modifier = Modifier.padding(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Registration form content.
 */
@Composable
private fun RegisterForm(
    state: com.calypsan.listenup.client.presentation.auth.RegisterUiState,
    onSubmit: (email: String, password: String, firstName: String, lastName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val isLoading = state.status is RegisterStatus.Loading

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.auth_request_an_account),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(Res.string.auth_create_an_account_request_an),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // First Name
        ListenUpTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = stringResource(Res.string.auth_first_name),
            enabled = !isLoading,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Last Name
        ListenUpTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = stringResource(Res.string.auth_last_name),
            enabled = !isLoading,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Email
        ListenUpTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            enabled = !isLoading,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Password
        ListenUpTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Confirm Password
        ListenUpTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = stringResource(Res.string.auth_confirm_password),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
            supportingText =
                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    "Passwords do not match"
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!isLoading && password == confirmPassword) {
                            onSubmit(email, password, firstName, lastName)
                        }
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Submit button
        ListenUpButton(
            onClick = {
                onSubmit(email, password, firstName, lastName)
            },
            text = stringResource(Res.string.auth_request_account),
            enabled = !isLoading && password == confirmPassword && password.isNotEmpty(),
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

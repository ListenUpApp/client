package com.calypsan.listenup.client.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.composeapp.R
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.theme.LocalDarkTheme
import com.calypsan.listenup.client.presentation.auth.LoginErrorType
import com.calypsan.listenup.client.presentation.auth.LoginField
import com.calypsan.listenup.client.presentation.auth.LoginStatus
import com.calypsan.listenup.client.presentation.auth.LoginViewModel
import org.koin.compose.koinInject

/**
 * Login screen for user authentication.
 *
 * Features:
 * - Clean Material 3 layout matching Setup/ServerSetup style
 * - Email and password fields
 * - Client-side validation with field-specific errors
 * - Snackbar for network/server/credential errors
 * - Auto-navigation on success via AuthState
 *
 * @param openRegistration Whether to show the "Create Account" button
 * @param onChangeServer Callback when user wants to change server
 * @param onRegister Callback when user clicks "Create Account"
 */
@Composable
fun LoginScreen(
    openRegistration: Boolean = false,
    onChangeServer: () -> Unit,
    onRegister: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for non-validation errors
    LaunchedEffect(state.status) {
        when (val status = state.status) {
            is LoginStatus.Error -> {
                val message =
                    when (val type = status.type) {
                        is LoginErrorType.InvalidCredentials -> {
                            "Invalid email or password."
                        }

                        is LoginErrorType.NetworkError -> {
                            type.detail ?: "Network error. Check your connection."
                        }

                        is LoginErrorType.ServerError -> {
                            type.detail ?: "Server error. Please try again."
                        }

                        is LoginErrorType.ValidationError -> {
                            null
                        } // Handled inline
                    }
                message?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearError()
                }
            }

            else -> {}
        }
    }

    LoginContent(
        state = state,
        openRegistration = openRegistration,
        onSubmit = viewModel::onLoginSubmit,
        onChangeServer = onChangeServer,
        onRegister = onRegister,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless content for LoginScreen.
 */
@Composable
private fun LoginContent(
    state: com.calypsan.listenup.client.presentation.auth.LoginUiState,
    openRegistration: Boolean,
    onSubmit: (String, String) -> Unit,
    onChangeServer: () -> Unit,
    onRegister: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
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
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            BrandLogo()

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
                LoginForm(
                    state = state,
                    onSubmit = onSubmit,
                    modifier = Modifier.padding(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (openRegistration) {
                TextButton(onClick = onRegister) {
                    Text("Create Account")
                }
            }

            TextButton(onClick = onChangeServer) {
                Text("Change Server")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Brand logo section.
 */
@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    val isDarkTheme = LocalDarkTheme.current
    val logoRes =
        if (isDarkTheme) {
            R.drawable.listenup_logo_white
        } else {
            R.drawable.listenup_logo_black
        }

    Image(
        painter = painterResource(logoRes),
        contentDescription = "ListenUp Logo",
        modifier = modifier.size(160.dp),
    )
}

/**
 * Login form content.
 */
@Composable
private fun LoginForm(
    state: com.calypsan.listenup.client.presentation.auth.LoginUiState,
    onSubmit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val isLoading = state.status is LoginStatus.Loading

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Title
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Sign in to access your audiobook library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Email
        ListenUpTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            enabled = !isLoading,
            isError =
                state.status is LoginStatus.Error &&
                    (state.status as LoginStatus.Error).type is LoginErrorType.ValidationError &&
                    ((state.status as LoginStatus.Error).type as LoginErrorType.ValidationError)
                        .field == LoginField.EMAIL,
            supportingText =
                if (state.status is LoginStatus.Error &&
                    (state.status as LoginStatus.Error).type is LoginErrorType.ValidationError &&
                    ((state.status as LoginStatus.Error).type as LoginErrorType.ValidationError)
                        .field == LoginField.EMAIL
                ) {
                    "Invalid email address"
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            modifier =
                Modifier
                    .fillMaxWidth(),
        )

        // Password
        ListenUpTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            isError =
                state.status is LoginStatus.Error &&
                    (state.status as LoginStatus.Error).type is LoginErrorType.ValidationError &&
                    ((state.status as LoginStatus.Error).type as LoginErrorType.ValidationError).field ==
                    LoginField.PASSWORD,
            supportingText =
                if (state.status is LoginStatus.Error &&
                    (state.status as LoginStatus.Error).type is LoginErrorType.ValidationError &&
                    ((state.status as LoginStatus.Error).type as LoginErrorType.ValidationError).field ==
                    LoginField.PASSWORD
                ) {
                    "Password is required"
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
                        if (!isLoading) {
                            onSubmit(email, password)
                        }
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Submit button
        ListenUpButton(
            onClick = {
                onSubmit(email, password)
            },
            text = "Sign In",
            enabled = !isLoading,
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

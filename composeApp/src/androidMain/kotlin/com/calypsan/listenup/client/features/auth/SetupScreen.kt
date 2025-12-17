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
import com.calypsan.listenup.client.R
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.theme.LocalDarkTheme
import com.calypsan.listenup.client.presentation.auth.SetupErrorType
import com.calypsan.listenup.client.presentation.auth.SetupField
import com.calypsan.listenup.client.presentation.auth.SetupStatus
import com.calypsan.listenup.client.presentation.auth.SetupViewModel
import org.koin.compose.koinInject

/**
 * Initial server setup screen for creating the root/admin user.
 *
 * Features:
 * - Clean Material 3 layout matching ServerSetupScreen style
 * - Form for first name, last name, email, password, confirm password
 * - Client-side validation with field-specific errors
 * - Snackbar for network/server errors
 * - Auto-navigation on success via AuthState
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for non-validation errors
    LaunchedEffect(state.status) {
        when (val status = state.status) {
            is SetupStatus.Error -> {
                val message =
                    when (status.type) {
                        is SetupErrorType.NetworkError -> {
                            "Network error. Please check your connection."
                        }

                        is SetupErrorType.ServerError -> {
                            "Server error. Please try again."
                        }

                        is SetupErrorType.AlreadyConfigured -> {
                            "Server is already configured."
                        }

                        is SetupErrorType.ValidationError -> {
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

    SetupContent(
        state = state,
        onSubmit = viewModel::onSetupSubmit,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless content for SetupScreen.
 */
@Composable
private fun SetupContent(
    state: com.calypsan.listenup.client.presentation.auth.SetupUiState,
    onSubmit: (String, String, String, String, String) -> Unit,
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
                SetupForm(
                    state = state,
                    onSubmit = onSubmit,
                    modifier = Modifier.padding(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
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
 * Setup form content.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
@Composable
private fun SetupForm(
    state: com.calypsan.listenup.client.presentation.auth.SetupUiState,
    onSubmit: (String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val isLoading = state.status is SetupStatus.Loading

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Title
        Text(
            text = "Create Admin Account",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Set up your ListenUp server by creating the first admin account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // First Name
        ListenUpTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = "First Name",
            enabled = !isLoading,
            isError =
                state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.FIRST_NAME,
            supportingText =
                if (state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.FIRST_NAME
                ) {
                    "First name is required"
                } else {
                    null
                },
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
            label = "Last Name",
            enabled = !isLoading,
            isError =
                state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.LAST_NAME,
            supportingText =
                if (state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.LAST_NAME
                ) {
                    "Last name is required"
                } else {
                    null
                },
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
            isError =
                state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.EMAIL,
            supportingText =
                if (state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.EMAIL
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
            modifier = Modifier.fillMaxWidth(),
        )

        // Password
        ListenUpTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            isError =
                state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.PASSWORD,
            supportingText =
                if (state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.PASSWORD
                ) {
                    "Password must be at least 8 characters"
                } else {
                    null
                },
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
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            label = "Confirm Password",
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            isError =
                state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.PASSWORD_CONFIRM,
            supportingText =
                if (state.status is SetupStatus.Error &&
                    (state.status as SetupStatus.Error).type is SetupErrorType.ValidationError &&
                    ((state.status as SetupStatus.Error).type as SetupErrorType.ValidationError)
                        .field == SetupField.PASSWORD_CONFIRM
                ) {
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
                        if (!isLoading) {
                            onSubmit(firstName, lastName, email, password, passwordConfirm)
                        }
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Submit button
        ListenUpButton(
            onClick = {
                onSubmit(firstName, lastName, email, password, passwordConfirm)
            },
            text = "Create Account",
            enabled = !isLoading,
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

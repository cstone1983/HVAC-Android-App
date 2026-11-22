package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.PLACEHOLDER_HA_URL
import com.example.api.SignInStage
import com.example.api.signInUrl
import com.example.model.SignInConfig
import com.example.ui.theme.themedShape
import com.example.viewmodel.HvacViewModel

private val SignInGreen = Color(0xFF10B981)

/** The height of a text field's box, so a button beside one lines up with it. */
private val FieldHeight = 56.dp

/**
 * The sign-in screen: what a device shows until it has a Home Assistant token.
 *
 * Username and password is the normal way in (83.0). Home Assistant checks them, asks for a
 * two-step code if the account has one, and the app then has Home Assistant make a long-lived
 * token for this device under the name in the third field. Pasting a long-lived token is still
 * there underneath, for an account that signs in some other way.
 *
 * The password and the token are held in plain `remember`, not `rememberSaveable`, so neither is
 * written into the saved instance state.
 */
@Composable
fun HvacLoginScreen(
    viewModel: HvacViewModel,
    glowColor: Color,
    bgGradient: Brush
) {
    val layoutConfig by viewModel.layoutConfig.collectAsStateWithLifecycle()
    val loginError by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isLoggingIn.collectAsStateWithLifecycle()
    val stage by viewModel.signInStage.collectAsStateWithLifecycle()
    val passwordRejections by viewModel.passwordRejections.collectAsStateWithLifecycle()
    val mainUrl by viewModel.haUrl.collectAsState()
    val homeUrl by viewModel.backupHaUrl.collectAsState()

    SignInContent(
        title = layoutConfig.appTitle ?: "Home Control",
        subtitle = layoutConfig.appSubtitle ?: "HVAC SYSTEM CONTROLLER",
        text = layoutConfig.signIn ?: SignInConfig(),
        server = signInUrl(mainUrl, homeUrl),
        mainUrl = mainUrl,
        defaultMainUrl = remember { viewModel.getDefaultHaUrl() },
        stage = stage,
        loginError = loginError,
        isConnecting = isConnecting,
        passwordRejections = passwordRejections,
        defaultDeviceName = remember { viewModel.defaultSignInDeviceName() },
        glowColor = glowColor,
        bgGradient = bgGradient,
        actions = remember(viewModel) {
            SignInActions(
                logIn = viewModel::loginWithPassword,
                logInWithToken = viewModel::login,
                submitCode = viewModel::submitSignInCode,
                chooseMethod = viewModel::chooseSignInMethod,
                startOver = viewModel::cancelSignInStep,
                clearSessions = viewModel::clearSessions,
                saveServer = viewModel::updateHaUrl,
                restoreServer = viewModel::restoreDefaultHaUrl
            )
        }
    )
}

/** What the sign-in screen can ask the app to do. */
class SignInActions(
    val logIn: (username: String, password: String, deviceName: String) -> Unit = { _, _, _ -> },
    val logInWithToken: (String) -> Unit = {},
    val submitCode: (String) -> Unit = {},
    val chooseMethod: (String) -> Unit = {},
    val startOver: () -> Unit = {},
    val clearSessions: () -> Unit = {},
    val saveServer: (String) -> Unit = {},
    val restoreServer: () -> Unit = {}
)

/**
 * The screen itself, with no view model, so it can be drawn in a test.
 *
 * On a tablet lying on its side there is little height and the keyboard takes half of it, so the
 * wide layout puts the fields in pairs with the button beside the last one, keeps the header to
 * one line, and shows an error above the fields rather than below them.
 */
@Composable
fun SignInContent(
    title: String,
    subtitle: String,
    text: SignInConfig,
    server: String,
    mainUrl: String,
    defaultMainUrl: String,
    stage: SignInStage,
    loginError: String?,
    isConnecting: Boolean,
    passwordRejections: Int,
    defaultDeviceName: String,
    glowColor: Color,
    bgGradient: Brush,
    actions: SignInActions,
    startWithToken: Boolean = false,
    initialUsername: String = ""
) {
    var useToken by rememberSaveable { mutableStateOf(startWithToken) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var deviceName by rememberSaveable { mutableStateOf(defaultDeviceName) }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var showServer by rememberSaveable { mutableStateOf(false) }

    // A rejected password is not worth sending twice: Home Assistant counts each failure.
    LaunchedEffect(passwordRejections) { if (passwordRejections > 0) password = "" }
    LaunchedEffect(stage, loginError) { if (loginError != null || stage !is SignInStage.Code) code = "" }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = center
                )
            }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        val wide = maxWidth >= 720.dp
        val short = maxHeight < 700.dp

        Column(
            modifier = Modifier
                .fillMaxWidth(if (wide) 0.86f else 0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = if (short) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SignInHeader(title, subtitle, compact = short)
            Spacer(modifier = Modifier.height(if (short) 12.dp else 28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape = themedShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = if (short) 14.dp else 20.dp)) {
                    SideBySide(wide, spacing = 12.dp, alignment = Alignment.CenterVertically, first = {
                        Text(
                            "SIGN IN TO HOME ASSISTANT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }, second = {
                        Text(
                            if (server.isBlank()) "No server address set" else server,
                            fontSize = 12.sp,
                            color = if (server.isBlank()) Color(0xFFEF4444) else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.testTag("sign_in_server")
                        )
                    }, weighted = false)

                    SignInErrorCard(loginError)
                    Spacer(modifier = Modifier.height(10.dp))

                    when (val current = stage) {
                        is SignInStage.Code -> {
                            SignInNote("Open ${current.moduleName ?: "your authenticator app"} on your phone and enter the code it shows.")
                            Spacer(modifier = Modifier.height(10.dp))
                            SideBySide(wide, first = {
                                SignInField(
                                    value = code,
                                    onValueChange = { code = it },
                                    label = text.codeLabel ?: "Two-factor authentication code",
                                    icon = Icons.Default.Lock,
                                    tag = "sign_in_code_input",
                                    enabled = !isConnecting,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { actions.submitCode(code) })
                                )
                            }, second = {
                                SignInButton("VERIFY", isConnecting, "sign_in_code_button") { actions.submitCode(code) }
                            })
                        }

                        is SignInStage.ChooseMethod -> {
                            SignInNote("This account has more than one two-step method. Pick the one to use.")
                            current.options.forEach { (id, name) ->
                                Spacer(modifier = Modifier.height(10.dp))
                                SignInButton(name.uppercase(), isConnecting, "sign_in_method_$id") {
                                    actions.chooseMethod(id)
                                }
                            }
                        }

                        SignInStage.Credentials -> if (!useToken) {
                            SideBySide(wide, first = {
                                SignInField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = text.usernameLabel ?: "Username",
                                    icon = Icons.Default.Person,
                                    tag = "sign_in_username_input",
                                    enabled = !isConnecting,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    )
                                )
                            }, second = {
                                SignInField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = text.passwordLabel ?: "Password",
                                    icon = Icons.Default.Lock,
                                    tag = "sign_in_password_input",
                                    enabled = !isConnecting,
                                    secret = !passwordVisible,
                                    onToggleSecret = { passwordVisible = !passwordVisible },
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { actions.logIn(username, password, deviceName) }
                                    )
                                )
                            })
                            Spacer(modifier = Modifier.height(8.dp))
                            SideBySide(wide, first = {
                                SignInField(
                                    value = deviceName,
                                    onValueChange = { deviceName = it },
                                    label = text.deviceNameLabel ?: "Device name",
                                    icon = Icons.Default.Tablet,
                                    tag = "sign_in_device_name_input",
                                    enabled = !isConnecting,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                )
                            }, second = {
                                SignInButton("LOG IN", isConnecting, "login_button") {
                                    actions.logIn(username, password, deviceName)
                                }
                            })
                            Spacer(modifier = Modifier.height(10.dp))
                            SignInNote(text.passwordHint.orEmpty())
                        } else {
                            SideBySide(wide, first = {
                                SignInField(
                                    value = token,
                                    onValueChange = { token = it },
                                    label = text.tokenLabel ?: "Long-lived access token",
                                    icon = Icons.Default.Lock,
                                    tag = "ha_token_input",
                                    enabled = !isConnecting,
                                    secret = !tokenVisible,
                                    onToggleSecret = { tokenVisible = !tokenVisible },
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrectEnabled = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { actions.logInWithToken(token) })
                                )
                            }, second = {
                                SignInButton("LOG IN", isConnecting, "login_button") { actions.logInWithToken(token) }
                            })
                            Spacer(modifier = Modifier.height(10.dp))
                            SignInNote(text.tokenHint.orEmpty())
                        }
                    }

                    // The quiet options underneath.
                    SideBySide(wide, spacing = 0.dp, alignment = Alignment.CenterVertically, weighted = false, first = {
                        when {
                            stage !is SignInStage.Credentials ->
                                SignInLink("START OVER", enabled = !isConnecting, tag = "sign_in_start_over", onClick = actions.startOver)
                            useToken -> SignInLink("USE USERNAME AND PASSWORD INSTEAD", enabled = !isConnecting, tag = "sign_in_use_password") {
                                token = ""
                                useToken = false
                            }
                            else -> SignInLink("USE A LONG-LIVED TOKEN INSTEAD", enabled = !isConnecting, tag = "sign_in_use_token") {
                                password = ""
                                useToken = true
                            }
                        }
                    }, second = {
                        if (stage is SignInStage.Credentials) {
                            Row {
                                SignInLink(
                                    if (showServer) "HIDE SERVER ADDRESS" else "CHANGE SERVER ADDRESS",
                                    enabled = !isConnecting,
                                    tag = "sign_in_change_server"
                                ) { showServer = !showServer }
                                SignInLink(
                                    "CLEAR SESSIONS / TOKENS",
                                    enabled = !isConnecting,
                                    tag = "clear_sessions_button",
                                    onClick = actions.clearSessions
                                )
                            }
                        }
                    })

                    if (showServer && stage is SignInStage.Credentials) {
                        ServerAddressSection(mainUrl, defaultMainUrl, server, wide, actions) { showServer = false }
                    }
                }
            }
        }
    }
}

/**
 * Two things side by side on a wide screen, one above the other on a narrow one. [weighted]
 * gives each half the width; otherwise they sit at the two ends.
 */
@Composable
private fun SideBySide(
    wide: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    spacing: androidx.compose.ui.unit.Dp = 10.dp,
    alignment: Alignment.Vertical = Alignment.Bottom,
    weighted: Boolean = true
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (weighted) Arrangement.spacedBy(spacing) else Arrangement.SpaceBetween,
            verticalAlignment = alignment
        ) {
            if (weighted) {
                Box(modifier = Modifier.weight(1f)) { first() }
                Box(modifier = Modifier.weight(1f)) { second() }
            } else {
                first()
                second()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (weighted) Alignment.Start else Alignment.CenterHorizontally) {
            first()
            Spacer(modifier = Modifier.height(if (weighted) 8.dp else 2.dp))
            second()
        }
    }
}

@Composable
private fun SignInHeader(title: String, subtitle: String, compact: Boolean) {
    val icon: @Composable (Int) -> Unit = { box ->
        Box(
            modifier = Modifier
                .size(box.dp)
                .background(SignInGreen.copy(alpha = 0.15f), themedShape((box / 3).dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Weekend, contentDescription = null, tint = SignInGreen, modifier = Modifier.size((box / 2).dp))
        }
    }
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon(40)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 18.sp, color = Color.White)
                Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SignInGreen, letterSpacing = 1.5.sp)
            }
        }
    } else {
        icon(64)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 24.sp, color = Color.White)
        Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SignInGreen, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SignInField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    tag: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    secret: Boolean = false,
    onToggleSecret: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label, color = Color.White.copy(alpha = 0.4f)) },
        colors = signInFieldColors(),
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
        trailingIcon = if (onToggleSecret == null) null else {
            {
                IconButton(onClick = onToggleSecret) {
                    Icon(
                        imageVector = if (secret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (secret) "Show" else "Hide",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    )
}

@Composable
private fun signInFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.White.copy(alpha = 0.5f),
    focusedIndicatorColor = SignInGreen,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
)

@Composable
private fun SignInNote(text: String) {
    if (text.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF2196F3),
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), lineHeight = 15.sp)
    }
}

/** What Home Assistant said, in its own words where it has them. Sits above the fields. */
@Composable
private fun SignInErrorCard(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(10.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
        shape = themedShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("sign_in_error")
    ) {
        Text(
            text = message,
            color = Color(0xFFEF4444),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/** Disabled while a request is out, so one tap is one request. */
@Composable
private fun SignInButton(label: String, busy: Boolean, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = SignInGreen,
            disabledContainerColor = SignInGreen.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth().height(FieldHeight).testTag(tag),
        shape = themedShape(10.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("CONNECTING...", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black)
        } else {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SignInLink(label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
        modifier = Modifier.testTag(tag)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

/** The main address this device signs in to and uses away from home. */
@Composable
private fun ServerAddressSection(
    currentHaUrl: String,
    defaultHaUrl: String,
    signInServer: String,
    wide: Boolean,
    actions: SignInActions,
    onDone: () -> Unit
) {
    // A release has no main address built in, only a placeholder. Start the field on the address
    // sign-in is actually going to use rather than on the placeholder.
    val hasMain = currentHaUrl.isNotBlank() && currentHaUrl != PLACEHOLDER_HA_URL
    val hasDefault = defaultHaUrl.isNotBlank() && defaultHaUrl != PLACEHOLDER_HA_URL
    var urlText by remember(currentHaUrl, signInServer) {
        mutableStateOf(if (hasMain) currentHaUrl else signInServer)
    }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
        SideBySide(wide, first = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Server address", color = Color.White.copy(alpha = 0.4f)) },
                placeholder = { Text("http://192.168.1.10:8123", color = Color.White.copy(alpha = 0.2f)) },
                colors = signInFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth().testTag("login_ha_url_input"),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = SignInGreen.copy(alpha = 0.8f)) }
            )
        }, second = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (urlText.isNotBlank()) {
                            actions.saveServer(urlText)
                            Toast.makeText(context, "Server address saved", Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            Toast.makeText(context, "The address cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignInGreen.copy(alpha = 0.15f),
                        contentColor = SignInGreen
                    ),
                    border = BorderStroke(1.dp, SignInGreen.copy(alpha = 0.3f)),
                    shape = themedShape(10.dp),
                    modifier = Modifier.weight(1f).height(FieldHeight).testTag("login_ha_url_save")
                ) {
                    Text("SAVE ADDRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }

                val isDefault = urlText.trim().removeSuffix("/") == defaultHaUrl.trim().removeSuffix("/")
                if (hasDefault && !isDefault) {
                    Button(
                        onClick = {
                            actions.restoreServer()
                            Toast.makeText(context, "Default address restored", Toast.LENGTH_SHORT).show()
                            urlText = defaultHaUrl
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f),
                            contentColor = Color(0xFFEF5350)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
                        shape = themedShape(10.dp),
                        modifier = Modifier.height(FieldHeight)
                    ) {
                        Text("RESTORE DEFAULT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
            }
        })
    }
}

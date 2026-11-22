package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.api.SignInStage
import com.example.model.SignInConfig
import com.example.ui.SignInActions
import com.example.ui.SignInContent
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in screen drawn at the kitchen tablet's size (SM-X110: 1340 x 800 at tvdpi), and what
 * its buttons send. `recordRoborazziDebug` writes the pictures to `src/test/screenshots`; a
 * plain test run only checks the behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1007dp-h601dp-land-tvdpi", sdk = [36])
class SignInScreenTest {

    @get:Rule val compose = createComposeRule()

    private val sent = mutableListOf<String>()
    private var stage by mutableStateOf<SignInStage>(SignInStage.Credentials)
    private var error by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var rejections by mutableIntStateOf(0)

    private val actions = SignInActions(
        logIn = { user, password, device -> sent += "logIn|$user|$password|$device" },
        logInWithToken = { sent += "token|$it" },
        submitCode = { sent += "code|$it" },
        chooseMethod = { sent += "method|$it" },
        startOver = { sent += "startOver" }
    )

    private fun show(startWithToken: Boolean = false, username: String = "") {
        compose.setContent {
            MyApplicationTheme {
                SignInContent(
                    title = "Home Control",
                    subtitle = "HVAC SYSTEM CONTROLLER",
                    text = SignInConfig(),
                    server = "http://10.10.11.103:8123/",
                    mainUrl = "https://localhost/",
                    defaultMainUrl = "https://localhost/",
                    stage = stage,
                    loginError = error,
                    isConnecting = busy,
                    passwordRejections = rejections,
                    defaultDeviceName = "HVAC tablet - kitchen",
                    glowColor = Color(0xFF2196F3).copy(alpha = 0.12f),
                    bgGradient = Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF111827))),
                    actions = actions,
                    startWithToken = startWithToken,
                    initialUsername = username
                )
            }
        }
    }

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/sign_in_$name.png")

    @Test
    fun `log in sends what was typed and the device name, once`() {
        show()
        compose.onNodeWithText("http://10.10.11.103:8123/").assertIsDisplayed()
        compose.onNodeWithTag("sign_in_device_name_input").assertTextContains("HVAC tablet - kitchen")
        // On the tablet the button has to be on screen without scrolling: the keyboard will take
        // the bottom half.
        compose.onNodeWithTag("login_button").assertIsDisplayed()
        capture("1_empty")

        compose.onNodeWithTag("sign_in_username_input").performTextInput("chris")
        compose.onNodeWithTag("sign_in_password_input").performTextInput("correct horse")
        compose.onNodeWithTag("login_button").performScrollTo().performClick()

        assertEquals(listOf("logIn|chris|correct horse|HVAC tablet - kitchen"), sent)
    }

    @Test
    fun `while a request is out the button cannot be tapped again`() {
        busy = true
        show(username = "chris")
        compose.onNodeWithTag("login_button").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("CONNECTING...").assertIsDisplayed()
    }

    @Test
    fun `a rejected password is shown in Home Assistant's words and the field is emptied`() {
        show(username = "chris")
        compose.onNodeWithTag("sign_in_password_input").performTextInput("wrong")

        error = "Invalid username or password"
        rejections = 1
        compose.waitForIdle()

        compose.onNodeWithText("Invalid username or password").performScrollTo().assertIsDisplayed()
        capture("2_wrong_password")
        compose.onNodeWithTag("login_button").performScrollTo().performClick()
        assertEquals("the emptied password is what would be sent", listOf("logIn|chris||HVAC tablet - kitchen"), sent)
    }

    @Test
    fun `the code step sends the code, and start over goes back`() {
        stage = SignInStage.Code("Authenticator app")
        show()
        compose.onNodeWithText("Open Authenticator app on your phone and enter the code it shows.").assertIsDisplayed()
        compose.onNodeWithTag("sign_in_code_input").performTextInput("123456")
        capture("3_code")
        compose.onNodeWithTag("sign_in_code_button").performClick()
        compose.onNodeWithText("START OVER").performClick()

        assertEquals(listOf("code|123456", "startOver"), sent)
    }

    @Test
    fun `two methods are offered as buttons`() {
        stage = SignInStage.ChooseMethod(listOf("totp" to "Authenticator app", "notify" to "Notify one-time password"))
        show()
        compose.onNodeWithTag("sign_in_method_notify").performClick()

        assertEquals(listOf("method|notify"), sent)
    }

    @Test
    fun `a token can still be pasted instead`() {
        show()
        compose.onNodeWithTag("sign_in_use_token").performScrollTo().performClick()
        compose.onNodeWithTag("ha_token_input").performTextInput("pasted-token")
        capture("4_token")
        compose.onNodeWithTag("login_button").performScrollTo().performClick()
        compose.onNodeWithTag("sign_in_use_password").performScrollTo().performClick()
        compose.onNodeWithTag("sign_in_username_input").assertIsDisplayed()

        assertEquals(listOf("token|pasted-token"), sent)
    }

    @Test
    fun `can't reach the server is shown as that`() {
        error = "Can't reach the server at http://10.10.11.103:8123/ (nothing is answering there)"
        show(username = "chris")
        compose.onNodeWithText(error!!).performScrollTo().assertIsDisplayed()
        capture("5_unreachable")
    }
}

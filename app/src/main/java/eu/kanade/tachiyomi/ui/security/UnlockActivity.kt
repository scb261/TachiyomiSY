package eu.kanade.tachiyomi.ui.security

import android.os.Bundle
import androidx.biometric.BiometricPrompt
//import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.activity.BaseThemedActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
//import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import java.util.Date

// XZM -->
import java.util.concurrent.Executors
// XZM <--

/**
 * Blank activity with a BiometricPrompt.
 */
class UnlockActivity : BaseThemedActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    logcat(LogPriority.ERROR) { errString.toString() }
                    finishAffinity()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SecureActivityDelegate.locked = false
                    preferences.lastAppUnlock().set(Date().time)
                    finish()
                }
            }
        )

        var promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app))
            .setAllowedAuthenticators(AuthenticatorUtil.getSupportedAuthenticators(this))
            .setConfirmationRequired(false)

        if (!AuthenticatorUtil.isDeviceCredentialAllowed(this)) {
            promptInfo = promptInfo.setNegativeButtonText(getString(R.string.action_cancel))
        }

        biometricPrompt.authenticate(promptInfo.build())
    }
}

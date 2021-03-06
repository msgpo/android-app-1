/*
 * Copyright (c) 2018 Proton Technologies AG
 * 
 * This file is part of ProtonVPN.
 * 
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.ui.login

import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import butterknife.BindView
import butterknife.OnCheckedChanged
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.evernote.android.state.State
import com.google.android.material.textfield.TextInputLayout
import com.protonvpn.android.R
import com.protonvpn.android.api.ApiResult
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.components.BaseActivity
import com.protonvpn.android.components.CompressedTextWatcher
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.ui.onboarding.WelcomeDialog
import com.protonvpn.android.utils.ConstantTime
import com.protonvpn.android.utils.Constants.SIGNUP_URL
import com.protonvpn.android.utils.DeepLinkActivity
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import srp.Auth
import srp.Proofs

@ContentLayout(R.layout.activity_login)
class LoginActivity : BaseActivity(), KeyboardVisibilityEventListener {

    @BindView(R.id.email) lateinit var editEmail: AppCompatEditText
    @BindView(R.id.password) lateinit var editPassword: AppCompatEditText
    @BindView(R.id.inputEmail) lateinit var inputEmail: TextInputLayout
    @BindView(R.id.inputPassword) lateinit var inputPassword: TextInputLayout
    @BindView(R.id.layoutCredentials) lateinit var layoutCredentials: LinearLayout
    @BindView(R.id.switchStartWithDevice) lateinit var switchStartWithDevice: SwitchCompat
    @BindView(R.id.protonLogo) lateinit var protonLogo: View
    @BindView(R.id.textCreateAccount) lateinit var textCreateAccount: TextView
    @BindView(R.id.textNeedHelp) lateinit var textNeedHelp: TextView

    @Inject lateinit var api: ProtonApiRetroFit
    @Inject lateinit var userPrefs: UserData

    @State
    var downloadStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (userPrefs.isLoggedIn) {
            navigateTo(HomeActivity::class.java)
            finish()
            return
        }

        initInputFields()
        checkForRotation()
        checkIfOpenedFromWeb()
        KeyboardVisibilityEvent.setEventListener(this, this)
    }

    private fun initInputFields() {
        switchStartWithDevice.visibility = if (Build.VERSION.SDK_INT >= 24) GONE else VISIBLE
        switchStartWithDevice.isChecked = userPrefs.connectOnBoot
        editEmail.addTextChangedListener(getTextWatcher(editEmail))
        editPassword.addTextChangedListener(getTextWatcher(editPassword))
        editPassword.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })

        editEmail.setText(userPrefs.user)
    }

    private fun checkForRotation() {
        if (downloadStarted) {
            networkFrameLayout.switchToLoading()
            hideKeyboard()
        }
    }

    private fun checkIfOpenedFromWeb() {
        if (intent.getBooleanExtra(DeepLinkActivity.FROM_DEEPLINK, false)) {
            editEmail.setText(intent.getStringExtra(DeepLinkActivity.USER_NAME))
            WelcomeDialog.showDialog(supportFragmentManager, WelcomeDialog.DialogType.WELCOME)
        }
    }

    @OnCheckedChanged(R.id.switchStartWithDevice)
    fun onStartWithDeviceChanged(isChecked: Boolean) {
        userPrefs.connectOnBoot = isChecked
    }

    override fun onVisibilityChanged(isOpen: Boolean) {
        val visibility = if (isOpen) View.GONE else View.VISIBLE
        textCreateAccount.visibility = visibility
        textNeedHelp.visibility = visibility
        layoutCredentials.gravity = if (isOpen) Gravity.TOP else Gravity.CENTER_VERTICAL

        val params = protonLogo.layoutParams as ConstraintLayout.LayoutParams
        params.verticalBias = if (isOpen) 0.1f else 0.5f
        protonLogo.layoutParams = params
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (currentFocus is EditText) {
                val emailRect = Rect()
                val passRect = Rect()
                editEmail.getGlobalVisibleRect(emailRect)
                editPassword.getGlobalVisibleRect(passRect)
                if (!emailRect.contains(event.rawX.toInt(), event.rawY.toInt()) &&
                        !passRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun getTextWatcher(editText: AppCompatEditText?): TextWatcher {
        return object : CompressedTextWatcher() {
            override fun afterTextChanged(editable: Editable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    editText?.backgroundTintList =
                            ColorStateList.valueOf(ContextCompat.getColor(context,
                                    if (editable.toString() == "") R.color.lightGrey else R.color.colorAccent))
                }
            }
        }
    }

    @OnClick(R.id.buttonLogin)
    fun onAttemptlogin() {
        attemptLogin()
    }

    @OnClick(R.id.textCreateAccount)
    fun textCreateAccount() {
        openUrl(SIGNUP_URL)
    }

    @OnClick(R.id.textNeedHelp)
    fun textNeedHelp() {
        val dialog = MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.loginNeedHelp)
                .customView(R.layout.dialog_help, true)
                .negativeText(R.string.cancel)
                .show()
        initNeedHelpDialog(dialog.customView!!)
    }

    private fun initNeedHelpDialog(view: View) {
        view.findViewById<View>(R.id.buttonResetPassword).setOnClickListener { openUrl("https://account.protonvpn.com/reset-password") }
        view.findViewById<View>(R.id.buttonForgotUser).setOnClickListener { openUrl("https://account.protonvpn.com/forgot-username") }
        view.findViewById<View>(R.id.buttonLoginProblems).setOnClickListener { openUrl("https://account.protonvpn.com/support/login-problems/") }
        view.findViewById<View>(R.id.buttonGetSupport).setOnClickListener { openUrl("https://account.protonvpn.com/support") }
    }

    private fun attemptLogin() {
        inputEmail.error = null
        inputPassword.error = null

        val email = editEmail.text.toString()
        val password = editPassword.text.toString()

        var cancel = false
        var focusView: View? = null

        if (TextUtils.isEmpty(email)) {
            inputEmail.error = getString(R.string.error_field_required)
            focusView = editEmail
            cancel = true
        }

        if (TextUtils.isEmpty(password)) {
            inputPassword.error = getString(R.string.error_field_required)
            focusView = editPassword
            cancel = true
        }

        if (cancel) {
            focusView?.requestFocus()
        } else {
            hideKeyboard()
            userPrefs.user = email
            downloadStarted = true

            loadingContainer.setRetryListener {
                login()
            }
            login()
        }
    }

    private suspend fun getLoginBody(loginInfo: LoginInfoResponse): LoginBody? {
        val proofs = getProofs(userPrefs.user, editPassword.text.toString(), loginInfo) ?: return null
        return LoginBody(userPrefs.user, loginInfo.srpSession,
                ConstantTime.encodeBase64(proofs.clientEphemeral, true),
                ConstantTime.encodeBase64(proofs.clientProof, true), "")
    }

    private fun login() = lifecycleScope.launch {
        loadingContainer.switchToLoading()
        Storage.delete(LoginResponse::class.java)
        when (val loginInfoResult = api.postLoginInfo(userPrefs.user)) {
            is ApiResult.Error ->
                loadingContainer.switchToRetry(loginInfoResult)
            is ApiResult.Success -> {
                val loginBody = getLoginBody(loginInfoResult.value)
                if (loginBody == null) {
                    loadingContainer.switchToEmpty()
                    Toast.makeText(this@LoginActivity,
                            R.string.toastLoginAuthVersionError, Toast.LENGTH_LONG).show()
                } else {
                    loginWithProofs(loginBody)
                }
            }
        }
    }

    private suspend fun loginWithProofs(loginBody: LoginBody) {
        when (val loginResult = api.postLogin(loginBody)) {
            is ApiResult.Error -> {
                loadingContainer.switchToRetry(loginResult)
                loadingContainer.setRetryListener {
                    loadingContainer.switchToEmpty()
                }
            }
            is ApiResult.Success -> {
                Storage.save(loginResult.value)
                val infoResult = api.getVPNInfo()
                when (infoResult) {
                    is ApiResult.Error ->
                        loadingContainer.switchToRetry(infoResult)
                    is ApiResult.Success -> {
                        navigateTo(HomeActivity::class.java)
                        finish()
                    }
                }

                downloadStarted = false
                userPrefs.setLoggedIn(infoResult.valueOrNull)
                editPassword.setText("")
            }
        }
    }

    override fun onBackPressed() {
        if (networkFrameLayout.state == NetworkFrameLayout.State.ERROR) {
            networkFrameLayout.switchToEmpty()
        } else {
            super.onBackPressed()
        }
    }

    private suspend fun getProofs(
        username: String,
        password: String,
        infoResponse: LoginInfoResponse
    ): Proofs? = withContext(Dispatchers.Default) {
        val auth = Auth(infoResponse.version, username, password, infoResponse.salt,
                infoResponse.modulus, infoResponse.serverEphemeral)
        auth.generateProofs(2048)
    }
}

package com.tjlabs.sdk_sample_app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.tjlabs.sdk_sample_app.databinding.ActivityMainBinding
import com.tjlabs.tjlabsauth_sdk_android.AuthRegion
import com.tjlabs.tjlabsauth_sdk_android.AuthServerEnv
import com.tjlabs.tjlabsauth_sdk_android.Sdk
import com.tjlabs.tjlabsauth_sdk_android.ServerProvider
import com.tjlabs.tjlabsauth_sdk_android.TJAuthLogger
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        fun currentEnv(): AuthServerEnv =
            if (bind.radioDev.isChecked) AuthServerEnv.DEV_TESTING_ONLY else AuthServerEnv.PROD

        fun refreshEnvLabel() {
            val env = currentEnv()
            val suffix = if (env == AuthServerEnv.PROD) ".tjlabscorp.com" else ".tjlabs.dev"
            bind.textCurrentEnv.text = "Selected env : $env  (suffix : $suffix)"
        }
        refreshEnvLabel()
        bind.radioGroupEnv.setOnCheckedChangeListener { _, _ -> refreshEnvLabel() }

        bind.btnReadResult.setOnClickListener {
            bind.textViewResult.text = buildCurrentResultText(
                status = "Cached auth state",
                detail = "Read current values from TJLabsAuthManager. Env selection: ${currentEnv()}"
            )
        }

        bind.btnAuth.setOnClickListener {
            val accessKey = BuildConfig.AUTH_ACCESS_KEY.ifBlank { bind.editTextText.text.toString() }
            val secretAccessKey = BuildConfig.AUTH_SECRET_ACCESS_KEY.ifBlank { bind.editTextTextPassword.text.toString() }
            val clientSecret = BuildConfig.AUTH_CLIENT_SECRET
            val env = currentEnv()

            TJLabsAuthManager.setServerURL(
                provider = ServerProvider.GCP.value,
                region = AuthRegion.KOREA.value,
                env = env,
            )

            if (accessKey.isBlank() || secretAccessKey.isBlank()) {
                val message = "AUTH_ACCESS_KEY or AUTH_SECRET_ACCESS_KEY is empty."
                Log.e("CheckToken", message)
                bind.textViewResult.text = buildCurrentResultText(
                    status = "Auth failed",
                    detail = message
                )
                return@setOnClickListener
            }
            if (clientSecret.isBlank()) {
                val message = "AUTH_CLIENT_SECRET is empty. Set it in local.properties."
                Log.e("CheckToken", message)
                bind.textViewResult.text = buildCurrentResultText(
                    status = "Auth failed",
                    detail = message
                )
                return@setOnClickListener
            }

            TJAuthLogger.setEnabled(true)
            TJLabsAuthManager.setClientSecret(applicationContext, clientSecret)
            TJLabsAuthManager.setSdkInfos(
                listOf(
                    Sdk(name = "TJLabsNavi-sdk-android", version = "1.0.0"),
                    Sdk(name = "TJLabsJupiter-sdk-android", version = "1.0.0")
                )
            )
            bind.textViewResult.text = "Auth request in progress..."

            TJLabsAuthManager.auth(applicationContext, accessKey, secretAccessKey) { code, result ->
                Log.d("CheckToken", "auth // code : $code // result : $result")
                bind.textViewResult.text = buildCurrentResultText(
                    status = if (code == 200) "Auth success" else "Auth failed",
                    detail = "code=$code, result=$result"
                )
            }
        }
    }

    private fun buildCurrentResultText(status: String, detail: String): String {
        return buildString {
            appendLine(status)
            appendLine(detail)
            appendLine()
            appendLine("isAuthenticated: ${TJLabsAuthManager.isAuthenticated(applicationContext)}")
            appendLine("tenantName: ${TJLabsAuthManager.getTenantName() ?: "null"}")
            appendLine("tenantUserName: ${TJLabsAuthManager.getTenantUserName() ?: "null"}")
        }
    }
}

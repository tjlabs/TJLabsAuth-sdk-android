package com.tjlabs.sdk_sample_app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.tjlabs.sdk_sample_app.databinding.ActivityMainBinding
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bind = ActivityMainBinding.inflate(layoutInflater)

        bind.btnAuth.setOnClickListener {
            val accessKey = BuildConfig.AUTH_ACCESS_KEY.ifBlank { bind.editTextText.text.toString() }
            val secretAccessKey = BuildConfig.AUTH_SECRET_ACCESS_KEY.ifBlank { bind.editTextTextPassword.text.toString() }
            val clientSecret = BuildConfig.AUTH_CLIENT_SECRET

            if (accessKey.isBlank() || secretAccessKey.isBlank()) {
                Log.e("CheckToken", "AUTH_ACCESS_KEY or AUTH_SECRET_ACCESS_KEY is empty.")
                return@setOnClickListener
            }
            if (clientSecret.isBlank()) {
                Log.e("CheckToken", "AUTH_CLIENT_SECRET is empty. Set it in local.properties")
                return@setOnClickListener
            }
            TJLabsAuthManager.setClientSecret(applicationContext, clientSecret)
            TJLabsAuthManager.auth(accessKey, secretAccessKey) {
                    code, result ->
                Log.d("CheckToken", "auth // code : $code // result : $result")
            }
        }

        setContentView(bind.root)

    }
}

package com.tjlabs.tjlabsauth_sdk_android

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tjlabs.tjlabsauth_sdk_android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bind = ActivityMainBinding.inflate(layoutInflater)

        bind.btnAuth.setOnClickListener {
            val name = bind.editTextText.text.toString()
            val pw = bind.editTextTextPassword.text.toString()

            TJLabsAuthManager.initialize(applicationContext)
            TJLabsAuthManager.auth(name, pw) {
                    code, result ->
                Log.d("CheckToken", "auth // code : $code // result : $result")
            }
        }

        bind.btnRefresh.setOnClickListener {
            TJLabsAuthManager.refresh {
                    code, result ->
                Log.d("CheckToken", "refresh // code : $code // result : $result")
            }
        }

        bind.btnGetAuth.setOnClickListener {
            TJLabsAuthManager.getAccessToken() {
                    result ->
                when(result){
                    is TokenResult.Success ->{

                    }
                    is TokenResult.Failure -> {

                    }

                    else -> {}
                }
                Log.d("CheckToken", "get auth // result : $result")
            }
        }

        bind.btnRefresh.setOnClickListener {
            Log.d("CheckToken", "get refresh // result : ${TJLabsAuthManager.getRefreshToken()}")
        }

        bind.btnVerify.setOnClickListener {
            TJLabsAuthManager.verify {
                    result ->
                Log.d("CheckToken", "verify // result : $result")
            }


        }
        setContentView(bind.root)

    }
}
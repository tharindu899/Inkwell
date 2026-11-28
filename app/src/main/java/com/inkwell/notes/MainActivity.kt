package com.inkwell.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inkwell.notes.auth.GoogleAuth
import com.inkwell.notes.ui.nav.InkwellApp

class MainActivity : ComponentActivity() {
    private lateinit var auth: GoogleAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = GoogleAuth(this)
        setContent { InkwellApp(auth) }
    }
}

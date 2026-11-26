package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.core.configureAppCheck
import com.example.data.content.Lexicon
import com.example.data.content.StoryCatalog
import com.example.ui.navigation.PolyglotAppRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Lexicon.init(applicationContext)
        StoryCatalog.init(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            Lexicon.preloadAsync()
            StoryCatalog.preloadAsync()
        }
        configureAppCheck(this, intent)
        setContent {
            PolyglotAppRoot()
        }
    }
}

package com.example.catenarycompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.catenarycompose.ui.theme.CatenaryComposeTheme
import org.maplibre.android.MapLibre
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val styleUri = if (isSystemInDarkTheme()) "https://maps.catenarymaps.org/dark-style.json" else "https://maps.catenarymaps.org/light-style.json"

            CatenaryComposeTheme {
                MaplibreMap(
                    styleUri = styleUri,
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CatenaryComposeTheme {
        Greeting("Catenary")
    }
}
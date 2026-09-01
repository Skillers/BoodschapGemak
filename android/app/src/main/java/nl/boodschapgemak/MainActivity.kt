package nl.boodschapgemak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import nl.boodschapgemak.ui.AppRoot
import nl.boodschapgemak.ui.BoodschapGemakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoodschapGemakTheme {
                AppRoot()
            }
        }
    }
}

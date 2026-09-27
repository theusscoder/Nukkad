package `in`.nukkad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.nukkad.ui.theme.NukkadTheme
import `in`.nukkad.product.MerchantNotifications
import `in`.nukkad.product.ProductNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NukkadTheme { NukkadApp() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MerchantNotifications.EXTRA_OPEN_REQUEST, false)) {
            ProductNavigation.focusIncomingRequest()
            intent.removeExtra(MerchantNotifications.EXTRA_OPEN_REQUEST)
        }
    }
}

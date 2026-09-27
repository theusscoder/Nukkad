package `in`.nukkad.product

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import `in`.nukkad.model.Item

/** Customer APK shares the merchant navigation source but does not include camera/OCR dependencies. */
@Composable
fun MerchantScanner(onCancel: () -> Unit, onAdd: (List<Item>) -> Unit) {
    Text("Shop scanning is available in the merchant app.")
}

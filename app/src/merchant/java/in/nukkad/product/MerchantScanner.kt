package `in`.nukkad.product

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.nukkad.model.Item
import java.io.File
import java.util.concurrent.Executor

@Composable
fun MerchantScanner(onCancel: () -> Unit, onAdd: (List<Item>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraAllowed by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var scanText by remember { mutableStateOf("") }
    var scanComplete by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<Item>>(emptyList()) }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var cameraCycle by remember { mutableIntStateOf(0) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed = granted
        if (!granted) cameraError = "Camera access is needed to scan your shop. You can still add items by voice."
    }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    LaunchedEffect(previewView, cameraAllowed, lifecycleOwner, cameraCycle) {
        val view = previewView ?: return@LaunchedEffect
        if (!cameraAllowed) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
                cameraError = ""
            }.onFailure { cameraError = "Camera could not open. Try again or add items by voice." }
        }, executor)
    }
    LaunchedEffect(scanning) { if (scanning) boundProvider?.unbindAll() }
    DisposableEffect(Unit) { onDispose { boundProvider?.unbindAll() } }
    SurfaceCard {
        Text("SCAN YOUR MENU", style = MaterialTheme.typography.headlineMedium)
        Text("Point your camera at a printed menu or rate card.")
        if (!cameraAllowed) {
            Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("OPEN CAMERA") }
        } else if (!scanComplete) {
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
                if (scanning) {
                    val snapshot = remember(capturedPath) { capturedPath?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
                    snapshot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                } else AndroidView(factory = { view -> PreviewView(view).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } },
                    modifier = Modifier.fillMaxSize())
                if (scanning) {
                    val line by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f,
                        infiniteRepeatable(tween(900), RepeatMode.Restart), label = "scan-line")
                    Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).graphicsLayer { translationY = line * 290.dp.toPx() }
                        .background(Color(0xFFFF6B2C)))
                }
                Button(onClick = { takeAndRead(context, imageCapture, recognizer, executor,
                    onCapture = { file -> capturedPath = file.absolutePath; scanning = true },
                    onResult = { text, items -> scanText = text; candidates = items; scanning = false; scanComplete = true },
                    onFailure = { message -> cameraError = message; scanning = false }) },
                    enabled = imageCapture != null && !scanning, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(if (scanning) "SCANNING…" else "TAKE PHOTO")
                }
            }
        }
        if (cameraError.isNotBlank()) Text(cameraError, color = MaterialTheme.colorScheme.error)
        if (scanning) Text("Reading the menu…")
        if (scanComplete && scanText.isNotBlank()) {
            Text("TEXT FOUND", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                Text(scanText, Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(14.dp)
                    .verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
            }
            Text("Review the recognized text before turning it into products.", style = MaterialTheme.typography.bodySmall)
        }
        if (scanComplete && candidates.isEmpty()) {
            Text("Couldn't read that clearly.", style = MaterialTheme.typography.titleLarge)
            Text("Try a closer, brighter photo, or use Tell Nukkad to add items.")
            TextButton(onClick = { scanComplete = false; scanText = ""; capturedPath = null }) { Text("TRY AGAIN") }
        }
        AnimatedVisibility(candidates.isNotEmpty(), enter = fadeIn() + expandVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CHECK WHAT WE FOUND", style = MaterialTheme.typography.titleLarge)
                if (scanText.isBlank()) Text("Couldn't read that clearly. Try a closer, brighter photo.")
                candidates.forEachIndexed { index, item ->
                    key(index) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HarnessSetupField("Item", item.name, { updateCandidate(candidates, index, item.copy(name = it), onCandidates = { candidates = it }) })
                                HarnessSetupField("Price · ₹", item.pricePerUnit.toString(), {
                                    updateCandidate(candidates, index, item.copy(pricePerUnit = it.toIntOrNull() ?: 0), onCandidates = { candidates = it })
                                })
                                HarnessSetupField("Unit", item.unit, { updateCandidate(candidates, index, item.copy(unit = it), onCandidates = { candidates = it }) })
                                Text("${item.constraintSurcharges.entries.joinToString { "${it.key} +₹${it.value}" }}")
                            }
                        }
                    }
                }
                Text("These are suggestions from the photo. Your saved items stay as they are until you add these.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onAdd(candidates) }, enabled = candidates.all { it.name.isNotBlank() && it.pricePerUnit > 0 }, modifier = Modifier.fillMaxWidth()) {
                    Text("ADD ${candidates.size} ITEMS TO MY SHOP")
                }
                    TextButton(onClick = { candidates = emptyList(); scanText = ""; capturedPath = null; scanComplete = false; cameraCycle++ }) { Text("Try another photo") }
            }
        }
        TextButton(onClick = onCancel) { Text("Back to shop") }
    }
}

private fun updateCandidate(items: List<Item>, index: Int, value: Item, onCandidates: (List<Item>) -> Unit) {
    onCandidates(items.toMutableList().also { it[index] = value })
}

@Composable private fun HarnessSetupField(label: String, value: String, onValue: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun takeAndRead(context: Context, capture: ImageCapture?, recognizer: com.google.mlkit.vision.text.TextRecognizer,
                        executor: Executor, onCapture: (File) -> Unit, onResult: (String, List<Item>) -> Unit, onFailure: (String) -> Unit) {
    val useCase = capture ?: return onFailure("Camera is not ready. Try again.")
    val file = File(context.cacheDir, "menu-scan-${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    useCase.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            onCapture(file)
            val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }.getOrElse {
                onFailure("Couldn't open that photo. Please try again."); return
            }
            recognizer.process(image)
                .addOnSuccessListener(executor) { text -> onResult(text.text, MerchantSetupParser.parseCatalogue(text.text)) }
                .addOnFailureListener(executor) { onFailure("Couldn't read that clearly. Try a closer, brighter photo.") }
        }
        override fun onError(exception: ImageCaptureException) = onFailure("Photo wasn't captured. Please try again.")
    })
}

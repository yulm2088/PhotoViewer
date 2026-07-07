package com.photoframe.myapk_pf

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.tv.material3.*
import androidx.compose.material3.Switch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.photoframe.myapk_pf.ui.theme.MyAPK_PFTheme
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_MEDIA_MOUNTED || 
                intent.action == Intent.ACTION_MEDIA_UNMOUNTED ||
                intent.action == Intent.ACTION_MEDIA_REMOVED) {
                refreshTrigger?.invoke()
            }
        }
    }

    private var refreshTrigger: (() -> Unit)? = null

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        setContent {
            MyAPK_PFTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    PhotoFrameApp(
                        onRefreshBound = { refreshTrigger = it }
                    )
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        registerReceiver(usbReceiver, filter)
        
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, permissions, 100)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}

enum class TransitionType {
    CROSSFADE, SLIDE, ZOOM_FADE, NONE
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhotoFrameApp(
    onRefreshBound: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imagePaths by remember { mutableStateOf(emptyList<String>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var intervalSeconds by remember { mutableIntStateOf(10) }
    var transitionsEnabled by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(false) }
    var showExif by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSaveRotateConfirm by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    
    val currentImage = if (imagePaths.isNotEmpty()) imagePaths[currentIndex % imagePaths.size] else null
    val currentExifDate = remember(currentImage) {
        currentImage?.let { getExifDate(it) } ?: ""
    }

    fun scan() {
        scope.launch(Dispatchers.IO) {
            val paths = mutableListOf<String>()
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { volume ->
                    if (volume.isDirectory && volume.canRead()) {
                        scanDir(volume, paths)
                    }
                }
            }
            scanDir(Environment.getExternalStorageDirectory(), paths)
            
            withContext(Dispatchers.Main) {
                imagePaths = paths.distinct().shuffled()
                currentIndex = 0
            }
        }
    }

    LaunchedEffect(Unit) {
        onRefreshBound { scan() }
        scan()
    }

    LaunchedEffect(isPaused, intervalSeconds, imagePaths.size) {
        if (!isPaused && imagePaths.isNotEmpty()) {
            while (true) {
                delay(intervalSeconds * 1000L)
                currentIndex = (currentIndex + 1) % imagePaths.size
                rotationAngle = 0f
                showExif = true
                delay(5000)
                showExif = false
            }
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            delay(20000)
            showOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPaused) {
                                if (rotationAngle != 0f) showSaveRotateConfirm = true
                                else isPaused = false
                            } else {
                                isPaused = true
                                showOverlay = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (isPaused) isPaused = false
                            else (context as? ComponentActivity)?.finish()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isPaused) showSettings = true
                            else {
                                currentIndex = (currentIndex + 1) % imagePaths.size
                                rotationAngle = 0f
                                showOverlay = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isPaused) showDeleteConfirm = true
                            else {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else imagePaths.size - 1
                                rotationAngle = 0f
                                showOverlay = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isPaused) rotationAngle = (rotationAngle + 90f) % 360f
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isPaused) rotationAngle = (rotationAngle - 90f) % 360f
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        if (currentImage != null) {
            val transition = if (transitionsEnabled) {
                remember(currentIndex) { 
                    TransitionType.entries.filter { it != TransitionType.NONE }.random() 
                }
            } else TransitionType.NONE

            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    when (transition) {
                        TransitionType.CROSSFADE -> fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                        TransitionType.SLIDE -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(1000)) togetherWith slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(1000))
                        TransitionType.ZOOM_FADE -> (fadeIn(tween(1000)) + scaleIn(initialScale = 0.8f, animationSpec = tween(1000))) togetherWith (fadeOut(tween(1000)) + scaleOut(targetScale = 1.2f, animationSpec = tween(1000)))
                        else -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "ImageTransition"
            ) { targetIndex ->
                val path = imagePaths[targetIndex % imagePaths.size]
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(path)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotationAngle },
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("画像を検索中...", color = Color.White)
            }
        }

        if (showOverlay) {
            ClockOverlay(Modifier.align(Alignment.BottomEnd))
        }

        if (showExif && currentExifDate.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("撮影日: $currentExifDate", color = Color.White, fontSize = 18.sp)
            }
        }

        if (isPaused) {
            PauseOverlay(Modifier.align(Alignment.TopEnd))
        }

        // Custom Overlays for Dialogs
        if (showSettings) {
            OverlayDialog(onDismiss = { showSettings = false }) {
                Column {
                    Text("切替間隔", fontWeight = FontWeight.Bold)
                    Row {
                        listOf(10, 15, 20).forEach { sec ->
                            FilterChip(
                                selected = intervalSeconds == sec,
                                onClick = { intervalSeconds = sec },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text("${sec}s")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("演出 (Transition)")
                        Spacer(Modifier.weight(1f))
                        Switch(checked = transitionsEnabled, onCheckedChange = { transitionsEnabled = it })
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showSettings = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("閉じる")
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            OverlayDialog(onDismiss = { showDeleteConfirm = false }) {
                Column {
                    Text("削除確認", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("この写真を物理的に削除しますか？")
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.align(Alignment.End)) {
                        Button(onClick = { showDeleteConfirm = false }) {
                            Text("キャンセル")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            currentImage?.let { 
                                File(it).delete() 
                                scan()
                            }
                            showDeleteConfirm = false
                        }) {
                            Text("削除")
                        }
                    }
                }
            }
        }

        if (showSaveRotateConfirm) {
            OverlayDialog(onDismiss = { showSaveRotateConfirm = false }) {
                Column {
                    Text("回転保存", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("回転させた状態で上書き保存しますか？")
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.align(Alignment.End)) {
                        Button(onClick = { 
                            rotationAngle = 0f
                            isPaused = false
                            showSaveRotateConfirm = false 
                        }) {
                            Text("保存せずに再開")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            currentImage?.let { saveRotatedImage(it, rotationAngle) }
                            rotationAngle = 0f
                            isPaused = false
                            showSaveRotateConfirm = false
                        }) {
                            Text("保存して再開")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OverlayDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss, enabled = true),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF202020), RoundedCornerShape(12.dp))
                .padding(24.dp)
                .widthIn(min = 300.dp)
                .clickable(enabled = false) {},
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClockOverlay(modifier: Modifier) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }
    Text(
        text = timeText,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PauseOverlay(modifier: Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("一時停止中", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        PauseActionItem(Icons.Default.ArrowUpward, "回転(右)")
        PauseActionItem(Icons.Default.ArrowDownward, "回転(左)")
        PauseActionItem(Icons.Default.ArrowBack, "削除")
        PauseActionItem(Icons.Default.ArrowForward, "設定")
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
             Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PauseActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(" $text", color = Color.White, fontSize = 12.sp)
    }
}

fun scanDir(dir: File, list: MutableList<String>) {
    if (!dir.exists() || !dir.isDirectory) return
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            if (file.name != "Android" && !file.name.startsWith(".")) scanDir(file, list)
        } else {
            val name = file.name.lowercase()
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".bmp") || name.endsWith(".gif") || name.endsWith(".webp")) {
                list.add(file.absolutePath)
            }
        }
    }
}

fun getExifDate(path: String): String {
    return try {
        val exif = ExifInterface(path)
        exif.getAttribute(ExifInterface.TAG_DATETIME) ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun saveRotatedImage(path: String, angle: Float) {
    try {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val bitmap = BitmapFactory.decodeFile(path, options)
        val matrix = Matrix().apply { postRotate(angle) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        FileOutputStream(path).use { out ->
            val format = if (path.lowercase().endsWith(".png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            rotated.compress(format, 90, out)
        }
        bitmap.recycle()
        rotated.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

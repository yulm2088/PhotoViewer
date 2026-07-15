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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.stringResource
import com.photoframe.myapk_pf.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val mainFocusRequester = remember { FocusRequester() }

    var imagePaths by remember { mutableStateOf(emptyList<String>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var intervalSeconds by remember { mutableIntStateOf(10) }
    var transitionsEnabled by remember { mutableStateOf(true) }
    var showClock by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(false) }
    var showExif by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSaveRotateConfirm by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
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
        mainFocusRequester.requestFocus()
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
        if (showOverlay && !isPaused) {
            delay(5000)
            showOverlay = false
        }
    }

    val isAnyDialogOpen = showSettings || showDeleteConfirm || showSaveRotateConfirm

    LaunchedEffect(isAnyDialogOpen) {
        if (!isAnyDialogOpen) {
            // Delay slightly to ensure composition is ready before requesting focus
            delay(100)
            mainFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(mainFocusRequester)
            .onKeyEvent {
                if (isSaving) return@onKeyEvent true // Block input while saving
                if (it.type == KeyEventType.KeyDown) {
                    // When a dialog is open, the dialog's own components should handle keys.
                    // We only catch the BACK key here to close the dialogs.
                    if (isAnyDialogOpen) {
                        if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                            if (showSettings) showSettings = false
                            else if (showDeleteConfirm) showDeleteConfirm = false
                            else if (showSaveRotateConfirm) showSaveRotateConfirm = false
                            return@onKeyEvent true
                        }
                        // Allow other keys to propagate so focus navigation works in the dialog
                        return@onKeyEvent false
                    }

                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPaused) {
                                if (rotationAngle != 0f) {
                                    showOverlay = false
                                    showSaveRotateConfirm = true
                                }
                                else isPaused = false
                            } else {
                                isPaused = true
                                showOverlay = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (showSettings) showSettings = false
                            else if (showDeleteConfirm) showDeleteConfirm = false
                            else if (showSaveRotateConfirm) showSaveRotateConfirm = false
                            else if (isPaused) isPaused = false
                            else (context as? ComponentActivity)?.finish()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isPaused) {
                                showSettings = true
                            } else {
                                currentIndex = (currentIndex + 1) % imagePaths.size
                                rotationAngle = 0f
                                showOverlay = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isPaused) {
                                showDeleteConfirm = true
                            } else {
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
            .focusable(!isAnyDialogOpen)
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
                        .apply {
                            if (refreshKey > 0) {
                                setParameter("refresh", refreshKey)
                                memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                diskCachePolicy(coil.request.CachePolicy.DISABLED)
                            }
                        }
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
                Text(stringResource(R.string.scanning_images), color = Color.White)
            }
        }

        if (showOverlay && showClock) {
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
                Text(stringResource(R.string.shooting_date, currentExifDate), color = Color.White, fontSize = 18.sp)
            }
        }

        if (isPaused && !isAnyDialogOpen && !isSaving) {
            PauseOverlay(Modifier.align(Alignment.TopEnd))
        }

        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.saving_status), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.updating_image), color = Color.LightGray, fontSize = 14.sp)
                }
            }
        }

        if (showSettings) {
            val firstFocusRequester = remember { FocusRequester() }
            OverlayDialog(title = stringResource(R.string.dialog_settings_title), onDismiss = { showSettings = false }) {
                Column {
                    Text(stringResource(R.string.interval_title), style = MaterialTheme.typography.labelLarge, color = Color.LightGray)
                    Text(stringResource(R.string.interval_desc), fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        listOf(10, 15, 20).forEachIndexed { index, sec ->
                            FocusableFilterChip(
                                selected = intervalSeconds == sec,
                                onClick = { intervalSeconds = sec },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                            ) {
                                Text(stringResource(R.string.seconds, sec))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.transition_title), style = MaterialTheme.typography.labelLarge, color = Color.LightGray)
                    Text(stringResource(R.string.transition_desc), fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.enable_transition), color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = transitionsEnabled, onCheckedChange = { transitionsEnabled = it })
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(stringResource(R.string.clock_display_title), color = Color.White)
                            Text(stringResource(R.string.clock_display_desc), fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(checked = showClock, onCheckedChange = { showClock = it })
                    }
                    Spacer(Modifier.height(32.dp))
                    FocusableButton(
                        onClick = { 
                            showSettings = false
                            isPaused = false // Resume slideshow after closing settings
                            showOverlay = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.btn_close))
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(200)
                firstFocusRequester.requestFocus()
            }
        }

        if (showDeleteConfirm) {
            val cancelFocusRequester = remember { FocusRequester() }
            OverlayDialog(title = stringResource(R.string.dialog_delete_title), onDismiss = { showDeleteConfirm = false }) {
                Column {
                    Text(stringResource(R.string.delete_desc), color = Color.White)
                    Text(stringResource(R.string.delete_warning), color = Color.Red, fontSize = 12.sp)
                    Spacer(Modifier.height(32.dp))
                    Row(modifier = Modifier.align(Alignment.End)) {
                        FocusableButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.focusRequester(cancelFocusRequester)
                        ) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                        Spacer(Modifier.width(16.dp))
                        FocusableButton(onClick = {
                            currentImage?.let { 
                                File(it).delete() 
                                scan()
                            }
                            showDeleteConfirm = false
                            isPaused = false // Resume slideshow
                            showOverlay = false
                        }) {
                            Text(stringResource(R.string.btn_delete))
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(200)
                cancelFocusRequester.requestFocus()
            }
        }

        if (showSaveRotateConfirm) {
            val saveFocusRequester = remember { FocusRequester() }
            OverlayDialog(title = stringResource(R.string.dialog_rotate_title), onDismiss = { showSaveRotateConfirm = false }) {
                Column {
                    Text(stringResource(R.string.rotate_desc), color = Color.White)
                    Spacer(Modifier.height(32.dp))
                    Row(modifier = Modifier.align(Alignment.End)) {
                        FocusableButton(onClick = { 
                            rotationAngle = 0f
                            showSaveRotateConfirm = false 
                            isPaused = false
                        }) {
                            Text(stringResource(R.string.btn_resume_without_save))
                        }
                        Spacer(Modifier.width(16.dp))
                        FocusableButton(
                            onClick = {
                                val path = currentImage
                                val angle = rotationAngle
                                showSaveRotateConfirm = false
                                if (path != null) {
                                    scope.launch {
                                        isSaving = true
                                        withContext(Dispatchers.IO) {
                                            saveRotatedImage(path, angle)
                                        }
                                        rotationAngle = 0f
                                        refreshKey++ // Force reload
                                        delay(800) // Short delay to show updated image
                                        isSaving = false
                                        isPaused = false
                                    }
                                }
                            },
                            modifier = Modifier.focusRequester(saveFocusRequester)
                        ) {
                            Text(stringResource(R.string.btn_save_and_resume))
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(300)
                saveFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White)),
            border = Border(BorderStroke(1.dp, Color.Transparent))
        ),
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusableFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        border = FilterChipDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White)),
            border = Border(BorderStroke(1.dp, Color.Transparent))
        ),
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OverlayDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF282828), RoundedCornerShape(16.dp))
                .padding(32.dp)
                .widthIn(min = 400.dp, max = 600.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClockOverlay(modifier: Modifier) {
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            val now = Date()
            dateText = dateFormat.format(now)
            timeText = timeFormat.format(now)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .padding(32.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = (-2).sp,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f))
        )
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 0.dp),
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f))
        )
    }
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
        Text(stringResource(R.string.paused), color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        // Remote Control D-Pad Illustration
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Up arrow
            Icon(Icons.Default.ArrowDropUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left arrow
                Icon(Icons.Default.ArrowLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                // OK Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                // Right arrow
                Icon(Icons.Default.ArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            // Down arrow
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        
        Spacer(Modifier.height(16.dp))
        PauseActionItem(Icons.Default.ArrowUpward, stringResource(R.string.rotate_right))
        PauseActionItem(Icons.Default.ArrowDownward, stringResource(R.string.rotate_left))
        PauseActionItem(Icons.Default.ArrowBack, stringResource(R.string.delete_photo))
        PauseActionItem(Icons.Default.ArrowForward, stringResource(R.string.settings))
        PauseActionItem(Icons.Default.CheckCircle, stringResource(R.string.resume_save))
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

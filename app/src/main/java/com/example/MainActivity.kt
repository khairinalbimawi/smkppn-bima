package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import com.example.ui.theme.MyApplicationTheme
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    // Globals for Web file uploads
    var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Globals for Web Geolocation & Camera prompts
    var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    var pendingGeolocationOrigin: String? = null
    var pendingPermissionRequest: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(this)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, isGranted, false)
            pendingGeolocationCallback = null
            pendingGeolocationOrigin = null
        } else if (requestCode == 1003) {
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                val resources = pendingPermissionRequest?.resources ?: emptyArray()
                pendingPermissionRequest?.grant(resources)
            } else {
                pendingPermissionRequest?.deny()
            }
            pendingPermissionRequest = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(activity: MainActivity) {
    var showSplashScreen by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Dynamic lifecycle observer to check permissions when resuming
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val allGranted = requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    permissionsGranted = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }

    // Setup input file chooser contract launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data != null) {
                val dataUri = data.data
                val clipData = data.clipData
                if (clipData != null) {
                    val list = mutableListOf<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        list.add(clipData.getItemAt(i).uri)
                    }
                    list.toTypedArray()
                } else if (dataUri != null) {
                    arrayOf(dataUri)
                } else {
                    null
                }
            } else {
                null
            }
            activity.fileUploadCallback?.onReceiveValue(uris)
        } else {
            activity.fileUploadCallback?.onReceiveValue(null)
        }
        activity.fileUploadCallback = null
    }

    // Handle Hardware Back Press
    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showSplashScreen) {
            SplashScreen(onSplashFinished = { showSplashScreen = false })
        } else if (!permissionsGranted) {
            PermissionOnboardingScreen(
                onGrantPermissionsRequested = {
                    permissionsGranted = true
                },
                requiredPermissions = requiredPermissions,
                onPermissionsApproved = {
                    permissionsGranted = true
                }
            )
        } else {
            Scaffold(
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "SMKPP Negeri Bima",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Portal Jurnal & Presensi",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            navigationIcon = {
                                if (canGoBack) {
                                    IconButton(onClick = { webViewInstance?.goBack() }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Kembali",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { webViewInstance?.loadUrl("https://jurnnall.web.app/") }) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Beranda",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(onClick = { webViewInstance?.reload() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Muat Ulang",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // Base WebView Configuration
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setGeolocationEnabled(true)
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

                                // Viewports & responsiveness
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.supportZoom()
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        // Open standard external schemas in separate intent handlers (e.g. WA links, mail, etc.)
                                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                                            false // Load within our webview
                                        } else {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                                true
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        canGoBack = view?.canGoBack() ?: false
                                        super.onPageFinished(view, url)
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        val hasGps = ContextCompat.checkSelfPermission(
                                            activity, Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasGps) {
                                            callback?.invoke(origin, true, false)
                                        } else {
                                            activity.pendingGeolocationOrigin = origin
                                            activity.pendingGeolocationCallback = callback
                                            androidx.core.app.ActivityCompat.requestPermissions(
                                                activity,
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                ),
                                                1002
                                            )
                                        }
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        val resources = request?.resources ?: emptyArray()
                                        val hasCameraResource = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

                                        if (hasCameraResource) {
                                            val hasCamera = ContextCompat.checkSelfPermission(
                                                activity, Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED

                                             if (hasCamera) {
                                                 request?.grant(resources)
                                             } else {
                                                 activity.pendingPermissionRequest = request
                                                 androidx.core.app.ActivityCompat.requestPermissions(
                                                     activity,
                                                     arrayOf(Manifest.permission.CAMERA),
                                                     1003
                                                 )
                                             }
                                        } else {
                                            request?.grant(resources)
                                        }
                                    }

                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        activity.fileUploadCallback?.onReceiveValue(null)
                                        activity.fileUploadCallback = filePathCallback

                                        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "*/*"
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }

                                        try {
                                            fileChooserLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            activity.fileUploadCallback?.onReceiveValue(null)
                                            activity.fileUploadCallback = null
                                            return false
                                        }
                                        return true
                                    }
                                }

                                loadUrl("https://jurnnall.web.app/")
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingScreen(
    onGrantPermissionsRequested: () -> Unit,
    requiredPermissions: Array<String>,
    onPermissionsApproved: () -> Unit
) {
    val context = LocalContext.current
    var permissionsPromptedCount by remember { mutableStateOf(0) }

    val checkAllPermissionsGranted = {
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsPromptedCount++
        val allGranted = requiredPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onPermissionsApproved()
        }
    }

    LaunchedEffect(Unit) {
        if (!checkAllPermissionsGranted()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F3B21), // Forest dark green
                        Color(0xFF1B5E20)  // Deep green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_school_logo),
                    contentDescription = "Logo SMKPP Negeri Bima",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SMKPP Negeri Bima",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Sistem Portal Jurnal & Presensi",
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Aplikasi ini memerlukan izin agar sistem absensi online (titik koordinat GPS) dan upload dokumentasi (kamera) berjalan dengan akurat & sah:",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Geolokasi",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Geolokasi (Lokasi GPS)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Wajib aktif saat pengisian absensi jurnal untuk memverifikasi lokasi koordinat Anda di lapangan.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Kamera",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Kamera & Unggah Berkas",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Digunakan saat mengunggah foto bukti fisik kegiatan pembelajaran atau foto profil presensi.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic warning box if permissions were requested but failed
            val currentlyGranted = checkAllPermissionsGranted()
            if (permissionsPromptedCount > 0 && !currentlyGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Peringatan: Izin Belum Lengkap!",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Sistem mendeteksi bahwa beberapa izin penting dinonaktifkan di sistem Android Anda. Silakan klik tombol di bawah untuk mengaktifkannya secara manual.",
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (ex: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Buka Pengaturan Aplikasi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    permissionLauncher.launch(requiredPermissions)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF13502C)
                ),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = if (permissionsPromptedCount > 0) "Minta Izin Lagi" else "Izinkan & Buka Portal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { onGrantPermissionsRequested() }
            ) {
                Text(
                    text = "Lewati untuk navigasi dasar (Absensi GPS mungkin tidak jalan)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        )
    )
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        )
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF072C15), // Very deep green
                        Color(0xFF0F4120), // Rich forest green
                        Color(0xFF1B5E20)  // Solid academic green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_school_logo),
                    contentDescription = "Logo SMKPP Negeri Bima",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "DINAS PERTANIAN DAN KETAHANAN PANGAN",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "SMKPP NEGERI BIMA",
                color = Color(0xFFF1C40F), // Elegant gold label
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "PROVINSI NUSA TENGGARA BARAT",
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 1.6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Sistem Informasi & Absensi Digital",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(44.dp))

            CircularProgressIndicator(
                color = Color(0xFF2EE069), // Brighter matching green loader
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(alpha)
            )
        }
    }
}

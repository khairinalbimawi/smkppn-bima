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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LocationOn
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import com.example.ui.theme.MyApplicationTheme
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.DownloadManager
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

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
        
        // Setup sticky immersive fullscreen mode to hide status bars and navigation bars completely
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        // Render behind cutout/notch area for zero cut-off screen display
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            MyApplicationTheme {
                MainScreen(this)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                // Check if device GPS sensor is turned on
                val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                val isGpsEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                   lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                
                if (isGpsEnabled) {
                    pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, true, false)
                    pendingGeolocationCallback = null
                    pendingGeolocationOrigin = null
                } else {
                    // Force display GPS activation window
                    showGpsSettingsDialog(this, pendingGeolocationOrigin, pendingGeolocationCallback)
                }
            } else {
                pendingGeolocationCallback?.invoke(pendingGeolocationOrigin, false, false)
                pendingGeolocationCallback = null
                pendingGeolocationOrigin = null
            }
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

// Robust activity context resolver extension
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Elegant helper to trigger the system window prompting user to activate device GPS Services
fun showGpsSettingsDialog(
    activity: Activity,
    origin: String?,
    callback: GeolocationPermissions.Callback?
) {
    activity.runOnUiThread {
        android.app.AlertDialog.Builder(activity)
            .setTitle("Aktifkan Layanan Lokasi (GPS)")
            .setMessage("Untuk dapat mengisi absensi jurnal harian dan menggunakan koordinat GPS, silakan aktifkan Layanan Lokasi (GPS) Anda melalui Pengaturan agar presensi Anda dinyatakan sah.")
            .setCancelable(false)
            .setPositiveButton("Buka Pengaturan") { dialog, _ ->
                dialog.dismiss()
                if (activity is MainActivity) {
                    activity.pendingGeolocationOrigin = origin
                    activity.pendingGeolocationCallback = callback
                }
                try {
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                        activity.startActivity(intent)
                    } catch (ex: Exception) {
                        callback?.invoke(origin, false, false)
                    }
                }
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
                callback?.invoke(origin, false, false)
            }
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun MainScreen(activity: MainActivity) {
    var showSplashScreen by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

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

                // If there is a pending Geolocation request and the user returns to the app with GPS enabled
                if (activity.pendingGeolocationCallback != null) {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                    val isGpsActive = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                      lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                    
                    if (hasPerm && isGpsActive) {
                        try {
                            activity.pendingGeolocationCallback?.invoke(activity.pendingGeolocationOrigin, true, false)
                        } catch (e: Exception) {}
                        activity.pendingGeolocationCallback = null
                        activity.pendingGeolocationOrigin = null
                        webViewInstance?.reload()
                    }
                }
                
                // Reapply immersive fullscreen mode when app resumes
                val window = context.findActivity()?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Advanced dynamic options
    var isDesktopMode by remember { mutableStateOf(false) }
    var isDiagnosticsOpen by remember { mutableStateOf(false) }

    // Connection diagnostics ping details
    var pingResult by remember { mutableStateOf<String?>("Belum diuji") }
    var isPinging by remember { mutableStateOf(false) }

    val runPingTest: suspend () -> Unit = {
        isPinging = true
        pingResult = "Mengukur..."
        val startTime = System.currentTimeMillis()
        try {
            withContext(Dispatchers.IO) {
                val urlConnection = java.net.URL("https://www.google.com").openConnection() as java.net.HttpURLConnection
                urlConnection.connectTimeout = 3000
                urlConnection.readTimeout = 3000
                urlConnection.connect()
                val code = urlConnection.responseCode
                val endTime = System.currentTimeMillis()
                if (code in 200..399) {
                    pingResult = "Normal (${endTime - startTime} ms)"
                } else {
                    pingResult = "Eror $code (${endTime - startTime} ms)"
                }
            }
        } catch (e: Exception) {
            pingResult = "Terputus / Lambat"
        } finally {
            isPinging = false
        }
    }

    // Trigger speed test when panel activates
    LaunchedEffect(isDiagnosticsOpen) {
        if (isDiagnosticsOpen) {
            runPingTest()
        }
    }

    // Error recovery states
    var isCustomErrorActive by remember { mutableStateOf(false) }
    var lastErrorCode by remember { mutableStateOf(0) }
    var lastErrorDescription by remember { mutableStateOf("") }
    var lastFailingUrl by remember { mutableStateOf("") }

    // Desktop/Mobile dynamic user-agent trigger
    LaunchedEffect(isDesktopMode) {
        webViewInstance?.let { webView ->
            val settings = webView.settings
            if (isDesktopMode) {
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                settings.userAgentString = null
            }
            webView.reload()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

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
            // Screen Box layout with absolute fill size and zero spacing restrictions to guarantee no cut-offs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (isCustomErrorActive) {
                    ConnectionErrorScreen(
                        lastErrorDescription = lastErrorDescription,
                        lastFailingUrl = lastFailingUrl,
                        onRetryRequested = {
                            isCustomErrorActive = false
                            isLoading = true
                            webViewInstance?.let { webView ->
                                webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                                webView.reload()
                                // restore LOAD_CACHE_ELSE_NETWORK shortly after
                                webView.postDelayed({
                                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                                }, 4000)
                            }
                        },
                        onLoadLocalCache = {
                            isCustomErrorActive = false
                            webViewInstance?.let { webView ->
                                webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                                webView.reload()
                            }
                        }
                    )
                } else {
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
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                // Enable downloads within our web portal
                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    try {
                                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                                            setMimeType(mimetype)
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            addRequestHeader("cookie", cookies)
                                            addRequestHeader("User-Agent", userAgent)
                                            setDescription("Mengunduh berkas Jurnal...")
                                            setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(
                                                Environment.DIRECTORY_DOWNLOADS,
                                                URLUtil.guessFileName(url, contentDisposition, mimetype)
                                            )
                                        }
                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.enqueue(request)
                                        Toast.makeText(ctx, "Mengunduh berkas...", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            ctx.startActivity(intent)
                                        } catch (ex: Exception) {
                                            Toast.makeText(ctx, "Gagal mengunduh: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }

                                // Viewports & responsiveness
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.supportZoom()
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                // Cookie configuration - Crucial for Google Apps Script third-party frame states
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                try {
                                    cookieManager.setAcceptThirdPartyCookies(this, true)
                                } catch (e: Exception) {
                                    // fine if not supported
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: ""
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
                                        isCustomErrorActive = false
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                        super.onPageFinished(view, url)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        // Handle main frame loading error natively
                                        if (request?.isForMainFrame == true) {
                                            lastErrorCode = error?.errorCode ?: -1
                                            lastErrorDescription = error?.description?.toString() ?: "Masalah koneksi"
                                            lastFailingUrl = request.url?.toString() ?: ""
                                            isCustomErrorActive = true
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        webProgress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        val hasGpsPermission = ContextCompat.checkSelfPermission(
                                            activity, Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (!hasGpsPermission) {
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
                                        } else {
                                            val lm = activity.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                                            val isGpsEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                                               lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true

                                            if (isGpsEnabled) {
                                                callback?.invoke(origin, true, false)
                                            } else {
                                                showGpsSettingsDialog(activity, origin, callback)
                                            }
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

                                loadUrl("https://sipegawaismkpp.web.app/")
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Ambient progress line at the very top of the screen
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { webProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }

                // Custom bottom snackbar host to display background updates or issues gently
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (isDiagnosticsOpen) {
        DiagnosticsBottomSheet(
            onDismissRequest = { isDiagnosticsOpen = false },
            isDesktopMode = isDesktopMode,
            onDesktopModeChanged = { isDesktopMode = it },
            pingResult = pingResult,
            isPinging = isPinging,
            onTriggerPing = {
                coroutineScope.launch {
                    runPingTest()
                }
            },
            onClearCacheRequested = {
                webViewInstance?.let { webView ->
                    webView.clearCache(true)
                    webView.clearHistory()
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Cache & cookie berhasil dibersihkan! Memuat ulang portal...")
                            webView.reload()
                        }
                    }
                }
                isDiagnosticsOpen = false
            },
            context = context,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onGoBack = { 
                webViewInstance?.goBack()
                isDiagnosticsOpen = false
            },
            onGoForward = { 
                webViewInstance?.goForward()
                isDiagnosticsOpen = false
            },
            onGoHome = { 
                isCustomErrorActive = false
                webViewInstance?.loadUrl("https://sipegawaismkpp.web.app/")
                isDiagnosticsOpen = false
            },
            onReload = { 
                isCustomErrorActive = false
                webViewInstance?.reload()
                isDiagnosticsOpen = false
            }
        )
    }
}

@Composable
fun ConnectionErrorScreen(
    lastErrorDescription: String,
    lastFailingUrl: String,
    onRetryRequested: () -> Unit,
    onLoadLocalCache: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Peringatan Koneksi",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Koneksi Terputus atau Lemah",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Portal Jurnal tidak dapat dimuat karena koneksi internet di sekolah sedang kurang stabil.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Diagnostic Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Detail Teknis:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pesan: $lastErrorDescription",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "URL: $lastFailingUrl",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onRetryRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Muat Ulang",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Muat Ulang Halaman (Coba Lagi)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLoadLocalCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Gunakan Cache Offline",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gunakan Cadangan Cache (Offline)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    onDismissRequest: () -> Unit,
    isDesktopMode: Boolean,
    onDesktopModeChanged: (Boolean) -> Unit,
    pingResult: String?,
    isPinging: Boolean,
    onTriggerPing: () -> Unit,
    onClearCacheRequested: () -> Unit,
    context: Context,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onReload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 44.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Pusat Diagnostik & Pengaturan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Validasi fungsionalitas sistem & setelan web",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Quick Navigation Control Card (replacing bottom bar buttons)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Navigasi Cepat Portal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onGoBack,
                            enabled = canGoBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sebelumnya")
                        }
                        IconButton(
                            onClick = onGoForward,
                            enabled = canGoForward,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Sesudahnya")
                        }
                        IconButton(
                            onClick = onGoHome,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Beranda")
                        }
                        IconButton(
                            onClick = onReload,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("Kembali", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text("Maju", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text("Beranda", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("Refresh", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 1. Connection Ping Section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Ping Respon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Kecepatan Respon Sekolah",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(
                            onClick = onTriggerPing,
                            enabled = !isPinging,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Cek Ulang",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Status Koneksi (Ke Google):",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = pingResult ?: "Belum dicek",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (pingResult?.contains("Normal") == true) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 2. Desktop Mode Toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tampilan Desktop (PC)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Aktifkan apabila tampilan portal terpotong pada layar HP",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = isDesktopMode,
                        onCheckedChange = onDesktopModeChanged,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // 3. Clear Cache/History/Reset
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Setel Ulang Data Berselancar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Membersihkan cache lokal & cookie apabila terjadi error pemuatan atau portal hang",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onClearCacheRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bersihkan Cache & Reset Sesi",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. GPS Native Status Panel
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Sensor GPS",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Sensor Geolokasi & Keamanan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "Sistem presensi memerlukan verifikasi koordinat sah.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val hasGpsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                        val isGpsActive = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                          lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                        
                        val (statusText, statusColor) = when {
                            !hasGpsPerm -> "IZIN DITOLAK ❌" to MaterialTheme.colorScheme.error
                            !isGpsActive -> "GPS NONAKTIF ⚠️" to Color(0xFFF39C12) // Dark Amber
                            else -> "AKTIF & DISETUJUI ✅" to Color(0xFF10B981) // Bright Emerald
                        }

                        Text(
                            text = "Akses GPS Perangkat:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = statusColor
                        )
                    }
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
                text = "TU KOMBAT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
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
                                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
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
                text = "TU KOMBAT - Portal Jurnal & Absensi",
                color = Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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

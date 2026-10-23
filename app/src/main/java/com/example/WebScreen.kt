package com.example

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun WebScreen(
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showFlashScreen by remember { mutableStateOf(true) }

    // Request system notification permission on Android 13+ so website notifications work seamlessly
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Permission response handled */ }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Snappy branded flash screen: Fades out smoothly in 850ms or as soon as page begins rendering
    LaunchedEffect(Unit) {
        delay(850)
        showFlashScreen = false
    }

    // Dismiss flash screen immediately if page progress passes 70%
    LaunchedEffect(loadingProgress) {
        if (loadingProgress >= 0.7f) {
            showFlashScreen = false
        }
    }

    // Dismiss flash screen immediately if an error occurs
    LaunchedEffect(hasError) {
        if (hasError) {
            showFlashScreen = false
        }
    }

    // Back button handling: If WebView can go back, navigate backwards; otherwise require double-tap to exit
    BackHandler {
        val webView = webViewRef
        if (hasError && webView != null && webView.canGoBack()) {
            hasError = false
            webView.goBack()
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                onExitApp()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, context.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Lifecycle-aware power & RAM optimization: Flush cookies and pause JS timers when app is backgrounded
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    CookieManager.getInstance().flush()
                    webViewRef?.onPause()
                    webViewRef?.pauseTimers()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    webViewRef?.resumeTimers()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Native Android WebView with hardware acceleration and pre-rastering
        AndroidView(
            factory = { ctx ->
                createConfiguredWebView(
                    context = ctx,
                    onLoadingChanged = { loading, progress ->
                        isLoading = loading
                        loadingProgress = progress / 100f
                    },
                    onError = { errorText ->
                        hasError = true
                        errorMessage = errorText
                        showFlashScreen = false
                    },
                    onCanGoBackChanged = { backAvailable ->
                        canGoBack = backAvailable
                    },
                    onOpenFileChooser = onOpenFileChooser,
                    onRendererCrashed = {
                        hasError = true
                        errorMessage = "WebView renderer encountered an issue. Tap Try Again to restore."
                        showFlashScreen = false
                    },
                    onPageVisible = {
                        showFlashScreen = false
                    }
                ).also { wv ->
                    webViewRef = wv
                    wv.loadUrl(UrlDispatcher.BASE_URL)
                }
            },
            update = { wv ->
                webViewRef = wv
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_web_view")
        )

        // Subtle loading progress indicator at top edge
        AnimatedVisibility(
            visible = isLoading && !hasError && !showFlashScreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .testTag("loading_progress_bar"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Native Offline / Error Screen
        if (hasError) {
            ErrorOverlay(
                message = errorMessage.ifEmpty { stringResource(R.string.error_no_internet_msg) },
                onRetry = {
                    hasError = false
                    isLoading = true
                    val wv = webViewRef
                    if (wv != null) {
                        if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                            wv.loadUrl(UrlDispatcher.BASE_URL)
                        } else {
                            wv.reload()
                        }
                    }
                },
                onExit = onExitApp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Branded Flash / Splash Page with Logo
        AnimatedVisibility(
            visible = showFlashScreen,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            FlashScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("flash_page")
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.removeAllViews()
                wv.destroy()
            }
            webViewRef = null
        }
    }
}

/**
 * Ultra-fast Flash/Splash Page featuring the brand logo and title with zero continuous recomposition.
 */
@Composable
fun FlashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF140205), // Darkest Crimson
                        Color(0xFF2E0409), // Rich Ruby Red
                        Color(0xFF190205)  // Deep Wine
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Carlo 567 Brand Logo
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .testTag("flash_logo"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFBBF24), // Amber Gold
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Smooth loading indicator
            CircularProgressIndicator(
                modifier = Modifier
                    .size(26.dp)
                    .testTag("flash_progress"),
                color = Color(0xFFFBBF24),
                strokeWidth = 2.5.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.splash_loading),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier.alpha(0.85f)
            )
        }
    }
}

/**
 * Creates and configures the WebView instance with maximum speed, hardware acceleration,
 * off-screen pre-rasterization, and zero-stutter optimizations.
 */
private fun createConfiguredWebView(
    context: Context,
    onLoadingChanged: (Boolean, Int) -> Unit,
    onError: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    onRendererCrashed: () -> Unit,
    onPageVisible: () -> Unit
): WebView {
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFocusable = true
        isFocusableInTouchMode = true

        // Enable natural vertical scrolling so user can scroll when keyboard is open
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        // Hardware accelerated GPU layer
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Set solid dark background to eliminate white flashing while pages load
        setBackgroundColor(android.graphics.Color.parseColor("#140205"))

        // Add Notification JavaScript bridges so website push/alert triggers work natively
        addJavascriptInterface(NotificationHelper.WebNotificationBridge(context), "AndroidNotification")
        addJavascriptInterface(NotificationHelper.WebNotificationBridge(context), "NativeNotification")
    }

    val settings = webView.settings
    // Natural caching mode ensures login cookies, session tokens, and MPIN auth are preserved
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.loadsImagesAutomatically = true

    // Maximum Scrolling Performance: Pre-raster off-screen content (Android 6.0+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        settings.offscreenPreRaster = true
    }

    // Security hardening: Prevent file scheme escalation and cross-origin file access
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    // Responsive Viewport & Form Input Stability
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.textZoom = 100 // Locks standard font scale so password inputs and admin tables don't wrap/jump
    settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false

    // Window handling (for target="_blank" and window.open)
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = true

    // Cookie & Session configuration
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    // Download Manager setup
    webView.setDownloadListener(createDownloadListener(context))

    // WebViewClient for navigation control and security
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return handleUrlNavigation(context, view, url)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null) return false
            return handleUrlNavigation(context, view, url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onLoadingChanged(true, 25)
            onCanGoBackChanged(view?.canGoBack() ?: false)
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            onPageVisible()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageVisible()
            onLoadingChanged(false, 100)
            onCanGoBackChanged(view?.canGoBack() ?: false)
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)
            // Only handle main frame failures as full-page errors to avoid blocking on failed minor third-party beacons
            if (request?.isForMainFrame == true) {
                val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.errorCode ?: 0
                } else {
                    0
                }
                // Handle actual network connection errors
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                    errorCode == WebViewClient.ERROR_CONNECT ||
                    errorCode == WebViewClient.ERROR_TIMEOUT
                ) {
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Network connection error"
                    } else {
                        "Network connection error"
                    }
                    onError(desc)
                }
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            // Strict SSL Security: Never bypass SSL errors!
            handler?.cancel()
            onError(context.getString(R.string.error_ssl_msg))
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // Prevent app crash when render process terminates unexpectedly
            onRendererCrashed()
            return true
        }
    }

    // WebChromeClient for progress, title, target="_blank", and file uploads
    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            onLoadingChanged(newProgress < 100, newProgress)
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val result = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            // Create a lightweight temporary interceptor to capture window.open destination
            val tempWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        tempView: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUrl = request?.url?.toString()
                        if (targetUrl != null) {
                            if (UrlDispatcher.isInternalUrl(targetUrl)) {
                                view?.loadUrl(targetUrl)
                            } else {
                                UrlDispatcher.dispatch(context, targetUrl)
                            }
                        }
                        tempView?.destroy()
                        return true
                    }
                }
            }
            result.webView = tempWebView
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowFileChooser(
            wv: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            onOpenFileChooser(filePathCallback, fileChooserParams)
            return true
        }

        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
            // Automatically grant website notifications and web permissions
            request?.grant(request.resources)
        }
    }

    return webView
}

/**
 * Routes URL navigation through UrlDispatcher.
 */
private fun handleUrlNavigation(context: Context, webView: WebView?, url: String): Boolean {
    val destination = UrlDispatcher.classifyUrl(url)
    return when (destination) {
        is UrlDispatcher.UrlDestination.Internal -> {
            // Internal URL or script: let the current WebView load it
            false
        }
        else -> {
            // External URL, UPI, WhatsApp, Dial, Mailto, or Custom Intent
            UrlDispatcher.dispatch(context, url)
            true
        }
    }
}

/**
 * Creates a DownloadListener to enqueue file downloads using Android's native DownloadManager.
 */
private fun createDownloadListener(context: Context): DownloadListener {
    return DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                setDescription("Downloading $fileName")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)
            Toast.makeText(context, "${context.getString(R.string.download_started)}: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("error_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_wifi_off),
                    contentDescription = stringResource(R.string.error_no_internet_title),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.error_no_internet_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("retry_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.btn_retry),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("exit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_exit),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

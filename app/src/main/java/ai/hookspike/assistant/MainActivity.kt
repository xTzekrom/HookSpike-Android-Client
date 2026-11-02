package ai.hookspike.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQ_CODE = 1234
    private var isOrbRunning = false

    companion object {
        @SuppressLint("StaticFieldLeak")
        var sharedWebView: WebView? = null

        // Native Android se seedha Web AI ko text bhejna
        fun sendTextToWebAi(text: String) {
            sharedWebView?.post {
                sharedWebView?.evaluateJavascript("dispatchVoiceQuery('$text');", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        val rootLayout = FrameLayout(this)

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Media auto-play allow karna taaki background mein voice aaye
            settings.mediaPlaybackRequiresUserGesture = false 
            
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
            loadUrl("https://hookspike-core.onrender.com")
        }
        sharedWebView = webView
        rootLayout.addView(webView)

        val toggleButton = Button(this).apply {
            text = "⚡ Start Assistant Orb"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))
            setTextColor(android.graphics.Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 80)
            }
            setOnClickListener {
                if (checkOverlayPermission()) toggleOrbService(this)
                else requestOverlayPermission()
            }
        }
        rootLayout.addView(toggleButton)

        setContentView(rootLayout)
    }

    private fun checkOverlayPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), PERMISSION_REQ_CODE)
        }
    }

    private fun toggleOrbService(button: Button) {
        val intent = Intent(this, FloatingOrbService::class.java)
        if (!isOrbRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            isOrbRunning = true
            button.text = "❌ Stop Orb"
        } else {
            stopService(intent)
            isOrbRunning = false
            button.text = "⚡ Start Assistant Orb"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedWebView = null
    }
}

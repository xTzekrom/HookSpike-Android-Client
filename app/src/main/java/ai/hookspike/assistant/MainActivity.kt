package ai.hookspike.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    private var isOrbRunning = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Root Layout Container
        val rootLayout = FrameLayout(this)

        // 2. Full-Screen Web App (Tumhari Live Website)
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            loadUrl("https://hookspike-core.onrender.com")
        }
        rootLayout.addView(webView)

        // 3. Floating Orb Toggle Button (Screen ke bottom par sleek button)
        val toggleButton = Button(this).apply {
            text = "⚡ Enable Floating Orb"
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 50)
            }
            layoutParams = params

            setOnClickListener {
                if (checkOverlayPermission()) {
                    toggleOrbService(this)
                } else {
                    requestOverlayPermission()
                }
            }
        }
        rootLayout.addView(toggleButton)

        setContentView(rootLayout)
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun toggleOrbService(button: Button) {
        val intent = Intent(this, FloatingOrbService::class.java)
        if (!isOrbRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isOrbRunning = true
            button.text = "❌ Disable Floating Orb"
            Toast.makeText(this, "HookSpike Orb Started!", Toast.LENGTH_SHORT).show()
        } else {
            stopService(intent)
            isOrbRunning = false
            button.text = "⚡ Enable Floating Orb"
            Toast.makeText(this, "HookSpike Orb Stopped!", Toast.LENGTH_SHORT).show()
        }
    }
}

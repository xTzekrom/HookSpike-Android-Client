package ai.hookspike.assistant

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingOrbService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var orbView: FrameLayout
    private lateinit var hudView: LinearLayout
    private lateinit var orbParams: WindowManager.LayoutParams
    private lateinit var hudParams: WindowManager.LayoutParams
    
    private val handler = Handler(Looper.getMainLooper())
    private var isHudOpen = false
    private val idleRunnable = Runnable { orbView.animate().alpha(0.35f).setDuration(400).start() }

    // Native Speech Engine
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        setupOrbView()
        setupHudView()
        setupTouchListener()
        setupNativeSpeechEngine()
    }

    private fun setupOrbView() {
        val outerShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 46f
            setColor(Color.parseColor("#E60D0F12"))
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }
        val innerCore = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#10B981"))
        }
        val layers = LayerDrawable(arrayOf(outerShape, innerCore)).apply { setLayerInset(1, 32, 32, 32, 32) }

        orbView = FrameLayout(this).apply {
            background = layers
            elevation = 20f
            alpha = 0.85f
        }

        val size = (62 * resources.displayMetrics.density).toInt()
        orbParams = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 400
        }
        windowManager.addView(orbView, orbParams)
        resetIdleTimer()
    }

    private fun setupHudView() {
        hudView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FA0D0F12"))
            setPadding(40, 40, 40, 40)
            visibility = View.GONE
            
            val title = TextView(this@FloatingOrbService).apply {
                text = "OMNI CONTROLLER"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 12f
                setPadding(0, 0, 0, 30)
                gravity = Gravity.CENTER
            }
            addView(title)
            
            val row1 = LinearLayout(this@FloatingOrbService).apply { orientation = LinearLayout.HORIZONTAL }
            row1.addView(createHudButton("🎙️ Listen") { toggleVoiceMode(); closeHud() })
            row1.addView(createHudButton("🔦 Torch") { Toast.makeText(context, "Torch toggled", Toast.LENGTH_SHORT).show() })
            
            val row2 = LinearLayout(this@FloatingOrbService).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 30, 0, 0) }
            row2.addView(createHudButton("📈 Trade") { launchApp("com.tradingview.tradingviewapp") })
            row2.addView(createHudButton("💻 Code") { launchApp("com.editor.vscode") }) 
            row2.addView(createHudButton("❌ Close") { closeHud() })

            addView(row1)
            addView(row2)
        }

        hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        
        windowManager.addView(hudView, hudParams)
    }

    private fun createHudButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setPadding(15, 25, 15, 25)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun setupNativeSpeechEngine() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                // Listening mode: Turn Gold
                (orbView.background as LayerDrawable).getDrawable(1).setTint(Color.parseColor("#F59E0B"))
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userText = matches[0]
                    // Processing mode: Turn Purple
                    (orbView.background as LayerDrawable).getDrawable(1).setTint(Color.parseColor("#8B5CF6"))
                    // Send to backend via WebView bridge
                    MainActivity.sendTextToWebAi(userText)
                }
                resetOrbColor()
            }
            override fun onError(error: Int) { resetOrbColor() }
            
            // Unused overrides required by interface
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun resetOrbColor() {
        handler.postDelayed({
            (orbView.background as LayerDrawable).getDrawable(1).setTint(Color.parseColor("#10B981"))
        }, 2000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        var isMoving = false
        val longPressRunnable = Runnable { if (!isMoving) openHud() } 

        orbView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isMoving = false
                    initialX = orbParams.x; initialY = orbParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    orbView.animate().alpha(1.0f).scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                    
                    handler.postDelayed(longPressRunnable, 450)
                    resetIdleTimer()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                        isMoving = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (isMoving && !isHudOpen) {
                        orbParams.x = initialX + diffX
                        orbParams.y = initialY + diffY
                        windowManager.updateViewLayout(orbView, orbParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    orbView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    
                    if (!isMoving && !isHudOpen) {
                        toggleVoiceMode() // Single Tap -> Native Listen
                    } else if (isMoving) {
                        snapToEdge()
                    }
                    resetIdleTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun openHud() {
        isHudOpen = true
        orbView.visibility = View.GONE
        hudView.visibility = View.VISIBLE
    }

    private fun closeHud() {
        isHudOpen = false
        hudView.visibility = View.GONE
        orbView.visibility = View.VISIBLE
        resetIdleTimer()
    }

    private fun toggleVoiceMode() {
        speechRecognizer?.startListening(speechIntent)
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
        }
        closeHud()
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (orbParams.x < screenWidth / 2) 16 else screenWidth - orbView.width - 16
        val animator = ValueAnimator.ofInt(orbParams.x, targetX).apply { duration = 220 }
        animator.addUpdateListener { 
            orbParams.x = it.animatedValue as Int
            windowManager.updateViewLayout(orbView, orbParams)
        }
        animator.start()
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, 3500)
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("hs_channel", "HookSpike Live", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        startForeground(1, NotificationCompat.Builder(this, "hs_channel").setContentTitle("HookSpike").setContentText("Assistant Active").setSmallIcon(android.R.drawable.ic_menu_compass).build())
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        if (::orbView.isInitialized) windowManager.removeView(orbView)
        if (::hudView.isInitialized) windowManager.removeView(hudView)
    }
}

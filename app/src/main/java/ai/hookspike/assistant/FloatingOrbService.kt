package ai.hookspike.assistant

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat

class FloatingOrbService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var touchView: FrameLayout
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        touchView.animate().alpha(0.35f).setDuration(400).start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupAssistiveTouchView()
        setupTouchListener()
    }

    private fun setupAssistiveTouchView() {
        // Outer dark translucent glass background
        val outerShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 42f // Soft rounded squircle (Apple style)
            setColor(Color.parseColor("#991C1C1E")) // Translucent Dark Gray
            setStroke(2, Color.parseColor("#44FFFFFF")) // Subtle glass border
        }

        // Inner glowing core
        val innerCore = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#D9FFFFFF")) // Crisp white center dot
        }

        val layers = LayerDrawable(arrayOf(outerShape, innerCore)).apply {
            setLayerInset(1, 24, 24, 24, 24) // Center dot inside outer shell
        }

        touchView = FrameLayout(this).apply {
            background = layers
            elevation = 16f
            alpha = 0.85f
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Sleek 60dp size
        val sizeInPx = (58 * resources.displayMetrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            sizeInPx,
            sizeInPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        windowManager.addView(touchView, layoutParams)
        resetIdleTimer()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        touchView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchView.animate().alpha(1.0f).scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                    idleHandler.removeCallbacks(idleRunnable)

                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                        isClick = false
                    }

                    layoutParams.x = initialX + diffX
                    layoutParams.y = initialY + diffY
                    windowManager.updateViewLayout(touchView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    touchView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()

                    if (isClick) {
                        // User tap feedback: pulse green aura
                        touchView.animate().alpha(1.0f).setDuration(100).withEndAction {
                            resetIdleTimer()
                        }.start()
                    } else {
                        // Smooth Edge Snapping (Apple AssistiveTouch style: left ya right wall par chipakna)
                        snapToEdge()
                    }
                    resetIdleTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = touchView.width

        val targetX = if (layoutParams.x + (viewWidth / 2) < screenWidth / 2) {
            16 // Snap to Left edge
        } else {
            screenWidth - viewWidth - 16 // Snap to Right edge
        }

        val startX = layoutParams.x
        val animator = ValueAnimator.ofInt(startX, targetX)
        animator.duration = 220
        animator.addUpdateListener { animation ->
            layoutParams.x = animation.animatedValue as Int
            windowManager.updateViewLayout(touchView, layoutParams)
        }
        animator.start()
    }

    private fun resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 2500)
    }

    private fun startForegroundNotification() {
        val channelId = "hookspike_assistive_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HookSpike Assistive Touch",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("HookSpike Active")
            .setContentText("Assistive Touch active on screen")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleHandler.removeCallbacks(idleRunnable)
        if (::touchView.isInitialized) {
            windowManager.removeView(touchView)
        }
    }
}

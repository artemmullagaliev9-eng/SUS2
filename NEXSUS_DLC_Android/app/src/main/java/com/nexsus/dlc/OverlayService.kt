package com.nexsus.dlc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "com.nexsus.dlc.START"
        private const val CHANNEL = "nexsus_overlay"
        private const val NOTIFICATION_ID = 41
        private const val PREFS = "nexsus_settings"
    }

    private lateinit var wm: WindowManager
    private var menuView: View? = null
    private var crossView: TextView? = null
    private var fpsView: TextView? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var crossParams: WindowManager.LayoutParams? = null
    private var fpsParams: WindowManager.LayoutParams? = null

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var crossEnabled = true
    private var fpsEnabled = true
    private var crossShape = "＋"
    private var crossSize = 30
    private var crossColor = Color.WHITE
    private var customFps = 120
    private var selectedTheme = "Чорна"

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        loadSettings()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showCrosshair()
        showFps()
        showMenu()
        updateOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        remove(menuView); remove(crossView); remove(fpsView)
        menuView = null; crossView = null; fpsView = null
        super.onDestroy()
    }

    private fun loadSettings() {
        crossEnabled = prefs.getBoolean("crossEnabled", true)
        fpsEnabled = prefs.getBoolean("fpsEnabled", true)
        crossShape = prefs.getString("crossShape", "＋") ?: "＋"
        crossSize = prefs.getInt("crossSize", 30)
        crossColor = prefs.getInt("crossColor", Color.WHITE)
        customFps = prefs.getInt("customFps", 120)
        selectedTheme = prefs.getString("theme", "Чорна") ?: "Чорна"
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("crossEnabled", crossEnabled)
            .putBoolean("fpsEnabled", fpsEnabled)
            .putString("crossShape", crossShape)
            .putInt("crossSize", crossSize)
            .putInt("crossColor", crossColor)
            .putInt("customFps", customFps)
            .putString("theme", selectedTheme)
            .apply()
    }

    private fun layoutParams(gravity: Int, flags: Int, x: Int = 0, y: Int = 0) =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }

    private fun showCrosshair() {
        crossView = TextView(this).apply {
            text = crossShape
            textSize = crossSize.toFloat()
            setTextColor(crossColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        crossParams = layoutParams(
            Gravity.CENTER,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        wm.addView(crossView, crossParams)
    }

    private fun showFps() {
        fpsView = TextView(this).apply {
            text = "FPS: $customFps"
            textSize = 13f
            setTextColor(0xFFBD75FF.toInt())
            setPadding(12, 7, 12, 7)
            background = rounded(0xDD171322.toInt(), 0xFFBD75FF.toInt())
        }
        fpsParams = layoutParams(
            Gravity.TOP or Gravity.START,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            12, 48
        )
        wm.addView(fpsView, fpsParams)
        fpsView?.setOnTouchListener(dragListener(fpsView!!, fpsParams!!))
    }

    private fun showMenu() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = rounded(themePanel(), themeAccent())
        }
        val title = TextView(this).apply {
            text = "NEXSUS DLC   ⋮"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        panel.addView(title)
        
        panel.addView(actionButton(if (crossEnabled) "Приціл: УВІМКНЕНО" else "Приціл: ВИМКНЕНО") {
            crossEnabled = !crossEnabled; saveSettings(); updateOverlays(); rebuildMenu()
        })
        panel.addView(actionButton(if (fpsEnabled) "FPS-бейдж: УВІМКНЕНО" else "FPS-бейдж: ВИМКНЕНО") {
            fpsEnabled = !fpsEnabled; saveSettings(); updateOverlays(); rebuildMenu()
        })
        panel.addView(actionButton("Форма прицілу: $crossShape") {
            crossShape = when (crossShape) { "＋" -> "●"; "●" -> "□"; else -> "＋" }
            saveSettings(); updateOverlays(); rebuildMenu()
        })
        panel.addView(actionButton("Колір прицілу: змінити") {
            crossColor = when (crossColor) {
                Color.WHITE -> Color.RED
                Color.RED -> Color.GREEN
                Color.GREEN -> Color.CYAN
                else -> Color.WHITE
            }
            saveSettings(); updateOverlays()
        })
        panel.addView(actionButton("Зменшити приціл") {
            crossSize = (crossSize - 2).coerceAtLeast(12); saveSettings(); updateOverlays()
        })
        panel.addView(actionButton("Збільшити приціл") {
            crossSize = (crossSize + 2).coerceAtMost(64); saveSettings(); updateOverlays()
        })

        val fpsInput = EditText(this).apply {
            setSingleLine(true)
            hint = "Власне число FPS (1–1000)"
            setText(customFps.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFCCCCCC.toInt())
            background = rounded(0xFF303044.toInt(), themeAccent())
        }
        panel.addView(fpsInput)
        panel.addView(actionButton("Застосувати число FPS") {
            val n = fpsInput.text.toString().toIntOrNull()
            if (n != null && n in 1..1000) {
                customFps = n; saveSettings(); updateOverlays()
            } else {
                fpsInput.error = "Введи число від 1 до 1000"
            }
        })

        panel.addView(actionButton("Стиль: $selectedTheme") {
            selectedTheme = when (selectedTheme) {
                "Чорна" -> "Біла"
                "Біла" -> "Halloween"
                "Halloween" -> "Новорічна"
                else -> "Чорна"
            }
            saveSettings()
            menuView?.let { (it as? LinearLayout)?.background = rounded(themePanel(), themeAccent()) }
            updateOverlays()
        })
        panel.addView(actionButton("Згорнути меню") { collapseMenu() })
        panel.addView(actionButton("Закрити плаваюче меню") { stopSelf() })

        menuView = panel
        menuParams = layoutParams(
            Gravity.TOP or Gravity.START,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            18, 110
        )
        wm.addView(menuView, menuParams)
        title.setOnTouchListener(dragListener(panel, menuParams!!))
    }

    private fun rebuildMenu() {
        val old = menuView
        remove(old)
        menuView = null
        showMenu()
    }

    private fun collapseMenu() {
        val mini = TextView(this).apply {
            text = "NEXSUS"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(14, 10, 14, 10)
            background = rounded(themePanel(), themeAccent())
            setOnClickListener { remove(this); menuView = null; showMenu() }
        }
        remove(menuView)
        menuView = mini
        menuParams = layoutParams(Gravity.TOP or Gravity.START, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, 18, 110)
        wm.addView(mini, menuParams)
        mini.setOnTouchListener(dragListener(mini, menuParams!!))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(0xFF39465B.toInt(), themeAccent())
        setOnClickListener { action() }
    }

    private fun updateOverlays() {
        crossView?.visibility = if (crossEnabled) View.VISIBLE else View.GONE
        crossView?.text = crossShape
        crossView?.textSize = crossSize.toFloat()
        crossView?.setTextColor(crossColor)
        fpsView?.visibility = if (fpsEnabled) View.VISIBLE else View.GONE
        fpsView?.text = "FPS: $customFps"
        fpsView?.background = rounded(themePanel(), themeAccent())
        saveSettings()
    }

    private fun themeAccent(): Int = when (selectedTheme) {
        "Біла" -> 0xFF6384B8.toInt()
        "Halloween" -> 0xFFFF9B36.toInt()
        "Новорічна" -> 0xFF4CC9F0.toInt()
        else -> 0xFFBD75FF.toInt()
    }

    private fun themePanel(): Int = when (selectedTheme) {
        "Біла" -> 0xFF414958.toInt()
        "Halloween" -> 0xFF49250F.toInt()
        "Новорічна" -> 0xFF142923.toInt()
        else -> 0xFF171322.toInt()
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = 18f; setStroke(2, stroke)
    }

    private fun dragListener(view: View, params: WindowManager.LayoutParams?) =
        View.OnTouchListener { _, event ->
            if (params == null) return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.setTag(R.id.drag_data, floatArrayOf(event.rawX, event.rawY, params.x.toFloat(), params.y.toFloat()))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val data = view.getTag(R.id.drag_data) as? FloatArray ?: return@OnTouchListener false
                    params.x = (data[2] + event.rawX - data[0]).toInt()
                    params.y = (data[3] + event.rawY - data[1]).toInt()
                    wm.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

    private fun remove(view: View?) {
        if (view != null) try { wm.removeView(view) } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "NEXSUS floating menu", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyNotificationBuilder() = Notification.Builder(this)

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            else legacyNotificationBuilder()
        return builder
            .setContentTitle("NEXSUS DLC працює")
            .setContentText("Плаваючі елементи активні. Натисни «Зупинити» в застосунку, щоб завершити.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

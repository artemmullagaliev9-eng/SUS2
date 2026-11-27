package com.nexsus.dlc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFFF5F3FF.toInt())
        }
        root.addView(TextView(this).apply {
            text = "NEXSUS DLC"
            textSize = 26f
            setTextColor(0xFF281344.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Плаваючий приціл, панель і ручний FPS-бейдж. Налаштування зберігаються на пристрої."
            textSize = 15f
            setPadding(0, 14, 0, 20)
        })
        root.addView(makeButton("1. Дозволити показ поверх інших програм") {
            if (!Settings.canDrawOverlays(this)) {
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(i)
            } else Toast.makeText(this, "Дозвіл уже надано", Toast.LENGTH_SHORT).show()
        })
        root.addView(makeButton("2. Запустити плаваюче меню") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Спочатку надай дозвіл поверх інших програм", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                Toast.makeText(this, "Плаваюче меню запущено", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(makeButton("3. Зупинити плаваюче меню") {
            stopService(Intent(this, OverlayService::class.java))
        })
        root.addView(TextView(this).apply {
            text = "Примітка: FPS-бейдж показує введене тобою число, а не реальну частоту кадрів Standoff 2. Цей застосунок не змінює гру."
            textSize = 13f
            setPadding(0, 22, 0, 0)
        })
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }
}

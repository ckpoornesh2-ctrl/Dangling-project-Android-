package com.example.dangleheroes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(16, 20, 34))
        }
        val title = TextView(this).apply {
            text = "DANGLE HEROES"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Japanese Chibi Edition"
            textSize = 18f
            setTextColor(Color.rgb(245, 190, 145))
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad)
        }
        val info = TextView(this).apply {
            text = "Five original characters with seven animated poses each.\n\nSora · Hana · Kaito · Miyu · Ren\n\nPull down and release to sling. Drag sideways to steer. Tap the character to switch."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad * 2)
        }
        val start = Button(this).apply {
            text = "START OVERLAY"
            setOnClickListener { startOverlay() }
        }
        val stop = Button(this).apply {
            text = "STOP OVERLAY"
            setOnClickListener {
                stopService(Intent(this@MainActivity, DangleOverlayService::class.java))
            }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(info, LinearLayout.LayoutParams(-1, -2))
        root.addView(start, LinearLayout.LayoutParams(-1, -2))
        root.addView(stop, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(Intent(this, DangleOverlayService::class.java))
        } else {
            startService(Intent(this, DangleOverlayService::class.java))
        }
    }
}

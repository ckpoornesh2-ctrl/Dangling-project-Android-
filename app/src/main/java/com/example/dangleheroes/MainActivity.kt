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
            setBackgroundColor(Color.rgb(16, 19, 29))
        }
        val title = TextView(this).apply {
            text = "LUCKY DANGLE"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "A lucky charm for your screen."
            textSize = 17f
            setTextColor(Color.rgb(224, 184, 111))
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad)
        }
        val info = TextView(this).apply {
            text = "One realistic 3D charm at a time.\n\nEvil Eye · Chili & Lemon · Daruma · Lucky Cat · Oni Mask\n\nTap the charm to switch to the next one. Drag it down and release to sling it. Drag it to the bottom of the screen and release to close the overlay."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad * 2)
        }
        val start = Button(this).apply {
            text = "START LUCKY DANGLE"
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

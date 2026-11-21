package com.example.dangleheroes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(14, 17, 27))
        }
        val title = TextView(this).apply {
            text = "DANGLE HEROES"; textSize = 28f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; letterSpacing = 0.12f
        }
        val intro = TextView(this).apply {
            text = "A full-screen swinging hero overlay.\\n\\nPull the hero down to stretch the tether, then release to sling. Drag sideways to steer. Tap to switch heroes. The rest of your screen stays interactive."
            textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad * 2)
        }
        val start = Button(this).apply { text = "START DANGLING HERO"; setOnClickListener { startOverlay() } }
        val stop = Button(this).apply {
            text = "STOP OVERLAY"
            setOnClickListener { stopService(Intent(this@MainActivity, DangleOverlayService::class.java)) }
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(intro, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        val intent = Intent(this, DangleOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
}

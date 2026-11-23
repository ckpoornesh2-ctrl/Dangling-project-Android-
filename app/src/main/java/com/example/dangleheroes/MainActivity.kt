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

/** One-time setup screen. After starting, the small top hook controls the widget. */
class MainActivity : Activity() {
    private var musicButton: Button? = null

    override fun onResume() {
        super.onResume()
        musicButton?.text = if (MediaSessionControls.hasNotificationAccess(this)) {
            "MUSIC CONTROLS ENABLED"
        } else {
            "ENABLE MUSIC CONTROLS"
        }
    }

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
            text = "A little motion for your screen."
            textSize = 17f
            setTextColor(Color.rgb(224, 184, 111))
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad)
        }
        val info = TextView(this).apply {
            text = "Start the floating handle once. It stays at the top of your screen.\n\nDRAG THE TOP HOOK LEFT OR RIGHT to choose its position. Pull it DOWN to show your charm, or push it UP to hide it.\n\nTap a charm to switch. Hold for settings. Short pull down = play/pause; swipe left/right = next/previous track. Long pull = sling."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }
        val start = Button(this).apply {
            text = "START FLOATING HANDLE"
            setOnClickListener { startOverlay() }
        }
        val music = Button(this).apply {
            text = if (MediaSessionControls.hasNotificationAccess(this@MainActivity)) {
                "MUSIC CONTROLS ENABLED"
            } else {
                "ENABLE MUSIC CONTROLS"
            }
            setOnClickListener { openMusicAccessSettings() }
        }
        musicButton = music
        val stop = Button(this).apply {
            text = "STOP FLOATING HANDLE"
            setOnClickListener {
                stopService(Intent(this@MainActivity, DangleOverlayService::class.java))
            }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(info, LinearLayout.LayoutParams(-1, -2))
        root.addView(start, LinearLayout.LayoutParams(-1, -2))
        root.addView(music, LinearLayout.LayoutParams(-1, -2))
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
        val serviceIntent = Intent(this, DangleOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun openMusicAccessSettings() {
        if (MediaSessionControls.hasNotificationAccess(this)) return
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            // Older/fallback Android builds may expose only the main settings page.
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

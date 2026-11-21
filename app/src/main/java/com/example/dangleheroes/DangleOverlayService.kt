package com.example.dangleheroes

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs

class DangleOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var renderer: DangleView? = null
    private var touch: DangleTouchView? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var lastX=Int.MIN_VALUE; private var lastY=Int.MIN_VALUE

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(77, notification())
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        installOverlay()
    }

    private fun installOverlay() {
        val draw=DangleView(this); renderer=draw
        val full=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.START }
        wm.addView(draw,full)

        val hit=DangleTouchView(this,draw); touch=hit
        val hp=WindowManager.LayoutParams(
            dp(104),dp(112),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.START; x=0; y=dp(150) }
        touchParams=hp
        wm.addView(hit,hp)
        draw.onHeroPosition={x,y ->
            val p=touchParams
            if(p!=null) {
                val nx=(x-dp(52)).toInt()
                val ny=(y-dp(45)).toInt()
                if(abs(nx-lastX)>dp(3)||abs(ny-lastY)>dp(3)) {
                    lastX=nx; lastY=ny; p.x=nx; p.y=ny
                    try { wm.updateViewLayout(hit,p) } catch (_:Exception) {}
                }
            }
        }
    }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            val ch=NotificationChannel("dangle_heroes","Dangle Heroes",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    private fun notification():Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,DangleOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"dangle_heroes") else Notification.Builder(this)
        return b.setContentTitle("Dangle Heroes is running")
            .setContentText("Pull down and release to sling · Tap to change hero")
            .setSmallIcon(android.R.drawable.star_on)
            .addAction(Notification.Action.Builder(null,"Stop",stop).build())
            .setOngoing(true).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        touch?.let { try { wm.removeView(it) } catch(_:Exception){} }
        renderer?.let { try { wm.removeView(it) } catch(_:Exception){} }
        if(Build.VERSION.SDK_INT>=24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
    companion object { const val ACTION_STOP="com.example.dangleheroes.STOP" }
}

package name.nailgun.adbhttpserver

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*

class HttpServerService : Service() {
    
    private val binder = LocalBinder()
    private var httpServer: HttpServer? = null
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "HttpServerChannel"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): HttpServerService = this@HttpServerService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_SERVER" -> {
                val port = intent.getIntExtra("port", 8080)
                startServer(port)
            }
            "STOP_SERVER" -> {
                stopServer()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onDestroy() {
        stopServer()
        releaseWakeLock()
        super.onDestroy()
    }
    
    private fun startServer(port: Int) {
        if (httpServer != null) {
            return
        }
        
        startForeground(NOTIFICATION_ID, createNotification("Server starting..."))
        
        httpServer = HttpServer(port) { message ->
            // Update notification with log message
            updateNotification(message)
            
            // Broadcast log message to activity
            val broadcastIntent = Intent("SERVER_LOG_MESSAGE").apply {
                putExtra("message", message)
            }
            sendBroadcast(broadcastIntent)
        }
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                httpServer?.start()
            } catch (e: Exception) {
                // Handle server errors
                val broadcastIntent = Intent("SERVER_ERROR").apply {
                    putExtra("error", e.message)
                }
                sendBroadcast(broadcastIntent)
            }
        }
        
        // Notify activity that server started
        val broadcastIntent = Intent("SERVER_STATUS_CHANGED").apply {
            putExtra("status", "RUNNING")
            putExtra("port", port)
        }
        sendBroadcast(broadcastIntent)
    }
    
    private fun stopServer() {
        serverJob?.cancel()
        httpServer?.stop()
        httpServer = null
        
        // Notify activity that server stopped
        val broadcastIntent = Intent("SERVER_STATUS_CHANGED").apply {
            putExtra("status", "STOPPED")
        }
        sendBroadcast(broadcastIntent)
        
        stopForeground(true)
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HTTP Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ADB HTTP Server notifications"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ADB HTTP Server")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ADBHttpServer::WakeLock"
        )
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}

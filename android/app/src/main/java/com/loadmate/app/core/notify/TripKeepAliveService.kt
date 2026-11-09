package com.loadmate.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.loadmate.app.R

/** 운행 중 앱이 내비 뒤에 있어도 프로세스를 유지하는 경계. 테스트에서는 Fake 를 쓴다 */
interface TripKeepAlive {
    fun start()
    fun stop()
}

class AndroidTripKeepAlive(context: Context) : TripKeepAlive {
    private val ctx = context.applicationContext
    private val intent get() = Intent(ctx, TripKeepAliveService::class.java)

    // 뒤에 있는 앱은 서비스를 시작할 수 없다. 실패해도 음성·알림은 앱이 살아 있는 동안 동작하므로 기록만 남긴다
    override fun start() {
        runCatching { ContextCompat.startForegroundService(ctx, intent) }
            .onFailure { Log.w(TAG, "운행 유지 서비스 시작 실패: $it") }
    }

    override fun stop() {
        ctx.stopService(intent)
    }

    private companion object {
        const val TAG = "LoadmateKeepAlive"
    }
}

/** 프로세스 유지만 한다. 운행 감시는 TripSession 이 맡고, 조용한 알림이라 내비 화면을 가리지 않는다 */
class TripKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "운행 유지", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.pleos_ic_info_line)
            .setContentTitle("Loadmate 운행 중")
            .setContentText("운행을 마치면 자동으로 꺼집니다")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val CHANNEL = "trip_keep_alive"
        const val NOTIFICATION_ID = 100
    }
}

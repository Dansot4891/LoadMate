package com.loadmate.app.core.notify

import com.loadmate.app.R
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.loadmate.app.MainActivity

/**
 * 위쪽에 작게 뜨는 헤드업 알림. 도착, 짐칸 이상, 운전 중 뒷문 열림에 쓴다. 내비 화면을 바꾸지 않고, 누를지는 기사가 정한다
 * (2026-11-15 사용자 결정. 그전에는 운전 중 음성만 썼다).
 * 뒤에 있는 앱은 스스로 앞으로 나올 수 없어(실측) 이 알림을 눌러야 앱으로 전환된다.
 * 실측(2026-11-08): 카테고리를 지정해야 내비 위 헤드업으로 뜬다 (CATEGORY_MESSAGE / CATEGORY_NAVIGATION).
 */
class DriverNotifier(context: Context) {
    private val ctx = context.applicationContext
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "운행 안내", NotificationManager.IMPORTANCE_HIGH))
    }

    fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            id,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.pleos_ic_info_line)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel(id: Int) = nm.cancel(id)

    companion object {
        const val CHANNEL = "trip"
        const val ID_ROUTE = 1
        const val ID_ISSUE = 2
        const val ID_ARRIVAL = 3
        const val ID_COUNT = 4
        const val ID_REAR_DOOR = 5
    }
}

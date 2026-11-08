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
 * 내비가 화면을 덮고 있을 때의 짧은 알림 (F32·R09). 화면을 강제로 바꾸지 않는다.
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
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

    companion object {
        const val CHANNEL = "trip"
        const val ID_ROUTE = 1
        const val ID_ISSUE = 2
    }
}

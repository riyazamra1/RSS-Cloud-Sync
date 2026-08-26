package com.riyaz.rsscloudsync

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** Compact toolbar notification bell with a conditional unread indicator. */
class NotificationBellView(context: Context) : FrameLayout(context) {
    private val badge = View(context)

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Notifications"

        val bell = ImageView(context).apply {
            setImageResource(R.drawable.ic_notification_bell)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LayoutParams(dp(48), dp(48), Gravity.CENTER)
        }
        addView(bell)

        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(239, 68, 68))
        }
        badge.layoutParams = LayoutParams(dp(8), dp(8), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(9)
            rightMargin = dp(9)
        }
        addView(badge)

        setOnClickListener {
            context.startActivity(Intent(context, NotificationsActivity::class.java))
        }
        refreshBadge()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshBadge()
    }

    private fun refreshBadge() {
        val unread = context.getSharedPreferences("rss_cloud_sync", Context.MODE_PRIVATE)
            .getInt("unread_notifications", 0)
        badge.visibility = if (unread > 0) VISIBLE else GONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

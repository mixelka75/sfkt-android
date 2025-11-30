package wtf.mxl.sfkt.data.network

import com.google.gson.annotations.SerializedName

data class SubscriptionInfo(
    @SerializedName("traffic_used_bytes")
    val trafficUsedBytes: Long,

    @SerializedName("traffic_limit_bytes")
    val trafficLimitBytes: Long?,

    @SerializedName("is_premium")
    val isPremium: Boolean,

    @SerializedName("subscription_days_left")
    val subscriptionDaysLeft: Int?
) {
    fun getUsedTrafficFormatted(): String {
        return formatBytes(trafficUsedBytes)
    }

    fun getLimitTrafficFormatted(): String {
        return if (trafficLimitBytes == null) {
            "∞"
        } else {
            formatBytes(trafficLimitBytes)
        }
    }

    fun getTrafficPercentage(): Int {
        return if (trafficLimitBytes == null || trafficLimitBytes == 0L) {
            0
        } else {
            ((trafficUsedBytes.toDouble() / trafficLimitBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
        }
    }

    fun getDaysLeftText(): String {
        return when {
            subscriptionDaysLeft == null -> "Нет подписки"
            subscriptionDaysLeft == 0 -> "Истекает сегодня"
            subscriptionDaysLeft == 1 -> "1 день"
            subscriptionDaysLeft in 2..4 -> "$subscriptionDaysLeft дня"
            else -> "$subscriptionDaysLeft дней"
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

package com.tiger.tigeraibooking.utils

import android.content.Context
import java.util.UUID

class HandleFirstLaunch {
    fun getOrCreateUUID(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var uuid = prefs.getString("app_uuid", null)

        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("app_uuid", uuid).apply()
        }

        return uuid.replace("-", "")
    }


}


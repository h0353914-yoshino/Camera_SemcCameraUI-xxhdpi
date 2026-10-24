package com.sonyericsson.android.camera.debug

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.Locale















object DebugParameterUtils {

    const val KEY_DEBUG_ALWAYS_PREDICTIVE_CAPTURE = "KEY_DEBUG_ALWAYS_PREDICTIVE_CAPTURE"

    const val KEY_DEBUG_DISABLE_AUTO_POWER_OFF = "KEY_DEBUG_DISABLE_AUTO_POWER_OFF"

    const val KEY_DEBUG_DISABLE_LOW_POWER_MODE = "KEY_DEBUG_DISABLE_LOW_POWER_MODE"

    const val KEY_DEBUG_EMULATE_SIDETOUCH = "KEY_DEBUG_EMULATE_SIDETOUCH"

    const val KEY_DEBUG_FORCE_MIGRATE_SETTINGS = "KEY_DEBUG_FORCE_MIGRATE_SETTINGS"

    const val SHARED_PREFERENCES_CAMERA_DEBUG = "com.sonyericsson.android.camera.shared_preferences_debug"

    val isUserBuild by lazy {
        Build.TYPE.lowercase(Locale.ENGLISH) == "user"
    }









    fun preload(context: Context) {
        getSharedPreferences(context)
    }

    fun reset(context: Context) {
        getSharedPreferences(context)?.edit()?.run {
            remove(KEY_DEBUG_DISABLE_AUTO_POWER_OFF)
            remove(KEY_DEBUG_DISABLE_LOW_POWER_MODE)
            remove(KEY_DEBUG_ALWAYS_PREDICTIVE_CAPTURE)
            remove(KEY_DEBUG_EMULATE_SIDETOUCH)
            apply()
        }
    }












    fun isAutoPowerOffDisabled(context: Context): Boolean {
        return isTrue(context, KEY_DEBUG_DISABLE_AUTO_POWER_OFF)
    }








    fun isLowPowerModeDisabled(context: Context): Boolean {
        return isTrue(context, KEY_DEBUG_DISABLE_LOW_POWER_MODE)
    }








    fun isAlwaysPredictiveCaptureEnabled(context: Context): Boolean {
        return isTrue(context, KEY_DEBUG_ALWAYS_PREDICTIVE_CAPTURE)
    }








    fun isEmulateSideTouchEnabled(context: Context): Boolean {
        return isTrue(context, KEY_DEBUG_EMULATE_SIDETOUCH)
    }

    private fun isTrue(context: Context, key: String): Boolean {
        if (isUserBuild) return false
        val prefs = getSharedPreferences(context)
        return prefs != null && prefs.getBoolean(key, false)
    }




    private fun getSharedPreferences(context: Context): SharedPreferences? {
        return if (isUserBuild) null
        else context.getSharedPreferences(SHARED_PREFERENCES_CAMERA_DEBUG, Context.MODE_PRIVATE)
    }



}

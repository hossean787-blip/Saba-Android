package ir.saba.invoice.data

import android.content.Context

/** Device-wide, non-secret preferences; synchronous reads before rendering the first frame. */
internal class SuraDeviceSettings(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("sura_device_settings",Context.MODE_PRIVATE)
    val protectScreen:Boolean get()=prefs.getBoolean("protect_screen",false)
    val playIntro:Boolean get()=prefs.getBoolean("play_intro",true)
    fun setProtectScreen(value:Boolean):Boolean=prefs.edit().putBoolean("protect_screen",value).commit()
    fun setPlayIntro(value:Boolean):Boolean=prefs.edit().putBoolean("play_intro",value).commit()
}

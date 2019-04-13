package se.quizhelp

import android.content.Context

class MainPreference(context : Context) {

    val PREFERENCE_NAME = "Quiz Help"
    val SCREEN_HEIGHT = ""
    val SCREEN_WIDTH = ""

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getScreenWidth() : String? {
        return preference.getString(SCREEN_WIDTH, null)
    }

    fun getScreenHeight() : String? {
        return preference.getString(SCREEN_HEIGHT, null)
    }

    fun setScreenWidth(width: String) {
        val editor = preference.edit()
        editor.putString(SCREEN_WIDTH, width)
        editor.apply()
    }

    fun setScreenHeight(height : String) {
        val editor = preference.edit()
        editor.putString(SCREEN_HEIGHT, height)
        editor.apply()
    }

}
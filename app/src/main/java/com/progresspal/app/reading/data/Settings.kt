package com.progresspal.app.reading.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("reading_settings", Context.MODE_PRIVATE)
    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_GOAL, 20))
    val dailyGoal: StateFlow<Int> = _dailyGoal

    fun setDailyGoal(goal: Int) {
        val g = goal.coerceIn(1, 1000)
        prefs.edit().putInt(KEY_GOAL, g).apply()
        _dailyGoal.value = g
    }

    private companion object {
        const val KEY_GOAL = "daily_goal"
    }
}

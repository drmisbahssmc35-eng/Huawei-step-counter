package com.progresspal.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The order the user dragged the Daily Tasks into (task ids), kept on the phone. */
object DailyOrder {
    private lateinit var prefs: SharedPreferences
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    val ids: StateFlow<List<String>> = _ids

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("daily_order", Context.MODE_PRIVATE)
        _ids.value = prefs.getString("ids", "")!!.split(',').filter { it.isNotBlank() }
    }

    /** Puts the day's tasks in the saved order; tasks never dragged keep their time order at the end. */
    fun <T> sort(items: List<T>, id: (T) -> String): List<T> {
        val pos = _ids.value.withIndex().associate { it.value to it.index }
        return items.withIndex().sortedWith(compareBy({ pos[id(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }

    /** Saves the new order of [dayIds], keeping the relative place of tasks not shown today. */
    fun save(dayIds: List<String>) {
        val rest = _ids.value.filterNot { it in dayIds }
        val next = dayIds + rest
        _ids.value = next
        if (::prefs.isInitialized) prefs.edit().putString("ids", next.joinToString(",")).apply()
    }
}

package com.progresspal.app.reading.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE books SET lastPage = :page, lastReadAt = :now WHERE id = :id")
    suspend fun updateLastPage(id: Long, page: Int, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVisit(visit: PageVisit): Long

    @Query("SELECT day, COUNT(*) AS pages FROM page_visits GROUP BY day ORDER BY day")
    fun observeDailyCounts(): Flow<List<DayCount>>

    @Query("SELECT day, bookId, COUNT(*) AS pages FROM page_visits GROUP BY day, bookId ORDER BY day")
    fun observeDayBookCounts(): Flow<List<DayBookCount>>

    @Query("SELECT day, COUNT(*) AS pages FROM page_visits WHERE bookId = :bookId GROUP BY day ORDER BY day")
    fun observeDailyCountsForBook(bookId: Long): Flow<List<DayCount>>
}

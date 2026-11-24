package com.progresspal.app.reading.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Badge
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.data.BookStatus
import com.progresspal.app.reading.ui.ReadingStats
import com.progresspal.app.reading.ui.components.BookListItem
import com.progresspal.app.reading.ui.components.EmptyState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    stats: ReadingStats,
    contentPadding: PaddingValues,
    onOpenBook: (Long) -> Unit,
    onStatus: (Book, BookStatus) -> Unit,
    onEdit: (Book) -> Unit,
    onDelete: (Book) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(BookStatus.READING, BookStatus.COMPLETED, BookStatus.WISHLIST)
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PrimaryTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, s ->
                val count = stats.books.count { it.status == s }
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Row {
                            Text(s.label)
                            if (count > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge { Text("$count") }
                            }
                        }
                    }
                )
            }
        }
        val status = tabs[tab]
        val list = stats.books.filter { it.status == status }
        if (list.isEmpty() && stats.loaded) {
            val (icon, title, body) = when (status) {
                BookStatus.READING -> Triple(Icons.AutoMirrored.Rounded.MenuBook, "Nothing in progress", "Tap + to add a PDF and start reading.")
                BookStatus.COMPLETED -> Triple(Icons.Rounded.CheckCircle, "No finished books yet", "Books move here automatically when you reach the last page.")
                BookStatus.WISHLIST -> Triple(Icons.Rounded.Bookmark, "Your wishlist is empty", "Add PDFs you plan to read later to your wishlist.")
            }
            EmptyState(icon, title, body, Modifier.padding(top = 48.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, contentPadding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { b ->
                    BookListItem(
                        b,
                        onOpen = { onOpenBook(b.id) },
                        onStatus = { onStatus(b, it) },
                        onEdit = { onEdit(b) },
                        onDelete = { onDelete(b) },
                    )
                }
            }
        }
    }
}

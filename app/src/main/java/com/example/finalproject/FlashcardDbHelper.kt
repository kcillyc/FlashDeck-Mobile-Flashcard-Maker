package com.example.finalproject

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Deck(val id: Long, val name: String, val cardCount: Int)
data class Flashcard(val id: Long, val term: String, val definition: String, val status: Int)

class FlashcardDbHelper(context: Context) : SQLiteOpenHelper(context, "flashcards.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE decks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT, deck_id INTEGER NOT NULL, term TEXT NOT NULL, definition TEXT NOT NULL, status INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(deck_id) REFERENCES decks(id) ON DELETE CASCADE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE cards ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
    }

    fun getDecks(): List<Deck> {
        val result = mutableListOf<Deck>()
        readableDatabase.rawQuery("SELECT d.id, d.name, COUNT(c.id) FROM decks d LEFT JOIN cards c ON c.deck_id = d.id GROUP BY d.id ORDER BY d.name", null).use { cursor ->
            while (cursor.moveToNext()) result += Deck(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))
        }
        return result
    }

    fun getCards(deckId: Long): List<Flashcard> {
        val result = mutableListOf<Flashcard>()
        readableDatabase.rawQuery("SELECT id, term, definition, status FROM cards WHERE deck_id = ? ORDER BY id DESC", arrayOf(deckId.toString())).use { cursor ->
            while (cursor.moveToNext()) result += Flashcard(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3))
        }
        return result
    }

    fun addDeck(name: String): Long = writableDatabase.insert("decks", null, ContentValues().apply { put("name", name) })

    fun addCard(deckId: Long, term: String, definition: String): Long = writableDatabase.insert("cards", null, ContentValues().apply {
        put("deck_id", deckId)
        put("term", term)
        put("definition", definition)
    })

    fun addCards(deckId: Long, cards: List<Pair<String, String>>) {
        writableDatabase.beginTransaction()
        try {
            cards.forEach { (term, definition) ->
                writableDatabase.insert("cards", null, ContentValues().apply {
                    put("deck_id", deckId)
                    put("term", term)
                    put("definition", definition)
                })
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun updateCardStatus(cardId: Long, status: Int) {
        writableDatabase.update("cards", ContentValues().apply { put("status", status) }, "id = ?", arrayOf(cardId.toString()))
    }

    fun clearCardStatuses(deckId: Long) {
        writableDatabase.update("cards", ContentValues().apply { put("status", 0) }, "deck_id = ?", arrayOf(deckId.toString()))
    }

    fun deleteCards(cardIds: Set<Long>) {
        writableDatabase.beginTransaction()
        try {
            cardIds.forEach { cardId -> writableDatabase.delete("cards", "id = ?", arrayOf(cardId.toString())) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun seedAndroidVersionsDeck() {
        val deckId = addDeck("Android Versions")
        val sampleCards = listOf(
            "Android 1.5 (Cupcake)" to "2009 — Introduced the on-screen keyboard, home-screen widgets, and video recording.",
            "Android 1.6 (Donut)" to "2009 — Added support for different screen sizes and improved Android Market search.",
            "Android 2.0 (Eclair)" to "2009 — Added turn-by-turn Google Maps navigation, multiple account support, and HTML5 browser features.",
            "Android 2.2 (Froyo)" to "2010 — Introduced app installation on external storage and mobile hotspot support.",
            "Android 2.3 (Gingerbread)" to "2010 — Improved gaming performance, power management, and introduced NFC support.",
            "Android 3.0 (Honeycomb)" to "2011 — Redesigned Android for tablets with a system bar and action bar.",
            "Android 4.0 (Ice Cream Sandwich)" to "2011 — Unified the phone and tablet interfaces with a modern Holo design.",
            "Android 4.1 (Jelly Bean)" to "2012 — Made the interface smoother with Project Butter and introduced Google Now.",
            "Android 4.4 (KitKat)" to "2013 — Optimized Android to run well on devices with as little as 512 MB of RAM.",
            "Android 5.0 (Lollipop)" to "2014 — Introduced Material Design and the Android Runtime (ART).",
            "Android 6.0 (Marshmallow)" to "2015 — Added runtime permissions, Doze battery saving, and Google Now on Tap.",
            "Android 7.0 (Nougat)" to "2016 — Added split-screen multitasking, bundled notifications, and Vulkan graphics support.",
            "Android 8.0 (Oreo)" to "2017 — Introduced notification channels, picture-in-picture, and background execution limits.",
            "Android 9 (Pie)" to "2018 — Added gesture navigation, Adaptive Battery, and an adaptive brightness system.",
            "Android 10" to "2019 — Introduced system-wide dark theme and full gesture navigation.",
            "Android 11" to "2020 — Added conversation notifications, built-in screen recording, and one-time permissions.",
            "Android 12" to "2021 — Introduced Material You with dynamic color themes and redesigned widgets.",
            "Android 13" to "2022 — Added per-app language settings and notification permission controls.",
            "Android 14" to "2023 — Added stronger privacy controls, improved battery life, and Ultra HDR photo support."
        )
        sampleCards.forEach { (term, definition) -> addCard(deckId, term, definition) }
    }

    fun deleteDeck(deckId: Long) {
        writableDatabase.delete("cards", "deck_id = ?", arrayOf(deckId.toString()))
        writableDatabase.delete("decks", "id = ?", arrayOf(deckId.toString()))
    }
}

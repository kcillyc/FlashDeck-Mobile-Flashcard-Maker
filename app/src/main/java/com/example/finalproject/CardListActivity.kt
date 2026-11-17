package com.example.finalproject

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Gravity
import android.widget.*
import androidx.core.view.setPadding
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class CardListActivity : AppCompatActivity() {
    private lateinit var db: FlashcardDbHelper
    private val selectedCardIds = mutableSetOf<Long>()
    private lateinit var deleteSelectedButton: Button
    private lateinit var cardListContainer: LinearLayout
    private var tableDeckId = -1L
    private var tableCurrentCardId = -1L
    private var tableFilter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FlashcardDbHelper(this)
        setContentView(R.layout.activity_card_list)

        val deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        tableDeckId = deckId
        val deckName = intent.getStringExtra(EXTRA_DECK_NAME).orEmpty()
        val currentCardId = intent.getLongExtra(EXTRA_CURRENT_CARD_ID, -1L)
        tableCurrentCardId = currentCardId
        findViewById<TextView>(R.id.toolbarTitle).text = "$deckName cards"
        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.viewFlashcardButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.addCardButton).setOnClickListener { showAddCardDialog(deckId) }
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)
        deleteSelectedButton.setOnClickListener { confirmDeleteSelected() }

        cardListContainer = findViewById(R.id.cardListContainer)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 0, 14, 12)
        }
        header.addView(TextView(this).apply {
            text = "TERM"
            setTextColor(Color.rgb(91, 55, 148))
            textSize = 14f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "ANSWER"
            setTextColor(Color.rgb(91, 55, 148))
            textSize = 14f
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 12 })
        cardListContainer.addView(header)
        val tableFilterSpinner = findViewById<Spinner>(R.id.tableFilterSpinner)
        tableFilterSpinner.adapter = ArrayAdapter.createFromResource(this, R.array.filter_options, R.layout.spinner_filter_item).apply {
            setDropDownViewResource(R.layout.spinner_filter_dropdown)
        }
        tableFilterSpinner.setSelection(tableFilter)
        tableFilterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position != tableFilter) {
                    tableFilter = position
                    renderRows()
                }
            }
        }
        renderRows()
    }

    private fun renderRows() {
        if (cardListContainer.childCount > 1) cardListContainer.removeViews(1, cardListContainer.childCount - 1)
        val cards = when (tableFilter) {
            1 -> db.getCards(tableDeckId).filter { it.status == 1 }
            2 -> db.getCards(tableDeckId).filter { it.status == 2 }
            else -> db.getCards(tableDeckId)
        }
        cards.forEach { card ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_card_row, cardListContainer, false)
            row.findViewById<TextView>(R.id.termText).text = card.term
            row.findViewById<TextView>(R.id.answerText).text = card.definition
            row.findViewById<TextView>(R.id.statusDot).setTextColor(when (card.status) {
                1 -> Color.rgb(237, 177, 42)
                2 -> Color.rgb(46, 150, 78)
                else -> Color.rgb(189, 189, 189)
            })
            if (card.id == tableCurrentCardId) row.setBackgroundResource(R.drawable.flashcard_current)
            row.setOnLongClickListener {
                toggleSelection(row, card.id)
                true
            }
            row.setOnClickListener {
                if (selectedCardIds.isNotEmpty()) {
                    toggleSelection(row, card.id)
                } else {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_DECK_ID, tableDeckId)
                        putExtra(MainActivity.EXTRA_OPEN_CARD_ID, card.id)
                    })
                }
            }
            cardListContainer.addView(row)
        }

        if (cards.isEmpty()) {
            cardListContainer.addView(TextView(this).apply {
                text = if (tableFilter == 0) "No flashcards in this subject yet." else "No cards match this filter."
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                textSize = 16f
            }, LinearLayout.LayoutParams(-1, 120))
        }
    }

    private fun toggleSelection(row: View, cardId: Long) {
        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId)
            row.setBackgroundResource(R.drawable.flashcard_surface)
        } else {
            selectedCardIds.add(cardId)
            row.setBackgroundResource(R.drawable.flashcard_selected)
        }
        deleteSelectedButton.visibility = if (selectedCardIds.isEmpty()) View.GONE else View.VISIBLE
        deleteSelectedButton.text = "Delete selected (${selectedCardIds.size})"
    }

    private fun confirmDeleteSelected() {
        if (selectedCardIds.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete selected cards?")
            .setMessage("${selectedCardIds.size} flashcard(s) will be permanently removed.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                db.deleteCards(selectedCardIds)
                selectedCardIds.clear()
                recreate()
            }.show()
    }

    private fun showAddCardDialog(deckId: Long) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 0)
        }
        val term = EditText(this).apply { hint = "Term or question"; setSingleLine() }
        val answer = EditText(this).apply { hint = "Definition or answer"; minLines = 3; gravity = Gravity.TOP }
        layout.addView(term)
        layout.addView(answer)
        AlertDialog.Builder(this)
            .setTitle("Add flashcard")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val termText = term.text.toString().trim()
                val answerText = answer.text.toString().trim()
                if (termText.isNotEmpty() && answerText.isNotEmpty()) {
                    db.addCard(deckId, termText, answerText)
                    Toast.makeText(this, "flashcard added", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }.show()
    }

    companion object {
        const val EXTRA_DECK_ID = "deck_id"
        const val EXTRA_DECK_NAME = "deck_name"
        const val EXTRA_CURRENT_CARD_ID = "current_card_id"
    }
}

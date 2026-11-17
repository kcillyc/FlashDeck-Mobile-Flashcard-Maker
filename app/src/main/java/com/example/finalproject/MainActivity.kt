package com.example.finalproject

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Gravity
import android.view.animation.AlphaAnimation
import android.widget.*
import java.util.regex.Pattern
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {
    private lateinit var db: FlashcardDbHelper
    private var allDeckCards: List<Flashcard> = emptyList()
    private var studyCards: List<Flashcard> = emptyList()
    private var studyIndex = 0
    private var studyRevealed = false
    private var studyDeckId = -1L
    private var studyFilter = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var studyTimerRunning = false
    private var studyTimerSeconds = 0L
    private var studyTimerPomodoro = false
    private var studyTimerPhase = TimerPhase.STUDY
    private var pomodoroPromptShown = false
    private lateinit var studyTimerText: TextView
    private lateinit var content: LinearLayout
    private val purple = Color.rgb(91, 55, 148)
    private val gold = Color.rgb(237, 177, 42)
    private val ink = Color.rgb(45, 38, 57)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FlashcardDbHelper(this)
        val samplePrefs = getSharedPreferences("sample_data", MODE_PRIVATE)
        if (!samplePrefs.getBoolean("android_versions_added", false) && db.getDecks().none { it.name == "Android Versions" }) {
            db.seedAndroidVersionsDeck()
            samplePrefs.edit().putBoolean("android_versions_added", true).apply()
        }
        val openDeckId = intent.getLongExtra(EXTRA_OPEN_DECK_ID, -1L)
        val openDeck = db.getDecks().firstOrNull { it.id == openDeckId }
        if (openDeck != null) showDeck(openDeck) else showHome()
    }

    private fun base(title: String, back: (() -> Unit)? = null): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(250, 248, 252)) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(18, 10, 16, 10); setBackgroundColor(purple) }
        if (back != null) bar.addView(TextView(this).apply { text = "‹"; textSize = 38f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { back() } }, LinearLayout.LayoutParams(48, 56))
        bar.addView(TextView(this).apply { text = title; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, 56, 1f))
        root.addView(bar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20); isClickable = true }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showHome() {
        stopStudyTimer()
        pomodoroPromptShown = false
        setContentView(R.layout.activity_main)
        val container = findViewById<LinearLayout>(R.id.contentContainer)
        findViewById<Button>(R.id.addDeckButton).setOnClickListener { newDeckDialog() }
        findViewById<TextView>(R.id.importHtmlButton).setOnClickListener { openHtmlFilePicker() }
        db.getDecks().forEach { deck ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_deck, container, false)
            card.findViewById<TextView>(R.id.deckName).text = deck.name
            card.findViewById<TextView>(R.id.deckSummary).text = "${deck.cardCount} ${if (deck.cardCount == 1) "card" else "cards"}  •  Tap to open"
            card.setOnClickListener { showDeck(deck) }
            container.addView(card)
        }
        if (db.getDecks().isEmpty()) container.addView(TextView(this).apply { text = "No subjects yet. Start with your first group above!"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.GRAY) }, LinearLayout.LayoutParams(-1, 120))
    }

    private fun newDeckDialog() {
        val input = EditText(this).apply { hint = "e.g. Mobile Programming"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("New subject group").setMessage("Keep your cards organized by subject.").setView(input).setNegativeButton("CANCEL", null).setPositiveButton("CREATE") { _, _ ->
            if (input.text.toString().trim().isNotEmpty()) { db.addDeck(input.text.toString().trim()); showHome() }
        }.show()
    }

    private fun openHtmlFilePicker() {
        AlertDialog.Builder(this)
            .setTitle("Import from StudyStacks html")
            .setMessage("1. Go to StudyStack and pick the flashcard group you want to import.\n\n2. Find the Print option.\n\n3. Generate the printable table page.\n\n4. Download the webpage as an HTML file.\n\n5. Select that downloaded file here.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CHOOSE FILE") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/html"
                }, REQUEST_IMPORT_HTML)
            }.show()
    }

    @Deprecated("Handled for the local HTML importer")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_HTML && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val html = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val parsed = parseStudyStackHtml(html)
                if (parsed.cards.isEmpty()) {
                    Toast.makeText(this, "Unsupported file. Choose a StudyStack printable table HTML file.", Toast.LENGTH_LONG).show()
                } else {
                    showImportDestinationDialog(parsed.title, parsed.cards)
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Could not read that HTML file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseStudyStackHtml(html: String): ParsedStudyStack {
        val title = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { cleanHtml(it) }?.ifBlank { "Imported StudyStack" } ?: "Imported StudyStack"
        val table = Regex("""<table[^>]*id=["']theTable["'][^>]*>(.*?)</table>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1) ?: return ParsedStudyStack(title, emptyList())
        val rows = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(table)
        val firstRowCells = rows.firstOrNull()?.let { rowMatch ->
            Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(rowMatch.groupValues[1]).map { cleanHtml(it.groupValues[1]).lowercase() }.toList()
        }.orEmpty()
        if (firstRowCells.size < 2 || firstRowCells[0] != "question" || firstRowCells[1] != "answer") return ParsedStudyStack(title, emptyList())
        val cards = rows.mapNotNull { rowMatch ->
            val cells = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(rowMatch.groupValues[1]).map { cleanHtml(it.groupValues[1]) }.toList()
            if (cells.size >= 2 && cells[0].lowercase() != "question" && cells[0].isNotBlank() && cells[1].isNotBlank()) cells[0] to cells[1] else null
        }.toList()
        return ParsedStudyStack(title, cards)
    }

    private fun cleanHtml(value: String): String = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun showImportDestinationDialog(title: String, cards: List<Pair<String, String>>) {
        val decks = db.getDecks()
        val options = listOf("Create new subject: $title") + decks.map { "Add to: ${it.name}" }
        AlertDialog.Builder(this)
            .setTitle("Import ${cards.size} flashcards")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply { setText(title); selectAll(); hint = "Subject name" }
                    AlertDialog.Builder(this).setTitle("New subject group").setView(input).setNegativeButton("CANCEL", null).setPositiveButton("IMPORT") { _, _ ->
                        val name = input.text.toString().trim().ifBlank { title }
                        val deckId = db.addDeck(name)
                        db.addCards(deckId, cards)
                        Toast.makeText(this, "flashcards imported", Toast.LENGTH_SHORT).show()
                        showHome()
                    }.show()
                } else {
                    db.addCards(decks[which - 1].id, cards)
                    Toast.makeText(this, "flashcards imported", Toast.LENGTH_SHORT).show()
                    showHome()
                }
            }.show()
    }

    private data class ParsedStudyStack(val title: String, val cards: List<Pair<String, String>>)

    private fun showDeck(deck: Deck) {
        setContentView(R.layout.activity_study)
        findViewById<TextView>(R.id.toolbarTitle).text = deck.name
        findViewById<TextView>(R.id.backButton).setOnClickListener { showHome() }
        studyTimerText = findViewById(R.id.studyTimerText)
        studyTimerText.setOnClickListener { showStudyTimerPrompt() }
        findViewById<Button>(R.id.addCardButton).setOnClickListener { newCardDialog(deck.id) }
        findViewById<TextView>(R.id.viewAllButton).setOnClickListener {
            startActivity(Intent(this, CardListActivity::class.java).apply {
                putExtra(CardListActivity.EXTRA_DECK_ID, deck.id)
                putExtra(CardListActivity.EXTRA_DECK_NAME, deck.name)
                if (studyCards.isNotEmpty()) putExtra(CardListActivity.EXTRA_CURRENT_CARD_ID, studyCards[studyIndex].id)
            })
        }
        studyDeckId = deck.id
        studyFilter = 0
        refreshStudyCards()
        val openCardId = intent.getLongExtra(EXTRA_OPEN_CARD_ID, -1L)
        studyIndex = studyCards.indexOfFirst { it.id == openCardId }.takeIf { it >= 0 } ?: 0
        studyRevealed = false
        findViewById<TextView>(R.id.shuffleButton).setOnClickListener {
            if (studyCards.size > 1) {
                studyCards = studyCards.shuffled()
                studyIndex = 0
                studyRevealed = false
                renderStudyCard()
            }
        }
        findViewById<TextView>(R.id.restartMenuButton).setOnClickListener { showRestartMenu(it) }
        val filterSpinner = findViewById<Spinner>(R.id.filterSpinner)
        filterSpinner.adapter = ArrayAdapter.createFromResource(this, R.array.filter_options, R.layout.spinner_filter_item).apply {
            setDropDownViewResource(R.layout.spinner_filter_dropdown)
        }
        filterSpinner.setSelection(studyFilter)
        filterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position != studyFilter) {
                    studyFilter = position
                    studyIndex = 0
                    studyRevealed = false
                    refreshStudyCards()
                    renderStudyCard()
                }
            }
        }
        findViewById<Button>(R.id.previousButton).setOnClickListener {
            if (studyCards.isNotEmpty()) { studyIndex = (studyIndex - 1 + studyCards.size) % studyCards.size; studyRevealed = false; renderStudyCard() }
        }
        findViewById<Button>(R.id.nextButton).setOnClickListener {
            if (studyCards.isNotEmpty()) { studyIndex = (studyIndex + 1) % studyCards.size; studyRevealed = false; renderStudyCard() }
        }
        findViewById<TextView>(R.id.reviewButton).setOnClickListener { updateCurrentStatus(1) }
        findViewById<TextView>(R.id.memorizedButton).setOnClickListener { updateCurrentStatus(2) }
        findViewById<TextView>(R.id.studyCard).setOnClickListener {
            if (studyCards.isNotEmpty()) { studyRevealed = !studyRevealed; renderStudyCard() }
        }
        renderStudyCard()
        if (!pomodoroPromptShown) {
            pomodoroPromptShown = true
            showStudyTimerPrompt()
        }
    }

    private fun renderStudyCard() {
        val card = findViewById<TextView>(R.id.studyCard)
        val progress = findViewById<TextView>(R.id.cardProgress)
        val status = findViewById<TextView>(R.id.statusText)
        if (studyCards.isEmpty()) {
            card.text = "No flashcards yet\n\nAdd your first card above"
            card.setBackgroundResource(R.drawable.flashcard_surface)
            card.setTextColor(Color.rgb(45, 38, 57))
            progress.text = "0 cards"
            status.text = "No status"
            return
        }
        val current = studyCards[studyIndex]
        card.text = if (studyRevealed) current.definition else current.term
        card.setBackgroundResource(if (studyRevealed) R.drawable.flashcard_revealed else R.drawable.flashcard_surface)
        card.setTextColor(if (studyRevealed) Color.WHITE else Color.rgb(45, 38, 57))
        card.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 280 })
        progress.text = "Card ${studyIndex + 1} of ${studyCards.size}"
        progress.setTextColor(if (studyRevealed) Color.WHITE else Color.GRAY)
        when (current.status) {
            1 -> { status.text = "●  To review"; status.setTextColor(Color.rgb(237, 177, 42)) }
            2 -> { status.text = "●  Memorized"; status.setTextColor(Color.rgb(46, 150, 78)) }
            else -> { status.text = "Not marked yet"; status.setTextColor(Color.GRAY) }
        }
    }

    private fun updateCurrentStatus(newStatus: Int) {
        if (studyCards.isEmpty()) return
        db.updateCardStatus(studyCards[studyIndex].id, newStatus)
        refreshStudyCards()
        if (studyCards.isNotEmpty()) studyIndex = studyIndex.coerceAtMost(studyCards.lastIndex)
        renderStudyCard()
    }

    private fun showStudyTimerPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Would you like to use a pomodoro timer?")
            .setItems(arrayOf("Set study and rest times", "No thank you — time my study session")) { _, which ->
                if (which == 0) showPomodoroSettingsDialog() else startUnlimitedStudyTimer()
            }.show()
    }

    private fun showPomodoroSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 0, 24, 0)
        }
        val study = EditText(this).apply { hint = "Study minutes"; inputType = 2; setText("25") }
        val rest = EditText(this).apply { hint = "Rest minutes"; inputType = 2; setText("5") }
        layout.addView(study, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(rest, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 12 })
        AlertDialog.Builder(this)
            .setTitle("Pomodoro settings")
            .setMessage("Choose the length of each study and rest phase.")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("START") { _, _ ->
                val studyMinutes = study.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 25L
                val restMinutes = rest.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 5L
                startPomodoroTimer(studyMinutes, restMinutes)
            }.show()
    }

    private fun startUnlimitedStudyTimer() {
        stopStudyTimer()
        studyTimerPomodoro = false
        studyTimerPhase = TimerPhase.STUDY
        studyTimerSeconds = 0
        studyTimerRunning = true
        startTimerTicker()
    }

    private fun startPomodoroTimer(studyMinutes: Long, restMinutes: Long) {
        stopStudyTimer()
        studyTimerPomodoro = true
        studyTimerPhase = TimerPhase.STUDY
        studyTimerSeconds = studyMinutes * 60
        studyStudySeconds = studyMinutes * 60
        studyRestSeconds = restMinutes * 60
        studyTimerRunning = true
        startTimerTicker()
    }

    private var studyRestSeconds = 5 * 60L
    private val studyTimerTicker = object : Runnable {
        override fun run() {
            if (!studyTimerRunning) return
            if (!studyTimerPomodoro) {
                studyTimerSeconds++
            } else {
                studyTimerSeconds--
                if (studyTimerSeconds <= 0L) {
                    val enteringRest = studyTimerPhase == TimerPhase.STUDY
                    studyTimerPhase = if (enteringRest) TimerPhase.REST else TimerPhase.STUDY
                    studyTimerSeconds = if (enteringRest) studyRestSeconds else studyStudySeconds
                    sendStudyNotification(if (enteringRest) "Study time is over" else "Rest time is over", if (enteringRest) "Time to take a break." else "Time to get back to studying.")
                }
            }
            updateStudyTimerDisplay()
            timerHandler.postDelayed(this, 1000)
        }
    }
    private var studyStudySeconds = 25 * 60L

    private fun startTimerTicker() {
        createStudyNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
        }
        updateStudyTimerDisplay()
        timerHandler.post(studyTimerTicker)
    }

    private fun stopStudyTimer() {
        studyTimerRunning = false
        timerHandler.removeCallbacks(studyTimerTicker)
    }

    private fun updateStudyTimerDisplay() {
        if (!::studyTimerText.isInitialized) return
        studyTimerText.text = String.format("%02d:%02d", studyTimerSeconds / 60, studyTimerSeconds % 60)
        if (!studyTimerPomodoro) {
            studyTimerText.setBackgroundResource(R.drawable.button_status_white)
            studyTimerText.setTextColor(Color.rgb(91, 55, 148))
        } else if (studyTimerPhase == TimerPhase.REST) {
            studyTimerText.setBackgroundResource(R.drawable.timer_rest_background)
            studyTimerText.setTextColor(Color.rgb(30, 90, 115))
        } else {
            studyTimerText.setBackgroundResource(R.drawable.timer_study_background)
            studyTimerText.setTextColor(Color.rgb(135, 75, 20))
        }
    }

    private fun createStudyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("study_timer", "Study timer", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendStudyNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, "study_timer") else android.app.Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(message).setAutoCancel(true)
        getSystemService(NotificationManager::class.java).notify(2001, builder.build())
    }

    private fun showRestartMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("Restart Shuffle")
            menu.add("Restart flashcard markings")
            setOnMenuItemClickListener menuItem@{ item ->
                when (item.title.toString()) {
                    "Restart Shuffle" -> {
                        studyFilter = 0
                        findViewById<Spinner>(R.id.filterSpinner).setSelection(0)
                        refreshStudyCards()
                    }
                    "Restart flashcard markings" -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Restart flashcard markings?")
                            .setMessage("This will remove the To review and Memorized markings from all cards in this subject. Your terms and answers will not be deleted.")
                            .setNegativeButton("CANCEL", null)
                            .setPositiveButton("RESTART") { _, _ ->
                                db.clearCardStatuses(studyDeckId)
                                studyFilter = 0
                                findViewById<Spinner>(R.id.filterSpinner).setSelection(0)
                                refreshStudyCards()
                                studyIndex = 0
                                studyRevealed = false
                                renderStudyCard()
                            }
                            .show()
                        return@menuItem true
                    }
                }
                studyIndex = 0
                studyRevealed = false
                renderStudyCard()
                true
            }
        }.show()
    }

    private fun refreshStudyCards() {
        allDeckCards = db.getCards(studyDeckId)
        studyCards = when (studyFilter) {
            1 -> allDeckCards.filter { it.status == 1 }
            2 -> allDeckCards.filter { it.status == 2 }
            else -> allDeckCards
        }
    }

    private fun newCardDialog(deckId: Long) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 0, 24, 0) }
        val term = EditText(this).apply { hint = "Term or question"; setSingleLine() }
        val answer = EditText(this).apply { hint = "Definition or answer; you can write multiple lines"; minLines = 3; gravity = Gravity.TOP }
        layout.addView(term); layout.addView(answer)
        AlertDialog.Builder(this).setTitle("Add flashcard").setView(layout).setNegativeButton("CANCEL", null).setPositiveButton("SAVE") { _, _ ->
            val t = term.text.toString().trim(); val a = answer.text.toString().trim()
            if (t.isNotEmpty() && a.isNotEmpty()) {
                db.addCard(deckId, t, a)
                Toast.makeText(this, "flashcard added", Toast.LENGTH_SHORT).show()
                db.getDecks().firstOrNull { it.id == deckId }?.let { showDeck(it) }
            }
        }.show()
    }

    private fun confirmDelete(deck: Deck) {
        AlertDialog.Builder(this).setTitle("Delete ${deck.name}?").setMessage("All cards in this subject will be removed.").setNegativeButton("CANCEL", null).setPositiveButton("DELETE") { _, _ -> db.deleteDeck(deck.id); showHome() }.show()
    }

    override fun onDestroy() {
        stopStudyTimer()
        super.onDestroy()
    }

    private enum class TimerPhase { STUDY, REST }

    companion object {
        const val EXTRA_OPEN_DECK_ID = "open_deck_id"
        const val EXTRA_OPEN_CARD_ID = "open_card_id"
        const val REQUEST_IMPORT_HTML = 701
    }
}

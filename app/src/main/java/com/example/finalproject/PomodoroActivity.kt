package com.example.finalproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class PomodoroActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerText: TextView
    private lateinit var phaseText: TextView
    private lateinit var startPauseButton: TextView
    private lateinit var studyInput: EditText
    private lateinit var restInput: EditText
    private lateinit var unlimitedCheckBox: CheckBox
    private var running = false
    private var unlimited = false
    private var phase = Phase.STUDY
    private var remainingSeconds = 25 * 60L
    private var elapsedSeconds = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            if (unlimited) {
                elapsedSeconds++
            } else if (remainingSeconds > 0) {
                remainingSeconds--
                if (remainingSeconds == 0L) switchPhase()
            }
            updateDisplay()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pomodoro)
        timerText = findViewById(R.id.timerText)
        phaseText = findViewById(R.id.phaseText)
        startPauseButton = findViewById(R.id.startPauseButton)
        studyInput = findViewById(R.id.studyMinutesInput)
        restInput = findViewById(R.id.restMinutesInput)
        unlimitedCheckBox = findViewById(R.id.unlimitedCheckBox)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 900)
        }
        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }
        startPauseButton.setOnClickListener { toggleTimer() }
        findViewById<TextView>(R.id.resetButton).setOnClickListener { resetTimer() }
        unlimitedCheckBox.setOnCheckedChangeListener { _, checked ->
            unlimited = checked
            studyInput.isEnabled = !checked
            restInput.isEnabled = !checked
            resetTimer()
        }
        updateDisplay()
    }

    private fun toggleTimer() {
        if (!running) {
            if (!unlimited) {
                val studyMinutes = studyInput.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 25L
                val restMinutes = restInput.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 5L
                if (phase == Phase.STUDY && remainingSeconds <= 0) remainingSeconds = studyMinutes * 60
                if (phase == Phase.REST && remainingSeconds <= 0) remainingSeconds = restMinutes * 60
            }
            running = true
            startPauseButton.text = "Pause"
            handler.post(ticker)
        } else {
            running = false
            startPauseButton.text = "Start"
            handler.removeCallbacks(ticker)
        }
    }

    private fun resetTimer() {
        running = false
        handler.removeCallbacks(ticker)
        phase = Phase.STUDY
        elapsedSeconds = 0
        val minutes = studyInput.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 25L
        remainingSeconds = minutes * 60
        startPauseButton.text = "Start"
        updateDisplay()
    }

    private fun switchPhase() {
        val nextIsRest = phase == Phase.STUDY
        phase = if (nextIsRest) Phase.REST else Phase.STUDY
        val minutes = if (nextIsRest) restInput.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 5L else studyInput.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: 25L
        remainingSeconds = minutes * 60
        sendNotification(if (nextIsRest) "Study session complete" else "Rest time is over", if (nextIsRest) "Time to take a break." else "Time to get back to studying.")
    }

    private fun updateDisplay() {
        phaseText.text = if (unlimited) "Unlimited study time" else if (phase == Phase.STUDY) "Study time" else "Rest time"
        if (unlimited) {
            timerText.text = formatTime(elapsedSeconds)
        } else {
            timerText.text = formatTime(remainingSeconds)
        }
    }

    private fun formatTime(seconds: Long): String = String.format("%02d:%02d", seconds / 60, seconds % 60)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Pomodoro timer", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, CHANNEL_ID) else android.app.Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(message).setAutoCancel(true)
        getSystemService(NotificationManager::class.java).notify(1001, builder.build())
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private enum class Phase { STUDY, REST }

    companion object { private const val CHANNEL_ID = "pomodoro_channel" }
}

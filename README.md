# FlashDeck Flashcard App

FlashDeck is a simple Android flashcard app for organizing subjects, studying cards, importing StudyStack HTML files, and tracking review progress.

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Requirements](#requirements)
- [Setup](#setup)
- [Build](#build)
- [Database](#database)
- [StudyStack HTML import](#studystack-html-import)
- [Project structure](#project-structure)
- [Notifications](#notifications)

## Tech stack

- Kotlin
- Android SDK
- AndroidX AppCompat and Core KTX
- Material Components
- SQLite through `SQLiteOpenHelper`
- XML layouts with programmatic screen behavior

## Features

- Create multiple subject groups
- Add flashcards with a term and answer
- Store all data locally with SQLite
- Tap a flashcard to reveal or hide its answer
- Move between cards with previous and next arrows
- Shuffle cards
- Filter cards by:
  - All cards
  - To review
  - Memorized
- Mark cards as **To review** or **Memorized**
- View all cards in a two-column table
- Long-press table rows to select and delete multiple cards
- Highlight the currently selected card in the table view
- Import flashcards from a downloaded StudyStack printable HTML file
- Add imported cards to an existing subject or create a new subject
- One-time sample deck for Android versions
- Embedded Pomodoro timer with:
  - Custom study and rest durations
  - Default 25-minute study and 5-minute rest periods
  - Unlimited study timer mode
  - Study/rest notifications
- Purple, gold, white, and soft status-color theme

## Requirements

- Android Studio
- Android SDK with API 37 available
- Java 11 or newer
- Android device or emulator with Android API 24 or newer

## Setup

Clone the repository and open the project root in Android Studio:

```bash
git clone <repository-url>
cd FinalProject
```

Allow Android Studio to sync the Gradle project. Then run the `app` configuration on an emulator or connected Android device.

The app uses the application ID:

```text
com.example.finalproject
```

## Build

From the project directory:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Database

The app uses a local SQLite database managed by `FlashcardDbHelper`:

```text
app/src/main/java/com/example/finalproject/FlashcardDbHelper.kt
```

The database is named `flashcards.db` and contains:

- `decks` — subject groups
- `cards` — terms, answers, and card status values

Card status values:

```text
0 = unmarked
1 = to review
2 = memorized
```

The database automatically upgrades when the schema version changes.

## StudyStack HTML import

The importer supports StudyStack printable table HTML files with a table containing:

- A `Question` column
- An `Answer` column
- The standard StudyStack table identifier `theTable`

Import steps:

1. Open a StudyStack group.
2. Choose the Print option.
3. Generate the printable table page.
4. Download the webpage as an HTML file.
5. Open the app and tap **Import from StudyStacks html**.
6. Select the downloaded HTML file.
7. Choose an existing subject or create a new one.

Unsupported files are rejected without changing the database.

## Project structure

```text
app/
└── src/main/
    ├── java/com/example/finalproject/
    │   ├── MainActivity.kt
    │   ├── CardListActivity.kt
    │   ├── PomodoroActivity.kt
    │   └── FlashcardDbHelper.kt
    └── res/
        ├── drawable/
        ├── layout/
        └── values/
```

The main screens are defined in:

```text
app/src/main/res/layout/
```

## Source highlights

- `MainActivity.kt` — subject groups, flashcard study view, HTML import, filtering, shuffle, and embedded timer
- `CardListActivity.kt` — table view, filters, status dots, row selection, and bulk deletion
- `FlashcardDbHelper.kt` — SQLite schema, queries, imports, statuses, and migrations
- `PomodoroActivity.kt` — standalone Pomodoro implementation retained for future reuse

## Notifications

The app requests notification permission on Android 13 and newer. Notifications are used by the Pomodoro timer when switching between study and rest phases.

## Theme

The app uses:

- Purple for navigation and primary actions
- Golden yellow for important actions
- White for secondary controls
- Light orange for study timer mode
- Baby blue for rest timer mode
- Yellow and green status indicators for card markings

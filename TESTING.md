# 🧪 Guida Completa al Testing - PokéVault

Questa guida ti spiega come usare il sistema di testing automatizzato per PokéVault, sia localmente che con GitHub Actions.

---

## 📋 Indice

1. [Struttura dei Test](#struttura-dei-test)
2. [Esecuzione Locale](#esecuzione-locale)
3. [GitHub Actions Automatico](#github-actions-automatico)
4. [Scrivere Nuovi Test](#scrivere-nuovi-test)
5. [Interpretare i Risultati](#interpretare-i-risultati)
6. [Best Practices](#best-practices)

---

## 🏗️ Struttura dei Test

I test sono organizzati in due categorie principali:

### Test Unitari (`src/test/`)
- **Posizione**: `app/src/test/java/com/emabuia/pokevault/`
- **Cosa testano**: Logica isolata senza dipendenze Android
- **Esempi**:
  - `util/CardPriceUtilsTest.kt` - Logica di calcolo prezzi
  - `viewmodel/DeckLabViewModelTest.kt` - State management
  - `data/local/CardDaoTest.kt` - Database queries

### Test Instrumentati/UI (`src/androidTest/`)
- **Posizione**: `app/src/androidTest/java/com/emabuia/pokevault/`
- **Cosa testano**: Comportamento Android-specific e UI
- **Esempi**:
  - `ComposeUITest.kt` - Rendering Compose
  - `data/local/SetDaoInstrumentedTest.kt` - Database con Android Framework
  - Test Espresso per UI navigation

---

## 💻 Esecuzione Locale

### Prerequisiti
- Android Studio 2024+ o Android SDK Command Line Tools
- Java 11+
- Gradle 8.0+
- (Opzionale) Emulatore Android o dispositivo fisico per test instrumentati

### 1️⃣ Eseguire TUTTI i Test

```bash
# Test unitari + instrumentati
./gradlew test connectedAndroidTest

# O con più dettagli
./gradlew test connectedAndroidTest --stacktrace --info
```

### 2️⃣ Eseguire Solo Test Unitari (VELOCE)

```bash
# Tutti i test unitari
./gradlew testDebugUnitTest

# Solo un file di test specifico
./gradlew testDebugUnitTest --tests "*CardPriceUtilsTest"

# Solo una classe specifica
./gradlew testDebugUnitTest --tests "com.emabuia.pokevault.util.CardPriceUtilsTest"

# Solo un metodo di test specifico
./gradlew testDebugUnitTest --tests "*CardPriceUtilsTest.testMinimumEurPriceWithLowPrice"
```

### 3️⃣ Eseguire Test Instrumentati (Richiede Emulatore)

```bash
# Prerequisito: Avvia un emulatore Android o connetti un dispositivo

# Tutti i test instrumentati
./gradlew connectedAndroidTest

# Solo un file di test
./gradlew connectedAndroidTest --tests "*SetDaoInstrumentedTest"

# Con report dettagliato
./gradlew connectedAndroidTest --stacktrace --info
```

### 4️⃣ Eseguire Test Specifici da Android Studio

1. **Apri il file di test** (es. `CardPriceUtilsTest.kt`)
2. **Right-click sul nome della classe** → `Run 'CardPriceUtilsTest'`
3. **Oppure** right-click su un singolo metodo di test:
   - `Run 'testMinimumEurPriceWithLowPrice'`

### 5️⃣ Generare Report di Coverage

```bash
# Coverage completo
./gradlew testDebugUnitTest jacocoTestDebugUnitTestReport

# Il report sarà in: app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html
# Aprilo nel browser per visualizzare quale codice è coperto dai test
```

### 6️⃣ Eseguire Lint (Analisi Statica)

```bash
# Controlla errori di codice, stile, ecc.
./gradlew lint

# Report: app/build/reports/lint-results-debug.html
```

---

## 🔄 GitHub Actions Automatico

### Come Funziona

GitHub Actions esegue automaticamente i test ogni volta che:
- ✅ Fai un **push** su `main` o `develop`
- ✅ Crei una **Pull Request** verso `main` o `develop`
- ✅ Secondo una **pianificazione** (default: ogni giorno alle 2:00 UTC)

### Visualizzare i Risultati

#### 1. Nel Repository GitHub

1. Vai su **Actions** nel tuo repository
2. Seleziona il workflow che ti interessa:
   - `Android CI/CD Tests` - Test di base
   - `Advanced Android Testing with Coverage` - Test completi con coverage

#### 2. In una Pull Request

Quando apri una PR, vedrai:
- ✅ Stato dei test accanto al nome della PR
- 📊 Commento automatico con il sommario dei test
- 🔗 Link ai report dettagliati (artifacts)

### Scarica i Report

1. Vai su **Actions**
2. Seleziona il run che ti interessa
3. Scorri fino a **Artifacts** nella sezione inferiore
4. Scarica i report:
   - `unit-test-reports` - Risultati test unitari
   - `instrumented-test-reports` - Risultati test UI/integrazione
   - `lint-report` - Report di analisi statica
   - `debug-apk` - APK compilata per il testing manuale

---

## ✍️ Scrivere Nuovi Test

### Anatomia di un Test

```kotlin
class MyFeatureTest {
    @Before
    fun setup() {
        // Preparazione: inizializza i mock, database, ecc.
    }

    @Test
    fun testFeatureBehavior() {
        // Arrange: prepara i dati
        val input = "test data"
        
        // Act: esegui l'azione da testare
        val result = myFunction(input)
        
        // Assert: verifica il risultato
        assertEquals("expected output", result)
    }

    @After
    fun tearDown() {
        // Pulizia: chiudi i database, reset i mock, ecc.
    }
}
```

### Template: Test Unitario Semplice

```kotlin
package com.emabuia.pokevault.util

import org.junit.Test
import org.junit.Assert.*

class MyUtilityTest {
    
    @Test
    fun testHappyPath() {
        // Arrange
        val input = "test"
        
        // Act
        val result = myFunction(input)
        
        // Assert
        assertEquals("expected", result)
    }

    @Test
    fun testEdgeCase() {
        // Test casi limite (null, empty, ecc.)
        val result = myFunction(null)
        assertNull(result)
    }

    @Test
    fun testErrorHandling() {
        // Test comportamento in caso di errore
        assertThrows(IllegalArgumentException::class.java) {
            myFunction("invalid")
        }
    }
}
```

### Template: Test ViewModel con Mock

```kotlin
package com.emabuia.pokevault.viewmodel

import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MyViewModelTest {
    
    private lateinit var viewModel: MyViewModel
    private val mockRepository: MyRepository = mockk()

    @Before
    fun setup() {
        viewModel = MyViewModel(mockRepository)
    }

    @Test
    fun testLoadData() = runTest {
        // Act
        viewModel.loadData()

        // Assert
        coVerify { mockRepository.fetchData() }
        assertTrue(viewModel.dataLoaded)
    }
}
```

### Template: Test UI Compose

```kotlin
package com.emabuia.pokevault.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MyScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testScreenDisplay() {
        composeTestRule.setContent {
            MyScreen()
        }

        composeTestRule.onNodeWithText("Expected Title").assertExists()
        composeTestRule.onNodeWithText("Click Me").performClick()
    }
}
```

### Template: Test Database

```kotlin
package com.emabuia.pokevault.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyDaoTest {
    
    private lateinit var database: PokeVaultDatabase
    private lateinit var dao: MyDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PokeVaultDatabase::class.java).build()
        dao = database.myDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieve() = runBlocking {
        // Arrange
        val item = MyEntity("id", "name")
        
        // Act
        dao.insert(item)
        val retrieved = dao.getById("id")
        
        // Assert
        assertNotNull(retrieved)
        assertEquals("name", retrieved?.name)
    }
}
```

---

## 📊 Interpretare i Risultati

### Report Unitari

```
Dopo ./gradlew testDebugUnitTest, vedrai:
- Total Tests: 12
- Passed: 11 ✅
- Failed: 1 ❌
- Skipped: 0

File report: app/build/reports/tests/testDebugUnitTest/index.html
```

Clicka su test falliti per vedere l'errore specifico.

### Report Coverage

```
Coverage report: app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html

Metriche importanti:
- Line Coverage: % di linee di codice testate
- Branch Coverage: % di branch (if/else) testati
- Method Coverage: % di metodi testati

⚠️ Non esiste alcun gate di coverage nel progetto: il plugin jacoco e'
applicato in app/build.gradle.kts ma nessun task JacocoReport e' registrato,
quindi `jacocoTestDebugUnitTestReport` (invocato da android-advanced-tests.yml)
non esiste. Il 70% qui sotto e' un obiettivo, non un controllo automatico.

Obiettivo indicativo: 70% line coverage
```

### Report Instrumentati

```
Report: app/build/reports/androidTests/release/index.html

Mostra:
- Test durata
- Screenshot/video di test UI falliti
- Stack trace degli errori
```

---

## ✅ Best Practices

### 1. Nome Descrittivo dei Test

```kotlin
// ❌ Male
fun test1() { }
fun testData() { }

// ✅ Bene
fun testCalculateTotalPriceWithValidCards() { }
fun testLoadDeckHandlesNetworkError() { }
fun testDatabaseDeleteExpiredItemsSuccessfully() { }
```

### 2. Una Asserzione per Concetto

```kotlin
// ❌ Troppi assert
fun testLoadDeck() {
    val deck = loadDeck()
    assertEquals(50, deck.cards.size)
    assertTrue(deck.isValid)
    assertNotNull(deck.name)
    assertEquals("My Deck", deck.name)
}

// ✅ Test separati e chiari
fun testLoadDeckReturnsCorrectCardCount() {
    val deck = loadDeck()
    assertEquals(50, deck.cards.size)
}

fun testLoadDeckIsValid() {
    val deck = loadDeck()
    assertTrue(deck.isValid)
}
```

### 3. Mockare Dipendenze Esterne

```kotlin
// ✅ Buono - mockare la rete
val mockRepository: CardRepository = mockk()
every { mockRepository.fetchCard("id") } returns testCard
val viewModel = MyViewModel(mockRepository)

// ❌ Male - dipendere dalla rete reale
val viewModel = MyViewModel(RealRepository()) // NON FARE!
```

### 4. Test Setup e Teardown

```kotlin
@Before
fun setup() {
    // ✅ Prep per OGNI test
    database = createTestDatabase()
    viewModel = MyViewModel(database)
}

@After
fun tearDown() {
    // ✅ Pulizia dopo OGNI test
    database.close()
}
```

### 5. Coprire Casi Limite

```kotlin
@Test
fun testWithNullInput() {
    val result = processData(null)
    assertEquals(defaultValue, result)
}

@Test
fun testWithEmptyList() {
    val result = processData(emptyList())
    assertEquals(0, result.size)
}

@Test
fun testWithLargeDataSet() {
    val largeData = (1..10000).map { createItem(it) }
    val result = processData(largeData)
    assertNotNull(result)
}
```

---

## 🚀 Workflow Consigliato

### Durante lo Sviluppo

```bash
# 1. Scrivi il test prima (TDD)
# 2. Esegui solo il test appena scritto (veloce)
./gradlew testDebugUnitTest --tests "*MyNewTest"

# 3. Implementa la feature
# 4. Riesegui il test
./gradlew testDebugUnitTest --tests "*MyNewTest"

# 5. Quando hai finito, esegui tutti i test
./gradlew testDebugUnitTest
```

### Prima di Fare Commit

```bash
# Esegui tutti i test unitari
./gradlew testDebugUnitTest

# Se ok, esegui anche lint
./gradlew lint

# Se tutto passa, puoi committare
git add .
git commit -m "Add new feature with tests"
```

### Prima di Fare Push

```bash
# Esegui test completi (inclusi UI/integrazione)
./gradlew test connectedAndroidTest

# Se usi emulatore
# Assicurati che sia avviato prima!
adb devices
./gradlew connectedAndroidTest
```

---

## 🐛 Troubleshooting

### Test fallisce ma localmente passa

```bash
# Pulisci la cache di gradle
./gradlew clean

# Riscarica le dipendenze
./gradlew build --refresh-dependencies

# Riesegui i test
./gradlew test
```

### Emulatore non risponde

```bash
# Lista emulatori disponibili
emulator -list-avds

# Avvia un emulatore specifico
emulator -avd Pixel_6_Pro_API_34

# Attendi che sia fully loaded
adb shell getprop sys.boot_from_charger_mode
```

### Test timeout su GitHub Actions

Nel file workflow, aumenta il timeout:
```yaml
timeout-minutes: 60  # Aumentato da 30 a 60
```

---

## 📞 Supporto

Se riscontri problemi:

1. **Leggi il log completo**: Copia lo stacktrace in un editor
2. **Cerca in Google**: "android junit [error message]"
3. **Controlla le dipendenze**: `./gradlew dependencies`
4. **Ricrea l'ambiente**: `./gradlew clean && ./gradlew build`

---

## 🎯 Metriche di Successo

| Metrica | Target | Attuale |
|---------|--------|---------|
| Test Coverage | 70%+ | - |
| Test Execution Time (CI) | < 10 min | - |
| Test Pass Rate | 100% | - |
| Critical Tests | 100% | - |

---

## 📚 Risorse Utili

- [JUnit 4 Documentation](https://junit.org/junit4/)
- [Mockk Documentation](https://mockk.io/)
- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [Room Testing](https://developer.android.com/training/data-storage/room/testing-db)
- [Android Testing Codelab](https://developer.android.com/codelabs/android-testing)

---

**Ultimo aggiornamento**: Maggio 2024 | Versione: 1.0

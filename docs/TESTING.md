# Testing

Guida al testing di PokéVault: come eseguire i test in locale, come funziona la
CI su GitHub Actions, template per scriverne di nuovi e problemi comuni.

> Consolidato nel settembre 2026 da 8 documenti di testing separati e in gran
> parte duplicati (`TESTING_GUIDE.md`, `README_TESTING.md`, `TESTING_COMPLETE.md`,
> `TESTING_SUMMARY.md`, `QUICK_REFERENCE.md`, `TESTING_START_HERE.md`,
> `QUICK_START_TESTING.md`, `GITHUB_SETUP.md`) — vedi `MIGRATION_PLAN.md`
> sezione 8, voce 21.

## Struttura dei test

| Cartella | Cosa testa | Esempi reali nel repo |
|---|---|---|
| `app/src/test/` (unit, JVM) | Logica isolata, senza dipendenze Android | `util/CardPriceUtilsTest.kt`, `viewmodel/DeckLabViewModelTest.kt`, `data/local/CardDaoTest.kt`, `data/remote/SetCodeMapperTest.kt`, `ocr/CardFieldParserTest.kt` |
| `app/src/androidTest/` (instrumentati) | Comportamento Android-specifico e UI, richiede emulatore/device | `ComposeUITest.kt`, `data/local/SetDaoInstrumentedTest.kt` |

I file `ExampleUnitTest.kt`/`ExampleInstrumentedTest.kt` sono i template di default
di Android Studio, mai sostituiti — vedi `MIGRATION_PLAN.md` sez. 8 voce 23.

## Eseguire i test in locale

Prerequisiti: JDK 21 (non il JBR 25 di Android Studio, vedi nota in
`MIGRATION_PLAN.md`), Android SDK, opzionale un emulatore per i test instrumentati.

```bash
# Solo unit test (veloce, nessun emulatore richiesto)
./gradlew testDebugUnitTest

# Un file/classe/metodo specifico
./gradlew testDebugUnitTest --tests "*CardPriceUtilsTest"
./gradlew testDebugUnitTest --tests "com.emabuia.pokevault.util.CardPriceUtilsTest.testMinimumEurPriceWithLowPrice"

# Test instrumentati (richiede emulatore avviato o device connesso)
./gradlew connectedAndroidTest

# Tutto insieme
./gradlew test connectedAndroidTest

# Coverage (Jacoco)
./gradlew testDebugUnitTest jacocoTestDebugUnitTestReport
# Report: app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html

# Analisi statica
./gradlew lint
# Report: app/build/reports/lint-results-debug.html
```

Report dei test unitari: `app/build/reports/tests/testDebugUnitTest/index.html`.
Report dei test instrumentati: `app/build/reports/androidTests/release/index.html`.

Da Android Studio: click destro sulla classe o sul singolo metodo di test → *Run*.

## CI su GitHub Actions

Due workflow, entrambi in `.github/workflows/`:

| Workflow | Job | Trigger attuale |
|---|---|---|
| `android-tests.yml` | `test` (unit test) | push su `main`, `develop`, `release/**`, `feature/**`, `fix/**`; PR verso `main`/`develop` |
| `android-advanced-tests.yml` | `unit-tests`, `instrumented-tests`, `lint-analysis`, `build` | push/PR su `main`/`develop`; schedule giornaliero |

**Debito noto** (vedi `MIGRATION_PLAN.md` sez. 1.2, voci 2-3): nessuno dei due
workflow gira sui branch di lavoro reali del progetto (`release/R3.0.0`,
`claude/*`) a meno che il nome combaci con `release/**`; `android-advanced-tests.yml`
non ha `release/**` tra i trigger. `android-advanced-tests.yml` usa inoltre
JDK 11, incompatibile con AGP 8.13.2 (il task `jacocoTestDebugUnitTestReport`
non è mai stato registrato correttamente). Prima di fidarsi del verde CI su un
branch che non sia `main`/`develop`, verificare che il workflow sia effettivamente
partito nel tab *Actions*.

Ogni run produce artifact scaricabili dal tab *Actions* → run → *Artifacts*:
`unit-test-reports`, `instrumented-test-reports`, `lint-report`, `debug-apk`.

### Branch protection (setup una tantum, lato repository GitHub)

1. **Settings → Branches → Add rule**, pattern del branch da proteggere (es. `main`)
2. Abilitare *Require a pull request before merging* e *Require status checks to pass before merging*
3. Selezionare come check obbligatori i job che compaiono dopo il primo push: `test` / `unit-tests`, `instrumented-tests`, `lint-analysis`, `build`
4. Salvare

Con questo, il merge è bloccato finché i check non sono verdi. Le notifiche
email sui fallimenti si attivano da **GitHub → Settings → Notifications**
(personali) o da **Settings → Notifications** del repository per le preferenze
specifiche.

## Scrivere nuovi test

Schema base (Arrange/Act/Assert):

```kotlin
class MyFeatureTest {
    @Before fun setup() { /* inizializza mock, database, ecc. */ }

    @Test
    fun testCalculateTotalPriceWithValidCards() {
        val input = "test data"
        val result = myFunction(input)
        assertEquals("expected output", result)
    }

    @After fun tearDown() { /* chiudi database, reset mock */ }
}
```

### Template: ViewModel con MockK

```kotlin
class MyViewModelTest {
    private lateinit var viewModel: MyViewModel
    private val mockRepository: MyRepository = mockk()

    @Before
    fun setup() {
        viewModel = MyViewModel(mockRepository)
    }

    @Test
    fun testLoadData() = runTest {
        viewModel.loadData()
        coVerify { mockRepository.fetchData() }
        assertTrue(viewModel.dataLoaded)
    }
}
```

### Template: UI Compose

```kotlin
class MyScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testScreenDisplay() {
        composeTestRule.setContent { MyScreen() }
        composeTestRule.onNodeWithText("Expected Title").assertExists()
        composeTestRule.onNodeWithText("Click Me").performClick()
    }
}
```

### Template: Room DAO (instrumentato)

```kotlin
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

    @After fun tearDown() { database.close() }

    @Test
    fun testInsertAndRetrieve() = runBlocking {
        dao.insert(MyEntity("id", "name"))
        val retrieved = dao.getById("id")
        assertEquals("name", retrieved?.name)
    }
}
```

## Best practice

- **Nomi descrittivi**: `testLoadDeckHandlesNetworkError`, non `test1`.
- **Un'asserzione per concetto**: test separati per ogni comportamento verificato, non un unico test con 5 `assert`.
- **Mockare le dipendenze esterne** (rete, repository reali) — mai far dipendere un unit test dalla rete vera.
- **Coprire i casi limite**: input null, liste vuote, dataset grandi.
- **Setup/teardown in `@Before`/`@After`**, mai stato condiviso implicito tra test.

## Troubleshooting

| Sintomo | Soluzione |
|---|---|
| Test fallisce in CI ma passa in locale | `./gradlew clean && ./gradlew build --refresh-dependencies && ./gradlew test` |
| Emulatore non risponde | `emulator -list-avds` → `emulator -avd <nome>`, attendere il boot completo prima di lanciare `connectedAndroidTest` |
| Timeout su GitHub Actions | Aumentare `timeout-minutes` nel workflow YAML interessato |
| `local.properties` mancante in CI | I workflow lo generano al volo (`echo "..." > local.properties`) — se manca una chiave, verificare lo step *Create local.properties* nel workflow |

## Target

Coverage line dichiarato in questo documento come obiettivo: **50%** (vedi
`MIGRATION_PLAN.md` sez. 8 voce 25 — mai raggiunto, non ancora misurato in modo
sistematico). Non esiste oggi un numero di coverage reale da citare: va generato
con `jacocoTestDebugUnitTestReport` prima di poter dire dove si è.

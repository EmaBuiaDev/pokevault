# ⚡ Quick Start - Testing PokéVault

## 🎯 In 5 Minuti: Eseguire i Tuoi Primi Test

### Step 1: Apri Terminal
```bash
cd /path/to/pokevault
```

### Step 2: Esegui Tutti i Test Unitari
```bash
./gradlew testDebugUnitTest
```

✅ Vedrai qualcosa come:
```
MyTest.testExample PASSED
CardPriceUtilsTest.testMinimumEurPrice PASSED
...
BUILD SUCCESSFUL ✨
```

### Step 3: Visualizza i Report
- **HTML Report**: `open app/build/reports/tests/testDebugUnitTest/index.html`
- O naviga in Android Studio: **Build > Open Module Settings > Reports**

---

## 🔄 GitHub Actions Automatico

Quando fai **push**, GitHub esegue automaticamente:
1. ✅ Test unitari (2-3 minuti)
2. ✅ Test UI/integrazione (5-10 minuti)
3. ✅ Analisi di codice (1 minuto)
4. ✅ Build debug APK (3-5 minuti)

**Vedi i risultati**: Repository → **Actions** tab

---

## 📝 Aggiungi un Nuovo Test

### 1. Crea il File
`app/src/test/java/com/emabuia/pokevault/util/MyNewTest.kt`

### 2. Scrivi il Test
```kotlin
package com.emabuia.pokevault.util

import org.junit.Test
import org.junit.Assert.*

class MyNewTest {
    @Test
    fun testMyFeature() {
        // Test qui
        assertEquals(1 + 1, 2)
    }
}
```

### 3. Esegui
```bash
./gradlew testDebugUnitTest --tests "*MyNewTest"
```

---

## 🚀 Comandi Essenziali

| Comando | Cosa Fa | Tempo |
|---------|---------|-------|
| `./gradlew testDebugUnitTest` | Test unitari | 2-3 min |
| `./gradlew testDebugUnitTest --tests "*MyTest"` | Un test specifico | <1 min |
| `./gradlew connectedAndroidTest` | Test UI (richiede emulator) | 10-15 min |
| `./gradlew lint` | Analisi statica | 1-2 min |
| `./gradlew clean && ./gradlew testDebugUnitTest` | Reset e test | 3-5 min |

---

## ❌ Se Qualcosa Non Funziona

### Test fallisce localmente
```bash
./gradlew clean
./gradlew testDebugUnitTest --stacktrace --info
```

### Gradle si blocca
```bash
./gradlew --stop
./gradlew testDebugUnitTest
```

### Dipendenze non trovate
```bash
./gradlew build --refresh-dependencies
```

---

## 📊 Vedere il Coverage

```bash
./gradlew testDebugUnitTest jacocoTestDebugUnitTestReport
open app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html
```

**Cosa significano i colori**:
- 🟢 Verde = Testato
- 🔴 Rosso = Non testato

---

## 💡 Pro Tips

### Esegui test velocemente durante lo sviluppo
```bash
# Solo test unitari (NON instrumentati)
./gradlew testDebugUnitTest -x connectedAndroidTest
```

### Guarda i dettagli di un test fallito
```bash
./gradlew testDebugUnitTest --stacktrace
```

### Esegui test da Android Studio
1. Apri il file di test
2. Right-click sulla classe/metodo
3. **Run 'TestName'**
4. Vedi i risultati nel pannello "Run"

### Monitora GitHub Actions in tempo reale
GitHub Actions mostra lo stato in tempo reale:
- Repository → **Actions**
- Clicca sul workflow in esecuzione
- Vedi i log minuto per minuto

---

## 📚 Leggi la Guida Completa

Per una documentazione più dettagliata, vedi: **[TESTING_GUIDE.md](./TESTING_GUIDE.md)**

---

## 🎓 Prossimi Step

1. ✅ Aggiungi test per le tue nuove feature
2. ✅ Aumenta il coverage a 70%+
3. ✅ Fai in modo che GitHub Actions sia verde (all tests pass)
4. ✅ Rivedi il coverage report mensilmente

---

**Fatto!** Ora hai un sistema di testing completamente automatizzato. 🎉

# 🚀 GUIDA RAPIDA - Comandi & Link

## ⚡ Esecuzione Veloce

```bash
# In 30 secondi: Esegui i test
./gradlew testDebugUnitTest

# Visualizza il report
open app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 📋 Comandi Essenziali

### Test Unitari (Veloce)
```bash
./gradlew testDebugUnitTest                    # Tutti
./gradlew testDebugUnitTest --tests "*Price"  # Specifico
./gradlew testDebugUnitTest -t                # Watch mode
```

### Test UI (Richiede Emulator)
```bash
./gradlew connectedAndroidTest                # Tutti
./gradlew connectedAndroidTest --tests "*UI"  # Specifico
```

### Code Coverage
```bash
./gradlew testDebugUnitTest jacocoTestReport
open app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html
```

### Build & Lint
```bash
./gradlew lint                # Analisi statica
./gradlew assembleDebug       # Compila APK
./gradlew clean testDebugUnitTest  # Reset
```

---

## 📁 File Importanti

| File | Descrizione | Tempo Lettura |
|------|-------------|--------------|
| QUICK_START_TESTING.md | Start in 5 min | 5 min |
| TESTING_GUIDE.md | Guida completa | 20 min |
| README_TESTING.md | Riepilogo setup | 10 min |
| GITHUB_SETUP.md | Config GitHub | 15 min |
| TESTING_SUMMARY.md | Questo file | 2 min |

---

## 📝 File di Test Disponibili

### Unitari (Test rapidi, no emulator)
- `app/src/test/java/.../util/CardPriceUtilsTest.kt` - Utility functions
- `app/src/test/java/.../viewmodel/DeckLabViewModelTest.kt` - ViewModel
- `app/src/test/java/.../data/local/CardDaoTest.kt` - Database

### Instrumentati (Richiedono emulator)
- `app/src/androidTest/java/.../ComposeUITest.kt` - UI components
- `app/src/androidTest/java/.../data/local/SetDaoInstrumentedTest.kt` - Android DB

---

## 🌐 GitHub Actions

### Visualizzare i Risultati
1. Repository → **Actions** tab
2. Seleziona il workflow in esecuzione
3. Clicca su un job per i dettagli
4. Scarica artifacts in fondo

### Workflow Disponibili
- `android-tests.yml` - Base, veloce
- `android-advanced-tests.yml` - Completo, con coverage

---

## 🎯 Flusso di Sviluppo Consigliato

```
1. Scrivi il test
   └─ ./gradlew testDebugUnitTest

2. Implementa la feature
   └─ ./gradlew testDebugUnitTest

3. Fai commit e push
   └─ git push origin feature-branch

4. GitHub Actions esegue automaticamente
   └─ Vedi risultati in Actions tab

5. Apri PR
   └─ I test devono passare per il merge

6. Merge quando tutto è verde
   └─ ✅ Feature live!
```

---

## 🐛 Troubleshooting Rapido

| Problema | Comando |
|----------|---------|
| Test non riconosciuti | `./gradlew clean && ./gradlew testDebugUnitTest` |
| Timeout test | Aumenta `timeout-minutes: 60` in workflow |
| Emulator non parte | `emulator -avd Pixel_6_Pro_API_34` |
| Gradle lento | `./gradlew --stop && ./gradlew testDebugUnitTest` |

---

## 📊 Target di Qualità

| Metrica | Target | Come Misurare |
|---------|--------|----------------|
| Test Pass Rate | 100% | GitHub Actions |
| Code Coverage | 70%+ | `./gradlew testDebugUnitTest jacocoTestReport` |
| Build Time | <10 min | GitHub Actions Workflow |
| Critical Tests | 100% | Nessun skip di test |

---

## 💼 Setup GitHub (Opzionale)

### Branch Protection
1. Repository → Settings → Branches
2. Aggiungi rule per `main`
3. Abilita: "Require status checks to pass"
4. Seleziona: `unit-tests`, `instrumented-tests`

### Email Notifications
1. GitHub.com → Settings → Notifications
2. Email → "Notify me when tests fail"

### Slack (se usi Slack aziendale)
```
/github subscribe pokevault/pokevault workflows:push
```

---

## 🔗 Link Utili

- [JUnit Docs](https://junit.org/junit4/)
- [Mockk Docs](https://mockk.io/)
- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [Room Testing](https://developer.android.com/training/data-storage/room/testing-db)
- [GitHub Actions](https://docs.github.com/en/actions)
- [Jacoco](https://www.eclemma.org/jacoco/)

---

## ✅ Verifica Setup

```bash
# Esegui lo script di verifica
bash verify-testing-setup.sh

# Oppure manualmente:
./gradlew testDebugUnitTest
ls -la app/src/test/java/com/emabuia/pokevault/
ls -la .github/workflows/
```

---

## 🎓 Template Test Rapido

### Unit Test
```kotlin
class MyTest {
    @Test
    fun testFeature() {
        assertEquals("expected", myFunction())
    }
}
```

### Run It
```bash
./gradlew testDebugUnitTest --tests "*MyTest"
```

---

## 📱 Per Android Studio

### Eseguire Test da IDE
1. Right-click sul file test
2. **Run 'ClassName'**
3. Vedi i risultati nel pannello "Run"

### Visualizzare Coverage da IDE
1. Test → **Run with Coverage**
2. Report appare nel "Coverage" tool window

---

## 🚀 CI/CD Pipeline

```
Push → GitHub Actions → Unit Tests → Instrumented Tests → Lint → Build
  ↓
  PR Comment with Results
  ↓
  Status Check (Pass/Fail)
  ↓
  Merge if All Passing
```

---

## 📈 Metriche Settimanali

Ogni settimana, controlla:

- [ ] **Test Pass Rate**: 100%?
- [ ] **Code Coverage**: ↑ (trend positivo)?
- [ ] **Build Time**: < 10 min?
- [ ] **Failed Tests**: 0?

---

## 🎯 Checklist Onboarding

- [ ] Leggi QUICK_START_TESTING.md
- [ ] Esegui `./gradlew testDebugUnitTest`
- [ ] Vedi il report HTML
- [ ] Scarica il coverage report
- [ ] Fai il primo push
- [ ] Vedi GitHub Actions girare
- [ ] Configura branch protection
- [ ] Primo test fallimento -> fix -> push -> passa

---

## 💡 Pro Tips

### Esecuzione Veloce
```bash
# Solo test unitari (no UI)
./gradlew testDebugUnitTest

# Durante sviluppo: riesegui al salvataggio
./gradlew testDebugUnitTest -t
```

### Debugging Test Fallito
```bash
# Vedi lo stacktrace completo
./gradlew testDebugUnitTest --stacktrace

# Esegui da Android Studio per debug interattivo
```

### Coverage Incrementale
```bash
# Genera report
./gradlew testDebugUnitTest jacocoTestReport

# Apri in browser
open app/build/reports/jacoco/jacocoTestDebugUnitTestReport/html/index.html

# Identifica le aree con basso coverage (rosso)
```

---

## 🎉 Status: PRONTO ALL'USO

```
✅ Test Framework installato
✅ GitHub Actions configurato
✅ Documentation completa
✅ Esempio test creati
✅ Code coverage ready
✅ Pronto per il team
```

---

**Inizia ora**: `./gradlew testDebugUnitTest` 🚀

*Ultimo update: Maggio 2024 | v1.0*

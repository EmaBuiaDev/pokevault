# 🔒 Configurazione GitHub - Branch Protection & Notifications

Questa guida spiega come configurare GitHub per:
- Bloccare i push se i test falliscono (enforced CI)
- Ricevere notifiche sui fallimenti
- Visualizzare i test reports nelle PR

---

## 🛡️ Parte 1: Branch Protection Rules

### Passaggi:

1. **Vai nel tuo Repository su GitHub.com**
2. **Settings → Branches**
3. **Clicca su "Add rule" sotto "Branch protection rules"**
4. **Pattern**: `main` (proteggi il branch principale)
5. **Abilita**:
   - ☑️ "Require a pull request before merging"
   - ☑️ "Require status checks to pass before merging"
   - ☑️ "Require branches to be up to date before merging"

### 6. **Seleziona i Check Obbligatori**:

Dopo il primo push, vedrai questi check disponibili:
- ☑️ `build` - Compilazione riuscita
- ☑️ `unit-tests` - Test unitari passati
- ☑️ `instrumented-tests` - Test UI passati
- ☑️ `lint-analysis` - Analisi statica passata

### 7. **Salva le Regole**

Adesso:
- ❌ Non puoi fare push direttamente su `main` se i test falliscono
- ✅ Puoi fare push solo tramite PR approvate
- ✅ I test DEVONO passare prima del merge

---

## 📧 Parte 2: Notifiche via Email

### Per Ricevere Email sui Fallimenti:

1. **GitHub → Settings → Notifications** (profilo personale)
2. **Default notifications**: 
   - Seleziona: "Email" per i fallimenti
   - Seleziona: "Web" per gli avvisi
3. **Salva**

Riceverai email automaticamente quando:
- ❌ Un test fallisce
- ✅ Un test torna a passare
- 🔧 Qualcuno commenta la tua PR

### Configurazione Repository-Specific:

1. **Settings → Notifications** (dentro il repository)
2. **Scegli come essere notificato**:
   - Email per tutti gli eventi
   - Email solo per i fallimenti
   - Nessuna notifica

---

## 🔔 Parte 3: Slack Integration (Opzionale)

Se usi Slack aziendale:

### Installare Slack App per GitHub:

1. Vai a [GitHub's Slack App](https://github.com/apps/slack-github)
2. Clicca "Install"
3. Seleziona il tuo workspace Slack
4. Autorizza

### Configurare Notifiche Slack:

Nel channel Slack:
```
/github subscribe pokevault/pokevault workflows:push
```

Riceverai notifiche come:
```
🔴 @user's push failed tests
   main: CardPriceUtilsTest failed
   Link: [View Workflow]
```

---

## 📊 Parte 4: Visualizzare i Test Reports in PR

Quando apri una PR, GitHub mostra automaticamente:

### Status Check in PR:
```
1 pending status check
✅ build
✅ unit-tests
❌ instrumented-tests (3 failed)
⏳ lint-analysis (in progress)

1 required status check failing: instrumented-tests

View workflow runs
```

### Clicca su "Details" per Vedere:
- 📋 Quali test fallirono
- 📍 Che classe/metodo fallì
- 🔗 Link ai log completi

### Esempio Report in PR:

```
## Test Results Summary

✅ Unit Tests: 45 passed
❌ Instrumented Tests: 2 failed
  - SetDaoTest.testDeleteExpired
  - ComposeUITest.testButtonClick
✅ Build: Successful

[View Full Report] [Download Artifacts]
```

---

## 🚀 Parte 5: Workflow Automatico Consigliato

### Sviluppatore Locale:

```
1. Scrivi codice + test
2. ./gradlew testDebugUnitTest (locale)
3. git push (verso feature branch)
4. GitHub Actions esegue tutti i test
5. Se ok: Apri PR
6. Reviewer approva
7. GitHub auto-merge se tutti i test passano
```

### GitHub Actions Automaticamente:

```
On: Push verso main/develop
↓
Run: Unit Tests (2 min)
↓
Run: Instrumented Tests (10 min)
↓
Run: Lint Analysis (1 min)
↓
Run: Build APK (5 min)
↓
Report: All ✅ or Some ❌
↓
Comment: Publish results on PR
↓
Status Check: Pass/Fail
```

### Branch Protection:

```
PR aperta → Tutti i test devono passare → Approved by reviewer → Merge automatico
```

---

## ✅ Verifica Setup

Per verificare che tutto è configurato correttamente:

### Checklist:

- [ ] Branch protection attiva su `main`
- [ ] `unit-tests` required prima di merge
- [ ] `instrumented-tests` required prima di merge
- [ ] Email notifications abilitate
- [ ] Primo push trigger GitHub Actions
- [ ] Artifacts disponibili per download
- [ ] PR mostra lo stato dei test

### Test Fallito Intenzionale (Verifica):

1. Apri il file `CardPriceUtilsTest.kt`
2. Modifica un assert:
   ```kotlin
   assertEquals(0.0, result, 0.0)  // Cambia a:
   assertEquals(999.0, result, 0.0)
   ```
3. Fai push
4. Vedi GitHub Actions fallire ❌
5. Ricevi email di notifica
6. Ripristina il file
7. Fai push di nuovo
8. Vedi GitHub Actions passare ✅

---

## 🎯 Best Practices

### ✅ Fai:

- ✅ Configura branch protection per `main`
- ✅ Richiedi test passing come condition
- ✅ Abilita le notifiche email
- ✅ Rivedi i report prima di merge
- ✅ Scrivi test per ogni bug fix
- ✅ Mantieni il coverage > 70%

### ❌ Non Fare:

- ❌ Disabilitare i test prima di merge
- ❌ Fare force push su main
- ❌ Ignorare i fallimenti di test
- ❌ Ridurre il coverage intentionally
- ❌ Skippare test critici

---

## 📈 Monitoraggio Settimanale

Ogni settimana, controlla:

1. **GitHub Actions Dashboard**: Vedi quanti test passano
2. **Code Coverage Report**: Aumenta il coverage
3. **PR Comments**: Leggi il feedback automatico
4. **Failed Tests**: Fissi i test che falliscono

---

## 🔗 Link Utili

- [GitHub Branch Protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches)
- [GitHub Actions](https://docs.github.com/en/actions)
- [GitHub Notifications](https://docs.github.com/en/account-and-profile/managing-subscriptions-and-notifications-on-github)
- [Slack GitHub App](https://github.com/apps/slack-github)

---

**Ora sei completamente configurato per un flusso di lavoro CI/CD professionale!** 🎉

*Ultimo aggiornamento: Maggio 2024*

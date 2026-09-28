# Piano di localizzazione

Obiettivo: tutti i testi dell'app seguono la lingua del dispositivo. Oggi sono in italiano,
scritti direttamente nel codice.

## 1. Situazione attuale

| Area | Dove | Note |
|---|---|---|
| Testi delle schermate | `ui/**/*Screen.kt` (~140 stringhe) | `Text("...")`, label, placeholder, dialog, snackbar |
| Descrizioni per l'accessibilità | 14 `contentDescription` | "Indietro", "Impostazioni", "Elimina"... |
| Messaggi generati fuori dalla UI | `GeminiException`, `ProductLookupException`, `BarcodeViewModel`, `EntryViewModel`, `PhotoReviewViewModel` | messaggi di errore e stati creati come `String` in ViewModel e data layer |
| Etichette degli enum | `MealType.label`, `ServingUnit` (singolare/plurale/menu), `ThemeMode.label`, `HistoryRange.label` | |
| Formattazione | `ui/Format.kt`, `CalorieChart.kt` | `Locale.ITALIAN` fisso per date, giorni della settimana e virgola decimale; "Oggi/Ieri/Domani" nel codice |
| Contenuti prodotti dall'AI | `GeminiPrompts.kt`, `FoodAnalysis.kt` | prompt in italiano, quindi nomi degli alimenti e note arrivano in italiano; `confidence` vale "alta/media/bassa" |
| Open Food Facts | `OpenFoodFactsClient.kt` | usa `product_name_it` |
| Tabelle CREA | `assets/crea_foods.tsv` | nomi solo in italiano (è la fonte) |

In totale sono circa 190 occorrenze, cioè circa 170 stringhe distinte.

## 2. Scelte proposte

1. **Lingue**: inglese come lingua predefinita in `values/`, usata per qualunque lingua non
   tradotta, e italiano in `values-it/`. La struttura permette di aggiungere altre lingue
   con un solo file `strings.xml` in più.
2. **Lingua del dispositivo**: è il comportamento standard di Android con le risorse, quindi
   niente selettore nell'app. Con `generateLocaleConfig` Android 13+ mostra anche
   "Lingua dell'app" nelle impostazioni di sistema, e si può usare l'app in una lingua
   diversa da quella del telefono.
3. **Unità**: solo metriche (g, ml, kcal). Tazzina, tazza, bicchiere e calice mantengono le
   stesse capienze e cambiano solo nome (in inglese: espresso cup, cup, glass, wine glass).
4. **Contenuti inseriti dall'utente** (nomi delle voci): non vengono tradotti.

## 3. Interventi

### Passo 1 — Infrastruttura
- `app/src/main/res/resources.properties` con `unqualifiedResLocale=en`, e
  `androidResources { generateLocaleConfig = true }` in `app/build.gradle.kts`.
- Pseudo-localizzazioni nella build di debug (`isPseudoLocalesEnabled = true`, lingue en-XA
  e ar-XB) per trovare i testi rimasti nel codice e quelli troncati.
- Tipo `UiText` (`sealed interface`: `Resource(@StringRes id, args)` / `Plural(...)` /
  `Raw(text)`), usato dai ViewModel e dal data layer al posto delle `String`, e risolto nella
  UI con `stringResource`.

### Passo 2 — Testi delle schermate
- Si estraggono schermata per schermata in `values/strings.xml` (inglese) e
  `values-it/strings.xml` (i testi italiani attuali), con nomi per schermata
  (`today_empty`, `entry_title_new`, `settings_gemini_title`...).
- Testi con valori: placeholder posizionali (`Rimangono %1$s kcal` /
  `%1$s kcal left`).
- Plurali con `<plurals>`: "2 calici", "5 giorni su 7", "1 porzione".
- Anche le `contentDescription`.

### Passo 3 — Enum e messaggi
- `MealType`, `ThemeMode`, `HistoryRange`: `@StringRes labelRes` al posto di `label`.
- `ServingUnit`: `@PluralsRes` per il nome e `@StringRes` per la voce di menu. **I nomi
  delle costanti (`TAZZINA`, `CALICE`...) restano invariati**, perché sono salvati nel
  database: cambiarli richiederebbe una migrazione.
- `GeminiException` e `ProductLookupException` non contengono più testo: la UI associa il
  tipo di errore a una risorsa. I dettagli tecnici (codice HTTP, messaggio del server)
  restano come argomenti.
- Stati dei ViewModel (`AiEstimate.Failed`, `BarcodeStatus.Error`, `ReviewStatus.Error`)
  con `UiText`.

### Passo 4 — Numeri e date
- `Format.kt` usa la lingua corrente (`LocalConfiguration.current.locales[0]` nella UI,
  `Locale.getDefault()` altrove) invece di `Locale.ITALIAN`.
- Date con `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM/FULL)` oppure
  `DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy")`, così l'ordine giorno/mese
  segue la lingua.
- "Oggi/Ieri/Domani" diventano risorse. I giorni della settimana del grafico usano la
  lingua corrente.
- `parseDecimal` accetta già sia la virgola che il punto e resta così. I numeri si
  mostrano con il separatore della lingua.

### Passo 5 — AI e fonti esterne
- **Prompt Gemini**: un'unica versione in inglese, più chiara per il modello, con
  l'istruzione "scrivi nomi e note in {lingua}", dove {lingua} è il nome della lingua del
  dispositivo (es. "Italian", "English"). Non si mantiene un prompt per ogni lingua.
- **`confidence`**: valori neutri nello schema (`high`/`medium`/`low`), tradotti nella UI.
- **Tabelle CREA**: restano in italiano, come la fonte. Nella revisione il nome CREA si
  mostra così com'è ("Valori CREA: Pasta di semola, cotta"). Proposta: attive di default
  solo se la lingua del dispositivo è l'italiano, perché descrivono alimenti italiani;
  l'interruttore resta disponibile per tutti.
- **Open Food Facts**: si chiede `product_name_{lingua}` in base alla lingua del
  dispositivo, con `product_name` come ripiego.

### Passo 6 — Verifica
- Test JVM di `Format.kt` con `Locale.ITALY` e `Locale.US` (decimali, date, quantità).
- Controllo di tutte le schermate in pseudo-locale en-XA (testi lunghi e nel codice) e in
  ar-XB (layout da destra a sinistra).
- Screenshot sull'emulatore in italiano e in inglese (`adb shell cmd locale set-app-locales`).
- Rilettura della traduzione inglese.

## 4. Decisioni da prendere

1. Lingua predefinita per le lingue non tradotte: **inglese** (consigliato) o italiano?
2. Oltre a italiano e inglese servono altre lingue adesso?
3. Tabelle CREA attive di default solo in italiano, oppure sempre?
4. Bastano la lingua del dispositivo e la "Lingua dell'app" di Android 13+, oppure serve
   anche un selettore dentro l'app?

## 5. Ordine consigliato

Passo 1 → 2 → 3 in un unico blocco, così da non mescolare testi estratti e testi nel
codice; poi il 4, poi il 5 (tocca i prompt: da riprovare con la API key); il 6 alla fine
di ogni passo.

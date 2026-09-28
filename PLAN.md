# PLAN.md — Contacalorie Android con riconoscimento foto

> Istruzioni per Claude Code: leggi tutto il file prima di iniziare. Lavora una fase alla volta, nell'ordine indicato. Alla fine di ogni fase verifica che il progetto compili (`./gradlew assembleDebug`) e fermati per farmi testare prima di passare alla fase successiva. Commenti e testi UI in italiano.

## 1. Obiettivo

App Android **personale** (sideload dell'APK, nessuna pubblicazione sugli store) per contare le calorie giornaliere di cibi e bevande. Funzione chiave: **scatto una foto al pasto → l'AI riconosce gli alimenti e stima grammi e kcal → confermo/correggo → salvo**.

## 2. Vincoli

- Solo Android. Nessun backend, nessun login, nessun server: tutto locale sul telefono.
- Costo zero: Gemini API free tier (Google AI Studio), Open Food Facts (API pubblica).
- Semplicità prima di tutto: un solo modulo Gradle, niente over-engineering.
- La API key **non** va nel codice né nel repository: l'utente la incolla in una schermata Impostazioni.

## 3. Stack

| Ambito | Scelta |
|---|---|
| Linguaggio / UI | Kotlin + Jetpack Compose (Material 3) |
| Architettura | MVVM semplice (ViewModel + StateFlow), DI manuale (niente Hilt) |
| Database | Room (SQLite) |
| Preferenze | DataStore Preferences (obiettivo kcal, API key, modello) |
| Fotocamera | `ActivityResultContracts.TakePicture` + FileProvider (fotocamera di sistema) |
| Rete | OkHttp + kotlinx.serialization, chiamata **REST diretta** all'endpoint Gemini `generateContent` (non usare SDK deprecati) |
| Barcode | ML Kit Barcode Scanning (on-device, gratuito) |
| Immagini | Coil per le miniature |
| minSdk / targetSdk | 26 / ultima stabile |

**Modello Gemini:** configurabile da Impostazioni. Default: il modello Flash più recente disponibile nel free tier. Verifica i nomi attuali dei modelli nella documentazione ufficiale (ai.google.dev) prima di impostare il default.

## 4. Modello dati (Room)

```kotlin
@Entity data class FoodEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val date: LocalDate,          // giorno a cui appartiene
  val mealType: MealType,       // COLAZIONE, PRANZO, CENA, SPUNTINO
  val name: String,
  val grams: Double?,
  val kcal: Double,
  val proteinG: Double?,
  val carbsG: Double?,
  val fatG: Double?,
  val source: Source,           // PHOTO, MANUAL, BARCODE
  val photoPath: String?,       // file locale, facoltativo
  val createdAt: Instant
)
```

Type converter per `LocalDate`, `Instant` e gli enum. DAO con: inserimento, modifica, cancellazione, elenco per giorno (Flow), totali per giorno (Flow), totali per intervallo (storico).

## 5. Schermate

1. **Oggi**: data con frecce ← → per cambiare giorno; barra di avanzamento kcal consumate / obiettivo; macro totali; elenco voci raggruppate per pasto; pulsante flottante "+" con tre azioni: *Foto*, *Barcode*, *Manuale*.
2. **Revisione foto**: miniatura della foto, campo opzionale "note" (es. "con olio", "porzione grande"), pulsante *Analizza*; poi elenco degli alimenti riconosciuti con grammi e kcal **modificabili**, possibilità di eliminare o aggiungere righe, scelta del pasto, *Salva*.
3. **Inserimento manuale**: nome, grammi, kcal (obbligatorie), macro facoltative. Pulsante opzionale "Stima con AI" che manda solo testo a Gemini (es. "2 fette pane integrale").
4. **Barcode**: scansione → Open Food Facts → valori per 100 g → inserisco i grammi → calcolo automatico.
5. **Storico**: grafico semplice delle kcal degli ultimi 7/30 giorni (Canvas Compose, niente librerie pesanti).
6. **Impostazioni**: API key Gemini, modello, obiettivo kcal giornaliero, pulsante "Test connessione".

## 6. Integrazione Gemini (foto)

- Prima dell'invio: ridimensiona la foto a lato lungo max ~1024 px, JPEG qualità ~80, in Base64 (`inline_data`, `image/jpeg`).
- `generationConfig`: `responseMimeType = "application/json"` e `responseSchema` con questa struttura:

```json
{
  "items": [
    { "name": "string", "grams": 0, "kcal": 0,
      "protein_g": 0, "carbs_g": 0, "fat_g": 0,
      "confidence": "alta|media|bassa" }
  ],
  "notes": "string"
}
```

- Prompt di sistema (in italiano): identifica ogni alimento visibile, stima i grammi dalla porzione, calcola kcal e macro con valori nutrizionali standard, considera condimenti probabili e segnalali in `notes`, non inventare alimenti non visibili, se la foto non contiene cibo restituisci `items` vuoto.
- Includi nel prompt le note scritte dall'utente, se presenti.
- Il risultato **non si salva mai automaticamente**: passa sempre dalla schermata di revisione.

## 7. Gestione errori

- HTTP 429: messaggio chiaro ("limite richieste raggiunto, riprova tra poco") + retry con backoff esponenziale (1 s, 2 s, 4 s, max 3 tentativi).
- Nessuna rete / API key mancante o errata: messaggio chiaro e rimando alle Impostazioni.
- JSON non valido: un nuovo tentativo, poi errore con possibilità di inserimento manuale.

## 8. Fasi di sviluppo

**Fase 1 — Scheletro e diario manuale**
Progetto Compose, Room, DataStore, navigazione, schermata Oggi, inserimento manuale, modifica/cancellazione voci, obiettivo kcal.
✅ Criterio: aggiungo voci a mano, vedo totali corretti, cambio giorno, i dati restano dopo il riavvio dell'app.

**Fase 2 — Foto + Gemini**
Impostazioni con API key e test connessione, scatto foto, client REST Gemini, schermata Revisione, salvataggio.
✅ Criterio: foto di un piatto → elenco alimenti modificabile → salvato nel diario.

**Fase 3 — Barcode**
ML Kit + Open Food Facts, calcolo per grammi.
✅ Criterio: scansiono un prodotto confezionato e lo aggiungo con i grammi scelti.

**Fase 4 — Storico e rifiniture**
Grafico 7/30 giorni, tema chiaro/scuro, icona app, stima AI testuale nell'inserimento manuale.

**Fase 5 (facoltativa) — Precisione cucina italiana**
Import delle Tabelle di composizione degli alimenti CREA in un database SQLite incluso negli asset; l'AI identifica alimento e grammi, le kcal vengono prese dal database quando c'è una corrispondenza.

## 9. Build e installazione

- APK di debug: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Installazione: `adb install -r app-debug.apk` (debug USB attivo) oppure copio l'APK sul telefono.
- Facoltativo più avanti: APK release firmato con keystore locale (keystore e password fuori dal repository, in `local.properties` o variabili d'ambiente).
- `.gitignore`: escludere `local.properties`, keystore, build.

## 10. Fuori scope

Login, sync cloud, backend, pubblicazione sugli store, notifiche, integrazione Health Connect (eventuale idea futura).

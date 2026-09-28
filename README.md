# PhotoCal

App Android personale per contare le calorie giornaliere: diario manuale, riconoscimento
dei pasti da foto con Gemini, scansione barcode con Open Food Facts. Tutto locale sul
telefono, nessun backend. Il piano completo è in [PLAN.md](PLAN.md).

## Stato

- [x] Fase 1 — Scheletro e diario manuale
- [x] Fase 2 — Foto + Gemini
- [x] Fase 3 — Barcode
- [x] Fase 4 — Storico e rifiniture
- [x] Fase 5 — Tabelle CREA

## Note d'uso

- **API key Gemini**: si crea gratis su [Google AI Studio](https://aistudio.google.com/apikey) e si
  incolla in Impostazioni. Resta solo sul telefono ed è esclusa dal backup di Android.
- **Bevande**: nell'inserimento manuale la quantità si può indicare in tazzine (30 ml), tazze
  (250 ml), bicchieri (200 ml), calici (150 ml) o ml; l'app salva i ml come grammi (1 ml ≈ 1 g).
  Le capienze sono in `ServingUnit.kt`.
- **Stima AI da testo**: nell'inserimento manuale "Stima con AI" usa nome e quantità
  (es. "2 fette di pane integrale"). Dopo la stima, cambiando la quantità kcal e macro si
  ricalcolano; se li modifichi a mano, comandano i tuoi valori.
- **Storico**: grafico 7/30 giorni (Canvas), media, giorni registrati ed entro l'obiettivo,
  calcolati sui giorni conclusi. I colori del grafico sono fissi e verificati per il daltonismo.
- **Tema**: Sistema/Chiaro/Scuro e colori dinamici (Android 12+) in Impostazioni.
- **Tabelle CREA**: con l'opzione attiva (Impostazioni) l'AI riceve l'elenco degli alimenti
  CREA e indica per ogni voce il codice corrispondente; kcal e macro vengono allora dalla
  tabella (valori per 100 g × grammi stimati). Nella revisione foto ogni riga dice da dove
  arrivano i valori e si può passare alla stima dell'AI.
- **Lingua**: italiano e inglese. Segue la lingua del telefono oppure la scelta in
  Impostazioni → Aspetto → Lingua. I testi sono in `res/values/strings.xml` (italiano,
  predefinito) e `res/values-en/strings.xml`; dettagli in
  [docs/LOCALIZATION_PLAN.md](docs/LOCALIZATION_PLAN.md).
- **Barcode**: lo scanner è quello di Google Play services (nessun permesso fotocamera); il
  modulo viene scaricato all'installazione. I valori nutrizionali arrivano da
  [Open Food Facts](https://world.openfoodfacts.org) (API v3). Il codice si può anche digitare.

## Stack

Kotlin, Jetpack Compose (Material 3), MVVM con DI manuale, Room, DataStore,
Navigation Compose, OkHttp + kotlinx.serialization (REST Gemini, Open Food Facts), Coil,
Google Code Scanner (ML Kit). minSdk 26, targetSdk 37.

## Dati CREA

`app/src/main/assets/crea_foods.tsv` è generato da `tools/crea/build_crea_table.py`, che legge
le pagine del portale [alimentinutrizione.it](https://www.alimentinutrizione.it/tabelle-nutrizionali)
(una richiesta al secondo, con cache in `tools/crea/.cache/`). Per rigenerarlo:

```bash
python3 tools/crea/build_crea_table.py
```

Fonte dei dati: CREA – Centro di ricerca Alimenti e Nutrizione, *Tabelle di composizione
degli alimenti*. Le condizioni d'uso del sito chiedono di citare la fonte.

## Build e installazione

Serve `local.properties` con il percorso dell'Android SDK (Android Studio lo crea da solo):

```
sdk.dir=/Users/<utente>/Library/Android/sdk
```

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest   # test JVM
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In alternativa si copia `app-debug.apk` sul telefono e lo si installa da lì.

## Struttura

```
app/src/main/java/it/emanuelemelini/photocal/
├── PhotoCalApp.kt        Application + AppContainer (DI manuale)
├── MainActivity.kt
├── data/
│   ├── db/               Room: FoodEntry, DAO, converter
│   ├── prefs/            DataStore: impostazioni
│   ├── gemini/           client REST generateContent, prompt, schema
│   ├── crea/             tabelle CREA (da assets/crea_foods.tsv)
│   ├── estimate/         stima AI + sostituzione con i valori CREA
│   ├── openfoodfacts/    client API v3, prodotti
│   ├── http/             utilità OkHttp condivise
│   ├── photo/            file delle foto, ridimensionamento
│   └── FoodRepository.kt
└── ui/
    ├── today/            schermata Oggi
    ├── entry/            inserimento/modifica manuale
    ├── photo/            revisione dell'analisi foto
    ├── barcode/          scansione e prodotto da Open Food Facts
    ├── history/          storico e grafico
    ├── components/       campi del form condivisi (quantità/unità, pasto)
    ├── settings/         impostazioni
    └── theme/
```

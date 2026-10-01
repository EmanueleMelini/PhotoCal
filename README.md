# PhotoCal

App Android personale per contare le calorie giornaliere: diario manuale, riconoscimento
dei pasti da foto con l'AI che scegli (Gemini, OpenAI, Claude o un servizio compatibile
OpenAI), scansione barcode con Open Food Facts. Diario solo sul telefono, nessun backend;
Firebase per le notifiche con le novità e, solo con il consenso, statistiche e report dei crash. Il piano completo è in [PLAN.md](PLAN.md).

Sito: **[photocal.emanuelemelini.dev](https://photocal.emanuelemelini.dev)**, con la presentazione
dell'app e il download dell'ultima versione.

## Stato

- [x] Fase 1 — Scheletro e diario manuale
- [x] Fase 2 — Foto + Gemini
- [x] Fase 3 — Barcode
- [x] Fase 4 — Storico e rifiniture
- [x] Fase 5 — Tabelle CREA

## Note d'uso

- **AI e API key** (Impostazioni → Intelligenza artificiale): si sceglie il servizio e si incolla
  la sua chiave. Gemini ha una chiave gratuita su [Google AI Studio](https://aistudio.google.com/apikey);
  OpenAI e Claude (Anthropic) usano le chiavi a pagamento di [OpenAI Platform](https://platform.openai.com/api-keys)
  e della [Claude Platform](https://platform.claude.com/settings/keys). "Compatibile OpenAI" chiede
  l'indirizzo base (solo `https://`, es. `https://openrouter.ai/api/v1`), il modello e, se serve,
  la chiave: va bene ogni servizio con l'API Chat Completions; per le foto il modello deve leggere
  le immagini. Ogni AI tiene la sua chiave e il suo modello (`gemini_api_key` è la stessa chiave
  delle versioni prima della 1.5.0). Le chiavi restano solo sul telefono, sono escluse dal backup
  di Android e dal file di backup, che salva invece l'AI scelta, i modelli e l'indirizzo.
- **Risposta dell'AI**: stessi prompt e stesso JSON per tutte; cambia solo come si vincola lo
  schema (`responseSchema` per Gemini, JSON Schema `strict` per OpenAI, `output_config.format`
  per Claude). Se un servizio compatibile non conosce `json_schema`, l'app riprova in modalità
  `json_object` con lo schema nel prompt. Con Claude Opus 5.5, Opus 5, Fable 5.1 e Sonnet 5.5
  è attivo il `fallbacks: "default"` lato server, che rifà la richiesta su un altro modello se i
  filtri di sicurezza la rifiutano.
- **Se l'AI non risponde** (errore del servizio, risposta non valida, rifiuto; non per rete o
  impostazioni da correggere) la revisione della foto mostra Riprova, "Cambia modello" e
  Inserisci a mano, l'inserimento manuale "Cambia modello". Porta alle Impostazioni già sulla
  sezione AI (`SettingsRoute(showAi = true)`), come il pulsante Impostazioni degli errori di
  configurazione.
- **Bevande**: nell'inserimento manuale la quantità si può indicare in tazzine (30 ml), tazze
  (250 ml), bicchieri (200 ml), calici (150 ml) o ml; l'app salva i ml come grammi (1 ml ≈ 1 g).
  Le capienze sono in `ServingUnit.kt`.
- **Stima AI da testo**: nell'inserimento manuale "Stima con AI" usa nome e quantità
  (es. "2 fette di pane integrale"). Dopo la stima, cambiando la quantità kcal e macro si
  ricalcolano; se li modifichi a mano, comandano i tuoi valori.
- **Storico**: grafico 7/30 giorni (Canvas), media, giorni registrati ed entro l'obiettivo,
  calcolati sui giorni conclusi. I colori del grafico sono fissi e verificati per il daltonismo.
- **Tema**: Sistema / Chiaro / Scuro / Viola / Viola scuro in Impostazioni. I primi tre usano
  la palette verde (o i colori dinamici di Android 12+); Viola (viola medio) e Viola scuro
  (viola profondo) hanno superfici viola con accenti rosa, non dipendono dalla modalità del
  telefono e non usano i colori dinamici.
- **Icona dell'app**: verde (predefinita), nera o viola, in Impostazioni → Aspetto. Ogni icona
  è un `activity-alias` nel manifest e ne è attivo uno solo alla volta (`AppIcon.kt`).
- **Tabelle CREA**: con l'opzione attiva (Impostazioni) l'AI riceve l'elenco degli alimenti
  CREA e indica per ogni voce il codice corrispondente; kcal e macro vengono allora dalla
  tabella (valori per 100 g × grammi stimati). Nella revisione foto ogni riga dice da dove
  arrivano i valori e si può passare alla stima dell'AI.
- **Profilo e obiettivi** (Impostazioni → Obiettivo, oppure toccando il riepilogo in Oggi):
  sesso, anno di nascita, altezza, peso, attività e obiettivo, più l'obiettivo dell'acqua. L'app stima metabolismo basale
  (formula di Mifflin-St Jeor), fabbisogno giornaliero e calorie suggerite, con un minimo di
  sicurezza durante il dimagrimento (1200/1500 kcal, deficit al massimo del 25%), e propone
  proteine, carboidrati e grassi. I valori suggeriti si applicano con un tocco, ma tutti gli
  obiettivi si possono scrivere a mano (per esempio quelli dati dal medico). Con gli obiettivi
  dei macro, Oggi mostra i progressi anche per proteine, carboidrati e grassi.
- **Acqua**: in Oggi un contatore dei bicchieri del giorno con − e +; l'obiettivo è nel profilo,
  in bicchieri, con la capienza del bicchiere (200 ml predefiniti, modificabile). Il predefinito è
  8 bicchieri (1,6 L). Il suggerimento segue i LARN/EFSA: 2,5 L di acqua totale per gli uomini e
  2,0 L per le donne (30 ml per kg se il peso dà di più), di cui l'80% da bere, più 200 ml per
  ogni livello di attività, arrotondati per eccesso a bicchieri interi. I bevuti sono salvati in
  ml: cambiando la capienza, i giorni passati mantengono i litri (e possono avere bicchieri con
  decimali).
- **Widget**: "Bicchieri d'acqua" (3×2) mostra i bicchieri di oggi `X/TOT` tra − e +, e sotto due
  scorciatoie per il diario di oggi: fotocamera (pasto da foto) e + (inserimento manuale). Toccando
  il contatore si apre l'app. È un widget RemoteViews (nessuna libreria): si aggiorna a ogni
  modifica e si azzera entro 10 minuti dopo la mezzanotte (allarme non esatto, senza risvegliare
  il telefono). I colori sono quelli verdi fissi, chiari o scuri secondo il telefono.
- **Scorciatoie dell'app**: tenendo premuta l'icona compaiono "Foto pasto", "Pasto a mano" e
  "+1 bicchiere" (si possono anche trascinare sulla home). "+1 bicchiere" aggiunge un bicchiere
  a oggi senza aprire l'app e mostra il nuovo conteggio in un messaggio. Sono scorciatoie
  dinamiche (`AppShortcuts.kt`): seguono la lingua dell'app e vengono spostate sull'alias
  dell'icona scelta.
- **Profilo e condivisione**: nel profilo si mettono nome e foto facoltativa (galleria o
  fotocamera, ritagliata al centro). Con il nome compare "Condividi" in Oggi (e come
  scorciatoia dell'icona): si condivide il giorno mostrato o i 7 giorni che finiscono lì,
  scegliendo cosa nascondere (foto, obiettivi, calorie, macro, acqua, pasti, peso; di default
  si vede tutto) con l'anteprima esatta di ciò che vedrà chi riceve. Il link
  `https://photocal.emanuelemelini.dev/d#…` apre PhotoCal in una schermata di sola lettura;
  nulla viene salvato nel diario di chi lo apre. I dati stanno dopo il `#` (il browser non li
  manda al server), come JSON compresso in base64url con una firma HMAC-SHA256: un link
  modificato o troncato viene rifiutato. La firma protegge dalle modifiche a mano, non da chi
  estrae la chiave dall'APK (senza server non si può).
- **Registro peso**: pesate (una al giorno) con grafico a 30/90/365 giorni; il peso del
  profilo è l'ultima pesata. Si apre dal profilo o dallo Storico.
- **Promemoria**: notifiche locali (nessun server) per colazione, pranzo, spuntino e cena,
  più un riepilogo serale e uno settimanale (domenica). Si attivano in Impostazioni →
  Promemoria, dove si sceglie anche l'orario; il permesso notifiche viene chiesto solo allora.
  Il promemoria di un pasto non arriva se quel pasto è già nel diario di oggi, e sparisce
  appena lo registri. Per l'acqua ci sono "metà obiettivo" (15:00) e "obiettivo" (22:00): non
  arrivano se a quell'ora hai già bevuto abbastanza, dicono quanti bicchieri mancano, hanno il
  pulsante "+1 bicchiere" e spariscono appena la soglia è raggiunta (da notifica, app o widget). Arrivano entro 10 minuti dall'orario scelto (allarmi non esatti,
  nessun permesso speciale) e vengono riprogrammati dopo un riavvio del telefono.
- **Lingua**: italiano e inglese. Segue la lingua del telefono oppure la scelta in
  Impostazioni → Aspetto → Lingua. I testi sono in `res/values/strings.xml` (italiano,
  predefinito) e `res/values-en/strings.xml`; dettagli in
  [docs/LOCALIZATION_PLAN.md](docs/LOCALIZATION_PLAN.md).
- **Aggiornamenti**: all'avvio (solo nelle build release, al massimo una volta al giorno)
  PhotoCal legge l'ultima Release dall'API pubblica di GitHub (senza token) e, se la versione è
  più nuova di quella installata, propone "Aggiorna" o "Più tardi" (che vuol dire: ne riparliamo
  domani). Il controllo si può lanciare anche da Impostazioni → Info, in ogni build. "Aggiorna"
  scarica l'APK dentro una sessione di `PackageInstaller` e Android chiede la conferma; serve il
  permesso "Installa app sconosciute" per PhotoCal, chiesto solo allora. Senza il permesso, o se
  qualcosa va storto, resta il link per scaricare l'APK dal browser. Android rifiuta un APK con
  una firma diversa da quella dell'app installata, quindi sopra una build debug l'aggiornamento
  non si installa.
- **Promemoria "Nuova versione"** (spento di default, ore 10:00): all'orario scelto chiede a
  GitHub l'ultima Release e, se è più nuova di quella installata, mostra una notifica sul canale
  "Aggiornamenti" con la prima novità. Avvisa una sola volta per versione (`last_notified_update`
  nelle preferenze); senza rete tace e riprova il giorno dopo. Toccandola si apre l'app con il
  dialogo di aggiornamento, anche se il controllo giornaliero è già stato fatto. Come il
  controllo all'avvio, non fa nulla nelle build debug. Per provarlo sull'emulatore serve una
  build che si dichiari più vecchia dell'ultima Release e `adb root` per lanciare la sveglia:
  `adb shell am broadcast -n it.emanuelemelini.photocal/.data.reminders.ReminderAlarmReceiver
  --es reminder_type UPDATE`.
- **Novità**: alla prima apertura dopo un aggiornamento compare il changelog delle versioni
  nuove, in italiano o in inglese secondo la lingua dell'app. Dopo un'installazione da zero non
  compare. Si rilegge in Impostazioni → Info.
- **Barcode**: lo scanner è quello di Google Play services (nessun permesso fotocamera); il
  modulo viene scaricato all'installazione. I valori nutrizionali arrivano da
  [Open Food Facts](https://world.openfoodfacts.org) (API v3). Il codice si può anche digitare.
- **Pezzi (barcode)**: oltre a grammi e ml, la quantità si può dare in pezzi (es. 3 biscotti).
  Scegliendo "pezzi" compare "Grammi per pezzo", sempre modificabile: è già compilato quando il
  testo della porzione su Open Food Facts (`serving_size`) dice quanti pezzi sono, per esempio
  "3 biscotti (25 g)" o "25 g (3 biscuits)" (`ServingSize.kt`). Se quel testo nomina i pezzi al
  plurale, nel diario compare il suo nome ("3 biscotti (25 g)"), altrimenti "pezzi"/"pieces".
  Il diario salva i grammi totali, il numero di pezzi e il nome; i grammi di un pezzo si
  ricavano da questi. Nell'inserimento manuale "pezzi" compare solo per un alimento già usato
  in pezzi (dai recenti o dai suggerimenti).
- **Recenti e preferiti**: ogni voce nuova (foto, barcode, manuale) salva il suo alimento nella
  tabella `saved_foods`, con i valori per 100 g e l'ultima quantità; il barcode per codice, gli
  altri per nome. "Recenti" nel menu + mostra i preferiti (cuore) e gli ultimi 50, con ricerca;
  tenendo premuto si toglie un alimento. Toccandolo si apre il form già compilato, con kcal e
  valori che seguono la quantità. Nell'inserimento manuale, da 2 lettere del nome compaiono fino
  a 5 suggerimenti. Le copie non contano come uso. Il barcode usa il nome e i grammi per pezzo
  salvati e, senza connessione, il prodotto salvato.
- **Copia**: pressione lunga su una voce → "Duplica" (stesso giorno e pasto) o "Copia in…";
  l'icona accanto al pasto copia il pasto, quella in alto il giorno (ogni voce nel suo pasto).
  Il dialogo propone oggi, o domani se si copia da oggi. La foto è condivisa tra le copie.
- **Fibre, zuccheri e sale**: da Open Food Facts (`fiber_100g`, `sugars_100g`, `salt_100g`, o
  sodio × 2,5), dalle tabelle CREA e, facoltativi, dalla stima AI. Si scrivono anche a mano
  ("Altri valori") e il totale del giorno compare sotto le macro in Oggi. Nessun obiettivo.
- **Backup** (Impostazioni → Dati): "Esporta backup" scrive un file JSON (`data/backup/`) con
  diario, peso, acqua, alimenti salvati e impostazioni, senza API key né foto (di ogni foto
  resta il nome del file, ricollegato se la foto è ancora sul telefono). "Ripristina" controlla
  formato, versione e valori, poi chiede: **Sostituisci tutto** (cancella i dati del telefono e
  ripristina anche le impostazioni) o **Unisci** (aggiunge le voci che mancano, confrontando
  giorno, pasto, nome e kcal; peso e acqua solo dei giorni vuoti; impostazioni invariate).
  "Esporta diario (CSV)" scrive una riga per voce: in italiano `;` e virgola decimale, in
  inglese `,` e punto, UTF-8 con BOM. Si usa il selettore file di Android (nessun permesso).
- **Salute di Android** (Health Connect, Impostazioni): "Collega" chiede i permessi di lettura di
  passi e calorie attive e di scrittura di peso e acqua. Oggi mostra passi e kcal bruciate del
  giorno; con "Aggiungi le calorie bruciate all'obiettivo" (spento di default) l'obiettivo del
  giorno è base + bruciate (conviene mettere "Sedentario" nel profilo). Con "Scrivi peso e
  acqua" ogni pesata e ogni bicchiere da quel momento vanno in Health Connect, un record per
  giorno con id fisso (`photocal-weight-<data>`, `photocal-water-<data>`), aggiornato a ogni
  modifica. Storico, promemoria e condivisione usano l'obiettivo base. Su Android 13 e precedenti
  serve l'app Health Connect dal Play Store. La pagina "Dati di salute e privacy" è quella che
  Health Connect apre dalla schermata dei permessi.
- **Statistiche e crash** (Firebase, `data/telemetry/Telemetry.kt`): Analytics e Crashlytics sono
  spenti nel manifest e si accendono solo con il consenso, chiesto al primo avvio (prima del
  changelog) e modificabile in Impostazioni → Privacy. Senza consenso le statistiche vengono
  azzerate e i crash non inviati cancellati; l'ID pubblicitario non viene mai raccolto. Si
  inviano solo nomi fissi: `screen_view` (nome della schermata, mai gli argomenti della route),
  `food_added` (`source`: photo/manual/barcode), `ai_request` (`provider`, `kind`, `outcome`) e le
  proprietà `ai_provider`, `theme`, `app_language`, `health_connect`, `reminders_on`. Mai cibi,
  quantità, peso o dati di Health Connect. Le build debug non inviano niente. Informativa:
  [`site/privacy.html`](site/privacy.html).
- **Novità (push)** (`data/push/News.kt`): niente server, si invia dalla console Firebase →
  Messaging → nuova campagna di notifica, con destinazione **Argomento**. Ogni installazione è
  iscritta a `news` (tutti) e a `news-it` o `news-en` (lingua dell'app, aggiornata a ogni avvio);
  le build debug anche a `news-debug`, per provare un messaggio sul proprio telefono prima di
  mandarlo a tutti. Con l'app chiusa la notifica la mostra il sistema (canale `news`), con l'app
  aperta `NewsMessagingService`; il tocco apre Oggi. Si spegne in Impostazioni → Promemoria e
  novità. Serve Google Play Services sul telefono.

## Stack

Kotlin, Jetpack Compose (Material 3), MVVM con DI manuale, Room, DataStore,
Navigation Compose, OkHttp + kotlinx.serialization (REST Gemini, OpenAI, Anthropic, Open Food Facts), Coil,
Google Code Scanner (ML Kit), Health Connect, Firebase (Analytics, Crashlytics, Cloud Messaging). minSdk 26, targetSdk 37.

## Dati CREA

`app/src/main/assets/crea_foods.tsv` è generato da `tools/crea/build_crea_table.py`, che legge
le pagine del portale [alimentinutrizione.it](https://www.alimentinutrizione.it/tabelle-nutrizionali)
(una richiesta al secondo, con cache in `tools/crea/.cache/`). Per rigenerarlo:

```bash
python3 tools/crea/build_crea_table.py
```

Colonne: kcal, proteine, grassi, carboidrati disponibili, fibra totale, zuccheri solubili e
sale (dal sodio in mg × 2,5 / 1000). Con la cache già scaricata la rigenerazione non fa richieste.

Fonte dei dati: CREA – Centro di ricerca Alimenti e Nutrizione, *Tabelle di composizione
degli alimenti*. Le condizioni d'uso del sito chiedono di citare la fonte.

## Rilasci

Ogni tag di versione (`v1.0.0`, `v1.1.0`...) avvia il workflow
[`.github/workflows/release.yml`](.github/workflows/release.yml): compila l'APK firmato e lo
pubblica come file scaricabile nella **Release** del tag, nella pagina *Releases* della repo.

Per una nuova versione:

1. aggiorna `versionName` (e aumenta `versionCode`) in `app/build.gradle.kts`: il workflow si
   ferma se il tag non corrisponde a `versionName`;
2. scrivi il changelog: un `string-array` `changelog_x_y_z` in `res/values/strings.xml`
   (italiano) e in `res/values-en/strings.xml` (inglese), più la voce in cima a
   `Changelog.entries` (`data/update/Changelog.kt`). `ChangelogTest` fallisce se manca la versione
   corrente;
3. `git tag v1.1.0 && git push origin v1.1.0`.

Le note della Release su GitHub sono lo stesso testo inglese che l'app mostra dopo
l'aggiornamento: le scrive `.github/scripts/release_notes.py` dall'array di `values-en`, e il
workflow si ferma se l'array manca. Per vederle in anteprima:
`python3 .github/scripts/release_notes.py 1.3.0`.

**Firma**: gli APK sono firmati con la chiave `~/.android/photocal-release.jks` (fuori dalla
repo). In locale la password è in `local.properties`; su GitHub chiave e password sono nei
secret `PHOTOCAL_KEYSTORE_BASE64`, `PHOTOCAL_KEYSTORE_PASSWORD` e `PHOTOCAL_KEY_ALIAS`.
**Conserva una copia della chiave e della password**: senza, l'app installata non si può più
aggiornare (bisognerebbe disinstallarla, perdendo i dati). Gli APK di debug hanno un'altra
firma: per passare da debug a release serve disinstallare.

**Firebase**: `app/google-services.json` non è nella repo (`.gitignore`): in locale si scarica
dalla console Firebase (Impostazioni progetto → app Android), su GitHub è nel secret
`PHOTOCAL_GOOGLE_SERVICES_JSON` (`gh secret set PHOTOCAL_GOOGLE_SERVICES_JSON < app/google-services.json`),
che il workflow scrive in `app/` prima della build. Senza il file la build si ferma. Non è un vero
segreto (i valori finiscono comunque nell'APK), ma fuori dalla repo pubblica evita gli avvisi di
secret scanning e i bot che cercano chiavi `AIza`. La sua API key è limitata nella Google Cloud
Console al package con gli SHA-1 delle chiavi release e debug. Le build release caricano su
Crashlytics il mapping di R8, così gli stack trace si leggono in chiaro.

## Sito

La cartella `site/` è pubblicata da Cloudflare Pages su `photocal.emanuelemelini.dev` (il dominio è
in `gradle.properties`, `photocal.share.host`). HTML statico, in italiano e in inglese: la lingua
segue il browser e si cambia con IT/EN (`site.js`, scelta salvata nel browser).

- **`/`, come funziona** (`index.html`): le funzioni dell'app con gli screenshot e il pulsante
  per scaricarla. È anche la pagina di `/d` (regola 200 in `_redirects`): chi apre un link di
  condivisione senza l'app vede in più il riquadro "Ti hanno condiviso una giornata".
- **`/download`** (`download.html`, `download.js`): legge le Release dall'API pubblica di GitHub
  (dal browser, senza token, al massimo 60 richieste l'ora per indirizzo, con 10 minuti di cache
  nella sessione) e mostra l'ultima versione con data, novità e pulsante per l'APK, poi "Mostra
  versioni precedenti", ognuna con le sue novità e il suo APK. Le note sono quelle scritte da
  `release_notes.py` (inglese visibile, italiano nel commento nascosto) e sono lette come in
  `GitHubReleasesClient.parseNotes`. Una versione compare solo quando la sua Release ha l'APK:
  dopo una release la pagina si aggiorna da sola, senza ripubblicare il sito.
- **`/privacy`** (`privacy.html`): l'informativa, aperta dal dialogo del primo avvio e da
  Impostazioni → Privacy (`PRIVACY_POLICY_URL`). Va aggiornata se cambiano i dati inviati a
  Firebase (eventi, proprietà) o i tempi di conservazione impostati nella console.
- **Screenshot** (`img/`, WebP 540 px, ~430 KB in tutto): presi sull'AVD telefono
  `PhotoCalPhone_API34` con la build release e i dati di prova di
  `tools/site/demo_backup.py` (Impostazioni → Dati → Ripristina backup → Sostituisci tutto, solo
  sull'emulatore); quelli della foto del pasto (`photo-*`) vengono da un telefono vero, perché
  serve una risposta vera dell'AI. Quelli della scelta dell'AI (`ai-*`): Impostazioni con la
  lingua dell'app su Italiano o English, titolo "Intelligenza artificiale" subito sotto la barra,
  Claude selezionato e chiave vuota, senza salvare. Senza la barra di stato: `magick in.png -crop 1080x2270+0+130
  +repage out.png`, poi `cwebp -q 78 -m 6 -resize 540 0 -metadata none out.png -o x.webp`.
- **Pubblicazione**: il progetto Pages è collegato alla repo (cartella `site/`), quindi ogni push
  su `master` pubblica il sito; i commit mostrano il controllo "Cloudflare Pages".
- **Sicurezza**: `_headers` mette la CSP (script e stili solo dal sito, chiamate solo verso
  `api.github.com`) e `.well-known/assetlinks.json`, che autorizza l'app ad aprire i link (App
  Links): ha le impronte SHA-256 della chiave di rilascio e della chiave debug di Android Studio.
  Se cambia una chiave, aggiorna il file.

## Link di condivisione

- **Sito**: vedi sopra; `/d` è il percorso dei link.
- **Chiave HMAC**: `photocal.share.key` in `local.properties` e secret GitHub
  `PHOTOCAL_SHARE_KEY`, stesso valore, mai nella repo (il codice è pubblico). Le build firmate
  si fermano se manca. **Conservane una copia**: se cambia, i link già inviati non si aprono più.
  Le build non firmate usano una chiave di sviluppo, i cui link le release rifiutano.
- **Verifica**: `adb shell pm get-app-links it.emanuelemelini.photocal` deve dire `verified`
  per il dominio.
- **Formato**: `SharedCard.kt`, versione 1. Le versioni vecchie ignorano i campi che non
  conoscono ma rifiutano le unità sconosciute: per questo le voci in pezzi viaggiano con i soli
  grammi più due campi nuovi (`pc`, `pl`), e chi ha una versione prima della 1.4.0 vede "25 g".

## Database

Gli schemi di Room sono esportati in `app/schemas/` (versionati). Dalla 1.0.0 l'app è
installata con dati veri: ogni modifica allo schema richiede una migrazione in
`AppDatabase.kt` (v1 → v2 aggiunge il registro del peso, v2 → v3 il contatore dell'acqua,
v3 → v4 nome dei pezzi, fibre, zuccheri e sale delle voci e la tabella degli alimenti salvati).

## Build e installazione

Serve `local.properties` con il percorso dell'Android SDK (Android Studio lo crea da solo):

```
sdk.dir=/Users/<utente>/Library/Android/sdk
```

```bash
./gradlew assembleDebug
./gradlew assembleRelease     # APK firmato, se local.properties contiene la chiave
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
│   ├── ai/               AiService, prompt, schema; client REST gemini/, openai/, anthropic/
│   ├── crea/             tabelle CREA (da assets/crea_foods.tsv)
│   ├── estimate/         stima AI + sostituzione con i valori CREA
│   ├── nutrition/        profilo, formula del fabbisogno, macro
│   ├── openfoodfacts/    client API v3, prodotti
│   ├── http/             utilità OkHttp condivise
│   ├── photo/            file delle foto, ridimensionamento
│   ├── reminders/        promemoria: orari, allarmi, notifiche
│   ├── backup/           backup JSON, ripristino, CSV
│   ├── health/           Health Connect: letture e scritture
│   ├── telemetry/        statistiche e crash (Firebase), solo con il consenso
│   ├── push/             novità: topic Firebase e notifiche
│   ├── FoodRepository.kt
│   └── SavedFoodRepository.kt  alimenti recenti e preferiti
└── ui/
    ├── today/            schermata Oggi
    ├── entry/            inserimento/modifica manuale
    ├── photo/            revisione dell'analisi foto
    ├── barcode/          scansione e prodotto da Open Food Facts
    ├── recent/           alimenti recenti e preferiti
    ├── health/           privacy dei dati di salute
    ├── privacy/          consenso al primo avvio
    ├── history/          storico e grafico
    ├── profile/          profilo e obiettivi
    ├── weight/           registro del peso
    ├── components/       campi del form condivisi (quantità/unità, pasto)
    ├── settings/         impostazioni
    └── theme/
```

// Download page: reads the GitHub Releases (the same ones the app updates from) and shows the
// latest version with its notes, then the older ones. A release is listed only once its APK is
// attached, so the page never points to a file that doesn't exist yet.
(function () {
  const API = "https://api.github.com/repos/EmanueleMelini/PhotoCal/releases?per_page=100";
  // The API allows 60 calls per hour per address without a token: a short cache is enough
  const CACHE_KEY = "photocal.releases";
  const CACHE_MS = 10 * 60 * 1000;

  const TEXT = {
    it: {
      version: "PhotoKCal {0}",
      released: "Pubblicata il {0}",
      download: "Scarica APK ({0})",
      downloadThis: "Scarica la {0} ({1})",
      whatsNew: "Novità",
      firstVersion: "Prima versione pubblicata.",
      noNotes: "Nessuna nota per questa versione.",
      oldWarning: "Le versioni vecchie non hanno le ultime correzioni. Per tornare a una versione precedente bisogna disinstallare l'app, perdendo i dati: prima fai un backup.",
    },
    en: {
      version: "PhotoKCal {0}",
      released: "Released on {0}",
      download: "Download APK ({0})",
      downloadThis: "Download {0} ({1})",
      whatsNew: "What's new",
      firstVersion: "First published version.",
      noNotes: "No notes for this version.",
      oldWarning: "Old versions lack the latest fixes. Going back to an older version means uninstalling the app and losing its data: make a backup first.",
    },
  };

  let releases = null;

  function format(template, ...args) {
    return template.replace(/\{(\d)\}/g, (_, i) => args[Number(i)]);
  }

  function element(tag, props, children) {
    const node = document.createElement(tag);
    Object.assign(node, props || {});
    (children || []).forEach((child) => node.append(child));
    return node;
  }

  /** "v1.4.0" -> [1, 4, 0]; null for tags that aren't a version. */
  function parseVersion(tag) {
    const match = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(tag || "");
    return match ? match.slice(1).map(Number) : null;
  }

  function compareVersions(a, b) {
    for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return b[i] - a[i];
    return 0;
  }

  function bullets(text) {
    return text.split("\n")
      .map((line) => line.trim())
      .filter((line) => line.startsWith("- ") || line.startsWith("* "))
      .map((line) => line.slice(2).trim())
      .filter((line) => line.length > 0);
  }

  /**
   * Same rules as GitHubReleasesClient.parseNotes in the app: the visible bullets are English,
   * the other languages are in hidden "<!-- photocal-notes:it ... -->" blocks.
   */
  function parseNotes(body) {
    const notes = {};
    const hidden = /<!--\s*photocal-notes:([a-z]{2,3})\s([\s\S]*?)-->/g;
    let match;
    while ((match = hidden.exec(body)) !== null) {
      const items = bullets(match[2]);
      if (items.length) notes[match[1]] = items;
    }
    const english = bullets(body.replace(/<!--[\s\S]*?-->/g, ""));
    if (english.length) notes.en = english;
    return notes;
  }

  function toRelease(raw) {
    if (raw.draft || raw.prerelease) return null;
    const version = parseVersion(raw.tag_name);
    const apk = (raw.assets || []).find((asset) => /\.apk$/i.test(asset.name || ""));
    if (!version || !apk || !String(apk.browser_download_url).startsWith("https://")) return null;
    return {
      name: version.join("."),
      version: version,
      date: raw.published_at,
      apkUrl: apk.browser_download_url,
      apkSize: apk.size || 0,
      notes: parseNotes(raw.body || ""),
    };
  }

  function readCache() {
    try {
      const cached = JSON.parse(sessionStorage.getItem(CACHE_KEY));
      if (cached && Date.now() - cached.at < CACHE_MS && Array.isArray(cached.releases)) return cached.releases;
    } catch (e) {
      // No storage or a damaged value: read GitHub again
    }
    return null;
  }

  function writeCache(list) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), releases: list }));
    } catch (e) {
      // Not cached: the next visit reads GitHub again
    }
  }

  async function load() {
    const cached = readCache();
    if (cached) return cached;
    const response = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
    if (!response.ok) throw new Error("GitHub " + response.status);
    const list = (await response.json()).map(toRelease).filter(Boolean);
    list.sort((a, b) => compareVersions(a.version, b.version));
    writeCache(list);
    return list;
  }

  function sizeText(bytes, lang) {
    const mb = bytes / (1024 * 1024);
    return new Intl.NumberFormat(lang, { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(mb) + " MB";
  }

  function dateText(iso, lang) {
    const date = new Date(iso);
    if (isNaN(date)) return "";
    return new Intl.DateTimeFormat(lang === "it" ? "it-IT" : "en-GB", { day: "numeric", month: "long", year: "numeric" }).format(date);
  }

  /** Notes in the page language, else English; the oldest version has none. */
  function notesList(release, lang, isOldest) {
    const items = release.notes[lang] || release.notes.en;
    if (!items) return element("p", { className: "muted", textContent: isOldest ? TEXT[lang].firstVersion : TEXT[lang].noNotes });
    return element("ul", {}, items.map((item) => element("li", { textContent: item })));
  }

  function render() {
    if (!releases) return;
    const lang = window.photocalLang();
    const text = TEXT[lang];
    const oldest = releases[releases.length - 1];
    const [latest, ...older] = releases;

    const latestCard = document.getElementById("latest");
    latestCard.replaceChildren(
      element("h2", { textContent: format(text.version, latest.name) }),
      element("p", { className: "muted", textContent: format(text.released, dateText(latest.date, lang)) }),
      element("a", { className: "button", href: latest.apkUrl, textContent: format(text.download, sizeText(latest.apkSize, lang)) }),
      element("h3", { textContent: text.whatsNew }),
      notesList(latest, lang, latest === oldest),
    );

    const olderCard = document.getElementById("older");
    olderCard.hidden = older.length === 0;
    document.getElementById("older-list").replaceChildren(
      element("p", { className: "muted", textContent: text.oldWarning }),
      ...older.map((release) => element("details", {}, [
        element("summary", {}, [
          release.name + " ",
          element("span", { className: "version-date", textContent: "· " + dateText(release.date, lang) }),
        ]),
        notesList(release, lang, release === oldest),
        element("a", {
          className: "button small",
          href: release.apkUrl,
          textContent: format(text.downloadThis, release.name, sizeText(release.apkSize, lang)),
        }),
      ])),
    );
  }

  document.addEventListener("photocal:lang", render);

  load()
    .then((list) => {
      if (!list.length) throw new Error("No release with an APK");
      releases = list;
      render();
    })
    .catch(() => {
      document.getElementById("loading").hidden = true;
      document.getElementById("fallback").hidden = false;
    });
})();

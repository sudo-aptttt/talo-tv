// TV home page, tvOS style: Keep watching on top, a grid of apps below, one screen, no scrolling.
// Runs inside the launcher's WebView (window.TVOS is the native side) or in any browser for
// development: ?mock=1 uses mock.js for everything, ?preview=1 uses mock apps with the real bridge.
(function () {
  "use strict";

  var TV = window.TVOS || null;
  var Q = location.search;
  var MOCK = /[?&]mock\b/.test(Q);
  var PREVIEW = /[?&]preview\b/.test(Q);
  var API = decodeURIComponent((Q.match(/[?&]api=([^&]+)/) || [])[1] || "");
  // which TV this page is on (the launcher adds ?tv=<id> on any TV that is not the default); none = living room
  var TV_ID = (Q.match(/[?&]tv=([a-z-]+)/) || [])[1] || "";
  // The launcher's saved copy of this page, opened when the computer is off (or when there is no
  // computer at all): scenes, clock, weather straight from Open-Meteo and the apps in the saved
  // order; nothing that needs the bridge.
  var OFFLINE = /[?&]offline=1/.test(Q);
  // No computer at all (option A): the launcher's built-in copy is the home, so there is no
  // "computer is off" note to show.
  var ALONE = OFFLINE && /[?&]alone=1/.test(Q);
  if (OFFLINE) document.documentElement.classList.add("offline");
  var CACHE_KEY = "tvhome.cache.v2";
  // Every word on screen comes from i18n.js, in the language picked in Home settings.
  var t = window.I18N ? window.I18N.t : function (k) { return k; };

  // Settings from the bridge's config.json (served as config.js, and saved with the offline copy),
  // read in readConfig() once the page starts (mock.js fills them in for previews). See docs/CONFIG.md.
  var CFG = {}, HOME = {}, TVCFG = {}, QUICK = [];
  var WXCFG = { lat: -33.8688, lon: 151.2093, place: "Sydney", timezone: "Australia/Sydney" };
  var QUICK_ALL = ["watchlist", "live", "sleep", "picture", "input"];
  // The look: ?theme= for a preview, then Home settings (or the Look button) on this TV, then home.look.
  var THEME = "night", NIGHT = true, CLOCK24 = false;
  // Home settings made on this TV with the gear tile (see "home settings" below). They win over
  // config.js. ST is the saved object; the rest are read from it in readConfig().
  var ST = {}, WX_SHOW = true, WX_F = false, WX_EVERY = 30, HIDDEN_TV = [];

  // What this TV keeps for itself (app order, people order, new apps, the look). Written to the
  // page's storage and to the launcher (TVOS.keep), so the live page and the saved copy share it.
  var PFX = MOCK || PREVIEW ? "mock:" : "";
  function kept(key) {
    var v = null;
    try { if (TV && TV.kept) v = TV.kept(PFX + key); } catch (e) {}
    if (v == null) try { v = localStorage.getItem(PFX + key); } catch (e) {}
    try { return v == null ? null : JSON.parse(v); } catch (e) { return null; }
  }
  function keep(key, value) {
    var j = JSON.stringify(value);
    try { localStorage.setItem(PFX + key, j); } catch (e) {}
    try { if (TV && TV.keep) TV.keep(PFX + key, j); } catch (e) {}
  }

  // Default order: YouTube, then Netflix, then the rest the way a household reaches for them.
  var APP_ORDER = [
    "com.google.android.youtube.tv", "com.netflix.ninja", "au.net.abc.iview", "com.disney.disneyplus",
    "au.com.stan.and", "au.com.streamotion.ares.tv", "com.amazon.amazonvideo.livingroom",
    "com.sbs.ondemand.tv", "com.apple.atve.androidtv.appletv", "com.cbs.ca", "au.com.ninenow.ctv",
    "com.swm.live", "au.com.tenplay", "au.com.kayosports.tv", "au.com.foxtel.atv", "com.wbd.stream",
    "com.spotify.tv.android", "com.google.android.youtube.tvmusic"
  ];
  // The TV's own tools go last, in this order, just before the screens.
  var APP_LAST = ["com.mediatek.wwtv.tvcenter", "com.android.vending", "com.android.tv.settings"];
  // Kept behind the "More apps" tile on every TV (apps most people rarely open).
  var TUCKED = [];
  // The same tools on other makers' TVs, by name.
  var TUCKED_NAMES = /(?!.*)/;
  // The Play Store, under the name most people know it by (in the chosen language).
  var RENAME = { "com.android.vending": "app_store" };
  // Never shown: Talo TV itself
  var HIDDEN = [
    "com.talotv.launcher", "com.talotv.launcher.debug"
  ];
  var GRID_COLS = 5, GRID_MAX = 50;
  var DOCK_APPS = 4;   // Night: apps in the dock before Settings and More apps (home.dock_size)
  var SETTINGS_PKG = "com.android.tv.settings";
  // The Play Store: always the first tile of More apps, never in the dock's order.
  var STORE_PKG = "com.android.vending";
  var HOLD_MS = 600;    // how long OK is held to pick something up

  var ICONS = {
    screen: '<svg viewBox="0 0 24 24"><rect x="2.5" y="3.5" width="19" height="13" rx="2"/><path d="M9 20.5h6M12 16.5v4"/></svg>',
    moon: '<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/></svg>',
    picture: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 3.5v17a8.5 8.5 0 0 0 0-17z" class="fill"/></svg>',
    more: '<svg viewBox="0 0 24 24"><circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/></svg>',
    gear: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3.2"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
    look: '<svg viewBox="0 0 24 24"><rect x="2.5" y="4.5" width="19" height="15" rx="3"/><path d="M2.5 16l5.5-5.5 4.5 4.5 3-3 6 6"/><circle cx="16.5" cy="8.8" r="1.6"/></svg>',
    input: '<svg viewBox="0 0 24 24"><rect x="2.5" y="4" width="19" height="13" rx="2.5"/><path d="M8 21h8M12 7.5v6M9.5 11l2.5 2.5 2.5-2.5"/></svg>',
    store: '<svg viewBox="0 0 24 24"><path d="M5 8h14l-1.2 11.2a2 2 0 0 1-2 1.8H8.2a2 2 0 0 1-2-1.8z"/><path d="M9 10.5V7a3 3 0 0 1 6 0v3.5"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>',
    updown: '<svg viewBox="0 0 24 24"><path d="M8 9.5l4-4 4 4M8 14.5l4 4 4-4"/></svg>',
    sliders: '<svg viewBox="0 0 24 24"><path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/></svg>',
    chevron: '<svg viewBox="0 0 24 24"><path d="M9.5 5.5 16 12l-6.5 6.5"/></svg>',
    // the settings screen's sections
    globe: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.6 2.5 3.8 5.3 3.8 8.5s-1.2 6-3.8 8.5c-2.6-2.5-3.8-5.3-3.8-8.5s1.2-6 3.8-8.5z"/></svg>',
    pin: '<svg viewBox="0 0 24 24"><path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.4"/></svg>',
    cloud: '<svg viewBox="0 0 24 24"><path d="M17.5 19H8a4 4 0 1 1 .9-7.9A5.5 5.5 0 0 1 19.4 12a3.5 3.5 0 0 1-1.9 7z"/></svg>',
    apps: '<svg viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/></svg>',
    person: '<svg viewBox="0 0 24 24"><circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20.5c.8-3.9 3.7-6 7.5-6s6.7 2.1 7.5 6"/></svg>',
    bolt: '<svg viewBox="0 0 24 24"><path d="M13 2.8 5.5 13.2h5.8L10.5 21l8-10.6h-5.9z"/></svg>',
    star: '<svg viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
    bookmark: '<svg viewBox="0 0 24 24"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>',
    lock: '<svg viewBox="0 0 24 24"><rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/><path d="M7.5 10.5V7a4.5 4.5 0 0 1 9 0v3.5"/></svg>',
    shield: '<svg viewBox="0 0 24 24"><path d="M12 2.5s8 2.5 8 8.5c0 5.5-5 9.5-8 10.5-3-1-8-5-8-10.5 0-6 8-8.5 8-8.5z"/></svg>',
    info: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.6v.2"/></svg>'
  };
  // weather glyphs for the date line, by Open-Meteo's WMO code
  var WX = {
    sun: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/></svg>',
    moon: '<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/></svg>',
    cloud: '<svg viewBox="0 0 24 24"><path d="M17.5 19H8a4 4 0 1 1 .9-7.9A5.5 5.5 0 0 1 19.4 12a3.5 3.5 0 0 1-1.9 7z"/></svg>',
    rain: '<svg viewBox="0 0 24 24"><path d="M17.5 15H8a4 4 0 1 1 .9-7.9A5.5 5.5 0 0 1 19.4 8a3.5 3.5 0 0 1-1.9 7z"/><path d="M9 18l-1 2.5M13 18l-1 2.5M17 18l-1 2.5"/></svg>',
    storm: '<svg viewBox="0 0 24 24"><path d="M17.5 15H8a4 4 0 1 1 .9-7.9A5.5 5.5 0 0 1 19.4 8a3.5 3.5 0 0 1-1.9 7z"/><path d="M12.5 15.5 10.5 19h3l-2 3.5"/></svg>'
  };
  function wxIcon(w) {
    var c = w.code;
    if (c >= 95) return WX.storm;
    if (c >= 51) return WX.rain;
    if (c >= 3 || c === 45 || c === 48) return WX.cloud;
    return w.is_day ? WX.sun : WX.moon;
  }

  var $ = function (id) { return document.getElementById(id); };
  var data = { shows: [], showsBy: {}, apps: [], screens: [], bridge: null };
  // Whose Keep watching is showing. Remembered on this TV. Any number of people: none or one shows
  // no switcher, two the pill switch, three or more a round head icon that opens a list.
  // With nobody set up, everything is shown ("both" to the bridge).
  var PEOPLE = [], NOBODY = false, who = null, pi = 0;
  try { who = localStorage.getItem("tvhome.who"); } catch (e) {}
  var zone = "grid", s = 0, g = 0;   // focus: zone, shelf index, grid index
  var shelfEls = [], gridEls = [], gridItems = [];
  var touched = false, expanded = false;
  var retryTimer = null, retryMs = 2500, statusTimer = null;
  var pmi = 0, plift = false;        // the people list: focused row, and whether that row is picked up
  var lift = null;                   // the dock app picked up to move: {pkg}
  var newApps = [];                  // apps installed since this TV first saw its apps, until opened once

  function setPeople(list) {
    PEOPLE = (list || []).filter(function (p) { return p && p.id && p.name; });
    NOBODY = !PEOPLE.length;
    if (NOBODY) PEOPLE = [{ id: "both", name: t("everyone") }];
    PEOPLE.forEach(function (p) { if (!data.showsBy[p.id]) data.showsBy[p.id] = []; });
    if (!PEOPLE.some(function (p) { return p.id === who; })) who = PEOPLE[0].id;
  }
  // An order made on this TV with no bridge to keep it (or before the bridge heard about it).
  function localPeopleOrder(list, always) {
    var lo = kept("tvhome.people.order");
    if (!lo || !lo.ids || !(always || lo.pending)) return list;
    var by = {};
    list.forEach(function (p) { by[p.id] = p; });
    var out = lo.ids.filter(function (id) { return by[id]; }).map(function (id) { return by[id]; });
    return out.concat(list.filter(function (p) { return lo.ids.indexOf(p.id) < 0; }));
  }

  // Which wins, lowest first: the defaults here, config.js (config.json), then Home settings made on
  // this TV (kept by the bridge too when there is one), then ?theme= for a preview. docs/CONFIG.md.
  function readConfig() {
    CFG = window.TALO_CONFIG || window.COUCH_CONFIG || {};
    HOME = CFG.home || {};
    ST = loadSettings();
    var sysLang = window.I18N && window.I18N.detect ? window.I18N.detect() : "en";
    if (window.I18N) window.I18N.set(ST.lang || sysLang);
    if (CFG.weather) WXCFG = CFG.weather;
    var pl = ST.place;
    if (pl && isFinite(pl.lat) && isFinite(pl.lon)) WXCFG = { lat: pl.lat, lon: pl.lon, place: pl.name || "", timezone: pl.timezone || "auto" };
    WX_SHOW = ST.wx_show !== false;
    WX_F = ST.wx_unit === "f";
    WX_EVERY = [15, 30, 60].indexOf(ST.wx_every) >= 0 ? ST.wx_every : 30;
    var mine = TV_ID || CFG.default_tv;
    TVCFG = (CFG.tvs || []).filter(function (t) { return t.id === mine; })[0] || (CFG.tvs || [])[0] || {};
    // no "quick" from an older bridge: the sleep timer, and the picture switch where the TV has one
    QUICK = (Array.isArray(ST.quick) ? ST.quick : TVCFG.quick || ["watchlist", "live", "sleep", "picture"])
      .filter(function (q, i, a) { return QUICK_ALL.indexOf(q) >= 0 && a.indexOf(q) === i; });
    THEME = "night";
    NIGHT = true;
    document.documentElement.classList.add("theme-night");
    GRID_COLS = 5;
    DOCK_APPS = 4;
    CLOCK24 = (ST.clock || HOME.clock || "24h") === "24h";
    (Array.isArray(HOME.hide_packages) ? HOME.hide_packages : []).forEach(function (p) {
      if (typeof p === "string" && HIDDEN.indexOf(p) < 0) HIDDEN.push(p);
    });
    HIDDEN_TV = (Array.isArray(ST.hidden_apps) ? ST.hidden_apps : []).filter(function (p) { return typeof p === "string"; });
    var people = (CFG && Array.isArray(CFG.people)) ? CFG.people : [];
    // people added, renamed or removed in Home settings with no bridge to keep them
    var lst = kept("tvhome.people.list");
    if (lst && Array.isArray(lst.people) && (lst.pending || ALONE)) people = lst.people;
    setPeople(localPeopleOrder(people, OFFLINE));
    newApps = kept("tvhome.apps.new") || [];
  }

  // ---------------- helpers ----------------
  function esc(v) { return String(v == null ? "" : v).replace(/[&<'">]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[c]; }); }
  // The saved copy has its own storage, so the app order is also kept by the launcher (TVOS.keep).
  function load() {
    if (MOCK || PREVIEW) return null;
    try {
      if (OFFLINE) return TV && TV.kept ? { order: (JSON.parse(TV.kept(CACHE_KEY) || "null") || {}).order || null } : null;
      return JSON.parse(localStorage.getItem(CACHE_KEY) || "null");
    } catch (e) { return null; }
  }
  function save() {
    if (MOCK || PREVIEW || OFFLINE) return;
    var j = JSON.stringify({ showsBy: data.showsBy, screens: data.screens, order: data.order });
    try { localStorage.setItem(CACHE_KEY, j); } catch (e) {}
    try { if (TV && TV.keep) TV.keep(CACHE_KEY, j); } catch (e) {}
  }
  function mark(name) { try { performance.mark(name); } catch (e) {} }

  function api(method, path, body, timeoutMs) {
    if (TV_ID) {
      if (method === "GET") path += (path.indexOf("?") < 0 ? "?" : "&") + "tv=" + TV_ID;
      else body = Object.assign({ tv: TV_ID }, body || {});
    }
    if (MOCK && window.MOCK_API) return window.MOCK_API(method, path, body);
    // previews on a computer read the real bridge but never change it
    if (PREVIEW && method !== "GET") return Promise.resolve({ ok: true, note: "" });
    return new Promise(function (resolve, reject) {
      var x = new XMLHttpRequest();
      x.open(method, API + path);
      x.timeout = timeoutMs || 6000;
      x.setRequestHeader("Content-Type", "application/json");
      x.onload = function () {
        var j = null;
        try { j = JSON.parse(x.responseText); } catch (e) {}
        if (x.status >= 200 && x.status < 300 && j && j.ok !== false) resolve(j);
        else reject(new Error((j && j.error) || t("bridge_no", { status: x.status })));
      };
      x.onerror = function () { reject(new Error(t("bridge_down"))); };
      x.ontimeout = function () { reject(new Error(t("bridge_slow"))); };
      x.send(body ? JSON.stringify(body) : null);
    });
  }

  function status(text, sticky) {
    clearTimeout(statusTimer);
    $("status").textContent = text || "";
    if (text && !sticky) statusTimer = setTimeout(function () { status(data.bridge === false && !ALONE ? t("computer_quiet") : "", true); }, 6000);
  }

  function iconUrl(pkg) {
    if (!pkg) return null;
    if (TV) return TV.iconUrl ? TV.iconUrl(pkg) : "http://tvos.invalid/icon/" + pkg;   // Samsung: samsung/app/tizen.js
    return window.MOCK_ICON ? window.MOCK_ICON(pkg) : null;
  }

  // ---------------- The clock and date ----------------
  function tempText(c) { return WX_F ? Math.round(c * 9 / 5 + 32) + "°F" : c + "°"; }
  function wxText(w) { var k = WMO_KEY[w.code]; return k ? t(k) : (w.text || ""); }

  function clock() {
    var d = new Date(), h = d.getHours(), m = d.getMinutes();
    var mm = (m < 10 ? "0" : "") + m;
    var hm = CLOCK24 ? (h < 10 ? "0" : "") + h + ":" + mm : (h % 12 || 12) + ":" + mm;
    $("time").innerHTML = hm + (CLOCK24 ? "" : "<small>" + esc(h < 12 ? t("am") : t("pm")) + "</small>");
    var loc = window.I18N ? window.I18N.locale() : "en-AU";
    var day = d.toLocaleDateString(loc, { weekday: "long", day: "numeric", month: "long" }).replace(",", "");
    day = day.charAt(0).toUpperCase() + day.slice(1);
    var w = WX_SHOW && data.weather && data.weather.temp != null ? data.weather : null;
    $("date").innerHTML = '<span class="dline">' + esc(day) + (w ? '<span class="wx">' + wxIcon(w) + esc(tempText(w.temp)) +
      '<span class="ww"> ' + esc(wxText(w)) + "</span></span>" : "") + "</span>";
    fitDate();
    document.body.classList.toggle("night", h >= 21 || h < 6);
    glassClock(hm);
    if (quickReady) renderQuick();
  }

  // ---------------- Places & Weather-driven Immersive Scenes ----------------
  var PLACES = [
    {
      id: "alpine-mountain",
      name: "Montagne alpine",
      folder: "alpine-mountain",
      images: {
        sun: "sun.jpg",
        golden: "golden-hour.jpg",
        cloudy: "cloudy.jpg",
        fog: "fog.jpg",
        rain: "rain.jpg",
        storm: "storm.jpg",
        night_storm: "night-storm.jpg",
        snow: "snow.jpg",
        night: "night.jpg",
        halloween: "hallowen.jpg",
        christmas: "christmas.jpg"
      }
    }
  ];

  function currentPlaceId() {
    return ST.scene || "alpine-mountain";
  }

  function currentPlace() {
    var id = currentPlaceId();
    return PLACES.filter(function (p) { return p.id === id; })[0] || PLACES[0];
  }

  function minutesFromIso(iso) {
    if (!iso || typeof iso !== "string" || iso.indexOf("T") < 0) return null;
    var parts = iso.split("T")[1].split(":");
    return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
  }

  function weatherImageForPlace(place, wx) {
    if (!place || !place.images) return "sun.jpg";
    var imgs = place.images;

    // 1. Determine local time & month of the location via Open-Meteo
    var month = -1, hour = -1, nowM = null;
    if (wx && wx.time && wx.time.indexOf("T") >= 0) {
      var dateParts = wx.time.split("T")[0].split("-");
      month = parseInt(dateParts[1], 10);   // 1 to 12
      var timeParts = wx.time.split("T")[1].split(":");
      hour = parseInt(timeParts[0], 10);
      nowM = hour * 60 + parseInt(timeParts[1], 10);
    } else {
      var d = new Date();
      month = d.getMonth() + 1;             // 1 to 12
      hour = d.getHours();
      nowM = hour * 60 + d.getMinutes();
    }

    // 2. Golden Hour check based on Open-Meteo's exact sunrise & sunset for this place
    var isGolden = false;
    if (wx && wx.sunrise && wx.sunset && nowM !== null) {
      var riseM = minutesFromIso(wx.sunrise);
      var setM = minutesFromIso(wx.sunset);
      // ±45 minutes around sunrise or sunset
      if (riseM !== null && Math.abs(nowM - riseM) <= 45) {
        isGolden = true;
      } else if (setM !== null && Math.abs(nowM - setM) <= 45) {
        isGolden = true;
      }
    } else if (hour === 6 || hour === 7 || hour === 18 || hour === 19) {
      isGolden = true;
    }

    // 3. Day / Night check from Open-Meteo (solar position at this place)
    var isDay = wx && wx.is_day != null ? !!wx.is_day : (hour >= 7 && hour < 20);

    // 4. Nighttime: event mode takes over at night (Halloween in October, Christmas in December)
    if (!isDay) {
      var eventsEnabled = ST.season_events !== false;
      if (eventsEnabled) {
        if (imgs.halloween && month === 10) return imgs.halloween;
        if (imgs.christmas && month === 12) return imgs.christmas;
      }
    }

    // 5. Weather conditions from Open-Meteo
    var code = wx && wx.code != null ? wx.code : null;

    if (code != null) {
      // Thunderstorm
      if (code >= 95) {
        if (!isDay && imgs.night_storm) return imgs.night_storm;
        if (imgs.storm) return imgs.storm;
      }
      // Snow
      if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
        if (imgs.snow) return imgs.snow;
      }
      // Rain / Drizzle / Showers
      if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
        if (imgs.rain) return imgs.rain;
      }
      // Fog / Mist
      if (code === 45 || code === 48) {
        if (imgs.fog) return imgs.fog;
      }
      // Overcast / Cloudy
      if (code === 3 && isDay) {
        if (imgs.cloudy) return imgs.cloudy;
      }
    }

    // 6. If golden hour (and daytime / dusk / dawn):
    if (isGolden && isDay && imgs.golden) {
      return imgs.golden;
    }

    // 7. Clear night (when not in event mode):
    if (!isDay) {
      if (imgs.night) return imgs.night;
    }

    // 8. Sunny daylight default
    return imgs.sun || Object.values(imgs)[0];
  }

  var sceneEls = [], activeSceneEl = 0, currentBgPath = "", glassShown = "";

  function buildScenes() {
    var box = $("scenes");
    box.innerHTML = "";
    sceneEls = [0, 1].map(function () {
      var el = document.createElement("div");
      el.className = "scene s-alpine-mountain";
      el.innerHTML = '<img class="sbg" alt=""><div class="shade"></div><div class="gclock"></div>';
      box.appendChild(el);
      return el;
    });
    activeSceneEl = 0;
    updateSceneBackground(true);
  }

  function drawClocks(el, place, imgSrc, hm) {
    if (!hm || !el) return;
    var cs = el.querySelectorAll(".gclock");
    for (var k = 0; k < cs.length; k++) {
      var c = cs[k];
      c.innerHTML = '<span class="fill">' + hm + '</span><span class="sheen">' + hm + '</span><span class="rim">' + hm + "</span>";
      var f = c.firstChild;
      f.style.backgroundImage = 'url("' + imgSrc + '")';
      f.style.backgroundPosition = -(c.offsetLeft + f.offsetLeft) + "px " + -(c.offsetTop + f.offsetTop) + "px";
    }
  }

  function glassClock(hm) {
    if (hm === glassShown && sceneEls[activeSceneEl]) return;
    glassShown = hm;
    if (sceneEls.length && sceneEls[activeSceneEl]) {
      var place = currentPlace();
      drawClocks(sceneEls[activeSceneEl], place, currentBgPath, hm);
    }
  }

  function updateSceneBackground(force) {
    var place = currentPlace();
    var imgName = weatherImageForPlace(place, data.weather);
    var imgPath = "backgrounds/" + place.folder + "/" + imgName;

    if (!force && imgPath === currentBgPath) {
      if (sceneEls.length && glassShown) {
        drawClocks(sceneEls[activeSceneEl], place, imgPath, glassShown);
      }
      return;
    }
    currentBgPath = imgPath;

    if (!sceneEls.length) return;

    var nextIdx = 1 - activeSceneEl;
    var nextEl = sceneEls[nextIdx];
    var prevEl = sceneEls[activeSceneEl];

    nextEl.className = "scene s-" + place.id + " ready";
    var img = nextEl.querySelector(".sbg");

    var applyTransition = function () {
      nextEl.classList.add("on");
      if (prevEl && prevEl !== nextEl) prevEl.classList.remove("on");
      activeSceneEl = nextIdx;
      if (glassShown) {
        drawClocks(nextEl, place, imgPath, glassShown);
      }
    };

    img.onload = applyTransition;
    img.src = imgPath;
    if (img.complete) {
      applyTransition();
    }

    var root = document.documentElement;
    root.className = root.className.replace(/\bscene-\w+/g, "").replace(/\s+/g, " ").trim() + " scene-" + place.id;
    fitDate();
  }

  function fitDate() {
    var d = $("date"), line = d.firstChild;
    if (!line || !line.getBoundingClientRect) return;
    d.style.left = ""; d.style.right = "";
    d.classList.remove("tight", "tighter");
    var b = line.getBoundingClientRect(), rem = innerWidth / 120;
    var near = function (r) { return r && r.width > 0 && r.bottom > b.top && r.top < b.bottom; };
    var q = $("quick").classList.contains("none") ? null : $("qbtns").getBoundingClientRect();
    var p = $("people").getBoundingClientRect();
    var hitQ = near(q) && b.right > q.left - rem, hitP = near(p) && b.left < p.right + rem;
    if (!hitQ && !hitP) return;
    if (hitQ) d.style.right = (innerWidth - q.left + 1.5 * rem) + "px";
    if (hitP) d.style.left = (p.right + 1.5 * rem) + "px";
    if (d.scrollWidth > d.clientWidth + 1) d.classList.add("tight");
    if (d.scrollWidth > d.clientWidth + 1) d.classList.add("tighter");
  }

  var lastWxFetch = 0;
  function refreshWeather(force) {
    if (!WX_SHOW) return Promise.resolve();
    var now = Date.now();
    if (!force && now - lastWxFetch < 10 * 60000 && data.weather && data.weather.temp != null) {
      return Promise.resolve();
    }
    lastWxFetch = now;
    var direct = OFFLINE || !!(ST.place && isFinite(ST.place.lat));
    return (direct ? weatherDirect() : api("GET", "/api/weather")).then(function (j) {
      data.weather = j;
      clock();
      updateSceneBackground();
    }, function () {
      updateSceneBackground();
    });
  }

  // Open-Meteo (free, no key), the same call the bridge makes. The words come from i18n.js.
  var WMO_KEY = { 0: "wx_clear", 1: "wx_mostly_clear", 2: "wx_partly", 3: "wx_cloudy", 45: "wx_fog", 48: "wx_fog",
    51: "wx_drizzle", 53: "wx_drizzle", 55: "wx_drizzle", 56: "wx_drizzle", 57: "wx_drizzle", 61: "wx_light_rain",
    63: "wx_rain", 65: "wx_heavy_rain", 66: "wx_rain", 67: "wx_rain", 80: "wx_showers", 81: "wx_showers",
    82: "wx_heavy_showers", 95: "wx_storms", 96: "wx_storms", 99: "wx_storms" };
  function weatherDirect() {
    var url = "https://api.open-meteo.com/v1/forecast?latitude=" + WXCFG.lat + "&longitude=" + WXCFG.lon +
      "&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=" + encodeURIComponent(WXCFG.timezone || "auto") + "&forecast_days=1";
    return fetch(url).then(function (r) { return r.json(); }).then(function (j) {
      var c = j.current || {}, d = j.daily || {}, code = Math.round(c.weather_code != null ? c.weather_code : 0);
      var sunrise = d.sunrise && d.sunrise[0] ? d.sunrise[0] : null;
      var sunset = d.sunset && d.sunset[0] ? d.sunset[0] : null;
      return {
        temp: Math.round(c.temperature_2m != null ? c.temperature_2m : 20),
        code: code,
        text: "",
        is_day: c.is_day != null ? !!c.is_day : true,
        time: c.time || null,
        sunrise: sunrise,
        sunset: sunset,
        timezone: j.timezone || WXCFG.timezone
      };
    });
  }

  function hero(it) {
    var el = $("hero");
    if (!it) { el.classList.remove("on"); return; }
    $("hero-title").textContent = it.title;
    $("hero-sub").textContent = subtitle(it);
    el.classList.add("on");
  }

  // ---------------- quick buttons: from tvs[].quick in config.json, in that order ----------------
  // sleep: the sleep timer. picture: flip between the TV's two picture modes. look: Night scenes or
  // Classic. input: the TV's input list. A button that cannot work right now is left out (no bridge
  // for the sleep timer, a TV with no picture setting, an input list nobody can open).
  var SLEEP_CHOICES = [15, 30, 60, 90, 0];   // 0 = off
  // picture mode only exists where the bridge says this TV supports it
  var sleepEnds = null, picMode = null, picCanSet = false, picShown = false, picBusy = false, picModes = null;
  var qi = 0, menuI = 0, noteTimer = null, quickReady = false, quickEls = [];

  function note(text) {
    clearTimeout(noteTimer);
    $("quick-note").textContent = text || "";
    if (text) noteTimer = setTimeout(function () { $("quick-note").textContent = ""; }, 6000);
  }
  function minutesLeft() {
    if (!sleepEnds) return 0;
    var m = Math.ceil((sleepEnds - Date.now()) / 60000);
    if (m <= 0) { sleepEnds = null; return 0; }
    return m;
  }
  function nativeInputs() {
    try { return !!(TV && TV.canOpenInputs && TV.canOpenInputs()); } catch (e) { return false; }
  }
  function quickVisible() {
    return [];
  }
  function hasQuick() { return false; }
  function picLabel() {
    if (picBusy) return t("switching");
    var names = picModes || (TVCFG.picture_modes || []).map(function (n) { return { id: null, name: n }; });
    var hit = names.filter(function (m) { return m.id === picMode; })[0];
    if (hit) return hit.name;
    if (picMode && picMode !== "other") return picMode.charAt(0).toUpperCase() + picMode.slice(1).replace(/-/g, " ");
    return t("picture_mode");
  }
  function renderQuick() {
    quickReady = true;
    var q = $("quick");
    if (q) q.classList.add("none");
    quickEls = [];
    fitDate();
  }
  function renderMenu() {
    var menu = $("sleep-menu");
    menu.innerHTML = SLEEP_CHOICES.map(function (c, i) {
      return '<span class="opt' + (i === menuI ? " focus" : "") + '">' + esc(c ? t("n_min", { n: c }) : t("timer_off")) + "</span>";
    }).join("");
  }
  function openMenu() {
    var m = minutesLeft();
    menuI = m ? SLEEP_CHOICES.length - 1 : 1;   // a timer is running: start on Off; otherwise 30 min
    zone = "menu";
    $("sleep-menu").classList.add("on");
    renderMenu();
  }
  function closeMenu() {
    $("sleep-menu").classList.remove("on");
    zone = "quick";
  }
  function setSleep(minutes) {
    closeMenu();
    sleepEnds = minutes ? Date.now() + minutes * 60000 : null;   // show it straight away
    renderQuick();
    api("POST", "/api/sleep", { minutes: minutes })
      .then(function (j) { if (j.ends !== undefined) sleepEnds = j.ends ? Date.parse(j.ends) : null; renderQuick(); })
      .catch(function (e) { note(e.message); refreshQuick(); });
  }
  function togglePicture() {
    if (!picCanSet) { note(t("pic_not_ready")); return; }
    // A picture switch can take several seconds. Extra presses meanwhile would switch it straight back,
    // so they do nothing, and the label says what the TV actually ended on, not a guess.
    if (picBusy) return;
    picBusy = true;
    renderQuick();
    api("POST", "/api/picture", { mode: "toggle" }, 20000)
      .then(function (j) { if (j.mode) picMode = j.mode; })
      .catch(function (e) { note(e.message); })
      .then(function () { picBusy = false; renderQuick(); });
  }
  // The look is kept on this TV; the page starts again in the other look.  // Start the page again, so a new look or new settings take effect everywhere at once.
  function restart() {
    try {
      if (TV && TV.reload) {
        TV.reload();
        return;
      }
    } catch (e) {}
    location.replace(location.href.replace(/([?&])theme=[a-z]+&?/, "$1").replace(/[?&]$/, ""));
  }
  // The TV's own input list (HDMI, a console, a set top box): the TV_INPUT key through the bridge,
  // or straight from the launcher when there is no bridge (only where the TV has such a screen).
  function openInputs() {
    var native = function () { try { return !!(TV && TV.openInputs && TV.openInputs()); } catch (e) { return false; } };
    if (OFFLINE) { if (!native()) note(t("no_inputs")); return; }
    api("POST", "/api/key", { key: "input" })
      .catch(function (e) { if (!native()) note(e.message); });
  }
  function watchlistAppName() {
    if (!ST.quick_watchlist_pkg) return "";
    var hit = (data.apps || []).filter(function (a) { return a.package === ST.quick_watchlist_pkg; })[0];
    return hit ? hit.name : ST.quick_watchlist_pkg;
  }
  function liveAppName() {
    var pkg = ST.quick_live_pkg;
    if (!pkg) return "";
    var hit = (data.apps || []).filter(function (a) { return a.package === pkg; })[0];
    if (hit) return hit.name;
    if (pkg === "com.android.tv" || pkg === "com.google.android.tv") return t("quick_live");
    return pkg;
  }
  function launchApp(pkg) {
    if (!pkg) return;
    try {
      if (TV && TV.launch) {
        if (TV.launch(pkg)) return;
      }
    } catch (e) {}
    api("POST", "/api/launch", { app: pkg }).catch(function (e) { status(e.message); });
  }
  function openWatchlist() {
    if (zone === "settings") {
      $("settings").classList.remove("on");
      document.documentElement.classList.remove("setting");
      zone = "grid";
    }
    if (zone === "gear") closeGear();
    var pkg = ST.quick_watchlist_pkg;
    if (pkg) launchApp(pkg);
    else openAppPicker("watchlist");
  }
  function openLiveChannels() {
    if (zone === "settings") {
      $("settings").classList.remove("on");
      document.documentElement.classList.remove("setting");
      zone = "grid";
    }
    if (zone === "gear") closeGear();
    var pkg = ST.quick_live_pkg;
    if (pkg) {
      launchApp(pkg);
    } else {
      openAppPicker("live");
    }
  }
  var subOpenedAt = 0;
  function openAppPicker(target) {
    subOpenedAt = Date.now();
    if (zone !== "settings") {
      openSettings();
      for (var s = 0; s < SECS.length; s++) {
        if (SECS[s][0] === "quick") { SET.sec = s; break; }
      }
    }
    SET.col = "rows";
    var pkgKey = target === "watchlist" ? "quick_watchlist_pkg" : "quick_live_pkg";
    var backKey = target === "watchlist" ? "quick:watchlist_app" : "quick:live_app";
    var currentPkg = ST[pkgKey] || "";
    var titleFn = function () {
      return target === "watchlist" ? t("quick_watchlist_picker_title") : t("quick_live_picker_title");
    };

    SET.sub = {
      back: backKey,
      crumb: t("sec_quick"),
      title: titleFn,
      rows: function () {
        var rList = [];
        if (ST[pkgKey]) {
          rList.push({
            kind: "action",
            key: "quick:unset_" + target,
            label: target === "watchlist" ? t("quick_watchlist_app_unset") : t("quick_live_app_unset"),
            act: function () {
              ST[pkgKey] = "";
              saveSettings();
              renderQuick();
              if (SET.sub) {
                SET.keyFocus = SET.sub.back;
                SET.sub = null;
                invalidateSettingsCache();
                renderSettings();
              }
            }
          });
          rList.push(head(t("sec_apps")));
        }
        var appList = (data.apps || []).slice();
        appList.forEach(function (a) {
          var isChosen = ST[pkgKey] === a.package;
          rList.push({
            kind: "choice",
            key: "app:" + a.package,
            label: a.name,
            icon: a.package,
            checked: isChosen,
            act: function () {
              ST[pkgKey] = a.package;
              saveSettings();
              renderQuick();
              if (SET.sub) {
                SET.keyFocus = SET.sub.back;
                SET.sub = null;
                invalidateSettingsCache();
                renderSettings();
              }
            }
          });
        });
        return rList;
      }
    };

    var subRows = SET.sub.rows();
    var targetKey = currentPkg ? "app:" + currentPkg : null;
    var targetIdx = targetKey ? indexOfKey(subRows, targetKey) : -1;
    if (targetIdx < 0) targetIdx = firstRow(subRows);
    if (targetIdx < 0) targetIdx = 0;
    SET.ri = targetIdx;
    SET.keyFocus = subRows[targetIdx] ? subRows[targetIdx].key : null;
    invalidateSettingsCache();
    renderSettings();
  }
  function quickAction() {
    var q = quickVisible()[qi];
    if (q === "watchlist") openWatchlist();
    else if (q === "live") openLiveChannels();
    else if (q === "sleep") openMenu();
    else if (q === "picture") togglePicture();
    else if (q === "input") openInputs();
  }
  function refreshQuick() {
    var jobs = [];
    if (QUICK.indexOf("sleep") >= 0) {
      jobs.push(api("GET", "/api/sleep").then(function (j) { sleepEnds = j.ends ? Date.parse(j.ends) : null; }, function () {}));
    }
    if (QUICK.indexOf("picture") >= 0) {
      jobs.push(api("GET", "/api/picture").then(function (j) {
        picShown = j.supported !== false;
        picMode = j.mode || null; picCanSet = !!j.can_set;
        if (j.modes && j.modes.length) picModes = j.modes;
      }, function () { picShown = false; picCanSet = false; }));
    }
    return Promise.all(jobs).then(renderQuick);
  }

  // ---------------- Keep watching ----------------
  function subtitle(it) {
    var parts = [];
    if (it.app && it.app.name) parts.push(it.app.name);
    if (it.season && it.episode) parts.push("S" + it.season + ", E" + it.episode);
    if (it.episode_title) parts.push(it.episode_title);
    return parts.join("  ·  ");
  }

  // ---------------- who is watching: none, a pill switch for two, a head icon and a list for more ----------------
  var PALETTE = ["#ff9f0a", "#30b350", "#0a84ff", "#bf5af2", "#ff375f", "#00a5a8", "#5e5ce6", "#ac8e68"];
  function peopleCount() { return NOBODY ? 0 : PEOPLE.length; }
  function isHead() { return peopleCount() >= 3; }
  function colourOf(p) {
    if (p.colour) return p.colour;
    for (var h = 0, i = 0; i < p.id.length; i++) h = (h * 31 + p.id.charCodeAt(i)) >>> 0;
    return PALETTE[h % PALETTE.length];
  }
  function avatarHTML(p) {
    return '<span class="avatar" style="background:' + esc(colourOf(p)) + '">' + esc((p.name || "?").trim().charAt(0).toUpperCase()) + "</span>";
  }
  function whoIndex() { return Math.max(0, PEOPLE.map(function (p) { return p.id; }).indexOf(who)); }

  function renderPeople() {
    var el = $("people"), n = peopleCount();
    el.classList.toggle("none", n < 2);
    el.classList.toggle("head", n >= 3);
    if (n >= 3) {
      var cur = PEOPLE[whoIndex()];
      el.innerHTML = avatarHTML(cur) + '<span class="sr">' + esc(cur.name) + "</span>";
    } else {
      el.innerHTML = PEOPLE.map(function (p) {
        return '<span class="person' + (p.id === who ? " on" : "") + '">' + esc(p.name) + "</span>";
      }).join("");
    }
    renderPop();
  }

  function renderPop() {
    var el = $("people-pop");
    if (!el.classList.contains("on")) return;
    el.innerHTML = '<div class="plist">' + PEOPLE.map(function (p, i) {
      var cls = "prow" + (i === pmi ? " focus" : "") + (plift && i === pmi ? " lifted" : "") + (p.id === who ? " on" : "");
      var tail = plift && i === pmi ? '<i class="pmark">' + ICONS.updown + "</i>" : p.id === who ? '<i class="pmark">' + ICONS.check + "</i>" : "";
      return '<div class="' + cls + '">' + avatarHTML(p) + "<b>" + esc(p.name) + "</b>" + tail + "</div>";
    }).join("") + '</div><p class="phint">' + (plift ? t("people_lift_hint") : t("people_hold_hint")) + "</p>";
  }
  function openPop() {
    zone = "pmenu"; pmi = whoIndex(); plift = false;
    $("people-pop").classList.add("on");
    renderPop();
  }
  function closePop() {
    $("people-pop").classList.remove("on");
    $("people-pop").innerHTML = "";
    plift = false;
    zone = "people";
  }
  function pickWho(id) {
    if (id === who) return;
    who = id; s = 0;
    try { localStorage.setItem("tvhome.who", who); } catch (e) {}
    if (!NOBODY) api("POST", "/api/who", { who: who }).catch(function () {});
    renderShelf();
  }
  // The order of the names: kept by the bridge (the phone's People sheet uses the same list), or on
  // this TV when there is no bridge. "pending" means the bridge has not heard about it yet.
  function savePeopleOrder() {
    var ids = PEOPLE.map(function (p) { return p.id; });
    keep("tvhome.people.order", { ids: ids, pending: true });
    if (OFFLINE || data.bridge === false) return;
    api("POST", "/api/people", { order: ids })
      .then(function () { keep("tvhome.people.order", { ids: ids, pending: false }); }, function () {});
  }
  function dropPeople() {
    plift = false;
    savePeopleOrder();
    renderPeople();
  }

  function renderShelf() {
    var track = $("shelf");
    data.shows = data.showsBy[who] || [];
    var anyone = PEOPLE.some(function (p) { return (data.showsBy[p.id] || []).length; });
    // nobody has anything on the go: no empty box, the photo has the space
    $("shelf-section").classList.toggle("empty", !anyone);
    $("people").classList.toggle("empty", !anyone);
    renderPeople();
    if (!data.shows.length) {
      var name = PEOPLE[whoIndex()].name;
      track.innerHTML = '<p class="shelf-none">' + esc(peopleCount() < 2 ? t("nothing_yet") : t("nothing_for", { name: name })) + "</p>";
      shelfEls = [];
      return;
    }
    if (NIGHT) {
      // lock screen notifications: the first three shows as glass cards
      data.shows = data.shows.slice(0, 3);
      track.innerHTML = data.shows.map(function (it) {
        var art = it.backdrop || it.poster, pkg = it.app && it.app.package;
        var ep = it.season && it.episode ? "S" + it.season + " E" + it.episode : (it.episode_title || "");
        return '<div class="tile ncard">' +
          (art ? '<img class="thumb" src="' + esc(art) + '" alt="" decoding="async">' : '<span class="thumb none"></span>') +
          '<div class="t"><div class="nap">' + (pkg && iconUrl(pkg) ? '<img src="' + esc(iconUrl(pkg)) + '" alt="" onerror="this.remove()">' : "") +
          esc(it.app ? it.app.name : "") + "</div><b>" + esc(it.title) + "</b>" + (ep ? "<i>" + esc(ep) + "</i>" : "") +
          (it.progress ? '<span class="nbar"><i style="width:' + Math.round(it.progress * 100) + '%"></i></span>' : "") + "</div></div>";
      }).join("");
      shelfEls = Array.prototype.slice.call(track.children);
      return;
    }
    track.innerHTML = data.shows.map(function (it) {
      var art = it.backdrop || it.poster;
      var pkg = it.app && it.app.package;
      var badge = pkg && iconUrl(pkg) ? '<span class="badge"><img src="' + esc(iconUrl(pkg)) + '" alt="" onerror="this.parentNode.remove()"></span>' : "";
      var bar = it.progress ? '<span class="bar"><i style="width:' + Math.round(it.progress * 100) + '%"></i></span>' : "";
      return '<div class="tile show"><div class="face">' +
        (art ? '<img src="' + esc(art) + '" alt="" decoding="async">' : '<span class="no-art">' + esc(it.title) + "</span>") +
        badge + bar + '</div><div class="caption"><b>' + esc(it.title) + "</b><span>" + esc(subtitle(it)) + "</span></div></div>";
    }).join("");
    shelfEls = Array.prototype.slice.call(track.children);
  }

  // ---------------- apps ----------------
  function tucked(a) {
    return TUCKED.indexOf(a.package) >= 0 || TUCKED_NAMES.test(a.name || "");
  }
  function isNew(pkg) { return newApps.indexOf(pkg) >= 0; }
  // The TV's apps less the ones hidden in Home settings (they come back from the Apps list there).
  function shownApps() { return data.apps.filter(function (a) { return HIDDEN_TV.indexOf(a.package) < 0; }); }
  // The gear tile: always there, whatever the TV calls its own settings app. OK opens a small menu,
  // Home settings (this page's own) or TV settings (Android's).
  function gearItem() { return { kind: "gear", name: t("settings"), package: SETTINGS_PKG }; }
  function moreItem() { return { kind: "more", name: t("more_apps") }; }
  // Smart home (optional, smarthome.js): its tile sits just before the gear tile, only when
  // config.json has a home_assistant section with rooms or scenes. Otherwise nothing changes.
  function smartOn() { return !!(window.smartHome && window.smartHome.available()); }
  function smartItem() { return { kind: "smart", name: t("smart_home"), package: "smart:home" }; }
  function toolItems() { return smartOn() ? [smartItem(), gearItem()] : [gearItem()]; }
  function gridList() {
    if (NIGHT) return dockList();
    var apps = shownApps().filter(function (a) { return a.package !== SETTINGS_PKG && a.package !== "com.android.settings"; });
    var list = apps.filter(function (a) { return !tucked(a); });
    var extra = apps.filter(tucked);
    data.screens.forEach(function (sc) { list.push({ kind: "screen", id: sc.id, name: sc.name, ready: sc.ready, note: sc.note }); });
    var tools = toolItems();
    if (expanded) return list.concat(extra, tools);
    if (extra.length || list.length > GRID_MAX - tools.length) {
      list = list.slice(0, GRID_MAX - 1 - tools.length).concat(tools, [moreItem()]);
    } else list = list.concat(tools);
    return list;
  }

  // Night: a dock of the first ten apps (home.dock_size), then Settings, then More apps (which shows
  // everything, the App Store first). The order and the More apps line come from Arrange apps on the
  // phone, or from moving apps with the TV remote (hold OK), once one is saved.
  function arrangeItems() {
    var rest = shownApps().filter(function (a) { return a.package !== SETTINGS_PKG && a.package !== "com.android.settings"; });
    var screens = data.screens.map(function (sc) {
      return { kind: "screen", id: sc.id, name: sc.name, ready: sc.ready, note: sc.note, package: "screen:" + sc.id };
    });
    var items = rest.concat(screens);
    var line = Math.min(DOCK_APPS, rest.length);
    var o = data.order;
    if (o && o.saved) {
      var at = {};
      o.packages.forEach(function (p, i) { at[p] = i; });
      var known = items.filter(function (it) { return at[it.package] !== undefined; })
        .sort(function (a, b) { return at[a.package] - at[b.package]; });
      var gained = items.filter(function (it) { return at[it.package] === undefined; });   // new apps go below the line
      items = known.concat(gained);
      // the line counts the apps above it that this TV still has
      var above = o.packages.slice(0, o.line).filter(function (p) { return known.some(function (it) { return it.package === p; }); }).length;
      line = Math.min(DOCK_APPS, above);
    }
    return { items: items, line: line };
  }

  function dockList() {
    var arr = arrangeItems();
    var tools = toolItems();
    var dockCount = Math.max(1, 5 - tools.length);
    var dockApps = arr.items.slice(0, dockCount).concat(tools);
    if (!expanded) {
      return dockApps;
    }
    var restApps = arr.items.slice(dockCount);
    return dockApps.concat(restApps);
  }

  // Tell the bridge which apps this TV has (in its default order), so the phone can arrange them.
  var reported = "";
  function reportApps() {
    if (!NIGHT || MOCK || PREVIEW || !data.apps.length) return;
    var saved = data.order;
    data.order = null;   // the default order, not the arranged one
    var arr = arrangeItems();
    data.order = saved;
    var payload = { apps: arr.items.map(function (it) { return { package: it.package, name: it.name }; }), line: arr.line };
    var key = JSON.stringify(payload);
    if (key === reported) return;
    api("POST", "/api/home-apps", payload).then(function () { reported = key; }, function () {});
  }

  // ---------------- moving apps with the TV remote ----------------
  // The arrangement as it is on screen, written down as a saved order, so it can be changed.
  function materialise() {
    var arr = arrangeItems();
    data.order = { saved: true, line: arr.line, packages: arr.items.map(function (it) { return it.package; }) };
    return arr;
  }
  // Saved through the bridge (the phone's Arrange apps reads the same order), or on this TV with no
  // bridge. A TV-only order is marked pending and handed to the bridge the next time it answers.
  function persistOrder() {
    var o = data.order;
    var local = { line: o.line, packages: o.packages, pending: true };
    keep("tvhome.order.tv", local);
    save();
    if (OFFLINE || data.bridge === false) return;
    api("POST", "/api/app-order", { order: o.packages, line: o.line })
      .then(function () { local.pending = false; keep("tvhome.order.tv", local); }, function () {});
  }
  function nameOf(pkg) {
    var it = arrangeItems().items.filter(function (x) { return x.package === pkg; })[0];
    return it ? it.name : t("that_app");
  }
  var hintTimer = null, hintFlash = "";
  function renderHint() {
    var text = hintFlash ||
      (lift ? t("app_lift_hint") : NIGHT && expanded && zone === "grid" ? t("app_add_hint") : "");
    $("hint").textContent = text;
    $("hint").classList.toggle("on", !!text);
    document.documentElement.classList.toggle("hinting", !!text);
  }
  function flash(text) {
    clearTimeout(hintTimer);
    hintFlash = text;
    renderHint();
    hintTimer = setTimeout(function () { hintFlash = ""; renderHint(); }, 4000);
  }
  function holdApp() {
    var a = gridItems[g];
    if (!a || a.kind === "more") return false;
    if (a.store || a.kind === "gear" || a.kind === "smart" || a.package === SETTINGS_PKG || a.package === "com.android.settings") { flash(t("stays_put", { name: a.name })); return true; }
    var arr = arrangeItems();
    var idx = arr.items.map(function (it) { return it.package; }).indexOf(a.package);
    if (idx < 0) return false;
    if (!expanded) {
      materialise();
      lift = { pkg: a.package };
      hintFlash = "";
      renderGrid(); paintFocus();
      return true;
    }
    if (idx < arr.line) { flash(t("already_home", { name: a.name })); return true; }
    // More apps: add it to the end of the dock; a full dock sends its last app behind More apps
    var o = materialise() && data.order, msg = t("now_home", { name: a.name });
    o.packages.splice(idx, 1);
    if (o.line < DOCK_APPS) { o.packages.splice(o.line, 0, a.package); o.line += 1; }
    else {
      o.packages.splice(o.line - 1, 0, a.package);
      msg += " " + t("moved_more", { name: nameOf(o.packages[o.line]) });
    }
    persistOrder();
    expanded = false;
    renderGrid();
    g = o.packages.indexOf(a.package);
    paintFocus();
    flash(msg);
    return true;
  }
  function moveLift(dx, dy) {
    var o = data.order, i = o.packages.indexOf(lift.pkg);
    if (dx) {
      var j = i + dx;
      if (j >= 0 && j < o.line) { o.packages[i] = o.packages[j]; o.packages[j] = lift.pkg; g = j; }
    } else if (dy > 0) {   // behind More apps: first in line there
      var name = nameOf(lift.pkg);
      o.packages.splice(i, 1);
      o.packages.splice(o.line - 1, 0, lift.pkg);
      o.line -= 1;
      lift = null;
      persistOrder();
      renderGrid();
      g = Math.min(i, gridEls.length - 1);
      flash(t("moved_more", { name: name }));
    }
    renderGrid();
    paintFocus();
  }
  function dropLift() {
    lift = null;
    persistOrder();
    renderGrid();
    paintFocus();
    flash(t("saved"));
  }

  // Apps installed since this TV first listed its apps: the end of the dock when there is room,
  // otherwise first behind More apps, with a New dot until opened once.
  function detectNew() {
    if (!data.apps.length) return;
    var pk = data.apps.filter(function (a) { return a.package !== SETTINGS_PKG && a.package !== "com.android.settings" && a.package !== STORE_PKG; })
      .map(function (a) { return a.package; });
    var seen = kept("tvhome.apps.seen");
    if (!seen) { keep("tvhome.apps.seen", pk); return; }
    var fresh = pk.filter(function (p) { return seen.indexOf(p) < 0; });
    if (!fresh.length) return;
    keep("tvhome.apps.seen", seen.concat(fresh));
    newApps = newApps.concat(fresh.filter(function (p) { return newApps.indexOf(p) < 0; }));
    keep("tvhome.apps.new", newApps);
    if (!NIGHT) return;
    materialise();
    var o = data.order;
    fresh.forEach(function (p) {
      var app = data.apps.filter(function (a) { return a.package === p; })[0];
      var i = o.packages.indexOf(p);
      if (i < 0 || i < o.line || (app && tucked(app))) return;
      o.packages.splice(i, 1);
      o.packages.splice(o.line, 0, p);
      if (o.line < DOCK_APPS) o.line += 1;
    });
    persistOrder();
  }
  function opened(pkg) {
    if (!isNew(pkg)) return;
    newApps = newApps.filter(function (p) { return p !== pkg; });
    keep("tvhome.apps.new", newApps);
    renderGrid();
  }

  function dockHTML(it) {
    var face;
    var banner = it.banner || (it.package ? (TV && TV.bannerUrl ? TV.bannerUrl(it.package) : "http://tvos.invalid/banner/" + it.package) : null);
    if (it.kind === "screen") face = '<div class="face tool">' + ICONS.screen + "</div>";
    else if (it.kind === "more") face = '<div class="face tool more-tool">' + ICONS.more + "</div>";
    else if (it.kind === "gear") face = '<div class="face tool gear-tool">' + ICONS.gear + "</div>";
    else if (it.kind === "smart") face = '<div class="face tool smart-tool">' + window.smartHome.icon + "</div>";
    else if (banner) face = '<div class="face banner"><img src="' + esc(banner) + '" alt="" decoding="async" onerror="this.onerror=null;this.src=\'' + esc(iconUrl(it.package) || "") + '\';this.className=\'banner-fallback\'"></div>';
    else if (iconUrl(it.package)) face = '<div class="face banner"><img src="' + esc(iconUrl(it.package)) + '" alt="" class="banner-fallback" decoding="async"></div>';
    else face = '<div class="face word banner">' + esc(it.name) + "</div>";
    var cls = "tile app dock-banner" + (lift && it.package === lift.pkg ? " lifted" : "");
    return '<div class="' + cls + '">' + face + (isNew(it.package) ? '<i class="dot" aria-label="' + esc(t("new_app")) + '"></i>' : "") + "</div>";
  }

  function appHTML(it) {
    if (NIGHT) return dockHTML(it);
    var face;
    if (it.kind === "screen") face = '<div class="face tool">' + ICONS.screen + "</div>";
    else if (it.kind === "more") face = '<div class="face tool">' + ICONS.more + "</div>";
    else if (it.kind === "gear") face = '<div class="face tool">' + ICONS.gear + "</div>";
    else if (it.kind === "smart") face = '<div class="face tool">' + window.smartHome.icon + "</div>";
    else if (it.banner) face = '<div class="face"><img src="' + esc(it.banner) + '" alt="" decoding="async"></div>';
    else if (iconUrl(it.package)) face = '<div class="face icon" style="background:' + esc(it.colour || "#2a2a30") + '"><img src="' + esc(iconUrl(it.package)) + '" alt=""></div>';
    else face = '<div class="face word" style="background:' + esc(it.colour || "#2a2a30") + '">' + esc(it.name) + "</div>";
    return '<div class="tile app">' + face + (isNew(it.package) ? '<i class="dot" aria-label="' + esc(t("new_app")) + '"></i>' : "") + "</div>";
  }

  function renderGrid() {
    document.documentElement.classList.toggle("expanded", expanded);
    document.documentElement.classList.toggle("lifting", !!lift);
    gridItems = gridList();
    if (NIGHT) {
      GRID_COLS = 5;
      $("grid").style.gridTemplateColumns = "repeat(5, 1fr)";
      $("grid").style.gridAutoRows = "11.25rem";
    }
    $("grid").innerHTML = gridItems.map(appHTML).join("");
    gridEls = Array.prototype.slice.call($("grid").children);
    renderHint();
  }

  // ---------------- focus ----------------
  function clearFocus() {
    shelfEls.concat(gridEls, quickEls).forEach(function (el) { if (el) el.classList.remove("focus"); });
    $("people").classList.remove("focus");
    Array.prototype.forEach.call($("people").children, function (el) { el.classList.remove("focus"); });
  }

  function scrollShelf() {
    var track = $("shelf");
    var el = shelfEls[s];
    if (!el) { track.style.transform = ""; return; }
    var step = el.offsetWidth + parseFloat(getComputedStyle(track).columnGap || 0);
    var first = Math.max(0, Math.min(s - 2, shelfEls.length - 5));   // keep up to five in view
    track.style.transform = "translateX(" + (-first * step) + "px)";
  }

  function shelfOn() { return !$("shelf-section").classList.contains("empty"); }
  // the switcher shows for two or more people, and only while someone has something on the go
  function peopleOn() { return peopleCount() >= 2 && shelfOn(); }
  function toPeople() { zone = "people"; pi = whoIndex(); }
  function showPhoto() {}

  function paintFocus() {
    if (zone === "settings") { renderSettings(); return; }
    if (zone === "smart") { window.smartHome.paint(); return; }
    clearFocus();
    renderHint();
    if (zone === "gear") { if (gridEls[g]) gridEls[g].classList.add("focus"); renderGear(); return; }
    if (zone === "menu") { renderMenu(); return; }
    if (zone === "pmenu") { renderPop(); $("people").classList.add("focus"); return; }
    if (zone === "quick" && quickEls[qi]) {
      quickEls[qi].classList.add("focus");
      hero(null);
      $("place").style.visibility = "";
      return;
    }
    if (zone === "people" && peopleOn()) {
      if (isHead()) $("people").classList.add("focus");
      else if ($("people").children[pi]) $("people").children[pi].classList.add("focus");   // drawn yet
      hero(null);
      $("place").style.visibility = "";
      return;
    }
    if (zone === "shelf" && shelfEls[s]) {
      shelfEls[s].classList.add("focus");
      scrollShelf();
      if (NIGHT) return;   // the cards carry the titles; the photo stays put under the clock
      setBg(data.shows[s].backdrop || data.shows[s].poster, true);
      hero(data.shows[s]);
      $("place").style.visibility = "hidden";
    } else {
      zone = "grid";
      g = Math.max(0, Math.min(g, gridEls.length - 1));
      if (gridEls[g]) {
        gridEls[g].classList.add("focus");
        if (expanded && typeof gridEls[g].scrollIntoView === "function") {
          gridEls[g].scrollIntoView({ behavior: "auto", block: "nearest" });
        }
      }
      hero(null);
      $("place").style.visibility = "";
    }
    renderHint();
  }

  function nearestInRow(row, x) {
    var best = row * GRID_COLS, bestD = Infinity;
    for (var i = row * GRID_COLS; i < Math.min(gridEls.length, (row + 1) * GRID_COLS); i++) {
      var b = gridEls[i].getBoundingClientRect();
      var d = Math.abs(b.left + b.width / 2 - x);
      if (d < bestD) { bestD = d; best = i; }
    }
    return best;
  }

  function move(dx, dy) {
    touched = true;
    if (zone === "settings") { setMove(dx, dy); return; }
    if (zone === "smart") { window.smartHome.move(dx, dy); return; }
    if (zone === "gear") { if (dy) { gi = Math.max(0, Math.min(1, gi + dy)); renderGear(); } return; }
    if (lift) { moveLift(dx, dy); return; }
    // Night with More apps open: the cards step aside, so up and down skip them
    var cards = NIGHT && expanded ? [] : shelfEls;
    if (zone === "menu") {
      if (dx) menuI = Math.max(0, Math.min(SLEEP_CHOICES.length - 1, menuI + dx));
      paintFocus();
      return;
    }
    if (zone === "pmenu") {
      if (dy) {
        var n = Math.max(0, Math.min(PEOPLE.length - 1, pmi + dy));
        if (plift && n !== pmi) { var tmp = PEOPLE[pmi]; PEOPLE[pmi] = PEOPLE[n]; PEOPLE[n] = tmp; }
        pmi = n;
        renderPop();
      }
      return;
    }
    if (zone === "quick") {
      var nq = quickVisible().length;
      if (dx < 0 && qi === 0) { if (peopleOn()) toPeople(); }
      else if (dx) qi = Math.max(0, Math.min(nq - 1, qi + dx));
      else if (dy > 0) {
        if (peopleOn()) toPeople();
        else if (cards.length) zone = "shelf";
        else { zone = "grid"; g = Math.min(GRID_COLS, gridEls.length) - 1; }
      }
      paintFocus();
      return;
    }
    if (zone === "people") {
      if (dx > 0 && (isHead() || pi === PEOPLE.length - 1)) { if (hasQuick()) { zone = "quick"; qi = 0; } }
      else if (dx && !isHead()) {
        // moving across the names only moves the outline; OK picks (you have to click to select), so passing a name on the way to Sleep timer changes nothing
        pi = Math.max(0, Math.min(PEOPLE.length - 1, pi + dx));
      } else if (dy > 0) zone = cards.length ? "shelf" : "grid";
    } else if (zone === "shelf") {
      if (dx) s = Math.max(0, Math.min(shelfEls.length - 1, s + dx));
      else if (dy < 0) { if (peopleOn()) toPeople(); else if (hasQuick()) { zone = "quick"; qi = 0; } }
      else if (dy > 0 && gridEls.length) {
        // land on the app under the show
        var b = shelfEls[s].getBoundingClientRect();
        g = nearestInRow(0, b.left + b.width / 2);
        zone = "grid";
      }
    } else {
      var row = Math.floor(g / GRID_COLS), lastRow = Math.floor((gridEls.length - 1) / GRID_COLS);
      if (dx) {
        var nx = g + dx;
        if (nx >= 0 && nx < gridEls.length && Math.floor(nx / GRID_COLS) === row) g = nx;
      } else if (dy < 0 && row === 0) {
        if (expanded && NIGHT) {
          expanded = false;
          if ($("apps-section")) $("apps-section").scrollTop = 0;
          renderGrid();
          g = Math.min(g, gridEls.length - 1);
        } else if (cards.length) zone = "shelf";
        else if (peopleOn()) toPeople();
        else if (hasQuick()) zone = "quick";   // offline the sleep and picture buttons are hidden
      } else if (dy < 0) {
        g -= GRID_COLS;
      } else if (dy > 0) {
        if (!expanded && NIGHT) {
          expanded = true;
          renderGrid();
          g = Math.min(g + GRID_COLS, gridEls.length - 1);
        } else if (row < lastRow) {
          g = Math.min(g + GRID_COLS, gridEls.length - 1);
        }
      }
    }
    paintFocus();
  }

  // Start on the people switch, so the first press picks who is watching.
  function startFocus() {
    s = 0; g = 0;
    if (peopleOn()) toPeople();
    else zone = shelfEls.length ? "shelf" : "grid";
  }

  function goHome() {
    if (zone === "settings") { setBack(); return; }
    if (zone === "smart") { closeSmart(); paintFocus(); return; }
    if (zone === "gear") { closeGear(); paintFocus(); return; }
    if (lift) { dropLift(); return; }   // BACK puts a picked-up app down where it is
    if (zone === "pmenu") { if (plift) dropPeople(); else closePop(); paintFocus(); return; }
    if (zone === "menu") { closeMenu(); paintFocus(); return; }   // BACK just closes the sleep choices
    if (expanded) {
      expanded = false;
      if ($("apps-section")) $("apps-section").scrollTop = 0;
      renderGrid();
      startFocus();
      paintFocus();
      return;
    }
    startFocus();
    paintFocus();
  }

  // ---------------- actions ----------------
  // A long press of OK (or the Menu key): pick up an app or a name to move it. Returns false when
  // there is nothing to pick up here, and then the press counts as a normal OK.
  function hold() {
    touched = true;
    if (zone === "settings") return setHold();
    if (zone === "gear" || zone === "smart") return false;
    if (lift) { dropLift(); return true; }
    if (zone === "pmenu") {
      if (plift) dropPeople();
      else if (PEOPLE.length > 1) { plift = true; renderPop(); }
      return true;
    }
    if (zone === "grid" && NIGHT) return holdApp();
    return false;
  }

  function activate() {
    touched = true;
    if (zone === "settings") { setActivate(); return; }
    if (zone === "smart") { window.smartHome.ok(); return; }
    if (zone === "gear") { if (gi === 0) openSettings(); else openTvSettings(); return; }
    if (lift) { dropLift(); return; }
    if (zone === "menu") { setSleep(SLEEP_CHOICES[menuI]); paintFocus(); return; }
    if (zone === "pmenu") {
      if (plift) { dropPeople(); return; }
      pickWho(PEOPLE[pmi].id);
      closePop();
      renderPeople();
      paintFocus();
      return;
    }
    if (zone === "quick") { quickAction(); return; }
    if (zone === "people") {
      if (isHead()) { openPop(); paintFocus(); return; }
      pickWho(PEOPLE[pi].id);
      paintFocus();
      return;
    }
    if (zone === "shelf") {
      var it = data.shows[s];
      if (!it) return;
      status(it.app ? t("opening_in", { title: it.title, app: it.app.name }) : t("opening", { title: it.title }), true);
      if (it.wn && TV && TV.openWatchNext) {
        if (!TV.openWatchNext(String(it.id)) && it.app) TV.launch(it.app.package);
        status("");
        return;
      }
      api("POST", "/api/continue/open", { id: it.id, who: NOBODY ? "both" : who }, 60000)
        .then(function (res) { status(res.note || ""); })
        .catch(function (e) {
          // the bridge can't help: at least open the app it was in
          if (TV && it.app && TV.launch(it.app.package)) { status(""); return; }
          status(e.message);
        });
      return;
    }
    var a = gridItems[g], el = gridEls[g];
    if (!a) return;
    if (a.kind === "more") { expanded = true; renderGrid(); paintFocus(); return; }
    if (a.kind === "gear" || a.package === SETTINGS_PKG || a.package === "com.android.settings") { openGear(); return; }
    if (a.kind === "smart") { openSmart(); return; }
    if (a.kind === "screen") {
      if (!a.ready) { status(a.note || t("screen_not_set")); return; }
      el.classList.add("busy");
      api("POST", "/api/screens/open", { id: a.id }, 10000)
        .then(function (res) { status(res.note || ""); })
        .catch(function (e) {
          if (TV && TV.launch("com.limelight")) { status(""); return; }
          status(e.message);
        })
        .then(function () { el.classList.remove("busy"); });
      return;
    }
    opened(a.package);
    if (TV && TV.launch(a.package)) return;
    api("POST", "/api/launch", { app: a.id || a.package }).catch(function (e) { status(e.message); });
  }

  // ---------------- Smart home: the rooms and scenes screen (smarthome.js) ----------------
  function openSmart() { zone = "smart"; clearFocus(); window.smartHome.open(); }
  function closeSmart() { window.smartHome.close(); zone = "grid"; }

  // ---------------- the gear tile's menu: Home settings or the TV's own settings ----------------
  var gi = 0;
  function openGear() {
    if (zone === "settings") closeSettings();
    clearFocus();
    zone = "gear"; gi = 0;
    for (var i = 0; i < gridItems.length; i++) {
      if (gridItems[i] && (gridItems[i].kind === "gear" || gridItems[i].package === SETTINGS_PKG || gridItems[i].package === "com.android.settings")) {
        g = i;
        break;
      }
    }
    renderGear();
  }
  function renderGear() {
    var pop = $("gear-pop");
    var items = [[ICONS.sliders, t("gear_home"), t("gear_home_sub")], [ICONS.gear, t("gear_tv"), t("gear_tv_sub")]];
    var headHtml = '<div class="gear-head"><b>' + esc(t("settings")) + "</b></div>";
    var itemsHtml = items.map(function (it, i) {
      return '<div class="grow' + (i === gi ? " focus" : "") + '" role="menuitem" data-gi="' + i + '"><i>' + it[0] + "</i><span><b>" + esc(it[1]) +
        "</b><small>" + esc(it[2]) + "</small></span></div>";
    }).join("");
    pop.innerHTML = headHtml + itemsHtml;
    pop.onclick = function (e) {
      e.stopPropagation();
      var row = e.target.closest(".grow");
      if (!row) return;
      var i = parseInt(row.getAttribute("data-gi"), 10);
      if (i === 0) openSettings();
      else if (i === 1) openTvSettings();
    };
    pop.classList.add("on");
    document.documentElement.classList.add("gearing");
  }
  function closeGear() {
    $("gear-pop").classList.remove("on");
    document.documentElement.classList.remove("gearing");
    if (zone === "gear") zone = "grid";
  }
  // Android's own settings: the Settings app where the TV has one, else the system settings screen.
  function openTvSettings() {
    closeGear();
    paintFocus();
    var done = false;
    try {
      if (TV) {
        if (TV.openSettings) done = !!TV.openSettings();
        if (!done && TV.launch) done = !!TV.launch(SETTINGS_PKG);
      }
    } catch (e) {}
    if (done) return;
    if (OFFLINE) { status(t("tv_settings_none")); return; }
    api("POST", "/api/launch", { app: "settings" }).catch(function () { status(t("tv_settings_none")); });
  }

  // ---------------- Settings: Talo TV's own settings screen ----------------
  // A tvOS style list: the sections on the left, their rows on the right. Up and down move, OK
  // changes a row, holding OK picks a row up to move it (scenes, apps, people, quick buttons), Back
  // goes back a step and then closes. What is saved, and which copy wins: docs/CONFIG.md.
  // Saved on this TV (page storage and the launcher's own store, TVOS.keep) and, with a bridge,
  // also on the computer (/api/settings), so the phone and the TV agree.
  var ST_KEY = "tvhome.settings";
  var ONBOARDING = false;
  var OB_SECS = [
    ["ob_lang", "globe"],
    ["ob_perms", "lock"],
    ["ob_unit", "cloud"],
    ["ob_clock", "sleep"],
    ["ob_loc", "pin"],
    ["ob_profiles", "person"]
  ];
  var SECS = [["language", "globe"], ["permissions", "lock"], ["shield", "shield"], ["location", "pin"], ["weather", "cloud"], ["look", "look"],
    ["apps", "apps"], ["people", "person"], ["quick", "bolt"], ["about", "info"]];
  var SET = { col: "secs", sec: 0, ri: 0, keyFocus: null, sub: null, lift: null, confirm: null, dirty: false, typing: null,
    msg: "", geo: { q: "", list: null, busy: false, err: false } };
  var REPO_URL = "Talo TV";
  var WX_DEFAULT = { lat: -33.8688, lon: 151.2093, place: "Sydney", timezone: "Australia/Sydney" };
  var pushTimer = null;

  function loadSettings() {
    var r = kept(ST_KEY);
    return r && r.s && typeof r.s === "object" && !Array.isArray(r.s) ? r.s : {};
  }
  function keepSettings(pending) {
    var rec = { s: ST, updated: Date.now(), pending: pending };
    keep(ST_KEY, rec);
    return rec;
  }
  function pushSettings(rec) {
    return api("POST", "/api/settings", { settings: rec.s })
      .then(function () { rec.pending = false; keep(ST_KEY, rec); }, function () {});
  }
  // Kept on this TV straight away; handed to the bridge a moment later (pending until it has it).
  function saveSettings() {
    invalidateSettingsCache();
    var rec = keepSettings(true);
    SET.dirty = true;
    if (OFFLINE || data.bridge === false) return;
    clearTimeout(pushTimer);
    pushTimer = setTimeout(function () { pushSettings(rec); }, 400);
  }
  // Same content, whatever order the keys came back in.
  function canon(v) {
    if (Array.isArray(v)) return "[" + v.map(canon).join(",") + "]";
    if (v && typeof v === "object") return "{" + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ":" + canon(v[k]); }).join(",") + "}";
    return JSON.stringify(v);
  }
  // On every refresh with the bridge: a change made here while the computer was off goes to the
  // bridge; otherwise the bridge's copy comes back (this TV's storage was cleared, say).
  function syncSettings() {
    return api("GET", "/api/settings").then(function (j) {
      var rec = kept(ST_KEY), remote = j.settings && typeof j.settings === "object" && !Array.isArray(j.settings) ? j.settings : null;
      if (rec && rec.pending) return pushSettings(rec);
      if (!remote || (!j.updated && !Object.keys(remote).length)) {   // the bridge has none yet: it gets ours
        if (Object.keys(ST).length) return pushSettings(keepSettings(true));
        return null;
      }
      if (canon(remote) === canon(ST)) return null;
      ST = remote;
      keepSettings(false);
      if (zone !== "settings") restart();
      return null;
    });
  }

  function staticText() {
    $("shelf-title").textContent = t("keep_watching");
    $("people-pop").setAttribute("aria-label", t("who_watching"));
    $("apps-section").setAttribute("aria-label", t("apps"));
    $("settings").setAttribute("aria-label", ONBOARDING ? t("setup_title") : t("set_title"));
  }

  function openSettings() {
    invalidateSettingsCache();
    ONBOARDING = false;
    closeGear();
    zone = "settings";
    SET.col = "secs"; SET.sec = 0; SET.ri = 0; SET.keyFocus = null; SET.sub = null; SET.lift = null; SET.confirm = null; SET.msg = "";
    clearFocus();
    $("settings").classList.add("on");
    document.documentElement.classList.add("setting");
    renderSettings();
  }
  // Closing starts the page again when anything changed, so the look, scenes and words all agree.
  function closeSettings() {
    invalidateSettingsCache();
    if (ONBOARDING) return;
    if (SET.typing) stopTyping();
    if (SET.lift) dropRow();
    $("settings").classList.remove("on");
    document.documentElement.classList.remove("setting");
    zone = "grid";
    if (SET.dirty) { SET.dirty = false; restart(); return; }
    paintFocus();
  }

  // ---- rows ----
  function rowsNow() {
    if (SET.sub) return SET.sub.rows();
    if (ONBOARDING) return onboardingRows(OB_SECS[SET.sec][0]);
    return sectionRows(SECS[SET.sec][0]);
  }
  function focusable(r) { return !!r && r.kind !== "head" && r.kind !== "info" && r.kind !== "spacer"; }
  function firstRow(rows) { for (var i = 0; i < rows.length; i++) if (focusable(rows[i])) return i; return -1; }
  function stepRow(rows, i, d) { for (var j = i + d; j >= 0 && j < rows.length; j += d) if (focusable(rows[j])) return j; return i; }
  function indexOfKey(rows, k) { for (var i = 0; i < rows.length; i++) if (rows[i].key === k) return i; return -1; }
  function head(label) { return { kind: "head", key: "h:" + label, label: label }; }
  function info(key, label, value, sub) { return { kind: "info", key: key, label: label, value: value, sub: sub }; }

  function sectionRows(id) {
    if (id === "language") return languageRows();
    if (id === "permissions") return permissionsRows();
    if (id === "shield") return shieldRows();
    if (id === "location") return locationRows();
    if (id === "weather") return weatherRows();
    if (id === "look") return lookRows();
    if (id === "apps") return appsRows();
    if (id === "people") return peopleRows();
    if (id === "quick") return quickRows();
    return aboutRows();
  }

  function onboardingRows(id) {
    if (id === "ob_lang") return onboardingLanguageRows();
    if (id === "ob_perms") return onboardingPermissionsRows();
    if (id === "ob_unit") return onboardingUnitRows();
    if (id === "ob_clock") return onboardingClockRows();
    if (id === "ob_loc") return onboardingLocationRows();
    return onboardingProfilesRows();
  }

  function getPermissionsStatus() {
    try {
      if (TV && TV.permissionsStatus) {
        return JSON.parse(TV.permissionsStatus()) || {};
      }
    } catch (e) {}
    return {
      accessibility: false,
      tv_listings: false,
      overlay: false,
      default_home: false
    };
  }

  function openSystemSheet(fn) {
    document.body.classList.add("system-sheet-open");
    try { fn(); } catch (e) {}
    invalidateSettingsCache();
  }

  function permissionsRows() {
    var p = getPermissionsStatus();
    var allGood = p.accessibility && p.tv_listings && p.overlay && p.default_home;
    return [
      head(t("ob_perms_title")),
      info("ob-perms-desc", t("ob_perms_desc")),
      {
        kind: "action",
        key: "perm:all",
        label: t("perm_grant_all"),
        sub: t("perm_grant_all_sub"),
        value: allGood ? "✓ " + t("perm_granted") : "",
        act: function () {
          openSystemSheet(function () {
            if (TV && TV.requestAllPermissions) TV.requestAllPermissions();
          });
        }
      },
      {
        kind: "action",
        key: "perm:access",
        label: t("perm_access_title"),
        sub: t("perm_access_sub"),
        value: p.accessibility ? "✓ " + t("perm_granted") : t("perm_enable"),
        act: function () {
          openSystemSheet(function () {
            if (TV && TV.openAccessibilitySettings) TV.openAccessibilitySettings();
          });
        }
      },
      {
        kind: "action",
        key: "perm:tv",
        label: t("perm_tv_title"),
        sub: t("perm_tv_sub"),
        value: p.tv_listings ? "✓ " + t("perm_granted") : t("perm_enable"),
        act: function () {
          openSystemSheet(function () {
            if (TV && TV.requestTvPermissions) {
              TV.requestTvPermissions();
            } else if (TV && TV.requestRuntimePermissions) {
              TV.requestRuntimePermissions();
            }
          });
        }
      },
      {
        kind: "action",
        key: "perm:overlay",
        label: t("perm_overlay_title"),
        sub: t("perm_overlay_sub"),
        value: p.overlay ? "✓ " + t("perm_granted") : t("perm_enable"),
        act: function () {
          openSystemSheet(function () {
            if (TV && TV.openOverlaySettings) TV.openOverlaySettings();
          });
        }
      },
      {
        kind: "action",
        key: "perm:home",
        label: t("perm_home_title"),
        sub: t("perm_home_sub"),
        value: p.default_home ? "✓ " + t("perm_granted") : t("perm_enable"),
        act: function () {
          openSystemSheet(function () {
            if (TV && TV.openHomeSettings) TV.openHomeSettings();
          });
        }
      }
    ];
  }

  function isShieldAppEnabled(pkg) {
    if (ST.shield_enabled === false) return false;
    var dis = ST.shield_disabled || [];
    return dis.indexOf(pkg) < 0;
  }

  function getShieldAppsList() {
    var raw = (data.apps && data.apps.length ? data.apps : nativeApps() || []).slice();
    var list = raw.filter(function (a) {
      return a && a.package && a.package !== "com.talotv.launcher" && a.package !== "com.talotv.launcher.debug";
    });
    var seen = {}, deduped = [];
    for (var i = 0; i < list.length; i++) {
      if (!seen[list[i].package]) {
        seen[list[i].package] = true;
        deduped.push(list[i]);
      }
    }
    return deduped.sort(function (a, b) {
      return (a.name || "").localeCompare(b.name || "");
    });
  }

  function setShieldAll(enable) {
    var allApps = getShieldAppsList();
    ST.shield_enabled = true;
    if (enable) {
      ST.shield_disabled = [];
    } else {
      ST.shield_disabled = allApps.map(function (a) { return a.package; }).concat(["system"]);
    }
    saveSettings();
    if (TV && TV.reloadShieldVpn) TV.reloadShieldVpn();
    if (zone === "settings") renderSettings();
  }

  function toggleShieldApp(pkg) {
    ST.shield_enabled = true;
    ST.shield_disabled = (ST.shield_disabled || []).slice();
    var idx = ST.shield_disabled.indexOf(pkg);
    if (idx >= 0) {
      ST.shield_disabled.splice(idx, 1);
    } else {
      ST.shield_disabled.push(pkg);
    }
    saveSettings();
    if (TV && TV.reloadShieldVpn) TV.reloadShieldVpn();
    if (zone === "settings") renderSettings();
  }

  function shieldRows() {
    var allApps = getShieldAppsList();
    var rows = [];

    rows.push(head(t("shield_head")));
    rows.push(info("shield-desc", t("shield_desc")));

    // Protection mode (VPN local vs DNS Privé natif)
    rows.push(head(t("shield_mode_head")));
    var mode = ST.shield_mode || "vpn";
    rows.push({
      kind: "choice",
      key: "shield:mode",
      label: t("shield_mode_title"),
      sub: mode === "vpn" ? t("shield_mode_vpn_desc") : t("shield_mode_dns_desc"),
      icon: mode === "vpn" ? "shield" : "globe",
      value: mode === "vpn" ? t("shield_mode_vpn") : t("shield_mode_dns"),
      act: function () {
        ST.shield_mode = (mode === "vpn" ? "dns" : "vpn");
        saveSettings();
        if (zone === "settings") renderSettings();
      }
    });

    if (mode === "vpn") {
      var vpnRunning = (TV && TV.isShieldVpnRunning && TV.isShieldVpnRunning());
      rows.push({
        kind: "action",
        key: "shield:vpn_toggle",
        label: t("shield_vpn_status"),
        sub: t("shield_cast_safe"),
        icon: "shield",
        value: vpnRunning ? t("shield_vpn_active") : t("shield_vpn_inactive"),
        act: function () {
          if (vpnRunning) {
            if (TV && TV.stopShieldVpn) TV.stopShieldVpn();
          } else {
            if (TV && TV.startShieldVpn) TV.startShieldVpn();
          }
          setTimeout(function () { if (zone === "settings") renderSettings(); }, 500);
        }
      });
    } else {
      var dnsInfo = { active: false, specifier: "dns.adguard-dns.com", canWrite: false };
      try {
        if (TV && TV.getPrivateDnsInfo) dnsInfo = JSON.parse(TV.getPrivateDnsInfo());
      } catch (e) {}
      rows.push({
        kind: "action",
        key: "shield:dns_toggle",
        label: t("shield_dns_status"),
        sub: t("shield_cast_safe"),
        icon: "globe",
        value: dnsInfo.active ? t("shield_dns_active", { specifier: dnsInfo.specifier || "AdGuard DNS" }) : t("shield_dns_inactive"),
        act: function () {
          if (dnsInfo.canWrite) {
            if (dnsInfo.active) {
              if (TV && TV.setPrivateDns) TV.setPrivateDns("opportunistic", "");
            } else {
              if (TV && TV.setPrivateDns) TV.setPrivateDns("hostname", "dns.adguard-dns.com");
            }
          } else {
            openSystemSheet(function () {
              if (TV && TV.openPrivateDnsSettings) TV.openPrivateDnsSettings();
            });
          }
          setTimeout(function () { if (zone === "settings") renderSettings(); }, 600);
        }
      });
    }

    // Debloat TV section
    var debloatList = [];
    try {
      if (TV && TV.getDebloatCandidates) debloatList = JSON.parse(TV.getDebloatCandidates());
    } catch (e) {}

    if (debloatList && debloatList.length) {
      rows.push(head(t("shield_debloat_head")));
      rows.push(info("shield-debloat-desc", t("shield_debloat_desc")));

      var debloatApplied = (ST.shield_debloat_applied === true);
      var enabledDebloat = debloatList.filter(function (d) { return d.enabled; });
      var systemCleaned = (enabledDebloat.length === 0);
      var allCleaned = systemCleaned || debloatApplied;

      var cleanLabel = allCleaned ? t("shield_debloat_btn_restore") : t("shield_debloat_btn_clean");
      var cleanSub = systemCleaned
        ? "Tous les bloatwares détectés sont désactivés au niveau système"
        : (debloatApplied
          ? "Bouclier actif : publicités Leanback, télémétrie et serveurs bloqués"
          : enabledDebloat.length + " bloatwares actifs peuvent être neutralisés");
      var cleanVal = allCleaned ? "Optimisé ✓" : (enabledDebloat.length + " détectés");

      rows.push({
        kind: "action",
        key: "shield:debloat:clean_all",
        label: cleanLabel,
        sub: cleanSub,
        icon: "shield",
        value: cleanVal,
        act: function () {
          if (allCleaned) {
            ST.shield_debloat_applied = false;
            debloatList.forEach(function (cand) {
              if (TV && TV.debloatPackage) TV.debloatPackage(cand.package, false, false);
            });
            saveSettings();
            if (TV && TV.showToast) TV.showToast("Restauration effectuée");
          } else {
            ST.shield_debloat_applied = true;
            ST.shield_enabled = true;
            var anySystemDisabled = false;
            debloatList.forEach(function (cand) {
              if (cand.enabled) {
                var resStr = (TV && TV.debloatPackage) ? TV.debloatPackage(cand.package, true, false) : null;
                try {
                  var res = JSON.parse(resStr);
                  if (res && res.success) anySystemDisabled = true;
                } catch (e) {}
              }
            });
            saveSettings();
            if (TV && TV.reloadShieldVpn) TV.reloadShieldVpn();
            if (TV && TV.showToast) {
              TV.showToast(anySystemDisabled ? "✓ Bloatwares désactivés avec succès !" : "✓ Bloatwares neutralisés par Talo Shield !");
            }
          }
          if (zone === "settings") renderSettings();
        }
      });

      debloatList.forEach(function (cand) {
        var isSystemClean = !cand.enabled;
        var isClean = isSystemClean || debloatApplied;
        var statusVal = isSystemClean
          ? t("shield_debloat_candidate_disabled")
          : (debloatApplied ? "Neutralisé (Shield ✓)" : t("shield_debloat_candidate_enabled"));

        rows.push({
          kind: "check",
          key: "shield:debloat:" + cand.package,
          label: cand.name,
          sub: cand.package + (isSystemClean ? " • Désactivé système" : (debloatApplied ? " • Télémétrie et pubs bloquées" : " • Cliquer pour gérer")),
          icon: "shield",
          checked: !isClean,
          value: statusVal,
          act: function () {
            openSystemSheet(function () {
              if (TV && TV.debloatPackage) {
                TV.debloatPackage(cand.package, !isClean, true);
              }
            });
          }
        });
      });
    }

    // Apps & System Protection List
    var totalWithSystem = allApps.length + 1; // apps + system
    var protectedCount = 0;
    if (isShieldAppEnabled("system")) protectedCount++;
    for (var i = 0; i < allApps.length; i++) {
      if (isShieldAppEnabled(allApps[i].package)) protectedCount++;
    }
    var allActive = (totalWithSystem > 0 && protectedCount === totalWithSystem);
    var noneActive = (protectedCount === 0);

    var allValue = allActive ? t("shield_all_active") : noneActive ? t("shield_all_inactive") : t("shield_partial", { n: protectedCount, total: totalWithSystem });

    rows.push(head(t("shield_apps_head") + " (" + protectedCount + " / " + totalWithSystem + ")"));

    // "Tous" row
    rows.push({
      kind: "check",
      key: "shield:all",
      label: t("shield_all"),
      sub: t("shield_all_sub"),
      icon: "shield",
      checked: allActive,
      value: allValue,
      act: function () {
        setShieldAll(!allActive);
      }
    });

    // "Système Android TV" row
    var isSystemProt = isShieldAppEnabled("system");
    rows.push({
      kind: "check",
      key: "shield:app:system",
      label: t("shield_system"),
      sub: isSystemProt ? t("shield_system_sub_on") : t("shield_system_sub_off"),
      icon: "gear",
      checked: isSystemProt,
      value: isSystemProt ? t("shield_protected") : t("shield_unprotected"),
      act: function () {
        toggleShieldApp("system");
      }
    });

    // Installed apps
    allApps.forEach(function (app) {
      var isProt = isShieldAppEnabled(app.package);
      rows.push({
        kind: "check",
        key: "shield:app:" + app.package,
        label: app.name || app.package,
        sub: isProt ? t("shield_app_sub_on") : t("shield_app_sub_off"),
        icon: app.package,
        checked: isProt,
        value: isProt ? t("shield_protected") : t("shield_unprotected"),
        act: function () {
          toggleShieldApp(app.package);
        }
      });
    });

    return rows;
  }

  function onboardingPermissionsRows() {
    var rows = permissionsRows();
    rows.push({ kind: "spacer" });
    rows.push({ kind: "ob-nav", key: "ob-nav" });
    return rows;
  }

  function onboardingLanguageRows() {
    var L = window.I18N;
    var rows = L ? L.langs.map(function (l) {
      return {
        kind: "choice",
        key: "ob-lang:" + l.id,
        label: l.name,
        lang: l.locale,
        checked: L.lang() === l.id,
        act: function () {
          ST.lang = l.id;
          L.set(l.id);
          saveSettings();
          staticText();
          renderSettings();
        }
      };
    }) : [];
    rows.push(info("ob-lang-note", t("lang_note")));
    rows.push({ kind: "spacer" });
    rows.push({ kind: "ob-nav", key: "ob-nav" });
    return rows;
  }

  function onboardingUnitRows() {
    return [
      head(t("ob_unit_title")),
      {
        kind: "choice",
        key: "unit:c",
        label: t("ob_unit_c"),
        checked: !WX_F,
        act: function () {
          WX_F = false;
          ST.wx_unit = "c";
          saveSettings();
          renderSettings();
        }
      },
      {
        kind: "choice",
        key: "unit:f",
        label: t("ob_unit_f"),
        checked: !!WX_F,
        act: function () {
          WX_F = true;
          ST.wx_unit = "f";
          saveSettings();
          renderSettings();
        }
      },
      info("ob-unit-note", t("wx_night_only")),
      { kind: "spacer" },
      { kind: "ob-nav", key: "ob-nav" }
    ];
  }

  function onboardingClockRows() {
    return [
      head(t("ob_clock_title")),
      {
        kind: "choice",
        key: "clock:24",
        label: t("ob_clock_24"),
        checked: !!CLOCK24,
        act: function () {
          CLOCK24 = true;
          ST.clock = "24h";
          saveSettings();
          glassShown = "";
          clock();
          renderSettings();
        }
      },
      {
        kind: "choice",
        key: "clock:12",
        label: t("ob_clock_12"),
        checked: !CLOCK24,
        act: function () {
          CLOCK24 = false;
          ST.clock = "12h";
          saveSettings();
          glassShown = "";
          clock();
          renderSettings();
        }
      },
      { kind: "spacer" },
      { kind: "ob-nav", key: "ob-nav" }
    ];
  }

  var SUGGESTED_CITIES = [
    { name: "Paris", area: "Île-de-France, France", lat: 48.86, lon: 2.35, timezone: "Europe/Paris" },
    { name: "Lyon", area: "Auvergne-Rhône-Alpes, France", lat: 45.76, lon: 4.84, timezone: "Europe/Paris" },
    { name: "Marseille", area: "Provence-Alpes-Côte d'Azur, France", lat: 43.30, lon: 5.37, timezone: "Europe/Paris" },
    { name: "Bruxelles", area: "Belgique", lat: 50.85, lon: 4.35, timezone: "Europe/Brussels" },
    { name: "Genève", area: "Suisse", lat: 46.20, lon: 6.14, timezone: "Europe/Zurich" },
    { name: "Montréal", area: "Québec, Canada", lat: 45.50, lon: -73.57, timezone: "America/Toronto" },
    { name: "London", area: "England, United Kingdom", lat: 51.51, lon: -0.13, timezone: "Europe/London" },
    { name: "New York", area: "New York, United States", lat: 40.71, lon: -74.01, timezone: "America/New_York" }
  ];

  function onboardingLocationRows() {
    var geo = SET.geo;
    var curName = (ST.place && ST.place.name) || WXCFG.place || t("loc_unset");
    var curTz = (ST.place && ST.place.timezone) || (WXCFG.timezone && WXCFG.timezone !== "auto" ? WXCFG.timezone : "");
    var rows = [
      info("loc-now", t("loc_now"), curName, curTz),
      {
        kind: "input",
        key: "loc-search",
        label: t("loc_search"),
        value: geo.q,
        act: function () {
          startTyping({
            label: t("loc_type"),
            value: geo.q,
            search: true,
            live: function (q) { findPlaces(q, false); },
            submit: function (q) { findPlaces(q, true); }
          });
        }
      }
    ];
    if (geo.busy) rows.push(info("loc-busy", t("loc_searching")));
    else if (geo.err) rows.push(info("loc-err", t("loc_error")));
    else if (geo.list && !geo.list.length) rows.push(info("loc-none", t("loc_none", { q: geo.q })));

    if (geo.list && geo.list.length) {
      rows.push(head(t("loc_results")));
      geo.list.forEach(function (p, i) {
        var isCur = !!(ST.place && ST.place.lat === p.lat && ST.place.lon === p.lon);
        rows.push({
          kind: "choice",
          key: "place:" + i,
          label: p.name,
          sub: p.area,
          checked: isCur,
          act: function () {
            choosePlace(p);
            renderSettings();
          }
        });
      });
    } else if (!geo.q) {
      rows.push(head(t("loc_suggestions")));
      SUGGESTED_CITIES.forEach(function (p, i) {
        var isCur = !!(ST.place && ST.place.lat === p.lat && ST.place.lon === p.lon);
        rows.push({
          kind: "choice",
          key: "sug-place:" + i,
          label: p.name,
          sub: p.area,
          checked: isCur,
          act: function () {
            choosePlace(p);
            renderSettings();
          }
        });
      });
    }
    rows.push({ kind: "spacer" });
    rows.push({ kind: "ob-nav", key: "ob-nav" });
    return rows;
  }

  function onboardingProfilesRows() {
    var list = NOBODY ? [] : PEOPLE, rows = [];
    rows.push(head(t("ob_profiles_head")));
    rows.push(info("ob-profiles-desc", t("ob_profiles_desc")));
    if (!list.length) {
      rows.push(info("ob-no-profiles", t("ob_no_profiles")));
    } else {
      list.forEach(function (p) {
        rows.push({
          kind: "nav",
          key: "person:" + p.id,
          label: p.name,
          avatar: p,
          act: function () { openPerson(p.id); },
          move: function (d) { movePerson(p.id, d); }
        });
      });
    }
    if (list.length < 12) {
      rows.push({
        kind: "input",
        key: "people-add",
        label: t("people_add"),
        act: function () {
          startTyping({ label: t("people_type"), value: "", submit: addPerson });
        }
      });
    }
    rows.push({ kind: "spacer" });
    rows.push({ kind: "ob-nav", key: "ob-nav" });
    return rows;
  }

  function openOnboarding() {
    ONBOARDING = true;
    closeGear();
    zone = "settings";
    SET.col = "rows";
    SET.sec = 0;
    SET.sub = null;
    SET.lift = null;
    SET.confirm = null;
    SET.msg = "";
    SET.obBtn = "next";
    clearFocus();
    $("settings").classList.add("onboarding");
    $("settings").classList.add("on");
    document.documentElement.classList.add("setting");
    SET.ri = firstRow(rowsNow());
    SET.keyFocus = rowsNow()[SET.ri] ? rowsNow()[SET.ri].key : null;
    renderSettings();
  }

  function nextOnboardingStep() {
    if (SET.sec < OB_SECS.length - 1) {
      SET.sec += 1;
      SET.col = "rows";
      SET.sub = null;
      SET.msg = "";
      SET.obBtn = "next";
      SET.ri = firstRow(rowsNow());
      SET.keyFocus = rowsNow()[SET.ri] ? rowsNow()[SET.ri].key : null;
      renderSettings();
    } else {
      finishOnboarding();
    }
  }

  function prevOnboardingStep() {
    if (SET.sec > 0) {
      SET.sec -= 1;
      SET.col = "rows";
      SET.sub = null;
      SET.msg = "";
      SET.obBtn = "next";
      SET.ri = firstRow(rowsNow());
      SET.keyFocus = rowsNow()[SET.ri] ? rowsNow()[SET.ri].key : null;
      renderSettings();
    }
  }

  function finishOnboarding() {
    keep("tvhome.setup_done", true);
    saveSettings();
    savePeopleList();
    ONBOARDING = false;
    $("settings").classList.remove("onboarding");
    $("settings").classList.remove("on");
    document.documentElement.classList.remove("setting");
    zone = "grid";
    try { refreshWeather(); } catch (e) {}
    try { render(); } catch (e) {}
    try { ensureFocus(); } catch (e) {}
    restart();
  }

  function languageRows() {
    var L = window.I18N;
    var rows = L ? L.langs.map(function (l) {
      return { kind: "choice", key: "lang:" + l.id, label: l.name, lang: l.locale, checked: L.lang() === l.id, act: function () {
        ST.lang = l.id; L.set(l.id); saveSettings(); staticText();
      } };
    }) : [];
    rows.push(info("lang-note", t("lang_note")));
    return rows;
  }

  // ---- location: Open-Meteo's place search (free, no key) ----
  var geoTimer = null, geoTicket = 0;

  function locationRows() {
    var geo = SET.geo;
    var rows = [info("loc-now", t("loc_now"), WXCFG.place || t("loc_unset"), WXCFG.timezone && WXCFG.timezone !== "auto" ? WXCFG.timezone : "")];
    rows.push({ kind: "input", key: "loc-search", label: t("loc_search"), value: geo.q, act: function () {
      startTyping({ label: t("loc_type"), value: geo.q, search: true,
        live: function (q) { findPlaces(q, false); }, submit: function (q) { findPlaces(q, true); } });
    } });
    if (geo.busy) rows.push(info("loc-busy", t("loc_searching")));
    else if (geo.err) rows.push(info("loc-err", t("loc_error")));
    else if (geo.list && !geo.list.length) rows.push(info("loc-none", t("loc_none", { q: geo.q })));
    if (geo.list && geo.list.length) {
      rows.push(head(t("loc_results")));
      geo.list.forEach(function (p, i) {
        rows.push({ kind: "choice", key: "place:" + i, label: p.name, sub: p.area,
          checked: !!(ST.place && ST.place.lat === p.lat && ST.place.lon === p.lon), act: function () { choosePlace(p); } });
      });
    }
    if (ST.place) {
      rows.push({ kind: "action", key: "loc-reset", label: t("loc_reset"), act: function () {
        delete ST.place;
        WXCFG = CFG.weather || WX_DEFAULT;
        saveSettings();
        SET.msg = t("loc_set", { place: WXCFG.place || "" });
      } });
    }
    rows.push(info("loc-note", t("loc_note")));
    return rows;
  }
  function geocode(q) {
    var lang = (window.I18N ? window.I18N.lang() : "en").slice(0, 2);
    var url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language=" + lang + "&name=" + encodeURIComponent(q);
    return fetch(url).then(function (r) { if (!r.ok) throw new Error("place search " + r.status); return r.json(); }).then(function (j) {
      return (j.results || []).filter(function (r) { return isFinite(r.latitude) && isFinite(r.longitude); }).map(function (r) {
        // two decimals is about a kilometre: the town, never the house
        return { name: r.name, area: [r.admin1, r.country].filter(Boolean).join(", "),
          lat: Math.round(r.latitude * 100) / 100, lon: Math.round(r.longitude * 100) / 100, timezone: r.timezone || "auto" };
      });
    });
  }
  // While typing: a search half a second after the last key. OK: straight away, then onto the first place.
  function findPlaces(q, go) {
    clearTimeout(geoTimer);
    q = (q || "").trim();
    SET.geo.q = q;
    if (q.length < 2) {
      SET.geo = { q: q, list: null, busy: false, err: false };
      if (zone === "settings") renderSettings();
      return;
    }
    var run = function () {
      var ticket = ++geoTicket;
      SET.geo.busy = true; SET.geo.err = false;
      if (zone === "settings") renderSettings();
      geocode(q).then(function (list) {
        if (ticket !== geoTicket) return;
        SET.geo = { q: q, list: list, busy: false, err: false };
        if (go && list.length && !SET.typing) { SET.col = "rows"; SET.keyFocus = "place:0"; }
        if (zone === "settings") renderSettings();
      }, function () {
        if (ticket !== geoTicket) return;
        SET.geo = { q: q, list: null, busy: false, err: true };
        if (zone === "settings") renderSettings();
      });
    };
    if (go) run(); else geoTimer = setTimeout(run, 500);
  }
  function choosePlace(p) {
    ST.place = { name: p.name, lat: p.lat, lon: p.lon, timezone: p.timezone };
    WXCFG = { lat: p.lat, lon: p.lon, place: p.name, timezone: p.timezone };
    saveSettings();
    SET.msg = t("loc_set", { place: p.name });
    data.weather = null;
    refreshWeather();
  }

  // ---- weather ----
  function weatherRows() {
    return [
      { kind: "toggle", key: "wx-show", label: t("wx_show"), value: WX_SHOW ? t("on") : t("off"), act: function () {
        WX_SHOW = !WX_SHOW; ST.wx_show = WX_SHOW; saveSettings();
        if (WX_SHOW) refreshWeather();
        clock();
      } },
      { kind: "cycle", key: "wx-unit", label: t("wx_units"), value: WX_F ? "°F (Fahrenheit)" : "°C (Celsius)", act: function () {
        WX_F = !WX_F; ST.wx_unit = WX_F ? "f" : "c"; saveSettings(); clock();
      } },
      { kind: "cycle", key: "wx-every", label: t("wx_every"), value: t("every_min", { n: WX_EVERY }), act: function () {
        WX_EVERY = WX_EVERY === 15 ? 30 : WX_EVERY === 30 ? 60 : 15; ST.wx_every = WX_EVERY; saveSettings();
      } },
      info("wx-provider", t("wx_provider"), "Open-Meteo", t("wx_provider_note")),
      info("wx-night", t("wx_night_only"))
    ];
  }

  // ---- look & scenes ----
  function lookRows() {
    var rows = [head(t("scenes_head"))];
    var current = currentPlaceId();
    PLACES.forEach(function (p) {
      var activeImg = weatherImageForPlace(p, data.weather);
      var thumb = "backgrounds/" + p.folder + "/" + activeImg;
      rows.push({
        kind: "choice",
        key: "scene:" + p.id,
        label: t("scene_" + p.id.replace(/-/g, "_")) || p.name,
        sub: t("scene_weather_auto"),
        thumb: thumb,
        checked: current === p.id,
        act: function () {
          ST.scene = p.id;
          saveSettings();
          updateSceneBackground(true);
          renderSettings();
        }
      });
    });

    var eventsOn = ST.season_events !== false;
    rows.push({
      kind: "toggle",
      key: "season-events",
      label: t("season_events"),
      sub: t("season_events_sub"),
      value: eventsOn ? t("on") : t("off"),
      act: function () {
        ST.season_events = !eventsOn;
        saveSettings();
        updateSceneBackground(true);
        renderSettings();
      }
    });

    rows.push(head(t("clock_head")));
    rows.push({
      kind: "cycle",
      key: "clock",
      label: t("clock"),
      value: CLOCK24 ? t("clock_24") : t("clock_12"),
      act: function () {
        CLOCK24 = !CLOCK24;
        ST.clock = CLOCK24 ? "24h" : "12h";
        saveSettings();
        glassShown = "";
        clock();
      }
    });

    rows.push(head(t("startup_head")));
    var introQ = ST.intro_quality || "fast";
    var introLabels = {
      "fast": t("intro_fast"),
      "high": t("intro_high"),
      "off": t("intro_off")
    };
    rows.push({
      kind: "cycle",
      key: "intro_quality",
      label: t("intro_video"),
      sub: t("intro_video_sub"),
      value: introLabels[introQ] || introLabels["fast"],
      act: function () {
        var cycle = ["fast", "high", "off"];
        var nextIdx = (cycle.indexOf(introQ) + 1) % cycle.length;
        ST.intro_quality = cycle[nextIdx];
        saveSettings();
        renderSettings();
      }
    });
    return rows;
  }

  // ---- apps: the same order as holding OK on the dock, plus hiding ----
  function appsRows() {
    var arr = arrangeItems(), rows = [];
    var hidden = data.apps.filter(function (a) { return HIDDEN_TV.indexOf(a.package) >= 0; });
    if (!arr.items.length && !hidden.length) return [info("apps-none", t("apps_none"))];
    var row = function (it, where) {
      return { kind: "item", key: "app:" + it.package, label: it.name, value: t(where), icon: it.package,
        act: function () { cycleApp(it.package); },
        move: where === "app_hidden" ? null : function (d) { moveAppRow(it.package, d); } };
    };
    rows.push(head(t("apps_home", { n: arr.line, max: DOCK_APPS })));
    arr.items.slice(0, arr.line).forEach(function (it) { rows.push(row(it, "app_on_home")); });
    rows.push(head(t("apps_more")));
    arr.items.slice(arr.line).forEach(function (it) { rows.push(row(it, "app_in_more")); });
    if (hidden.length) {
      rows.push(head(t("apps_hidden")));
      hidden.forEach(function (a) { rows.push(row(a, "app_hidden")); });
    }
    rows.push(info("apps-note", t("apps_note")));
    if (!NIGHT) rows.push(info("apps-classic", t("apps_classic")));
    return rows;
  }
  function setHidden(list) { HIDDEN_TV = list; ST.hidden_apps = list.slice(); saveSettings(); }
  // OK on an app: home, then More apps, then hidden, then back on home (a screen skips hidden).
  function cycleApp(pkg) {
    var h = HIDDEN_TV.indexOf(pkg);
    if (h >= 0) {
      setHidden(HIDDEN_TV.filter(function (p) { return p !== pkg; }));
      materialise();
      appToHome(pkg);
    } else {
      var o = materialise() && data.order, i = o.packages.indexOf(pkg);
      if (i < 0) return;
      if (i < o.line) { o.packages.splice(i, 1); o.line -= 1; o.packages.splice(o.line, 0, pkg); persistOrder(); }
      else if (pkg.indexOf("screen:") === 0) appToHome(pkg);
      else { o.packages.splice(i, 1); setHidden(HIDDEN_TV.concat([pkg])); persistOrder(); }
    }
    SET.dirty = true;
    renderGrid();
  }
  // To the end of the dock; a full dock sends its last app behind More apps (as holding OK does).
  function appToHome(pkg) {
    var o = data.order, i = o.packages.indexOf(pkg);
    if (i >= 0) o.packages.splice(i, 1);
    if (o.line < DOCK_APPS) { o.packages.splice(o.line, 0, pkg); o.line += 1; }
    else {
      o.packages.splice(o.line - 1, 0, pkg);
      SET.msg = t("moved_more", { name: nameOf(o.packages[o.line]) });
    }
    persistOrder();
  }
  // Up and down while an app is picked up; across the line moves it between home and More apps.
  function moveAppRow(pkg, d) {
    var o = data.order, i = o.packages.indexOf(pkg), j = i + d;
    if (i < 0) return;
    var swap = function (a, b) { var x = o.packages[a]; o.packages[a] = o.packages[b]; o.packages[b] = x; };
    if (d > 0 && i === o.line - 1) o.line -= 1;
    else if (d < 0 && i === o.line) { if (o.line < DOCK_APPS) o.line += 1; else swap(i, i - 1); }
    else if (j >= 0 && j < o.packages.length) swap(i, j);
    renderGrid();
  }

  // ---- people: the same list the phone's People sheet edits ----
  function peopleRows() {
    var list = NOBODY ? [] : PEOPLE, rows = [];
    if (!list.length) rows.push(info("people-none", t("people_none")));
    list.forEach(function (p) {
      rows.push({ kind: "nav", key: "person:" + p.id, label: p.name, avatar: p, sub: p.id === who && list.length > 1 ? t("person_on") : "",
        act: function () { openPerson(p.id); }, move: function (d) { movePerson(p.id, d); } });
    });
    if (list.length < 12) {
      rows.push({ kind: "input", key: "people-add", label: t("people_add"), act: function () {
        startTyping({ label: t("people_type"), value: "", submit: addPerson });
      } });
    } else rows.push(info("people-full", t("people_full")));
    rows.push(info("people-note", t("people_note")));
    return rows;
  }
  function personById(id) { return NOBODY ? null : PEOPLE.filter(function (p) { return p.id === id; })[0] || null; }
  function openPerson(id) {
    SET.sub = { back: "person:" + id, crumb: ONBOARDING ? t("ob_profiles") : t("sec_people"),
      title: function () { var p = personById(id); return p ? p.name : ""; },
      rows: function () {
        var p = personById(id);
        if (!p) return [];
        return [
          { kind: "input", key: "p-name", label: t("person_name"), value: p.name, act: function () {
            startTyping({ label: t("person_rename_type"), value: p.name, submit: function (v) { renamePerson(id, v); } });
          } },
          { kind: "action", key: "p-remove", label: t("person_remove", { name: p.name }), danger: true, act: function () { removePerson(id); } },
          { kind: "action", key: "p-back", label: t("ob_prev"), act: function () { if (SET.sub) { SET.keyFocus = SET.sub.back; SET.sub = null; renderSettings(); } } }
        ];
      } };
    SET.keyFocus = "p-name";
  }
  function cleanName(v) { return String(v || "").replace(/\s+/g, " ").trim().slice(0, 40); }
  function newPersonId(name, list) {
    var base = name.toLowerCase();
    try { base = base.normalize("NFD").replace(/[̀-ͯ]/g, ""); } catch (e) {}
    base = base.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 24) || "person";
    if (["both", "all", "everyone", "shared", "together"].indexOf(base) >= 0) base += "-1";
    var id = base, n = 2;
    while (list.some(function (p) { return p.id.toLowerCase() === id; })) id = base + "-" + n++;
    return id;
  }
  // Kept on this TV (pending) and handed to the bridge, which keeps it in bridge/data/people.json.
  function savePeopleList() {
    var list = NOBODY ? [] : PEOPLE.map(function (p) {
      var o = { id: p.id, name: p.name };
      ["colour", "kid", "guest"].forEach(function (k) { if (p[k] != null) o[k] = p[k]; });
      return o;
    });
    keep("tvhome.people.list", { people: list, pending: true });
    keep("tvhome.people.order", { ids: list.map(function (p) { return p.id; }), pending: false });
    SET.dirty = true;
    renderShelf();
    if (OFFLINE || data.bridge === false) return;
    api("POST", "/api/people", { people: list })
      .then(function (r) { keep("tvhome.people.list", { people: r.people || list, pending: false }); }, function () {});
  }
  function addPerson(v) {
    var name = cleanName(v), list = NOBODY ? [] : PEOPLE.slice();
    if (!name) return;
    if (list.length >= 12) { SET.msg = t("people_full"); return; }
    var id = newPersonId(name, list);
    list.push({ id: id, name: name });
    setPeople(list);
    savePeopleList();
    SET.keyFocus = "person:" + id;
  }
  function renamePerson(id, v) {
    var name = cleanName(v), p = personById(id);
    if (!name || !p) return;
    p.name = name;
    savePeopleList();
  }
  function removePerson(id) {
    if (NOBODY) return;
    setPeople(PEOPLE.filter(function (p) { return p.id !== id; }));
    savePeopleList();
    SET.sub = null;
    SET.keyFocus = "people-add";
  }
  function movePerson(id, d) {
    if (NOBODY) return;
    var i = PEOPLE.map(function (p) { return p.id; }).indexOf(id), j = i + d;
    if (i < 0 || j < 0 || j >= PEOPLE.length) return;
    var x = PEOPLE[i]; PEOPLE[i] = PEOPLE[j]; PEOPLE[j] = x;
    renderPeople();
  }

  // ---- quick buttons & remote shortcuts ----
  function quickRows() {
    var rows = [];

    // --- Section 1: Raccourcis de la télécommande ---
    rows.push(head(t("quick_remote_head")));

    // 1. Bouton Watchlist (Favoris)
    var watchName = watchlistAppName();
    rows.push({
      kind: "nav",
      key: "quick:watchlist_app",
      label: t("quick_watchlist_btn"),
      value: watchName || t("quick_app_none"),
      sub: t("quick_watchlist_sub"),
      icon: ST.quick_watchlist_pkg || null,
      act: function () {
        openAppPicker("watchlist");
      }
    });

    if (watchName) {
      rows.push({
        kind: "action",
        key: "quick:open_watchlist",
        label: t("quick_app_open", { name: watchName }),
        act: function () {
          openWatchlist();
        }
      });
    }

    // 2. Bouton Chaînes TV (Live Channels)
    var liveName = liveAppName();
    rows.push({
      kind: "nav",
      key: "quick:live_app",
      label: t("quick_live_btn"),
      value: liveName || t("quick_app_none"),
      sub: t("quick_live_sub"),
      icon: ST.quick_live_pkg || null,
      act: function () {
        openAppPicker("live");
      }
    });

    if (liveName) {
      rows.push({
        kind: "action",
        key: "quick:open_live",
        label: t("quick_app_open", { name: liveName }),
        act: function () {
          openLiveChannels();
        }
      });
    }

    return rows;
  }
  function quickNeeds(q) {
    if (q === "watchlist") return ST.quick_watchlist_pkg ? watchlistAppName() : t("quick_app_none");
    if (q === "live") return ST.quick_live_pkg ? liveAppName() : t("quick_app_none");
    if (q === "sleep" && OFFLINE) return t("quick_needs_bridge");
    if (q === "picture" && (OFFLINE || !picShown)) return t("quick_needs_picture");
    if (q === "input" && OFFLINE && !nativeInputs()) return t("quick_needs_inputs");
    return "";
  }
  function setQuick(list) { QUICK = list; ST.quick = list.slice(); saveSettings(); renderQuick(); }
  function toggleQuick(q) {
    var had = QUICK.indexOf(q) >= 0;
    setQuick(had ? QUICK.filter(function (x) { return x !== q; }) : QUICK.concat([q]));
    if (!had && !OFFLINE) refreshQuick().then(function () { if (zone === "settings") renderSettings(); });
  }
  function moveQuick(q, d) {
    var l = QUICK.slice(), i = l.indexOf(q), j = i + d;
    if (i < 0 || j < 0 || j >= l.length) return;
    l[i] = l[j]; l[j] = q;
    setQuick(l);
  }

  // ---- about ----
  function aboutRows() {
    var ver = "";
    try { ver = TV && TV.version ? TV.version() : ""; } catch (e) {}
    var run = ALONE ? "run_alone" : OFFLINE ? "run_off" : data.bridge === false ? "run_quiet" : "run_bridge";
    return [
      info("ver", t("about_version"), ver || t("about_web")),
      info("code", t("about_code"), REPO_URL),
      info("run", t("about_running"), t(run)),
      info("kept", t("about_kept"), t(OFFLINE || data.bridge === false ? "kept_tv" : "kept_both")),
      { kind: "action", key: "reset", label: t("about_reset"), sub: t("about_reset_sub"),
        act: resetSettings }
    ];
  }
  // Complete reset of Talo TV: clears all storage, local order, people, and restores factory defaults.
  function resetSettings() {
    try {
      localStorage.clear();
      sessionStorage.clear();
    } catch (e) {}
    try {
      if (TV && TV.resetAll) {
        TV.resetAll();
      } else if (TV && TV.keep) {
        var keys = [
          "tvhome.setup_done", "tvhome.settings", "tvhome.order.tv", "tvhome.apps.seen", "tvhome.apps.new",
          "tvhome.people.order", "tvhome.people.list", "tvhome.cache.v2", ST_KEY
        ];
        keys.forEach(function (k) {
          try { TV.keep(PFX + k, ""); } catch (err) {}
          try { TV.keep(k, ""); } catch (err) {}
        });
      }
    } catch (e) {}
    try {
      keep("tvhome.setup_done", "");
      keep("tvhome.settings", "");
      keep("tvhome.order.tv", "");
      keep("tvhome.apps.seen", "");
      keep("tvhome.apps.new", "");
      keep("tvhome.people.order", "");
      keep("tvhome.people.list", "");
      keep("tvhome.cache.v2", "");
      keep(ST_KEY, "");
    } catch (e) {}
    ST = {};
    PEOPLE = [];
    setPeople([]);
    SET.confirm = null;
    SET.msg = "";
    var sysLang = window.I18N && window.I18N.detect ? window.I18N.detect() : "en";
    ST.lang = sysLang;
    if (window.I18N) window.I18N.set(sysLang);
    ST.wx_unit = "c";
    WX_F = false;
    ST.clock = "24h";
    CLOCK24 = true;
    staticText();
    openOnboarding();
  }

  // ---- typing a place, a name or a photo address ----
  // One text box above the rows. OK on a typing row focuses it, so the TV's keyboard comes up; OK
  // (Enter) is done, Back cancels, up and down leave it. The phone remote's Type also lands here.
  function isTyping() { return !!SET.typing; }
  function startTyping(spec) {
    SET.typing = spec;
    var inp = $("set-input");
    $("set-type-label").textContent = spec.label;
    inp.value = spec.value || "";
    inp.type = spec.url ? "url" : "text";
    inp.setAttribute("enterkeyhint", spec.search ? "search" : "done");
    inp.setAttribute("autocapitalize", spec.url ? "off" : "words");
    $("set-type").classList.add("on");
    renderSettings();
    try { inp.focus(); inp.setSelectionRange(inp.value.length, inp.value.length); } catch (e) {}
  }
  function stopTyping() {
    SET.typing = null;
    $("set-type").classList.remove("on");
    try { $("set-input").blur(); } catch (e) {}
    renderSettings();
  }
  function typingKey(e) {
    if (!SET.typing || zone !== "settings") return false;
    var inp = $("set-input");
    if (document.activeElement !== inp) { stopTyping(); return false; }   // the box lost the keys
    if (e.key === "Enter" || e.keyCode === 13) {
      // Nothing typed yet: this OK is the remote asking for the TV's keyboard (a WebView raises it
      // on OK in a text box), so it is left to do that rather than closing the box.
      if (inp.value.trim() === String(SET.typing.value || "").trim()) return true;
      e.preventDefault();
      var spec = SET.typing, v = inp.value;
      stopTyping();
      spec.submit(v);
      if (zone === "settings") renderSettings();
      return true;
    }
    if (e.key === "Escape" || e.key === "Esc") { e.preventDefault(); stopTyping(); return true; }
    var up = e.key === "ArrowUp" || e.keyCode === 38, down = e.key === "ArrowDown" || e.keyCode === 40;
    if (up || down) { e.preventDefault(); stopTyping(); setMove(0, up ? -1 : 1); return true; }
    return true;   // everything else is typing (Backspace deletes here, it does not go back)
  }

  // ---- keys on the settings screen ----
  function enterRows() {
    var rows = rowsNow(), i = firstRow(rows);
    if (i < 0) return;
    SET.col = "rows"; SET.ri = i; SET.keyFocus = rows[i].key;
  }
  function leaveRows() {
    if (SET.sub) { SET.keyFocus = SET.sub.back; SET.sub = null; return; }
    SET.col = "secs"; SET.keyFocus = null;
  }
  function setMove(dx, dy) {
    var rows = rowsNow();
    if (SET.lift) {
      if (dy) {
        var r = rows[indexOfKey(rows, SET.lift)];
        if (r && r.move) r.move(dy);
        SET.keyFocus = SET.lift;
      }
      renderSettings();
      return;
    }
    SET.confirm = null;
    if (ONBOARDING) {
      var cur = rows[SET.ri];
      if (cur && cur.kind === "ob-nav") {
        if (dx < 0 && SET.sec > 0) {
          SET.obBtn = "prev";
          renderSettings();
          return;
        }
        if (dx > 0) {
          SET.obBtn = "next";
          renderSettings();
          return;
        }
      }
      if (dy) {
        var nextRi = stepRow(rows, SET.ri, dy);
        if (nextRi !== SET.ri) {
          SET.ri = nextRi;
          SET.keyFocus = rows[SET.ri] ? rows[SET.ri].key : null;
          if (rows[SET.ri] && rows[SET.ri].kind === "ob-nav" && !SET.obBtn) {
            SET.obBtn = "next";
          }
        }
      }
      renderSettings();
      return;
    }
    var maxSecs = SECS.length;
    if (SET.col === "secs") {
      if (dy) {
        var n = Math.max(0, Math.min(maxSecs - 1, SET.sec + dy));
        if (n !== SET.sec) { SET.sec = n; SET.sub = null; SET.msg = ""; SET.keyFocus = null; SET.ri = 0; }
      } else if (dx > 0) enterRows();
    } else if (dy) {
      SET.ri = stepRow(rows, SET.ri, dy);
      SET.keyFocus = rows[SET.ri] ? rows[SET.ri].key : null;
    } else if (dx < 0) leaveRows();
    renderSettings();
  }
  function setActivate() {
    if (SET.lift) { dropRow(); return; }
    if (SET.col === "secs") { enterRows(); renderSettings(); return; }
    if (SET.sub && Date.now() - subOpenedAt < 250) return;
    var rows = rowsNow(), r = rows[SET.ri];
    if (ONBOARDING && r && r.kind === "ob-nav") {
      if (SET.obBtn === "prev") {
        prevOnboardingStep();
      } else {
        if (SET.sec < OB_SECS.length - 1) nextOnboardingStep();
        else finishOnboarding();
      }
      return;
    }
    if (!focusable(r) || !r.act) return;
    if (r.danger && SET.confirm !== r.key) { SET.confirm = r.key; renderSettings(); return; }
    SET.confirm = null;
    SET.keyFocus = r.key;
    invalidateSettingsCache();
    r.act();
    if (zone === "settings" && !SET.typing) renderSettings();
  }
  function setHold() {
    if (SET.lift) { dropRow(); return true; }
    if (SET.col !== "rows") return false;
    var r = rowsNow()[SET.ri];
    if (!r || !r.move) return false;
    SET.confirm = null;
    if (r.key.indexOf("app:") === 0) materialise();
    SET.lift = r.key;
    renderSettings();
    return true;
  }
  function dropRow() {
    var k = SET.lift || "";
    SET.lift = null;
    if (k.indexOf("app:") === 0) { persistOrder(); SET.dirty = true; }
    if (k.indexOf("person:") === 0) savePeopleList();
    if (zone === "settings") renderSettings();
  }
  function setBack() {
    if (SET.typing) { stopTyping(); return; }
    if (SET.lift) { dropRow(); return; }
    SET.confirm = null;
    if (SET.sub) {
      SET.keyFocus = SET.sub.back;
      SET.sub = null;
      renderSettings();
      return;
    }
    if (ONBOARDING) {
      if (SET.sec > 0) {
        prevOnboardingStep();
      }
      return;
    }
    if (SET.col === "rows") { leaveRows(); renderSettings(); return; }
    closeSettings();
  }

  // ---- drawing it ----
  function rowHTML(r, i) {
    if (r.kind === "spacer") return '<div class="sspacer"></div>';
    if (r.kind === "ob-nav") {
      var isLast = SET.sec === OB_SECS.length - 1;
      var showPrev = SET.sec > 0;
      var isRowFocused = SET.col === "rows" && i === SET.ri;
      var prevFocus = isRowFocused && SET.obBtn === "prev";
      var nextFocus = isRowFocused && (SET.obBtn === "next" || !SET.obBtn);
      var prevLabel = t("ob_prev");
      var nextLabel = isLast ? t("ob_finish") : t("ob_next");
      var prevSvg = '<svg viewBox="0 0 24 24" style="transform:rotate(180deg)"><path d="M9.5 5.5 16 12l-6.5 6.5"/></svg>';
      var nextSvg = isLast
        ? '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'
        : '<svg viewBox="0 0 24 24"><path d="M9.5 5.5 16 12l-6.5 6.5"/></svg>';

      var html = '<div class="ob-nav-pair">';
      if (showPrev) {
        html += '<button type="button" class="ob-btn ob-btn-prev' + (prevFocus ? ' focus' : '') + '">' + prevSvg + '<span>' + esc(prevLabel) + '</span></button>';
      }
      html += '<button type="button" class="ob-btn ob-btn-next' + (nextFocus ? ' focus' : '') + '"><span>' + esc(nextLabel) + '</span>' + nextSvg + '</button>';
      html += '</div>';
      return html;
    }
    if (r.kind === "head") return '<div class="shead">' + esc(r.label) + "</div>";
    var f = SET.col === "rows" && i === SET.ri, lifted = SET.lift === r.key;
    var cls = "srow k-" + r.kind + (f ? " focus" : "") + (lifted ? " lifted" : "") + (r.danger ? " danger" : "");
    var lead = "";
    if (r.avatar) lead = avatarHTML(r.avatar);
    else if (r.icon && ICONS[r.icon]) lead = '<span class="sicon none">' + ICONS[r.icon] + "</span>";
    else if (r.icon && iconUrl(r.icon)) lead = '<img class="sicon" src="' + esc(iconUrl(r.icon)) + '" alt="" onerror="this.remove()">';
    else if (r.kind === "item") lead = '<span class="sicon none">' + (r.icon.indexOf("screen:") === 0 ? ICONS.screen : esc((r.label || "?").charAt(0).toUpperCase())) + "</span>";
    else if (r.thumb) lead = '<img class="sthumb" src="' + esc(r.thumb) + '" alt="" decoding="async" onerror="this.remove()">';
    var label = SET.confirm === r.key ? r.confirmText || t("confirm_remove") : r.label;
    var tail = "";
    if (lifted) tail = '<i class="smark">' + ICONS.updown + "</i>";
    else if (r.kind === "choice") tail = r.checked ? '<i class="smark">' + ICONS.check + "</i>" : "";
    else if (r.kind === "check") tail = '<i class="sbox' + (r.checked ? " on" : "") + '">' + (r.checked ? ICONS.check : "") + "</i>";
    else if (r.kind === "nav" || r.kind === "input" || r.kind === "action") tail = '<i class="smark dim">' + ICONS.chevron + "</i>";
    var value = r.value != null && r.value !== "" ? '<span class="sv">' + esc(r.value) + "</span>" : "";
    var sub = r.sub ? "<small>" + esc(r.sub) + "</small>" : "";
    return '<div class="' + cls + '"' + (r.lang ? ' lang="' + esc(r.lang) + '"' : "") + ">" + lead +
      '<span class="sl"><b>' + esc(label) + "</b>" + sub + "</span>" + value + tail + "</div>";
  }
  var lastRenderedSecKey = "";
  function invalidateSettingsCache() {
    lastRenderedSecKey = "";
  }
  function renderSettings() {
    var rows = rowsNow();
    if (SET.sub && !rows.length) { SET.sub = null; rows = rowsNow(); }
    if (ONBOARDING) {
      SET.col = "rows";
      $("settings").classList.add("onboarding");
    } else {
      $("settings").classList.remove("onboarding");
    }
    if (SET.col === "rows") {
      var k = SET.keyFocus ? indexOfKey(rows, SET.keyFocus) : -1;
      if (k >= 0 && focusable(rows[k])) SET.ri = k;
      else {
        SET.ri = Math.max(0, Math.min(SET.ri, rows.length - 1));
        if (!focusable(rows[SET.ri])) { var back = stepRow(rows, SET.ri, -1); SET.ri = back !== SET.ri ? back : stepRow(rows, SET.ri, 1); }
      }
      if (focusable(rows[SET.ri])) SET.keyFocus = rows[SET.ri].key;
      else if (!ONBOARDING) SET.col = "secs";
    }
    var isPermSec = (!ONBOARDING && SECS[SET.sec] && SECS[SET.sec][0] === "permissions") || (ONBOARDING && OB_SECS[SET.sec] && OB_SECS[SET.sec][0] === "ob_perms");
    var pSig = isPermSec ? JSON.stringify(getPermissionsStatus()) : "";
    var isShieldSec = !ONBOARDING && SECS[SET.sec] && SECS[SET.sec][0] === "shield";
    var sSig = isShieldSec ? (JSON.stringify(ST.shield_disabled || []) + ":" + (ST.shield_enabled !== false) + ":" + (ST.shield_mode || "") + ":" + (ST.shield_debloat_applied ? "1" : "0") + ":" + (TV && TV.isShieldVpnRunning ? TV.isShieldVpnRunning() : "") + ":" + (TV && TV.getPrivateDnsInfo ? TV.getPrivateDnsInfo() : "")) : "";
    var currentSecKey = (ONBOARDING ? "ob:" : "sec:") + SET.sec + ":" + (SET.sub ? (SET.sub.back || "sub") : "root") + ":" + (SET.lift || "") + ":" + (SET.confirm || "") + ":" + rows.length + ":" + pSig + ":" + sSig;
    var box = $("set-rows");
    if (currentSecKey !== lastRenderedSecKey || box.children.length !== rows.length) {
      lastRenderedSecKey = currentSecKey;
      $("set-title").textContent = ONBOARDING ? t("setup_title") : t("set_title");
      var secsList = ONBOARDING ? OB_SECS : SECS;
      $("set-secs").innerHTML = secsList.map(function (sc, i) {
        var cls = "ssec" + (i === SET.sec ? (SET.col === "secs" ? " focus" : " on") : "");
        var label = ONBOARDING ? t(sc[0]) : t("sec_" + sc[0]);
        return '<div class="' + cls + '"><i>' + (ICONS[sc[1]] || "") + "</i><span>" + esc(label) + "</span></div>";
      }).join("");
      $("set-crumb").textContent = SET.sub ? SET.sub.crumb : (ONBOARDING ? (SET.sec + 1) + " / " + OB_SECS.length : "");
      $("set-head").textContent = SET.sub ? SET.sub.title() : (ONBOARDING ? t(OB_SECS[SET.sec][0] + "_head") : t("sec_" + SECS[SET.sec][0]));
      box.innerHTML = rows.map(rowHTML).join("");
    } else {
      var ssecs = $("set-secs").children;
      for (var sIdx = 0; sIdx < ssecs.length; sIdx++) {
        var isSec = (sIdx === SET.sec);
        var scls = "ssec" + (isSec ? (SET.col === "secs" ? " focus" : " on") : "");
        if (ssecs[sIdx].className !== scls) ssecs[sIdx].className = scls;
      }
      var rowsEls = box.children;
      for (var rIdx = 0; rIdx < rowsEls.length; rIdx++) {
        var isFocused = (SET.col === "rows" && rIdx === SET.ri);
        if (rowsEls[rIdx].classList.contains("focus") !== isFocused) {
          rowsEls[rIdx].classList.toggle("focus", isFocused);
        }
      }
    }
    $("set-msg").textContent = SET.msg || "";
    var cur = rows[SET.ri];
    $("set-hint").textContent = SET.typing ? t("hint_typing") : SET.lift ? t("hint_lift") : SET.col === "secs" ? t("hint_secs") :
      cur && cur.move ? t("hint_rows_move") : t("hint_rows");
    var f = box.querySelector(".srow.focus, .ob-btn.focus");
    if (!f) { box.scrollTop = 0; return; }
    var top = f.offsetTop, bottom = top + f.offsetHeight, view = box.clientHeight, edge = view / 4;
    if (top - edge < box.scrollTop) box.scrollTop = Math.max(0, top - edge);
    else if (bottom + edge > box.scrollTop + view) box.scrollTop = bottom + edge - view;
  }
  function initSettings() {
    $("set-input").addEventListener("input", function () { if (SET.typing && SET.typing.live) SET.typing.live(this.value); });
    $("set-secs").addEventListener("click", function (e) {
      var item = e.target.closest(".ssec");
      if (!item) return;
      var idx = Array.prototype.indexOf.call($("set-secs").children, item);
      if (idx >= 0) {
        SET.sec = idx;
        SET.col = "rows";
        SET.sub = null;
        SET.msg = "";
        SET.keyFocus = null;
        SET.ri = firstRow(rowsNow());
        renderSettings();
      }
    });
    $("set-rows").addEventListener("click", function (e) {
      if (Date.now() - subOpenedAt < 350) return;
      var prevBtn = e.target.closest(".ob-btn-prev");
      if (prevBtn) {
        prevOnboardingStep();
        return;
      }
      var nextBtn = e.target.closest(".ob-btn-next");
      if (nextBtn) {
        if (SET.sec < OB_SECS.length - 1) nextOnboardingStep();
        else finishOnboarding();
        return;
      }
      var item = e.target.closest(".srow");
      if (!item) return;
      var rows = rowsNow();
      var srows = Array.prototype.slice.call($("set-rows").querySelectorAll(".srow"));
      var idx = srows.indexOf(item);
      var fCount = -1;
      for (var r = 0; r < rows.length; r++) {
        if (rows[r].kind !== "head" && rows[r].kind !== "spacer" && rows[r].kind !== "ob-nav") {
          fCount++;
          if (fCount === idx) {
            SET.col = "rows";
            SET.ri = r;
            SET.keyFocus = rows[r].key;
            setActivate();
            break;
          }
        }
      }
    });
  }

  // ---------------- keys: any normal TV remote, keyboard or game controller ----------------
  // The full table is in docs/REMOTES.md. The launcher also turns BUTTON_A, BUTTON_SELECT and
  // NUMPAD_ENTER into OK and BUTTON_B and ESCAPE into Back before the page sees them. Any other key
  // (media, numbers, colour buttons, voice, app buttons) is left alone so Android still gets it.
  var OK_KEYS = ["Enter", " ", "Select", "Accept", "NumpadEnter"];
  var BACK_KEYS = ["Escape", "Esc", "Backspace", "GoBack", "BrowserBack"];
  var ARROWS = { ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0],
    Up: [0, -1], Down: [0, 1], Left: [-1, 0], Right: [1, 0] };
  var ARROW_CODES = { 38: [0, -1], 40: [0, 1], 37: [-1, 0], 39: [1, 0], 19: [0, -1], 20: [0, 1], 21: [-1, 0], 22: [1, 0] };
  function isOk(e) { return OK_KEYS.indexOf(e.key) >= 0 || e.keyCode === 13 || e.keyCode === 23 || e.keyCode === 32; }
  function isBack(e) { return BACK_KEYS.indexOf(e.key) >= 0 || e.keyCode === 27 || e.keyCode === 8 || e.keyCode === 10009; }   // 10009: Samsung
  function isMenu(e) { return e.key === "ContextMenu" || e.key === "Menu" || e.keyCode === 93 || e.keyCode === 82; }
  function arrowOf(e) { return ARROWS[e.key] || (e.key === "Unidentified" || !e.key ? ARROW_CODES[e.keyCode] : null) || null; }

  // Focus is never lost: after any key, if nothing shows focus, the first dock app gets it.
  function ensureFocus() {
    if (document.querySelector(".focus")) return;
    zone = "grid"; g = 0;
    paintFocus();
  }

  // OK acts when it is let go, so holding it can pick things up instead (tvOS does the same).
  // Works for remotes that repeat the key while it is held and for ones that only send the press
  // and, later, the release. One press is one action: a pick-up never also opens the app.
  var okDown = false, okHeld = false, okTimer = null;
  function okReset() { okDown = false; okHeld = false; clearTimeout(okTimer); }
  document.addEventListener("keydown", function (e) {
    if (typingKey(e)) return;   // typing a place or a name: the keys belong to the text box
    if (isOk(e)) {
      e.preventDefault();
      if (e.repeat || okDown) return;   // a held key repeating: the timer is already running
      okDown = true; okHeld = false;
      okTimer = setTimeout(function () { okHeld = true; if (!hold()) activate(); ensureFocus(); }, HOLD_MS);
      return;
    }
    var arrow = arrowOf(e);
    if (arrow) move(arrow[0], arrow[1]);
    else if (isBack(e)) goHome();
    else if (isMenu(e)) { if (!e.repeat) hold(); }   // optional: many remotes have no Menu key
    else { ensureFocus(); return; }                   // anything else is not ours: Android gets it
    e.preventDefault();
    ensureFocus();
  });
  document.addEventListener("keyup", function (e) {
    if (!isOk(e) || isTyping()) return;
    e.preventDefault();
    if (!okDown) return;
    var held = okHeld;
    okReset();
    if (!held) activate();
    ensureFocus();
  });
  window.addEventListener("blur", okReset);

  // ---------------- data ----------------
  function nativeApps() {
    var list;
    try { list = JSON.parse(TV.apps()); } catch (e) { return null; }
    return list.map(function (a) {
      return { kind: "app", name: RENAME[a.package] ? t(RENAME[a.package]) : a.label, package: a.package,
        banner: TV.bannerUrl ? TV.bannerUrl(a.package) : "http://tvos.invalid/banner/" + a.package };   // the launcher makes one when the app has none
    });
  }
  function sortApps(list) {
    var rank = function (p) {
      var i = APP_ORDER.indexOf(p);
      if (i >= 0) return i;
      i = APP_LAST.indexOf(p);
      return i >= 0 ? 2000 + i : 1000;
    };
    return list.filter(function (a) { return HIDDEN.indexOf(a.package) < 0; })
      .sort(function (a, b) { return rank(a.package) - rank(b.package) || a.name.localeCompare(b.name); });
  }

  function render() {
    renderShelf();
    renderGrid();
    if (!touched) startFocus();
    paintFocus();
  }

  // The bridge's people list (the phone edits it). An order made on this TV while the bridge was
  // away is handed over first.
  function bridgePeople(j) {
    var list = j.people || [];
    var lo = kept("tvhome.people.order"), lst = kept("tvhome.people.list");
    if (lst && lst.pending && Array.isArray(lst.people)) {
      // people added, renamed or removed in Home settings while the computer was off
      list = lst.people;
      api("POST", "/api/people", { people: lst.people })
        .then(function (r) { keep("tvhome.people.list", { people: r.people || lst.people, pending: false }); }, function () {});
    } else if (lo && lo.pending && lo.ids) {
      list = localPeopleOrder(list, true);
      api("POST", "/api/people", { order: lo.ids })
        .then(function () { keep("tvhome.people.order", { ids: lo.ids, pending: false }); }, function () {});
    }
    if (zone !== "pmenu") setPeople(list);
  }
  function bridgeOrder(j) {
    data.order = { saved: !!j.saved, line: j.line, packages: (j.apps || []).map(function (a) { return a.package; }) };
    var lo = kept("tvhome.order.tv");
    if (lo && lo.pending && lo.packages) {
      data.order = { saved: true, line: lo.line, packages: lo.packages };
      api("POST", "/api/app-order", { order: lo.packages, line: lo.line })
        .then(function () { lo.pending = false; keep("tvhome.order.tv", lo); }, function () {});
    }
  }

  function refresh() {
    // never reshuffle under something being moved, or under the settings screen
    if (lift || zone === "pmenu" || zone === "settings" || zone === "gear" || zone === "smart") return Promise.resolve();
    if (OFFLINE) {
      // only the TV's own apps, in the order last saved; no shows, no Screens
      if (TV) data.apps = sortApps(nativeApps() || []);
      else if (window.MOCK_APPS) data.apps = sortApps(window.MOCK_APPS.slice());
      data.bridge = false;
      // Keep watching with no computer: Android's own Watch Next list, the same for everyone in the house
      if (TV && TV.watchNext) {
        var wn = [];
        try { wn = JSON.parse(TV.watchNext()) || []; } catch (e) {}
        PEOPLE.forEach(function (p) { data.showsBy[p.id] = wn; });
      }
      detectNew();
      status(ALONE ? "" : t("computer_off"), true);
      render();
      return Promise.resolve();
    }
    var people = api("GET", "/api/people").then(bridgePeople, function () {});
    var jobs = [
      people.then(function () {
        return Promise.all(PEOPLE.map(function (p) {
          return api("GET", "/api/continue?who=" + p.id, null, 9000).then(function (j) { data.showsBy[p.id] = (j.items || []).slice(0, 12); });
        }));
      }),
      api("GET", "/api/screens").then(function (j) { data.screens = j.screens || []; }),
      // whoever the phone remote or this TV last set as watching
      api("GET", "/api/who").then(function (j) {
        return people.then(function () { if (PEOPLE.some(function (p) { return p.id === j.who; }) && !touched) { who = j.who; } });
      }),
      (TV || PREVIEW || MOCK) ? Promise.resolve() : api("GET", "/api/apps").then(function (j) { data.bridgeApps = j.apps || []; }),
      // the order arranged on the phone (or with this TV's remote)
      NIGHT ? api("GET", "/api/app-order").then(bridgeOrder) : Promise.resolve(),
      // Home settings: the bridge keeps a copy, so the phone and this TV agree
      syncSettings()
    ];
    return Promise.all(jobs.map(function (p) { return p.then(function () { return true; }, function () { return false; }); }))
      .then(function (ok) {
        if (lift || zone === "pmenu" || zone === "settings" || zone === "gear" || zone === "smart") return;
        data.bridge = ok[0] || ok[2];
        var apps = TV ? nativeApps() : (window.MOCK_APPS ? window.MOCK_APPS.slice() : null);
        if (!apps) apps = (data.bridgeApps || []).filter(function (a) { return a.installed !== false && a.id !== "home"; })
          .map(function (a) { return { kind: "app", id: a.id, name: a.name, package: a.package, colour: a.colour }; });
        data.apps = sortApps(apps);
        if (!data.bridge) {   // no bridge: the order this TV keeps itself
          var lo = kept("tvhome.order.tv");
          if (lo && lo.packages) data.order = { saved: true, line: lo.line, packages: lo.packages };
        }
        reportApps();
        detectNew();
        status(data.bridge ? "" : t("computer_quiet"), true);
        if (data.bridge) save();
        render();
        clearTimeout(retryTimer);
        if (!ok[0] && !ok[1]) { retryMs = Math.min(retryMs * 2, 60000); retryTimer = setTimeout(refresh, retryMs); }
        else retryMs = 2500;
      });
  }

  function forceHome() {
    if (zone === "settings") {
      SET.sub = null;
      closeSettings();
    }
    if (zone === "gear") closeGear();
    if (zone === "smart") closeSmart();
    if (lift) dropLift();
    if (zone === "pmenu") { plift = null; closePop(); }
    if (zone === "menu") closeMenu();
    if (expanded) {
      expanded = false;
      if ($("apps-section")) $("apps-section").scrollTop = 0;
      renderGrid();
    }
    window.scrollTo(0, 0);
    startFocus();
    paintFocus();
    refresh();
  }

  // the native side calls these
  // Home on the remote also re-reads the lists, so an order arranged on the phone shows straight away
  window.tv = {
    back: goHome,
    home: forceHome,
    toggleGear: function () {
      if (zone === "gear") {
        closeGear();
        paintFocus();
      } else {
        if (zone === "settings") closeSettings();
        openGear();
      }
    },
    openGear: openGear,
    openWatchlist: openWatchlist,
    openLive: openLiveChannels,
    openSettings: openSettings,
    resume: function () {
      document.body.classList.remove("system-sheet-open");
      invalidateSettingsCache();
      if (zone === "settings") renderSettings();
      okReset();
      refresh();
      if (!OFFLINE) refreshQuick();
      refreshWeather();
    },
    renderSettings: function () {
      invalidateSettingsCache();
      if (zone === "settings") renderSettings();
    },
    onShieldVpnStateChange: function (running) {
      invalidateSettingsCache();
      if (zone === "settings") renderSettings();
    },
    photosAdded: function () {}
  };

  window.onShieldVpnStateChange = function (running) {
    invalidateSettingsCache();
    if (zone === "settings") renderSettings();
  };

  function start() {
    readConfig();
    staticText();
    initSettings();
    mark("app-start");
    clock();
    setInterval(clock, 15000);
    buildScenes();
    if (document.fonts) document.fonts.load("300 10px Antonio").then(function () { glassShown = ""; clock(); });
    refreshWeather();
    setInterval(refreshWeather, WX_EVERY * 60000);
    var cached = load();
    if (cached) { data.showsBy = cached.showsBy || data.showsBy; data.screens = cached.screens || []; data.order = cached.order || null; }
    setPeople(PEOPLE);
    if (OFFLINE) {
      var lo = kept("tvhome.order.tv");
      if (lo && lo.packages) data.order = { saved: true, line: lo.line, packages: lo.packages };
    }
    if (TV) data.apps = sortApps(nativeApps() || []);
    if (cached || TV) { render(); mark("home-painted-from-cache"); }
    renderQuick();
    if (!OFFLINE) refreshQuick();
    $("grid").addEventListener("click", function (e) {
      var tile = e.target.closest(".tile");
      if (!tile) return;
      var idx = gridEls.indexOf(tile);
      if (idx >= 0) {
        zone = "grid";
        g = idx;
        paintFocus();
        activate();
      }
    });
    document.addEventListener("click", function (e) {
      if (zone === "gear" && !e.target.closest("#gear-pop") && !e.target.closest(".gear-tool")) {
        closeGear();
        paintFocus();
      }
    });
    $("qbtns").addEventListener("click", function (e) {
      var btn = e.target.closest(".qbtn");
      if (!btn) return;
      var idx = quickEls.indexOf(btn);
      if (idx >= 0) {
        zone = "quick";
        qi = idx;
        paintFocus();
        quickAction();
      }
    });

    var setupDone = !!kept("tvhome.setup_done");
    if (!setupDone) {
      var sysLang = window.I18N && window.I18N.detect ? window.I18N.detect() : "en";
      if (!ST.lang) {
        ST.lang = sysLang;
        if (window.I18N) window.I18N.set(sysLang);
      }
      if (!ST.wx_unit) {
        ST.wx_unit = "c";
        WX_F = false;
      }
      if (!ST.clock) {
        ST.clock = "24h";
        CLOCK24 = true;
      }
      staticText();
      openOnboarding();
    }

    refresh().then(function () { mark("home-painted-live"); });
  }

  if (MOCK || PREVIEW) {
    var sc = document.createElement("script");
    sc.src = "mock.js";
    sc.onload = start;
    document.head.appendChild(sc);
  } else {
    start();
  }
})();

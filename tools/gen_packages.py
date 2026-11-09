#!/usr/bin/env python3
"""Generates app/src/main/assets/packages.json. Edit this file, then run it."""
import json, os

CYT = "https://github.com/Gathin23/cleanyourtv/blob/main/examples/xiaomi-mitv-aesp0/debloat.txt"
EST = "https://github.com/yositapjani/ExtremeSlimTV/blob/main/profiles/mitv-aesp0-mi-tv-stick-fhd.txt"
RMM = "https://github.com/ricardomm7/xiaomi-tv-stick-aquaman-debloat/blob/main/scripts/debloat.sh"
INV = "https://github.com/ricardomm7/xiaomi-tv-stick-aquaman-debloat/blob/main/data/baseline_packages.json"
BOOT = "https://github.com/Gathin23/cleanyourtv/issues/884"

P = []
def add(pkg, level, label, en, tr, sources=(), aggressive=False):
    P.append(dict(packageName=pkg, level=level, label=label, descriptionEn=en, descriptionTr=tr,
                  sources=list(sources), aggressive=aggressive))

S, C, X = "SAFE", "CAUTION", "PROTECTED"

# ---------------- SAFE: disabled on verified MDZ-24-AA units by at least two lists ----------------
add("com.miui.tv.analytics", S, "Xiaomi Analytics", "Xiaomi usage analytics/telemetry.", "Xiaomi kullanım analitiği (telemetri).", [CYT, EST, RMM])
add("tv.alphonso.alphonso_eula", S, "Alphonso", "Alphonso automatic content recognition (viewing tracking).", "Alphonso otomatik içerik tanıma (izleme takibi).", [CYT, EST, RMM])
add("com.google.android.feedback", S, "Google Feedback", "Sends feedback/crash reports to Google.", "Google'a geri bildirim/hata raporu gönderir.", [CYT, EST, RMM])
add("com.google.android.tv.bugreportsender", S, "Bug report sender", "Uploads bug reports.", "Hata raporlarını yükler.", [CYT, EST])
add("com.mitv.tvhome.michannel", S, "Mi Channel rows", "PatchWall/Xiaomi content rows on the home screen.", "Ana ekrandaki Xiaomi/PatchWall içerik satırları.", [CYT, EST, RMM])
add("com.xm.webcontent", S, "Xiaomi web content", "Xiaomi sponsored web content.", "Xiaomi sponsorlu web içeriği.", [CYT, EST, RMM])
add("android.autoinstalls.config.xiaomi.mibox3", S, "Play Auto Installs", "Auto-installs partner apps from Play.", "Partner uygulamalarını Play'den otomatik kurar.", [CYT, EST, RMM])
add("android.autoinstalls.config.xioami.mibox3", S, "Play Auto Installs (dup.)", "Duplicate of the partner auto-install config (misspelled by Xiaomi).", "Partner otomatik kurulum ayarının kopyası (Xiaomi yazımı hatalı).", [CYT, EST, RMM])
add("com.mitv.download.service", S, "Mi download service", "PatchWall download service.", "PatchWall indirme servisi.", [CYT, EST, RMM])
add("com.mitv.dream", S, "Mi screensaver", "Xiaomi screensaver.", "Xiaomi ekran koruyucusu.", [CYT, EST, RMM])
add("com.xiaomo.tv.milegal", S, "Mi Legal", "Xiaomi privacy-policy screen.", "Xiaomi gizlilik politikası ekranı.", [CYT, RMM])
add("com.android.printspooler", S, "Print spooler", "Printing service; unused on a TV.", "Yazdırma servisi; TV'de kullanılmaz.", [CYT, EST, RMM])
add("com.google.android.syncadapters.calendar", S, "Calendar sync", "Google Calendar sync adapter.", "Google Takvim eşitleme bağdaştırıcısı.", [CYT, EST, RMM])
add("com.google.android.syncadapters.contacts", S, "Contacts sync", "Google Contacts sync adapter.", "Google Kişiler eşitleme bağdaştırıcısı.", [CYT, EST, RMM])
add("com.mitv.videoplayer", S, "Mi Video Player", "Stock Xiaomi video player. Keep it if you use it.", "Xiaomi video oynatıcı. Kullanıyorsanız dokunmayın.", [CYT, EST, RMM])
add("com.dazn", S, "DAZN", "Preloaded DAZN app.", "Önyüklü DAZN uygulaması.", [CYT, EST])
add("com.nousguide.android.rbtv", S, "Red Bull TV", "Preloaded Red Bull TV app.", "Önyüklü Red Bull TV uygulaması.", [CYT, EST])
add("com.google.android.apps.tachyon", S, "Google Duo", "Preloaded Google Duo.", "Önyüklü Google Duo.", [CYT, EST])
add("com.google.android.play.games", S, "Play Games", "Google Play Games.", "Google Play Oyunlar.", [CYT, EST])
add("com.tcl.browser", S, "TCL Browser", "Preloaded browser.", "Önyüklü tarayıcı.", [CYT, EST])
add("com.google.android.videos", S, "Play Movies & TV", "Google Play Movies & TV.", "Google Play Filmler ve TV.", [CYT, EST])
add("com.google.android.youtube.tvmusic", S, "YouTube Music", "YouTube Music for TV. Keep it if you use it.", "TV için YouTube Music. Kullanıyorsanız dokunmayın.", [CYT, EST])
add("com.google.android.tv", S, "Live Channels", "Live Channels; the stick has no tuner or HDMI input.", "Canlı Kanallar; çubukta tuner/HDMI girişi yok.", [CYT, EST])
add("com.google.android.backdrop", S, "Ambient screensaver", "Google ambient screensaver.", "Google ambiyans ekran koruyucusu.", [CYT, EST])

# ---------------- CAUTION: some lists disable them, but with side effects or weaker evidence ----------------
add("com.google.android.tvrecommendations", C, "TV recommendations", "Content recommendations. Disabled in lists that also replace the launcher; the stock home rows may change.", "İçerik önerileri. Başlatıcıyı değiştiren listelerde kapatılmış; ana ekran satırları değişebilir.", [CYT, EST], True)
add("com.mitv.tvhome.atv", C, "PatchWall", "Xiaomi PatchWall. Disabled in lists that also replace the launcher.", "Xiaomi PatchWall. Başlatıcıyı değiştiren listelerde kapatılmış.", [CYT, EST], True)
add("mitv.service", C, "MiTV service", "MiTV service framework. Crashes Settings on some Xiaomi firmware.", "MiTV servis çatısı. Bazı Xiaomi yazılımlarında Ayarlar'ı çökertir.", [CYT, EST])
add("com.mitv.milinkservice", C, "Mi Cast", "Mi Cast receiver. Disable only if you never cast from a Xiaomi phone.", "Mi Cast alıcısı. Xiaomi telefondan yansıtma yapmıyorsanız kapatın.", [CYT, EST, RMM], True)
add("com.android.providers.calendar", C, "Calendar storage", "Calendar data provider. Disabled on verified units, but it is an Android core provider.", "Takvim veri sağlayıcısı. Doğrulanmış cihazlarda kapatılmış ama Android çekirdek sağlayıcısı.", [CYT, EST, RMM], True)
add("com.android.providers.contacts", C, "Contacts storage", "Contacts data provider. Disabled on verified units, but it is an Android core provider.", "Kişiler veri sağlayıcısı. Doğrulanmış cihazlarda kapatılmış ama Android çekirdek sağlayıcısı.", [CYT, EST, RMM], True)
add("com.google.android.partnersetup", C, "Partner setup", "Google partner setup helper.", "Google partner kurulum yardımcısı.", [CYT, EST])
add("com.google.android.onetimeinitializer", C, "One-time init (Google)", "Runs once at first boot.", "İlk açılışta bir kez çalışır.", [CYT, EST])
add("com.android.onetimeinitializer", C, "One-time init", "Runs once at first boot.", "İlk açılışta bir kez çalışır.", [CYT, EST])
add("com.google.android.backuptransport", C, "Google backup transport", "Google backup. You lose cloud backup of app data.", "Google yedekleme. Uygulama verisi bulut yedeği kaybolur.", [CYT, EST])
add("com.android.backupconfirm", C, "Backup confirmation", "Confirmation UI for adb backup.", "adb yedekleme onay ekranı.", [CYT, EST])
add("com.android.sharedstoragebackup", C, "Shared storage backup", "Backup helper.", "Yedekleme yardımcısı.", [CYT, EST])
add("com.android.wallpaperbackup", C, "Wallpaper backup", "Backup helper.", "Yedekleme yardımcısı.", [CYT, EST])
add("com.android.settings.intelligence", C, "Settings search", "Search/suggestions inside Settings.", "Ayarlar içinde arama/öneriler.", [CYT, EST])
add("com.android.se", C, "Secure element", "Secure element service; the stick has no NFC.", "Güvenli öğe servisi; çubukta NFC yok.", [CYT, EST])
add("com.android.cts.ctsshim", C, "CTS shim", "Compatibility test shim.", "Uyumluluk testi yardımcısı.", [CYT, EST])
add("com.android.cts.priv.ctsshim", C, "CTS shim (priv)", "Compatibility test shim.", "Uyumluluk testi yardımcısı.", [CYT, EST])
add("com.android.dreams.basic", C, "Basic screensaver", "Basic Android screensaver.", "Temel Android ekran koruyucusu.", [CYT, EST])
add("com.google.android.apps.mediashell", C, "Chromecast built-in", "Chromecast receiver. Disabling it stops casting.", "Chromecast alıcısı. Kapatırsanız yansıtma çalışmaz.", [CYT, EST])
add("com.google.android.katniss", C, "Google voice search", "Voice search/Assistant. The remote's voice button stops working.", "Sesli arama/Asistan. Kumandanın ses tuşu çalışmaz.", [CYT, EST])
add("com.google.android.speech.pumpkin", C, "Speech (Pumpkin)", "Offline speech recognition for voice commands.", "Sesli komutlar için çevrimdışı konuşma tanıma.", [CYT, EST])
add("com.google.android.tts", C, "Google Text-to-speech", "Text-to-speech. Needed by TalkBack and apps that read aloud.", "Metin okuma. TalkBack ve sesli okuyan uygulamalar için gerekli.", [CYT, EST])
add("com.google.android.marvin.talkback", C, "TalkBack", "Accessibility screen reader. Keep it if anyone needs it.", "Erişilebilirlik ekran okuyucusu. İhtiyaç varsa dokunmayın.", [CYT, EST])
add("com.google.android.tv.frameworkpackagestubs", C, "Framework stubs", "Stub activities for intents a TV cannot handle. Unverified.", "TV'nin işleyemediği istekler için yedek ekranlar. Doğrulanmadı.", [INV])
add("com.android.htmlviewer", C, "HTML viewer", "Shows license/HTML pages. Unverified.", "Lisans/HTML sayfalarını gösterir. Doğrulanmadı.", [INV])
add("com.android.providers.userdictionary", C, "User dictionary", "Keyboard user dictionary. Unverified.", "Klavye kullanıcı sözlüğü. Doğrulanmadı.", [INV])
add("com.android.statementservice", C, "Intent filter verifier", "App link verification. Unverified.", "Uygulama bağlantı doğrulaması. Doğrulanmadı.", [INV])
add("com.android.vpndialogs", C, "VPN dialogs", "VPN confirmation dialogs. Needed if you use a VPN app.", "VPN onay pencereleri. VPN kullanıyorsanız gerekli.", [INV])
add("com.android.pacprocessor", C, "PAC processor", "Proxy auto-config. Unverified.", "Proxy otomatik yapılandırma. Doğrulanmadı.", [INV])
add("com.google.android.youtube.tv", C, "YouTube", "YouTube for TV. Keep it if you use it.", "TV için YouTube. Kullanıyorsanız dokunmayın.", [INV])
add("com.netflix.ninja", C, "Netflix", "Netflix. Keep it if you use it; the remote's Netflix key will do nothing.", "Netflix. Kullanıyorsanız dokunmayın; kumandadaki Netflix tuşu çalışmaz.", [INV, CYT])
add("com.amazon.amazonvideo.livingroom", C, "Prime Video", "Prime Video. Keep it if you use it.", "Prime Video. Kullanıyorsanız dokunmayın.", [INV])

# ---------------- PROTECTED ----------------
pr = [
 ("android", "Android system", "Core Android framework.", "Android çekirdek çatısı."),
 ("com.google.android.tvlauncher", "Home screen (launcher)", "Stock home screen. Without it the Home key does nothing and the stick may not finish booting.", "Ana ekran. Olmadan Ana Sayfa tuşu çalışmaz, cihaz açılmayabilir."),
 ("com.google.android.apps.tv.launcherx", "Google TV home", "Home screen component.", "Ana ekran bileşeni."),
 ("com.google.android.leanbacklauncher", "Leanback launcher", "Home screen.", "Ana ekran."),
 ("com.google.android.tungsten.setupwraith", "Setup wizard", "Setup wizard; on this stick it can own the saved Wi-Fi network (disabling it drops Wi-Fi and ADB).", "Kurulum sihirbazı; bu cihazda kayıtlı Wi-Fi ağının sahibi olabilir (kapatmak Wi-Fi ve ADB'yi keser)."),
 ("com.android.vending", "Google Play Store", "App store.", "Uygulama mağazası."),
 ("com.google.android.gms", "Google Play Services", "Google core services.", "Google çekirdek servisleri."),
 ("com.google.android.gsf", "Google Services Framework", "Google core framework.", "Google çekirdek çatısı."),
 ("com.google.android.sss.authbridge", "Auth bridge", "Google account sign-in bridge.", "Google hesap giriş köprüsü."),
 ("com.google.android.packageinstaller", "Package installer", "Installs apps.", "Uygulama yükler."),
 ("com.android.packageinstaller", "Package installer", "Installs apps.", "Uygulama yükler."),
 ("com.android.systemui", "System UI", "System user interface.", "Sistem arayüzü."),
 ("com.android.tv.settings", "Settings", "TV settings.", "TV ayarları."),
 ("com.android.settings", "Settings", "Settings.", "Ayarlar."),
 ("com.android.providers.settings", "Settings storage", "Stores all system settings.", "Tüm sistem ayarlarını saklar."),
 ("com.android.bluetooth", "Bluetooth", "Bluetooth; the remote uses it.", "Bluetooth; kumanda bunu kullanır."),
 ("com.droidlogic.ble", "BLE remote service", "Amlogic Bluetooth LE (remote) service.", "Amlogic Bluetooth LE (kumanda) servisi."),
 ("com.android.companiondevicemanager", "Companion devices", "Bluetooth companion pairing.", "Bluetooth eşlik eşleştirmesi."),
 ("com.android.inputdevices", "Input devices", "Keyboard/remote layouts.", "Klavye/kumanda düzenleri."),
 ("com.google.android.tv.remote.service", "Android TV Remote Service", "Phone-as-remote and the remote's microphone bridge.", "Telefonu kumanda yapma ve kumanda mikrofonu köprüsü."),
 ("com.google.android.inputmethod.latin", "Gboard", "On-screen keyboard (input method).", "Ekran klavyesi (giriş yöntemi)."),
 ("com.google.android.leanback.ime", "Leanback keyboard", "On-screen keyboard.", "Ekran klavyesi."),
 ("com.android.shell", "Shell (ADB)", "Runs ADB commands. This app needs it.", "ADB komutlarını çalıştırır. Bu uygulama buna muhtaç."),
 ("com.android.captiveportallogin", "Captive portal login", "Wi-Fi login pages.", "Wi-Fi giriş sayfaları."),
 ("com.android.proxyhandler", "Proxy handler", "Network proxy.", "Ağ proxy'si."),
 ("com.android.certinstaller", "Certificate installer", "Network/Wi-Fi certificates.", "Ağ/Wi-Fi sertifikaları."),
 ("com.android.keychain", "Key chain", "Credential storage.", "Kimlik bilgisi deposu."),
 ("com.android.defcontainer", "Package access helper", "Needed to install apps.", "Uygulama kurulumu için gerekli."),
 ("com.android.externalstorage", "External storage", "Storage access.", "Depolama erişimi."),
 ("com.android.providers.media", "Media storage", "Media database.", "Medya veritabanı."),
 ("com.android.providers.downloads", "Download manager", "System download manager (Play and updates use it).", "Sistem indirme yöneticisi (Play ve güncellemeler kullanır)."),
 ("com.android.providers.tv", "TV provider", "Channel/row data for the home screen.", "Ana ekran kanal/satır verisi."),
 ("com.android.location.fused", "Fused location", "Location provider.", "Konum sağlayıcısı."),
 ("com.google.android.ext.services", "Android Services Library", "Core system services.", "Çekirdek sistem servisleri."),
 ("com.google.android.ext.shared", "Android Shared Library", "Shared system library.", "Paylaşılan sistem kütüphanesi."),
 ("com.google.android.webview", "Android System WebView", "Web rendering used by many apps and sign-in.", "Birçok uygulama ve giriş ekranının kullandığı web motoru."),
 ("com.droidlogic", "Amlogic platform", "Amlogic hardware platform service.", "Amlogic donanım platform servisi."),
 ("com.droidlogic.SubTitleService", "Subtitle service", "Amlogic platform subtitle service.", "Amlogic platform altyazı servisi."),
 ("com.droidlogic.overlay", "Amlogic overlay", "Platform resources.", "Platform kaynakları."),
 ("com.xiaomi.mitv.res", "Xiaomi resources", "Xiaomi system resources.", "Xiaomi sistem kaynakları."),
 ("com.xiaomi.mitv.updateservice", "Xiaomi system update", "Firmware updates.", "Yazılım güncellemeleri."),
 ("com.xiaomi.android.tvsetup.partnercustomizer", "Xiaomi partner customizer", "Handles Bluetooth remote pairing and shortcut buttons on many Xiaomi devices.", "Birçok Xiaomi cihazda kumanda eşleştirmesi ve kısayol tuşlarını yönetir."),
]
for pkg, label, en, tr in pr:
    src = [INV]
    if pkg in ("com.google.android.tvlauncher", "com.google.android.tungsten.setupwraith"): src = [CYT, BOOT]
    if pkg in ("com.xiaomi.android.tvsetup.partnercustomizer", "com.android.providers.tv", "com.android.companiondevicemanager"): src = [CYT]
    add(pkg, X, label, en, tr, src)

names = [p["packageName"] for p in P]
assert len(names) == len(set(names)), "duplicate"
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "packages.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    json.dump({"version": 1, "device": "Xiaomi Mi TV Stick MDZ-24-AA (MiTV-AESP0, aquaman), Android TV 9",
               "packages": P}, f, ensure_ascii=False, indent=2)
print(len(P), "entries;", sum(p["level"] == "SAFE" for p in P), "SAFE")

# TV Debloater

Android phone app that connects to an Android TV (tested target: **Xiaomi Mi TV Stick MDZ-24-AA**, Android TV 9) over Wi-Fi ADB and safely disables unnecessary system apps, with full backup and undo.

[Türkçe](#türkçe) · [English](#english)

---

## Türkçe

### Ne yapar?
- Telefonunuzdan TV'ye Wi-Fi üzerinden ADB ile bağlanır (bilgisayar gerekmez, root gerekmez).
- `pm list packages -s` ve `-d` ile sistem uygulamalarını ve kapalı olanları listeler.
- Her paketi **GÜVENLİ / DİKKAT / KORUMALI** olarak işaretler. Korumalı paketler (başlatıcı, Play Store, Play Hizmetleri, kurulum sihirbazı, Bluetooth/kumanda, klavye, Wi-Fi, ADB, Xiaomi güncelleme/eşleştirme…) seçilemez; kodda ayrıca sabit bir güvenlik ağı vardır.
- Varsayılan işlem geri alınabilir: `pm disable-user --user 0 <paket>`. İsteğe bağlı: `pm uninstall -k --user 0 <paket>` (uyarıyla).
- Her işlemden önce zaman damgalı yedek kaydı tutar. Geçmiş ekranından tüm işlemi veya tek paketi geri alabilir, geçmişi JSON olarak dışa/içe aktarabilirsiniz.
- **Deneme modu** ilk açılışta açıktır: komutları çalıştırmadan gösterir.
- "Hafif" ve "Agresif" profiller yalnızca ön seçim yapar; onayı siz verirsiniz.
- İşlem öncesi/sonrası boş RAM ve `/data` alanını gösterir.
- **Tümünü geri yükle** düğmesi, uygulamanın değiştirdiği her şeyi yeniden açar.
- TV'yi asla kendisi yeniden başlatmaz; yalnızca önerir.

### TV'de ADB'yi açma
1. Ayarlar > Cihaz Tercihleri > Hakkında > **Derleme** satırına 7 kez OK basın ("Artık bir geliştiricisiniz").
2. Cihaz Tercihleri > **Geliştirici seçenekleri**: anahtarı açın, **USB hata ayıklama**yı açın (varsa "Ağ üzerinden hata ayıklama"yı da).
3. Ayarlar > Ağ ve İnternet > Wi-Fi ağınız: **IP adresini** not edin (ör. 192.168.1.23).
4. Uygulamada IP'yi girin (port 5555) veya "Ağda tara"ya basın, sonra Bağlan.
5. TV'de **"USB hata ayıklamaya izin verilsin mi?"** sorusu çıkar: "Her zaman izin ver"i işaretleyip Tamam'a basın.

### APK'yı telefondan kurma
1. Telefonun tarayıcısında GitHub'a giriş yapın (depo özel olduğu için gerekli).
2. [Releases](../../releases/latest) sayfasından `tv-debloater-debug.apk` dosyasını indirin. (Alternatif: Actions > son başarılı çalışma > Artifacts > `tv-debloater-debug-apk` zip olarak iner; içinden APK'yı çıkarın.)
3. İndirilen dosyaya dokunun; Android "bilinmeyen uygulamaları yükleme" izni isterse tarayıcınıza izin verin.
4. Kurun ve açın. Debug APK olduğu için Play Protect uyarı verebilir; "Yine de yükle"yi seçebilirsiniz.

### Bir şeyler ters giderse
1. Uygulamada **Geçmiş > Geri al** veya **Tümünü geri yükle**.
2. Elle: `pm enable <paket>` veya `cmd package install-existing <paket>` (uygulama bunları sizin yerinize çalıştırır).
3. TV'yi yeniden başlatın (Ayarlar > Cihaz Tercihleri > Hakkında > Yeniden başlat).
4. **Son çare – fabrika ayarları:** Ayarlar > Cihaz Tercihleri > Sıfırla. Menüye ulaşamıyorsanız: çubuğun gücünü çekin, kumandada **OK + Geri** tuşlarını basılı tutarak gücü yeniden takın, kurtarma menüsünde **Wipe data / factory reset** seçin ([Xiaomi destek](https://www.mi.com/uk/support/faq/details/KA-557214/)). Fabrika sıfırlaması tüm sistem uygulamalarını geri getirir, ancak hesaplarınız ve kurduğunuz uygulamalar silinir.

### Paket sınıflandırması
`app/src/main/assets/packages.json` (üretici: `tools/gen_packages.py`). Bir paket yalnızca Mi TV Stick (MiTV-AESP0 / aquaman) üzerinde doğrulanmış kamuya açık listelerde kapatıldıysa GÜVENLİ sayılır; her girdide kaynak bağlantıları vardır. Belirsiz olanlar DİKKAT, veritabanında olmayanlar "Bilinmiyor" (DİKKAT) olarak gösterilir; bilinmeyen `com.android.*` paketleri korunur.

---

## English

### What it does
- Connects from your phone to the TV over Wi-Fi ADB (no computer, no root).
- Lists system packages (`pm list packages -s`) and disabled ones (`-d`).
- Classifies each package as **SAFE / CAUTION / PROTECTED**. Protected packages (launcher, Play Store, Play Services, setup wizard, Bluetooth/remote, input methods, Wi-Fi, ADB shell, Xiaomi update/pairing…) cannot be selected, and a hard-coded guard in `ProtectedGuard.kt` blocks them even if the JSON is edited.
- Default action is reversible: `pm disable-user --user 0 <pkg>`. Optional: `pm uninstall -k --user 0 <pkg>` (with a warning).
- Saves a timestamped backup record before every batch. History lets you undo a whole batch or one package and export/import the history as JSON via the share sheet.
- **Dry run** is on by default: shows the exact commands without running them.
- "Light" and "Aggressive" profiles only preselect; you confirm manually.
- Shows free RAM (`dumpsys meminfo`) and `/data` space (`df /data`) before and after a batch.
- **Restore all** re-enables everything the app ever changed.
- Never reboots the TV; it only suggests a restart.

### Enable ADB on the TV
1. Settings > Device Preferences > About > press OK on **Build** 7 times.
2. Device Preferences > **Developer options**: turn on the switch and **USB debugging** (and "Network debugging" if present).
3. Settings > Network & Internet > your Wi-Fi: note the **IP address**.
4. In the app, enter the IP (port 5555) or tap "Scan network", then Connect.
5. Accept **"Allow USB debugging?"** on the TV (tick "Always allow").

### Install the APK from a phone
1. Sign in to GitHub in the phone's browser (the repository is private).
2. Download `tv-debloater-debug.apk` from the [latest release](../../releases/latest) (or from Actions > latest run > Artifacts, which downloads a zip).
3. Open the file and allow your browser to install unknown apps when asked.
4. Install and open it. Play Protect may warn about a debug APK.

### If something goes wrong
1. In the app: **History > Undo** or **Restore all**.
2. Manually: `pm enable <pkg>` or `cmd package install-existing <pkg>`.
3. Restart the TV.
4. **Last resort – factory reset:** Settings > Device Preferences > Reset, or unplug the stick, hold **OK + Back** on the remote while plugging power back in, then choose **Wipe data / factory reset** ([Xiaomi support](https://www.mi.com/uk/support/faq/details/KA-557214/)). This restores all system apps but erases accounts and installed apps.

### Build
GitHub Actions (`.github/workflows/build.yml`) runs unit tests and `assembleDebug` on every push and on manual dispatch, uploads the APK as an artifact, and publishes a release on `main`.

Pinned: Gradle 8.9 (wrapper), AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, JDK 17, compileSdk 35, targetSdk 34, minSdk 26, [dadb](https://github.com/mobile-dev-inc/dadb) 2.0.0.

### Sources for SAFE packages
- [cleanyourtv – xiaomi-mitv-aesp0](https://github.com/Gathin23/cleanyourtv/blob/main/examples/xiaomi-mitv-aesp0/debloat.txt) (four verified MDZ-24-AA units)
- [ExtremeSlimTV – Mi TV Stick FHD profile](https://github.com/yositapjani/ExtremeSlimTV/blob/main/profiles/mitv-aesp0-mi-tv-stick-fhd.txt)
- [xiaomi-tv-stick-aquaman-debloat – conservative script](https://github.com/ricardomm7/xiaomi-tv-stick-aquaman-debloat/blob/main/scripts/debloat.sh) and its [factory package inventory](https://github.com/ricardomm7/xiaomi-tv-stick-aquaman-debloat/blob/main/data/baseline_packages.json)

### Safety scope
The app only changes package state for user 0 on the connected device (disable / enable / uninstall for user / install-existing). No root, no flashing, no bootloader, no data wipes.

License: MIT.

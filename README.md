# Kvieta Android eşlikçi

BIG V1 UPDATE için Android'e özel, hesapsız eşlikçi uygulama. **1.0.0-rc2**, aynı ağda onaylı QR eşleştirmesi, farklı ağ/mobil veride şifreli dashboard ve gerçek aile ek-süre karar akışını içerir.

## RC2 doğrulaması — 22 Eylül 2026

- Günlük ekran: belirgin süre özeti, kapalı başlayan kullanım ayrıntısı; bağlantı, bildirim ve unutma işlemleri ayrı panelde. Aile talebi önceliklidir. Kvieta zeytin yeşili/sıcak açık paleti ve koyu tema korunur.
- Dakika seçenekleri karar göndermez; seçimin ardından tek Onayla düğmesi kullanılır. 1–180 dakika özel değer ve ret desteklenir.
- Yenileme bir kararı iptal etmez. Belirsiz gönderim, Keystore ile korunan aynı şifreli kararın tekrarını kullanır; çelişen ikinci karar yaratmaz.
- Uygulama yeniden açıldığında şifreli son özet eski veri olarak gösterilir. JSON null değerleri kullanıcıya metin olarak çıkmaz. Oturum süresi ile ön plan uygulama süresi ayrıdır.
- 22 Android testi geçti, 0 hata ve 0 atlanan: birim testleri, gerçek izole .NET TLS sunucusu, canlı Cloudflare şifreli özet/karar ve Robolectric UI etkileşimleri. Açık/koyu tema ve 320 dp / %150 yazı ekranları yerel olarak render edilip incelendi.
- assembleDebug ve lintDebug başarılı. Lint 69 uyarı içerir (bağımlılık sürümü, kullanılmayan eski kaynaklar, KTX önerileri ve bilinçli özel sertifika doğrulayıcı dahil); hata yoktur.
- APK sürümü 1.0.0-rc2-dev / code 4. SHA-256: 8C9F99098F1ABCACFB4F99069A18BDB28340594B4EE2ADD8FB721BCEF69D2C18. Debug v2 imzası doğrulandı. Uyumlu Windows kurulum sürümü 5.1.1 / V1-RC2.
- Fiziksel Android cihaz bağlı değildi: gerçek kamera, cihaz Keystore'u, Wi-Fi/mobil veri geçişi ve OEM bildirim teslim süresi saha testini bekler. JVM testleri bunların yerine geçmez.
- Arka plan bildirimi anlık push değildir. WorkManager 15 dakikalık periyotta kontrol eder; Android geciktirebilir. Uygulama açıkken 30 saniyelik yenileme ve elle Yenile vardır. Üretim imzalı/mağaza paketi değil, denenebilir RC paketidir.

## Şu an ne var?

- Hesapsız bilgisayar daveti: öncelikli Cloudflare relay QR eşleştirmesi; yerel HTTPS yalnızca yedek/test yolu.
- Gerçek cihaz adı, gün, kullanılan/kalan süre ve ilk üç uygulama; kaydın zamanı ve eski veri durumu.
- Android Keystore cihaz anahtarı, sertifikası sabitlenmiş yerel HTTPS bağlantısı, Cloudflare üzerinden uçtan uca şifreli uzaktan özet, kayıtlı bilgisayara yeniden bağlanma ve erişim iptali.
- Eşleşmeden sonra doğrudan gerçek kişisel/aile dashboard'u; demo ana akışta yoktur.
- Kullanılan/kalan süre özeti, oturum durumu ve dokununca açılan uygulama dağılımı.
- Aile modunda bilgisayardan gelen gerçek ek süre talebi, 15/30/60 veya özel dakika onayı, ret ve bilgisayardan `Applied` makbuzu.
- Uygulama açıkken 30 saniyelik otomatik yenileme; Android WorkManager ile arka plan talep kontrolü ve izinli yerel bildirim.
- Türkçe/İngilizce kaynaklar, sistemin açık/koyu temasına uyum, sistem kenar boşlukları ve geri gezinme.
- Kvieta temalı QR tarama ekranı: kare kamera alanı, geri dönüş, fener, kamera izni ve hata durumları. Yanlış/süresi dolmuş QR tarayıcıyı kapatmaz; yalnız geçerli Android daveti eşleştirmeyi başlatır. Kamera uygulama arka plana geçince durdurulur.

Uygulama içi QR kamera tarayıcısı gerçek bağlantı akışının ana yoludur; metin yapıştırma yedek olarak kalır. Uzaktan bağlantı yalnız bilgisayarda açıkça etkinleştirilir. Özet ve kararlar uçtan uca şifrelidir; relay içerikleri okuyamaz. Telefon planı/PIN'i düzenleyemez, yalnız bilgisayarın oluşturduğu süre talebini yanıtlayabilir.

## Telefonla deneme

1. Yeni Windows derlemesinde **Ayarlar → Gizlilik ve veri → Telefonum** açılır. PIN ayarlıysa doğrulama istenir; PIN'siz Aile modunda paylaşım başlatılmaz. Android kullanım bağlantısı ve mevcut tarayıcı kurtarma telefonu aynı ekranda görünür; kurtarma ayrı yetkidir ve Android'e otomatik verilmez.
2. İlk QR eşleştirmesi için telefon ve bilgisayar aynı özel Wi-Fi/yerel ağda olmalı. Windows güvenlik duvarı sorarsa yalnız özel ağdaki bağlantıya izin ver. Port 24882 kullanılır; genel ağa açmak gerekmez.
3. APK'yı yükle, **Bilgisayar bağla** bölümüne gir ve **Bilgisayar QR kodunu okut** düğmesine bas. Kvieta'nın paylaşım penceresindeki QR'ı tara; davet okununca eşleştirme otomatik başlar. Kamera kullanılamıyorsa davet metnini yapıştırıp Bağlan'a bas. Davet iki dakika geçerlidir.
4. İki ekrandaki 12 karakterli kodu karşılaştırıp bilgisayarda onayla. Gerçek dashboard açılır ve otomatik yenilenir; son veri zamanı ayrıca görünür.
5. Bilgisayarda Telefonum ekranından **Dışarıdan erişimi aç** seçeneğini etkinleştir ve Android'de aynı Wi-Fi'da bir kez **Özeti yenile**. Bundan sonra telefon farklı Wi-Fi veya mobil verideyken Cloudflare üzerinden şifreli son özeti alabilir. Bilgisayar kapalıysa son kayıt gösterilir; güncel kayıt için Kvieta çalışıyor olmalı.
6. Aile oturumunda bilgisayardan **Ek süre iste** seçilince talep telefonda görünür. Karar PC tarafından tekrar doğrulanır ve yalnız bir kez uygulanır. Bildirim için Android'de **Talep bildirimlerini aç** seçilir.
7. **Telefon erişimini kaldır**, bilgisayarda kayıtlı anahtarı iptal eder. Android'de **Bağlantıyı unut**, yerel bağlantıyı ve özel anahtarı siler; yeniden eşleştirmeden önce bilgisayardaki eski kaydı da kaldır.

Kurulu eski Alpha 5 bu çift yönlü akışı içermez; birlikte üretilen güncel Windows V1 RC kurulumu gerekir.

## Teknik temel

Kotlin + Jetpack Compose seçildi; yalnız Android hedefi için ikinci bir çapraz platform katmanı eklenmedi. Masaüstü C# çözümünden bağımsızdır. Mevcut `companion-web` ve telefon kurtarma bağlantısı değiştirilmedi.

- Android 8.0 / API 26 başlangıç alt sınırı; gerçek cihaz değerlendirmesiyle kesinleştirilecek. RC4 relay QR’ı Hyper-V/NAT içindeki bilgisayara doğrudan bağlanmaz.
- compile/target SDK 36, JDK 17, Gradle 9.1.0, AGP 9.0.1, Compose compiler 2.2.10.
- Compose BOM 2025.08.01 ve Activity 1.11.0 sabitlendi; otomatik değişen sürüm kullanılmadı.
- AGP'nin [yerleşik Kotlin desteği ve araç uyumluluğu](https://developer.android.com/build/releases/agp-9-0-0-release-notes) ve [Compose compiler kurulumu](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) esas alındı.
- Gradle wrapper resmi v9.1.0 kaynağından alındı; JAR SHA-256 `76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3` resmi Gradle checksum'u ile doğrulandı. Dağıtım checksum'u wrapper ayarında sabittir.

## Açma ve derleme

Android Studio'da bu klasörü proje olarak aç. JDK 17 ve SDK Platform 36 / Build Tools 36.0.0 gerekir. SDK yolu gerekiyorsa makineye özel `local.properties` içine eklenir; bu dosya Git'e girmez.

Windows'ta bu klasör içinde:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS üzerinde `sh ./gradlew` kullanılabilir. İlk çalışma bağımlılıkları indirir. APK başarılı derlemeden sonra `app/build/outputs/apk/debug/app-debug.apk` altında oluşur. Geliştirme paket kimliği `com.kvieta.companion.dev` olur. İmzalı dağıtım veya mağaza yayını yapılandırılmadı.

Bu bilgisayarda `./build-preview.ps1` Java/SDK ortamını yalnız işlem süresince ayarlar ve derleme/test/lint çalıştırır. Araçlar `C:\Users\Public\KvietaAndroidTools` altındadır; oradaki `project` junction'ı bu klasöre bakar. Bu ASCII yol, Java araçlarının kullanıcı adındaki Türkçe karakter sorununu aşar. Sistem PATH'i değiştirilmez.

20 Eylül 2026: ilk debug APK üretildi; `app-debug.apk` boyutu 10.2 MB, SHA-256 `F79DE1ED323293455B959A662E6ECF29856E0736F46D122A07C5B6F57350F320`. `:app:testDebugUnitTest` ve `:app:lintDebug` başarılı. ADB'de bağlı cihaz bulunmadığı için gerçek telefon kurulumu ve görsel cihaz kontrolü yapılamadı. SDK araçları XML sürüm uyumluluğu hakkında uyarı verdi ancak derlemeyi başarısız kılmadı.

## Kod sınırları

Güncel çıktı (21 Eylül, 1.0.0-rc1-dev): SHA-256 derleme çıktısında yazdırılır; APK v2 imzası doğrulanmalıdır. QR kamera tarayıcısı JourneyApps ZXing Embedded 4.3.0 ile çalışır. Bağlı fiziksel cihaz yoktu.

- `ui/CompanionApp.kt`: eşleşmemiş karşılama ve eşleşince doğrudan gerçek dashboard akışı.
- `ui/KvietaTheme.kt`: Kvieta renkleri ve iki tema.
- `connection/`: davet doğrulama, Keystore kimliği, sabit sertifikalı istemci ve bağlantı durumu.
- `ui/ConnectionScreen.kt`: gerçek bağlantı, özet ve süre kararı akışı.
- `src/test`: demo kontrolleri, davet/yerel adres sınırları ve isteğe bağlı .NET/Kotlin birlikte çalışma testi.

Manifest INTERNET, kamera ve Android 13+ bildirim izni ister; kamera yalnızca kullanıcı tarama düğmesine bastığında kullanılır. Özel anahtar Android Keystore'dadır, uygulama koduna dışa aktarılmaz. Eşleştirilen adres/sertifika pini özel uygulama ayarlarında tutulur; yedekleme ve cihaz aktarımında bu ayarlar dışlanır. Windows eşleşmesi ve talep durumu kullanıcıya bağlı DPAPI ile saklanır. Mevcut yönetici/kurtarma telefonuna yeni yetki verilmez.

21 Eylül 2026 doğrulaması: Windows Release derlemesi ve tam smoke testleri geçti. TLS pini, onay öncesi veri erişimi, imzalı istek tekrarı, kayıtlı eşleşme ve iptal kontrolleri geçti. Kotlin istemcisi izole gerçek .NET sunucusuna bağlanıp veri okudu; bu birlikte çalışma testinde 1 test çalıştı, 0 atlandı. Android derlemesi, birim testleri ve lint tamamlandı; lint sürüm/tavsiye uyarıları ve bilinçli özel sertifika doğrulayıcı uyarısı içerir. Fiziksel Android Keystore, kamera bağlantısı açma, Windows güvenlik duvarı ve gerçek Wi-Fi cihaz testi henüz yapılmadı.

## RC sonrası saha doğrulaması

1. APK ve V1 RC Windows kurulumunu gerçek telefonda dene: QR kamera, farklı Wi-Fi/mobil veri, bildirim, onay/red ve tek sefer uygulama.
2. OEM pil tasarrufunun 15 dakikalık WorkManager periyodunu geciktirebildiğini cihaz bazında ölç; uygulamayı açmak her zaman anlık yenileme başlatır.
3. Üretim yayını için Android release signing/mağaza dağıtımı ve Windows kod imzasını tamamla.

Bu proje masaüstünde `kveita-android` klasöründe, Kvieta masaüstü deposundan bağımsız yerel Git deposudur. Uzak depo henüz yoktur.

Esas kapsam ve ertelenen masaüstü kontrollerinin ayrılma anındaki kopyası kökteki `V1 RELEASE.md` içindedir. Sonraki Android ilerlemesi burada tutulur; masaüstündeki planla otomatik eşitlenmez. Plandaki eski `companion-android` yolları tarihsel konumdur; bu projenin kökü artık bu klasördür.

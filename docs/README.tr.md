<div align="center">

<img src="../assets/branding/kvieta-mark.svg" alt="Kvieta logo" width="132" />

# Kvieta Mobil

### All in good time.

Windows için Kvieta uygulamasının sakin, yerel öncelikli Android eşlikçisi.

[**English**](../README.md)

![Android](https://img.shields.io/badge/Android-native-87946B?style=flat-square&labelColor=292B26)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-87946B?style=flat-square&labelColor=292B26)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-C9B98E?style=flat-square&labelColor=292B26)
![Gizlilik](https://img.shields.io/badge/gizlilik-yerel--öncelikli-87946B?style=flat-square&labelColor=292B26)
![Durum](https://img.shields.io/badge/durum-Alpha_1.1-C9B98E?style=flat-square&labelColor=292B26)
![Lisans](https://img.shields.io/badge/lisans-MIT-87946B?style=flat-square&labelColor=292B26)

</div>

Kvieta Mobil, [Windows için Kvieta](https://github.com/Rel0adediso/Kvieta-app) uygulamasının yerel Android eşlikçisidir. Ekran süresi farkındalığını, uzaktan oturum yönetimini ve ek süre onaylarını verilerinizi kendi cihazlarınızda tutarak doğrudan cebinize taşır. Bulut hesabı gerekmez.

## Kvieta Mobil Alpha 1.1 İndir

[**Android için Kvieta Mobil APK İndir**](https://github.com/Rel0adediso/kvieta-mobile/releases/download/alpha-1.1/kvieta-mobile-alpha-1.1.apk)

Android 8.0 (API 26) ve üzeri sürümlerle uyumludur. Jetpack Compose ve Material 3 ile Kvieta'nın zeytin yeşili ve sıcak taş renk paletine tam sadık kalınarak geliştirilmiştir.

Sürüm notları, sağlama toplamları ve dosyalar [Kvieta Mobil Alpha 1.1 sürüm sayfasındadır](https://github.com/Rel0adediso/kvieta-mobile/releases/tag/alpha-1.1).

> **Önemli:** Kvieta Mobil isteğe bağlı bir destekçi uygulamadır. Tüm temel ekran süresi limitleri, programlar ve uygulama kuralları Windows bilgisayarınız üzerinde çalışır.

## Bilgisayarınızla Etkileşim Yolları

| Özellik | Rol | Deneyim |
|---|---|---|
| **Uzaktan Oturum Kontrolü** | Masadan ayrıldığınızda | Tek dokunuşla bilgisayarınızı kilitleyin veya sakin bir mola başlatın. |
| **Ek Süre Onayları** | Süre taleplerini yanıtlama | Bilgisayardan gelen ek süre isteklerini biyometri veya PC PIN'i ile onaylayın. |
| **Canlı Kullanım Panosu** | Ekran süresini görünür kılma | Kalan günlük süreyi, mevcut odak modunu ve kategori dağılımını görün. |
| **Web Guard Yönetimi** | Dikkat dağıtıcı siteleri filtreleme | Alan adı kısıtlamalarını ve koruma kurallarını uzaktan düzenleyin. |

## Kvieta Mobil Neler Yapabilir?

| | |
|---|---|
| **Kullanımı İzleme** | Canlı oturum durumu, kalan günlük süre ve en çok kullanılan uygulamaların dökümü. |
| **Uzaktan Kontrol** | Masaya dönmeden tek dokunuşla oturumu kilitleme, mola verme ve devam ettirme. |
| **Talep Onaylama** | Biyometrik parmak izi okuyucu veya Windows yönetici PIN'i (PBKDF2-SHA256) ile güvenli onay. |
| **Hızlı Eşleştirme** | Wi-Fi veya şifreli güvenli röle üzerinden masaüstündeki QR kodu kamera ile tarayarak bağlanma. |
| **Gizliliği Koruma** | Cihaz anahtarları Android Keystore içinde saklanır; uçtan uca şifreleme ve sıfır bulut kaydı. |
| **Sakin Uyum** | Açık/koyu tema uyumu, dinamik sistem kenar boşlukları ve nazik dokunsal geri bildirimler. |

## Eşleştirme Nasıl Yapılır?

1. Windows bilgisayarınızda **Kvieta** uygulamasını açın ve **Telefon Bağlama** seçeneğine tıklayın.
2. Android telefonunuzda **Kvieta Mobil** uygulamasını açın ve **QR Kodu Tara** düğmesine dokunun.
3. Kameranızı ekrandaki koda tutarak yerel Wi-Fi veya şifreli röle üzerinden saniyeler içinde bağlanın.
4. Telefonunuz artık eşleşmiştir. Erişimi dilediğiniz an Windows bilgisayarınızdan kaldırabilirsiniz.

## Alpha 1.1 ile Gelen Yenilikler

- **Biyometrik veya PIN Doğrulaması**: Ek süre onaylarında parmak izi veya PC PIN'i seçeneği.
- **Kvieta Temalı PIN Diyaloğu**: Yumuşak animasyonlara ve duyarlı hata yönetimine sahip özel PIN arayüzü.
- **Hızlı QR Kod Tarayıcısı**: Fener desteği ve manuel kod girişi yedeği sunan kamera tarayıcısı.
- **Uzaktan Kilit ve Mola Senkronizasyonu**: Hızlandırılmış durum bildirimleri ve gerçek zamanlı oturum senkronizasyonu.
- **Web Guard Uzaktan Yönetimi**: Alan adı filtreleme kurallarını uzaktan inceleme ve yapılandırma.

## Tasarımı Gereği Gizli

Kvieta Mobil, Windows bilgisayarınızla yerel ağ üzerinden veya özel uçtan uca şifreli röle ile doğrudan iletişim kurar. Hesap, üçüncü parti analiz araçları veya merkezi veri tabanları yoktur. Gizli anahtarlar asla telefonunuzun donanımsal Keystore alanından dışarı çıkmaz.

## Kaynak Koddan Derleme

Gereksinimler: JDK 17 ve Android SDK Platform 36.

```powershell
.\gradlew.bat :app:assembleDebug
```

Testleri ve lint kontrollerini çalıştırmak için:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

## Güvenlik Modeli

Kvieta Mobil, Windows için Kvieta uygulamasına yetkilendirilmiş bir eşlikçi olarak çalışır. Onaylar kriptografik olarak imzalanır, sorgu-cevap (challenge-response) protokolüyle denetlenir ve Windows bilgisayarı tarafından doğrulanır. Onay verebilmek için telefona fiziksel erişim ve biyometri ya da PIN gereklidir.

## Geliştirme Yaklaşımı

**İnsan yönetiminde ürün · Yapay zekâ destekli geliştirme.** Ürün yönü, kullanıcı deneyimi kararları ve uygulamalı testler [Rel0adediso](https://github.com/Rel0adediso) tarafından yürütülür. Mimari, uygulama ve test geliştirme OpenAI Codex ile birlikte yapılır.

Kvieta Mobil, [MIT Lisansı](LICENSE) altında açık kaynaklı bir yazılımdır.

---

<div align="center">

**Kvieta Mobil** · *All in good time.*

</div>

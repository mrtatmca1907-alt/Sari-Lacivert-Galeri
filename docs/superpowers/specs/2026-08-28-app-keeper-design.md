# ATMACA Koruma Design

## Amaç
Android'de kullanıcı tarafından seçilen uygulamalar için en güçlü kullanıcı-seviyesi koruma katmanını sağlamak: ATMACA Koruma kendi foreground servisini açık tutar, CPU uykuya geçmesini azaltmak için partial WakeLock kullanır, Wi-Fi bağlantısında high-performance WifiLock kullanır, ağ durumunu izler, telefon açılışında koruma daha önce etkinse servisi geri başlatır ve seçili uygulamaları tek dokunuşla yeniden açar.

## Sınırlar
- Root/device-owner olmadan başka bir uygulamanın force-stop edilmesini veya üretici RAM yöneticisinin onu öldürmesini kesin olarak engelleyemez.
- Başka bir uygulamanın iç indirme durumunu geri yükleyemez; hedef uygulama kendi devam mekanizmasına sahip olmalıdır.
- Arka plandan başka uygulamayı otomatik öne getirme Android tarafından engellenebilir. Bu nedenle ağ geri geldiğinde bildirim ve hızlı açma eylemi kullanılır.

## Mimari
- Native Kotlin + Android Views.
- `KeeperService`: foreground servis, `START_STICKY`, WakeLock, WifiLock, NetworkCallback, durum bildirimi.
- `BootReceiver`: BOOT_COMPLETED sonrasında tercih açıksa servisi yeniden başlatır.
- `AppRepository`: launcher uygulamalarını listeler.
- `Prefs`: seçili paketler ve koruma açık/kapalı durumu.
- `MainActivity`: uygulama seçici, korumayı başlat/durdur, pil optimizasyonu ayarına git, seçili uygulamayı aç.
- `TargetRules`: seçili paket listesini temizler, boş/self paketleri dışlar ve tekrarları kaldırır.

## Kabul Kriterleri
- Kullanıcı birden fazla launcher uygulaması seçebilir ve seçim kalıcıdır.
- Koruma açıldığında kalıcı bildirim görünür ve servis foreground çalışır.
- Ekran kapalıyken servis CPU WakeLock tutar; Wi-Fi mevcutsa WifiLock alır.
- Ağ durumu değişince bildirim metni güncellenir.
- Telefon yeniden başladıktan sonra koruma daha önce açıksa servis yeniden başlatılır.
- Kullanıcı seçili uygulamaları uygulama içinden açabilir.
- Android 13 cihazlarda çalışacak; minSdk 26, targetSdk 33, compileSdk 35.

# ATMACA ZIP Paketleyici 1.1

Android 13 odaklı, dahili depolamanın kökü dahil seçilen klasörü alt klasörleriyle birlikte tek ZIP'e paketleyen yerel APK.

## 1.1 değişiklikleri
- Android SAF klasör seçicisi kaldırıldı.
- Kendi klasör tarayıcısı /storage/emulated/0 kökünden başlar.
- Dahili depolamanın kökü doğrudan seçilebilir.
- Android 11+ için "Tüm dosyalara erişim" izni kullanılır.
- ZIP dosyaları otomatik olarak /storage/emulated/0/ATMACA_ZIP içine yazılır.
- Kaynak kök seçilmiş olsa bile oluşturulan ZIP kendisini arşive eklemez.
- Aynı adlı ZIP varsa eskisini ezmez; (2), (3) şeklinde yeni ad oluşturur.

## Güvenlik
- Kaynak dosyaları silmez, taşımaz, yeniden adlandırmaz.
- Sıkıştırma seviyesi 0: büyük foto/video paketlerinde CPU harcanmaz.
- ZIP64 desteği java.util.zip tarafından otomatik kullanılır.
- İş bitince ZipFile ile tüm girdiler yeniden okunur; CRC ve toplam bayt/sayaç doğrulaması yapılır.
- Kaynaktan tek dosya bile okunamazsa işlem başarısız sayılır ve yarım ZIP silinir.
- Foreground service + wake lock ile ekran kapalıyken devam eder.
- İnternet izni yoktur.

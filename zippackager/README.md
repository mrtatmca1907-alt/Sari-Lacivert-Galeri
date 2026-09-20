# ATMACA ZIP Paketleyici

Android 13 odaklı, seçilen klasörü alt klasörleriyle birlikte tek ZIP'e paketleyen yerel APK.

## Temel kurallar
- Kaynak dosyaları silmez, taşımaz, yeniden adlandırmaz.
- Açılışta tüm telefonu taramaz; yalnız kullanıcının seçtiği klasörü işler.
- Fotoğraf/video gibi zaten sıkışmış içerikte CPU harcamamak için DEFLATE seviye 0 kullanır.
- ZIP64 desteğini Android'in java.util.zip uygulaması üzerinden otomatik kullanır.
- İş bitince arşivi baştan sona okuyup CRC doğrulaması yapar ve EOCD kaydını kontrol eder.
- Bir kaynak dosya okunamazsa işlem başarısız sayılır ve oluşturulan hedef ZIP silinmeye çalışılır.
- Foreground service + wake lock ile ekran kapalıyken devam eder.
- İnternet izni yoktur.

## Hedef
Tecno Spark 10 Pro / Android 13; minSdk 26, targetSdk 35.

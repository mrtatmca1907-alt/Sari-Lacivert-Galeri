# Sarı Lacivert Galeri V2 — Modern Core

Bu dal/zip, mevcut sarı-lacivert görünümü koruyup uygulama motorunu modern Android teknolojilerine taşıyan V2 tabanıdır.

## Android uyumluluğu
- **minSdk 26**: Android 8.0+
- **targetSdk 36**: Android 16 davranışları hedeflenir
- **compileSdk 37**: güncel Compose 1.12 gereksinimi; daha eski Android telefonlarda çalışmayı engellemez
- Android 13 için `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` izinleri desteklenir
- Android 14+ sınırlı fotoğraf seçimi (`READ_MEDIA_VISUAL_USER_SELECTED`) desteklenir

## Modern teknoloji tabanı
- Kotlin
- Jetpack Compose BOM 2026.08.00 / Compose 1.12
- Material 3
- Android Gradle Plugin 9.3.0 + Gradle 9.5
- Media3 / ExoPlayer 1.11.0
- DataStore 1.2.1
- WorkManager 2.11.2
- ExifInterface 1.4.2
- Scoped Storage + MediaStore

## Bu pakette çalışan hedefler
- Albümleri gerçek MediaStore klasör yoluna göre grupla
- Fotoğraf + video tarama
- Albüm kapağı, öğe sayısı, video rozeti
- 3 / 4 / 5 sütun
- Albüm ve medya sıralama
- Dosya/albüm/klasör adına göre arama
- Kamera uygulamasını açma
- Favoriler
- Fotoğraf görüntüleyici: yakınlaştırma, sürükleme, çift dokunma, kaydırma, tam ekran, döndürme
- Video: Media3 / ExoPlayer, kontrol çubuğu, kaldığı yeri hatırlama, yatay/dikey ekran
- Paylaşma
- EXIF / medya bilgisi
- Yeniden adlandırma (Android izin modeliyle)
- SAF hedef klasörü seçerek kopyalama ve taşıma
- Android sistem çöp kutusuna taşıma
- Çöp kutusundan geri yükleme
- Kalıcı silme için sistem onayı
- Slayt gösterisi ve ayarlanabilir süre
- Birebir aynı dosya taraması: SHA-256
- Benzer fotoğraf taraması: dHash + BK-tree + ayarlanabilir Hamming mesafesi
- Duplicate taraması WorkManager ile arka planda çalışır
- Duplicate ekranı hiçbir şeyi kendi kendine silmez

## Güvenlik / veri koruma
- Kopya/benzer taraması **otomatik silme yapmaz**.
- Kopyalama/taşıma/silme işlemleri Android'in izin ve onay akışlarına uyar.
- Geniş `MANAGE_EXTERNAL_STORAGE` izni kullanılmaz.

## Build
GitHub Actions workflow `.github/workflows/build-apk.yml` içindedir. Push sonrası `Sari-Lacivert-Galeri-V2-APK` artifact'i üretilir.

> Not: Bu kaynak paket burada statik olarak hazırlanıp XML/yapı kontrollerinden geçirilir. Gerçek Android derlemesi GitHub Actions üzerinde yapılır; ilk Actions derlemesinde ortaya çıkabilecek API/derleyici uyumsuzluğu log üzerinden düzeltilmelidir.

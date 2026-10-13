# ATMACA Canlı Dublaj

Android 13 için deneysel Facebook Reels canlı dublaj projesi.

## Mevcut durum
İlk APK yalnızca MediaProjection izin ekranını açar. Gerçek ses yakalama, otomatik dil algılama, çeviri, TTS ve orijinal sesi susturma henüz uygulanmadı. Android AudioPlaybackCapture Facebook'un ses paylaşım iznine bağlıdır; üçüncü taraf uygulama Facebook'un sesini bağımsız şekilde susturamaz. Tam senkronizasyon ve tüm diller garanti edilemez.

## Sonraki aşamalar
1. Foreground mediaProjection servisi ve AudioPlaybackCapture ile izin verilen uygulamaların PCM sesini yakalama.
2. Konuşma bölümlendirme ve dil tanıma.
3. Akış halinde Türkçe çeviri ve TTS.
4. Gecikme ölçümü ve testler.

GitHub Actions iş akışı debug APK derlemesi için tanımlanmıştır.
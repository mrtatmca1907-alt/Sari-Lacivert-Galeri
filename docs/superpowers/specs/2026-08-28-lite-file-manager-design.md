# ATMACA Hafif Dosya Yöneticisi — Tasarım

## Amaç
Samsung My Files hissine yakın, ancak özgün sarı-lacivert görünümlü, hızlı ve düşük bellek kullanan Android dosya yöneticisi. Galeri ile aynı depolama mantığını kullanacak ve medya değişiklikleri iki uygulamada da tutarlı görünecek.

## Kapsam
- Ana ekran: Dahili depolama, varsa SD kart, Son kullanılanlar, İndirilenler, Görseller, Videolar, Ses, Belgeler, APK'lar.
- Klasör görünümü: yalnızca açılan klasörün içeriğini yükler; cihazı baştan sona taramaz.
- Liste ve ızgara görünümü, isim/tarih/boyut sıralaması, arama ve tür filtresi.
- Çoklu seçim; kopyala, taşı, yeniden adlandır, sil, yeni klasör oluştur.
- Görsel/video dosyalarında `Galeride aç`; galeride ise `Dosya yöneticisinde göster` entegrasyonuna uygun URI/SAF mantığı.
- Büyük klasörlerde parçalı/lazy listeleme ve thumbnail cache; tam boyut görseller liste ekranında çözülmez.

## Android Depolama Yaklaşımı
- MediaStore: medya kategorileri, son kullanılanlar ve hızlı indeksli sorgular.
- Storage Access Framework / DocumentFile: kullanıcı tarafından seçilen klasörler ve genel dosya işlemleri.
- Android 10+ scoped storage kurallarına uyum; root veya MANAGE_EXTERNAL_STORAGE zorunlu olmayacak.
- Silme/taşıma gibi izin gerektiren işlemlerde Android'in sistem onay akışı kullanılacak.

## Mimari
Native Kotlin + Android SDK. UI thread yalnızca görünüm günceller; klasör okuma ve dosya işlemleri `Dispatchers.IO` üzerinde çalışır. RecyclerView/Lazy benzeri sanallaştırma ile görünmeyen satırlar bellekte tutulmaz. Dosya işlemleri bir `FileOps` katmanında, depolama gezintisi `StorageRepository` katmanında ayrılır.

## Arayüz
Samsung My Files'tan esinlenen sade ana sayfa: üstte arama; altında büyük kategori kartları; ardından depolama kartları. Klasör ekranında üst yol çubuğu, liste/ızgara anahtarı ve sıralama menüsü. Renkler lacivert zemin, sarı vurgu; Samsung ikon ve varlıkları kopyalanmayacak.

## Performans
- Açılışta tam disk taraması yok.
- Yalnızca aktif klasör sorgulanır.
- Thumbnail'lar küçük boyutlu ve cache'li yüklenir.
- Kopyalama/taşıma progress göstergeli ve UI dışı iş parçacığında yürür.
- Binlerce öğeli klasörlerde aşamalı yükleme hedeflenir.

## Hata Davranışı
İzin yoksa işlem yapılmaz ve hangi klasör için izin gerektiği açıkça gösterilir. Kopyalama/taşıma yarıda kalırsa kaynak dosya silinmez; taşıma ancak hedef yazımı başarıyla tamamlandıktan sonra kaynak silinerek commit edilir. Aynı isim varsa kullanıcıya yeniden adlandır/üzerine yaz/atla seçenekleri sunulur.

## Başarı Ölçütleri
1. Uygulama açılışında tam disk taraması yapmadan ana ekran hızlı gelir.
2. Bir klasör açıldığında yalnızca o klasör listelenir.
3. Kopyala/taşı/sil/yeniden adlandır/yeni klasör işlemleri scoped storage kurallarıyla çalışır.
4. Taşıma sonrası kaynakta hayalet kopya kalmaz; başarısız taşıma kaynak dosyayı korur.
5. Görsel/video galeride açılabilir ve URI paylaşımı iki uygulama arasında uyumludur.
6. CI'de unit testler ve debug APK derlemesi başarıyla tamamlanır.

## İlk Sürüm Dışı
ZIP/RAR arşiv yöneticisi, ağ depolama, FTP/SMB, root dosya sistemi, bulut senkronizasyonu ve gelişmiş APK analizörü ilk sürümde yoktur.

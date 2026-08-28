# Sarı-Lacivert Hafif Galeri Design

## Amaç
Samsung Gallery hissine yakın, fakat daha küçük ve hızlı bir native Android galeri: açılışta klasörleri MediaStore üzerinden gösterir; bir klasöre girince yalnızca o klasörün foto/video öğelerini yükler; thumbnail üretimini arka planda yapar; fotoğraflarda pinch/double-tap zoom, videolarda yerleşik oynatma, çoklu seçim ve güvenli silme/kopyalama/taşıma sunar.

## Performans İlkeleri
- Dosya sistemini recursive tarama yok; MediaStore indeksini kullan.
- Ana thread üzerinde medya sorgusu/bitmap decode yok.
- RecyclerView virtualization ve view-holder başına iptal edilebilir thumbnail işi.
- Tam çözünürlük resmi doğrudan RAM'e almak yerine ekran boyutuna göre sample-size kullan.
- Klasör değişikliklerinde yalnızca ilgili sorguyu yenile.

## Mimari
- Native Kotlin + Android Views.
- `MediaRepository`: albüm sayımları ve albüm içi öğeler.
- `AlbumAdapter` / `MediaGridAdapter`: RecyclerView.
- `ViewerActivity`: ViewPager2; foto sayfasında `ZoomImageView`, video sayfasında `VideoView`.
- `ImageSample`: decode örnekleme hesabı; unit-testli saf Kotlin.
- `FileOps`: Storage Access Framework hedef klasörüne kopyalama; taşıma için kopyalama sonrası MediaStore silme isteği.
- `AlbumActivity`: çoklu seçim, sil/kopyala/taşı.

## Depolama Davranışı
- Android 13+: READ_MEDIA_IMAGES + READ_MEDIA_VIDEO.
- Android 12 ve altı: READ_EXTERNAL_STORAGE.
- Silme: Android 11+ MediaStore.createDeleteRequest ile sistem onayı.
- Kopyala/Taşı: kullanıcı ACTION_OPEN_DOCUMENT_TREE ile hedef klasörü seçer; DocumentFile üzerinden stream kopyalanır. Taşıma kopya tamamlandıktan sonra silme onayı ister.

## Kabul Kriterleri
- Uygulama açılınca klasör listesi MediaStore'dan gelir; recursive tarama yapmaz.
- Albüm ekranı 3 sütun grid ve arka plan thumbnail yükleme kullanır.
- Fotoğraflar pinch ve double-tap zoom yapar; videolar ekran içinde oynar.
- Albüm içinde sağlıklı çoklu seçim, silme, kopyalama ve taşıma akışı vardır.
- minSdk 29, targetSdk 35, compileSdk 35.

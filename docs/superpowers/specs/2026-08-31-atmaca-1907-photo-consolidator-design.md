# ATMACA 1907 Fotoğraf Toplayıcı — Tasarım

## Amaç
Telefondaki MediaStore tarafından görülebilen tüm fotoğrafları tek bir güvenli akışta toparlamak, birebir aynı kopyaları kaldırmak ve kalan benzersiz fotoğrafların tamamını doğrudan `Pictures/1907` klasörüne taşımak.

## Kapsam
- Sadece fotoğraflar; videolar bu sürümün dışında.
- Android 13 hedef cihazda çalışacak.
- Kaynaklar: MediaStore'un gördüğü tüm cihaz fotoğrafları (Camera/DCIM, Pictures, Screenshots, Download, WhatsApp vb. erişilebilir medya klasörleri).
- `Android/data` gibi Android tarafından erişimi kısıtlanan özel alanlar kapsam dışı.
- Tek hedef klasör: `Pictures/1907`.
- Alt klasör, 50'li paketleme veya yeniden bölme yapılmayacak.

## Temel Akış
1. **Tarama**
   - Tüm erişilebilir fotoğraflar MediaStore üzerinden listelenir.
   - `Pictures/1907` içindeki mevcut fotoğraflar da taramaya dahil edilir; böylece uygulama tekrar çalıştırıldığında aynı dosyaları yeniden üretmez.
   - Her kayıt için URI, mevcut relative path, dosya adı, boyut, MIME türü ve tarih bilgisi tutulur.

2. **Kesin kopya doğrulama**
   - Bütün fotoğraflar önce dosya boyutuna göre gruplanır.
   - Aynı boyutta birden fazla dosya bulunan gruplarda SHA-256 stream üzerinden hesaplanır.
   - SHA-256 aynıysa dosya adları farklı olsa bile birebir aynı kopya kabul edilir.
   - SHA-256 farklıysa dosya adları aynı olsa bile iki fotoğraf da korunur.
   - `(1)`, `_copy`, `copy`, `-kopya` gibi ad ekleri kullanıcıya kopya adayını açıklamak için kullanılabilir; isim tek başına silme kararı vermez.

3. **Kopya temizliği**
   - Her SHA-256 grubundan tek bir survivor bırakılır.
   - Tercih sırası: zaten `Pictures/1907` içinde bulunan doğrulanmış kopya > daha eski/orijinal görünen ad > diğerleri.
   - Fazla kopyalar survivor dosyasının hâlâ erişilebilir olduğu doğrulanmadan kaldırılmaz.
   - Başka uygulamalara ait medyada Android kullanıcı onayı gerekiyorsa resmi MediaStore onay akışları kullanılır.

4. **Tek klasöre taşıma**
   - Kopya temizliği bittikten sonra bütün survivor fotoğraflar `Pictures/1907` içine taşınır.
   - `Pictures/1907` içinde zaten bulunan survivor tekrar taşınmaz.
   - Aynı dosya adına sahip fakat içeriği farklı iki fotoğraf varsa veri kaybını önlemek için ikinci dosyaya çakışmasız bir ad verilir; körlemesine üzerine yazılmaz.
   - Android doğrudan relative path güncellemesine izin veriyorsa MediaStore `RELATIVE_PATH` güncellemesi tercih edilir.
   - Doğrudan taşıma mümkün değilse semantik `hedefe yaz → hedefi doğrula → kaynağı kaldır` olur.

## Veri Kaybını Önleme
- Kopya silme yalnızca SHA-256 eşleşmesinden sonra yapılır.
- Aynı isim tek başına silme veya üzerine yazma nedeni değildir.
- Benzersiz bir fotoğraf doğrulanmış hedef oluşmadan kaynaktan kaldırılmaz.
- Kaynak klasörlerin kendisi topluca silinmez; yalnızca fotoğraf dosyaları taşınır.
- Uygulama yarıda kapanırsa yeniden açıldığında gerçek MediaStore durumu tekrar taranır ve `Pictures/1907` içindeki mevcut dosyalar hesaba katılır.

## Durum Kaydı ve Devam Etme
- İşlem oturumu uygulamanın yerel durum dosyasında tutulur; ağır bir veritabanı bağımlılığı eklenmez.
- Durum alanları: `phase`, `scanned`, `duplicates`, `moved`, `failed`, `currentName`, `updatedAt`.
- Yeniden başlatmada önce gerçek MediaStore durumu ile kayıt uzlaştırılır; yalnızca eski oturum kaydına güvenilmez.
- Hata alan dosyalar sonraki çalıştırmada yeniden denenebilir.

## Kullanıcı Arayüzü
Tek bir sade ekran yeterli:
- `Tara ve Toparla` düğmesi.
- Sayaçlar: `Tarandı`, `Kopya`, `Taşındı`, `Kalan`, `Hata`.
- Aktif işlem: `Taranıyor`, `Kopyalar doğrulanıyor`, `Kopyalar temizleniyor`, `1907'ye taşınıyor`, `Tamamlandı`.
- Aktif dosya adı.
- İşlem tamamlanınca `Pictures/1907` içindeki toplam benzersiz fotoğraf sayısı gösterilir.

## Android Depolama Modeli
- Tarama için MediaStore kullanılacak.
- Android 13 için `READ_MEDIA_IMAGES` izni kullanılacak.
- Hedef `Pictures/1907` MediaStore/Scoped Storage uyumlu biçimde yönetilecek.
- Başka uygulamalara ait medyada değişiklik için gereken kullanıcı onayları resmi MediaStore akışıyla alınacak.
- `MANAGE_EXTERNAL_STORAGE` zorunlu tutulmayacak.

## Performans
- Dosyalar RAM'e topluca yüklenmeyecek.
- SHA-256 yalnızca aynı boyutta birden fazla dosya bulunan gruplarda hesaplanacak.
- Hash hesapları ve taşıma işleri `Dispatchers.IO` / WorkManager üzerinde çalışacak.
- Arayüz ana iş parçacığını bloklamayacak.
- İlerleme WorkManager progress verisiyle ekrana aktarılacak.

## Test Stratejisi
- Aynı boyut + aynı hash grubundan tek survivor seçildiğini doğrulayan birim test.
- Aynı isim + farklı hash senaryosunda iki dosyanın da korunduğunu doğrulayan test.
- `Pictures/1907` içinde olan kopyanın survivor olarak tercih edildiğini doğrulayan test.
- Çakışan farklı dosya adlarında güvenli yeni isim üretildiğini doğrulayan test.
- Hedefte bulunan fotoğrafın ikinci kez taşınmadığını doğrulayan test.
- Yeniden başlatma/yarım kalmış işlem için idempotent plan üretimi testi.
- GitHub Actions üzerinde `:app:testDebugUnitTest` ve `:app:assembleDebug` doğrulaması.

## Başarı Kriterleri
- İşlem sonunda erişilebilir her benzersiz fotoğraf içeriği yalnızca bir kez kalır.
- Bütün survivor fotoğraflar doğrudan `Pictures/1907` içindedir; alt klasör oluşturulmaz.
- Hiçbir farklı içerik yalnızca adı aynı diye silinmez veya üzerine yazılmaz.
- Uygulama yeniden çalıştırıldığında yeni kopya oluşturmaz.
- İşlem yarıda kesilse bile daha önce güvenle taşınmış fotoğraflar kaybolmaz veya tekrar çoğalmaz.

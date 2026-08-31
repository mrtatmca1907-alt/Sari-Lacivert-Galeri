# ATMACA 1907 Fotoğraf Toplayıcı — Tasarım

## Amaç
Telefondaki MediaStore tarafından görülebilen tüm fotoğrafları tek bir güvenli akışta toparlamak, gerçekten aynı olan kopyaları kaldırmak ve kalan benzersiz fotoğrafları `Pictures/1907` altında 50'şerli klasörlere taşımak.

## Kapsam
- Sadece fotoğraflar; videolar bu sürümün dışında.
- Android 13 hedef cihazda çalışacak.
- Kaynaklar: MediaStore'un gördüğü tüm cihaz fotoğrafları (Camera/DCIM, Pictures, Screenshots, Download, WhatsApp vb. erişilebilir medya klasörleri).
- `Android/data` gibi Android tarafından erişimi kısıtlanan özel alanlar kapsam dışı.
- Hedef kök: `Pictures/1907`.
- Son klasörler: `001`, `002`, `003` ...; her klasörde en fazla 50 benzersiz fotoğraf.

## Temel Akış
1. **Tarama**
   - Tüm erişilebilir fotoğraflar MediaStore üzerinden listelenir.
   - `Pictures/1907` içindeki mevcut fotoğraflar da taramaya dahil edilir; böylece uygulama yeniden çalıştırıldığında kopya üretmez.
   - Her kayıt için URI, mevcut yol/relative path, dosya adı, boyut, MIME türü ve tarih bilgisi tutulur.

2. **Kopya adayı bulma**
   - Dosya adları normalize edilir; `(1)`, `_copy`, `copy`, `-kopya` gibi çoğalma ekleri arayüzde kopya adaylarını açıklamak için kullanılır.
   - Ancak isim tek başına silme kararı vermez ve yalnızca isim eşleşmesine güvenilmez.
   - Bütün fotoğraflar önce dosya boyutuna göre gruplanır. Aynı boyutta yalnızca bir dosya varsa pahalı hash hesabına gerek yoktur; aynı boyutta birden fazla dosya varsa kesin doğrulama aşamasına geçilir.

3. **Kesin kopya doğrulama**
   - Aynı boyut grubundaki dosyaların SHA-256 değeri stream üzerinden hesaplanır.
   - SHA-256 aynıysa, dosya adları farklı olsa bile içerikler birebir aynı kabul edilir.
   - SHA farklıysa, isimleri aynı olsa bile iki dosya da korunur.
   - Böylece hem `IMG_1.jpg` / `IMG_1 (1).jpg` gibi açık kopyalar hem de sonradan yeniden adlandırılmış birebir kopyalar yakalanır.

4. **Kopya temizliği**
   - Her birebir kopya grubundan tek bir survivor seçilir.
   - Tercih sırası: zaten `Pictures/1907` içinde olan doğrulanmış örnek > daha eski/orijinal görünen ad > diğerleri.
   - Fazla kopyalar ancak survivor dosyasının erişilebilir ve doğrulanmış olduğu teyit edildikten sonra silinir.
   - Android'in gerekli sistem silme/yazma onayları kullanılır; uygulama izin modelini atlamaz.

5. **Taşıma ve yeniden paketleme**
   - Kalan benzersiz fotoğraflar deterministik olarak dosya adına göre sıralanır.
   - `Pictures/1907/001`, `002`, `003` ... klasörleri oluşturulur.
   - Her klasöre en fazla 50 fotoğraf taşınır.
   - Son klasör 50'den az dosya içerebilir.
   - Aynı dosya adına sahip fakat içeriği farklı iki fotoğraf varsa veri kaybını önlemek için ikinci dosyaya çakışmasız ad verilir; dosya atlanmaz ve üzerine körlemesine yazılmaz.
   - Android sürümü veya dosyanın sahipliği doğrudan yol güncellemesine izin vermiyorsa taşıma semantiği `hedefe yaz → hedefi doğrula → kaynağı sil` olarak uygulanır.

## Veri Kaybını Önleme
- Taşıma, `hedef oluşturuldu/doğrulandı → kaynak kaldırıldı` sırasıyla yapılır.
- Uygulama hiçbir benzersiz fotoğrafı doğrulanmış hedef olmadan silmez.
- Aynı dosya adı, tek başına üzerine yazma nedeni değildir.
- Kopya silme yalnızca SHA-256 eşleşmesinden sonra yapılır.
- İşlem sırasında uygulama kapanırsa tekrar çalıştırıldığında `Pictures/1907` yeniden taranır ve mevcut hedefler hesaba katılır; aynı fotoğraf yeniden çoğaltılmaz.
- Kaynak klasörler otomatik olarak topluca silinmez; yalnızca fotoğraf dosyaları taşınır. Böylece başka uygulamaların kullandığı klasör yapıları bozulmaz.

## Durum Kaydı ve Devam Etme
- İşlem oturumu yerel veritabanında tutulur.
- Her dosya için durum: `BEKLIYOR`, `HASH_OK`, `KOPYA`, `TASINDI`, `SILINDI`, `HATA`.
- Uygulama/telefon kapanırsa tamamlanan dosyalar yeniden işlenmez.
- Hata alan dosyalar ayrı listelenir ve işlem sonunda tekrar denenebilir.
- Yeniden başlatmada önce gerçek MediaStore durumu ile kayıtlar uzlaştırılır; yalnızca veritabanındaki eski duruma körlemesine güvenilmez.

## Kullanıcı Arayüzü
Tek ana ekran yeterli:
- `Taramayı Başlat`
- Sayaçlar: `Tarandı`, `Benzersiz`, `Kopya`, `Taşındı`, `Kalan`, `Hata`
- Aktif dosya adı ve mevcut işlem
- `Başlat / Devam Et`
- `Hataları Göster`

İşlem başlamadan önce özet gösterilir:
- toplam bulunan fotoğraf
- kesin kopya sayısı
- kalacak benzersiz fotoğraf sayısı
- oluşturulacak yaklaşık 50'lik klasör sayısı

## Android Depolama Modeli
- Tarama için MediaStore kullanılacak.
- Android 13 için `READ_MEDIA_IMAGES` izni kullanılacak.
- Hedef `Pictures/1907` MediaStore/Scoped Storage uyumlu biçimde yönetilecek.
- Başka uygulamalara ait medya üzerinde Android sisteminin kullanıcı onayı gerektirdiği durumlarda resmi MediaStore onay akışları kullanılacak.
- `MANAGE_EXTERNAL_STORAGE` zorunlu tutulmayacak.

## Performans
- Hash hesapları arka plan işçisi üzerinde yapılacak.
- Dosyalar topluca RAM'e alınmayacak; stream ile işlenecek.
- SHA-256 yalnızca aynı boyuta sahip birden fazla dosya bulunan gruplarda hesaplanacak.
- Büyük arşivlerde arayüz ana iş parçacığında bloklanmayacak.
- Uzun işlemlerde ilerleme kalıcı olarak kaydedilecek ve uygulama tekrar açıldığında kaldığı yerden devam edilecek.

## Test Stratejisi
- İsim normalizasyonu için birim testleri.
- 50'li klasör dağıtımı için birim testleri.
- Aynı ad/farklı hash dosyalarının ikisinin de korunduğunu doğrulayan test.
- Farklı ad/aynı hash senaryosunda tek survivor kaldığını doğrulayan test.
- Aynı boyut/farklı hash senaryosunda hiçbir dosyanın yanlışlıkla silinmediğini doğrulayan test.
- Yarım kalmış taşıma sonrası yeniden başlatmada çoğalma olmadığını doğrulayan test.
- 49/50/51/100/101 dosyalık paketleme sınır testleri.
- Android CI derlemesi ve mümkün olan MediaStore entegrasyon testleri.

## Başarı Kriterleri
- İşlem sonunda erişilebilir fotoğrafların her benzersiz içeriği yalnızca bir kez kalır.
- Hiçbir farklı içerik yalnızca adı aynı diye silinmez.
- Tüm benzersiz fotoğraflar `Pictures/1907/NNN` altında bulunur.
- Her `NNN` klasöründe en fazla 50 dosya vardır.
- Uygulama yeniden çalıştırıldığında yeni kopya üretmez.
- İşlem yarıda kesilse bile daha önce güvenle taşınmış dosyalar kaybolmaz veya tekrar çoğalmaz.

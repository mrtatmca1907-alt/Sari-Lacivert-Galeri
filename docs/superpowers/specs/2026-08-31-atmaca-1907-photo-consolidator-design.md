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
   - Dosya adları normalize edilir.
   - `(1)`, `_copy`, `copy`, `-kopya`, benzeri tipik çoğalma ekleri aday eşleştirmede dikkate alınır.
   - Aynı/benzer ada sahip dosyalar yalnızca "aday" sayılır; isim tek başına silme kararı vermez.

3. **Kesin kopya doğrulama**
   - Önce dosya boyutu karşılaştırılır.
   - Boyut eşleşen adaylarda SHA-256 hesaplanır.
   - SHA-256 aynıysa dosyalar birebir aynı kabul edilir.
   - SHA farklıysa, isimleri aynı olsa bile iki dosya da korunur.

4. **Kopya temizliği**
   - Her birebir kopya grubundan tek bir "survivor" seçilir.
   - Tercih sırası: zaten `Pictures/1907` içinde olan sağlam örnek > daha eski/orijinal görünen ad > diğerleri.
   - Fazla kopyalar ancak survivor dosyasının erişilebilir ve doğrulanmış olduğu teyit edildikten sonra silinir.
   - Android'in gerekli sistem silme/yazma onayları kullanılır; uygulama gizlice izin atlamaz.

5. **Taşıma ve yeniden paketleme**
   - Kalan benzersiz fotoğraflar deterministik olarak dosya adına göre sıralanır.
   - `Pictures/1907/001`, `002`, `003` ... klasörleri oluşturulur.
   - Her klasöre en fazla 50 fotoğraf taşınır.
   - Son klasör 50'den az dosya içerebilir.
   - Aynı dosya adına sahip fakat içeriği farklı iki fotoğraf varsa veri kaybını önlemek için ikinci dosyaya çakışmasız ad verilir; dosya atlanmaz ve üzerine körlemesine yazılmaz.

## Veri Kaybını Önleme
- Taşıma, "hedef oluşturuldu/doğrulandı → kaynak kaldırıldı" sırasıyla yapılır.
- Uygulama hiçbir benzersiz fotoğrafı doğrulanmış hedef olmadan silmez.
- Aynı dosya adı, tek başına üzerine yazma nedeni değildir.
- Kopya silme yalnızca SHA-256 eşleşmesinden sonra yapılır.
- İşlem sırasında uygulama kapanırsa tekrar çalıştırıldığında `Pictures/1907` yeniden taranır ve mevcut hedefler hesaba katılır; aynı fotoğraf yeniden çoğaltılmaz.

## Durum Kaydı ve Devam Etme
- İşlem oturumu yerel veritabanında tutulur.
- Her dosya için durum: `BEKLIYOR`, `HASH_OK`, `KOPYA`, `TASINDI`, `SILINDI`, `HATA`.
- Uygulama/telefon kapanırsa tamamlanan dosyalar yeniden işlenmez.
- Hata alan dosyalar ayrı listelenir ve işlem sonunda tekrar denenebilir.

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
- Başka uygulamalara ait medya üzerinde Android sisteminin kullanıcı onayı gerektirdiği durumlarda `MediaStore.createWriteRequest` / `createDeleteRequest` benzeri resmi akışlar kullanılacak.
- `MANAGE_EXTERNAL_STORAGE` zorunlu tutulmayacak.

## Performans
- Hash hesapları arka plan işçisi üzerinde yapılacak.
- Dosyalar topluca RAM'e alınmayacak; stream ile işlenecek.
- Aynı boyuttaki/isim adayı olmayan her fotoğrafa gereksiz SHA-256 uygulanmayacak.
- Büyük arşivlerde arayüz ana iş parçacığında bloklanmayacak.

## Test Stratejisi
- İsim normalizasyonu için birim testleri.
- 50'li klasör dağıtımı için birim testleri.
- Aynı ad/farklı hash dosyalarının ikisinin de korunduğunu doğrulayan test.
- Farklı ad/aynı hash senaryosunda tek survivor kaldığını doğrulayan test.
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

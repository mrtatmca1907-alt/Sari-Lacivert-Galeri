# ATMACA Gallery Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tecno Spark 10 Pro / Android 13 için ATMACA Galeri'de eksik medya-albüm yükleme, görüntü kalitesi, görüntüleyici hareketleri, kırpma ve sade menü düzenini kararlı hale getirmek.

**Architecture:** Büyük `GalleryApp.kt` dosyasını doğrudan yeniden yazmak yerine mevcut kontrollü `coreapp/apply_runtime_fixes.py` katmanı ile küçük ve doğrulanabilir çalışma zamanı yamaları uygulanacak; repository/view-model davranışları mümkün olduğunda doğrudan kaynak dosyalarda tutulacak. Her davranış önce sözleşme testiyle kilitlenecek, CI'da kırmızı doğrulandıktan sonra üretim değişikliği yapılacak.

**Tech Stack:** Kotlin, Jetpack Compose, MediaStore, Media3/ExoPlayer, JUnit4, GitHub Actions, Python build-time source patch.

**Spec:** 2026-09-04 sohbetindeki kullanıcı ekran görüntüleri ve kabul edilmiş davranışlar.

## Global Constraints

- Hedef cihaz: Tecno Spark 10 Pro, Android 13 / HiOS 13.
- Kökten dosya seçimi çalışan haliyle korunacak.
- APK boyutu öncelik değil; kararlılık ve işlev öncelikli.
- CI başarısı telefon davranışının kanıtı sayılmayacak; son doğrulama fiziksel cihazda yapılacak.
- Medya görünümünde 480 gibi yapay sonlanma olmayacak.
- Albümler MediaStore Images + Video + Files kaynaklarından birleştirilerek eksiksiz tutulacak.
- Tam ekran fotoğraf decode'u orijinal çözünürlükte olacak; grid önizlemeleri yüksek kaliteli olacak.
- Tek ana galeri/klasör görünümü ve sade üç nokta menüsü kullanılacak.

---

### Task 1: Full-quality viewer and high-quality grid

**Files:**
- Modify: `coreapp/apply_runtime_fixes.py`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/FullQualityViewerContractTest.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/MediaThumbnailQualityContractTest.kt`

**Interfaces:**
- Consumes: `calculateViewerDecodeSample(...)`, `loadThumbnailCompat(...)`
- Produces: original-resolution viewer decode; `HIGH_QUALITY_THUMBNAIL_EDGE` based grid thumbnails.

- [x] **Step 1: Write failing tests**
- [x] **Step 2: Verify CI fails for missing quality behavior**
- [x] **Step 3: Patch viewer sampling and thumbnail edge**
- [ ] **Step 4: Verify all tests/build pass**

### Task 2: Cursor/keyset media paging past 480

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt` or `coreapp/apply_runtime_fixes.py`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt` or runtime patch
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/KeysetPagingContractTest.kt`

**Interfaces:**
- Produces: page continuation using last `(dateAdded, id)` instead of relying only on OEM `OFFSET`.

- [ ] **Step 1: Write failing source/behavior contract requiring cursor-based continuation**
- [ ] **Step 2: Verify RED in CI**
- [ ] **Step 3: Implement keyset selection `(DATE_ADDED < ? OR (DATE_ADDED = ? AND _ID < ?))`**
- [ ] **Step 4: Verify GREEN and build**

### Task 3: Complete OEM-safe album discovery and album contents

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/CompleteAlbumMergeContractTest.kt`

**Interfaces:**
- Produces: merged albums from Images, Video and Files without dropping partial non-empty sources; album contents use OEM-safe collection scan.

- [ ] **Step 1: Reproduce partial-source merge gap with failing test**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Merge source identities by path/bucket and retain union counts/covers**
- [ ] **Step 4: Verify GREEN and regression suite**

### Task 4: Viewer swipe, zoom settle and crop full-screen mapping

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/CropEditor.kt`
- Test: viewer/crop contract tests

**Interfaces:**
- Produces: one-finger horizontal pager ownership at 1x, no reverse/end bounce masquerading as next item, released transform settles centered; crop canvas uses actual available image area without half-screen shrink.

- [ ] **Step 1: Add failing contracts for pager ownership/release/crop viewport**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement minimal gesture/layout fixes**
- [ ] **Step 4: Verify GREEN**

### Task 5: Single gallery surface and elegant three-dot menu

**Files:**
- Modify: `coreapp/apply_runtime_fixes.py`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/GalleryMenuLayoutContractTest.kt`

**Interfaces:**
- Produces: no bottom `Medya` tab; Albums/folder view is primary; menu toggles `Tüm klasör içeriğini göster` / `Klasör görünümüne geç`; settings/filter/sort/columns/camera/slideshow/tools live in compact `⋮` menu.

- [ ] **Step 1: Write failing menu/navigation contract**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Patch Scaffold/top menu/section transitions**
- [ ] **Step 4: Verify GREEN**

### Task 6: Viewer chrome, pull-to-refresh and landscape video

**Files:**
- Modify: `coreapp/apply_runtime_fixes.py`
- Modify: video viewer source if required
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/ViewerChromeContractTest.kt`

**Interfaces:**
- Produces: no refresh icon; downward pull refresh; crop/info in bottom bar; favorite removed; share/slideshow under `⋮`; back action moved to opposite lower corner; video follows landscape orientation/fullscreen.

- [ ] **Step 1: Write failing contracts**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement chrome/pull/video orientation behavior**
- [ ] **Step 4: Verify GREEN**

### Task 7: Final regression APK

**Files:**
- No new source unless a regression is found.

- [ ] **Step 1: Run full unit suite and debug APK build in GitHub Actions**
- [ ] **Step 2: Inspect every workflow step for success**
- [ ] **Step 3: Download artifact and verify APK size/hash**
- [ ] **Step 4: Hand APK to user for physical Tecno test; do not claim phone bugs fixed until tested**

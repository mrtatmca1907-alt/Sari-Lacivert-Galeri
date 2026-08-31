# ATMACA 1907 Fotoğraf Toplayıcı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Telefonda MediaStore tarafından görülen tüm fotoğraflardan birebir kopyaları kaldırıp kalan her benzersiz fotoğrafı doğrudan `Pictures/1907` klasörüne güvenli biçimde taşıyan ayrı bir Android APK üretmek.

**Architecture:** Mevcut galeri modülüne dokunmadan yeni `collector1907` Android application modülü oluşturulacak. Saf Kotlin politika sınıfı kopya/survivor/isim çakışması kararlarını verir; `CollectorRepository` MediaStore taraması, SHA-256 ve RELATIVE_PATH güncellemelerini yapar; Compose `MainActivity` izin/onay state-machine'ini yürütür. Android 12+ cihazlarda isteğe bağlı `MANAGE_MEDIA` özel erişimi resmi `Settings.ACTION_REQUEST_MANAGE_MEDIA` ekranından istenir; yoksa `MediaStore.createDeleteRequest/createWriteRequest` kullanıcı onayıyla çalışır.

**Tech Stack:** Android API 26+, target/compile mevcut repo ile 36/37; Kotlin; Jetpack Compose Material 3; Coroutines; MediaStore; JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-31-atmaca-1907-photo-consolidator-design.md`

## Global Constraints

- Sadece fotoğraflar işlenecek; videolar dahil edilmeyecek.
- Tek hedef `Pictures/1907/`; alt klasör oluşturulmayacak.
- Aynı isim silme nedeni değildir; kesin kopya yalnızca aynı boyut + aynı SHA-256 ile belirlenir.
- Farklı içerik aynı ada sahipse ikisi de korunur ve hedefte çakışmasız ad üretilir.
- Kopya temizliği bitmeden toplu taşıma aşamasına geçilmez.
- `MANAGE_EXTERNAL_STORAGE` kullanılmayacak.
- Android'in MediaStore kullanıcı onay modeli atlanmayacak.
- Yeniden çalıştırma idempotent olacak: hedefte bulunan fotoğraf tekrar taşınmayacak.

---

### Task 1: Ayrı collector1907 modülü ve saf politika motoru

**Files:**
- Modify: `settings.gradle.kts`
- Create: `collector1907/build.gradle.kts`
- Create: `collector1907/src/main/AndroidManifest.xml`
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorModels.kt`
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorPolicy.kt`
- Test: `collector1907/src/test/kotlin/com/atmaca/toplayici1907/CollectorPolicyTest.kt`

**Interfaces:**
- Produces: `PhotoRecord(id: Long, uri: String, name: String, size: Long, relativePath: String, dateAdded: Long)`
- Produces: `DuplicateSet(survivor: PhotoRecord, duplicates: List<PhotoRecord>)`
- Produces: `CollectorPolicy.chooseSurvivor(List<PhotoRecord>): PhotoRecord`
- Produces: `CollectorPolicy.isTarget(PhotoRecord): Boolean`
- Produces: `CollectorPolicy.uniqueName(original: String, reserved: MutableSet<String>): String`

- [ ] **Step 1: Write failing policy tests**

```kotlin
@Test fun targetCopyWinsSurvivor() {
    val outside = p(1, "IMG.jpg", "DCIM/Camera/")
    val target = p(2, "IMG (1).jpg", "Pictures/1907/")
    assertEquals(target, CollectorPolicy.chooseSurvivor(listOf(outside, target)))
}

@Test fun sameNameDifferentContentCanReceiveUniqueTargetNames() {
    val reserved = mutableSetOf("IMG.jpg")
    assertEquals("IMG_2.jpg", CollectorPolicy.uniqueName("IMG.jpg", reserved))
}

@Test fun targetDetectionAcceptsNormalizedSlash() {
    assertTrue(CollectorPolicy.isTarget(p(1, "x.jpg", "Pictures/1907")))
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: FAIL because `CollectorPolicy` and module do not yet exist.

- [ ] **Step 3: Implement minimal models/policy and module shell**

```kotlin
object CollectorPolicy {
    const val TARGET = "Pictures/1907/"

    fun isTarget(photo: PhotoRecord): Boolean =
        photo.relativePath.trim('/').equals("Pictures/1907", ignoreCase = true)

    fun chooseSurvivor(group: List<PhotoRecord>): PhotoRecord =
        group.sortedWith(
            compareByDescending<PhotoRecord> { isTarget(it) }
                .thenBy { copySuffixScore(it.name) }
                .thenBy { it.dateAdded }
                .thenBy { it.id }
        ).first()

    fun uniqueName(original: String, reserved: MutableSet<String>): String {
        if (reserved.add(original.lowercase())) return original
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "${base}_${i}${ext}"
            if (reserved.add(candidate.lowercase())) return candidate
            i++
        }
    }
}
```

- [ ] **Step 4: Run policy tests GREEN**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add 1907 collector policy module`

---

### Task 2: MediaStore tarama ve kesin SHA-256 kopya planı

**Files:**
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorRepository.kt`
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/ConsolidationPlanner.kt`
- Test: `collector1907/src/test/kotlin/com/atmaca/toplayici1907/ConsolidationPlannerTest.kt`

**Interfaces:**
- Produces: `CollectorRepository.loadPhotos(): List<PhotoRecord>`
- Produces: `CollectorRepository.sha256(photo: PhotoRecord): String?`
- Produces: `ConsolidationPlanner.plan(photos, hashProvider): ConsolidationPlan`
- `ConsolidationPlan` fields: `survivors`, `duplicates`, `hashFailures`.

- [ ] **Step 1: Write failing planner tests**

```kotlin
@Test fun equalSizeAndHashProducesOneDuplicate() {
    val a = p(1, "A.jpg", "DCIM/", size = 100)
    val b = p(2, "A (1).jpg", "Pictures/", size = 100)
    val plan = ConsolidationPlanner.plan(listOf(a, b)) { "same" }
    assertEquals(1, plan.survivors.size)
    assertEquals(1, plan.duplicates.size)
}

@Test fun equalSizeDifferentHashPreservesBoth() {
    val a = p(1, "A.jpg", "DCIM/", size = 100)
    val b = p(2, "A.jpg", "Pictures/", size = 100)
    val plan = ConsolidationPlanner.plan(listOf(a, b)) { if (it.id == 1L) "x" else "y" }
    assertEquals(2, plan.survivors.size)
    assertTrue(plan.duplicates.isEmpty())
}
```

- [ ] **Step 2: Run RED**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: FAIL because planner does not exist.

- [ ] **Step 3: Implement planner**

Algorithm:
```kotlin
val bySize = photos.filter { it.size > 0 }.groupBy { it.size }
for (sizeGroup in bySize.values) {
    if (sizeGroup.size == 1) survivors += sizeGroup.single()
    else {
        val byHash = sizeGroup.mapNotNull { p -> hashProvider(p)?.let { it to p } }.groupBy({ it.first }, { it.second })
        byHash.values.forEach { group ->
            val survivor = CollectorPolicy.chooseSurvivor(group)
            survivors += survivor
            duplicates += group.filterNot { it.id == survivor.id }
        }
    }
}
```
Zero-size/hash-failure items are never deleted; they are preserved as survivors with failure telemetry.

- [ ] **Step 4: Implement MediaStore query/hash**

Query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` columns `_ID`, `DISPLAY_NAME`, `SIZE`, `DATE_ADDED`, `RELATIVE_PATH`; stream SHA-256 using 1 MiB buffer. Do not load bitmap pixels.

- [ ] **Step 5: Run tests GREEN**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: plan exact duplicate consolidation`

---

### Task 3: Güvenli MediaStore silme/taşıma işlemleri ve durum kaydı

**Files:**
- Modify: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorRepository.kt`
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorStateStore.kt`
- Test: `collector1907/src/test/kotlin/com/atmaca/toplayici1907/CollectorPolicyTest.kt`

**Interfaces:**
- Produces: `CollectorRepository.deleteRequest(uris: List<Uri>): IntentSender`
- Produces: `CollectorRepository.writeRequest(uris: List<Uri>): IntentSender`
- Produces: `CollectorRepository.moveTo1907(photo: PhotoRecord, targetName: String): Boolean`
- Produces: `CollectorRepository.exists(uri: String): Boolean`
- Produces: `CollectorStateStore.save(CollectorProgress)` / `load(): CollectorProgress?`

- [ ] **Step 1: Add unique-name/idempotence tests**

```kotlin
@Test fun alreadyTargetPhotoNeedsNoMove() {
    assertTrue(CollectorPolicy.isTarget(p(1, "A.jpg", "Pictures/1907/")))
}

@Test fun multipleNameConflictsAreStable() {
    val names = mutableSetOf("a.jpg")
    assertEquals("a_2.jpg", CollectorPolicy.uniqueName("a.jpg", names))
    assertEquals("a_3.jpg", CollectorPolicy.uniqueName("a.jpg", names))
}
```

- [ ] **Step 2: Run RED/GREEN for policy additions**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: PASS after minimal policy adjustment if needed.

- [ ] **Step 3: Implement requests and move**

```kotlin
fun moveTo1907(photo: PhotoRecord, targetName: String): Boolean {
    val uri = Uri.parse(photo.uri)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.RELATIVE_PATH, CollectorPolicy.TARGET)
        if (targetName != photo.name) put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
    }
    return resolver.update(uri, values, null, null) == 1
}
```

Delete duplicates through `MediaStore.createDeleteRequest`; do not call raw delete before approval. Write access for move batch is obtained through `MediaStore.createWriteRequest`. Use chunks of at most 1000 URIs.

- [ ] **Step 4: Implement atomic progress state**

Persist compact JSON using `AtomicFile(File(context.filesDir, "collector1907-state.json"))` with phase/counters/currentName/timestamp. Failure to persist state must not delete media.

- [ ] **Step 5: Compile unit tests**

Run: `gradle :collector1907:testDebugUnitTest --stacktrace`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add safe MediaStore consolidation operations`

---

### Task 4: Tek ekran izin/onay state-machine ve ilerleme

**Files:**
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/MainActivity.kt`
- Create: `collector1907/src/main/kotlin/com/atmaca/toplayici1907/CollectorController.kt`
- Create: `collector1907/src/main/res/values/strings.xml`
- Create: `collector1907/src/main/res/values/themes.xml`
- Modify: `collector1907/src/main/AndroidManifest.xml`

**Interfaces:**
- `CollectorController.scanAndPlan(onProgress): ConsolidationPlan`
- `CollectorController.prepareTargetNames(plan): Map<Long,String>`
- Activity owns Activity Result launchers for media read permission, optional `Settings.ACTION_REQUEST_MANAGE_MEDIA`, delete request, and write request.

- [ ] **Step 1: Implement permission UI**

Manifest declares `READ_MEDIA_IMAGES`, legacy `READ_EXTERNAL_STORAGE maxSdkVersion=32`, `ACCESS_MEDIA_LOCATION`, `MANAGE_MEDIA`. On Android 31+, show `Medya yönetimi erişimini aç` only when `!MediaStore.canManageMedia(context)`; app still works with standard confirmation flow if user declines.

- [ ] **Step 2: Implement scan/plan state**

`Tara ve Toparla` starts `Dispatchers.IO` scan. UI states are exactly `Taranıyor`, `Kopyalar doğrulanıyor`, `Kopyalar temizleniyor`, `1907'ye taşınıyor`, `Tamamlandı`, `Hata`.

- [ ] **Step 3: Delete duplicates before moving**

After scan, build duplicate URI chunks. For each chunk launch `MediaStore.createDeleteRequest`; on `RESULT_OK`, rescan before moving. If user cancels, stop with `Kopya temizliği iptal edildi`; do not move remaining items.

- [ ] **Step 4: Obtain write access and move survivors**

Rescan after deletions, plan again, exclude target items, reserve names already in `Pictures/1907`, request write access in <=1000 URI chunks, then update `RELATIVE_PATH` one by one. Save progress after each successful move. On a failed move, increment `Hata` and continue without deleting the source.

- [ ] **Step 5: Final verification pass**

Rescan. UI reports total photos, remaining exact duplicate count, number outside `Pictures/1907`, moved count, error count. Success is only shown when duplicate count is 0 and outside-target count is 0; otherwise `Eksik kaldı` with counts.

- [ ] **Step 6: Commit**

Commit message: `feat: add 1907 collector workflow UI`

---

### Task 5: CI build, APK adı ve doğrulama

**Files:**
- Create: `.github/workflows/build-collector1907.yml`

**Interfaces:**
- Workflow produces artifact `ATMACA-1907-Fotograf-Toplayici-APK` containing `ATMACA-1907-Fotograf-Toplayici.apk`.

- [ ] **Step 1: Add feature-branch build workflow**

Workflow triggers on pushes to `atmaca-1907-toplayici` and `workflow_dispatch`, installs Java 17/API 37/Gradle 9.5, runs:

```bash
gradle :collector1907:testDebugUnitTest --stacktrace
gradle :collector1907:assembleDebug --stacktrace
cp collector1907/build/outputs/apk/debug/collector1907-debug.apk ATMACA-1907-Fotograf-Toplayici.apk
```

- [ ] **Step 2: Push CI commit and inspect run**

Expected: unit test job and APK build both success.

- [ ] **Step 3: Download artifact and verify archive/APK**

Verify artifact exists, ZIP opens cleanly, APK exists and has non-zero size.

- [ ] **Step 4: Final commit/checkpoint**

Commit message: `ci: build 1907 photo collector apk`

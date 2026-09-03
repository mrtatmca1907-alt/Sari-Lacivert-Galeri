from pathlib import Path

root=Path("coreapp/src/main/kotlin/com/atmaca/gallery")
app=(root/"GalleryApp.kt").read_text(encoding="utf-8")
vm=(root/"GalleryViewModel.kt").read_text(encoding="utf-8")
repo=(root/"MediaStoreRepository.kt").read_text(encoding="utf-8")
photo=(root/"StablePhotoPage.kt").read_text(encoding="utf-8")
settings=(root/"ModernSettingsDialog.kt").read_text(encoding="utf-8")
tools=(root/"CompleteSettingsExtras.kt").read_text(encoding="utf-8")
engine=(root/"CompleteToolEngine.kt").read_text(encoding="utf-8")
scanner=(root/"ToolFolderScanner.kt").read_text(encoding="utf-8")
loader=(root/"ImageLoader.kt").read_text(encoding="utf-8")
worker=(root/"VideoFrameWorker.kt").read_text(encoding="utf-8")
screenshot=(root/"ScreenshotStorage.kt").read_text(encoding="utf-8")
gradle=Path("coreapp/build.gradle.kts").read_text(encoding="utf-8")
manifest=Path("coreapp/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def require(text, markers, label):
    for marker in markers:
        if marker not in text: raise SystemExit(f"DIRECT SOURCE FAIL: {label} missing {marker!r}")

require(app,[
    "enum class HomeSection { MEDIA, ALBUMS, SETTINGS",'label = { Text("Medya") }','label = { Text("Albümler") }','label = { Text("Ayarlar") }',
    "ModernSettingsDialog(","detectDragGesturesAfterLongPress","slideshowPrefetchIndices(","prefetchViewerBitmap(",
    "pendingMutationIds","vm.removeItemsByIds(affectedIds)","var albums by remember { mutableStateOf<List<GalleryAlbum>>(emptyList()) }",
    "LaunchedEffect(albumsRefresh, section, pathAction)","shouldReloadPrimaryMediaAfterCamera(section, success = true)","vm.openAlbum(album)",
    'Text("Screenshot klasörü seç")',"ActivityResultContracts.OpenDocumentTree()",'prefs.edit().putString("screenshot_tree_uri"',"saveScreenshotToTree(context, shot, selectedTree)"
],"GalleryApp")
if "produceState<List<GalleryAlbum>>" in app: raise SystemExit("DIRECT SOURCE FAIL: albums still reset through produceState")
mutation=app.split("val mutationConsentLauncher",1)[1].split("val cameraLauncher",1)[0]
for bad in ["refreshToken++","albumsRefresh++","duplicatesRefresh++","vm.reload()"]:
    if bad in mutation: raise SystemExit(f"DIRECT SOURCE FAIL: mutation consent expensive refresh {bad}")
camera=app.split("val cameraLauncher",1)[1].split("fun runAfterWriteAccess",1)[0]
if "vm.reload()" in camera: raise SystemExit("DIRECT SOURCE FAIL: camera return full reload")

require(vm,["fun removeItemsByIds(ids: Set<Long>)","val albumBucketId: Long = 0L","val albumBucketName: String? = null","albumBucketId = album.bucketId","albumBucketName = album.bucketName ?: album.name","albumBucketName = snapshot.albumBucketName"],"GalleryViewModel")
require(repo,["val bucketId: Long = 0L","val bucketName: String? = null","albumIdentityKey(rawPath, item.bucketId, item.bucketName)","albumLocator(albumPath, albumBucketId, albumBucketName)","BUCKET_DISPLAY_NAME","albumBucketName: String? = null"],"MediaStoreRepository")
require(photo,["isViewerDoubleTap(","nextDoubleTapScale(","zoomOffsetAroundFocus(","loadHighResolutionBitmap("],"StablePhotoPage")
require(settings,["DialogProperties(usePlatformDefaultWidth = false)","LazyColumn(","SettingsChoiceRow("],"ModernSettingsDialog")
require(tools,["AtmacaToolPage.PERSON_CROP","AtmacaToolPage.PACKAGER","AtmacaToolPage.VIDEO_FRAMES","ActivityResultContracts.OpenDocumentTree()","collectToolUrisFromTree(","filterToolUris(context, uris, tool)","toolPickerMimeTypes(tool).toTypedArray()","enqueueVideoFrameWork(context, selectedUris, framesPerSecond)"],"tool UI")
require(scanner,["buildChildDocumentsUriUsingTree","buildDocumentUriUsingTree","toolAcceptsDocument(","filterToolUris(","COLUMN_DISPLAY_NAME","resolver.getType(documentUri)","coroutineContext.ensureActive()"],"folder scanner")
require(engine,["FaceDetection.getClient","PERFORMANCE_MODE_ACCURATE","personCropBounds(","moveSourceAfterSuccess: Boolean = false","moveVideoToOutputFolder(plan.uri, outputPath)","MediaStore.MediaColumns.RELATIVE_PATH"],"tool engine")
if "android.media.FaceDetector" in engine: raise SystemExit("DIRECT SOURCE FAIL: legacy FaceDetector returned")
require(worker,["class VideoFrameWorker","CoroutineWorker","OneTimeWorkRequestBuilder<VideoFrameWorker>()","moveSourceAfterSuccess = true","setProgressAsync(","setForeground(createForegroundInfo())","FOREGROUND_SERVICE_TYPE_DATA_SYNC"],"VideoFrameWorker")
require(screenshot,["DocumentsContract.createDocument","resolver.openOutputStream(created","image/jpeg"],"ScreenshotStorage")
require(loader,["ViewerBitmapCache","prefetchViewerBitmap("],"ImageLoader")
require(gradle,['implementation("com.google.mlkit:face-detection:16.1.7")','implementation("androidx.work:work-runtime:2.11.2")'],"build.gradle")
require(manifest,["android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_DATA_SYNC","SystemForegroundService",'android:foregroundServiceType="dataSync"'],"AndroidManifest")
for legacy in ['label = { Text("Foto") }','label = { Text("Video") }','label = { Text("Kopya") }','label = { Text("Çöp") }']:
    if legacy in app: raise SystemExit(f"DIRECT SOURCE FAIL: legacy bottom nav {legacy}")
print("Direct source architecture OK")

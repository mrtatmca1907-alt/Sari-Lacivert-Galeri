package com.atmaca.gallery

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class MediaMeta(val id: Long,val relativePath:String,val size:Long,val mimeType:String?)
data class AlbumSummary(val relativePath:String,val count:Int)
data class NormalizedCropRect(val left:Float,val top:Float,val right:Float,val bottom:Float){val width:Float get()=right-left; val height:Float get()=bottom-top}
data class IntCropRect(val left:Int,val top:Int,val right:Int,val bottom:Int){val width:Int get()=right-left; val height:Int get()=bottom-top}
data class ViewerPanBounds(val maxX:Float,val maxY:Float)
enum class CropRatio(val ratio:Float){FREE(0f),SQUARE(1f),FOUR_THREE(4f/3f),SIXTEEN_NINE(16f/9f)}
enum class MediaFilter { ALL, PHOTOS, VIDEOS, GIF, RAW, SVG }
enum class MediaSort { NAME, PATH, SIZE, MODIFIED, TAKEN, RANDOM }
enum class SortDirection { ASCENDING, DESCENDING }

fun homeSections():List<HomeSection> = listOf(HomeSection.MEDIA, HomeSection.ALBUMS, HomeSection.SETTINGS)
fun mediaNameOverlay(name:String):String = name.trim()
fun mediaFilterAccepts(isVideo:Boolean,filter:MediaFilter):Boolean = mediaFilterAccepts(isVideo,null,filter)
fun mediaFilterAccepts(isVideo:Boolean,mimeType:String?,filter:MediaFilter):Boolean {
    val mime = mimeType?.trim()?.lowercase().orEmpty()
    return when(filter){
        MediaFilter.ALL -> true
        MediaFilter.PHOTOS -> !isVideo
        MediaFilter.VIDEOS -> isVideo
        MediaFilter.GIF -> !isVideo && mime == "image/gif"
        MediaFilter.SVG -> !isVideo && mime == "image/svg+xml"
        MediaFilter.RAW -> !isVideo && mime in RAW_MIME_TYPES
    }
}
private val RAW_MIME_TYPES = setOf(
    "image/dng", "image/x-adobe-dng", "image/x-canon-cr2", "image/x-canon-cr3",
    "image/x-nikon-nef", "image/x-sony-arw", "image/x-fuji-raf", "image/x-panasonic-rw2",
    "image/x-olympus-orf", "image/x-pentax-pef", "image/x-samsung-srw"
)
fun mediaFilterLabels():List<String> = listOf("Tümü","Fotoğraflar","Videolar","GIF'ler","RAW resimler","SVG'ler")
fun mediaSortLabels():List<String> = listOf("Ad","Yol","Boyut","Son değiştirilme","Alınan tarih","Rastgele")
fun sortDirectionLabels():List<String> = listOf("Artan","Azalan")
fun <T> applySortDirection(items:List<T>,direction:SortDirection):List<T> = if(direction==SortDirection.ASCENDING) items else items.asReversed()
fun completeSettingsEntries():List<String> = listOf("Geri Dönüşüm Kutusu","Slayt gösterisi","Akıllı Kişi Kırpma","Görsel Paketleyici","Video Kareleri")
fun clampSlideshowSeconds(seconds:Int):Int = seconds.coerceIn(1,30)
fun packageBatchPath(batch:Int):String = "Pictures/ATMACA Paketler/Paket_${batch.coerceAtLeast(1).toString().padStart(4,'0')}/"
fun frameIntervalMs(framesPerSecond:Int):Long = 1000L / framesPerSecond.coerceIn(1,30)

fun personCropBounds(sourceWidth:Int,sourceHeight:Int,faceLeft:Int,faceTop:Int,faceRight:Int,faceBottom:Int):IntCropRect {
    if(sourceWidth<=0 || sourceHeight<=0) return IntCropRect(0,0,0,0)
    val l0=minOf(faceLeft,faceRight).coerceIn(0,sourceWidth-1)
    val r0=maxOf(faceLeft,faceRight).coerceIn(l0+1,sourceWidth)
    val t0=minOf(faceTop,faceBottom).coerceIn(0,sourceHeight-1)
    val b0=maxOf(faceTop,faceBottom).coerceIn(t0+1,sourceHeight)
    val w=(r0-l0).coerceAtLeast(1)
    val h=(b0-t0).coerceAtLeast(1)
    val left=(l0-w*0.7f).roundToInt().coerceAtLeast(0)
    val right=(r0+w*0.7f).roundToInt().coerceAtMost(sourceWidth)
    val top=(t0-h*0.6f).roundToInt().coerceAtLeast(0)
    val bottom=(b0+h*2.2f).roundToInt().coerceAtMost(sourceHeight)
    return IntCropRect(left,top,right,bottom)
}

fun isViewerDoubleTap(previousUpMs:Long,currentUpMs:Long,distancePx:Float,maxDelayMs:Long=300L,maxDistancePx:Float=80f):Boolean = previousUpMs>0L && currentUpMs>previousUpMs && currentUpMs-previousUpMs<=maxDelayMs && distancePx<=maxDistancePx
fun calculateViewerDecodeSample(sourceWidth:Int,sourceHeight:Int,viewportWidth:Int,viewportHeight:Int):Int {
    if(sourceWidth<=0||sourceHeight<=0||viewportWidth<=0||viewportHeight<=0)return 1
    val sourceEdge=maxOf(sourceWidth,sourceHeight).toLong(); val targetEdge=maxOf(viewportWidth,viewportHeight).toLong()*2L
    if(targetEdge<=0L)return 1
    return (sourceEdge/targetEdge).toInt().coerceAtLeast(1)
}
fun clampViewerScale(scale:Float)=scale.coerceIn(1f,4f)
fun galleryZoomFactor(rawFactor:Float)=rawFactor.coerceIn(0.72f,1.35f)
fun dampedZoomFactor(rawFactor:Float)=galleryZoomFactor(rawFactor)
fun nextDoubleTapScale(scale:Float)=if(scale>1.1f)1f else 2.25f
fun zoomOffsetAroundFocus(oldOffset:Float,focusFromCenter:Float,oldScale:Float,newScale:Float):Float { if(oldScale<=0f)return oldOffset; val ratio=newScale/oldScale; return oldOffset+focusFromCenter*(1f-ratio) }
fun normalizeViewerRotation(rotation:Float)=((rotation%360f)+360f)%360f
fun applyViewerRotationDelta(current:Float,delta:Float)=normalizeViewerRotation(current+delta)
fun shouldPhotoConsumeGesture(pointerCount:Int,scale:Float,rotation:Float):Boolean = pointerCount>=2 || scale>1.001f || (normalizeViewerRotation(rotation)>0.5f && normalizeViewerRotation(rotation)<359.5f)
fun shouldCommitViewerTransform(gestureEnded:Boolean):Boolean=gestureEnded
fun viewerMenuEntries(isVideo:Boolean,screenshotMode:Boolean):List<String> = if(isVideo) listOf("Ad değiştir","Çöpe taşı / sil") else listOf("Kırp",if(screenshotMode)"Screenshot modunu kapat" else "Screenshot modu","Ad değiştir","Çöpe taşı / sil")
fun viewerBottomActions(isVideo:Boolean):List<String> = listOf("Paylaş","Geri")
fun shouldEnablePager(scale:Float)=scale<=1.001f
fun shouldEnablePager(scale:Float,rotation:Float)=scale<=1.001f&&(normalizeViewerRotation(rotation)<0.5f||normalizeViewerRotation(rotation)>359.5f)
fun shouldShowViewerControls(scale:Float,gestureActive:Boolean)=!gestureActive&&scale<=1.001f
fun shouldRenderViewerChrome(captureInProgress:Boolean,controlsVisible:Boolean,scale:Float,gestureActive:Boolean)=!captureInProgress&&controlsVisible&&shouldShowViewerControls(scale,gestureActive)
fun viewerPanBounds(viewportWidth:Float,viewportHeight:Float,imageWidth:Float,imageHeight:Float,scale:Float,rotation:Float):ViewerPanBounds{
 if(viewportWidth<=0f||viewportHeight<=0f||imageWidth<=0f||imageHeight<=0f)return ViewerPanBounds(0f,0f)
 val fit=minOf(viewportWidth/imageWidth,viewportHeight/imageHeight); val fw=imageWidth*fit; val fh=imageHeight*fit; val ss=clampViewerScale(scale); val a=normalizeViewerRotation(rotation)*PI.toFloat()/180f; val c=abs(cos(a)); val s=abs(sin(a)); val rw=(fw*c+fh*s)*ss; val rh=(fw*s+fh*c)*ss
 return ViewerPanBounds(((rw-viewportWidth)/2f).coerceAtLeast(0f),((rh-viewportHeight)/2f).coerceAtLeast(0f))
}
fun clampViewerOffset(offset:Float,maxOffset:Float)=offset.coerceIn(-maxOffset.coerceAtLeast(0f),maxOffset.coerceAtLeast(0f))
fun nextQuarterRotation(rotation:Float)=normalizeViewerRotation(rotation+90f)
fun normalizedCropRect(left:Float,top:Float,right:Float,bottom:Float):NormalizedCropRect{val l=minOf(left,right).coerceIn(0f,1f);val r=maxOf(left,right).coerceIn(0f,1f);val t=minOf(top,bottom).coerceIn(0f,1f);val b=maxOf(top,bottom).coerceIn(0f,1f);return NormalizedCropRect(l,t,r,b)}
fun groupAlbums(items:List<MediaMeta>)=items.groupBy{normalizeRelativePath(it.relativePath)}.map{(p,m)->AlbumSummary(p,m.size)}.sortedBy{it.relativePath.lowercase()}
fun duplicateCandidateGroups(items:List<MediaMeta>)=items.asSequence().filter{it.size>0L}.groupBy{it.size}.values.asSequence().filter{it.size>1}.map{it.sortedBy(MediaMeta::id)}.sortedBy{it.first().size}.toList()
fun normalizeRelativePath(raw:String):String{val parts=raw.trim().replace('\\','/').split('/').map(String::trim).filter(String::isNotEmpty);val n=if(parts.isEmpty())"Pictures/ATMACA" else parts.joinToString("/");return "$n/"}
fun albumDisplayName(relativePath:String)=normalizeRelativePath(relativePath).trimEnd('/').substringAfterLast('/').ifBlank{"Depolama"}

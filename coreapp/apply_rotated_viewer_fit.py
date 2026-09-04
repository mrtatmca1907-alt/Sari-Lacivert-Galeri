from pathlib import Path

rules_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt")
rules = rules_path.read_text(encoding="utf-8")
old_rules = '''fun viewerImageRenderSize(viewportWidth:Float,viewportHeight:Float,imageWidth:Float,imageHeight:Float):ViewerImageRenderSize {
    if(viewportWidth<=0f||viewportHeight<=0f||imageWidth<=0f||imageHeight<=0f) return ViewerImageRenderSize(0f,0f)
    val fit=minOf(viewportWidth/imageWidth,viewportHeight/imageHeight)
    return ViewerImageRenderSize(imageWidth*fit,imageHeight*fit)
}
'''
new_rules = '''fun viewerImageRenderSize(viewportWidth:Float,viewportHeight:Float,imageWidth:Float,imageHeight:Float):ViewerImageRenderSize =
    viewerImageRenderSize(viewportWidth, viewportHeight, imageWidth, imageHeight, 0f)
fun viewerImageRenderSize(viewportWidth:Float,viewportHeight:Float,imageWidth:Float,imageHeight:Float,rotation:Float):ViewerImageRenderSize {
    if(viewportWidth<=0f||viewportHeight<=0f||imageWidth<=0f||imageHeight<=0f) return ViewerImageRenderSize(0f,0f)
    val quarterTurn = ((normalizeViewerRotation(rotation) / 90f).roundToInt() % 2) != 0
    val rotatedWidth = if(quarterTurn) imageHeight else imageWidth
    val rotatedHeight = if(quarterTurn) imageWidth else imageHeight
    val fit=minOf(viewportWidth/rotatedWidth,viewportHeight/rotatedHeight)
    return ViewerImageRenderSize(imageWidth*fit,imageHeight*fit)
}
'''
if new_rules not in rules:
    count = rules.count(old_rules)
    if count != 1:
        raise SystemExit(f"viewer image size: beklenen 1 eslesme, bulunan {count}")
    rules = rules.replace(old_rules, new_rules, 1)
    rules_path.write_text(rules, encoding="utf-8")

page_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt")
page = page_path.read_text(encoding="utf-8")
old_call = '''            val fitted = viewerImageRenderSize(
                viewportWidth.toFloat(),
                viewportHeight.toFloat(),
                source.width.toFloat(),
                source.height.toFloat()
            )
'''
new_call = '''            val fitted = viewerImageRenderSize(
                viewportWidth.toFloat(),
                viewportHeight.toFloat(),
                source.width.toFloat(),
                source.height.toFloat(),
                localRotation
            )
'''
if new_call not in page:
    count = page.count(old_call)
    if count != 1:
        raise SystemExit(f"StablePhotoPage fit call: beklenen 1 eslesme, bulunan {count}")
    page = page.replace(old_call, new_call, 1)
    page_path.write_text(page, encoding="utf-8")

print("Rotated viewer fit applied")

package com.local.edgebar.data

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

data class LaunchableApp(val packageName: String, val label: String)

/** Rendered icon plus whether it came from the app's themed (monochrome) layer. */
data class AppIcon(val bitmap: Bitmap, val fromMonochromeLayer: Boolean)

object AppCatalog {
    private val launcherIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** All apps with a launcher activity (visible thanks to the <queries> manifest entry). */
    fun queryLaunchable(pm: PackageManager): List<LaunchableApp> =
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            .asSequence()
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()

    fun isLaunchable(pm: PackageManager, pkg: String): Boolean = pm.getLaunchIntentForPackage(pkg) != null

    fun label(pm: PackageManager, pkg: String): String? = try {
        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** The launcher activity's icon, falling back to the application icon. */
    private fun rawIcon(pm: PackageManager, pkg: String): Drawable? = try {
        pm.getLaunchIntentForPackage(pkg)?.component?.let { pm.getActivityIcon(it) }
            ?: pm.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Monochrome rendering: the adaptive icon's themed layer (API 33
     * AdaptiveIconDrawable.getMonochrome) tinted white when present, otherwise the normal
     * icon converted to grayscale.
     */
    fun loadIcon(pm: PackageManager, pkg: String, sizePx: Int): AppIcon? {
        val drawable = rawIcon(pm, pkg) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome?.mutate()
        if (mono != null) {
            // Adaptive layers are 108dp with a 72dp visible area: extend bounds by 1/4 per side.
            val inset = sizePx / 4
            mono.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            mono.setTint(android.graphics.Color.WHITE)
            mono.draw(canvas)
            return AppIcon(bitmap, fromMonochromeLayer = true)
        }
        val d = drawable.mutate()
        d.setBounds(0, 0, sizePx, sizePx)
        d.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        d.draw(canvas)
        return AppIcon(bitmap, fromMonochromeLayer = false)
    }
}

/** Small icon/label cache so repeated panel openings are instant. Cleared on memory pressure. */
class AppInfoCache(maxIcons: Int = 32) {
    private val icons = LruCache<String, AppIcon>(maxIcons)
    private val labels = LruCache<String, String>(256)

    fun icon(pkg: String): AppIcon? = icons.get(pkg)
    fun putIcon(pkg: String, icon: AppIcon) { icons.put(pkg, icon) }
    fun label(pkg: String): String? = labels.get(pkg)
    fun putLabel(pkg: String, label: String) { labels.put(pkg, label) }
    fun invalidate(pkg: String) { icons.remove(pkg); labels.remove(pkg) }
    fun clear() { icons.evictAll(); labels.evictAll() }
}

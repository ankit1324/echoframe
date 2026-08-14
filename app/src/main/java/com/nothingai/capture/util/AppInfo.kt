package com.nothingai.capture.util

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * Resolves a package name to its user-facing app label and launcher icon.
 *
 * The app intentionally does NOT hold QUERY_ALL_PACKAGES (Play restricts it to
 * device-search / antivirus / browser / file-manager apps), so under Android 11+
 * package-visibility rules most captured source packages are simply invisible to us.
 * Every lookup therefore degrades gracefully: the raw package name stands in for the
 * label, and the icon is absent. Callers must handle a null icon.
 */
object AppInfo {
    fun label(context: Context, pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Unknown app"
        // Catches NameNotFoundException (invisible or uninstalled package) and any other
        // PackageManager failure — an unresolvable label must never break the UI.
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                .trim()
                .ifEmpty { pkg }
        } catch (e: Exception) {
            pkg
        }
    }

    fun icon(context: Context, pkg: String?): Drawable? {
        if (pkg.isNullOrBlank()) return null
        return try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
    }
}

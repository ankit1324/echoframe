package com.nothingai.capture.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Resolves a package name to its user-facing app label and launcher icon. */
object AppInfo {
    fun label(context: Context, pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Unknown app"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun icon(context: Context, pkg: String?): Drawable? {
        if (pkg.isNullOrBlank()) return null
        return try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

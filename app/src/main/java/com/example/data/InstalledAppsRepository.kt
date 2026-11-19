package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.domain.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamically discovers installed launchable applications without hardcoding package names.
 * Highlights recognized payment brands based on the app's user-facing label when present.
 */
class InstalledAppsRepository(private val context: Context) {

  private val supportedPaymentKeywords =
    listOf(
      "jazzcash business" to "JazzCash Business",
      "jazzcash" to "JazzCash",
      "easypaisa" to "Easypaisa",
      "mashreq" to "Mashreq Bank",
      "raqami" to "Raqami Bank",
    )

  suspend fun getInstalledLaunchableApps(): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val mainIntent =
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

      val resolved =
        pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

      val selfPackage = context.packageName

      resolved
        .mapNotNull { info ->
          val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
          if (pkg == selfPackage) return@mapNotNull null
          val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
          if (label.isEmpty()) return@mapNotNull null

          val lowerLabel = label.lowercase()
          val matchedBrand =
            supportedPaymentKeywords.firstOrNull { (keyword, _) ->
              lowerLabel.contains(keyword)
            }?.second

          InstalledAppInfo(
            packageName = pkg,
            appName = label,
            isKnownPaymentApp = matchedBrand != null,
            suggestedPaymentBrand = matchedBrand,
          )
        }
        .distinctBy { it.packageName }
        .sortedWith(
          compareByDescending<InstalledAppInfo> { it.isKnownPaymentApp }
            .thenBy { it.appName.lowercase() }
        )
    }

  fun loadAppIcon(packageName: String): Drawable? {
    return try {
      context.packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
      null
    }
  }

  fun resolveAppName(packageName: String): String {
    return try {
      val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
      context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: PackageManager.NameNotFoundException) {
      packageName
    }
  }
}

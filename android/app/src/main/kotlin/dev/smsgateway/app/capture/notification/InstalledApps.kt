package dev.smsgateway.app.capture.notification

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

/**
 * Launchable apps the operator can choose to monitor. Uses a `<queries>`
 * launcher intent in the manifest instead of QUERY_ALL_PACKAGES.
 */
object InstalledApps {
    private const val ICON_PX = 96

    fun list(context: Context): List<Map<String, Any?>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                mapOf(
                    "packageName" to info.packageName,
                    "label" to pm.getApplicationLabel(info).toString(),
                    "icon" to runCatching { pm.getApplicationIcon(info).toPng() }.getOrNull(),
                    "installed" to true,
                )
            }
            .sortedBy { (it["label"] as String).lowercase() }
    }

    private fun Drawable.toPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, ICON_PX, ICON_PX)
        draw(Canvas(bitmap))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}

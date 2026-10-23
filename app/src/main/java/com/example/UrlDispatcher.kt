package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Handles URL parsing, routing, and intent dispatching for the WebView app.
 * Ensures security by enforcing strict scheme validation and sandboxed external app launches.
 */
object UrlDispatcher {

    const val BASE_URL = "https://matkabooking.online/"
    const val PRIMARY_HOST = "matkabooking.online"

    sealed class UrlDestination {
        data class Internal(val url: String) : UrlDestination()
        data class ExternalBrowser(val uri: Uri) : UrlDestination()
        data class Upi(val uri: Uri) : UrlDestination()
        data class WhatsApp(val uri: Uri) : UrlDestination()
        data class Telegram(val uri: Uri) : UrlDestination()
        data class Dial(val uri: Uri) : UrlDestination()
        data class Email(val uri: Uri) : UrlDestination()
        data class Sms(val uri: Uri) : UrlDestination()
        data class CustomIntent(val intent: Intent, val fallbackUrl: String?) : UrlDestination()
        data class ExternalApp(val uri: Uri) : UrlDestination()
        data class Unsupported(val reason: String) : UrlDestination()
    }

    /**
     * Checks if a given URL belongs to the internal matkabooking.online domain
     * or is an in-page script/anchor that must remain inside WebView.
     */
    fun isInternalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed.startsWith("javascript:", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true)) {
            return true
        }
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: return false
            host == PRIMARY_HOST || host.endsWith(".$PRIMARY_HOST")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Categorizes a URL into its intended destination.
     */
    fun classifyUrl(url: String?): UrlDestination {
        if (url.isNullOrBlank()) {
            return UrlDestination.Unsupported("Empty URL")
        }

        val trimmed = url.trim()

        // Handle in-page scripts and internal targets
        if (trimmed.startsWith("javascript:", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true)) {
            return UrlDestination.Internal(trimmed)
        }

        // Handle intent:// schemes securely
        if (trimmed.startsWith("intent:", ignoreCase = true)) {
            return try {
                val intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null

                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                UrlDestination.CustomIntent(intent, fallbackUrl)
            } catch (e: Exception) {
                UrlDestination.Unsupported("Invalid intent URI")
            }
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (e: Exception) {
            return UrlDestination.Unsupported("Malformed URL")
        }

        val scheme = uri.scheme?.lowercase() ?: return UrlDestination.Unsupported("Missing scheme")

        return when (scheme) {
            "tel" -> UrlDestination.Dial(uri)
            "mailto" -> UrlDestination.Email(uri)
            "sms", "smsto" -> UrlDestination.Sms(uri)
            "upi", "phonepe", "paytmmp", "tez", "gpay", "credpay", "mobikwik" -> UrlDestination.Upi(uri)
            "whatsapp" -> UrlDestination.WhatsApp(uri)
            "tg", "telegram" -> UrlDestination.Telegram(uri)
            "https", "http" -> {
                val host = uri.host?.lowercase() ?: ""
                if (host == PRIMARY_HOST || host.endsWith(".$PRIMARY_HOST")) {
                    UrlDestination.Internal(trimmed)
                } else if (isWhatsAppWebLink(uri)) {
                    UrlDestination.WhatsApp(uri)
                } else if (isTelegramWebLink(uri)) {
                    UrlDestination.Telegram(uri)
                } else {
                    UrlDestination.ExternalBrowser(uri)
                }
            }
            else -> UrlDestination.ExternalApp(uri)
        }
    }

    private fun isWhatsAppWebLink(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return (host == "wa.me" || host == "api.whatsapp.com" || host.endsWith(".whatsapp.com"))
    }

    private fun isTelegramWebLink(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return (host == "t.me" || host == "telegram.me" || host.endsWith(".telegram.org"))
    }

    /**
     * Dispatches the URL to the appropriate native Android intent or action.
     * Returns true if handled externally, false if it should be loaded inside WebView.
     */
    fun dispatch(context: Context, url: String?): Boolean {
        return when (val dest = classifyUrl(url)) {
            is UrlDestination.Internal -> {
                // Internal route or script: Let WebView load it
                false
            }
            is UrlDestination.ExternalBrowser -> {
                launchExternalBrowser(context, dest.uri)
                true
            }
            is UrlDestination.Upi -> {
                launchUpiIntent(context, dest.uri)
                true
            }
            is UrlDestination.WhatsApp -> {
                launchWhatsApp(context, dest.uri)
                true
            }
            is UrlDestination.Telegram -> {
                launchTelegram(context, dest.uri)
                true
            }
            is UrlDestination.Dial -> {
                launchDialer(context, dest.uri)
                true
            }
            is UrlDestination.Email -> {
                launchEmail(context, dest.uri)
                true
            }
            is UrlDestination.Sms -> {
                launchSms(context, dest.uri)
                true
            }
            is UrlDestination.CustomIntent -> {
                launchCustomIntent(context, dest.intent, dest.fallbackUrl)
                true
            }
            is UrlDestination.ExternalApp -> {
                launchExternalApp(context, dest.uri)
                true
            }
            is UrlDestination.Unsupported -> {
                Toast.makeText(context, "Cannot open link: ${dest.reason}", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun launchExternalBrowser(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchUpiIntent(context: Context, uri: Uri) {
        try {
            val upiIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(upiIntent, "Pay via UPI").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI payment app installed", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchWhatsApp(context: Context, uri: Uri) {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled("com.whatsapp", pm) || isPackageInstalled("com.whatsapp.w4b", pm)

        if (isInstalled) {
            try {
                val directUri = if (uri.scheme?.lowercase() == "whatsapp") {
                    uri
                } else {
                    uri
                }
                val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback to browser
            }
        }

        // WhatsApp not installed: if it was an https link (e.g. wa.me), open in browser
        if (uri.scheme?.lowercase() in listOf("https", "http")) {
            launchExternalBrowser(context, uri)
        } else {
            Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchTelegram(context: Context, uri: Uri) {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled("org.telegram.messenger", pm) || isPackageInstalled("org.thunderdog.challegram", pm)

        if (isInstalled) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback to browser
            }
        }

        if (uri.scheme?.lowercase() in listOf("https", "http")) {
            launchExternalBrowser(context, uri)
        } else {
            // Convert tg:// to t.me web link if possible
            val webUrl = uri.toString().replace("tg://resolve?domain=", "https://t.me/")
            launchExternalBrowser(context, Uri.parse(webUrl))
        }
    }

    private fun launchExternalApp(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to handle this request", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String, packageManager: android.content.pm.PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchDialer(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No dialer application found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchEmail(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSms(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No messaging app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCustomIntent(context: Context, intent: Intent, fallbackUrl: String?) {
        try {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (!fallbackUrl.isNullOrBlank()) {
                launchExternalBrowser(context, Uri.parse(fallbackUrl))
            } else {
                Toast.makeText(context, "No application available to handle this request", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

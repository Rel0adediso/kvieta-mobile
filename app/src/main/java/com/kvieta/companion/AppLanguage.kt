package com.kvieta.companion

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Turkish is the product default for this preview, independently of the phone's language. */
internal fun turkishContext(base: Context): Context {
    val locale = Locale.forLanguageTag("tr")
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocales(LocaleList(locale))
    configuration.setLayoutDirection(locale)
    return base.createConfigurationContext(configuration)
}

class KvietaApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(turkishContext(base))
    }
    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(android.app.LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != "tr") {
                manager.applicationLocales = LocaleList.forLanguageTags("tr")
            }
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("tr"))
        }
    }
}

abstract class KvietaActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT < 33 && AppCompatDelegate.getApplicationLocales().toLanguageTags() != "tr") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("tr"))
        }
        super.attachBaseContext(newBase)
    }
}

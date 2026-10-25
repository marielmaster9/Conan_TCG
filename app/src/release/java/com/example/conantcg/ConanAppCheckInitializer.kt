package com.example.conantcg

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release-build only. Register your app's Play Integrity settings in Firebase. */
object ConanAppCheckInitializer {
    fun initialize(context: Context) {
        FirebaseApp.initializeApp(context)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}

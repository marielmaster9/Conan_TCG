package com.example.conantcg

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug-build only. Register the token printed in Logcat with Firebase App Check. */
object ConanAppCheckInitializer {
    fun initialize(context: Context) {
        FirebaseApp.initializeApp(context)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}

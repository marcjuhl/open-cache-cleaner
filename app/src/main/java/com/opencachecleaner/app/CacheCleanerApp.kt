package com.opencachecleaner.app

import android.app.Application
import com.topjohnwu.superuser.Shell

class CacheCleanerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure LibSU Root Shell defaults
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}

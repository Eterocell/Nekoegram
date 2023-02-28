package com.eterocell.nekoegram

import org.telegram.messenger.ApplicationLoader

internal val nekoegramVersion: String
    get() = ApplicationLoader.applicationContext.packageManager.getPackageInfo(
        ApplicationLoader.applicationContext.packageName, 0
    ).versionName
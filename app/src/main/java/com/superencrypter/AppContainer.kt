package com.superencrypter

import android.content.Context
import com.superencrypter.data.crypto.CryptoManager
import com.superencrypter.data.crypto.SessionVaultManager
import com.superencrypter.data.file.FileStorage
import com.superencrypter.data.local.database.SuperEncrypterDatabase
import com.superencrypter.data.location.LocationService
import com.superencrypter.data.notification.NotificationHelper
import com.superencrypter.data.remote.VirusTotalClient
import com.superencrypter.data.repository.SuperEncrypterRepository
import com.superencrypter.util.AppConstants

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = SuperEncrypterDatabase.create(appContext)
    private val notificationHelper = NotificationHelper(appContext)

    val repository = SuperEncrypterRepository(
        vaultDao = database.vaultDao(),
        fileDao = database.vaultFileDao(),
        historyDao = database.historyDao(),
        crypto = CryptoManager(),
        fileStorage = FileStorage(appContext),
        locationService = LocationService(appContext),
        notificationHelper = notificationHelper,
        virusTotalClient = VirusTotalClient(AppConstants.DEFAULT_BACKEND_URL),
        sessionVaultManager = SessionVaultManager()
    )

    fun initialize() {
        notificationHelper.createChannel()
    }
}

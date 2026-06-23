package com.superencrypter.ui.navigation

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val CREATE = "create"
    const val DETAILS = "details/{vaultId}"
    const val UNLOCK = "unlock/{vaultId}"
    const val VIEWER = "viewer/{vaultId}/{fileId}"

    fun details(vaultId: Long) = "details/$vaultId"
    fun unlock(vaultId: Long) = "unlock/$vaultId"
    fun viewer(vaultId: Long, fileId: Long) = "viewer/$vaultId/$fileId"
}

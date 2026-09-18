package com.unsame.microband.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class BluetoothPermissionState(
    val canConnect: Boolean,
) {
    val allGranted: Boolean get() = canConnect
}

object BluetoothPermissionManager {
    fun state(context: Context) = BluetoothPermissionState(
        canConnect = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
    )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

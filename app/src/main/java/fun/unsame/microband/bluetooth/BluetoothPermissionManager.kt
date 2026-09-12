package com.unsame.microband.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class BluetoothPermissionState(
    val canScan: Boolean,
    val canConnect: Boolean,
) {
    val allGranted: Boolean get() = canScan && canConnect
}

object BluetoothPermissionManager {
    fun state(context: Context) = BluetoothPermissionState(
        canScan = granted(context, Manifest.permission.BLUETOOTH_SCAN),
        canConnect = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
    )

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

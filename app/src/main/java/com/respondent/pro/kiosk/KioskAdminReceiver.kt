package com.respondent.pro.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Цель выдачи Device Owner. Логики не содержит — объявлен в манифесте
 * с device_admin.xml, на этот компонент ссылается dpm set-device-owner.
 */
class KioskAdminReceiver : DeviceAdminReceiver()

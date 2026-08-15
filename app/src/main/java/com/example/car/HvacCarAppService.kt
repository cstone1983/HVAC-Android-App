package com.example.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Main CarAppService entry point for Android Auto integration.
 */
class HvacCarAppService : CarAppService() {
    override fun onCreateSession(): Session {
        return HvacCarSession()
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}

package com.lifelink119.app.core

/** In-memory consent for fixed example data. No account, credentials or network. */
class HealthLinkExample {
    var consented: Boolean = false
        private set

    fun agree(checked: Boolean) {
        require(checked)
        consented = true
    }
    fun revoke() { consented = false }
    fun canShowExample() = consented
}

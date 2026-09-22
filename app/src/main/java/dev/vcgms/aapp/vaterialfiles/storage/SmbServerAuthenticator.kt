package dev.vcgms.aapp.vaterialfiles.storage

import dev.vcgms.aapp.vaterialfiles.provider.smb.client.Authenticator
import dev.vcgms.aapp.vaterialfiles.provider.smb.client.Authority
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

object SmbServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<SmbServer>()

    override fun getPassword(authority: Authority): String? {
        val server = synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is SmbServer && it.authority == authority
        } as SmbServer?
        return server?.password
    }

    fun addTransientServer(server: SmbServer) {
        synchronized(transientServers) { transientServers += server }
    }

    fun removeTransientServer(server: SmbServer) {
        synchronized(transientServers) { transientServers -= server }
    }
}

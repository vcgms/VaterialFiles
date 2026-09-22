package dev.vcgms.aapp.vaterialfiles.storage

import dev.vcgms.aapp.vaterialfiles.provider.sftp.client.Authentication
import dev.vcgms.aapp.vaterialfiles.provider.sftp.client.Authenticator
import dev.vcgms.aapp.vaterialfiles.provider.sftp.client.Authority
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

object SftpServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<SftpServer>()

    override fun getAuthentication(authority: Authority): Authentication? {
        val server = synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is SftpServer && it.authority == authority
        } as SftpServer?
        return server?.authentication
    }

    fun addTransientServer(server: SftpServer) {
        synchronized(transientServers) { transientServers += server }
    }

    fun removeTransientServer(server: SftpServer) {
        synchronized(transientServers) { transientServers -= server }
    }
}

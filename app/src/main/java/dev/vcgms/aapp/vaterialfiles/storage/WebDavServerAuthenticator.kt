package dev.vcgms.aapp.vaterialfiles.storage

import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authentication
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authenticator
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authority
import dev.vcgms.aapp.vaterialfiles.settings.Settings
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

object WebDavServerAuthenticator : Authenticator {
    private val transientServers = mutableSetOf<WebDavServer>()

    override fun getAuthentication(authority: Authority): Authentication? {
        val server = synchronized(transientServers) {
            transientServers.find { it.authority == authority }
        } ?: Settings.STORAGES.valueCompat.find {
            it is WebDavServer && it.authority == authority
        } as WebDavServer?
        return server?.authentication
    }

    fun addTransientServer(server: WebDavServer) {
        synchronized(transientServers) { transientServers += server }
    }

    fun removeTransientServer(server: WebDavServer) {
        synchronized(transientServers) { transientServers -= server }
    }
}

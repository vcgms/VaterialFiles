package dev.vcgms.aapp.vaterialfiles.provider.webdav.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}

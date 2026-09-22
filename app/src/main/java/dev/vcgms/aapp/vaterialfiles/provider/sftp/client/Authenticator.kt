package dev.vcgms.aapp.vaterialfiles.provider.sftp.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}

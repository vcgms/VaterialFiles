package dev.vcgms.aapp.vaterialfiles.provider.smb.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}

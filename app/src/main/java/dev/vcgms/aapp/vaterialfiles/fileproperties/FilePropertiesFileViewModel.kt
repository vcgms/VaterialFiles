package dev.vcgms.aapp.vaterialfiles.fileproperties

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.util.Stateful

class FilePropertiesFileViewModel(file: FileItem) : ViewModel() {
    private val _fileLiveData = FileLiveData(file)
    val fileLiveData: LiveData<Stateful<FileItem>>
        get() = _fileLiveData

    fun reload() {
        _fileLiveData.loadValue()
    }

    override fun onCleared() {
        _fileLiveData.close()
    }
}

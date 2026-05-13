package com.bioasistencia.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bioasistencia.data.api.RetrofitClient
import com.bioasistencia.data.repository.AppRepository
import com.bioasistencia.utils.SessionManager

// Factory reutilizable para todos los ViewModels que reciban AppRepository
class RepoViewModelFactory(
    private val repository: AppRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        try {
            @Suppress("UNCHECKED_CAST")
            return modelClass.getDeclaredConstructor(AppRepository::class.java)
                .newInstance(repository) as T
        } catch (e: Exception) {
            throw IllegalArgumentException("ViewModel ${modelClass.name} no tiene constructor(AppRepository)", e)
        }
    }

    companion object {
        fun build(context: Context): RepoViewModelFactory {
            val session = SessionManager(context)
            val api     = RetrofitClient.create(session)
            return RepoViewModelFactory(AppRepository(api))
        }
    }
}

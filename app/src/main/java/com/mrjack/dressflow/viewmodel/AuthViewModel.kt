package com.mrjack.dressflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrjack.dressflow.data.api.TokenExpiredNotifier
import com.mrjack.dressflow.data.model.UsuarioLogado
import com.mrjack.dressflow.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Loading  : AuthState()
    object LoggedOut: AuthState()
    data class LoggedIn(val user: UsuarioLogado) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(app)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    init {
        viewModelScope.launch {
            if (repo.isLoggedIn()) {
                val user = repo.getUsuarioLogado()
                _state.value = if (user != null) AuthState.LoggedIn(user) else AuthState.LoggedOut
            } else {
                _state.value = AuthState.LoggedOut
            }
        }
        TokenExpiredNotifier.addListener {
            viewModelScope.launch {
                repo.logout()
                _state.value = AuthState.LoggedOut
            }
        }
    }

    fun login(email: String, senha: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val result = repo.login(email, senha)
            _state.value = result.fold(
                onSuccess  = { AuthState.LoggedIn(it) },
                onFailure  = { AuthState.Error(it.message ?: "Erro") },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _state.value = AuthState.LoggedOut
        }
    }
}

package com.mrjack.dressflow.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.api.PrefsKeys
import com.mrjack.dressflow.data.api.dataStore
import com.mrjack.dressflow.data.api.getSharedPrefs
import com.mrjack.dressflow.data.model.LoginRequest
import com.mrjack.dressflow.data.model.UsuarioLogado
import kotlinx.coroutines.flow.first

class AuthRepository(private val context: Context) {
    private val api  = NetworkModule.provideApiService(context)
    private val gson = Gson()
    private val sp   = getSharedPrefs(context)

    suspend fun login(email: String, senha: String): Result<UsuarioLogado> {
        return try {
            val response = api.login(LoginRequest(email, senha))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return Result.failure(Exception("Resposta inválida do servidor"))
                val user = body.user
                    ?: return Result.failure(Exception("Campo 'usuario' ausente na resposta"))

                // SharedPreferences — leitura síncrona no interceptor
                sp.edit()
                    .putString(PrefsKeys.SP_TOKEN, body.token)
                    .putString(PrefsKeys.SP_LOJA_SLUG, user.lojaSlug ?: "mrjack")
                    .apply()

                // DataStore — persiste JSON do usuário para leitura no app
                context.dataStore.edit { prefs ->
                    prefs[PrefsKeys.TOKEN]     = body.token
                    prefs[PrefsKeys.USER_JSON] = gson.toJson(user)
                    user.lojaSlug?.let { prefs[PrefsKeys.LOJA_SLUG] = it }
                }

                Result.success(user)
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = try {
                    gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
                        ?.get("error")?.asString ?: "Credenciais inválidas"
                } catch (_: Exception) {
                    "Erro ${response.code()} — verifique email e senha"
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Erro de conexão: ${e.message}"))
        }
    }

    suspend fun getUsuarioLogado(): UsuarioLogado? {
        val json = context.dataStore.data.first()[PrefsKeys.USER_JSON] ?: return null
        return try { gson.fromJson(json, UsuarioLogado::class.java) } catch (_: Exception) { null }
    }

    suspend fun isLoggedIn(): Boolean {
        return sp.getString(PrefsKeys.SP_TOKEN, null) != null
    }

    suspend fun logout() {
        sp.edit().clear().apply()
        context.dataStore.edit { it.clear() }
    }
}

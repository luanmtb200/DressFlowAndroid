package com.mrjack.dressflow.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.mrjack.dressflow.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dressflow_prefs")

object PrefsKeys {
    val TOKEN     = stringPreferencesKey("token")
    val USER_JSON = stringPreferencesKey("user_json")
    val LOJA_SLUG = stringPreferencesKey("loja_slug")

    // SharedPreferences keys (síncrono — usado no interceptor)
    const val SP_TOKEN     = "sp_token"
    const val SP_LOJA_SLUG = "sp_loja_slug"
}

fun getSharedPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences("dressflow_sp", Context.MODE_PRIVATE)

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val sp = getSharedPrefs(context)
        val token    = sp.getString(PrefsKeys.SP_TOKEN, null)
        val lojaSlug = sp.getString(PrefsKeys.SP_LOJA_SLUG, null) ?: BuildConfig.LOJA_SLUG

        val request = chain.request().newBuilder()
            .addHeader("X-Loja-Slug", lojaSlug)
            .apply { token?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = chain.proceed(request)

        // Só notifica expiração se havia token — evita disparar no login com senha errada
        if (response.code == 401 && token != null) {
            sp.edit().remove(PrefsKeys.SP_TOKEN).apply()
            TokenExpiredNotifier.notifyExpired()
        }
        return response
    }
}

/** Singleton que avisa o app quando o token expirou. */
object TokenExpiredNotifier {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    fun notifyExpired() { listeners.toList().forEach { it() } }
}

/**
 * Converte null → "" (String) e null → false (Boolean) durante a desserialização Gson.
 * Isso evita NullPointerException quando a API retorna null para campos não-nulos do modelo Kotlin.
 */
private class NullSafeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        if (raw != String::class.java && raw != java.lang.Boolean::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)
            @Suppress("UNCHECKED_CAST")
            override fun read(inp: JsonReader): T? {
                if (inp.peek() == JsonToken.NULL) {
                    inp.nextNull()
                    return if (raw == String::class.java) ("" as T) else (false as T)
                }
                return delegate.read(inp)
            }
        }
    }
}

object NetworkModule {
    fun provideApiService(context: Context): ApiService {
        val cache = Cache(File(context.cacheDir, "http_cache"), 10L * 1024 * 1024) // 10 MB
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        val client = clientBuilder.build()

        val gson: Gson = GsonBuilder()
            .registerTypeAdapterFactory(NullSafeAdapterFactory())
            .create()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}

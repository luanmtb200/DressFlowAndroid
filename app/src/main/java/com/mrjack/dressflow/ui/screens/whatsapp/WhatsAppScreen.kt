package com.mrjack.dressflow.ui.screens.whatsapp

import android.app.Application
import android.media.MediaRecorder
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.datastore.preferences.core.edit
import coil.compose.AsyncImage
import com.google.gson.reflect.TypeToken
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.api.PrefsKeys
import com.mrjack.dressflow.data.api.dataStore
import com.mrjack.dressflow.data.model.EtiquetaWa
import com.mrjack.dressflow.data.model.GatewayConversa
import com.mrjack.dressflow.data.model.GatewayMensagem
import com.mrjack.dressflow.data.model.UsuarioLogado
import com.mrjack.dressflow.data.model.Vendedor
import com.mrjack.dressflow.data.model.WaChat
import com.mrjack.dressflow.data.model.WaLastMessage
import com.mrjack.dressflow.data.model.WaMensagem
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.navigation.WaDeeplink
import com.mrjack.dressflow.ui.theme.*
import io.socket.client.IO
import io.socket.client.Socket as IoSocket
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val GATEWAY_URL = "https://gateway.dressflow.com.br"

// ─── Mensagens rápidas Mr.Jack ────────────────────────────────────────────────

private fun isLojaAberta(): Boolean {
    val br = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
    val dow = br.get(Calendar.DAY_OF_WEEK)
    val mins = br.get(Calendar.HOUR_OF_DAY) * 60 + br.get(Calendar.MINUTE)
    return when (dow) {
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY -> mins in (9 * 60)..(18 * 60 - 1)
        Calendar.SATURDAY -> mins in (8 * 60)..(12 * 60 - 1)
        else -> false
    }
}

private fun msgHorario() = "${if (isLojaAberta()) "Estamos abertos agora." else "Estamos fechados agora."}\n" +
    "Nosso horário de atendimento é:\n\n" +
    "‎Sábado: 08:00 – 12:00\n‎Domingo: Fechada\n" +
    "‎Segunda-feira: 09:00 – 18:00\n‎Terça-feira: 09:00 – 18:00\n" +
    "‎Quarta-feira: 09:00 – 18:00\n‎Quinta-feira: 09:00 – 18:00\n‎Sexta-feira: 09:00 – 18:00"

private const val MSG_PIX = "Pix CNPJ: 34.975.009/6000-125"
private const val MSG_SAUDACAO = "✨ Olá, que satisfação receber seu contato na Mr.Jack, empresa *especializada em trajes para festa.*\n\n" +
    "👉 Meu nome é *JACK* e vou ser responsável pelo seu atendimento on-line."
private const val MSG_AVALIACAO = "Seu feedback é importante para a Mr Jack! Poste uma avaliação em nosso Perfil pelo link: https://shre.ink/mrjackavaliacaoo"

// ─── ViewModel ────────────────────────────────────────────────────────────────

class WhatsAppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    val chats          = MutableStateFlow<List<WaChat>>(emptyList())
    val chatsFiltrados = MutableStateFlow<List<WaChat>>(emptyList())
    val chatAtivo      = MutableStateFlow<WaChat?>(null)
    val mensagens      = MutableStateFlow<List<WaMensagem>>(emptyList())
    val statusWa       = MutableStateFlow("STARTING")
    val labelsMap      = MutableStateFlow<Map<String, List<EtiquetaWa>>>(emptyMap())
    val allLabels      = MutableStateFlow<List<EtiquetaWa>>(emptyList())
    val waAllLabels    = MutableStateFlow<List<EtiquetaWa>>(emptyList())
    val chatLabels     = MutableStateFlow<List<EtiquetaWa>>(emptyList())
    val labelFiltrado  = MutableStateFlow<String?>(null)
    val isLoading      = MutableStateFlow(false)
    val isLoadingMsgs  = MutableStateFlow(false)
    val isSending      = MutableStateFlow(false)
    val erro           = MutableStateFlow<String?>(null)
    val fotoMap        = MutableStateFlow<Map<String, String?>>(emptyMap())
    val vendedores     = MutableStateFlow<List<Vendedor>>(emptyList())
    val usuarioVendedorId = MutableStateFlow<Int?>(null)
    val arquivadas        = MutableStateFlow<Set<String>>(emptySet())
    val mostrarArquivadas = MutableStateFlow(false)
    val nomeLocalMap      = MutableStateFlow<Map<String, String>>(emptyMap())

    private var buscaJob: Job? = null
    private var buscaAtual = ""
    private var waSocket: IoSocket? = null
    private var listSocket: IoSocket? = null

    init {
        viewModelScope.launch {
            try { statusWa.value = api.statusWhatsApp().body()?.status ?: "UNKNOWN" } catch (_: Exception) {}
            carregarChats()
            carregarLabels()
            try { vendedores.value = api.listarVendedores().body() ?: emptyList() } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                app.dataStore.data.collect { prefs ->
                    arquivadas.value = prefs[PrefsKeys.WA_ARQUIVADAS] ?: emptySet()
                    nomeLocalMap.value = (prefs[PrefsKeys.WA_CONTATOS] ?: emptySet()).mapNotNull {
                        val p = it.split("|", limit = 2)
                        if (p.size == 2) p[0] to p[1] else null
                    }.toMap()
                    aplicarFiltros()
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                app.dataStore.data.collect { prefs ->
                    val json = prefs[PrefsKeys.USER_JSON]
                    if (json != null) {
                        val u = gson.fromJson(json, UsuarioLogado::class.java)
                        usuarioVendedorId.value = u?.vendedorId
                    }
                }
            } catch (_: Exception) {}
        }
        conectarSocketLista()
    }

    fun carregarChats() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Carrega wha-server (dados ricos: nome, foto) + gateway (TODAS as conversas)
                // em paralelo — ambos são independentes, então não há motivo para esperar
                // um terminar antes de iniciar o outro.
                val (waChats, gwConversas) = coroutineScope {
                    val waChatsDeferred = async { try { api.listarChats().body() ?: emptyList() } catch (_: Exception) { emptyList() } }
                    val gwConversasDeferred = async { carregarGatewayConversas() }
                    waChatsDeferred.await() to gwConversasDeferred.await()
                }

                // Mapa rápido: telefone → WaChat para lookup
                val waMap = mutableMapOf<String, WaChat>()
                waChats.forEach { c ->
                    val tel = c.id.replace(Regex("@.*"), "").split(":")[0]
                    waMap[tel] = c
                }

                // Aplica unread counts do gateway e adiciona conversas que só estão no gateway
                val resultado = mutableListOf<WaChat>()
                val processados = mutableSetOf<String>()

                // Chats com dados ricos do wha-server (atualizados com unread do gateway)
                waChats.forEach { waChat ->
                    val tel = waChat.id.replace(Regex("@.*"), "").split(":")[0]
                    val gw: GatewayConversa? = gwConversas.firstOrNull { conv -> conv.telefone == tel || conv.telefone == waChat.id }
                    resultado.add(waChat.copy(
                        unreadCount = gw?.total_nao_lidas ?: waChat.unreadCount,
                        lastMessage = if (gw != null)
                            WaLastMessage(gw.ultima_mensagem, false, gw.ultimo_timestamp / 1000L, "chat")
                        else waChat.lastMessage,
                    ))
                    processados.add(tel)
                }

                // Conversas APENAS no gateway (não estão no wha-server — mais antigas)
                // Exige telefone BR válido (55 + 10/11 dígitos) — evita listar conversas
                // "fantasma" (LIDs órfãos, IDs de grupo, "status" etc. salvos com telefone
                // inválido no gateway), igual ao filtro já aplicado no web.
                val telefoneValido = Regex("^55\\d{10,11}$")
                for (gw in gwConversas) {
                    val tel = gw.telefone
                    if (tel !in processados && gw.ultimo_timestamp > 0 && telefoneValido.matches(tel)) {
                        resultado.add(WaChat(
                            id = if (tel.contains("@")) tel else "${tel}@s.whatsapp.net",
                            nome = gw.nome_contato,
                            name = gw.nome_contato ?: tel,
                            telefone = if (tel.startsWith("55") && tel.length >= 12) "+$tel" else null,
                            phoneNumber = if (tel.startsWith("55") && tel.length >= 12) tel else null,
                            unreadCount = gw.total_nao_lidas,
                            isGroup = false,
                            lastMessage = WaLastMessage(gw.ultima_mensagem, false, gw.ultimo_timestamp / 1000L, "chat"),
                        ))
                        processados.add(tel)
                    }
                }

                // Ordena por última mensagem mais recente
                val sorted = resultado.sortedByDescending { chat -> chat.lastMessage?.timestamp ?: 0L }
                chats.value = sorted
                aplicarFiltros()
                sorted.take(30).forEach { c -> carregarFoto(c.id) }
            } catch (e: Exception) { erro.value = e.message }
            finally { isLoading.value = false }
        }
    }

    private suspend fun carregarGatewayConversas(): List<GatewayConversa> = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder().url("$GATEWAY_URL/conversas").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<GatewayConversa>>() {}.type
            gson.fromJson(body, type)
        } catch (_: Exception) { emptyList() }
    }

    private fun carregarLabels() {
        viewModelScope.launch {
            try {
                val map = api.listarLabels().body() ?: emptyMap()
                labelsMap.value = map
                val unique = mutableMapOf<String, EtiquetaWa>()
                map.values.flatten().forEach { if (it.name.isNotBlank()) unique[it.id] = it }
                allLabels.value = unique.values.sortedBy { it.name }
                aplicarFiltros()
            } catch (_: Exception) {}
            try { waAllLabels.value = api.listarAllLabels().body() ?: emptyList() } catch (_: Exception) {}
        }
    }

    fun carregarLabelsChat(chatId: String) {
        viewModelScope.launch {
            try {
                val encoded = java.net.URLEncoder.encode(chatId, "UTF-8")
                chatLabels.value = api.listarLabelsChat(encoded).body() ?: emptyList()
            } catch (_: Exception) { chatLabels.value = emptyList() }
        }
    }

    fun adicionarLabelChat(chatId: String, labelId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val encoded = java.net.URLEncoder.encode(chatId, "UTF-8")
                api.adicionarLabel(encoded, labelId)
                carregarLabelsChat(chatId)
            } catch (_: Exception) {}
            onDone()
        }
    }

    fun removerLabelChat(chatId: String, labelId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val encoded = java.net.URLEncoder.encode(chatId, "UTF-8")
                api.removerLabel(encoded, labelId)
                carregarLabelsChat(chatId)
            } catch (_: Exception) {}
            onDone()
        }
    }

    fun carregarFoto(chatId: String) {
        if (fotoMap.value.containsKey(chatId)) return
        viewModelScope.launch {
            try {
                val lookupId = if (chatId.contains("@lid")) {
                    val chat = chats.value.find { it.id == chatId }
                    val digits = (chat?.phoneNumber ?: chat?.telefone)?.filter { it.isDigit() }
                    if (!digits.isNullOrBlank() && digits.length >= 12) "$digits@c.us" else chatId
                } else chatId
                val encoded = java.net.URLEncoder.encode(lookupId, "UTF-8")
                val url = api.fotoChat(encoded).body()?.url
                fotoMap.value = fotoMap.value + (chatId to url)
            } catch (_: Exception) {
                fotoMap.value = fotoMap.value + (chatId to null)
            }
        }
    }

    fun filtrar(q: String) {
        buscaAtual = q
        buscaJob?.cancel()
        buscaJob = viewModelScope.launch {
            delay(200)
            aplicarFiltros()
        }
    }

    fun filtrarPorLabel(labelId: String?) {
        labelFiltrado.value = labelId
        aplicarFiltros()
    }

    fun salvarNomeLocal(tel: String, nome: String) {
        viewModelScope.launch {
            try {
                getApplication<Application>().dataStore.edit { prefs ->
                    val atual = (prefs[PrefsKeys.WA_CONTATOS] ?: emptySet()).toMutableSet()
                    atual.removeAll { it.startsWith("$tel|") }
                    if (nome.isNotBlank()) atual.add("$tel|$nome")
                    prefs[PrefsKeys.WA_CONTATOS] = atual
                }
            } catch (_: Exception) {}
        }
    }

    private fun aplicarFiltros() {
        val q = buscaAtual.trim()
        val lbl = labelFiltrado.value
        val lmap = labelsMap.value
        val arq = arquivadas.value
        val mostrarArq = mostrarArquivadas.value
        val nomes = nomeLocalMap.value
        var resultado = chats.value
            .map { c ->
                val phone = c.telefoneExibir
                val localName = if (phone.isNotBlank()) nomes[phone] else null
                if (localName != null) c.copy(name = localName) else c
            }
            .filter { c -> (c.id in arq) == mostrarArq }
        if (lbl != null) resultado = resultado.filter { c -> lmap[c.id]?.any { it.id == lbl } == true }
        if (q.isNotEmpty()) resultado = resultado.filter {
            it.nomeExibir.contains(q, ignoreCase = true) || it.telefoneExibir.contains(q)
        }
        chatsFiltrados.value = resultado
    }

    // ── Carrega mensagens do gateway SQLite (histórico completo) ─────────────────
    private suspend fun carregarDoGateway(telefone: String): List<WaMensagem> = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder()
                .url("$GATEWAY_URL/mensagens?telefone=$telefone&limit=500")
                .build()
            val response = httpClient.newCall(req).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val type = object : TypeToken<List<GatewayMensagem>>() {}.type
            val gwMsgs: List<GatewayMensagem> = gson.fromJson(body, type)
            gwMsgs.map { it.toWaMensagem() }.reversed()
        } catch (_: Exception) { emptyList() }
    }

    fun abrirChat(chat: WaChat) {
        chatAtivo.value = chat
        mensagens.value = emptyList()
        carregarLabelsChat(chat.id)
        val telefone = chat.id.replace(Regex("@.*"), "").split(":")[0]

        // Zera badge localmente de imediato (não espera socket)
        chats.value = chats.value.map { c ->
            val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
            if (cTel == telefone) c.copy(unreadCount = 0) else c
        }
        aplicarFiltros()

        viewModelScope.launch {
            isLoadingMsgs.value = true
            try {
                // Carrega do gateway (histórico completo)
                val gwMsgs = carregarDoGateway(telefone)
                if (gwMsgs.isNotEmpty()) {
                    mensagens.value = gwMsgs.distinctBy { it.id }
                } else {
                    // Fallback: wha-server (mensagens recentes)
                    val data = api.listarMensagensWa(chat.id, 100).body() ?: emptyList()
                    mensagens.value = data.sortedBy { it.timestamp }.distinctBy { it.id.ifBlank { "${it.timestamp}-${it.fromMe}" } }
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isLoadingMsgs.value = false }
        }
        conectarSocket(chat.id)
        // Marca como lida no gateway em background
        viewModelScope.launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$GATEWAY_URL/conversas/$telefone/lida")
                    .post("""{"vendedor_id":1,"dispositivo_id":"android"}"""
                        .toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun conectarSocket(chatId: String) {
        desconectarSocket()
        val telefone = chatId.replace(Regex("@.*"), "").split(":")[0]
        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            val socket = IO.socket(GATEWAY_URL, opts)
            socket.on("mensagem:nova") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val tel = data.optString("telefone")
                if (tel != telefone) return@on
                val conteudo = data.optString("conteudo")
                val ts = data.optLong("timestamp", System.currentTimeMillis())
                val direcao = data.optString("direcao", "recebida")
                val msgId = data.optString("msg_id").takeIf { it.isNotBlank() }
                val fromMe = direcao == "enviada"
                val novoId = msgId ?: "live-${System.currentTimeMillis()}"
                val nova = WaMensagem(
                    id = novoId, body = conteudo.ifBlank { null }, fromMe = fromMe,
                    timestamp = if (ts > 1_000_000_000_000L) ts / 1000L else ts,
                    type = "chat", hasMedia = false, filename = null, msgId = msgId,
                )
                viewModelScope.launch {
                    val atual = mensagens.value
                    if (atual.none { it.id == novoId || (msgId != null && it.msgId == msgId) }) {
                        mensagens.value = (atual + nova).sortedBy { it.timestamp }
                    }
                }
            }
            socket.connect()
            waSocket = socket
        } catch (_: Exception) {}
    }

    private fun desconectarSocket() {
        try { waSocket?.off(); waSocket?.disconnect() } catch (_: Exception) {}
        waSocket = null
    }

    private fun conectarSocketLista() {
        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            val socket = IO.socket(GATEWAY_URL, opts)
            socket.on("mensagem:nova") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val tel = data.optString("telefone")
                val conteudo = data.optString("conteudo")
                val ts = data.optLong("timestamp", System.currentTimeMillis())
                val direcao = data.optString("direcao", "recebida")
                val tsSeconds = if (ts > 1_000_000_000_000L) ts / 1000L else ts
                viewModelScope.launch {
                    chats.value = chats.value.map { c ->
                        val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
                        if (cTel == tel || c.phoneNumber?.filter { it.isDigit() } == tel) {
                            c.copy(
                                lastMessage = WaLastMessage(conteudo, direcao == "enviada", tsSeconds, "chat"),
                                unreadCount = if (direcao == "recebida") c.unreadCount + 1 else c.unreadCount,
                            )
                        } else c
                    }.sortedByDescending { it.lastMessage?.timestamp ?: 0 }
                    aplicarFiltros()
                }
            }
            socket.on("conversa:lida") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val tel = data.optString("telefone")
                viewModelScope.launch {
                    chats.value = chats.value.map { c ->
                        val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
                        if (cTel == tel) c.copy(unreadCount = 0) else c
                    }
                    aplicarFiltros()
                }
            }
            socket.on("conversa:nao-lida") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val tel = data.optString("telefone")
                viewModelScope.launch {
                    chats.value = chats.value.map { c ->
                        val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
                        if (cTel == tel) c.copy(unreadCount = c.unreadCount + 1) else c
                    }
                    aplicarFiltros()
                }
            }
            socket.connect()
            listSocket = socket
        } catch (_: Exception) {}
    }

    fun fecharChat() {
        desconectarSocket()
        chatAtivo.value = null
        mensagens.value = emptyList()
    }

    fun enviar(texto: String, quotedMsgId: String? = null) {
        val chat = chatAtivo.value ?: return
        viewModelScope.launch {
            isSending.value = true
            try {
                val body = mutableMapOf<String, String?>("chatId" to chat.id, "texto" to texto)
                if (quotedMsgId != null) body["quotedMsgId"] = quotedMsgId
                api.enviarMensagemWa(body.filterValues { it != null } as Map<String, String>)
                val fakeMsg = WaMensagem(
                    id = "local-${System.currentTimeMillis()}",
                    body = texto, fromMe = true,
                    timestamp = System.currentTimeMillis() / 1000,
                    type = "chat", hasMedia = false, filename = null,
                )
                mensagens.value = mensagens.value + fakeMsg
            } catch (e: Exception) { erro.value = e.message }
            finally { isSending.value = false }
        }
    }

    fun reagir(msgId: String, emoji: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        mensagens.value = mensagens.value.map { m ->
            if (m.id == msgId || m.msgId == msgId) m.copy(reaction = if (m.reaction == emoji) null else emoji) else m
        }
        viewModelScope.launch {
            try { api.reagirWa(mapOf("msgId" to msgId, "emoji" to emoji)) } catch (_: Exception) {}
        }
    }

    fun encaminhar(msgId: String, toChatId: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        viewModelScope.launch {
            try { api.encaminharWa(mapOf("msgId" to msgId, "toChatId" to toChatId)) } catch (_: Exception) {}
        }
    }

    fun fixar(msgId: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        mensagens.value = mensagens.value.map { m ->
            if (m.id == msgId || m.msgId == msgId) m.copy(pinned = !m.pinned) else m
        }
        viewModelScope.launch {
            try { api.fixarWa(mapOf("msgId" to msgId, "duration" to 86400)) } catch (_: Exception) {}
        }
    }

    fun favoritar(msgId: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        mensagens.value = mensagens.value.map { m ->
            if (m.id == msgId || m.msgId == msgId) m.copy(starred = !m.starred) else m
        }
        viewModelScope.launch {
            val msg = mensagens.value.find { it.id == msgId || it.msgId == msgId }
            try { api.favoritarWa(mapOf("msgId" to msgId, "star" to (msg?.starred ?: true))) } catch (_: Exception) {}
        }
    }

    fun marcarNaoLida(chatId: String) {
        val telefone = chatId.replace(Regex("@.*"), "").split(":")[0]
        viewModelScope.launch {
            try { api.marcarNaoLidaWa(mapOf("telefone" to telefone)) } catch (_: Exception) {}
        }
        fecharChat()
    }

    // ── Ações da lista (long-press) ──────────────────────────────────────────────

    fun marcarChatComoLida(chat: WaChat) {
        val telefone = chat.id.replace(Regex("@.*"), "").split(":")[0]
        chats.value = chats.value.map { c ->
            val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
            if (cTel == telefone) c.copy(unreadCount = 0) else c
        }
        aplicarFiltros()
        viewModelScope.launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$GATEWAY_URL/conversas/$telefone/lida")
                    .post("""{"vendedor_id":1,"dispositivo_id":"android"}"""
                        .toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }

    fun marcarChatComoNaoLida(chat: WaChat) {
        val telefone = chat.id.replace(Regex("@.*"), "").split(":")[0]
        chats.value = chats.value.map { c ->
            val cTel = c.id.replace(Regex("@.*"), "").split(":")[0]
            if (cTel == telefone && c.unreadCount == 0) c.copy(unreadCount = 1) else c
        }
        aplicarFiltros()
        viewModelScope.launch {
            try { api.marcarNaoLidaWa(mapOf("telefone" to telefone)) } catch (_: Exception) {}
        }
    }

    fun arquivar(chatId: String) {
        arquivadas.value = arquivadas.value + chatId
        aplicarFiltros()
        viewModelScope.launch {
            try { getApplication<Application>().dataStore.edit { prefs -> prefs[PrefsKeys.WA_ARQUIVADAS] = arquivadas.value } } catch (_: Exception) {}
        }
    }

    fun desarquivar(chatId: String) {
        arquivadas.value = arquivadas.value - chatId
        aplicarFiltros()
        viewModelScope.launch {
            try { getApplication<Application>().dataStore.edit { prefs -> prefs[PrefsKeys.WA_ARQUIVADAS] = arquivadas.value } } catch (_: Exception) {}
        }
    }

    fun toggleMostrarArquivadas() {
        mostrarArquivadas.value = !mostrarArquivadas.value
        aplicarFiltros()
    }

    fun excluir(msgId: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        mensagens.value = mensagens.value.filter { it.id != msgId && it.msgId != msgId }
        viewModelScope.launch {
            try { api.excluirWa(mapOf("msgId" to msgId, "everyone" to "true")) } catch (_: Exception) {}
        }
    }

    fun editar(msgId: String, novoTexto: String) {
        if (msgId.startsWith("local-") || msgId.startsWith("gw-")) return
        mensagens.value = mensagens.value.map { m ->
            if (m.id == msgId || m.msgId == msgId) m.copy(body = novoTexto) else m
        }
        viewModelScope.launch {
            try { api.editarWa(mapOf("msgId" to msgId, "texto" to novoTexto)) } catch (_: Exception) {}
        }
    }

    fun enviarUrlGaleria(chatId: String, fotos: List<com.mrjack.dressflow.data.model.GaleriaFoto>) {
        viewModelScope.launch {
            isSending.value = true
            try {
                fotos.forEach { foto ->
                    api.enviarUrlWa(mapOf("chatId" to chatId, "url" to foto.url, "filename" to foto.nome, "caption" to foto.album))
                    val fakeMsg = WaMensagem(
                        id = "local-gal-${System.currentTimeMillis()}-${foto.id}",
                        body = null, fromMe = true,
                        timestamp = System.currentTimeMillis() / 1000,
                        type = "image", hasMedia = true, filename = foto.nome,
                    )
                    mensagens.value = mensagens.value + fakeMsg
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSending.value = false }
        }
    }

    fun enviarAudioBase64(chatId: String, base64: String) {
        viewModelScope.launch {
            isSending.value = true
            try {
                api.enviarAudioWa(mapOf("chatId" to chatId, "base64" to base64, "mimeType" to "audio/mp4"))
            } catch (_: Exception) {} finally { isSending.value = false }
        }
    }

    fun enviarMidia(chatId: String, context: android.content.Context, uris: List<android.net.Uri>) {
        viewModelScope.launch {
            isSending.value = true
            try {
                uris.forEach { uri ->
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val name = uri.lastPathSegment ?: "arquivo"
                    api.enviarMidiaWa(mapOf("chatId" to chatId, "data" to b64, "mimetype" to mime, "filename" to name))
                    val fakeMsg = WaMensagem(
                        id = "local-img-${System.currentTimeMillis()}",
                        body = null, fromMe = true,
                        timestamp = System.currentTimeMillis() / 1000,
                        type = if (mime.startsWith("image")) "image" else "document",
                        hasMedia = true, filename = name,
                    )
                    mensagens.value = mensagens.value + fakeMsg
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSending.value = false }
        }
    }

    fun criarAgendamento(
        nomeCliente: String, telefone: String?, tipo: String, dataHora: String,
        vendedorId: Int? = null, observacao: String? = null,
        onSuccess: () -> Unit, onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val body = mutableMapOf<String, Any?>(
                    "nomeCliente" to nomeCliente, "tipo" to tipo,
                    "dataHora" to dataHora, "status" to "PENDENTE",
                )
                if (!telefone.isNullOrBlank()) body["telefone"] = telefone
                if (vendedorId != null) body["vendedorId"] = vendedorId
                if (!observacao.isNullOrBlank()) body["observacao"] = observacao
                val resp = api.criarAgendamento(body)
                if (resp.isSuccessful) onSuccess() else onError("Erro ${resp.code()}")
            } catch (e: Exception) { onError(e.message ?: "Erro") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        desconectarSocket()
        try { listSocket?.off(); listSocket?.disconnect() } catch (_: Exception) {}
    }
}

// ─── Tela principal ───────────────────────────────────────────────────────────

@Composable
fun WhatsAppScreen(vm: WhatsAppViewModel = viewModel()) {
    val chatAtivo    by vm.chatAtivo.collectAsState()
    val pendingPhone by WaDeeplink.targetPhone.collectAsState()

    LaunchedEffect(pendingPhone) {
        val phone = pendingPhone ?: return@LaunchedEffect
        WaDeeplink.targetPhone.value = null
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return@LaunchedEffect
        val chatId = if (digits.startsWith("55") && digits.length >= 12) "$digits@c.us" else "55$digits@c.us"
        val existing = vm.chats.value.find { c ->
            c.id == chatId ||
            (c.phoneNumber ?: c.telefone)?.filter { it.isDigit() }?.takeLast(11) == digits.takeLast(11)
        }
        val chat = existing ?: WaChat(
            id = chatId, nome = null, name = digits,
            telefone = "+$digits", phoneNumber = "+$digits",
            unreadCount = 0, isGroup = false, lastMessage = null,
        )
        vm.abrirChat(chat)
    }

    if (chatAtivo == null) {
        ListaChats(vm)
    } else {
        BackHandler { vm.fecharChat() }
        ChatViewScreen(chatAtivo!!, vm)
    }
}

// ─── Lista de chats ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaChats(vm: WhatsAppViewModel) {
    val chats      by vm.chatsFiltrados.collectAsState()
    val statusWa   by vm.statusWa.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val allLabels  by vm.allLabels.collectAsState()
    val labelSel   by vm.labelFiltrado.collectAsState()
    val arquivadas by vm.arquivadas.collectAsState()
    val mostrarArquivadas by vm.mostrarArquivadas.collectAsState()
    var busca      by remember { mutableStateOf("") }
    var showNovaConversa by remember { mutableStateOf(false) }
    var chatMenuAberto by remember { mutableStateOf<WaChat?>(null) }
    var chatEtiquetar  by remember { mutableStateOf<WaChat?>(null) }
    val chatMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listScrollState = rememberLazyListState() // preserva posição ao voltar

    if (showNovaConversa) {
        NovaConversaDialog(vm = vm, onDismiss = { showNovaConversa = false })
    }

    val etiquetarSnapshot = chatEtiquetar
    if (etiquetarSnapshot != null) {
        EtiquetarDialog(chat = etiquetarSnapshot, vm = vm, onDismiss = { chatEtiquetar = null })
    }

    // Bottom sheet de ações da conversa (long-press na lista)
    val chatMenuSnapshot = chatMenuAberto
    if (chatMenuSnapshot != null) {
        ModalBottomSheet(
            onDismissRequest = { chatMenuAberto = null },
            sheetState = chatMenuSheetState,
            containerColor = Color.White,
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                listOf(
                    (if (chatMenuSnapshot.unreadCount > 0) "✅" else "🔴") to
                        (if (chatMenuSnapshot.unreadCount > 0) "Marcar como lida" else "Marcar como não lida") to {
                            if (chatMenuSnapshot.unreadCount > 0) vm.marcarChatComoLida(chatMenuSnapshot)
                            else vm.marcarChatComoNaoLida(chatMenuSnapshot)
                            chatMenuAberto = null
                        },
                    "🏷️" to "Etiquetar" to {
                        chatEtiquetar = chatMenuSnapshot
                        vm.carregarLabelsChat(chatMenuSnapshot.id)
                        chatMenuAberto = null
                    },
                    (if (mostrarArquivadas) "📤" else "🗄️") to (if (mostrarArquivadas) "Desarquivar" else "Arquivar") to {
                        if (mostrarArquivadas) vm.desarquivar(chatMenuSnapshot.id) else vm.arquivar(chatMenuSnapshot.id)
                        chatMenuAberto = null
                    },
                ).forEach { (pair, action) ->
                    val (icon, label) = pair
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = action)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(icon, fontSize = 18.sp)
                        Text(label, fontSize = 15.sp, color = Color(0xFF1F2937))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Surface(shadowElevation = 2.dp, color = Color(0xFF128C7E)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (mostrarArquivadas) "Arquivadas" else "WhatsApp", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        color = Color.White, modifier = Modifier.weight(1f))
                    WaStatusChip(statusWa)
                    Spacer(Modifier.width(8.dp))
                    if (mostrarArquivadas || arquivadas.isNotEmpty()) {
                        IconButton(onClick = { vm.toggleMostrarArquivadas() }, modifier = Modifier.size(36.dp)) {
                            Text(if (mostrarArquivadas) "💬" else "🗄️", fontSize = 16.sp)
                        }
                    }
                    IconButton(onClick = { vm.carregarChats() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                // Search
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it; vm.filtrar(it) },
                    placeholder = { Text("Buscar conversa...", fontSize = 13.sp, color = Color.White.copy(.6f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(.8f), modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.White.copy(.3f),
                        focusedBorderColor = Color.White.copy(.8f),
                        unfocusedContainerColor = Color.White.copy(.15f),
                        focusedContainerColor = Color.White.copy(.2f),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        cursorColor = Color.White,
                    ),
                )
                // Label filter chips
                if (allLabels.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // "Todos" chip
                        Surface(
                            color = if (labelSel == null) Color.White else Color.White.copy(.25f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clickable { vm.filtrarPorLabel(null) },
                        ) {
                            Text("Todos", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                color = if (labelSel == null) Color(0xFF128C7E) else Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                        allLabels.forEach { label ->
                            val sel = labelSel == label.id
                            val cor = try { Color(android.graphics.Color.parseColor("#${label.hexColor?.trimStart('#') ?: "25D366"}")) }
                                      catch (_: Exception) { Color(0xFF25D366) }
                            Surface(
                                color = if (sel) Color.White else Color.White.copy(.25f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { vm.filtrarPorLabel(if (sel) null else label.id) },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (sel) cor else Color.White.copy(.7f)))
                                    Text(label.name, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = if (sel) Color(0xFF128C7E) else Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF25D366))
            }
        } else if (chats.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        mostrarArquivadas -> "Nenhuma conversa arquivada"
                        statusWa == "WORKING" -> "Nenhuma conversa encontrada"
                        else -> "WhatsApp não conectado"
                    },
                    color = Gray500, fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(state = listScrollState, modifier = Modifier.weight(1f)) {
                itemsIndexed(chats) { _, c ->
                    ChatItem(c, vm, onClick = { vm.abrirChat(c) }, onLongClick = { chatMenuAberto = c })
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
    FloatingActionButton(
        onClick = { showNovaConversa = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        containerColor = Color(0xFF25D366),
    ) {
        Icon(Icons.Default.Add, null, tint = Color.White)
    }
    } // Box
}

// ─── Item de conversa ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(c: WaChat, vm: WhatsAppViewModel, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val fotoMap by vm.fotoMap.collectAsState()
    val labelsMap by vm.labelsMap.collectAsState()
    val fotoUrl = fotoMap[c.id]
    val labels = labelsMap[c.id] ?: emptyList()

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar — sem badge (badge fica no texto)
        if (fotoUrl != null) {
            AsyncImage(
                model = fotoUrl, contentDescription = null,
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            val isGroup = c.isGroup == true
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape)
                    .background(if (isGroup) Color(0xFF25D366) else Color(0xFF667EEA)),
                contentAlignment = Alignment.Center,
            ) {
                if (isGroup) Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(26.dp))
                else Text(c.nomeExibir.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.nomeExibir,
                    fontWeight = if (c.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp, color = Color(0xFF1F2937),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                c.lastMessage?.let {
                    Text(fmtTs(it.timestamp), fontSize = 11.sp,
                        color = if (c.unreadCount > 0) Color(0xFF25D366) else Gray500)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (c.lastMessage?.fromMe == true) {
                    Icon(Icons.Default.DoneAll, null, tint = Color(0xFF25D366), modifier = Modifier.size(14.dp))
                }
                Text(
                    labelMensagem(c.lastMessage), fontSize = 13.sp,
                    color = if (c.unreadCount > 0) Color(0xFF1F2937) else Gray500,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                // Badge de não lidas — na lateral do texto (não no avatar)
                if (c.unreadCount > 0) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF25D366)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (c.unreadCount > 99) "99+" else "${c.unreadCount}",
                            color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                    labels.take(3).forEach { lbl ->
                        val cor = try { Color(android.graphics.Color.parseColor("#${lbl.hexColor?.trimStart('#') ?: "25D366"}")) }
                                  catch (_: Exception) { Color(0xFF25D366) }
                        Surface(color = cor.copy(.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(lbl.name, fontSize = 9.sp, color = cor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun labelMensagem(msg: WaLastMessage?): String {
    if (msg == null) return ""
    if (msg.body.isNullOrBlank()) return when (msg.type) {
        "audio", "ptt" -> "🎤 Áudio"
        "image"        -> "📷 Foto"
        "video"        -> "🎥 Vídeo"
        "document"     -> "📎 Documento"
        "sticker"      -> "🎭 Sticker"
        else           -> "📎 Arquivo"
    }
    return if ((msg.body?.length ?: 0) > 45) "${msg.body?.take(45)}…" else msg.body ?: ""
}

// ─── Tela de chat ─────────────────────────────────────────────────────────────

private val QUICK_EMOJIS = listOf("👍","❤️","😂","😮","😢","🙏")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatViewScreen(chat: WaChat, vm: WhatsAppViewModel) {
    val ctx          = LocalContext.current
    val msgs         by vm.mensagens.collectAsState()
    val isLoading    by vm.isLoadingMsgs.collectAsState()
    val isSending    by vm.isSending.collectAsState()
    val statusWa     by vm.statusWa.collectAsState()
    val fotoMap      by vm.fotoMap.collectAsState()
    val fotoUrl      = fotoMap[chat.id]
    val chatLabels   by vm.chatLabels.collectAsState()
    var texto        by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()
    var quickReply   by remember { mutableStateOf("") }
    var showAgendar  by remember { mutableStateOf(false) }
    var showEtiquetar by remember { mutableStateOf(false) }
    var showGaleria  by remember { mutableStateOf(false) }
    var replyTo        by remember { mutableStateOf<WaMensagem?>(null) }
    var msgMenu        by remember { mutableStateOf<WaMensagem?>(null) }
    var showEncaminhar by remember { mutableStateOf(false) }
    var editandoMsg    by remember { mutableStateOf<WaMensagem?>(null) }
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chatsDisponiveis by vm.chats.collectAsState()

    // Agrupa mensagens por data — deve ficar antes do LaunchedEffect
    val grupos = remember(msgs) {
        val result = mutableListOf<Pair<String?, WaMensagem>>()
        var lastDate = ""
        for (m in msgs) {
            val dateKey = fmtData(m.timestamp)
            if (dateKey != lastDate) {
                lastDate = dateKey
                result.add(dateKey to WaMensagem("sep-$dateKey", dateKey, false, m.timestamp, "sep", false, null))
            }
            result.add(null to m)
        }
        result
    }

    LaunchedEffect(grupos.size) {
        if (grupos.isNotEmpty()) {
            try { listState.animateScrollToItem(grupos.size - 1) } catch (_: Exception) {}
        }
    }

    if (showAgendar) {
        AgendamentoDialog(chat = chat, vm = vm, onDismiss = { showAgendar = false })
    }
    if (showEtiquetar) {
        EtiquetarDialog(chat = chat, vm = vm, onDismiss = { showEtiquetar = false })
    }
    if (showGaleria) {
        GaleriaModal(
            chatId = chat.id,
            onDismiss = { showGaleria = false },
            onEnviar = { fotos -> vm.enviarUrlGaleria(chat.id, fotos) },
        )
    }

    // Bottom sheet de ações de mensagem
    val msgMenuSnapshot = msgMenu
    if (msgMenuSnapshot != null) {
        ModalBottomSheet(
            onDismissRequest = { msgMenu = null },
            sheetState = sheetState,
            containerColor = Color.White,
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                // Quick reactions
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QUICK_EMOJIS.forEach { emoji ->
                        val isSel = msgMenuSnapshot.reaction == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) Color(0xFFDCF8C6) else Color(0xFFF5F5F5),
                            modifier = Modifier.size(48.dp).clickable {
                                val id = msgMenuSnapshot.msgId ?: msgMenuSnapshot.id
                                vm.reagir(id, emoji)
                                msgMenu = null
                            },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
                HorizontalDivider()
                // Ações
                listOf(
                    "↩️" to "Responder" to { replyTo = msgMenuSnapshot; msgMenu = null },
                    if (msgMenuSnapshot.fromMe) "✏️" to "Editar mensagem" to { editandoMsg = msgMenuSnapshot; msgMenu = null }
                    else null,
                    "↗️" to "Encaminhar" to { showEncaminhar = true; msgMenu = null },
                    "📌" to (if (msgMenuSnapshot.pinned) "Desafixar" else "Fixar") to {
                        vm.fixar(msgMenuSnapshot.msgId ?: msgMenuSnapshot.id); msgMenu = null
                    },
                    "⭐" to (if (msgMenuSnapshot.starred) "Desfavoritar" else "Favoritar") to {
                        vm.favoritar(msgMenuSnapshot.msgId ?: msgMenuSnapshot.id); msgMenu = null
                    },
                    "🗑️" to "Excluir para todos" to {
                        vm.excluir(msgMenuSnapshot.msgId ?: msgMenuSnapshot.id); msgMenu = null
                    },
                    "🔴" to "Marcar conversa como não lida" to {
                        vm.marcarNaoLida(chat.id); msgMenu = null
                    },
                ).filterNotNull().forEach { (pair, action) ->
                    val (icon, label) = pair
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = action)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(icon, fontSize = 18.sp)
                        Text(label, fontSize = 15.sp, color = if (label.contains("não lida")) Color(0xFFEF4444) else Color(0xFF1F2937))
                    }
                }
            }
        }
    }

    // Modal de encaminhar
    if (showEncaminhar && msgMenu == null) {
        val msgParaEnc = msgMenuSnapshot
        Dialog(onDismissRequest = { showEncaminhar = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encaminhar para", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    var busca by remember { mutableStateOf("") }
                    OutlinedTextField(value = busca, onValueChange = { busca = it },
                        placeholder = { Text("Pesquisar...") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp), singleLine = true)
                    val filtrados = chatsDisponiveis.filter { c ->
                        c.isGroup != true && (c.nomeExibir.contains(busca, true) || c.telefoneExibir.contains(busca))
                    }.take(20)
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(filtrados) { _, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (msgParaEnc != null) vm.encaminhar(msgParaEnc.msgId ?: msgParaEnc.id, c.id)
                                    showEncaminhar = false
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF667EEA)),
                                    contentAlignment = Alignment.Center) {
                                    Text(c.nomeExibir.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(c.nomeExibir, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(c.telefoneExibir, fontSize = 11.sp, color = Gray500)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    OutlinedButton(onClick = { showEncaminhar = false }, Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFECE5DD))) {
        // Header
        Surface(shadowElevation = 2.dp, color = Color(0xFF128C7E)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = { vm.fecharChat() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    // Avatar
                    if (fotoUrl != null) {
                        AsyncImage(
                            model = fotoUrl, contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (chat.isGroup == true) Color(0xFF25D366) else Color(0xFF667EEA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (chat.isGroup == true) Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            else Text(chat.nomeExibir.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(chat.nomeExibir, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chat.telefoneExibir.isNotBlank()) {
                            Text(chat.telefoneExibir, fontSize = 11.sp, color = Color.White.copy(.8f))
                        }
                    }
                    Surface(
                        color = Color.White.copy(.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showAgendar = true },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.DateRange, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Agendar", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(
                        color = Color.White.copy(.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showEtiquetar = true },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.Label, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Etiquetar", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (chat.isGroup != true && chat.telefoneExibir.isNotBlank()) {
                        var mostrarDialogNome by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { mostrarDialogNome = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Salvar contato", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        if (mostrarDialogNome) {
                            var nomeDigitado by remember {
                                mutableStateOf(
                                    if (chat.nomeExibir == chat.telefoneExibir || chat.nomeExibir.matches(Regex("\\+?\\d{8,15}"))) ""
                                    else chat.nomeExibir
                                )
                            }
                            AlertDialog(
                                onDismissRequest = { mostrarDialogNome = false },
                                title = { Text("Salvar contato", fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(chat.telefoneExibir, fontSize = 13.sp, color = Color(0xFF6B7280))
                                        OutlinedTextField(
                                            value = nomeDigitado,
                                            onValueChange = { nomeDigitado = it },
                                            label = { Text("Nome") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        vm.salvarNomeLocal(chat.telefoneExibir, nomeDigitado.trim())
                                        mostrarDialogNome = false
                                    }) { Text("Salvar") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { mostrarDialogNome = false }) { Text("Cancelar") }
                                },
                            )
                        }
                    }
                }
                // Labels do chat
                if (chatLabels.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        chatLabels.forEach { lbl ->
                            val cor = try { Color(android.graphics.Color.parseColor("#${lbl.hexColor?.trimStart('#') ?: "25D366"}")) }
                                      catch (_: Exception) { Color(0xFF25D366) }
                            Surface(color = Color.White.copy(.2f), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
                                    Text(lbl.name, fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mensagens
        if (isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF25D366))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                itemsIndexed(grupos) { _, (isSep, msg) ->
                    if (isSep != null) {
                        // Separador de data
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Surface(color = Color(0xFFD1F4CC), shape = RoundedCornerShape(8.dp)) {
                                Text(isSep, fontSize = 11.sp, color = Color(0xFF4A4A4A),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    } else {
                        WaBubble(msg, onLongPress = { msgMenu = it })
                    }
                }
            }
        }

        // Respostas rápidas
        if (statusWa == "WORKING") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(Color(0xFFF0F2F5)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "🕐 Horário" to { msgHorario() },
                    "💳 Pix"     to { MSG_PIX },
                    "👋 Saudação" to { MSG_SAUDACAO },
                    "⭐ Avaliação" to { MSG_AVALIACAO },
                ).forEach { (label, getMsg) ->
                    OutlinedButton(
                        onClick = { quickReply = getMsg(); texto = quickReply },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C7E)),
                        border = BorderStroke(1.dp, Color(0xFF128C7E).copy(.4f)),
                    ) { Text(label, fontSize = 11.sp) }
                }
            }
        }

        // Reply preview
        val replySnap = replyTo
        if (replySnap != null) {
            Surface(color = Color(0xFFF0F2F5)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = Color.White, shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row {
                            Box(Modifier.width(4.dp).height(48.dp).background(Color(0xFF25D366)))
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    if (replySnap.fromMe) "Você" else chat.nomeExibir,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF25D366),
                                )
                                Text(
                                    (replySnap.body ?: "📎 Mídia").take(60),
                                    fontSize = 12.sp, color = Gray500, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { replyTo = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Gray500, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Barra de edição
        val editSnap = editandoMsg
        if (editSnap != null) {
            var editTexto by remember(editSnap.id) { mutableStateOf(editSnap.body ?: "") }
            Surface(color = Color(0xFFF0F2F5)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Row {
                            Box(Modifier.width(4.dp).height(56.dp).background(Color(0xFF2563EB)))
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("✏️ Editar mensagem", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                OutlinedTextField(
                                    value = editTexto, onValueChange = { editTexto = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true, shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = {
                            val msgId = editSnap.msgId ?: editSnap.id
                            vm.editar(msgId, editTexto.trim())
                            editandoMsg = null
                        }, enabled = editTexto.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)) { Text("Salvar", fontSize = 11.sp) }
                        OutlinedButton(onClick = { editandoMsg = null },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)) { Text("Cancelar", fontSize = 11.sp) }
                    }
                }
            }
        }

        // Input
        if (editandoMsg == null) {
            // ── Gravação de áudio ─────────────────────────────────────────────
            var gravando by remember { mutableStateOf(false) }
            var duracao  by remember { mutableStateOf(0) }
            var recorderRef = remember<MediaRecorder?> { null }
            var audioFile  = remember { "" }
            var timerJob   = remember<kotlinx.coroutines.Job?> { null }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            fun pararGravacao(cancelar: Boolean) {
                timerJob?.cancel(); timerJob = null
                try { recorderRef?.stop(); recorderRef?.release() } catch (_: Exception) {}
                recorderRef = null
                gravando = false; duracao = 0
                if (!cancelar && audioFile.isNotBlank()) {
                    val f = java.io.File(audioFile)
                    if (f.exists()) {
                        val bytes = f.readBytes()
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        vm.enviarAudioBase64(chat.id, b64)
                        f.delete()
                    }
                }
                audioFile = ""
            }

            fun iniciarGravacao() {
                try {
                    val file = java.io.File(ctx.cacheDir, "wa_audio_${System.currentTimeMillis()}.mp4")
                    audioFile = file.absolutePath
                    val rec = MediaRecorder(ctx)
                    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    rec.setOutputFile(audioFile)
                    rec.prepare(); rec.start()
                    recorderRef = rec; gravando = true; duracao = 0
                    timerJob = scope.launch {
                        while (true) { kotlinx.coroutines.delay(1000); duracao++ }
                    }
                } catch (_: Exception) { gravando = false }
            }

            Surface(shadowElevation = 2.dp, color = Color(0xFFF0F2F5)) {
                if (gravando) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = { pararGravacao(true) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444))
                        }
                        // Ondas animadas
                        Row(Modifier.weight(1f).background(Color.White, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(12) { i ->
                                val h = (8 + (i % 3) * 8).dp
                                Box(Modifier.width(3.dp).height(h).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                            }
                            Spacer(Modifier.weight(1f))
                            val m = duracao / 60; val s = duracao % 60
                            Text("%02d:%02d".format(m, s), fontSize = 14.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                        }
                        // Botão ENVIAR
                        FloatingActionButton(onClick = { pararGravacao(false) },
                            containerColor = Color(0xFF25D366), modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Send, null, tint = Color.White)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = { showGaleria = true }, enabled = !isSending && statusWa == "WORKING", modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.AttachFile, null, tint = if (statusWa == "WORKING") Color(0xFF128C7E) else Gray500)
                        }
                        OutlinedTextField(value = texto, onValueChange = { texto = it },
                            placeholder = { Text("Mensagem", color = Gray500) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 5,
                            enabled = !isSending && statusWa == "WORKING",
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                            ),
                        )
                        if (texto.isNotBlank()) {
                            FloatingActionButton(onClick = {
                                val t = texto.trim()
                                if (t.isNotEmpty() && statusWa == "WORKING") {
                                    val quotedId = replyTo?.msgId ?: replyTo?.id
                                    vm.enviar(t, quotedId); texto = ""; replyTo = null
                                }
                            }, containerColor = Color(0xFF25D366), modifier = Modifier.size(48.dp)) {
                                if (isSending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Send, null, tint = Color.White)
                            }
                        } else {
                            // Microfone
                            FloatingActionButton(onClick = { iniciarGravacao() },
                                containerColor = Color(0xFF25D366), modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Mic, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Bolha de mensagem ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaBubble(msg: WaMensagem, onLongPress: (WaMensagem) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = if (msg.fromMe) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp, topEnd = 14.dp,
                bottomStart = if (msg.fromMe) 14.dp else 4.dp,
                bottomEnd = if (msg.fromMe) 4.dp else 14.dp,
            ),
            color = if (msg.fromMe) Color(0xFFDCF8C6) else Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp).combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(msg) },
            ),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // Indicadores visuais de pin e star
                if (msg.pinned || msg.starred) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
                        if (msg.pinned) Text("📌", fontSize = 10.sp)
                        if (msg.starred) Text("⭐", fontSize = 10.sp)
                    }
                }
                when {
                    msg.hasMedia && msg.body.isNullOrBlank() -> {
                        Text(when (msg.type) {
                            "audio", "ptt" -> "🎤 Áudio"
                            "image"        -> "📷 Foto"
                            "video"        -> "🎥 Vídeo"
                            "document"     -> "📎 ${msg.filename ?: "Documento"}"
                            "sticker"      -> "🎭 Sticker"
                            else           -> "📎 Arquivo"
                        }, fontSize = 14.sp, color = Gray700)
                    }
                    else -> Text(msg.body ?: "", fontSize = 14.sp, color = Color(0xFF1F2937))
                }
                Row(
                    Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (msg.reaction != null) Text(msg.reaction, fontSize = 14.sp)
                    Text(fmtTs(msg.timestamp), fontSize = 10.sp, color = Gray500)
                    if (msg.fromMe) {
                        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF34B7F1), modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

// ─── Agendamento Dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendamentoDialog(chat: WaChat, vm: WhatsAppViewModel, onDismiss: () -> Unit) {
    val vendedores by vm.vendedores.collectAsState()
    val usuarioVendedorId by vm.usuarioVendedorId.collectAsState()

    val telRaw = remember(chat) {
        // Prefere phoneNumber/telefone reais; nunca usa LID (@lid ID) como telefone
        val rawPhone = (chat.phoneNumber ?: chat.telefone)?.filter { it.isDigit() } ?: ""
        if (rawPhone.length >= 10) {
            rawPhone.removePrefix("55").take(11)
        } else {
            // Fallback só para @c.us/@s.whatsapp.net (não @lid)
            val id = chat.id
            if (!id.contains("@lid")) id.replace(Regex("@.*"), "").removePrefix("55").take(11)
            else ""
        }
    }

    var nomeCliente  by remember { mutableStateOf(chat.nomeExibir) }
    var telefone     by remember { mutableStateOf(telRaw) }
    var tipo         by remember { mutableStateOf("ATENDIMENTO") }
    var dataSel      by remember { mutableStateOf("") }
    var horaSel      by remember { mutableStateOf("") }
    var vendedorId   by remember { mutableStateOf<Int?>(null) }
    var observacao   by remember { mutableStateOf("") }

    LaunchedEffect(usuarioVendedorId) {
        if (vendedorId == null) vendedorId = usuarioVendedorId
    }
    var salvando     by remember { mutableStateOf(false) }
    var ok           by remember { mutableStateOf(false) }
    var erro         by remember { mutableStateOf<String?>(null) }
    var tipoExpand   by remember { mutableStateOf(false) }
    var vendExpand   by remember { mutableStateOf(false) }

    val slots = remember(dataSel) {
        if (dataSel.isEmpty()) return@remember emptyList()
        try {
            val parts = dataSel.split("-")
            val cal = Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY ->
                    listOf("09:00","10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00")
                Calendar.SATURDAY ->
                    listOf("08:00","09:00","10:00","11:00","12:00")
                else -> emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    LaunchedEffect(slots) { if (horaSel !in slots) horaSel = "" }

    val tiposAg = listOf(
        "ATENDIMENTO" to "Atendimento",
        "PROVA"       to "Prova",
        "PRIMEIRA_PROVA_VESTIDO" to "1ª Prova Vestido",
        "RETIRADA_VESTIDO"       to "Retirada Vestido",
        "OUTRO"       to "Outro",
    )
    val tipoLabel = tiposAg.find { it.first == tipo }?.second ?: tipo

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Novo Agendamento", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1F2937))

                if (ok) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("✓ Agendamento salvo!", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = nomeCliente, onValueChange = { nomeCliente = it },
                        label = { Text("Nome do cliente *") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = telefone, onValueChange = { telefone = it },
                            label = { Text("Telefone") }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                        ExposedDropdownMenuBox(
                            expanded = tipoExpand, onExpandedChange = { tipoExpand = it },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = tipoLabel, onValueChange = {}, readOnly = true,
                                label = { Text("Tipo") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tipoExpand) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp), singleLine = true,
                            )
                            ExposedDropdownMenu(expanded = tipoExpand, onDismissRequest = { tipoExpand = false }) {
                                tiposAg.forEach { (v, l) ->
                                    DropdownMenuItem(text = { Text(l) }, onClick = { tipo = v; tipoExpand = false })
                                }
                            }
                        }
                    }

                    DatePickerField(
                        label = "Data *", value = dataSel,
                        onDateSelected = { dataSel = it }, modifier = Modifier.fillMaxWidth(),
                    )

                    if (dataSel.isNotEmpty()) {
                        if (slots.isEmpty()) {
                            Text("Sem horários disponíveis (Domingo)", fontSize = 12.sp, color = Color(0xFFEF4444))
                        } else {
                            Text("Horário *", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                slots.forEach { h ->
                                    val sel = horaSel == h
                                    Surface(
                                        color = if (sel) Color(0xFF128C7E) else Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (sel) Color(0xFF128C7E) else Color(0xFFD1D5DB)),
                                        modifier = Modifier.clickable { horaSel = h },
                                    ) {
                                        Text(
                                            h, fontSize = 12.sp,
                                            color = if (sel) Color.White else Color(0xFF374151),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (vendedores.isNotEmpty()) {
                        val vendNome = vendedores.find { it.id == vendedorId }?.nome ?: ""
                        ExposedDropdownMenuBox(expanded = vendExpand, onExpandedChange = { vendExpand = it }) {
                            OutlinedTextField(
                                value = vendNome, onValueChange = {}, readOnly = true,
                                label = { Text("Vendedor *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vendExpand) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp), singleLine = true,
                            )
                            ExposedDropdownMenu(expanded = vendExpand, onDismissRequest = { vendExpand = false }) {
                                vendedores.filter { it.ativo }.forEach { v ->
                                    DropdownMenuItem(text = { Text(v.nome) }, onClick = { vendedorId = v.id; vendExpand = false })
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = observacao, onValueChange = { observacao = it },
                        label = { Text("Observação") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp), maxLines = 3,
                    )

                    if (erro != null) {
                        Text(erro!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (nomeCliente.isBlank() || dataSel.isBlank() || horaSel.isBlank() || vendedorId == null) {
                                    erro = "Preencha nome, data, horário e vendedor"
                                    return@Button
                                }
                                salvando = true; erro = null
                                vm.criarAgendamento(
                                    nomeCliente = nomeCliente,
                                    telefone    = telefone.filter { it.isDigit() }.ifBlank { null },
                                    tipo        = tipo,
                                    dataHora    = "${dataSel}T${horaSel}:00",
                                    vendedorId  = vendedorId,
                                    observacao  = observacao.ifBlank { null },
                                    onSuccess   = { ok = true; salvando = false },
                                    onError     = { msg -> erro = msg; salvando = false },
                                )
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !salvando,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (salvando) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Salvar")
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}

// ─── Nova Conversa Dialog ────────────────────────────────────────────────────

@Composable
fun NovaConversaDialog(vm: WhatsAppViewModel, onDismiss: () -> Unit) {
    var numero by remember { mutableStateOf("") }
    var nome   by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Nova Conversa", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1F2937))

                OutlinedTextField(
                    value = numero, onValueChange = { numero = it },
                    label = { Text("Número com DDD *") },
                    placeholder = { Text("Ex: 85999999999") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )

                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val digits = numero.filter { it.isDigit() }
                    Button(
                        onClick = {
                            if (digits.isBlank()) return@Button
                            val chatId = "55$digits@c.us"
                            val chatNome = nome.ifBlank { "+55 $digits" }
                            val fakeChat = WaChat(
                                id = chatId, nome = chatNome, name = chatNome,
                                telefone = "+55$digits", phoneNumber = "+55$digits",
                                unreadCount = 0, isGroup = false, lastMessage = null,
                            )
                            vm.abrirChat(fakeChat)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        enabled = digits.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E)),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Abrir Conversa") }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Cancelar") }
                }
            }
        }
    }
}

// ─── Etiquetar Dialog ────────────────────────────────────────────────────────

@Composable
fun EtiquetarDialog(chat: WaChat, vm: WhatsAppViewModel, onDismiss: () -> Unit) {
    val waAllLabels by vm.waAllLabels.collectAsState()
    val chatLabels  by vm.chatLabels.collectAsState()
    var salvando    by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Etiquetas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1F2937))

                if (waAllLabels.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF25D366))
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        waAllLabels.forEach { label ->
                            val checked = chatLabels.any { it.id == label.id }
                            val cor = try {
                                Color(android.graphics.Color.parseColor("#${label.hexColor?.trimStart('#') ?: "25D366"}"))
                            } catch (_: Exception) { Color(0xFF25D366) }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !salvando) {
                                    salvando = true
                                    if (checked) vm.removerLabelChat(chat.id, label.id) { salvando = false }
                                    else         vm.adicionarLabelChat(chat.id, label.id) { salvando = false }
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = cor),
                                )
                                Box(Modifier.size(12.dp).clip(CircleShape).background(cor))
                                Text(label.name, fontSize = 14.sp, color = Color(0xFF1F2937), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Fechar") }
            }
        }
    }
}

// ─── Status chip ─────────────────────────────────────────────────────────────

@Composable
fun WaStatusChip(status: String) {
    val (cor, texto) = when (status) {
        "WORKING"      -> Color(0xFF4ADE80) to "Conectado"
        "STARTING"     -> Color(0xFFFBBF24) to "Conectando..."
        "SCAN_QR_CODE" -> Color(0xFFFBBF24) to "QR Code"
        else           -> Color(0xFFFC8181) to "Offline"
    }
    Surface(color = cor.copy(.2f), shape = RoundedCornerShape(6.dp)) {
        Text(texto, color = cor, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ─── Helpers de formatação ────────────────────────────────────────────────────

fun fmtTs(ts: Long): String {
    return try {
        val millis = if (ts > 1_000_000_000_000L) ts else ts * 1000L
        val d = Date(millis)
        val sdfDay  = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val sdfHm   = SimpleDateFormat("HH:mm",    Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM",    Locale.getDefault())
        val hoje = Date()
        val ontemCal = java.util.Calendar.getInstance().also { it.add(java.util.Calendar.DAY_OF_MONTH, -1) }
        when {
            sdfDay.format(d) == sdfDay.format(hoje)            -> sdfHm.format(d)
            sdfDay.format(d) == sdfDay.format(ontemCal.time)   -> "Ontem ${sdfHm.format(d)}"
            else                                                -> "${sdfDate.format(d)} ${sdfHm.format(d)}"
        }
    } catch (_: Exception) { "" }
}

private fun fmtData(ts: Long): String {
    return try {
        val millis = if (ts > 1_000_000_000_000L) ts else ts * 1000L
        val d = Date(millis)
        val hoje = Date()
        val sdfCheck = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val ontemCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_MONTH, -1) }
        when {
            sdfCheck.format(d) == sdfCheck.format(hoje) -> "Hoje"
            sdfCheck.format(d) == sdfCheck.format(ontemCal.time) -> "Ontem"
            else -> sdfDisplay.format(d)
        }
    } catch (_: Exception) { "" }
}

package com.mrjack.dressflow.ui.screens.whatsapp

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.EtiquetaWa
import com.mrjack.dressflow.data.model.WaChat
import com.mrjack.dressflow.data.model.WaLastMessage
import com.mrjack.dressflow.data.model.WaMensagem
import com.mrjack.dressflow.ui.theme.*
import io.socket.client.IO
import io.socket.client.Socket as IoSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val GATEWAY_URL = "https://dressflow-whatsapp.fly.dev"

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

    val chats          = MutableStateFlow<List<WaChat>>(emptyList())
    val chatsFiltrados = MutableStateFlow<List<WaChat>>(emptyList())
    val chatAtivo      = MutableStateFlow<WaChat?>(null)
    val mensagens      = MutableStateFlow<List<WaMensagem>>(emptyList())
    val statusWa       = MutableStateFlow("STARTING")
    val labelsMap      = MutableStateFlow<Map<String, List<EtiquetaWa>>>(emptyMap())
    val allLabels      = MutableStateFlow<List<EtiquetaWa>>(emptyList())
    val labelFiltrado  = MutableStateFlow<String?>(null)
    val isLoading      = MutableStateFlow(false)
    val isLoadingMsgs  = MutableStateFlow(false)
    val isSending      = MutableStateFlow(false)
    val erro           = MutableStateFlow<String?>(null)
    val fotoMap        = MutableStateFlow<Map<String, String?>>(emptyMap())

    private var buscaJob: Job? = null
    private var buscaAtual = ""
    private var pollJob: Job? = null
    private var waSocket: IoSocket? = null

    init {
        viewModelScope.launch {
            try { statusWa.value = api.statusWhatsApp().body()?.status ?: "UNKNOWN" } catch (_: Exception) {}
            carregarChats()
            carregarLabels()
        }
    }

    fun carregarChats() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val lista = api.listarChats().body() ?: emptyList()
                chats.value = lista
                aplicarFiltros()
                lista.take(30).forEach { c -> carregarFoto(c.id) }
            } catch (e: Exception) { erro.value = e.message }
            finally { isLoading.value = false }
        }
    }

    private fun carregarLabels() {
        viewModelScope.launch {
            try {
                val map = api.listarLabels().body() ?: emptyMap()
                labelsMap.value = map
                val unique = mutableMapOf<String, EtiquetaWa>()
                map.values.flatten().forEach { unique[it.id] = it }
                allLabels.value = unique.values.sortedBy { it.name }
                aplicarFiltros()
            } catch (_: Exception) {}
        }
    }

    fun carregarFoto(chatId: String) {
        if (fotoMap.value.containsKey(chatId)) return
        viewModelScope.launch {
            try {
                val url = api.fotoChat(chatId).body()?.url
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

    private fun aplicarFiltros() {
        val q = buscaAtual.trim()
        val lbl = labelFiltrado.value
        val lmap = labelsMap.value
        var resultado = chats.value
        if (lbl != null) {
            resultado = resultado.filter { c -> lmap[c.id]?.any { it.id == lbl } == true }
        }
        if (q.isNotEmpty()) {
            resultado = resultado.filter {
                it.nomeExibir.contains(q, ignoreCase = true) ||
                it.telefoneExibir.contains(q)
            }
        }
        chatsFiltrados.value = resultado
    }

    fun abrirChat(chat: WaChat) {
        chatAtivo.value = chat
        mensagens.value = emptyList()
        viewModelScope.launch {
            isLoadingMsgs.value = true
            try {
                val data = api.listarMensagensWa(chat.id, 60).body() ?: emptyList()
                mensagens.value = data.sortedBy { it.timestamp }
            } catch (e: Exception) { erro.value = e.message }
            finally { isLoadingMsgs.value = false }
        }
        conectarSocket(chat.id)
        iniciarPolling(chat.id)
    }

    private fun conectarSocket(chatId: String) {
        desconectarSocket()
        val telefone = chatId.replace(Regex("@.*"), "")
        try {
            val opts = IO.Options()
            opts.transports = arrayOf("websocket", "polling")
            val socket = IO.socket(GATEWAY_URL, opts)
            socket.on("mensagem:nova") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val tel = data.optString("telefone")
                if (tel != telefone) return@on
                val conteudo = data.optString("conteudo")
                val ts = data.optLong("timestamp", System.currentTimeMillis() / 1000L)
                val direcao = data.optString("direcao", "in")
                val msgId = data.optString("msg_id").takeIf { it.isNotBlank() }
                val fromMe = direcao == "out"
                val novoId = if (msgId != null) "wa-$msgId" else "live-${System.currentTimeMillis()}"
                val nova = WaMensagem(
                    id = novoId, body = conteudo.ifBlank { null }, fromMe = fromMe,
                    timestamp = ts, type = "chat", hasMedia = false, filename = null,
                )
                viewModelScope.launch {
                    val atual = mensagens.value
                    if (atual.none { it.id == novoId }) {
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

    private fun iniciarPolling(chatId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(8000)
                try {
                    val data = api.listarMensagensWa(chatId, 60).body() ?: continue
                    val ordenado = data.sortedBy { it.timestamp }
                    if (ordenado.size != mensagens.value.size ||
                        ordenado.lastOrNull()?.id != mensagens.value.lastOrNull()?.id) {
                        mensagens.value = ordenado
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun fecharChat() {
        pollJob?.cancel()
        desconectarSocket()
        chatAtivo.value = null
        mensagens.value = emptyList()
    }

    fun enviar(texto: String) {
        val chat = chatAtivo.value ?: return
        viewModelScope.launch {
            isSending.value = true
            try {
                api.enviarMensagemWa(mapOf("chatId" to chat.id, "texto" to texto))
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

    override fun onCleared() { super.onCleared(); pollJob?.cancel(); desconectarSocket() }
}

// ─── Tela principal ───────────────────────────────────────────────────────────

@Composable
fun WhatsAppScreen(vm: WhatsAppViewModel = viewModel()) {
    val chatAtivo by vm.chatAtivo.collectAsState()

    if (chatAtivo == null) {
        ListaChats(vm)
    } else {
        BackHandler { vm.fecharChat() }
        ChatViewScreen(chatAtivo!!, vm)
    }
}

// ─── Lista de chats ───────────────────────────────────────────────────────────

@Composable
fun ListaChats(vm: WhatsAppViewModel) {
    val chats      by vm.chatsFiltrados.collectAsState()
    val statusWa   by vm.statusWa.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val allLabels  by vm.allLabels.collectAsState()
    val labelSel   by vm.labelFiltrado.collectAsState()
    var busca      by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Surface(shadowElevation = 2.dp, color = Color(0xFF128C7E)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        color = Color.White, modifier = Modifier.weight(1f))
                    WaStatusChip(statusWa)
                    Spacer(Modifier.width(8.dp))
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
                    if (statusWa == "WORKING") "Nenhuma conversa encontrada" else "WhatsApp não conectado",
                    color = Gray500, fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(chats, key = { it.id }) { c ->
                    ChatItem(c, vm) { vm.abrirChat(c) }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

// ─── Item de conversa ─────────────────────────────────────────────────────────

@Composable
fun ChatItem(c: WaChat, vm: WhatsAppViewModel, onClick: () -> Unit) {
    val fotoMap by vm.fotoMap.collectAsState()
    val labelsMap by vm.labelsMap.collectAsState()
    val fotoUrl = fotoMap[c.id]
    val labels = labelsMap[c.id] ?: emptyList()

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        Box(Modifier.size(50.dp)) {
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
                    if (isGroup) {
                        Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    } else {
                        Text(
                            c.nomeExibir.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        )
                    }
                }
            }
            if (c.unreadCount > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                        .clip(CircleShape).background(Color(0xFF25D366)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (c.unreadCount > 99) "99+" else "${c.unreadCount}",
                        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.nomeExibir, fontWeight = if (c.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp, color = Color(0xFF1F2937),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                c.lastMessage?.let {
                    Text(fmtTs(it.timestamp), fontSize = 11.sp,
                        color = if (c.unreadCount > 0) Color(0xFF25D366) else Gray500)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (c.lastMessage?.fromMe == true) {
                    Icon(Icons.Default.DoneAll, null, tint = Color(0xFF25D366), modifier = Modifier.size(14.dp))
                }
                Text(
                    labelMensagem(c.lastMessage), fontSize = 13.sp,
                    color = if (c.unreadCount > 0) Color(0xFF1F2937) else Gray500,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
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

@Composable
fun ChatViewScreen(chat: WaChat, vm: WhatsAppViewModel) {
    val msgs         by vm.mensagens.collectAsState()
    val isLoading    by vm.isLoadingMsgs.collectAsState()
    val isSending    by vm.isSending.collectAsState()
    val statusWa     by vm.statusWa.collectAsState()
    val fotoMap      by vm.fotoMap.collectAsState()
    val labelsMap    by vm.labelsMap.collectAsState()
    val fotoUrl      = fotoMap[chat.id]
    val chatLabels   = labelsMap[chat.id] ?: emptyList()
    var texto        by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()
    var quickReply   by remember { mutableStateOf("") }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    // Agrupa mensagens por data
    val grupos = remember(msgs) {
        val result = mutableListOf<Pair<String?, WaMensagem>>() // null key = separator
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
                items(grupos, key = { if (it.first != null) "sep-${it.second.timestamp}" else it.second.id }) { (isSep, msg) ->
                    if (isSep != null) {
                        // Separador de data
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Surface(color = Color(0xFFD1F4CC), shape = RoundedCornerShape(8.dp)) {
                                Text(isSep, fontSize = 11.sp, color = Color(0xFF4A4A4A),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    } else {
                        WaBubble(msg)
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF128C7E).copy(.4f)),
                    ) { Text(label, fontSize = 11.sp) }
                }
            }
        }

        // Input
        Surface(shadowElevation = 2.dp, color = Color(0xFFF0F2F5)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Mensagem", color = Gray500) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5,
                    enabled = !isSending && statusWa == "WORKING",
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                    ),
                )
                FloatingActionButton(
                    onClick = {
                        val t = texto.trim()
                        if (t.isNotEmpty() && statusWa == "WORKING") {
                            vm.enviar(t)
                            texto = ""
                        }
                    },
                    containerColor = Color(0xFF25D366),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (isSending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, null, tint = Color.White)
                }
            }
        }
    }
}

// ─── Bolha de mensagem ────────────────────────────────────────────────────────

@Composable
fun WaBubble(msg: WaMensagem) {
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
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(fmtTs(msg.timestamp), fontSize = 10.sp, color = Gray500)
                    if (msg.fromMe) {
                        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF34B7F1), modifier = Modifier.size(13.dp))
                    }
                }
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
        val now = System.currentTimeMillis()
        val diff = now - millis
        val sdf = if (diff < 86400_000L) SimpleDateFormat("HH:mm", Locale.getDefault())
                  else SimpleDateFormat("dd/MM", Locale.getDefault())
        sdf.format(Date(millis))
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

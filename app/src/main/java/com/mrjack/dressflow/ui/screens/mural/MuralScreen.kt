package com.mrjack.dressflow.ui.screens.mural

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.api.PrefsKeys
import com.mrjack.dressflow.data.api.dataStore
import com.mrjack.dressflow.data.model.ConviteSecreto
import com.mrjack.dressflow.data.model.MuralCanal
import com.mrjack.dressflow.data.model.MuralMensagem
import com.mrjack.dressflow.data.model.Vendedor
import com.mrjack.dressflow.ui.theme.*
import io.socket.client.IO
import io.socket.client.Socket as IoSocket
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val GATEWAY_URL_MURAL = "https://optimistic-peace-production-0a23.up.railway.app"

class MuralViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)
    val canais = MutableStateFlow<List<MuralCanal>>(emptyList())
    val canalAtivo = MutableStateFlow<MuralCanal?>(null)
    val mensagens = MutableStateFlow<List<MuralMensagem>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val isSending = MutableStateFlow(false)
    var meId = 0

    val totalNaoLidas = MutableStateFlow(0)
    val conviteSecreto = MutableStateFlow<ConviteSecreto?>(null) // notificação global

    private var ultimaMsgIdAviso: Int? = null
    private val ctx = app.applicationContext
    private var socketSecreto: IoSocket? = null

    init {
        viewModelScope.launch {
            meId = app.dataStore.data.first().let {
                Gson().fromJson(it[PrefsKeys.USER_JSON], com.mrjack.dressflow.data.model.UsuarioLogado::class.java)?.id ?: 0
            }
            carregarCanais()
            iniciarPollingNaoLidas()
            conectarSocketSecreto()
        }
    }

    private fun iniciarPollingNaoLidas() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val lista = withContext(Dispatchers.IO) { api.listarCanais().body() ?: emptyList() }
                    val total = lista.sumOf { it.count?.mensagens ?: 0 }
                    totalNaoLidas.value = total
                    canais.value = lista

                    // Verifica canal de Avisos em paralelo com a atualização de badges
                    val avisos = lista.find { it.nome.contains("Avisos", ignoreCase = true) }
                    if (avisos != null && (avisos.count?.mensagens ?: 0) > 0) {
                        val msgs = withContext(Dispatchers.IO) { api.listarMensagens(avisos.id).body() }
                        val ultimaId = msgs?.lastOrNull()?.id
                        if (ultimaId != null && ultimaId != ultimaMsgIdAviso) {
                            if (ultimaMsgIdAviso != null) tocarAviso()
                            ultimaMsgIdAviso = ultimaId
                        }
                    }
                } catch (_: Exception) {}
                delay(30_000L)
            }
        }
    }

    private fun tocarAviso() {
        try {
            val mp = MediaPlayer()
            mp.setDataSource("https://mrjack.dressflow.com.br/aviso.mp3?v=3")
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { it.release() }
            mp.prepareAsync()
        } catch (_: Exception) {}
    }

    fun carregarCanais() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                canais.value = api.listarCanais().body() ?: emptyList()
                if (canalAtivo.value == null) canalAtivo.value = canais.value.firstOrNull()
                canalAtivo.value?.let { carregarMensagens(it.id) }
            } catch (e: Exception) { /* silencia */ }
            finally { isLoading.value = false }
        }
    }

    fun abrirCanal(canal: MuralCanal) {
        canalAtivo.value = canal
        viewModelScope.launch { carregarMensagens(canal.id) }
    }

    private suspend fun carregarMensagens(canalId: Int) {
        try {
            mensagens.value = api.listarMensagens(canalId).body() ?: emptyList()
            api.marcarLido(canalId)
            // Remove badge do canal
            canais.value = canais.value.map {
                if (it.id == canalId) it.copy(count = null) else it
            }
        } catch (e: Exception) { /* silencia */ }
    }

    fun enviar(texto: String) {
        val canal = canalAtivo.value ?: return
        viewModelScope.launch {
            isSending.value = true
            try {
                val msg = api.enviarMensagem(canal.id, mapOf("conteudo" to texto)).body()
                if (msg != null) mensagens.value = mensagens.value + msg
            } catch (e: Exception) { /* silencia */ }
            finally { isSending.value = false }
        }
    }

    val usuarios = MutableStateFlow<List<Vendedor>>(emptyList())
    val isLoadingUsuarios = MutableStateFlow(false)

    fun carregarUsuarios() {
        viewModelScope.launch {
            isLoadingUsuarios.value = true
            try { usuarios.value = api.listarUsuariosMural().body() ?: emptyList() } catch (_: Exception) {}
            finally { isLoadingUsuarios.value = false }
        }
    }

    fun criarDm(outroId: Int) {
        viewModelScope.launch {
            try {
                val canal = api.criarDm(outroId).body() ?: return@launch
                carregarCanais()
                abrirCanal(canal)
            } catch (_: Exception) {}
        }
    }

    // ── Chat Secreto ─────────────────────────────────────────────────────────────

    fun convidarSecreto(paraId: Int) {
        viewModelScope.launch {
            try { api.convidarSecreto(mapOf("paraId" to paraId)) } catch (_: Exception) {}
        }
    }

    fun aceitarSecreto(canalId: Int) {
        viewModelScope.launch {
            try {
                api.aceitarSecreto(canalId)
                carregarCanais()
                val canal = canais.value.find { it.id == canalId }
                if (canal != null) abrirCanal(canal)
                conviteSecreto.value = null
            } catch (_: Exception) { conviteSecreto.value = null }
        }
    }

    fun recusarSecreto(canalId: Int) {
        viewModelScope.launch {
            try { api.recusarSecreto(canalId) } catch (_: Exception) {}
            conviteSecreto.value = null
        }
    }

    fun encerrarSecreto(canalId: Int) {
        viewModelScope.launch {
            try { api.encerrarSecreto(canalId) } catch (_: Exception) {}
            canais.value = canais.value.filter { it.id != canalId }
            if (canalAtivo.value?.id == canalId) { canalAtivo.value = null; mensagens.value = emptyList() }
        }
    }

    private fun conectarSocketSecreto() {
        viewModelScope.launch {
            try {
                val opts = IO.Options().apply { transports = arrayOf("websocket", "polling") }
                val s = IO.socket(GATEWAY_URL_MURAL, opts)
                s.on(IoSocket.EVENT_CONNECT) {
                    if (meId > 0) s.emit("user:join", meId)
                }
                s.on("secreto:convite") { args ->
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val conv = ConviteSecreto(
                        canalId = data.optInt("canalId"),
                        deId = data.optInt("deId"),
                        deNome = data.optString("deNome", "Alguém"),
                    )
                    viewModelScope.launch { conviteSecreto.value = conv }
                }
                s.on("secreto:encerrado") { args ->
                    val data = args.firstOrNull() as? JSONObject ?: return@on
                    val canalId = data.optInt("canalId")
                    viewModelScope.launch {
                        canais.value = canais.value.filter { it.id != canalId }
                        if (canalAtivo.value?.id == canalId) { canalAtivo.value = null; mensagens.value = emptyList() }
                    }
                }
                s.connect()
                socketSecreto = s
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { socketSecreto?.off(); socketSecreto?.disconnect() } catch (_: Exception) {}
    }
}

@Composable
fun MuralScreen(vm: MuralViewModel = viewModel()) {
    val canais by vm.canais.collectAsState()
    val canalAtivo by vm.canalAtivo.collectAsState()
    val mensagens by vm.mensagens.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val usuarios by vm.usuarios.collectAsState()
    val isLoadingUsuarios by vm.isLoadingUsuarios.collectAsState()
    val conviteSecreto by vm.conviteSecreto.collectAsState()
    var showDmDialog by remember { mutableStateOf(false) }

    // ── Notificação global de convite secreto ─────────────────────────────────
    val conv = conviteSecreto
    if (conv != null) {
        AlertDialog(
            onDismissRequest = { vm.conviteSecreto.value = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💣", fontSize = 24.sp)
                    Text("Convite de chat secreto", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("${conv.deNome} te convidou para um chat onde as mensagens se apagam automaticamente em 1 minuto.")
            },
            confirmButton = {
                Button(onClick = { vm.aceitarSecreto(conv.canalId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))) {
                    Text("🔥 Aceitar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.recusarSecreto(conv.canalId) }) { Text("Recusar") }
            },
        )
    }

    if (showDmDialog) {
        LaunchedEffect(Unit) { vm.carregarUsuarios() }
        AlertDialog(
            onDismissRequest = { showDmDialog = false },
            title = { Text("Nova mensagem direta", fontWeight = FontWeight.SemiBold) },
            text = {
                if (isLoadingUsuarios) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(usuarios) { u ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    vm.criarDm(u.id)
                                    showDmDialog = false
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(16.dp), color = Blue600.copy(.2f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(u.nome.firstOrNull()?.uppercase() ?: "?", color = Blue600, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(u.nome, fontSize = 14.sp, color = Color(0xFF111827))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDmDialog = false }) { Text("Fechar") } },
        )
    }
    val isSending by vm.isSending.collectAsState()
    var texto by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(mensagens.size) {
        if (mensagens.isNotEmpty()) listState.animateScrollToItem(mensagens.size - 1)
    }

    Row(Modifier.fillMaxSize()) {
        // Sidebar canais
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF1F2937)),
        ) {
            Text(
                "Mural",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                val publicos = canais.filter { it.tipo == "PUBLICO" }
                val privados = canais.filter { it.tipo == "PRIVADO" }
                val dms = canais.filter { it.tipo == "DIRETO" }
                val secretos = canais.filter { it.tipo == "SECRETO" && it.conviteStatus == "ATIVO" }

                if (publicos.isNotEmpty()) {
                    item {
                        Text("CANAIS", fontSize = 10.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    items(publicos) { c -> CanalItem(c, c.id == canalAtivo?.id) { vm.abrirCanal(c) } }
                }
                if (privados.isNotEmpty()) {
                    item {
                        Text("PRIVADOS", fontSize = 10.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp, ))
                    }
                    items(privados) { c -> CanalItem(c, c.id == canalAtivo?.id) { vm.abrirCanal(c) } }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("MENSAGENS", fontSize = 10.sp, color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Nova DM",
                            tint = Color.White.copy(0.7f),
                            modifier = Modifier.size(16.dp).clickable { showDmDialog = true },
                        )
                    }
                }
                items(dms) { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CanalItem(c, c.id == canalAtivo?.id) { vm.abrirCanal(c) } }
                        // Botão 💣 para convidar para chat secreto
                        val outroId = c.membros?.firstOrNull { it.usuarioId != vm.meId }?.usuarioId
                        if (outroId != null) {
                            Text("💣", fontSize = 16.sp, modifier = Modifier
                                .clickable { vm.convidarSecreto(outroId) }
                                .padding(horizontal = 8.dp))
                        }
                    }
                }
                if (secretos.isNotEmpty()) {
                    item {
                        Text("💣 SECRETO", fontSize = 10.sp, color = Color(0xFFEA580C).copy(0.8f), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    items(secretos) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { CanalItem(c, c.id == canalAtivo?.id) { vm.abrirCanal(c) } }
                            Text("🗑️", fontSize = 14.sp, modifier = Modifier
                                .clickable { vm.encerrarSecreto(c.id) }
                                .padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }

        // Área de chat
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Header
            Surface(shadowElevation = 1.dp, color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(canalAtivo?.icone ?: "#", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(canalAtivo?.nome ?: "Selecione um canal", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            val isSecreto = canalAtivo?.autoDestruicao == true
            val ctx = LocalContext.current
            val bgBitmap = remember(isSecreto) {
                if (isSecreto) runCatching {
                    ctx.assets.open("babychatsecreto.jpg").use { BitmapFactory.decodeStream(it) }
                }.getOrNull() else null
            }

            // Auto-remove mensagens expiradas (chat secreto)
            LaunchedEffect(isSecreto) {
                if (!isSecreto) return@LaunchedEffect
                while (true) {
                    delay(2000)
                    val agora = System.currentTimeMillis()
                    // parse ISO8601 e remove expiradas
                    // (simplificado — confia no backend para deletar)
                }
            }

            if (isLoading) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue600)
                }
            } else {
                Box(Modifier.weight(1f)) {
                    // Background secreto
                    if (bgBitmap != null) {
                        Image(bitmap = bgBitmap.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(mensagens, key = { it.id }) { msg ->
                            MensagemBubble(msg, isMe = msg.autorId == vm.meId, secreto = isSecreto)
                        }
                    }
                }
            }

            // Input
            Surface(shadowElevation = 2.dp, color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        placeholder = { Text("Mensagem...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = canalAtivo != null && !isSending,
                    )
                    FloatingActionButton(
                        onClick = {
                            if (texto.isNotBlank()) { vm.enviar(texto.trim()); texto = "" }
                        },
                        containerColor = Blue600,
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanalItem(canal: MuralCanal, ativo: Boolean, onClick: () -> Unit) {
    val naoLidas = canal.count?.mensagens ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (ativo) Color.White.copy(0.15f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(canal.icone ?: "#", fontSize = 14.sp, color = Color.White.copy(0.7f))
        Text(
            canal.nome,
            color = if (ativo) Color.White else Color.White.copy(0.7f),
            fontSize = 13.sp,
            fontWeight = if (ativo || naoLidas > 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (naoLidas > 0) {
            Badge(containerColor = Red500) {
                Text("$naoLidas", color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun MensagemBubble(msg: MuralMensagem, isMe: Boolean, secreto: Boolean = false) {
    // Contagem regressiva para autodestruição
    var segundos by remember(msg.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(msg.deletaEm) {
        if (msg.deletaEm == null) return@LaunchedEffect
        while (true) {
            val diff = try {
                val t = ZonedDateTime.parse(msg.deletaEm).toInstant().toEpochMilli()
                ((t - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            } catch (_: Exception) { 0 }
            segundos = diff
            if (diff <= 0) break
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (!isMe) {
            Text(msg.autorNome, fontSize = 11.sp, color = if (secreto) Color.White.copy(0.8f) else Gray500,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp,
            ),
            color = if (isMe) Color(0xFF1A1A2E) else Color.Black.copy(0.7f),
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    msg.conteudo ?: "(arquivo)",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                if (segundos != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💣", fontSize = 10.sp)
                        Text("${segundos}s", fontSize = 10.sp,
                            color = if ((segundos ?: 60) <= 10) Color(0xFFEF4444) else Color(0xFFFB923C),
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(fmtHora(msg.createdAt), fontSize = 10.sp,
            color = if (secreto) Color.White.copy(0.7f) else Gray500,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp))
    }
}

fun fmtHora(ts: String): String = try {
    val zdt = ZonedDateTime.parse(ts)
    zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
} catch (e: Exception) { ts.take(5) }

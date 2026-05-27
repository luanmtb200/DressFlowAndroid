package com.mrjack.dressflow.ui.screens.padronizacoes

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mrjack.dressflow.BuildConfig
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.api.PrefsKeys
import com.mrjack.dressflow.data.api.dataStore
import com.mrjack.dressflow.data.model.*
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.theme.*
import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

// ─── Constantes ───────────────────────────────────────────────────────────────

val TIPOS_PAD = listOf(
    "CASAMENTO"      to "Casamento",
    "MEDICINA_MISSA" to "Formatura · Medicina — Missa",
    "MEDICINA_BAILE" to "Formatura · Medicina — Baile",
    "DIREITO_MISSA"  to "Formatura · Direito — Missa",
    "DIREITO_BAILE"  to "Formatura · Direito — Baile",
    "FORMATURA"      to "Formatura (genérica)",
    "OUTRO"          to "Outro",
)

val TIPOS_FORMATURA = setOf("MEDICINA_MISSA", "MEDICINA_BAILE", "DIREITO_MISSA", "DIREITO_BAILE", "FORMATURA")

val TIPOS_CLIENTE_LABEL = mapOf(
    "NOIVO" to "Noivo", "NOIVA" to "Noiva", "PADRINHO" to "Padrinho", "MADRINHA" to "Madrinha",
    "PAGEM" to "Pagem", "FORMANDO" to "Formando", "FORMANDA" to "Formanda",
    "PAI_FORMANDO" to "Pai do Formando", "MAE_FORMANDA" to "Mãe da Formanda",
    "DEBUTANTE" to "Debutante", "OUTROS" to "Outros",
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class PadronizacoesViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val lista            = MutableStateFlow<List<Padronizacao>>(emptyList())
    val detalhe          = MutableStateFlow<Padronizacao?>(null)
    val trajesPad        = MutableStateFlow<List<TrajePadronizacao>>(emptyList())
    val vendedores       = MutableStateFlow<List<Vendedor>>(emptyList())
    val currentVendedorId = MutableStateFlow<Int?>(null)
    val isLoading        = MutableStateFlow(false)
    val isLoadingDetalhe = MutableStateFlow(false)
    val isSaving         = MutableStateFlow(false)
    val isSavingTrajePad = MutableStateFlow(false)
    val criando          = MutableStateFlow(false)
    val editando         = MutableStateFlow<Padronizacao?>(null)
    val erro             = MutableStateFlow<String?>(null)
    val sucesso          = MutableStateFlow<String?>(null)
    val isDownloadingPdf = MutableStateFlow(false)
    val pdfErro          = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    init {
        carregar()
        carregarTrajesPad()
        viewModelScope.launch {
            try { vendedores.value = api.listarVendedores().body() ?: emptyList() } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                app.dataStore.data.collect { prefs ->
                    val json = prefs[PrefsKeys.USER_JSON]
                    if (json != null) {
                        val u = com.google.gson.Gson().fromJson(json, UsuarioLogado::class.java)
                        currentVendedorId.value = u?.vendedorId
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun carregar(busca: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!busca.isNullOrBlank()) delay(350)
            isLoading.value = true
            erro.value = null
            try {
                lista.value = api.listarPadronizacoes(busca = busca.takeIf { !it.isNullOrBlank() }).body() ?: emptyList()
            } catch (e: Exception) {
                erro.value = "Erro: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun abrirDetalhe(p: Padronizacao) {
        viewModelScope.launch {
            isLoadingDetalhe.value = true
            detalhe.value = try { api.buscarPadronizacao(p.id).body() ?: p } catch (_: Exception) { p }
            isLoadingDetalhe.value = false
        }
    }

    fun fecharDetalhe() { detalhe.value = null }

    fun criarPadronizacao(body: Map<String, Any?>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val resp = api.criarPadronizacao(body)
                if (resp.isSuccessful) {
                    sucesso.value = "Padronização criada!"
                    criando.value = false
                    carregar()
                    onSuccess()
                } else {
                    erro.value = "Erro ${resp.code()}"
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    fun atualizarPadronizacaoCompleta(id: Int, body: Map<String, Any?>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val resp = api.atualizarPadronizacao(id, body)
                if (resp.isSuccessful) {
                    sucesso.value = "Padronização atualizada!"
                    editando.value = null
                    carregar()
                    onSuccess()
                } else {
                    erro.value = "Erro ${resp.code()}"
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    fun incrementarPadrinhos(padId: Int, delta: Int) {
        viewModelScope.launch {
            try {
                val atual = detalhe.value?.padrinhosVieram ?: 0
                val novo = (atual + delta).coerceAtLeast(0)
                api.atualizarPadronizacao(padId, mapOf("padrinhosVieram" to novo))
                detalhe.value = api.buscarPadronizacao(padId).body() ?: detalhe.value
                carregar()
            } catch (e: Exception) { erro.value = e.message }
        }
    }

    fun baixarPdf(padId: Int, context: Context, onUri: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            isDownloadingPdf.value = true
            pdfErro.value = null
            try {
                val resp = api.baixarPdfPadronizacao(padId)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body == null) { pdfErro.value = "PDF vazio"; onUri(null); return@launch }
                    val uri = withContext(Dispatchers.IO) {
                        val file = File(context.cacheDir, "pad_$padId.pdf")
                        body.byteStream().use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    }
                    onUri(uri)
                } else {
                    pdfErro.value = "Erro ${resp.code()}"
                    onUri(null)
                }
            } catch (e: Exception) {
                pdfErro.value = "Erro: ${e.message}"
                onUri(null)
            } finally {
                isDownloadingPdf.value = false
            }
        }
    }

    // ── TrajePadronizacao CRUD ─────────────────────────────────────────────────

    fun carregarTrajesPad() {
        viewModelScope.launch {
            try { trajesPad.value = api.listarTrajesPadronizacao().body() ?: emptyList() } catch (_: Exception) {}
        }
    }

    fun criarTrajePad(nome: String, descricao: String, valor: String, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingTrajePad.value = true
            try {
                val body = mutableMapOf<String, Any?>("nome" to nome)
                if (descricao.isNotBlank()) body["descricao"] = descricao
                valor.replace(",", ".").toDoubleOrNull()?.let { body["valor"] = it }
                val resp = api.criarTrajePadronizacao(body)
                if (resp.isSuccessful) {
                    carregarTrajesPad()
                    onDone()
                } else {
                    erro.value = "Erro ${resp.code()}"
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSavingTrajePad.value = false }
        }
    }

    fun atualizarTrajePad(id: Int, nome: String, descricao: String, valor: String, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingTrajePad.value = true
            try {
                val body = mutableMapOf<String, Any?>("nome" to nome)
                body["descricao"] = descricao.ifBlank { null }
                body["valor"] = valor.replace(",", ".").toDoubleOrNull()
                val resp = api.atualizarTrajePadronizacao(id, body)
                if (resp.isSuccessful) {
                    carregarTrajesPad()
                    onDone()
                } else {
                    erro.value = "Erro ${resp.code()}"
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSavingTrajePad.value = false }
        }
    }

    fun deletarTrajePad(id: Int) {
        viewModelScope.launch {
            try {
                api.deletarTrajePadronizacao(id)
                carregarTrajesPad()
            } catch (e: Exception) { erro.value = e.message }
        }
    }
}

// ─── Tela Principal ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadronizacoesScreen(vm: PadronizacoesViewModel = viewModel()) {
    val lista        by vm.lista.collectAsState()
    val detalhe      by vm.detalhe.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val erro         by vm.erro.collectAsState()
    val criando      by vm.criando.collectAsState()
    val editando     by vm.editando.collectAsState()
    val sucesso      by vm.sucesso.collectAsState()
    val isLoadingDet by vm.isLoadingDetalhe.collectAsState()
    val isDownloadingPdf by vm.isDownloadingPdf.collectAsState()
    var busca        by remember { mutableStateOf("") }
    var tabIdx       by remember { mutableStateOf(0) }

    LaunchedEffect(sucesso) {
        if (sucesso != null) { kotlinx.coroutines.delay(2000); vm.sucesso.value = null }
    }

    if (criando) {
        NovaPadronizacaoScreen(vm, padronizacao = null) { vm.criando.value = false }
        return
    }

    if (editando != null) {
        NovaPadronizacaoScreen(vm, padronizacao = editando) { vm.editando.value = null }
        return
    }

    if (detalhe != null) {
        PadronizacaoDetalheScreen(
            p = detalhe!!,
            isLoading = isLoadingDet,
            isDownloadingPdf = isDownloadingPdf,
            onBack = { vm.fecharDetalhe() },
            onIncrementar = { delta -> vm.incrementarPadrinhos(detalhe!!.id, delta) },
            onBaixarPdf = { context, callback -> vm.baixarPdf(detalhe!!.id, context, callback) },
            onEditar = { vm.editando.value = detalhe },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(shadowElevation = 1.dp, color = Color.White) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Padronizações", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900, modifier = Modifier.weight(1f))
                        if (tabIdx == 0) {
                            Button(
                                onClick = { vm.criando.value = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nova")
                            }
                        }
                    }
                    TabRow(selectedTabIndex = tabIdx, containerColor = Color.White, contentColor = Blue600) {
                        listOf("Padronizações", "Trajes", "Índice").forEachIndexed { i, label ->
                            Tab(
                                selected = tabIdx == i,
                                onClick = { tabIdx = i },
                                text = { Text(label, fontSize = 13.sp) },
                            )
                        }
                    }
                }
            }

            if (erro != null) {
                Text(erro!!, color = Red500, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().background(Red100).padding(12.dp))
            }

            when (tabIdx) {
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = busca,
                            onValueChange = { busca = it; vm.carregar(it) },
                            placeholder = { Text("Buscar evento...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                        )
                        if (isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Blue600)
                            }
                        } else if (lista.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Nenhuma padronização encontrada", color = Gray500)
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(lista, key = { it.id }) { p ->
                                    EventoCard(p) { vm.abrirDetalhe(p) }
                                }
                            }
                        }
                    }
                }
                1 -> TrajesPadronizacaoTab(vm)
                2 -> IndiceTrajesTab(lista)
            }
        }

        sucesso?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Color(0xFF16A34A),
            ) { Text(it, color = Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}

// ─── Card do evento ───────────────────────────────────────────────────────────

@Composable
fun EventoCard(p: Padronizacao, onClick: () -> Unit) {
    val tipo = p.tipo ?: p.tipoEvento ?: "OUTRO"
    val ativo = p.ativo != false
    val clientes = p.clientes ?: emptyList()
    val locacoes = p.locacoes ?: emptyList()
    val isFormatura = tipo in TIPOS_FORMATURA
    val vieramCount = p.padrinhosVieram ?: clientes.filter { it.status != "CANCELADO" }.size
    val faltam = (p.numeroPadrinhos ?: p.totalPadrinhos)?.let { total -> maxOf(0, total - vieramCount) }
    var aberto by remember { mutableStateOf(false) }

    val tipoBadgeColor = when (tipo) {
        "CASAMENTO"      -> Color(0xFFEC4899) to Color(0xFFFCE7F3)
        "MEDICINA_MISSA" -> Color(0xFF1D4ED8) to Color(0xFFDBEAFE)
        "MEDICINA_BAILE" -> Color(0xFF475569) to Color(0xFFF1F5F9)
        "DIREITO_MISSA"  -> Color(0xFF92400E) to Color(0xFFFEF3C7)
        "DIREITO_BAILE"  -> Color(0xFFB45309) to Color(0xFFFDF3DC)
        "FORMATURA"      -> Color(0xFF1D4ED8) to Color(0xFFDBEAFE)
        else             -> Color(0xFF374151) to Color(0xFFF3F4F6)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (ativo) Color.White else Color(0xFFF9FAFB)),
        elevation = CardDefaults.cardElevation(1.dp),
        border = if (ativo) null else BorderStroke(1.dp, Gray100),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = tipoBadgeColor.second, shape = RoundedCornerShape(20.dp)) {
                    Text(
                        TIPOS_PAD.find { it.first == tipo }?.second ?: tipo,
                        color = tipoBadgeColor.first, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (!ativo) {
                    Surface(color = Gray100, shape = RoundedCornerShape(20.dp)) {
                        Text("Inativa", color = Gray500, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Text(fmtData(p.dataEvento), fontSize = 11.sp, color = Gray500)
            }
            Spacer(Modifier.height(6.dp))
            Text(p.nomeEvento ?: "Padronização #${p.id}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gray900)
            if (!p.nomeNoivos.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${if (isFormatura) "Turma" else "Noivos"}: ${p.nomeNoivos}${if (!p.telefoneNoivos.isNullOrBlank()) " · ${p.telefoneNoivos}" else ""}",
                    fontSize = 12.sp, color = Color(0xFF4B5563),
                )
            }
            if (!p.cerimonialNome.isNullOrBlank()) {
                Text("Cerimonial: ${p.cerimonialNome}${if (!p.cerimonialTelefone.isNullOrBlank()) " (${p.cerimonialTelefone})" else ""}", fontSize = 11.sp, color = Gray500)
            }
            if (p.consultor != null) {
                Text("Consultor: ${p.consultor.nome}", fontSize = 11.sp, color = Indigo600, fontWeight = FontWeight.Medium)
            } else if (p.vendedor != null) {
                Text("Consultor: ${p.vendedor.nome}", fontSize = 11.sp, color = Indigo600, fontWeight = FontWeight.Medium)
            }
            val trajeInfo = buildList {
                p.trajePadrinhos?.let { add(Triple(it.nome, p.valorTrajePadrinhos, if (isFormatura) "Formandos" else "Padrinhos")) }
                p.trajePais?.let { add(Triple(it.nome, p.valorTrajePais, if (isFormatura) "Premium" else "Pais")) }
            }
            if (trajeInfo.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    trajeInfo.forEach { (nome, valor, label) ->
                        Surface(color = Blue50, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "$label: $nome${if (!valor.isNullOrBlank()) " — R$ $valor" else ""}",
                                fontSize = 11.sp, color = Blue700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            if (!p.trajeCompletoPagem.isNullOrBlank()) {
                Surface(color = Green100, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Pajens: ${p.trajeCompletoPagem}${if (!p.valorTrajePagem.isNullOrBlank()) " — R$ ${p.valorTrajePagem}" else ""}",
                        fontSize = 11.sp, color = Green600,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (!p.corVestidoMadrinhas.isNullOrBlank()) {
                Text("Cor madrinhas: ${p.corVestidoMadrinhas}", fontSize = 11.sp, color = Gray500)
            }
            Spacer(Modifier.height(8.dp))
            val totalPad = p.numeroPadrinhos ?: p.totalPadrinhos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Green100, shape = RoundedCornerShape(20.dp)) {
                    Text("$vieramCount vieram", color = Green600, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (faltam != null) {
                    Surface(color = Amber100, shape = RoundedCornerShape(20.dp)) {
                        Text("$faltam faltam", color = Amber500, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (totalPad != null) Text("$totalPad total", fontSize = 11.sp, color = Gray500)
                Spacer(Modifier.weight(1f))
                if (clientes.isNotEmpty() || locacoes.isNotEmpty()) {
                    TextButton(
                        onClick = { aberto = !aberto },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(if (aberto) "Ocultar" else "Ver clientes", fontSize = 11.sp, color = Blue600)
                    }
                }
            }
            if (aberto) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Gray100)
                Spacer(Modifier.height(6.dp))
                Text("CLIENTES VINCULADOS", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val clientesList: List<Any> = if (clientes.isNotEmpty()) clientes else locacoes
                if (clientesList.isEmpty()) {
                    Text("Nenhum cliente vinculado ainda.", fontSize = 12.sp, color = Gray500)
                } else {
                    clientesList.forEach { item ->
                        HorizontalDivider(color = Color(0xFFF9FAFB))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when (item) {
                                is PadClienteInfo -> {
                                    val (bg, fg) = when (item.status) {
                                        "CANCELADO" -> Red100 to Red500
                                        "CONTRATO"  -> Indigo100 to Indigo600
                                        else        -> Blue50 to Blue600
                                    }
                                    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            when (item.status) { "CANCELADO" -> "Cancelado"; "CONTRATO" -> "Contrato"; else -> "Ativo" },
                                            fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(item.nome ?: "—", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Gray900, modifier = Modifier.weight(1f))
                                    if (!item.telefone.isNullOrBlank()) Text(item.telefone, fontSize = 10.sp, color = Gray500)
                                    if (!item.tipoCliente.isNullOrBlank()) {
                                        Surface(color = Gray100, shape = RoundedCornerShape(4.dp)) {
                                            Text(TIPOS_CLIENTE_LABEL[item.tipoCliente] ?: item.tipoCliente,
                                                fontSize = 10.sp, color = Color(0xFF374151),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(item.traje ?: "—", fontSize = 10.sp, color = Gray500)
                                }
                                is PadronizacaoLocacao -> {
                                    val loc = item.locacao
                                    val nome = item.nome ?: loc?.cliente?.nome ?: "—"
                                    Text(nome, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Gray900, modifier = Modifier.weight(1f))
                                    Text(loc?.traje ?: "—", fontSize = 10.sp, color = Gray500)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Detalhe ──────────────────────────────────────────────────────────────────

@Composable
fun PadronizacaoDetalheScreen(
    p: Padronizacao,
    isLoading: Boolean,
    isDownloadingPdf: Boolean = false,
    onBack: () -> Unit,
    onIncrementar: (Int) -> Unit,
    onBaixarPdf: (Context, (android.net.Uri?) -> Unit) -> Unit = { _, _ -> },
    onEditar: () -> Unit = {},
) {
    val tipo = p.tipo ?: p.tipoEvento ?: "OUTRO"
    val isFormatura = tipo in TIPOS_FORMATURA
    val context = LocalContext.current
    val publicBase = "https://${BuildConfig.LOJA_SLUG}.dressflow.com.br"

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Voltar") }
                Text(p.nomeEvento ?: "Padronização #${p.id}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Gray900, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onEditar,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 13.sp)
                }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = {
                        onBaixarPdf(context) { uri ->
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try { context.startActivity(Intent.createChooser(intent, "Abrir PDF")) }
                                catch (_: Exception) { }
                            }
                        }
                    },
                    enabled = !isDownloadingPdf,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    if (isDownloadingPdf) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Blue600, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val slug = p.slug.takeIf { !it.isNullOrBlank() } ?: "${p.id}"
                        val url = "${publicBase}/padronizacoes/public/$slug"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link", fontSize = 13.sp)
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            InfoPadItem("Tipo", TIPOS_PAD.find { it.first == tipo }?.second ?: tipo)
                            InfoPadItem("Data evento", fmtData(p.dataEvento))
                            if (!p.tipoCapelo.isNullOrBlank()) InfoPadItem("Capelo", p.tipoCapelo)
                        }
                        if (!p.nomeNoivos.isNullOrBlank()) {
                            Row { InfoPadItem(if (isFormatura) "Turma" else "Noivos", p.nomeNoivos) }
                        }
                        if (!p.cerimonialNome.isNullOrBlank()) {
                            InfoPadItem(if (isFormatura) "Comissão" else "Cerimonial", "${p.cerimonialNome}${if (!p.cerimonialTelefone.isNullOrBlank()) " – ${p.cerimonialTelefone}" else ""}")
                        }
                        if (p.consultor != null || p.vendedor != null) {
                            InfoPadItem("Consultor", (p.consultor ?: p.vendedor)!!.nome)
                        }
                    }
                }
            }

            if (!p.observacoes.isNullOrBlank()) {
                item { Text("Obs: ${p.observacoes}", fontSize = 13.sp, color = Gray500) }
            }

            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Padrinhos presentes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Gray500)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(onClick = { onIncrementar(-1) }) { Icon(Icons.Default.Remove, null, tint = Red500) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${p.padrinhosVieram}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Blue600)
                                Text("de ${p.numeroPadrinhos ?: p.totalPadrinhos ?: "?"}", fontSize = 12.sp, color = Gray500)
                            }
                            IconButton(onClick = { onIncrementar(1) }) { Icon(Icons.Default.Add, null, tint = Green600) }
                        }
                    }
                }
            }

            val temTrajes = p.trajePadrinhos != null || p.trajePais != null || !p.trajeCompletoPagem.isNullOrBlank()
            if (temTrajes) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TRAJES", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                        p.trajePadrinhos?.let { t ->
                            Surface(color = Blue50, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Blue200)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(if (isFormatura) "Traje formandos" else "Traje padrinhos", fontSize = 10.sp, color = Blue600, fontWeight = FontWeight.SemiBold)
                                    Text(t.nome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                    if (!p.valorTrajePadrinhos.isNullOrBlank()) Text("R$ ${p.valorTrajePadrinhos}", fontSize = 11.sp, color = Blue600)
                                }
                            }
                        }
                        p.trajePais?.let { t ->
                            Surface(color = Color(0xFFF5F3FF), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFFDDD6FE))) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(if (isFormatura) "Traje premium" else "Traje pais", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.SemiBold)
                                    Text(t.nome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4C1D95))
                                    if (!p.valorTrajePais.isNullOrBlank()) Text("R$ ${p.valorTrajePais}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                                }
                            }
                        }
                        if (!p.trajeCompletoPagem.isNullOrBlank()) {
                            Surface(color = Green100, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFFBBF7D0))) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Traje pajens", fontSize = 10.sp, color = Green600, fontWeight = FontWeight.SemiBold)
                                    Text(p.trajeCompletoPagem, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
                                    if (!p.valorTrajePagem.isNullOrBlank()) Text("R$ ${p.valorTrajePagem}", fontSize = 11.sp, color = Green600)
                                }
                            }
                        }
                    }
                }
            }

            val locacoesLista = p.locacoes ?: emptyList()
            val clientesLista = p.clientes ?: emptyList()
            val totalPart = if (clientesLista.isNotEmpty()) clientesLista.size else locacoesLista.size
            item { Text("Participantes ($totalPart)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900) }

            if (isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue600) } }
            } else if (clientesLista.isNotEmpty()) {
                items(clientesLista, key = { it.locacaoId }) { c ->
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (bg, fg) = when (c.status) {
                                "CANCELADO" -> Red100 to Red500; "CONTRATO" -> Indigo100 to Indigo600; else -> Blue50 to Blue600
                            }
                            Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
                                Text(when (c.status) { "CANCELADO" -> "Cancelado"; "CONTRATO" -> "Contrato"; else -> "Ativo" },
                                    fontSize = 10.sp, color = fg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(c.nome ?: "—", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Gray900)
                                if (!c.telefone.isNullOrBlank()) Text(c.telefone, fontSize = 11.sp, color = Gray500)
                            }
                            if (!c.tipoCliente.isNullOrBlank()) {
                                Surface(color = Gray100, shape = RoundedCornerShape(4.dp)) {
                                    Text(TIPOS_CLIENTE_LABEL[c.tipoCliente] ?: c.tipoCliente, fontSize = 10.sp, color = Color(0xFF374151),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text(c.traje ?: "—", fontSize = 11.sp, color = Gray500)
                        }
                    }
                }
            } else {
                items(locacoesLista, key = { it.id }) { pl ->
                    val loc = pl.locacao
                    val nome = pl.nome ?: loc?.cliente?.nome ?: "Participante #${pl.locacaoId}"
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(nome, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Gray900)
                                if (!loc?.traje.isNullOrBlank()) Text(loc!!.traje, fontSize = 11.sp, color = Gray500)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun InfoPadItem(label: String, valor: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
        Text(valor, fontSize = 13.sp, color = Gray900, fontWeight = FontWeight.SemiBold)
    }
}

fun fmtData(dateStr: String?): String {
    if (dateStr == null) return "—"
    return try {
        val p = dateStr.substring(0, 10).split("-")
        "${p[2]}/${p[1]}/${p[0]}"
    } catch (_: Exception) { dateStr }
}

// ─── Nova / Editar Padronização ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaPadronizacaoScreen(
    vm: PadronizacoesViewModel,
    padronizacao: Padronizacao? = null,
    nomeEventoInicial: String = "",
    dataEventoInicial: String = "",
    onFechar: () -> Unit,
) {
    val isEditing  = padronizacao != null
    val padId      = padronizacao?.id
    val isSaving   by vm.isSaving.collectAsState()
    val erro       by vm.erro.collectAsState()
    val trajesPad  by vm.trajesPad.collectAsState()
    val vendedores by vm.vendedores.collectAsState()
    val currentVId by vm.currentVendedorId.collectAsState()

    var nomeEvento     by remember { mutableStateOf(nomeEventoInicial) }
    var dataEvento     by remember { mutableStateOf(dataEventoInicial) }
    var tipo           by remember { mutableStateOf("CASAMENTO") }
    var consultorId    by remember { mutableStateOf<Int?>(null) }
    var ativo          by remember { mutableStateOf(true) }
    var nomeNoivos     by remember { mutableStateOf("") }
    var telefoneNoivo  by remember { mutableStateOf("") }
    var telefoneNoiva  by remember { mutableStateOf("") }
    var dataLimite     by remember { mutableStateOf("") }
    var cerimonialNome by remember { mutableStateOf("") }
    var cerimonialTel  by remember { mutableStateOf("") }
    var numPadrinhos   by remember { mutableStateOf("") }
    var numMadrinhas   by remember { mutableStateOf("") }
    var corVestido     by remember { mutableStateOf("") }
    var trajePadIdPad  by remember { mutableStateOf<Int?>(null) }
    var trajePadIdVest by remember { mutableStateOf<Int?>(null) }
    var valorDeTrajoPad by remember { mutableStateOf("") }
    var valorTrajoPad  by remember { mutableStateOf("") }
    var padObrg        by remember { mutableStateOf("") }
    var camisaPad      by remember { mutableStateOf("") }
    var valorDeCamisaPad by remember { mutableStateOf("") }
    var valorCamisaPad by remember { mutableStateOf("") }
    var sapatoPad      by remember { mutableStateOf("") }
    var valorDeSapatoPad by remember { mutableStateOf("") }
    var valorSapatoPad by remember { mutableStateOf("") }
    var opcional3      by remember { mutableStateOf("") }
    var valorDeOpc3    by remember { mutableStateOf("") }
    var valorOpc3      by remember { mutableStateOf("") }
    var trajeNomePais  by remember { mutableStateOf("") }
    var opcIntermPais  by remember { mutableStateOf("") }
    var valorDeIntermPais by remember { mutableStateOf("") }
    var valorIntermPais by remember { mutableStateOf("") }
    var opcPremiumPais by remember { mutableStateOf("") }
    var valorDePremiumPais by remember { mutableStateOf("") }
    var valorPremiumPais by remember { mutableStateOf("") }
    var opcColetePais  by remember { mutableStateOf("") }
    var valorDeColetePais by remember { mutableStateOf("") }
    var valorColetePais by remember { mutableStateOf("") }
    var camisaPais     by remember { mutableStateOf("") }
    var valorDeCamisaPais by remember { mutableStateOf("") }
    var valorCamisaPais by remember { mutableStateOf("") }
    var sapatoPais     by remember { mutableStateOf("") }
    var valorDeSapatoPais by remember { mutableStateOf("") }
    var valorSapatoPais by remember { mutableStateOf("") }
    var trajeCompletoPagem by remember { mutableStateOf("") }
    var valorDeTrajePagem by remember { mutableStateOf("") }
    var valorTrajePagem by remember { mutableStateOf("") }
    var tipoExpanded   by remember { mutableStateOf(false) }
    var consultorExpanded by remember { mutableStateOf(false) }
    var showGaleriaPad by remember { mutableStateOf(false) }
    var showGaleriaVest by remember { mutableStateOf(false) }

    // Pre-fill quando editando
    LaunchedEffect(padronizacao) {
        if (padronizacao != null) {
            nomeEvento    = padronizacao.nomeEvento ?: ""
            dataEvento    = padronizacao.dataEvento.take(10)
            tipo          = padronizacao.tipo ?: padronizacao.tipoEvento ?: "CASAMENTO"
            ativo         = padronizacao.ativo != false
            consultorId   = padronizacao.consultor?.id ?: padronizacao.vendedor?.id
            nomeNoivos    = padronizacao.nomeNoivos ?: ""
            telefoneNoivo = padronizacao.telefoneNoivo ?: ""
            telefoneNoiva = padronizacao.telefoneNoiva ?: ""
            cerimonialNome = padronizacao.cerimonialNome ?: ""
            cerimonialTel = padronizacao.cerimonialTelefone ?: ""
            numPadrinhos  = padronizacao.numeroPadrinhos?.toString() ?: ""
            numMadrinhas  = padronizacao.numeroMadrinhas?.toString() ?: ""
            corVestido    = padronizacao.corVestidoMadrinhas ?: ""
            trajePadIdPad = padronizacao.trajePadrinhos?.id
            trajePadIdVest = padronizacao.trajeVestido?.id
            valorTrajoPad = padronizacao.valorTrajePadrinhos ?: ""
            trajeNomePais = padronizacao.trajeNomePais ?: ""
            valorIntermPais = padronizacao.valorTrajePais ?: ""
            trajeCompletoPagem = padronizacao.trajeCompletoPagem ?: ""
            valorTrajePagem = padronizacao.valorTrajePagem ?: ""
        }
    }

    LaunchedEffect(currentVId) { if (consultorId == null && !isEditing) consultorId = currentVId }

    val isFormatura = tipo in TIPOS_FORMATURA

    fun buildBody(): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "nomeEvento" to nomeEvento,
            "dataEvento" to dataEvento,
            "tipo"       to tipo,
            "ativo"      to ativo,
        )
        consultorId?.let { m["consultorId"] = it }
        if (nomeNoivos.isNotBlank()) m["nomeNoivos"] = nomeNoivos
        if (telefoneNoivo.isNotBlank()) m["telefoneNoivo"] = telefoneNoivo
        if (telefoneNoiva.isNotBlank()) m["telefoneNoiva"] = telefoneNoiva
        if (dataLimite.isNotBlank()) m["dataLimite"] = dataLimite
        if (cerimonialNome.isNotBlank()) m["cerimonialNome"] = cerimonialNome
        if (cerimonialTel.isNotBlank()) m["cerimonialTelefone"] = cerimonialTel
        numPadrinhos.toIntOrNull()?.let { m["numeroPadrinhos"] = it }
        numMadrinhas.toIntOrNull()?.let { m["numeroMadrinhas"] = it }
        if (corVestido.isNotBlank()) m["corVestidoMadrinhas"] = corVestido
        trajePadIdPad?.let { m["trajePadIdPadrinhos"] = it }
        trajePadIdVest?.let { m["trajePadIdVestido"] = it }
        valorDeTrajoPad.replace(",",".").toDoubleOrNull()?.let { m["valorDeTrajePadrinhos"] = it }
        valorTrajoPad.replace(",",".").toDoubleOrNull()?.let { m["valorTrajePadrinhos"] = it }
        if (padObrg.isNotBlank()) m["padrinhoObrigatorio"] = padObrg
        if (camisaPad.isNotBlank()) m["camisaPadrinhos"] = camisaPad
        valorDeCamisaPad.replace(",",".").toDoubleOrNull()?.let { m["valorDeCamisaPadrinhos"] = it }
        valorCamisaPad.replace(",",".").toDoubleOrNull()?.let { m["valorCamisaPadrinhos"] = it }
        if (sapatoPad.isNotBlank()) m["sapatoPadrinhos"] = sapatoPad
        valorDeSapatoPad.replace(",",".").toDoubleOrNull()?.let { m["valorDeSapatoPadrinhos"] = it }
        valorSapatoPad.replace(",",".").toDoubleOrNull()?.let { m["valorSapatoPadrinhos"] = it }
        if (opcional3.isNotBlank()) m["opcional3"] = opcional3
        valorDeOpc3.replace(",",".").toDoubleOrNull()?.let { m["valorDeOpcional3"] = it }
        valorOpc3.replace(",",".").toDoubleOrNull()?.let { m["valorOpcional3"] = it }
        if (trajeNomePais.isNotBlank()) m["trajeNomePais"] = trajeNomePais
        if (opcIntermPais.isNotBlank()) m["opcaoIntermediariaPais"] = opcIntermPais
        valorDeIntermPais.replace(",",".").toDoubleOrNull()?.let { m["valorDeTrajePais"] = it }
        valorIntermPais.replace(",",".").toDoubleOrNull()?.let { m["valorTrajePais"] = it }
        if (opcPremiumPais.isNotBlank()) m["opcaoPremiumPais"] = opcPremiumPais
        valorDePremiumPais.replace(",",".").toDoubleOrNull()?.let { m["valorDePaletoCAlcaPais"] = it }
        valorPremiumPais.replace(",",".").toDoubleOrNull()?.let { m["valorPaletoCAlcaPais"] = it }
        if (opcColetePais.isNotBlank()) m["opcaoColetePais"] = opcColetePais
        valorDeColetePais.replace(",",".").toDoubleOrNull()?.let { m["valorDeColetePais"] = it }
        valorColetePais.replace(",",".").toDoubleOrNull()?.let { m["valorColetePais"] = it }
        if (camisaPais.isNotBlank()) m["camisaPais"] = camisaPais
        valorDeCamisaPais.replace(",",".").toDoubleOrNull()?.let { m["valorDeCamisaPais"] = it }
        valorCamisaPais.replace(",",".").toDoubleOrNull()?.let { m["valorCamisaPais"] = it }
        if (sapatoPais.isNotBlank()) m["sapatoPais"] = sapatoPais
        valorDeSapatoPais.replace(",",".").toDoubleOrNull()?.let { m["valorDeSapatoPais"] = it }
        valorSapatoPais.replace(",",".").toDoubleOrNull()?.let { m["valorSapatoPais"] = it }
        if (trajeCompletoPagem.isNotBlank()) m["trajeCompletoPagem"] = trajeCompletoPagem
        valorDeTrajePagem.replace(",",".").toDoubleOrNull()?.let { m["valorDeTrajePagem"] = it }
        valorTrajePagem.replace(",",".").toDoubleOrNull()?.let { m["valorTrajePagem"] = it }
        return m
    }

    // Dialogs de galeria
    if (showGaleriaPad) {
        TrajeGaleriaDialog(
            trajesPad = trajesPad,
            selected = trajePadIdPad,
            titulo = if (isFormatura) "Traje masculino (esquerda)" else "Traje dos padrinhos",
            onSelect = { trajePadIdPad = it; showGaleriaPad = false },
            onDismiss = { showGaleriaPad = false },
        )
    }
    if (showGaleriaVest) {
        TrajeGaleriaDialog(
            trajesPad = trajesPad,
            selected = trajePadIdVest,
            titulo = if (isFormatura) "Traje feminino (direita)" else "Vestido das madrinhas",
            onSelect = { trajePadIdVest = it; showGaleriaVest = false },
            onDismiss = { showGaleriaVest = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFechar) { Icon(Icons.Default.Close, null) }
                Text(
                    if (isEditing) "Editar padronização" else "Nova padronização",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (nomeEvento.isBlank()) { vm.erro.value = "Nome do evento obrigatório"; return@Button }
                        if (dataEvento.isBlank()) { vm.erro.value = "Data do evento obrigatória"; return@Button }
                        if (isEditing && padId != null)
                            vm.atualizarPadronizacaoCompleta(padId, buildBody()) {}
                        else
                            vm.criarPadronizacao(buildBody()) {}
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Salvar", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (erro != null) {
            Text(erro!!, color = Red500, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Red100).padding(12.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── EVENTO ─────────────────────────────────────────────────────────
            PadSectionHeader("EVENTO")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tipo", fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
                ExposedDropdownMenuBox(expanded = tipoExpanded, onExpandedChange = { tipoExpanded = it }) {
                    OutlinedTextField(
                        value = TIPOS_PAD.find { it.first == tipo }?.second ?: tipo,
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tipoExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                    )
                    ExposedDropdownMenu(expanded = tipoExpanded, onDismissRequest = { tipoExpanded = false }) {
                        TIPOS_PAD.forEach { (v, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { tipo = v; tipoExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nomeEvento, onValueChange = { nomeEvento = it },
                        label = { Text("Nome do evento *") },
                        modifier = Modifier.weight(1.5f), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    DatePickerField(
                        label = "Data do evento *", value = dataEvento,
                        onDateSelected = { dataEvento = it }, modifier = Modifier.weight(1f),
                    )
                }
                Text("Consultor responsável", fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
                ExposedDropdownMenuBox(expanded = consultorExpanded, onExpandedChange = { consultorExpanded = it }) {
                    OutlinedTextField(
                        value = vendedores.find { it.id == consultorId }?.nome ?: "Sem consultor",
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(consultorExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                    )
                    ExposedDropdownMenu(expanded = consultorExpanded, onDismissRequest = { consultorExpanded = false }) {
                        DropdownMenuItem(text = { Text("Sem consultor") }, onClick = { consultorId = null; consultorExpanded = false })
                        vendedores.filter { it.ativo }.forEach { v ->
                            DropdownMenuItem(text = { Text(v.nome) }, onClick = { consultorId = v.id; consultorExpanded = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = ativo, onCheckedChange = { ativo = it }, colors = SwitchDefaults.colors(checkedThumbColor = Blue600, checkedTrackColor = Blue100))
                    Text(if (ativo) "Padronização ativa" else "Padronização inativa", fontSize = 13.sp, color = Gray700)
                }
            }

            // ── NOIVOS / HOMENAGEADOS ──────────────────────────────────────────
            PadSectionHeader(if (isFormatura) "TURMA / HOMENAGEADOS" else "NOIVOS / HOMENAGEADOS")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nomeNoivos, onValueChange = { nomeNoivos = it },
                    label = { Text(if (isFormatura) "Nome da turma / homenageados" else "Nome dos noivos") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = telefoneNoivo, onValueChange = { telefoneNoivo = it },
                        label = { Text(if (isFormatura) "Contato principal" else "Telefone do noivo") },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    OutlinedTextField(
                        value = telefoneNoiva, onValueChange = { telefoneNoiva = it },
                        label = { Text(if (isFormatura) "Contato secundário" else "Telefone da noiva") },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                DatePickerField(
                    label = "Data limite p/ comparecer à loja",
                    value = dataLimite, onDateSelected = { dataLimite = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── CERIMONIAL / COMISSÃO ──────────────────────────────────────────
            PadSectionHeader(if (isFormatura) "COMISSÃO DE FORMATURA" else "CERIMONIAL")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cerimonialNome, onValueChange = { cerimonialNome = it },
                    label = { Text(if (isFormatura) "Nome da comissão" else "Nome da cerimonialista") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = cerimonialTel, onValueChange = { cerimonialTel = it },
                    label = { Text(if (isFormatura) "Telefone da comissão" else "Telefone") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }

            // ── PADRINHOS / MADRINHAS ──────────────────────────────────────────
            PadSectionHeader(if (isFormatura) "FORMANDOS & FORMANDAS" else "PADRINHOS & MADRINHAS")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = numPadrinhos, onValueChange = { numPadrinhos = it },
                        label = { Text(if (isFormatura) "Nº de formandos" else "Nº de padrinhos") },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = numMadrinhas, onValueChange = { numMadrinhas = it },
                        label = { Text(if (isFormatura) "Nº de formandas" else "Nº de madrinhas") },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = corVestido, onValueChange = { corVestido = it },
                    label = { Text(if (isFormatura) "Cor do traje feminino" else "Cor do vestido das madrinhas") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            // ── TRAJE PADRINHOS — Gallery Picker ───────────────────────────────
            Surface(
                color = Blue50, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Blue200), modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isFormatura) "TRAJE DOS FORMANDOS" else "TRAJE DOS PADRINHOS",
                        fontSize = 11.sp, color = Blue700, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isFormatura) "Traje masculino (foto esquerda)" else "Traje dos padrinhos (foto esquerda)",
                        fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium,
                    )
                    TrajeSelectorButton(
                        traje = trajesPad.find { it.id == trajePadIdPad },
                        placeholder = "Selecionar traje...",
                        onClick = { showGaleriaPad = true },
                        onClear = { trajePadIdPad = null },
                    )
                    Text(
                        if (isFormatura) "Traje feminino (foto direita)" else "Vestido das madrinhas (foto direita)",
                        fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium,
                    )
                    TrajeSelectorButton(
                        traje = trajesPad.find { it.id == trajePadIdVest },
                        placeholder = "Selecionar traje...",
                        onClick = { showGaleriaVest = true },
                        onClear = { trajePadIdVest = null },
                    )
                    ValorDePor(
                        label = "Valor do traje obrigatório (R$)",
                        valorDe = valorDeTrajoPad, onDe = { valorDeTrajoPad = it },
                        valorPor = valorTrajoPad, onPor = { valorTrajoPad = it },
                    )
                    OutlinedTextField(
                        value = padObrg, onValueChange = { padObrg = it },
                        label = { Text("Obrigatório (descrição)") },
                        placeholder = { Text("Ex: Paletó + Calça + Colete", fontSize = 12.sp, color = Gray500) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    OutlinedTextField(
                        value = camisaPad, onValueChange = { camisaPad = it },
                        label = { Text("Opcional 1 — Sugestão de camisa") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    )
                    ValorDePor("Valor opcional 1 (R$)", valorDeCamisaPad, { valorDeCamisaPad = it }, valorCamisaPad, { valorCamisaPad = it })
                    OutlinedTextField(
                        value = sapatoPad, onValueChange = { sapatoPad = it },
                        label = { Text("Opcional 2 — Sugestão de sapato") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    )
                    ValorDePor("Valor opcional 2 (R$)", valorDeSapatoPad, { valorDeSapatoPad = it }, valorSapatoPad, { valorSapatoPad = it })
                    OutlinedTextField(
                        value = opcional3, onValueChange = { opcional3 = it },
                        label = { Text("Opcional 3 — Sugestão compra sapato") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    )
                    ValorDePor("Valor opcional 3 (R$)", valorDeOpc3, { valorDeOpc3 = it }, valorOpc3, { valorOpc3 = it })
                }
            }

            // ── TRAJE PAIS / LINHA PREMIUM ────────────────────────────────────
            if (isFormatura) {
                Surface(
                    color = Color(0xFFF5F3FF), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TRAJE LINHA PREMIUM", fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = opcPremiumPais, onValueChange = { opcPremiumPais = it },
                            label = { Text("Descrição linha premium") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor premium (R$)", valorDePremiumPais, { valorDePremiumPais = it }, valorPremiumPais, { valorPremiumPais = it })
                        OutlinedTextField(
                            value = opcColetePais, onValueChange = { opcColetePais = it },
                            label = { Text("Com colete (descrição)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor colete (R$)", valorDeColetePais, { valorDeColetePais = it }, valorColetePais, { valorColetePais = it })
                    }
                }
                Surface(
                    color = Color(0xFFEEF2FF), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TRAJE INTERMEDIÁRIO", fontSize = 11.sp, color = Color(0xFF4338CA), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = opcIntermPais, onValueChange = { opcIntermPais = it },
                            label = { Text("Descrição traje intermediário") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor intermediário (R$)", valorDeIntermPais, { valorDeIntermPais = it }, valorIntermPais, { valorIntermPais = it })
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFF5F3FF), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TRAJE DOS PAIS DOS NOIVOS", fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = trajeNomePais, onValueChange = { trajeNomePais = it },
                            label = { Text("Nome do traje") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        OutlinedTextField(
                            value = opcIntermPais, onValueChange = { opcIntermPais = it },
                            label = { Text("Opção intermediária") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor intermediário (R$)", valorDeIntermPais, { valorDeIntermPais = it }, valorIntermPais, { valorIntermPais = it })
                        OutlinedTextField(
                            value = opcPremiumPais, onValueChange = { opcPremiumPais = it },
                            label = { Text("Opção premium") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor premium (R$)", valorDePremiumPais, { valorDePremiumPais = it }, valorPremiumPais, { valorPremiumPais = it })
                        OutlinedTextField(
                            value = opcColetePais, onValueChange = { opcColetePais = it },
                            label = { Text("Opção com colete") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                        )
                        ValorDePor("Valor colete (R$)", valorDeColetePais, { valorDeColetePais = it }, valorColetePais, { valorColetePais = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = camisaPais, onValueChange = { camisaPais = it }, label = { Text("Camisa") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp))
                            OutlinedTextField(value = sapatoPais, onValueChange = { sapatoPais = it }, label = { Text("Sapato") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp))
                        }
                        ValorDePor("Valor camisa (R$)", valorDeCamisaPais, { valorDeCamisaPais = it }, valorCamisaPais, { valorCamisaPais = it })
                        ValorDePor("Valor sapato (R$)", valorDeSapatoPais, { valorDeSapatoPais = it }, valorSapatoPais, { valorSapatoPais = it })
                    }
                }
                Surface(
                    color = Green100, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("TRAJE DOS PAJENS", fontSize = 11.sp, color = Green600, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = trajeCompletoPagem, onValueChange = { trajeCompletoPagem = it },
                            label = { Text("Traje completo do pajem") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        ValorDePor("Valor do traje (R$)", valorDeTrajePagem, { valorDeTrajePagem = it }, valorTrajePagem, { valorTrajePagem = it })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Botão seletor de traje (substitui dropdown) ──────────────────────────────

@Composable
private fun TrajeSelectorButton(
    traje: TrajePadronizacao?,
    placeholder: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (traje != null) Blue200 else Gray200),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (traje != null) {
                if (!traje.imagemUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = traje.imagemUrl, contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        Modifier.size(48.dp).background(Gray100, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Checkroom, null, tint = Gray500, modifier = Modifier.size(24.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(traje.nome, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Blue700)
                    if (!traje.valor.isNullOrBlank()) Text("R$ ${traje.valor}", fontSize = 11.sp, color = Blue600)
                }
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = Gray500, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Default.Checkroom, null, tint = Gray500, modifier = Modifier.size(22.dp))
                Text(placeholder, fontSize = 13.sp, color = Gray500, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Gray500)
            }
        }
    }
}

// ─── Dialog galeria de trajes ─────────────────────────────────────────────────

@Composable
private fun TrajeGaleriaDialog(
    trajesPad: List<TrajePadronizacao>,
    selected: Int?,
    titulo: String,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Gray900, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = Gray500)
                    }
                }
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Nenhum", color = Red500) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(trajesPad, key = { it.id }) { t ->
                        val isSel = t.id == selected
                        Card(
                            onClick = { onSelect(t.id) },
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSel) BorderStroke(2.dp, Blue600) else BorderStroke(1.dp, Gray200),
                            colors = CardDefaults.cardColors(containerColor = if (isSel) Blue50 else Color.White),
                            elevation = CardDefaults.cardElevation(if (isSel) 2.dp else 1.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!t.imagemUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = t.imagemUrl, contentDescription = t.nome,
                                        modifier = Modifier.fillMaxWidth().height(110.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth().height(110.dp).background(Gray100),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Default.Checkroom, null, tint = Gray500, modifier = Modifier.size(40.dp)) }
                                }
                                Column(Modifier.padding(8.dp)) {
                                    Text(t.nome, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Gray900, maxLines = 2)
                                    if (!t.valor.isNullOrBlank()) {
                                        Text("R$ ${t.valor}", fontSize = 10.sp, color = Blue600, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers de formulário ────────────────────────────────────────────────────

@Composable
private fun PadSectionHeader(label: String) {
    Text(label, fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
}

@Composable
private fun ValorDePor(
    label: String,
    valorDe: String, onDe: (String) -> Unit,
    valorPor: String, onPor: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("De", fontSize = 10.sp, color = Gray500)
                OutlinedTextField(
                    value = valorDe, onValueChange = onDe,
                    placeholder = { Text("0,00", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Por", fontSize = 10.sp, color = Gray500)
                OutlinedTextField(
                    value = valorPor, onValueChange = onPor,
                    placeholder = { Text("0,00", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}

// ─── Tab: Trajes de Padronização ──────────────────────────────────────────────

@Composable
fun TrajesPadronizacaoTab(vm: PadronizacoesViewModel) {
    val trajesPad by vm.trajesPad.collectAsState()
    var modalAberto by remember { mutableStateOf(false) }
    var editando    by remember { mutableStateOf<TrajePadronizacao?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { editando = null; modalAberto = true },
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Novo Traje")
            }
        }

        if (trajesPad.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nenhum traje de padronização cadastrado.", color = Gray500, fontSize = 15.sp)
                    Text("Crie o primeiro traje para usar nas padronizações.", color = Gray500, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(trajesPad, key = { it.id }) { t ->
                    TrajePadCard(
                        t = t,
                        onEdit = { editando = t; modalAberto = true },
                        onDelete = { vm.deletarTrajePad(t.id) },
                    )
                }
            }
        }
    }

    if (modalAberto) {
        TrajePadModal(
            traje = editando,
            isSaving = vm.isSavingTrajePad.collectAsState().value,
            onSalvar = { nome, desc, valor ->
                val e = editando
                if (e == null) vm.criarTrajePad(nome, desc, valor) { modalAberto = false; editando = null }
                else vm.atualizarTrajePad(e.id, nome, desc, valor) { modalAberto = false; editando = null }
            },
            onFechar = { modalAberto = false; editando = null },
        )
    }
}

@Composable
private fun TrajePadCard(t: TrajePadronizacao, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Gray200),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (!t.imagemUrl.isNullOrBlank()) {
                AsyncImage(
                    model = t.imagemUrl, contentDescription = t.nome,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier.size(96.dp).background(Gray100, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Checkroom, null, tint = Gray500, modifier = Modifier.size(36.dp))
                }
            }
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.nome, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900)
                if (!t.descricao.isNullOrBlank()) {
                    Text(t.descricao, fontSize = 12.sp, color = Gray500, maxLines = 2)
                }
                if (!t.valor.isNullOrBlank()) {
                    Text("R$ ${t.valor}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Blue700)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) { Text("Editar", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                        border = BorderStroke(1.dp, Red300),
                    ) { Text("Excluir", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun TrajePadModal(
    traje: TrajePadronizacao?,
    isSaving: Boolean,
    onSalvar: (nome: String, desc: String, valor: String) -> Unit,
    onFechar: () -> Unit,
) {
    var nome  by remember(traje) { mutableStateOf(traje?.nome ?: "") }
    var desc  by remember(traje) { mutableStateOf(traje?.descricao ?: "") }
    var valor by remember(traje) { mutableStateOf(traje?.valor ?: "") }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (traje == null) "Novo Traje de Padronização" else "Editar Traje de Padronização") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = valor, onValueChange = { valor = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nome.isNotBlank()) onSalvar(nome, desc, valor) },
                enabled = !isSaving && nome.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

// ─── Tab: Índice de Trajes ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndiceTrajesTab(lista: List<Padronizacao>) {
    val hoje = Calendar.getInstance()
    val mesOptions = remember {
        ((-3)..6).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, offset)
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val key = "%04d-%02d".format(y, m)
            val meses = listOf("", "janeiro", "fevereiro", "março", "abril", "maio", "junho",
                "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
            key to "${meses.getOrElse(m) { m.toString() }} de $y"
        }
    }
    var mesSel by remember {
        mutableStateOf("%04d-%02d".format(hoje.get(Calendar.YEAR), hoje.get(Calendar.MONTH) + 1))
    }
    var mesExpanded by remember { mutableStateOf(false) }

    fun semana(dateStr: String): Int {
        return try {
            val d = dateStr.substring(8, 10).toInt()
            when {
                d <= 7  -> 1
                d <= 14 -> 2
                d <= 21 -> 3
                d <= 28 -> 4
                else    -> 5
            }
        } catch (_: Exception) { 1 }
    }

    val doMes = remember(lista, mesSel) {
        lista.filter { p ->
            p.ativo != false && p.trajePadrinhos?.nome != null &&
            p.dataEvento.length >= 7 && p.dataEvento.substring(0, 7) == mesSel
        }
    }

    data class Linha(var total: Int = 0, var s1: Int = 0, var s2: Int = 0, var s3: Int = 0, var s4: Int = 0, var s5: Int = 0)

    val grupos = remember(doMes) {
        val map = mutableMapOf<String, Linha>()
        for (p in doMes) {
            val nome = p.trajePadrinhos!!.nome
            val qty = p.numeroPadrinhos ?: 0
            val s = semana(p.dataEvento)
            val l = map.getOrPut(nome) { Linha() }
            l.total += qty
            when (s) { 1 -> l.s1 += qty; 2 -> l.s2 += qty; 3 -> l.s3 += qty; 4 -> l.s4 += qty; 5 -> l.s5 += qty }
        }
        map.entries.sortedByDescending { it.value.total }
    }

    val totais = remember(grupos) {
        grupos.fold(Linha()) { acc, (_, l) ->
            acc.also { it.total += l.total; it.s1 += l.s1; it.s2 += l.s2; it.s3 += l.s3; it.s4 += l.s4; it.s5 += l.s5 }
        }
    }

    fun cell(n: Int) = if (n > 0) "$n" else "—"

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mês de referência:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
            ExposedDropdownMenuBox(
                expanded = mesExpanded, onExpandedChange = { mesExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = mesOptions.find { it.first == mesSel }?.second ?: mesSel,
                    onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mesExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                )
                ExposedDropdownMenu(expanded = mesExpanded, onDismissRequest = { mesExpanded = false }) {
                    mesOptions.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { mesSel = key; mesExpanded = false })
                    }
                }
            }
        }

        if (grupos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nenhuma pré-reserva para este mês.", color = Gray500, fontSize = 15.sp)
                    Text("Apenas padronizações ativas com traje e número de padrinhos são contabilizadas.", color = Gray500, fontSize = 12.sp)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Gray200),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Header
                    Row(
                        Modifier.fillMaxWidth().background(Gray50).padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Traje", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Gray700, modifier = Modifier.weight(2f))
                        Text("Total", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Blue700, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        listOf("Sem 1\n1–7", "Sem 2\n8–14", "Sem 3\n15–21", "Sem 4\n22–28", "Sem 5\n29+").forEach { col ->
                            Text(col, fontSize = 10.sp, color = Gray500, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    HorizontalDivider(color = Gray200)

                    // Rows
                    grupos.forEach { (nome, l) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(nome, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray900, modifier = Modifier.weight(2f))
                            Text("${l.total}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Blue600, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            listOf(l.s1, l.s2, l.s3, l.s4, l.s5).forEach { n ->
                                Text(cell(n), fontSize = 12.sp, color = Gray700, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }

                    // Footer total
                    HorizontalDivider(color = Gray200, thickness = 2.dp)
                    Row(
                        Modifier.fillMaxWidth().background(Blue50).padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Total geral", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Blue700, modifier = Modifier.weight(2f))
                        Text("${totais.total}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Blue600, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        listOf(totais.s1, totais.s2, totais.s3, totais.s4, totais.s5).forEach { n ->
                            Text(cell(n), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue700, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

package com.mrjack.dressflow.ui.screens.padronizacoes

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.BuildConfig
import com.mrjack.dressflow.data.api.NetworkModule
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
    val lista = MutableStateFlow<List<Padronizacao>>(emptyList())
    val detalhe = MutableStateFlow<Padronizacao?>(null)
    val isLoading = MutableStateFlow(false)
    val isLoadingDetalhe = MutableStateFlow(false)
    val isSaving = MutableStateFlow(false)
    val criando = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)
    val sucesso = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    init { carregar() }

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

    val isDownloadingPdf = MutableStateFlow(false)
    val pdfErro = MutableStateFlow<String?>(null)

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

}

// ─── Tela Principal ───────────────────────────────────────────────────────────

@Composable
fun PadronizacoesScreen(vm: PadronizacoesViewModel = viewModel()) {
    val lista by vm.lista.collectAsState()
    val detalhe by vm.detalhe.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()
    val criando by vm.criando.collectAsState()
    val sucesso by vm.sucesso.collectAsState()
    var busca by remember { mutableStateOf("") }
    val isLoadingDet by vm.isLoadingDetalhe.collectAsState()

    LaunchedEffect(sucesso) {
        if (sucesso != null) { kotlinx.coroutines.delay(2000); vm.sucesso.value = null }
    }

    if (criando) {
        NovaPadronizacaoScreen(vm) { vm.criando.value = false }
        return
    }

    val isDownloadingPdf by vm.isDownloadingPdf.collectAsState()

    if (detalhe != null) {
        PadronizacaoDetalheScreen(
            p = detalhe!!,
            isLoading = isLoadingDet,
            isDownloadingPdf = isDownloadingPdf,
            onBack = { vm.fecharDetalhe() },
            onIncrementar = { delta -> vm.incrementarPadrinhos(detalhe!!.id, delta) },
            onBaixarPdf = { context, callback -> vm.baixarPdf(detalhe!!.id, context, callback) },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 1.dp, color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Padronizações", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900, modifier = Modifier.weight(1f))
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
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it; vm.carregar(it) },
                    placeholder = { Text("Buscar evento...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
            }
        }

        if (erro != null) {
            Text(erro!!, color = Red500, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Red100).padding(12.dp))
        }

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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lista, key = { it.id }) { p ->
                    EventoCard(p) { vm.abrirDetalhe(p) }
                }
            }
        }
    } // fim Column
        sucesso?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Color(0xFF16A34A),
            ) { Text(it, color = Color.White, fontWeight = FontWeight.Medium) }
        }
    } // fim Box
}

// ─── Card do evento (idêntico ao web) ─────────────────────────────────────────

@Composable
fun EventoCard(p: Padronizacao, onClick: () -> Unit) {
    val tipo = p.tipo ?: p.tipoEvento ?: "OUTRO"
    val ativo = p.ativo != false
    val clientes = p.clientes ?: emptyList()
    val locacoes = p.locacoes ?: emptyList()
    val isFormatura = tipo in TIPOS_FORMATURA
    val vieram = clientes.filter { it.status != "CANCELADO" }
    val faltam = (p.numeroPadrinhos ?: p.totalPadrinhos)?.let { total -> maxOf(0, total - vieram.size) }
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
            // Linha 1: badges + data
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
            // Linha 2: nome do evento
            Text(p.nomeEvento ?: "Padronização #${p.id}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gray900)
            // Noivos
            if (!p.nomeNoivos.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${if (isFormatura) "Turma" else "Noivos"}: ${p.nomeNoivos}${if (!p.telefoneNoivos.isNullOrBlank()) " · ${p.telefoneNoivos}" else ""}",
                    fontSize = 12.sp, color = Color(0xFF4B5563),
                )
            }
            // Cerimonial
            if (!p.cerimonialNome.isNullOrBlank()) {
                Text("Cerimonial: ${p.cerimonialNome}${if (!p.cerimonialTelefone.isNullOrBlank()) " (${p.cerimonialTelefone})" else ""}", fontSize = 11.sp, color = Gray500)
            }
            // Consultor
            if (p.consultor != null) {
                Text("Consultor: ${p.consultor.nome}", fontSize = 11.sp, color = Indigo600, fontWeight = FontWeight.Medium)
            } else if (p.vendedor != null) {
                Text("Consultor: ${p.vendedor.nome}", fontSize = 11.sp, color = Indigo600, fontWeight = FontWeight.Medium)
            }
            // Trajes
            val trajeInfo = buildList {
                p.trajePadrinhos?.let { add(Triple(it.nome, p.valorTrajePadrinhos, if (isFormatura) "Formandos" else "Padrinhos")) }
                p.trajePais?.let { add(Triple(it.nome, p.valorTrajePais, if (isFormatura) "Premium" else "Pais")) }
            }
            if (trajeInfo.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            // Contadores
            Spacer(Modifier.height(8.dp))
            val totalPad = p.numeroPadrinhos ?: p.totalPadrinhos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Green100, shape = RoundedCornerShape(20.dp)) {
                    Text("${vieram.size} vieram", color = Green600, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (faltam != null) {
                    Surface(color = Amber100, shape = RoundedCornerShape(20.dp)) {
                        Text("$faltam faltam", color = Amber500, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (totalPad != null) {
                    Text("$totalPad total", fontSize = 11.sp, color = Gray500)
                }
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

            // Lista de clientes expandível
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
                                    if (!loc?.tamanhoPaleto.isNullOrBlank()) {
                                        Text("Pal ${loc?.tamanhoPaleto}", fontSize = 10.sp, color = Gray500)
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

// ─── Tela de Detalhe ─────────────────────────────────────────────────────────

@Composable
fun PadronizacaoDetalheScreen(
    p: Padronizacao,
    isLoading: Boolean,
    isDownloadingPdf: Boolean = false,
    onBack: () -> Unit,
    onIncrementar: (Int) -> Unit,
    onBaixarPdf: (Context, (android.net.Uri?) -> Unit) -> Unit = { _, _ -> },
) {
    val tipo = p.tipo ?: p.tipoEvento ?: "OUTRO"
    val isFormatura = tipo in TIPOS_FORMATURA
    val context = LocalContext.current
    val apiBase = BuildConfig.API_BASE_URL
    val publicBase = apiBase.replace("/api/", "")

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Voltar") }
                Text(p.nomeEvento ?: "Padronização #${p.id}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Gray900, modifier = Modifier.weight(1f))
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
                        // Usa slug se disponível, senão usa o ID como fallback
                        val slug = p.slug.takeIf { !it.isNullOrBlank() } ?: "${p.id}"
                        val url = "${publicBase}/padronizacoes/public/$slug"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver link", fontSize = 13.sp)
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Info geral
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            InfoPadItem("Tipo", TIPOS_PAD.find { it.first == tipo }?.second ?: tipo)
                            InfoPadItem("Data evento", fmtData(p.dataEvento))
                            if (!p.tipoCapelo.isNullOrBlank()) InfoPadItem("Capelo", p.tipoCapelo)
                        }
                        if (!p.nomeNoivos.isNullOrBlank()) {
                            Row {
                                InfoPadItem(if (isFormatura) "Turma" else "Noivos", p.nomeNoivos)
                            }
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

            // Contador de padrinhos
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

            // Trajes
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

            // Participantes
            val locacoesLista = p.locacoes ?: emptyList()
            val clientesLista = p.clientes ?: emptyList()
            val totalPart = if (clientesLista.isNotEmpty()) clientesLista.size else locacoesLista.size

            item {
                Text("Participantes ($totalPart)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900)
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue600)
                    }
                }
            } else if (clientesLista.isNotEmpty()) {
                items(clientesLista, key = { it.locacaoId }) { c ->
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val (bg, fg) = when (c.status) {
                                "CANCELADO" -> Red100 to Red500
                                "CONTRATO"  -> Indigo100 to Indigo600
                                else        -> Blue50 to Blue600
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
                                    Text(TIPOS_CLIENTE_LABEL[c.tipoCliente] ?: c.tipoCliente,
                                        fontSize = 10.sp, color = Color(0xFF374151),
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
                            if (!loc?.tamanhoPaleto.isNullOrBlank()) {
                                Text(buildString {
                                    loc?.tamanhoPaleto?.let { append("Pal $it ") }
                                    loc?.camisa?.let { append("Ca $it ") }
                                    loc?.calca?.let { append("Cl $it ") }
                                    loc?.sapato?.let { append("Sp $it") }
                                }.trim(), fontSize = 10.sp, color = Gray500)
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

// ─── Nova Padronização ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaPadronizacaoScreen(vm: PadronizacoesViewModel, onFechar: () -> Unit) {
    val isSaving by vm.isSaving.collectAsState()
    val erro by vm.erro.collectAsState()

    var nomeEvento     by remember { mutableStateOf("") }
    var dataEvento     by remember { mutableStateOf("") }
    var tipo           by remember { mutableStateOf("CASAMENTO") }
    var nomeNoivos     by remember { mutableStateOf("") }
    var telefoneNoivos by remember { mutableStateOf("") }
    var cerimonialNome by remember { mutableStateOf("") }
    var cerimonialTel  by remember { mutableStateOf("") }
    var numPadrinhos   by remember { mutableStateOf("") }
    var tipoExpanded   by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFechar) { Icon(Icons.Default.Close, null) }
                Text("Nova padronização", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (nomeEvento.isBlank()) { vm.erro.value = "Nome do evento obrigatório"; return@Button }
                        if (dataEvento.isBlank()) { vm.erro.value = "Data do evento obrigatória"; return@Button }
                        val body = mutableMapOf<String, Any?>(
                            "nomeEvento"         to nomeEvento,
                            "dataEvento"         to dataEvento,
                            "tipo"               to tipo,
                            "nomeNoivos"         to nomeNoivos.ifBlank { null },
                            "telefoneNoivos"     to telefoneNoivos.ifBlank { null },
                            "cerimonialNome"     to cerimonialNome.ifBlank { null },
                            "cerimonialTelefone" to cerimonialTel.ifBlank { null },
                            "numeroPadrinhos"    to numPadrinhos.toIntOrNull(),
                        )
                        vm.criarPadronizacao(body) {}
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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Tipo
            Text("TIPO DE EVENTO", fontSize = 11.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nomeEvento,
                    onValueChange = { nomeEvento = it },
                    label = { Text("Nome do evento *") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                DatePickerField(
                    label = "Data do evento *",
                    value = dataEvento,
                    onDateSelected = { dataEvento = it },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nomeNoivos,
                    onValueChange = { nomeNoivos = it },
                    label = { Text(if (tipo in TIPOS_FORMATURA) "Nome da turma" else "Nome dos noivos") },
                    modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = telefoneNoivos,
                    onValueChange = { telefoneNoivos = it },
                    label = { Text("Telefone") },
                    modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cerimonialNome,
                    onValueChange = { cerimonialNome = it },
                    label = { Text("Cerimonial / Comissão") },
                    modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = cerimonialTel,
                    onValueChange = { cerimonialTel = it },
                    label = { Text("Tel. cerimonial") },
                    modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = numPadrinhos,
                    onValueChange = { numPadrinhos = it },
                    label = { Text("Nº de padrinhos") },
                    modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

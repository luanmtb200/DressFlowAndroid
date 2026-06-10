package com.mrjack.dressflow.ui.screens.clientes

import android.app.Application
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Cliente
import com.mrjack.dressflow.data.model.Locacao
import com.mrjack.dressflow.ui.components.BrPhoneVisualTransformation
import com.mrjack.dressflow.ui.components.CpfVisualTransformation
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.components.TipoClienteSelect
import com.mrjack.dressflow.ui.components.WaBotao
import com.mrjack.dressflow.ui.screens.vendas.LocacaoFormScreen
import com.mrjack.dressflow.ui.screens.vendas.NovaLocacaoMultiTab
import com.mrjack.dressflow.ui.screens.vendas.VendasViewModel
import com.mrjack.dressflow.ui.screens.vendas.brl
import com.mrjack.dressflow.ui.screens.vendas.fmtDataCompleta
import com.mrjack.dressflow.ui.screens.vendas.fmtDataSimples
import com.mrjack.dressflow.ui.theme.*
import com.mrjack.dressflow.data.api.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ClientesViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val clientes              = MutableStateFlow<List<Cliente>>(emptyList())
    val selecionado           = MutableStateFlow<Cliente?>(null)
    val historico             = MutableStateFlow<List<Locacao>>(emptyList())
    val isLoading             = MutableStateFlow(false)
    val isLoadingHist         = MutableStateFlow(false)
    val isSaving              = MutableStateFlow(false)
    val criandoCliente        = MutableStateFlow(false)
    val mostrandoNovaLocacao  = MutableStateFlow(false)
    val erro                  = MutableStateFlow<String?>(null)
    val sucesso               = MutableStateFlow<String?>(null)
    val tabIdx                = MutableStateFlow(0)

    val devolucoes       = MutableStateFlow<List<Locacao>>(emptyList())
    val isLoadingDev     = MutableStateFlow(false)
    val isDevolvendoId   = MutableStateFlow<Int?>(null)
    val eventoParaLocacao      = MutableStateFlow("")
    val dataEventoParaLocacao  = MutableStateFlow("")
    val editandoCliente  = MutableStateFlow(false)
    private var searchJob: Job? = null

    fun buscar(q: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (q.length < 2) { clientes.value = emptyList(); return@launch }
            delay(350)
            isLoading.value = true
            clientes.value = try {
                api.listarClientes(busca = q, limit = 50).body()?.data ?: emptyList()
            } catch (_: Exception) { emptyList() }
            isLoading.value = false
        }
    }

    fun selecionar(c: Cliente) {
        selecionado.value = c
        criandoCliente.value = false
        mostrandoNovaLocacao.value = false
        viewModelScope.launch {
            isLoadingHist.value = true
            historico.value = try {
                api.locacoesPorCliente(c.id).body() ?: emptyList()
            } catch (_: Exception) { emptyList() }
            isLoadingHist.value = false
        }
    }

    fun carregarHistorico(clienteId: Int) {
        viewModelScope.launch {
            isLoadingHist.value = true
            historico.value = try {
                api.locacoesPorCliente(clienteId).body() ?: emptyList()
            } catch (_: Exception) { emptyList() }
            isLoadingHist.value = false
        }
    }

    fun carregarDevolucoes() {
        viewModelScope.launch {
            isLoadingDev.value = true
            devolucoes.value = try {
                api.devolucoesPendentes().body() ?: emptyList()
            } catch (_: Exception) { emptyList() }
            isLoadingDev.value = false
        }
    }

    fun devolver(id: Int) {
        viewModelScope.launch {
            isDevolvendoId.value = id
            try {
                val resp = api.devolver(id, mapOf("dataDevolucao" to java.time.LocalDate.now().toString()))
                if (resp.isSuccessful) {
                    sucesso.value = "Devolução registrada!"
                    carregarDevolucoes()
                } else {
                    erro.value = "Erro ao registrar devolução"
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isDevolvendoId.value = null }
        }
    }

    fun voltar()              { selecionado.value = null; historico.value = emptyList() }
    fun abrirFormCliente()    { criandoCliente.value = true; selecionado.value = null }
    fun fecharForm()          { criandoCliente.value = false }
    fun abrirNovaLocacao()    { mostrandoNovaLocacao.value = true }
    fun fecharNovaLocacao()   { mostrandoNovaLocacao.value = false }
    fun abrirEdicaoCliente()  { editandoCliente.value = true }
    fun fecharEdicaoCliente() { editandoCliente.value = false }

    fun salvarEdicaoCliente(form: ClienteForm) {
        val c = selecionado.value ?: return
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mutableMapOf<String, Any?>(
                    "nome"        to form.nome,
                    "telefone"    to form.telefone.ifBlank { null },
                    "cpf"         to form.cpf.replace(Regex("[^0-9]"), "").ifBlank { null },
                    "email"       to form.email.ifBlank { null },
                    "tipoCliente" to form.tipoCliente.ifBlank { null },
                    "cidade"      to form.cidade.ifBlank { null },
                    "observacoes" to form.observacoes.ifBlank { null },
                )
                val resp = api.atualizarCliente(c.id, body)
                if (resp.isSuccessful) {
                    resp.body()?.let { updated ->
                        selecionado.value = updated
                        sucesso.value = "Cliente atualizado!"
                    }
                    fecharEdicaoCliente()
                } else erro.value = "Erro ${resp.code()}"
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    val vendedores         = MutableStateFlow<List<com.mrjack.dressflow.data.model.Vendedor>>(emptyList())
    val currentVendedorId  = MutableStateFlow<Int?>(null)
    val userNivel          = MutableStateFlow("VENDEDOR")

    init {
        viewModelScope.launch {
            try { vendedores.value = api.listarVendedores().body() ?: emptyList() } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                app.dataStore.data.collect { prefs ->
                    val json = prefs[com.mrjack.dressflow.data.api.PrefsKeys.USER_JSON]
                    if (json != null) {
                        val u = com.google.gson.Gson().fromJson(json, com.mrjack.dressflow.data.model.UsuarioLogado::class.java)
                        currentVendedorId.value = u?.vendedorId
                        userNivel.value = u?.nivel ?: "VENDEDOR"
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun criarAtendimento(
        nome: String,
        telefone: String,
        cpf: String,
        tipoCliente: String,
        dataNascimento: String,
        referencia: String,
        cep: String,
        endereco: String,
        vendedorId: Int?,
        evento: String,
        dataEvento: String,
    ) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mutableMapOf<String, Any?>(
                    "nome"           to nome,
                    "telefone"       to telefone.ifBlank { null },
                    "cpf"            to cpf.replace(Regex("[^0-9]"), "").ifBlank { null },
                    "tipoCliente"    to tipoCliente.ifBlank { null },
                    "dataNascimento" to dataNascimento.ifBlank { null },
                    "referencia"     to referencia.ifBlank { null },
                    "cep"            to cep.replace(Regex("[^0-9]"), "").ifBlank { null },
                    "endereco"       to endereco.ifBlank { null },
                    "vendedorId"     to vendedorId,
                )
                val resp = api.criarCliente(body)
                if (resp.isSuccessful) {
                    resp.body()?.let { c ->
                        selecionado.value = c
                        historico.value = emptyList()
                        eventoParaLocacao.value = evento
                        dataEventoParaLocacao.value = dataEvento
                        mostrandoNovaLocacao.value = true
                    }
                } else erro.value = "Erro ${resp.code()}"
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    fun criarCliente(form: ClienteForm) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mutableMapOf<String, Any?>(
                    "nome"        to form.nome,
                    "telefone"    to form.telefone.ifBlank { null },
                    "cpf"         to form.cpf.ifBlank { null },
                    "email"       to form.email.ifBlank { null },
                    "tipoCliente" to form.tipoCliente.ifBlank { null },
                    "cidade"      to form.cidade.ifBlank { null },
                    "observacoes" to form.observacoes.ifBlank { null },
                )
                val resp = api.criarCliente(body)
                if (resp.isSuccessful) {
                    sucesso.value = "Cliente criado!"
                    fecharForm()
                    resp.body()?.let { selecionar(it) }
                } else erro.value = "Erro ${resp.code()}"
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }
}

data class ClienteForm(
    var nome: String = "",
    var telefone: String = "",
    var cpf: String = "",
    var email: String = "",
    var tipoCliente: String = "",
    var cidade: String = "",
    var observacoes: String = "",
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun diasAtrasoReal(dataEvento: String): Int {
    return try {
        val partes = dataEvento.substring(0, 10).split("-")
        val tz = java.util.TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(tz)
        cal.set(partes[0].toInt(), partes[1].toInt() - 1, partes[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val diaSemana = cal.get(Calendar.DAY_OF_WEEK)
        val diasAteSegunda = if (diaSemana == Calendar.SUNDAY) 1 else 9 - diaSemana
        cal.add(Calendar.DAY_OF_MONTH, diasAteSegunda)
        val prazoMs = cal.timeInMillis
        val hoje = Calendar.getInstance(tz)
        hoje.set(Calendar.HOUR_OF_DAY, 0); hoje.set(Calendar.MINUTE, 0)
        hoje.set(Calendar.SECOND, 0); hoje.set(Calendar.MILLISECOND, 0)
        ((hoje.timeInMillis - prazoMs) / (1000L * 60 * 60 * 24)).toInt()
    } catch (_: Exception) { 0 }
}

// ─── Tela principal ───────────────────────────────────────────────────────────

@Composable
fun ClientesScreen(vm: ClientesViewModel = viewModel()) {
    val selecionado      by vm.selecionado.collectAsState()
    val criandoCliente   by vm.criandoCliente.collectAsState()
    val mostrandoNovaLoc by vm.mostrandoNovaLocacao.collectAsState()
    val eventoLoc        by vm.eventoParaLocacao.collectAsState()
    val dataEventoLoc    by vm.dataEventoParaLocacao.collectAsState()
    val sucesso          by vm.sucesso.collectAsState()
    val editandoCliente  by vm.editandoCliente.collectAsState()

    LaunchedEffect(sucesso) {
        if (sucesso != null) { delay(2000); vm.sucesso.value = null }
    }

    val selAtual = selecionado
    Box(Modifier.fillMaxSize()) {
        when {
            mostrandoNovaLoc && selAtual != null ->
                NovaLocacaoMultiTab(
                    clienteIdInicial = selAtual.id,
                    clienteNomeInicial = selAtual.nome,
                    eventoInicial = eventoLoc,
                    dataEventoInicial = dataEventoLoc,
                    onFecharTudo = {
                        vm.fecharNovaLocacao()
                        vm.eventoParaLocacao.value = ""
                        vm.dataEventoParaLocacao.value = ""
                    },
                )
            editandoCliente && selAtual != null -> ClienteEditarScreen(vm, selAtual)
            criandoCliente      -> NovoAtendimentoScreen(vm)
            selAtual != null -> ClienteDetalheScreen(vm, selAtual)
            else                -> ListaClientesScreen(vm)
        }
        sucesso?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Green600,
            ) { Text(it, color = Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}

// ─── Lista com tabs ───────────────────────────────────────────────────────────

@Composable
fun ListaClientesScreen(vm: ClientesViewModel) {
    val tabIdx by vm.tabIdx.collectAsState()

    LaunchedEffect(tabIdx) {
        if (tabIdx == 1) vm.carregarDevolucoes()
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 1.dp, color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clientes", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { vm.abrirFormCliente() },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Novo cliente")
                    }
                }
                Spacer(Modifier.height(10.dp))
                TabRow(
                    selectedTabIndex = tabIdx,
                    containerColor = Color.Transparent,
                    contentColor = Blue600,
                ) {
                    listOf("Clientes", "Devoluções").forEachIndexed { i, label ->
                        Tab(
                            selected = tabIdx == i,
                            onClick  = { vm.tabIdx.value = i },
                            text = { Text(label, fontWeight = FontWeight.Medium) },
                        )
                    }
                }
            }
        }

        when (tabIdx) {
            0 -> ClientesTabContent(vm)
            1 -> DevolucoesTabContent(vm)
        }
    }
}

// ─── Aba Clientes ─────────────────────────────────────────────────────────────

@Composable
fun ClientesTabContent(vm: ClientesViewModel) {
    val clientes  by vm.clientes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var busca     by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it; vm.buscar(it) },
            placeholder = { Text("Buscar por nome ou telefone...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Blue600)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        when {
            busca.length < 2 ->
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("Digite ao menos 2 letras para buscar", color = Gray500)
                }
            !isLoading && clientes.isEmpty() ->
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum cliente encontrado", color = Gray500)
                }
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(clientes, key = { it.id }) { c ->
                        ClienteCard(c) { vm.selecionar(c) }
                    }
                }
        }
    }
}

@Composable
fun ClienteCard(c: Cliente, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(21.dp), color = Blue600) {
                Box(contentAlignment = Alignment.Center) {
                    Text(c.nome.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(c.nome, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900)
                if (!c.telefone.isNullOrBlank()) Text(c.telefone, fontSize = 12.sp, color = Gray500)
            }
            if (!c.tipoCliente.isNullOrBlank()) {
                Surface(color = Indigo100, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        tipoLabel(c.tipoCliente), color = Indigo600, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            WaBotao(c.telefone, modifier = Modifier.size(36.dp))
        }
    }
}

// ─── Aba Devoluções ───────────────────────────────────────────────────────────

@Composable
fun DevolucoesTabContent(vm: ClientesViewModel) {
    val devolucoes    by vm.devolucoes.collectAsState()
    val isLoadingDev  by vm.isLoadingDev.collectAsState()
    val isDevolvendoId by vm.isDevolvendoId.collectAsState()
    var busca         by remember { mutableStateOf("") }
    var confirmando   by remember { mutableStateOf<Locacao?>(null) }

    confirmando?.let { loc ->
        ModalDevolucao(
            locacao = loc,
            isLoading = isDevolvendoId == loc.id,
            onConfirm = { vm.devolver(loc.id); confirmando = null },
            onCancel  = { confirmando = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Banner aviso
            Surface(
                color = Amber100,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Warning, null, tint = Amber500, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Vestidos com devolução pendente ficam bloqueados", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF92400E))
                        Text("Efetue a devolução para liberar o vestido para novas locações. Somente vestidos (locações femininas) aparecem aqui.", fontSize = 12.sp, color = Color(0xFFB45309))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar por cliente ou vestido...") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (isLoadingDev) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Blue600)
                    else if (busca.isNotEmpty()) IconButton(onClick = { busca = "" }) { Icon(Icons.Default.Clear, null) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (isLoadingDev) {
            item { Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue600) } }
        } else {
            val filtradas = devolucoes.filter {
                if (busca.isBlank()) true
                else it.cliente?.nome?.contains(busca, ignoreCase = true) == true ||
                     it.traje.contains(busca, ignoreCase = true)
            }

            if (filtradas.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        if (busca.isNotBlank()) {
                            Text("Nenhuma devolução encontrada para \"$busca\".", color = Gray500)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = Green600, modifier = Modifier.size(48.dp))
                                Text("Nenhuma devolução pendente", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Gray700)
                                Text("Todos os vestidos foram devolvidos.", fontSize = 13.sp, color = Gray500)
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("${filtradas.size} vestido${if (filtradas.size != 1) "s" else ""} aguardando devolução", fontSize = 12.sp, color = Gray500)
                }

                // Cabeçalho da tabela
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("Cliente",        Modifier.weight(2f),   fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                            Text("Vestido",        Modifier.weight(2f),   fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                            Text("Data Evento",    Modifier.weight(1.5f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                            Text("Dias em atraso", Modifier.weight(1.5f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1.5f))
                        }
                    }
                }

                items(filtradas, key = { it.id }) { l ->
                    val dias = diasAtrasoReal(l.dataEvento)
                    val diasCor = when {
                        dias > 3 -> Red500
                        dias > 0 -> Color(0xFFEA580C)
                        else     -> Green600
                    }
                    val diasTexto = when {
                        dias > 0 -> "$dias dia${if (dias != 1) "s" else ""}"
                        dias == 0 -> "Hoje é o prazo"
                        else -> "No prazo"
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            HorizontalDivider(color = Gray100)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(2f)) {
                                    Text(l.cliente?.nome ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Blue600)
                                    if (!l.cliente?.telefone.isNullOrBlank()) {
                                        Text(l.cliente?.telefone ?: "", fontSize = 11.sp, color = Gray500)
                                    }
                                }
                                Text(l.traje, Modifier.weight(2f), fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
                                Text(fmtDataCompleta(l.dataEvento), Modifier.weight(1.5f), fontSize = 12.sp, color = Gray700)
                                Text(diasTexto, Modifier.weight(1.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diasCor)
                                Box(Modifier.weight(1.5f)) {
                                    OutlinedButton(
                                        onClick = { confirmando = l },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) { Text("Devolver", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)) {
                        Spacer(Modifier.fillMaxWidth().height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ModalDevolucao(locacao: Locacao, isLoading: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val dias = diasAtrasoReal(locacao.dataEvento)
    val diasTexto = when {
        dias > 0 -> "$dias dia${if (dias != 1) "s" else ""}"
        dias == 0 -> "Hoje é o prazo"
        else -> "No prazo"
    }
    val diasCor = when {
        dias > 3 -> Red500
        dias > 0 -> Color(0xFFEA580C)
        else     -> Green600
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color(0xFFFCE7F3), shape = RoundedCornerShape(20.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AssignmentReturn, null, tint = Color(0xFFBE185D), modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text("Confirmar Devolução", fontWeight = FontWeight.Bold)
                    Text("Esta ação não pode ser desfeita", fontSize = 12.sp, color = Gray500)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Gray100, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModalInfoRow("Cliente",        locacao.cliente?.nome ?: "—")
                        ModalInfoRow("Vestido",        locacao.traje)
                        ModalInfoRow("Data do evento", fmtDataCompleta(locacao.dataEvento))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dias em atraso", fontSize = 12.sp, color = Gray500)
                            Text(diasTexto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diasCor)
                        }
                    }
                }
                Text("A devolução ficará registrada com data e horário de agora. Confirmar?", fontSize = 13.sp, color = Gray700)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Confirmar Devolução")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, enabled = !isLoading, shape = RoundedCornerShape(8.dp)) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun ModalInfoRow(label: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Gray500)
        Text(valor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
    }
}

// ─── Detalhe do cliente ───────────────────────────────────────────────────────

@Composable
fun ClienteDetalheScreen(vm: ClientesViewModel, c: Cliente, vendasVm: VendasViewModel = viewModel()) {
    val historico     by vm.historico.collectAsState()
    val isLoadingHist by vm.isLoadingHist.collectAsState()
    var locacaoParaEditar by remember { mutableStateOf<Locacao?>(null) }

    if (locacaoParaEditar != null) {
        LocacaoFormScreen(
            vm = vendasVm,
            clienteIdFixo = 0,
            locacaoExistente = locacaoParaEditar,
            onFechar = {
                locacaoParaEditar = null
                vm.carregarHistorico(c.id)
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.voltar() }) { Icon(Icons.Default.ArrowBack, null) }
                Text(c.nome, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { vm.abrirEdicaoCliente() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 13.sp)
                }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { vm.abrirNovaLocacao() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nova Locação", fontSize = 13.sp)
                }
            }
        }

        Row(Modifier.fillMaxSize()) {
            // Painel esquerdo — dados do cliente
            Column(
                modifier = Modifier.width(240.dp).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dados", fontWeight = FontWeight.SemiBold, color = Gray500, fontSize = 12.sp)
                        HorizontalDivider(color = Gray200)
                        ClienteInfoItem("Telefone", c.telefone)
                        ClienteInfoItem("CPF", c.cpf)
                        ClienteInfoItem("E-mail", c.email)
                        ClienteInfoItem("Tipo", tipoLabel(c.tipoCliente))
                        ClienteInfoItem("Cidade", c.cidade)
                        if (!c.observacoes.isNullOrBlank()) ClienteInfoItem("Obs", c.observacoes)
                    }
                }

                val total = historico.size
                val faturado = historico.filter { it.tipo != "ORCAMENTO" }
                    .sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
                if (total > 0) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Blue50),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$total visitas", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Blue600)
                            Text(brl(faturado), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray700)
                            Text("faturado no total", fontSize = 11.sp, color = Gray500)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = Gray200)

            // Painel direito — histórico
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Histórico (${historico.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (isLoadingHist) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue600)
                    }
                } else if (historico.isEmpty()) {
                    Text("Nenhuma visita registrada", color = Gray500)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(historico, key = { it.id }) { l -> HistoricoRow(l, onEditar = { locacaoParaEditar = l }) }
                    }
                }
            }
        }
    }
}

@Composable
fun ClienteInfoItem(label: String, valor: String?) {
    if (valor.isNullOrBlank()) return
    Column {
        Text(label, fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
        Text(valor, fontSize = 13.sp, color = Gray900)
    }
}

@Composable
fun HistoricoRow(l: Locacao, onEditar: (() -> Unit)? = null) {
    val (tipoCor, tipoLabel) = when (l.tipo) {
        "LOCACAO"   -> Blue600 to "Locação"
        "ORCAMENTO" -> Amber500 to "Orçamento"
        else        -> Green600 to "Venda"
    }
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column {
            Row(
                Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Badge(containerColor = tipoCor) { Text(tipoLabel, color = Color.White, fontSize = 9.sp) }
                Column(Modifier.weight(1f)) {
                    Text(l.evento ?: l.traje, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Gray900)
                    Text("Evento: ${fmtDataCompleta(l.dataEvento)} · ${l.vendedor?.nome ?: "—"}", fontSize = 11.sp, color = Gray500)
                    if (!l.tamanhoPaleto.isNullOrBlank() || !l.tamanhoManga.isNullOrBlank()) {
                        Text(
                            buildString {
                                l.tamanhoPaleto?.let { append("Pal $it ") }
                                l.tamanhoManga?.let  { append("Mg $it ") }
                                l.camisa?.let        { append("Ca $it ") }
                                l.calca?.let         { append("Cl $it ") }
                                l.sapato?.let        { append("Sp $it") }
                            }.trim(),
                            fontSize = 11.sp, color = Gray500,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(brl(l.valor.toDoubleOrNull() ?: 0.0), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(fmtDataSimples(l.dataEvento), fontSize = 10.sp, color = Gray500)
                }
            }
            if (onEditar != null && l.status != "CANCELADO") {
                HorizontalDivider(color = Gray100)
                TextButton(
                    onClick = onEditar,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = Blue600)
                    Spacer(Modifier.width(6.dp))
                    Text("Editar", fontSize = 12.sp, color = Blue600)
                }
            }
        }
    }
}

// ─── Novo Atendimento (idêntico ao web) ──────────────────────────────────────

fun maskTelefone(raw: String): String {
    val d = raw.replace(Regex("[^0-9]"), "").take(11)
    return when {
        d.length <= 2  -> d
        d.length <= 6  -> "(${d.take(2)}) ${d.drop(2)}"
        d.length <= 10 -> "(${d.take(2)}) ${d.drop(2).take(4)}-${d.drop(6)}"
        else           -> "(${d.take(2)}) ${d.drop(2).take(5)}-${d.drop(7)}"
    }
}

fun maskCpf(raw: String): String {
    val d = raw.replace(Regex("[^0-9]"), "").take(11)
    return when {
        d.length <= 3  -> d
        d.length <= 6  -> "${d.take(3)}.${d.drop(3)}"
        d.length <= 9  -> "${d.take(3)}.${d.drop(3).take(3)}.${d.drop(6)}"
        else           -> "${d.take(3)}.${d.drop(3).take(3)}.${d.drop(6).take(3)}-${d.drop(9)}"
    }
}

fun maskCep(raw: String): String {
    val d = raw.replace(Regex("[^0-9]"), "").take(8)
    return if (d.length <= 5) d else "${d.take(5)}-${d.drop(5)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovoAtendimentoScreen(vm: ClientesViewModel) {
    val isSaving          by vm.isSaving.collectAsState()
    val erro              by vm.erro.collectAsState()
    val vendedores        by vm.vendedores.collectAsState()
    val currentVendedorId by vm.currentVendedorId.collectAsState()
    val userNivel         by vm.userNivel.collectAsState()
    val podeAdicionar = listOf("ADMIN", "GERENCIA", "DIRETOR").contains(userNivel)

    var nome            by remember { mutableStateOf("") }
    var telefone        by remember { mutableStateOf("") }
    var cpf             by remember { mutableStateOf("") }
    var tipoCliente     by remember { mutableStateOf("") }
    var dataNasc        by remember { mutableStateOf("") }
    var referencia      by remember { mutableStateOf("") }
    var cep             by remember { mutableStateOf("") }
    var rua             by remember { mutableStateOf("") }
    var numero          by remember { mutableStateOf("") }
    var bairro          by remember { mutableStateOf("") }
    var cidade          by remember { mutableStateOf("") }
    var vendedorId      by remember { mutableStateOf<Int?>(null) }
    var evento          by remember { mutableStateOf("") }
    var dataEvento      by remember { mutableStateOf("") }
    var tipoExpanded    by remember { mutableStateOf(false) }
    var vendedorExpanded by remember { mutableStateOf(false) }
    var cepLoading      by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Pré-seleciona vendedor do usuário logado quando carregado
    LaunchedEffect(currentVendedorId) {
        if (vendedorId == null && currentVendedorId != null) {
            vendedorId = currentVendedorId
        }
    }

    fun buscarCep(cepDigits: String) {
        scope.launch {
            cepLoading = true
            try {
                val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL("https://viacep.com.br/ws/$cepDigits/json/").readText()
                }
                val json = com.google.gson.JsonParser.parseString(resp).asJsonObject
                if (!json.has("erro")) {
                    rua    = json.get("logradouro")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    bairro = json.get("bairro")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val loc = json.get("localidade")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val uf  = json.get("uf")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    cidade = if (loc.isNotBlank() && uf.isNotBlank()) "$loc - $uf" else loc
                }
            } catch (_: Exception) {}
            cepLoading = false
        }
    }

    val tipos = listOf(
        "" to "— Selecionar —",
        "NOIVO" to "Noivo", "NOIVA" to "Noiva",
        "PADRINHO" to "Padrinho", "MADRINHA" to "Madrinha",
        "PAGEM" to "Pagem",
        "FORMANDO" to "Formando", "FORMANDA" to "Formanda",
        "PAI_FORMANDO" to "Pai do Formando", "MAE_FORMANDA" to "Mãe da Formanda",
        "DEBUTANTE" to "Debutante", "PRINCIPE_DEBUTANTE" to "Príncipe Debutante",
        "PAI_DEBUTANTE" to "Pai da Debutante", "MAE_DEBUTANTE" to "Mãe da Debutante",
    )

    fun buildEndereco() = listOf(rua, numero, bairro, cidade).filter { it.isNotBlank() }.joinToString(", ")

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.fecharForm() }) { Icon(Icons.Default.ArrowBack, null, tint = Blue600) }
                Text("← Voltar", fontSize = 13.sp, color = Blue600, modifier = Modifier.clickable { vm.fecharForm() })
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text("Novo atendimento", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Gray900)
            Spacer(Modifier.height(4.dp))
            Text("Preencha os dados básicos. Você completará o cadastro após o registro.", fontSize = 13.sp, color = Gray500)
            Spacer(Modifier.height(24.dp))

            if (erro != null) {
                Surface(color = Red100, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(erro!!, color = Red500, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Nome
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Nome completo *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                        OutlinedTextField(value = nome, onValueChange = { nome = it },
                            placeholder = { Text("Ex: João da Silva", color = Gray500) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                    }

                    // Telefone + CPF
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Telefone *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = telefone,
                                onValueChange = { new ->
                                    telefone = new.filter { c -> c.isDigit() }.take(11)
                                },
                                placeholder = { Text("(11) 99999-9999", color = Gray500) },
                                visualTransformation = BrPhoneVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CPF", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = cpf,
                                onValueChange = { new ->
                                    cpf = new.filter { c -> c.isDigit() }.take(11)
                                },
                                placeholder = { Text("000.000.000-00", color = Gray500) },
                                visualTransformation = CpfVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                        }
                    }

                    // Tipo + Nascimento
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tipo de cliente", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            TipoClienteSelect(
                                value = tipoCliente,
                                onValueChange = { tipoCliente = it },
                                podeAdicionar = podeAdicionar,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (podeAdicionar) {
                            DatePickerField(label = "Data de nascimento", value = dataNasc, onDateSelected = { dataNasc = it }, modifier = Modifier.weight(1f))
                        }
                    }

                    // Referência
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Referência", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                        OutlinedTextField(value = referencia, onValueChange = { referencia = it },
                            placeholder = { Text("Ex: Padrinho do noivo João", color = Gray500) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                    }

                    HorizontalDivider(color = Gray200)
                    Text("ENDEREÇO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Blue600, letterSpacing = 1.sp)

                    // CEP
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CEP", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = cep,
                                onValueChange = { v ->
                                    cep = maskCep(v)
                                    val digits = cep.replace(Regex("[^0-9]"), "")
                                    if (digits.length == 8) buscarCep(digits)
                                },
                                placeholder = { Text("00000-000", color = Gray500) },
                                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                            if (cepLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Blue600)
                        }
                    }

                    // Rua + Número
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Rua / Logradouro", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = rua, onValueChange = { rua = it },
                                placeholder = { Text("Preenchido pelo CEP", color = Gray500) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                        }
                        Column(Modifier.weight(0.7f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Número", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = numero, onValueChange = { numero = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                        }
                    }

                    // Bairro + Cidade
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Bairro", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = bairro, onValueChange = { bairro = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cidade", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = cidade, onValueChange = { cidade = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                        }
                    }

                    HorizontalDivider(color = Gray200)
                    Text("DADOS DO EVENTO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Blue600, letterSpacing = 1.sp)

                    // Vendedor
                    if (vendedores.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Vendedor", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            ExposedDropdownMenuBox(expanded = vendedorExpanded, onExpandedChange = { vendedorExpanded = it }) {
                                OutlinedTextField(
                                    value = vendedores.find { it.id == vendedorId }?.nome ?: "— Selecionar —",
                                    onValueChange = {}, readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vendedorExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(8.dp),
                                )
                                ExposedDropdownMenu(expanded = vendedorExpanded, onDismissRequest = { vendedorExpanded = false }) {
                                    vendedores.filter { it.ativo }.forEach { v ->
                                        DropdownMenuItem(text = { Text(v.nome) }, onClick = { vendedorId = v.id; vendedorExpanded = false })
                                    }
                                }
                            }
                        }
                    }

                    // Evento + Data
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Evento *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                            OutlinedTextField(value = evento, onValueChange = { evento = it },
                                placeholder = { Text("Ex: Casamento", color = Gray500) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                        }
                        DatePickerField(label = "Data do evento *", value = dataEvento, onDateSelected = { dataEvento = it }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        vm.erro.value = null
                        when {
                            nome.isBlank()       -> vm.erro.value = "Nome obrigatório"
                            telefone.isBlank()   -> vm.erro.value = "Telefone obrigatório"
                            evento.isBlank()     -> vm.erro.value = "Informe o evento"
                            dataEvento.isBlank() -> vm.erro.value = "Informe a data do evento"
                            else -> vm.criarAtendimento(nome, telefone, cpf, tipoCliente, dataNasc, referencia, cep, buildEndereco(), vendedorId, evento, dataEvento)
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Continuar para registro →", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = { vm.fecharForm() }, shape = RoundedCornerShape(8.dp)) { Text("Cancelar") }
            }
        }
    }
}

@Composable
fun ClienteField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    keyType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyType, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(10.dp),
    )
}

// ─── Editar cliente ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteEditarScreen(vm: ClientesViewModel, c: Cliente) {
    val isSaving  by vm.isSaving.collectAsState()
    val erro      by vm.erro.collectAsState()
    val userNivel by vm.userNivel.collectAsState()
    val podeAdicionar = listOf("ADMIN", "GERENCIA", "DIRETOR").contains(userNivel)

    var nome        by remember { mutableStateOf(c.nome) }
    var telefone    by remember { mutableStateOf(c.telefone?.filter { it.isDigit() } ?: "") }
    var cpf         by remember { mutableStateOf(c.cpf?.filter { it.isDigit() } ?: "") }
    var email       by remember { mutableStateOf(c.email ?: "") }
    var tipoCliente by remember { mutableStateOf(c.tipoCliente ?: "") }
    var cidade      by remember { mutableStateOf(c.cidade ?: "") }
    var observacoes by remember { mutableStateOf(c.observacoes ?: "") }
    var tipoExpanded by remember { mutableStateOf(false) }

    val tipos = listOf(
        "" to "— Selecionar —",
        "NOIVO" to "Noivo", "NOIVA" to "Noiva",
        "PADRINHO" to "Padrinho", "MADRINHA" to "Madrinha",
        "PAGEM" to "Pagem",
        "FORMANDO" to "Formando", "FORMANDA" to "Formanda",
        "PAI_FORMANDO" to "Pai do Formando", "MAE_FORMANDA" to "Mãe da Formanda",
        "DEBUTANTE" to "Debutante", "PRINCIPE_DEBUTANTE" to "Príncipe Debutante",
        "PAI_DEBUTANTE" to "Pai da Debutante", "MAE_DEBUTANTE" to "Mãe da Debutante",
    )

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.fecharEdicaoCliente() }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Blue600)
                }
                Text("Editar cliente", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (erro != null) {
                Surface(color = Red100, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(erro!!, color = Red500, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }

            ClienteField("Nome completo *", nome, { nome = it }, caps = KeyboardCapitalization.Words)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Telefone", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                    OutlinedTextField(
                        value = telefone,
                        onValueChange = { new -> telefone = new.filter { c -> c.isDigit() }.take(11) },
                        placeholder = { Text("(11) 99999-9999", color = Gray500) },
                        visualTransformation = BrPhoneVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CPF", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                    OutlinedTextField(
                        value = cpf,
                        onValueChange = { new -> cpf = new.filter { c -> c.isDigit() }.take(11) },
                        placeholder = { Text("000.000.000-00", color = Gray500) },
                        visualTransformation = CpfVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                }
            }

            ClienteField("E-mail", email, { email = it }, keyType = KeyboardType.Email)
            ClienteField("Cidade", cidade, { cidade = it }, caps = KeyboardCapitalization.Words)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tipo de cliente", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                TipoClienteSelect(
                    value = tipoCliente,
                    onValueChange = { tipoCliente = it },
                    podeAdicionar = podeAdicionar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Observações", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                OutlinedTextField(
                    value = observacoes,
                    onValueChange = { observacoes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 4,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (nome.isBlank()) { vm.erro.value = "Nome obrigatório"; return@Button }
                        vm.salvarEdicaoCliente(ClienteForm(
                            nome = nome, telefone = telefone, cpf = cpf,
                            email = email, tipoCliente = tipoCliente,
                            cidade = cidade, observacoes = observacoes,
                        ))
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salvar alterações", fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = { vm.fecharEdicaoCliente() },
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Cancelar") }
            }
        }
    }
}

fun tipoLabel(tipo: String?): String = when (tipo) {
    "NOIVO"              -> "Noivo"
    "NOIVA"              -> "Noiva"
    "PADRINHO"           -> "Padrinho"
    "MADRINHA"           -> "Madrinha"
    "PAGEM"              -> "Pagem"
    "FORMANDO"           -> "Formando"
    "FORMANDA"           -> "Formanda"
    "PAI_FORMANDO"       -> "Pai do Formando"
    "MAE_FORMANDA"       -> "Mãe da Formanda"
    "DEBUTANTE"          -> "Debutante"
    "PRINCIPE_DEBUTANTE" -> "Príncipe Debutante"
    "PAI_DEBUTANTE"      -> "Pai da Debutante"
    "MAE_DEBUTANTE"      -> "Mãe da Debutante"
    "OUTROS"             -> "Outros"
    else                 -> tipo ?: "—"
}

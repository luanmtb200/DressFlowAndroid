package com.mrjack.dressflow.ui.screens.agenda

import android.app.Application
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
import com.mrjack.dressflow.data.model.Agendamento
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Helpers de data (sem java.time) ─────────────────────────────────────────

private fun hoje(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

private fun addDias(dateStr: String, dias: Int): String {
    return try {
        val parts = dateStr.split("-")
        val c = Calendar.getInstance()
        c.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        c.add(Calendar.DAY_OF_MONTH, dias)
        "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    } catch (_: Exception) { dateStr }
}

private fun fmtDDMM(dateStr: String): String = try {
    "${dateStr.substring(8, 10)}/${dateStr.substring(5, 7)}"
} catch (_: Exception) { dateStr }

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AgendaViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)
    val agendamentos  = MutableStateFlow<List<Agendamento>>(emptyList())
    val isLoading     = MutableStateFlow(false)
    val isSaving      = MutableStateFlow(false)
    val erro          = MutableStateFlow<String?>(null)
    val sucesso       = MutableStateFlow<String?>(null)
    val criandoNovo   = MutableStateFlow(false)

    fun carregar(inicio: String, fim: String) {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                agendamentos.value = api.listarAgendamentos(inicio = inicio, fim = fim)
                    .body()?.filter { it.status != "CANCELADO" } ?: emptyList()
            } catch (e: Exception) { erro.value = e.message }
            finally { isLoading.value = false }
        }
    }

    fun confirmar(id: Int) {
        viewModelScope.launch {
            try {
                api.atualizarAgendamento(id, mapOf("status" to "CONFIRMADO"))
                agendamentos.value = agendamentos.value.map {
                    if (it.id == id) it.copy(status = "CONFIRMADO") else it
                }
            } catch (e: Exception) { erro.value = e.message }
        }
    }

    fun criarAgendamento(form: AgendamentoForm, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mutableMapOf<String, Any?>(
                    "clienteNome"     to form.clienteNome,
                    "clienteTelefone" to form.clienteTelefone.ifBlank { null },
                    "tipo"            to form.tipo,
                    "data"            to form.data,
                    "hora"            to form.hora.ifBlank { null },
                    "observacoes"     to form.observacoes.ifBlank { null },
                    "tipoCliente"     to form.tipoCliente.ifBlank { null },
                    "traje"           to form.traje.ifBlank { null },
                )
                val resp = api.criarAgendamento(body)
                if (resp.isSuccessful) {
                    sucesso.value = "Agendamento criado!"
                    criandoNovo.value = false
                    onSuccess()
                } else erro.value = "Erro ${resp.code()}"
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }
}

data class AgendamentoForm(
    var clienteNome: String = "",
    var clienteTelefone: String = "",
    var tipo: String = "ATENDIMENTO",
    var data: String = "",
    var hora: String = "",
    var tipoCliente: String = "",
    var traje: String = "",
    var observacoes: String = "",
)

// ─── Tela principal ───────────────────────────────────────────────────────────

@Composable
fun AgendaScreen(vm: AgendaViewModel = viewModel()) {
    val criandoNovo by vm.criandoNovo.collectAsState()
    val sucesso     by vm.sucesso.collectAsState()
    var inicio      by remember { mutableStateOf(hoje()) }
    var fim         by remember { mutableStateOf(hoje()) }

    LaunchedEffect(sucesso) {
        if (sucesso != null) { kotlinx.coroutines.delay(2000); vm.sucesso.value = null }
    }

    Box(Modifier.fillMaxSize()) {
        if (criandoNovo) {
            NovoAgendamentoScreen(vm, inicio) { vm.carregar(inicio, fim) }
        } else {
            ListaAgendaScreen(vm, inicio, fim,
                onPeriodoChange = { i, f -> inicio = i; fim = f },
                onNovo = { vm.criandoNovo.value = true },
            )
        }
        sucesso?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = Green600,
            ) { Text(it, color = Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun ListaAgendaScreen(
    vm: AgendaViewModel,
    inicio: String,
    fim: String,
    onPeriodoChange: (String, String) -> Unit,
    onNovo: () -> Unit,
) {
    val agendamentos by vm.agendamentos.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val erro        by vm.erro.collectAsState()

    LaunchedEffect(inicio, fim) { vm.carregar(inicio, fim) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Agenda", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.carregar(inicio, fim) }) { Icon(Icons.Default.Refresh, null, tint = Gray500) }
            Button(
                onClick = onNovo,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Novo")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Ontem"   to { onPeriodoChange(addDias(hoje(), -1), addDias(hoje(), -1)) },
                "Hoje"    to { onPeriodoChange(hoje(), hoje()) },
                "Amanhã"  to { onPeriodoChange(addDias(hoje(), 1), addDias(hoje(), 1)) },
                "+3 dias" to { onPeriodoChange(hoje(), addDias(hoje(), 3)) },
                "+7 dias" to { onPeriodoChange(hoje(), addDias(hoje(), 7)) },
            ).forEach { (label, action) ->
                OutlinedButton(
                    onClick = action,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text(label, fontSize = 11.sp) }
            }
        }

        Text(
            "${agendamentos.size} agendamentos · " +
            if (inicio == fim) fmtDDMM(inicio) else "${fmtDDMM(inicio)} – ${fmtDDMM(fim)}",
            fontSize = 13.sp, color = Gray500,
        )

        if (erro != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Red100), shape = RoundedCornerShape(8.dp)) {
                Text(erro!!, color = Red500, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
        } else if (agendamentos.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Text("Nenhum agendamento neste período", color = Gray500)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(agendamentos, key = { it.id }) { ag ->
                    AgendamentoCard(ag) { vm.confirmar(ag.id) }
                }
            }
        }
    }
}

@Composable
fun AgendamentoCard(ag: Agendamento, onConfirmar: () -> Unit) {
    val (corTexto, corFundo) = when (ag.status) {
        "CONFIRMADO" -> Green600 to Green100
        else         -> Amber500 to Amber100
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 44.dp)) {
                Text(ag.hora?.takeIf { it.isNotBlank() } ?: "--:--", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Blue600)
                val dataExib = ag.data.take(10).let { d ->
                    if (d.length >= 10) "${d.substring(8, 10)}/${d.substring(5, 7)}" else d.ifBlank { "—" }
                }
                Text(dataExib, fontSize = 11.sp, color = Gray500)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(ag.clienteNome.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AgChip(tipoAgLabel(ag.tipo))
                    if (!ag.tipoCliente.isNullOrBlank()) AgChip(ag.tipoCliente!!)
                }
                if (!ag.traje.isNullOrBlank())       Text(ag.traje!!, fontSize = 11.sp, color = Gray500)
                if (!ag.observacoes.isNullOrBlank()) Text(ag.observacoes!!, fontSize = 11.sp, color = Gray500)
                if (!ag.clienteTelefone.isNullOrBlank()) Text(ag.clienteTelefone!!, fontSize = 11.sp, color = Gray500)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = corFundo, shape = RoundedCornerShape(6.dp)) {
                    Text(ag.status, color = corTexto, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (ag.status == "PENDENTE") {
                    Button(
                        onClick = onConfirmar,
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Confirmar", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AgChip(label: String) {
    Surface(color = Indigo100, shape = RoundedCornerShape(4.dp)) {
        Text(label, color = Indigo600, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ─── Novo agendamento ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovoAgendamentoScreen(vm: AgendaViewModel, dataInicio: String, onSaved: () -> Unit) {
    var form     by remember { mutableStateOf(AgendamentoForm(data = dataInicio)) }
    val isSaving by vm.isSaving.collectAsState()
    val erro     by vm.erro.collectAsState()

    val tipos = listOf("ATENDIMENTO" to "Atendimento", "PROVA" to "Prova",
        "PRIMEIRA_PROVA_VESTIDO" to "1ª Prova Vestido", "RETIRADA_VESTIDO" to "Retirada Vestido", "OUTRO" to "Outro")
    val tiposCliente = listOf("" to "—", "NOIVO" to "Noivo", "PADRINHO" to "Padrinho",
        "FORMANDO" to "Formando", "PAI" to "Pai", "DEBUTANTE" to "Debutante", "SOCIAL" to "Social")

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.criandoNovo.value = false }) { Icon(Icons.Default.Close, null) }
                Text("Novo agendamento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (form.clienteNome.isBlank()) { vm.erro.value = "Nome do cliente obrigatório"; return@Button }
                        if (form.data.isBlank()) { vm.erro.value = "Data obrigatória"; return@Button }
                        vm.criarAgendamento(form, onSaved)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tipo de agendamento", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Gray700)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tipos.forEach { (v, label) ->
                    FilterChip(
                        selected = form.tipo == v,
                        onClick  = { form = form.copy(tipo = v) },
                        label    = { Text(label, fontSize = 11.sp) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgField("Nome do cliente *", form.clienteNome, { form = form.copy(clienteNome = it) },
                    caps = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
                AgField("Telefone", form.clienteTelefone, { form = form.copy(clienteTelefone = it) },
                    keyType = KeyboardType.Phone, modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatePickerField(
                    label = "Data *",
                    value = form.data,
                    onDateSelected = { form = form.copy(data = it) },
                    modifier = Modifier.weight(1f),
                )
                AgField("Hora (HH:MM)", form.hora, { form = form.copy(hora = it) },
                    keyType = KeyboardType.Number, modifier = Modifier.weight(1f))
            }

            var expandedTipo by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expandedTipo, onExpandedChange = { expandedTipo = it }) {
                OutlinedTextField(
                    value = tiposCliente.find { it.first == form.tipoCliente }?.second ?: "—",
                    onValueChange = {}, readOnly = true, label = { Text("Tipo de cliente") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedTipo) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(10.dp),
                )
                ExposedDropdownMenu(expanded = expandedTipo, onDismissRequest = { expandedTipo = false }) {
                    tiposCliente.forEach { (v, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            form = form.copy(tipoCliente = v); expandedTipo = false
                        })
                    }
                }
            }

            AgField("Traje", form.traje, { form = form.copy(traje = it) }, caps = KeyboardCapitalization.Sentences)
            OutlinedTextField(
                value = form.observacoes, onValueChange = { form = form.copy(observacoes = it) },
                label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

@Composable
fun AgField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    keyType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = true, shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyType, imeAction = ImeAction.Next),
    )
}

fun tipoAgLabel(tipo: String) = when (tipo) {
    "PROVA"                  -> "Prova"
    "ATENDIMENTO"            -> "Atendimento"
    "PRIMEIRA_PROVA_VESTIDO" -> "1ª Prova"
    "RETIRADA_VESTIDO"       -> "Retirada"
    "OUTRO"                  -> "Outro"
    else                     -> tipo.replace("_", " ").ifBlank { "—" }
}

package com.mrjack.dressflow.ui.screens.meupanel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Tarefa
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TarefasViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val tarefas = MutableStateFlow<List<Tarefa>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val isSaving = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)

    val totalPendentes get() = tarefas.value.count { !it.concluidaHoje }

    fun carregar() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                tarefas.value = withContext(Dispatchers.IO) { api.listarTarefas().body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar tarefas: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun criar(titulo: String, descricao: String?, prazo: String?, recorrencia: String?, diaRecorrencia: Int?) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mapOf<String, Any?>(
                    "titulo" to titulo,
                    "descricao" to descricao,
                    "prazo" to prazo,
                    "recorrencia" to recorrencia,
                    "diaRecorrencia" to diaRecorrencia,
                )
                withContext(Dispatchers.IO) { api.criarTarefa(body) }
                carregar()
            } catch (e: Exception) {
                erro.value = "Erro ao criar tarefa: ${e.message}"
            } finally {
                isSaving.value = false
            }
        }
    }

    fun editar(id: Int, titulo: String, descricao: String?, prazo: String?, recorrencia: String?, diaRecorrencia: Int?) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mapOf<String, Any?>(
                    "titulo" to titulo,
                    "descricao" to descricao,
                    "prazo" to prazo,
                    "recorrencia" to recorrencia,
                    "diaRecorrencia" to diaRecorrencia,
                )
                withContext(Dispatchers.IO) { api.atualizarTarefa(id, body) }
                carregar()
            } catch (e: Exception) {
                erro.value = "Erro ao editar tarefa: ${e.message}"
            } finally {
                isSaving.value = false
            }
        }
    }

    fun concluir(id: Int, concluida: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.concluirTarefa(id, mapOf("concluida" to concluida)) }
                carregar()
            } catch (e: Exception) {
                erro.value = "Erro: ${e.message}"
            }
        }
    }

    fun remover(id: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.deletarTarefa(id) }
                tarefas.value = tarefas.value.filter { it.id != id }
            } catch (e: Exception) {
                erro.value = "Erro ao remover: ${e.message}"
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private enum class PrazoStatus { ATRASADA, HOJE, FUTURA, SEM_PRAZO }

private fun statusPrazo(prazo: String?): PrazoStatus {
    if (prazo == null) return PrazoStatus.SEM_PRAZO
    return try {
        val data = LocalDate.parse(prazo.substring(0, 10))
        val hoje = LocalDate.now()
        when {
            data.isBefore(hoje) -> PrazoStatus.ATRASADA
            data.isEqual(hoje) -> PrazoStatus.HOJE
            else -> PrazoStatus.FUTURA
        }
    } catch (_: Exception) { PrazoStatus.SEM_PRAZO }
}

private fun fmtPrazo(prazo: String?): String? {
    if (prazo == null) return null
    return try { "${prazo.substring(8, 10)}/${prazo.substring(5, 7)}" } catch (_: Exception) { null }
}

private val OPCOES_RECORRENCIA = listOf<Pair<String?, String>>(
    null to "Sem repetição",
    "DIARIA" to "Diária",
    "SEMANAL" to "Semanal",
    "MENSAL" to "Mensal",
)

private val DIAS_SEMANA = listOf(
    0 to "Domingo", 1 to "Segunda", 2 to "Terça", 3 to "Quarta", 4 to "Quinta", 5 to "Sexta", 6 to "Sábado",
)
private val DIAS_SEMANA_ABREV = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

private fun labelRecorrencia(recorrencia: String?, diaRecorrencia: Int?): String = when (recorrencia) {
    "SEMANAL" -> if (diaRecorrencia != null) "Semanal (${DIAS_SEMANA_ABREV[diaRecorrencia]})" else "Semanal"
    "MENSAL" -> if (diaRecorrencia != null) "Mensal (dia $diaRecorrencia)" else "Mensal"
    "DIARIA" -> "Diária"
    else -> "Diária"
}

// ── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun TarefasTab(usuarioId: Int, vm: TarefasViewModel = viewModel()) {
    val tarefas by vm.tarefas.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    val erro by vm.erro.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Tarefa?>(null) }
    var expandidoHoje by remember { mutableStateOf(true) }
    var expandidoRecorrentes by remember { mutableStateOf(true) }
    var expandidoProximas by remember { mutableStateOf(true) }
    var expandidoConcluidas by remember { mutableStateOf(false) }

    LaunchedEffect(usuarioId) { vm.carregar() }

    val recorrentes = remember(tarefas) { tarefas.filter { it.recorrencia != null } }
    val naoRecorrentes = remember(tarefas) { tarefas.filter { it.recorrencia == null } }
    val concluidas = remember(naoRecorrentes) { naoRecorrentes.filter { it.concluida } }
    val pendentes = remember(naoRecorrentes) { naoRecorrentes.filter { !it.concluida } }
    val hojeAtrasadas = remember(pendentes) {
        pendentes.filter { statusPrazo(it.prazo) != PrazoStatus.FUTURA }
    }
    val proximas = remember(pendentes) {
        pendentes.filter { statusPrazo(it.prazo) == PrazoStatus.FUTURA }
    }

    Box(Modifier.fillMaxSize()) {
        if (isLoading && tarefas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (erro != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Red100),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                        }
                    }
                }

                if (tarefas.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhuma tarefa. Toque em + para criar.", color = Gray500, fontSize = 14.sp)
                        }
                    }
                }

                tarefaSecao(
                    titulo = "Hoje / Atrasadas",
                    tarefas = hojeAtrasadas,
                    expandido = expandidoHoje,
                    onToggle = { expandidoHoje = !expandidoHoje },
                    usuarioId = usuarioId,
                    isSaving = isSaving,
                    onConcluir = { id, c -> vm.concluir(id, c) },
                    onRemover = { vm.remover(it) },
                    onEditar = { editando = it; showDialog = true },
                )
                tarefaSecao(
                    titulo = "Recorrentes (diárias)",
                    tarefas = recorrentes,
                    expandido = expandidoRecorrentes,
                    onToggle = { expandidoRecorrentes = !expandidoRecorrentes },
                    usuarioId = usuarioId,
                    isSaving = isSaving,
                    onConcluir = { id, c -> vm.concluir(id, c) },
                    onRemover = { vm.remover(it) },
                    onEditar = { editando = it; showDialog = true },
                )
                tarefaSecao(
                    titulo = "Próximas",
                    tarefas = proximas,
                    expandido = expandidoProximas,
                    onToggle = { expandidoProximas = !expandidoProximas },
                    usuarioId = usuarioId,
                    isSaving = isSaving,
                    onConcluir = { id, c -> vm.concluir(id, c) },
                    onRemover = { vm.remover(it) },
                    onEditar = { editando = it; showDialog = true },
                )
                tarefaSecao(
                    titulo = "Concluídas",
                    tarefas = concluidas,
                    expandido = expandidoConcluidas,
                    onToggle = { expandidoConcluidas = !expandidoConcluidas },
                    usuarioId = usuarioId,
                    isSaving = isSaving,
                    onConcluir = { id, c -> vm.concluir(id, c) },
                    onRemover = { vm.remover(it) },
                    onEditar = { editando = it; showDialog = true },
                )

                item { Spacer(Modifier.height(64.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { editando = null; showDialog = true },
            containerColor = Blue600,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova tarefa")
        }
    }

    if (showDialog) {
        TarefaDialog(
            editando = editando,
            isSaving = isSaving,
            onConfirmar = { titulo, descricao, prazo, recorrencia, diaRecorrencia ->
                val atual = editando
                if (atual != null) vm.editar(atual.id, titulo, descricao, prazo, recorrencia, diaRecorrencia)
                else vm.criar(titulo, descricao, prazo, recorrencia, diaRecorrencia)
                showDialog = false
                editando = null
            },
            onDismiss = { showDialog = false; editando = null },
        )
    }
}

// ── Seção colapsável ─────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.tarefaSecao(
    titulo: String,
    tarefas: List<Tarefa>,
    expandido: Boolean,
    onToggle: () -> Unit,
    usuarioId: Int,
    isSaving: Boolean,
    onConcluir: (Int, Boolean) -> Unit,
    onRemover: (Int) -> Unit,
    onEditar: (Tarefa) -> Unit,
) {
    if (tarefas.isEmpty()) return

    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Gray50)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .let { it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titulo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, letterSpacing = 0.5.sp)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Gray200).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("${tarefas.size}", fontSize = 10.sp, color = Gray700, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = Gray500,
                )
            }
        }
    }

    if (expandido) {
        items(tarefas, key = { it.id }) { t ->
            TarefaItem(
                tarefa = t,
                usuarioId = usuarioId,
                isSaving = isSaving,
                onConcluir = onConcluir,
                onRemover = onRemover,
                onEditar = onEditar,
            )
        }
    }
}

// ── Item de tarefa ─────────────────────────────────────────────────────────────

@Composable
private fun TarefaItem(
    tarefa: Tarefa,
    usuarioId: Int,
    isSaving: Boolean,
    onConcluir: (Int, Boolean) -> Unit,
    onRemover: (Int) -> Unit,
    onEditar: (Tarefa) -> Unit,
) {
    val checked = if (tarefa.recorrencia != null) tarefa.concluidaHoje else tarefa.concluida
    val status = statusPrazo(tarefa.prazo)
    var expandido by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
        modifier = Modifier.clickable { expandido = !expandido },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onConcluir(tarefa.id, !checked) },
                enabled = !isSaving,
                colors = CheckboxDefaults.colors(checkedColor = Blue600),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tarefa.titulo,
                    fontSize = if (expandido) 16.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) Gray500 else Gray900,
                )
                if (!tarefa.descricao.isNullOrBlank()) {
                    Text(tarefa.descricao, fontSize = if (expandido) 14.sp else 11.sp, color = Gray500, maxLines = if (expandido) Int.MAX_VALUE else 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tarefa.recorrencia != null) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Indigo100).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(labelRecorrencia(tarefa.recorrencia, tarefa.diaRecorrencia), fontSize = 10.sp, color = Indigo600, fontWeight = FontWeight.Medium)
                        }
                    }
                    fmtPrazo(tarefa.prazo)?.let { prazoFmt ->
                        val (bg, fg) = when (status) {
                            PrazoStatus.ATRASADA -> Red100 to Red500
                            PrazoStatus.HOJE -> Amber100 to Amber500
                            else -> Gray100 to Gray500
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(
                                if (status == PrazoStatus.ATRASADA) "Atrasada — $prazoFmt" else prazoFmt,
                                fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    if (tarefa.criadoPorId != usuarioId) {
                        Text("de ${tarefa.criadoPorNome}", fontSize = 10.sp, color = Gray500)
                    }
                }
            }
            if (tarefa.criadoPorId == usuarioId) {
                IconButton(onClick = { onEditar(tarefa) }, modifier = Modifier.size(32.dp), enabled = !isSaving) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Gray500, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { onRemover(tarefa.id) }, modifier = Modifier.size(32.dp), enabled = !isSaving) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Gray500, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Diálogo: nova / editar tarefa ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarefaDialog(
    editando: Tarefa?,
    isSaving: Boolean,
    onConfirmar: (titulo: String, descricao: String?, prazo: String?, recorrencia: String?, diaRecorrencia: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var titulo by remember { mutableStateOf(editando?.titulo ?: "") }
    var descricao by remember { mutableStateOf(editando?.descricao ?: "") }
    var prazo by remember { mutableStateOf(editando?.prazo?.take(10) ?: "") }
    var recorrencia by remember { mutableStateOf(editando?.recorrencia) }
    var diaRecorrencia by remember { mutableStateOf(editando?.diaRecorrencia) }
    var expandidoDia by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editando != null) "Editar tarefa" else "Nova tarefa", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                Text("Repetição", fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OPCOES_RECORRENCIA.forEach { (valor, label) ->
                        FilterChip(
                            selected = recorrencia == valor,
                            onClick = { recorrencia = valor; diaRecorrencia = null; if (valor != null) prazo = "" },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
                }
                if (recorrencia == null) {
                    DatePickerField(
                        label = "Prazo (opcional)",
                        value = prazo,
                        onDateSelected = { prazo = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (recorrencia == "SEMANAL" || recorrencia == "MENSAL") {
                    val opcoesDia = if (recorrencia == "SEMANAL") DIAS_SEMANA else (1..31).map { it to "Dia $it" }
                    val labelDia = opcoesDia.find { it.first == diaRecorrencia }?.second ?: "Sem dia específico"
                    Text(
                        if (recorrencia == "SEMANAL") "Dia da semana" else "Dia do mês",
                        fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium,
                    )
                    ExposedDropdownMenuBox(expanded = expandidoDia, onExpandedChange = { expandidoDia = it }) {
                        OutlinedTextField(
                            value = labelDia, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandidoDia) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp), singleLine = true,
                        )
                        ExposedDropdownMenu(expanded = expandidoDia, onDismissRequest = { expandidoDia = false }) {
                            DropdownMenuItem(text = { Text("Sem dia específico") }, onClick = { diaRecorrencia = null; expandidoDia = false })
                            opcoesDia.forEach { (v, l) ->
                                DropdownMenuItem(text = { Text(l) }, onClick = { diaRecorrencia = v; expandidoDia = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(titulo.trim(), descricao.trim().ifBlank { null }, prazo.ifBlank { null }, recorrencia, diaRecorrencia)
                },
                enabled = titulo.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (editando != null) "Salvar" else "Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

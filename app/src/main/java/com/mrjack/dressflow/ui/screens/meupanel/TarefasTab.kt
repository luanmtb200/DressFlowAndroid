package com.mrjack.dressflow.ui.screens.meupanel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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

    fun criar(titulo: String, descricao: String?, prazo: String?, recorrente: Boolean) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mapOf<String, Any?>(
                    "titulo" to titulo,
                    "descricao" to descricao,
                    "prazo" to prazo,
                    "recorrente" to recorrente,
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

// ── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun TarefasTab(usuarioId: Int, vm: TarefasViewModel = viewModel()) {
    val tarefas by vm.tarefas.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    val erro by vm.erro.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var expandidoHoje by remember { mutableStateOf(true) }
    var expandidoRecorrentes by remember { mutableStateOf(true) }
    var expandidoProximas by remember { mutableStateOf(true) }
    var expandidoConcluidas by remember { mutableStateOf(false) }

    LaunchedEffect(usuarioId) { vm.carregar() }

    val recorrentes = remember(tarefas) { tarefas.filter { it.recorrente } }
    val naoRecorrentes = remember(tarefas) { tarefas.filter { !it.recorrente } }
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
                )

                item { Spacer(Modifier.height(64.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            containerColor = Blue600,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova tarefa")
        }
    }

    if (showDialog) {
        NovaTarefaDialog(
            isSaving = isSaving,
            onConfirmar = { titulo, descricao, prazo, recorrente ->
                vm.criar(titulo, descricao, prazo, recorrente)
                showDialog = false
            },
            onDismiss = { showDialog = false },
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
) {
    val checked = if (tarefa.recorrente) tarefa.concluidaHoje else tarefa.concluida
    val status = statusPrazo(tarefa.prazo)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) Gray500 else Gray900,
                )
                if (!tarefa.descricao.isNullOrBlank()) {
                    Text(tarefa.descricao, fontSize = 11.sp, color = Gray500, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tarefa.recorrente) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Indigo100).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text("Diária", fontSize = 10.sp, color = Indigo600, fontWeight = FontWeight.Medium)
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
                IconButton(onClick = { onRemover(tarefa.id) }, modifier = Modifier.size(32.dp), enabled = !isSaving) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Gray500, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Diálogo: nova tarefa ─────────────────────────────────────────────────────

@Composable
private fun NovaTarefaDialog(
    isSaving: Boolean,
    onConfirmar: (titulo: String, descricao: String?, prazo: String?, recorrente: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var prazo by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova tarefa", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = recorrente,
                        onCheckedChange = { recorrente = it; if (it) prazo = "" },
                        colors = CheckboxDefaults.colors(checkedColor = Blue600),
                    )
                    Text("Tarefa diária (recorrente)", fontSize = 13.sp, color = Gray700)
                }
                if (!recorrente) {
                    DatePickerField(
                        label = "Prazo (opcional)",
                        value = prazo,
                        onDateSelected = { prazo = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmar(titulo.trim(), descricao.trim().ifBlank { null }, prazo.ifBlank { null }, recorrente)
                },
                enabled = titulo.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

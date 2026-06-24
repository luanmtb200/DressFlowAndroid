package com.mrjack.dressflow.ui.screens.meupanel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.*
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ══════════════════════════════════════════════════════════════════════════════
// ViewModel
// ══════════════════════════════════════════════════════════════════════════════

class AcademiaViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val salas = MutableStateFlow<List<AcademiaSala>>(emptyList())
    val conteudos = MutableStateFlow<List<AcademiaConteudo>>(emptyList())
    val flashcards = MutableStateFlow<List<AcademiaFlashcard>>(emptyList())
    val quizzes = MutableStateFlow<List<AcademiaQuiz>>(emptyList())
    val historico = MutableStateFlow<List<AcademiaResposta>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)

    fun carregarSalas() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                salas.value = withContext(Dispatchers.IO) { api.listarSalasAcademia().body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar salas: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun carregarConteudos() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                conteudos.value = withContext(Dispatchers.IO) { api.listarConteudosAcademia().body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar conteudos: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun carregarFlashcards(conteudoId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                flashcards.value = withContext(Dispatchers.IO) { api.listarFlashcards(conteudoId).body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar flashcards: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun carregarQuizzes() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                quizzes.value = withContext(Dispatchers.IO) { api.listarQuizzesAcademia().body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar quizzes: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun carregarHistorico() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                historico.value = withContext(Dispatchers.IO) { api.meuHistoricoAcademia().body() ?: emptyList() }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar historico: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun responderQuiz(quizId: Int, respostas: Map<Int, Int>, onResult: (AcademiaResposta?) -> Unit) {
        viewModelScope.launch {
            try {
                val body = mapOf<String, Any>("respostas" to respostas)
                val resp = withContext(Dispatchers.IO) { api.responderQuizAcademia(quizId, body) }
                onResult(resp.body())
            } catch (e: Exception) {
                erro.value = "Erro ao enviar respostas: ${e.message}"
                onResult(null)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Composable principal — usado no MeuPainelScreen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun AcademiaTab(vm: AcademiaViewModel = viewModel()) {
    var subAba by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Conteúdos", "Salas", "Flashcards", "Quizzes", "Histórico")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = subAba,
            containerColor = Gray50,
            contentColor = Blue600,
            edgePadding = 0.dp,
            divider = { HorizontalDivider(color = Gray200) },
        ) {
            subTabs.forEachIndexed { i, label ->
                Tab(selected = subAba == i, onClick = { subAba = i }) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        fontSize = 12.sp,
                        fontWeight = if (subAba == i) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        when (subAba) {
            0 -> ConteudosContent(vm)
            1 -> SalasDeAulaContent(vm)
            2 -> FlashcardsContent(vm)
            3 -> QuizzesPendentesContent(vm)
            4 -> MeuHistoricoContent(vm)
        }
    }
}

/** Tela standalone para o NavHost (rota "academia") */
@Composable
fun AcademiaScreen(vm: AcademiaViewModel = viewModel()) {
    AcademiaTab(vm)
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab 0 — Conteúdos (todos os materiais de estudo)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConteudosContent(vm: AcademiaViewModel) {
    val conteudos by vm.conteudos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var expandido by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { vm.carregarConteudos() }

    if (isLoading && conteudos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue600)
        }
        return
    }

    if (conteudos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nenhum conteúdo disponível.", color = Gray500, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(conteudos, key = { it.id }) { c ->
            val isExpanded = expandido == c.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
                modifier = Modifier.clickable { expandido = if (isExpanded) null else c.id },
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gray900)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                if (!c.categoria.isNullOrBlank()) {
                                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFDBEAFE)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(c.categoria, fontSize = 10.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Medium)
                                    }
                                }
                                Box(Modifier.clip(RoundedCornerShape(50)).background(
                                    when (c.tipo) { "DOCUMENTO" -> Color(0xFFFED7AA); "VIDEO" -> Color(0xFFE9D5FF); else -> Color(0xFFF3F4F6) }
                                ).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Medium, color =
                                        when (c.tipo) { "DOCUMENTO" -> Color(0xFFEA580C); "VIDEO" -> Color(0xFF7C3AED); else -> Gray500 }
                                    )
                                }
                            }
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null, tint = Gray500,
                        )
                    }

                    // Conteúdo expandido
                    if (isExpanded) {
                        HorizontalDivider(color = Gray200)
                        Column(Modifier.padding(16.dp)) {
                            Text(c.conteudo, fontSize = 13.sp, color = Gray700, lineHeight = 20.sp)
                            if (!c.urlArquivo.isNullOrBlank()) {
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "📎 Abrir arquivo anexo",
                                    fontSize = 12.sp,
                                    color = Blue600,
                                    modifier = Modifier.clickable {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.urlArquivo)))
                                    },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Por ${c.criadoPorNome}", fontSize = 11.sp, color = Gray500)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab 1 — Salas de Aula
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SalasDeAulaContent(vm: AcademiaViewModel) {
    val salas by vm.salas.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()

    LaunchedEffect(Unit) { vm.carregarSalas() }

    if (isLoading && salas.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue600)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (erro != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Red100), shape = RoundedCornerShape(10.dp)) {
                    Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                }
            }
        }

        if (salas.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhuma sala de aula disponivel.", color = Gray500, fontSize = 14.sp)
                }
            }
        }

        items(salas, key = { it.id }) { sala ->
            SalaCard(sala)
        }
    }
}

@Composable
private fun SalaCard(sala: AcademiaSala) {
    var expandido by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
        modifier = Modifier.clickable { expandido = !expandido },
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sala.nome, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gray900)
                    if (!sala.descricao.isNullOrBlank()) {
                        Text(sala.descricao, fontSize = 12.sp, color = Gray500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val total = sala.conteudos?.size ?: 0
                    Text("$total conteudo${if (total != 1) "s" else ""}", fontSize = 11.sp, color = Blue600)
                }
                Icon(
                    if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = Gray500,
                )
            }

            if (expandido && !sala.conteudos.isNullOrEmpty()) {
                HorizontalDivider(color = Gray200)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sala.conteudos.forEach { conteudo ->
                        ConteudoItem(conteudo)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConteudoItem(conteudo: AcademiaConteudo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (conteudo.tipo) {
                "VIDEO" -> Icons.Default.PlayCircle
                "DOC" -> Icons.Default.Description
                else -> Icons.Default.Article
            },
            contentDescription = null,
            tint = Blue600,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(conteudo.titulo, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray900)
            if (!conteudo.categoria.isNullOrBlank()) {
                Text(conteudo.categoria, fontSize = 11.sp, color = Gray500)
            }
        }
        TipoBadge(conteudo.tipo)
    }
}

@Composable
private fun TipoBadge(tipo: String) {
    val (bg, fg, label) = when (tipo) {
        "VIDEO" -> Triple(Color(0xFFE0E7FF), Indigo600, "VIDEO")
        "DOC" -> Triple(Color(0xFFFEF3C7), Amber500, "DOC")
        else -> Triple(Blue100, Blue600, "TEXTO")
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 9.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab 2 — Flashcards
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FlashcardsContent(vm: AcademiaViewModel) {
    val conteudos by vm.conteudos.collectAsState()
    val flashcards by vm.flashcards.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()

    var conteudoSelecionado by remember { mutableStateOf<AcademiaConteudo?>(null) }

    LaunchedEffect(Unit) { vm.carregarConteudos() }

    if (conteudoSelecionado == null) {
        // Lista de conteudos para escolher
        if (isLoading && conteudos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (erro != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Red100), shape = RoundedCornerShape(10.dp)) {
                        Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                    }
                }
            }

            item {
                Text("Escolha um conteudo para estudar:", fontSize = 14.sp, color = Gray700, fontWeight = FontWeight.Medium)
            }

            if (conteudos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum conteudo disponivel.", color = Gray500, fontSize = 14.sp)
                    }
                }
            }

            items(conteudos, key = { it.id }) { c ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
                    modifier = Modifier.clickable {
                        conteudoSelecionado = c
                        vm.carregarFlashcards(c.id)
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = Blue600, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.titulo, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Gray900)
                            if (!c.categoria.isNullOrBlank()) {
                                Text(c.categoria, fontSize = 11.sp, color = Gray500)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Gray500)
                    }
                }
            }
        }
    } else {
        // Flashcard UI
        FlashcardViewer(
            conteudoTitulo = conteudoSelecionado!!.titulo,
            flashcards = flashcards,
            isLoading = isLoading,
            onVoltar = { conteudoSelecionado = null; vm.flashcards.value = emptyList() },
        )
    }
}

@Composable
private fun FlashcardViewer(
    conteudoTitulo: String,
    flashcards: List<AcademiaFlashcard>,
    isLoading: Boolean,
    onVoltar: () -> Unit,
) {
    var indice by remember { mutableIntStateOf(0) }
    var mostrarResposta by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onVoltar) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Gray700)
            }
            Text(conteudoTitulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
            return
        }

        if (flashcards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum flashcard disponivel.", color = Gray500, fontSize = 14.sp)
            }
            return
        }

        val card = flashcards[indice]

        // Contador
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "${indice + 1}/${flashcards.size}",
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Blue600,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Card com flip
        val rotacao by animateFloatAsState(
            targetValue = if (mostrarResposta) 180f else 0f,
            animationSpec = tween(400),
            label = "flip",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    rotationY = rotacao
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(20.dp))
                .background(if (mostrarResposta) Color(0xFFEFF6FF) else Color.White)
                .border(2.dp, if (mostrarResposta) Blue200 else Gray200, RoundedCornerShape(20.dp))
                .clickable { mostrarResposta = !mostrarResposta }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (rotacao <= 90f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PERGUNTA", fontSize = 11.sp, color = Blue600, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(card.pergunta, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Gray900, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Toque para ver a resposta", fontSize = 11.sp, color = Gray500)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.graphicsLayer { rotationY = 180f },
                ) {
                    Text("RESPOSTA", fontSize = 11.sp, color = Green600, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(card.resposta, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Gray900, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Toque para voltar", fontSize = 11.sp, color = Gray500)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Navegacao
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    if (indice > 0) { indice--; mostrarResposta = false }
                },
                enabled = indice > 0,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (indice > 0) Blue200 else Gray200),
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Anterior", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = {
                    if (indice < flashcards.size - 1) { indice++; mostrarResposta = false }
                },
                enabled = indice < flashcards.size - 1,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (indice < flashcards.size - 1) Blue200 else Gray200),
            ) {
                Text("Proximo", fontSize = 13.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab 3 — Quizzes Pendentes
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuizzesPendentesContent(vm: AcademiaViewModel) {
    val quizzes by vm.quizzes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()

    var quizAtivo by remember { mutableStateOf<AcademiaQuiz?>(null) }
    var resultado by remember { mutableStateOf<AcademiaResposta?>(null) }

    LaunchedEffect(Unit) { vm.carregarQuizzes() }

    if (resultado != null) {
        ResultadoQuizScreen(
            resultado = resultado!!,
            quiz = quizAtivo,
            onVoltar = { resultado = null; quizAtivo = null; vm.carregarQuizzes() },
        )
        return
    }

    if (quizAtivo != null) {
        QuizPlayerScreen(
            quiz = quizAtivo!!,
            vm = vm,
            onResultado = { resultado = it },
            onVoltar = { quizAtivo = null },
        )
        return
    }

    if (isLoading && quizzes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue600)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (erro != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Red100), shape = RoundedCornerShape(10.dp)) {
                    Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                }
            }
        }

        if (quizzes.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum quiz disponivel.", color = Gray500, fontSize = 14.sp)
                }
            }
        }

        items(quizzes, key = { it.id }) { quiz ->
            QuizCard(quiz = quiz, onClick = { quizAtivo = quiz })
        }
    }
}

@Composable
private fun QuizCard(quiz: AcademiaQuiz, onClick: () -> Unit) {
    val expirado = quiz.prazo?.let {
        try { LocalDate.parse(it.take(10)).isBefore(LocalDate.now()) } catch (_: Exception) { false }
    } ?: false

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expirado) Red300 else Gray200),
        modifier = Modifier.clickable(enabled = !expirado) { onClick() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(quiz.titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
                if (expirado) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Red100).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("Encerrado", fontSize = 10.sp, color = Red500, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Blue100).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(quiz.nivel, fontSize = 10.sp, color = Blue600, fontWeight = FontWeight.Medium)
                }
                Text("${quiz.perguntas.size} perguntas", fontSize = 11.sp, color = Gray500)
                if (!quiz.prazo.isNullOrBlank()) {
                    val prazoFmt = try { "${quiz.prazo.substring(8, 10)}/${quiz.prazo.substring(5, 7)}/${quiz.prazo.substring(0, 4)}" } catch (_: Exception) { quiz.prazo }
                    Text("Prazo: $prazoFmt", fontSize = 11.sp, color = if (expirado) Red500 else Gray500)
                }
            }

            if (quiz.criadoPorNome.isNotBlank()) {
                Text("Por: ${quiz.criadoPorNome}", fontSize = 11.sp, color = Gray500)
            }

            if (!expirado) {
                Button(
                    onClick = { onClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Text("Iniciar Quiz", fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Quiz Player ──────────────────────────────────────────────────────────────

@Composable
private fun QuizPlayerScreen(
    quiz: AcademiaQuiz,
    vm: AcademiaViewModel,
    onResultado: (AcademiaResposta?) -> Unit,
    onVoltar: () -> Unit,
) {
    var indice by remember { mutableIntStateOf(0) }
    val respostas = remember { mutableStateMapOf<Int, Int>() }
    var enviando by remember { mutableStateOf(false) }

    val pergunta = quiz.perguntas.getOrNull(indice)
    val total = quiz.perguntas.size

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onVoltar) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Gray700)
            }
            Text(quiz.titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
            Text("${indice + 1}/$total", fontSize = 13.sp, color = Blue600, fontWeight = FontWeight.Medium)
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (indice + 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Blue600,
            trackColor = Gray200,
        )

        if (pergunta == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Quiz sem perguntas.", color = Gray500)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(pergunta.pergunta, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Gray900)
            }

            itemsIndexed(pergunta.alternativas) { altIdx, alt ->
                val selecionada = respostas[indice] == altIdx
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selecionada) Blue50 else Color.White,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selecionada) 2.dp else 1.dp,
                        if (selecionada) Blue600 else Gray200,
                    ),
                    modifier = Modifier.clickable { respostas[indice] = altIdx },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(
                            selected = selecionada,
                            onClick = { respostas[indice] = altIdx },
                            colors = RadioButtonDefaults.colors(selectedColor = Blue600),
                        )
                        Text(alt, fontSize = 14.sp, color = Gray900)
                    }
                }
            }
        }

        // Navegacao
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (indice > 0) {
                OutlinedButton(
                    onClick = { indice-- },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Anterior", fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            if (indice < total - 1) {
                Button(
                    onClick = { indice++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Proxima", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = {
                        enviando = true
                        vm.responderQuiz(quiz.id, respostas.toMap()) { resp ->
                            enviando = false
                            onResultado(resp)
                        }
                    },
                    enabled = !enviando && respostas.size == total,
                    colors = ButtonDefaults.buttonColors(containerColor = Green600),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Enviar Respostas", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Resultado do Quiz ────────────────────────────────────────────────────────

@Composable
private fun ResultadoQuizScreen(
    resultado: AcademiaResposta,
    quiz: AcademiaQuiz?,
    onVoltar: () -> Unit,
) {
    val nota = resultado.acertos.toFloat() / resultado.total.coerceAtLeast(1) * 10f
    val corNota = when {
        nota >= 7f -> Green600
        nota >= 5f -> Amber500
        else -> Red500
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            if (nota >= 7f) Icons.Default.EmojiEvents else Icons.Default.Info,
            contentDescription = null,
            tint = corNota,
            modifier = Modifier.size(64.dp),
        )

        Text(
            if (nota >= 7f) "Parabens!" else if (nota >= 5f) "Quase la!" else "Estude mais!",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gray900,
        )

        Text("${resultado.acertos}/${resultado.total} acertos", fontSize = 18.sp, color = corNota, fontWeight = FontWeight.SemiBold)

        // Barra visual
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Gray50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resultado", fontSize = 13.sp, color = Gray500, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { resultado.acertos.toFloat() / resultado.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = corNota,
                    trackColor = Gray200,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Acertos: ${resultado.acertos}", fontSize = 12.sp, color = Green600)
                    Text("Erros: ${resultado.total - resultado.acertos}", fontSize = 12.sp, color = Red500)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onVoltar,
            colors = ButtonDefaults.buttonColors(containerColor = Blue600),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text("Voltar para Quizzes", fontSize = 14.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab 4 — Meu Historico
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MeuHistoricoContent(vm: AcademiaViewModel) {
    val historico by vm.historico.collectAsState()
    val quizzes by vm.quizzes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()

    LaunchedEffect(Unit) {
        vm.carregarHistorico()
        vm.carregarQuizzes()
    }

    if (isLoading && historico.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue600)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (erro != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Red100), shape = RoundedCornerShape(10.dp)) {
                    Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                }
            }
        }

        if (historico.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum quiz respondido ainda.", color = Gray500, fontSize = 14.sp)
                }
            }
        }

        items(historico, key = { it.id }) { resp ->
            HistoricoCard(resp, quizzes)
        }
    }
}

@Composable
private fun HistoricoCard(resp: AcademiaResposta, quizzes: List<AcademiaQuiz>) {
    val quiz = quizzes.find { it.id == resp.quizId }
    val nota = resp.acertos.toFloat() / resp.total.coerceAtLeast(1) * 10f
    val corNota = when {
        nota >= 7f -> Green600
        nota >= 5f -> Amber500
        else -> Red500
    }

    val dataFmt = try {
        "${resp.createdAt.substring(8, 10)}/${resp.createdAt.substring(5, 7)}/${resp.createdAt.substring(0, 4)}"
    } catch (_: Exception) { resp.createdAt }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    quiz?.titulo ?: "Quiz #${resp.quizId}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900,
                )
                Text(dataFmt, fontSize = 11.sp, color = Gray500)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${resp.acertos}/${resp.total}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = corNota,
                )
                Text(
                    "${"%.1f".format(nota)}",
                    fontSize = 11.sp, color = corNota, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

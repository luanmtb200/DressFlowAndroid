package com.mrjack.dressflow.ui.screens.whatsapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.GaleriaAlbum
import com.mrjack.dressflow.data.model.GaleriaFoto
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun GaleriaModal(
    chatId: String,
    onDismiss: () -> Unit,
    onEnviar: (List<GaleriaFoto>) -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val api = remember { NetworkModule.provideApiService(app) }

    var albums    by remember { mutableStateOf<List<GaleriaAlbum>>(emptyList()) }
    var fotos     by remember { mutableStateOf<List<GaleriaFoto>>(emptyList()) }
    var albumSel  by remember { mutableStateOf<String?>(null) }
    var busca     by remember { mutableStateOf("") }
    var page      by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading   by remember { mutableStateOf(true) }
    val selecionadas = remember { mutableStateListOf<GaleriaFoto>() }

    // Debounced search key: increments when busca or albumSel changes from user interaction
    var searchTrigger by remember { mutableIntStateOf(0) }

    // Load albums once on open
    LaunchedEffect(Unit) {
        try { albums = api.listarAlbunsGaleria().body() ?: emptyList() } catch (_: Exception) {}
    }

    // Debounced fetch: fires on searchTrigger change; first fire (trigger=0) loads immediately
    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0) delay(300)
        loading = true
        try {
            val resp = api.listarFotosGaleria(page = page, album = albumSel, q = busca.ifBlank { null }).body()
            fotos = resp?.fotos ?: emptyList()
            totalPages = resp?.pages ?: 1
        } catch (_: Exception) {}
        loading = false
    }

    fun mudarBusca(q: String) {
        busca = q
        page = 1
        searchTrigger++
    }

    fun mudarAlbum(album: String?) {
        albumSel = album
        page = 1
        searchTrigger++
    }

    fun mudarPagina(p: Int) {
        page = p
        searchTrigger++
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color.White,
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Surface(shadowElevation = 2.dp, color = Color(0xFF128C7E)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                            Text(
                                if (selecionadas.isEmpty()) "Galeria DressFlow"
                                else "${selecionadas.size} foto(s) selecionada(s)",
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                color = Color.White, modifier = Modifier.weight(1f),
                            )
                            if (selecionadas.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        onEnviar(selecionadas.toList())
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.Default.Send, null, tint = Color(0xFF128C7E), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Enviar ${selecionadas.size}",
                                        color = Color(0xFF128C7E), fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        // Busca
                        OutlinedTextField(
                            value = busca,
                            onValueChange = { mudarBusca(it) },
                            placeholder = { Text("Buscar foto...", fontSize = 13.sp, color = Color.White.copy(.6f)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(.8f), modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
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
                        // Album chips
                        if (albums.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = if (albumSel == null) Color.White else Color.White.copy(.25f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.clickable { mudarAlbum(null) },
                                ) {
                                    Text(
                                        "Todos", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        color = if (albumSel == null) Color(0xFF128C7E) else Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                                albums.forEach { a ->
                                    val sel = albumSel == a.album
                                    Surface(
                                        color = if (sel) Color.White else Color.White.copy(.25f),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.clickable { mudarAlbum(if (sel) null else a.album) },
                                    ) {
                                        Text(
                                            a.album, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                            color = if (sel) Color(0xFF128C7E) else Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grid de fotos
                when {
                    loading -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Color(0xFF25D366)) }
                    fotos.isEmpty() -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) { Text("Nenhuma foto encontrada", color = Gray500, fontSize = 14.sp) }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(fotos, key = { it.id }) { foto ->
                            val isSel = selecionadas.any { it.id == foto.id }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .clickable {
                                        if (isSel) selecionadas.removeAll { it.id == foto.id }
                                        else selecionadas.add(foto)
                                    },
                            ) {
                                AsyncImage(
                                    model = foto.url,
                                    contentDescription = foto.nome,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                if (isSel) {
                                    Box(Modifier.fillMaxSize().background(Color(0xFF128C7E).copy(.4f)))
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                                            .size(22.dp).clip(CircleShape)
                                            .background(Color(0xFF128C7E)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Check, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Paginação
                if (!loading && totalPages > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { mudarPagina(page - 1) }, enabled = page > 1) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                        Text("Página $page de $totalPages", fontSize = 13.sp, color = Gray700)
                        IconButton(onClick = { mudarPagina(page + 1) }, enabled = page < totalPages) {
                            Icon(Icons.Default.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }
}

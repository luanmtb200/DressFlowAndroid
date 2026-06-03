package com.mrjack.dressflow.data.api

import com.mrjack.dressflow.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ── Locações ──────────────────────────────────────────────────────────────
    @GET("locacoes")
    suspend fun listarLocacoes(
        @Query("status") status: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("busca") busca: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Locacao>>

    @GET("locacoes/{id}")
    suspend fun buscarLocacao(@Path("id") id: Int): Response<Locacao>

    @GET("locacoes/cliente/{clienteId}")
    suspend fun locacoesPorCliente(@Path("clienteId") clienteId: Int): Response<List<Locacao>>

    @GET("locacoes/vendas-dia")
    suspend fun vendasDia(): Response<VendasDia>

    @GET("locacoes/devolucoes-pendentes")
    suspend fun devolucoesPendentes(): Response<List<Locacao>>

    @GET("locacoes/orcamentos")
    suspend fun orcamentosAtivos(): Response<List<Locacao>>

    @GET("locacoes/listagem")
    suspend fun listagem(
        @Query("inicio") inicio: String,
        @Query("fim") fim: String,
    ): Response<List<Locacao>>

    @POST("locacoes")
    suspend fun criarLocacao(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Locacao>

    @PUT("locacoes/{id}")
    suspend fun atualizarLocacao(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Locacao>

    @PATCH("locacoes/{id}/converter")
    suspend fun converterOrcamento(@Path("id") id: Int): Response<Locacao>

    @PATCH("locacoes/{id}/anotacoes")
    suspend fun atualizarAnotacoes(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards String?>): Response<Locacao>

    @PATCH("locacoes/{id}/devolver")
    suspend fun devolver(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Locacao>

    @PATCH("locacoes/{id}/solicitar-cancelamento")
    suspend fun solicitarCancelamento(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards String?>): Response<Locacao>

    // ── Clientes ──────────────────────────────────────────────────────────────
    @GET("clientes")
    suspend fun listarClientes(
        @Query("busca") busca: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ClientesPage>

    @GET("clientes/{id}")
    suspend fun buscarCliente(@Path("id") id: Int): Response<Cliente>

    @POST("clientes")
    suspend fun criarCliente(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Cliente>

    @PUT("clientes/{id}")
    suspend fun atualizarCliente(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Cliente>

    // ── Agendamentos ─────────────────────────────────────────────────────────
    @GET("agendamentos")
    suspend fun listarAgendamentos(
        @Query("inicio") inicio: String? = null,
        @Query("fim") fim: String? = null,
    ): Response<List<Agendamento>>

    @POST("agendamentos")
    suspend fun criarAgendamento(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Agendamento>

    @PATCH("agendamentos/{id}")
    suspend fun atualizarAgendamento(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Agendamento>

    @DELETE("agendamentos/{id}")
    suspend fun deletarAgendamento(@Path("id") id: Int): Response<Unit>

    // ── Padronizações ─────────────────────────────────────────────────────────
    @GET("padronizacoes")
    suspend fun listarPadronizacoes(
        @Query("status") status: String? = null,
        @Query("busca") busca: String? = null,
    ): Response<List<Padronizacao>>

    @GET("padronizacoes/{id}")
    suspend fun buscarPadronizacao(@Path("id") id: Int): Response<Padronizacao>

    @POST("padronizacoes")
    suspend fun criarPadronizacao(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Padronizacao>

    @PATCH("padronizacoes/{id}")
    suspend fun atualizarPadronizacao(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Padronizacao>

    @GET("padronizacoes/{id}/pdf")
    suspend fun baixarPdfPadronizacao(@Path("id") id: Int): Response<okhttp3.ResponseBody>

    // ── Trajes ────────────────────────────────────────────────────────────────
    @GET("trajes/buscar/{codigo}")
    suspend fun buscarTrajePorCodigo(@Path("codigo") codigo: String): Response<Traje>

    // ── Trajes de Padronização ────────────────────────────────────────────────
    @GET("trajes-padronizacao")
    suspend fun listarTrajesPadronizacao(): Response<List<TrajePadronizacao>>

    @POST("trajes-padronizacao")
    suspend fun criarTrajePadronizacao(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<TrajePadronizacao>

    @PATCH("trajes-padronizacao/{id}")
    suspend fun atualizarTrajePadronizacao(@Path("id") id: Int, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<TrajePadronizacao>

    @DELETE("trajes-padronizacao/{id}")
    suspend fun deletarTrajePadronizacao(@Path("id") id: Int): Response<Unit>

    // ── Mural ─────────────────────────────────────────────────────────────────
    @GET("mural/canais")
    suspend fun listarCanais(): Response<List<MuralCanal>>

    @GET("mural/canais/{id}/mensagens")
    suspend fun listarMensagens(
        @Path("id") canalId: Int,
        @Query("before") before: String? = null,
    ): Response<List<MuralMensagem>>

    @POST("mural/canais/{id}/mensagens")
    suspend fun enviarMensagem(@Path("id") canalId: Int, @Body body: Map<String, String>): Response<MuralMensagem>

    @POST("mural/canais/{id}/lido")
    suspend fun marcarLido(@Path("id") canalId: Int): Response<Any>

    @GET("mural/usuarios")
    suspend fun listarUsuariosMural(): Response<List<Vendedor>>

    @POST("mural/dm/{outroId}")
    suspend fun criarDm(@Path("outroId") outroId: Int): Response<MuralCanal>

    // Chat Secreto
    @POST("mural/secreto/convidar")
    suspend fun convidarSecreto(@Body body: Map<String, Int>): Response<MuralCanal>

    @POST("mural/secreto/{canalId}/aceitar")
    suspend fun aceitarSecreto(@Path("canalId") canalId: Int): Response<MuralCanal>

    @POST("mural/secreto/{canalId}/recusar")
    suspend fun recusarSecreto(@Path("canalId") canalId: Int): Response<Any>

    @DELETE("mural/secreto/{canalId}/encerrar")
    suspend fun encerrarSecreto(@Path("canalId") canalId: Int): Response<Any>

    // ── WhatsApp ──────────────────────────────────────────────────────────────
    @GET("whatsapp/status")
    suspend fun statusWhatsApp(): Response<WhatsAppStatus>

    @GET("whatsapp/chats")
    suspend fun listarChats(): Response<List<WaChat>>

    @GET("whatsapp/mensagens/{chatId}")
    suspend fun listarMensagensWa(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 50,
    ): Response<List<WaMensagem>>

    @POST("whatsapp/enviar")
    suspend fun enviarMensagemWa(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Any>

    @POST("whatsapp/enviar-midia")
    suspend fun enviarMidiaWa(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Any>

    @POST("whatsapp/reagir")
    suspend fun reagirWa(@Body body: Map<String, String>): Response<Any>

    @POST("whatsapp/encaminhar")
    suspend fun encaminharWa(@Body body: Map<String, String>): Response<Any>

    @POST("whatsapp/fixar")
    suspend fun fixarWa(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Any>

    @POST("whatsapp/favoritar")
    suspend fun favoritarWa(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Any>

    @POST("whatsapp/marcar-nao-lida")
    suspend fun marcarNaoLidaWa(@Body body: Map<String, String>): Response<Any>

    @POST("whatsapp/enviar-audio")
    suspend fun enviarAudioWa(@Body body: Map<String, String>): Response<Any>

    @POST("whatsapp/excluir")
    suspend fun excluirWa(@Body body: Map<String, String>): Response<Any>

    @POST("whatsapp/editar")
    suspend fun editarWa(@Body body: Map<String, String>): Response<Any>

    @GET("whatsapp/labels/map")
    suspend fun listarLabels(): Response<Map<String, List<EtiquetaWa>>>

    @GET("whatsapp/labels")
    suspend fun listarAllLabels(): Response<List<EtiquetaWa>>

    @GET("whatsapp/labels/chat/{chatId}")
    suspend fun listarLabelsChat(@Path("chatId", encoded = true) chatId: String): Response<List<EtiquetaWa>>

    @POST("whatsapp/labels/chat/{chatId}/{labelId}")
    suspend fun adicionarLabel(
        @Path("chatId", encoded = true) chatId: String,
        @Path("labelId") labelId: String,
    ): Response<Any>

    @DELETE("whatsapp/labels/chat/{chatId}/{labelId}")
    suspend fun removerLabel(
        @Path("chatId", encoded = true) chatId: String,
        @Path("labelId") labelId: String,
    ): Response<Any>

    @GET("whatsapp/foto-chat/{chatId}")
    suspend fun fotoChat(@Path("chatId", encoded = true) chatId: String): Response<FotoChatResponse>

    @POST("whatsapp/enviar-url")
    suspend fun enviarUrlWa(@Body body: Map<String, @JvmSuppressWildcards String?>): Response<Any>

    // ── Galeria ───────────────────────────────────────────────────────────────
    @GET("galeria/albums")
    suspend fun listarAlbunsGaleria(): Response<List<GaleriaAlbum>>

    @GET("galeria")
    suspend fun listarFotosGaleria(
        @Query("page") page: Int = 1,
        @Query("album") album: String? = null,
        @Query("q") q: String? = null,
    ): Response<GaleriaPage>

    // ── Vendedores ────────────────────────────────────────────────────────────
    @GET("vendedores")
    suspend fun listarVendedores(): Response<List<Vendedor>>

    // ── Painel do vendedor ────────────────────────────────────────────────────
    @GET("locacoes/vendedor/{vendedorId}")
    suspend fun locacoesDoVendedor(
        @Path("vendedorId") vendedorId: Int,
        @Query("mes") mes: String? = null,
    ): Response<List<Locacao>>

    @GET("locacoes/vendedor/{vendedorId}/orcamentos")
    suspend fun orcamentosDoVendedor(
        @Path("vendedorId") vendedorId: Int,
    ): Response<List<Locacao>>

    @GET("locacoes/vendedor/{vendedorId}/orcamentos/cancelados")
    suspend fun orcamentosCanceladosDoVendedor(
        @Path("vendedorId") vendedorId: Int,
    ): Response<List<Locacao>>

    // ── Relatórios de Vendas ──────────────────────────────────────────────────
    @GET("locacoes/vendas-dia")
    suspend fun listarVendasDiaPorData(@Query("data") data: String): Response<List<Locacao>>

    @GET("locacoes/vendas-mes")
    suspend fun listarVendasMes(@Query("mes") mes: String): Response<VendasMesData>

    @GET("relatorios/financeiro")
    suspend fun financeiroResumo(@Query("mes") mes: String): Response<FinanceiroResumo>

    // ── Config Opções (formas pagamento / tipos cliente por loja) ─────────────
    @GET("config-opcoes")
    suspend fun listarConfigOpcoes(@Query("categoria") categoria: String): Response<List<com.mrjack.dressflow.data.model.ConfigOpcao>>

    @POST("config-opcoes")
    suspend fun criarConfigOpcao(@Body body: Map<String, String>): Response<com.mrjack.dressflow.data.model.ConfigOpcao>
}

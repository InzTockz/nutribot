package com.battilana.nutribot.service;

import com.battilana.nutribot.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** HU06: validacion de formato antes de indexar (nada llega a Qdrant ni a MySQL si el archivo no es valido). */
@DisplayName("IngestionService — validación de formato")
class IngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeDocumentRepository repo = mock(KnowledgeDocumentRepository.class);
    private final IngestionService service = new IngestionService(vectorStore, repo);

    @Test
    void rechazaFormatosNoPermitidos() {
        MockMultipartFile exe = new MockMultipartFile("file", "programa.exe", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.ingest(exe))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Formato no permitido");
        verifyNoInteractions(vectorStore, repo);
    }

    @Test
    void rechazaArchivosSinExtension() {
        MockMultipartFile sinExt = new MockMultipartFile("file", "documento", "text/plain", "hola".getBytes());
        assertThatThrownBy(() -> service.ingest(sinExt)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(vectorStore, repo);
    }

    @Test
    void rechazaArchivosVacios() {
        MockMultipartFile vacio = new MockMultipartFile("file", "faq.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.ingest(vacio))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("vacio");
        verifyNoInteractions(vectorStore, repo);
    }
}

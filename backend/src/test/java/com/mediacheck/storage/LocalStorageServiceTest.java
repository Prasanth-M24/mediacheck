package com.mediacheck.storage;

import com.mediacheck.config.ServiceProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {
  @TempDir Path temp;

  @Test
  void storesAtomicallyAndReturnsSha256Metadata() throws Exception {
    LocalStorageService service = new LocalStorageService(properties());
    UUID company = UUID.randomUUID(); UUID file = UUID.randomUUID(); byte[] content = "rights-data".getBytes(StandardCharsets.UTF_8);
    StoredFile stored = service.store("rights-assets", company, file, "txt", new ByteArrayInputStream(content), content.length);
    assertThat(stored.fileSize()).isEqualTo(content.length);
    assertThat(stored.sha256Checksum()).isEqualTo("7a44407fe1610b5754a2cc7410aeb2723178debe03d9a408fef09ae17a5c7ffc");
    assertThat(service.retrieve(stored.storageKey()).readAllBytes()).containsExactly(content);
  }

  @Test
  void rejectsEmptyAndTraversalKeys() {
    LocalStorageService service = new LocalStorageService(properties());
    assertThatThrownBy(() -> service.store("rights-assets", UUID.randomUUID(), UUID.randomUUID(), "bin", new ByteArrayInputStream(new byte[0]), 0)).isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> service.retrieve("../secret")).isInstanceOf(StorageException.class);
  }

  private ServiceProperties properties() { return new ServiceProperties("test", "http://ai", new ServiceProperties.Storage("local", temp.toString(), new ServiceProperties.B2(false, "", "", ""))); }
}

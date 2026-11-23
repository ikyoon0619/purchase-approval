package com.example.purchase.request;

import com.example.purchase.common.file.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** DB와 파일은 한 트랜잭션이 아니다: 롤백되면 방금 쓴 파일을 지우고, 삭제는 커밋된 뒤에만 */
@SpringBootTest
@ActiveProfiles("test")
class FileStorageTest {

    @Autowired FileStorage fileStorage;
    @Autowired TransactionTemplate tx;

    MockMultipartFile file = new MockMultipartFile("files", "견적서.pdf", "application/pdf", new byte[]{1, 2, 3});

    @Test
    @DisplayName("트랜잭션이 롤백되면 저장했던 파일도 지운다 (고아 파일 방지)")
    void rollbackDeletesStoredFile() {
        AtomicReference<String> stored = new AtomicReference<>();
        tx.executeWithoutResult(status -> {
            stored.set(fileStorage.store(file).storedName());
            assertThat(fileStorage.exists(stored.get())).isTrue();
            status.setRollbackOnly(); // 이후 DB 작업이 실패했다고 치자
        });
        assertThat(fileStorage.exists(stored.get())).isFalse();
    }

    @Test
    @DisplayName("삭제 요청은 커밋된 뒤에 실행된다. 롤백되면 파일은 남는다")
    void deleteOnlyAfterCommit() {
        String name = tx.execute(s -> fileStorage.store(file).storedName());

        tx.executeWithoutResult(status -> {
            fileStorage.deleteAfterCommit(name);
            assertThat(fileStorage.exists(name)).isTrue(); // 아직 커밋 전
            status.setRollbackOnly();
        });
        assertThat(fileStorage.exists(name)).isTrue();

        tx.executeWithoutResult(s -> fileStorage.deleteAfterCommit(name));
        assertThat(fileStorage.exists(name)).isFalse();
    }
}

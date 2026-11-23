package com.example.purchase.common.file;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 첨부파일을 디스크에 저장한다. 위치는 app.upload-dir (기본 ./uploads, AWS에서는 EBS 볼륨이나 S3로 바꿀 자리).
 *
 * <p>DB와 파일은 한 트랜잭션으로 묶이지 않는다. 그래서 두 가지를 맞춰 준다.
 * <ul>
 *   <li>저장: 파일을 먼저 쓰고, DB 트랜잭션이 <b>롤백되면</b> 방금 쓴 파일을 지운다 (고아 파일 방지)</li>
 *   <li>삭제: DB 트랜잭션이 <b>커밋된 뒤에</b> 파일을 지운다 (DB는 롤백됐는데 파일만 사라지는 일 방지)</li>
 * </ul>
 */
@Slf4j
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.upload-dir:./uploads}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("첨부파일 저장 위치: {}", root);
    }

    public record StoredFile(String originalName, String storedName, long size, String contentType) {
    }

    public StoredFile store(MultipartFile file) {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        String storedName = UUID.randomUUID() + (ext == null ? "" : "." + ext.toLowerCase());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_STORE_FAILED);
        }
        deleteIfRolledBack(storedName);
        return new StoredFile(original, storedName, file.getSize(), file.getContentType());
    }

    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    /** 트랜잭션 안이면 커밋 뒤에, 밖이면 바로 지운다 */
    public void deleteAfterCommit(String storedName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(storedName);
                }
            });
        } else {
            deleteQuietly(storedName);
        }
    }

    public boolean exists(String storedName) {
        return Files.exists(resolve(storedName));
    }

    private void deleteIfRolledBack(String storedName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deleteQuietly(storedName);
                    }
                }
            });
        }
    }

    private void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            log.warn("첨부파일을 지우지 못했습니다: {}", storedName, e);
        }
    }

    /** 저장 위치 밖을 가리키는 이름(../../etc/passwd 같은)은 거절한다 */
    private Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return path;
    }
}

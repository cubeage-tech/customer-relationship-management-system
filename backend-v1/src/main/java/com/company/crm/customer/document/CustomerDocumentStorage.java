package com.company.crm.customer.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class CustomerDocumentStorage {

    private final Path root;

    public CustomerDocumentStorage(
            @Value("${app.storage.customer-documents-dir:${user.home}/.crm/customer-documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public void store(String storageKey, MultipartFile file) {
        try {
            Files.createDirectories(root);
            Files.write(resolve(storageKey), file.getBytes(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the customer document", e);
        }
    }

    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the customer document", e);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not delete the customer document file", e);
        }
    }

    private Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid customer document storage key");
        }
        return path;
    }
}

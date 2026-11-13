package com.forensight.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import com.mongodb.client.gridfs.model.GridFSFile;

/** Stateless-container file storage backed by MongoDB GridFS. */
@Service
public class EvidenceFileStorageService {
    private static final String PREFIX = "gridfs:";
    private final GridFsTemplate gridFsTemplate;

    public EvidenceFileStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    public String save(InputStream input, String fileName, String contentType) throws IOException {
        if (input == null) throw new IOException("No file content was supplied.");
        ObjectId id = gridFsTemplate.store(input,
                fileName == null || fileName.isBlank() ? "evidence.bin" : fileName,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType);
        return PREFIX + id.toHexString();
    }

    public String save(Path path, String fileName, String contentType) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Generated artifact does not exist: " + path);
        }
        try (InputStream input = Files.newInputStream(path)) {
            return save(input, fileName, contentType);
        }
    }

    public byte[] readAllBytes(String reference) throws IOException {
        if (reference == null || reference.isBlank()) throw new IOException("File reference is empty.");
        if (!reference.startsWith(PREFIX)) {
            Path legacyPath = Paths.get(reference).toAbsolutePath().normalize();
            if (!Files.isRegularFile(legacyPath)) {
                throw new IOException("Legacy local file is no longer available: " + reference);
            }
            return Files.readAllBytes(legacyPath);
        }
        ObjectId id = parseId(reference);
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(id)));
        if (file == null) throw new IOException("GridFS object was not found: " + id.toHexString());
        GridFsResource resource = gridFsTemplate.getResource(file);
        try (InputStream input = resource.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    public Path materialize(String reference, String originalName) throws IOException {
        String suffix = ".bin";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String candidate = originalName.substring(dot);
                if (candidate.matches("\\.[A-Za-z0-9]{1,8}")) suffix = candidate;
            }
        }
        Path temp = Files.createTempFile("forensight-" + UUID.randomUUID() + "-", suffix);
        try {
            Files.write(temp, readAllBytes(reference));
            return temp;
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    public void delete(String reference) throws IOException {
        if (reference == null || reference.isBlank()) return;
        if (reference.startsWith(PREFIX)) {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(parseId(reference))));
            return;
        }
        Path legacyPath = Paths.get(reference).toAbsolutePath().normalize();
        if (Files.isRegularFile(legacyPath)) Files.deleteIfExists(legacyPath);
    }

    public void deleteTemporary(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { path.toFile().deleteOnExit(); }
    }

    private ObjectId parseId(String reference) throws IOException {
        String value = reference.substring(PREFIX.length());
        if (!ObjectId.isValid(value)) throw new IOException("Invalid GridFS file reference.");
        return new ObjectId(value);
    }
}

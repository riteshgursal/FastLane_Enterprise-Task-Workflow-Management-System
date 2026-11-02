package com.flowlane.attachment;

import com.flowlane.common.config.FlowlaneProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FlowlaneProperties props) throws IOException {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(String key, InputStream content) throws IOException {
        Files.copy(content, resolve(key), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (!key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Bad storage key");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Bad storage key");
        return p;
    }
}

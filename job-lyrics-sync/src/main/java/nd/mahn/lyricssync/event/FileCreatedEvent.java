package nd.mahn.lyricssync.event;

import lombok.Data;

import java.nio.file.Path;

@Data
public class FileCreatedEvent {
    private final Path file;
}

package nd.mahn.lyricssync.event;

import lombok.Data;

import java.nio.file.Path;

@Data
public class MetadataReadyEvent {
    private Path outputMetaData;
}

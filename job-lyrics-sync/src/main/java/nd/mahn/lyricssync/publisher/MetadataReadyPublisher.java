package nd.mahn.lyricssync.publisher;

import nd.mahn.lyricssync.event.MetadataReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class MetadataReadyPublisher {
    private final ApplicationEventPublisher publisher;

    public MetadataReadyPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishMetadataReadyDone(Path metaDir) {
        MetadataReadyEvent event = new MetadataReadyEvent();
        event.setOutputMetaData(metaDir);
        publisher.publishEvent(event);
    }
}

package nd.mahn.lyricssync.publisher;

import nd.mahn.lyricssync.event.FileCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class FileEventPublisher {
    private final ApplicationEventPublisher publisher;

    public FileEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishFileCreated(Path file) {
        publisher.publishEvent(new FileCreatedEvent(file));
    }
}

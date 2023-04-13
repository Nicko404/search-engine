package searchengine.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import searchengine.utils.DataSaver;

@Getter
@Setter
@Component
@RequiredArgsConstructor
public class SiteParserData {

    private final DataSaver dataSaver;
    private final ConnectionData connectionData;
    private final SitesList sitesList;
}

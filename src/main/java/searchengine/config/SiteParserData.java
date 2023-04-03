package searchengine.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

@Getter
@Setter
@Component
@RequiredArgsConstructor
public class SiteParserData {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ConnectionData connectionData;
    private final SitesList sitesList;
}

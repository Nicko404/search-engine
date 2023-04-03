package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteParserData;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.SiteParser;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    @Getter
    private static boolean indexingStarted = false;
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteParserData siteParserData;
    private final Map<ForkJoinPool, SiteParser> poolParserMap = new HashMap<>();
    private ScheduledExecutorService service;


    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (indexingStarted) {
            response.setResult(false);
            response.setError("Индексация уже запущена.");
            return response;
        }
        indexingStarted = true;
        for (searchengine.config.Site siteConf : sites.getSites()) {
            deleteAllSiteData(siteUrlToBaseForm(siteConf.getUrl()));
            ForkJoinPool pool = new ForkJoinPool();
            SiteParser parser = new SiteParser("", saveSite(siteConf), siteParserData);
            poolParserMap.put(pool, parser);
            pool.execute(parser);
        }
        response.setResult(true);
        startCheckForEndingIndexing();
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!indexingStarted) {
            response.setResult(false);
            response.setError("Индексация ещё не запущена.");
            return response;
        }
        indexingStarted = false;
        for (ForkJoinPool pool : new HashSet<>(poolParserMap.keySet())) {
            pool.shutdownNow();
            Site site = poolParserMap.get(pool).getSite();
            siteRepository.update(site, SiteStatus.FAILED, "Индексация остановлена пользователем");
            pageRepository.clearMap(site);
            poolParserMap.remove(pool);
        }
        service.shutdown();
        response.setResult(true);
        return response;
    }

    private Site saveSite(searchengine.config.Site siteConf) {
        Site site = new Site();
        site.setUrl(siteUrlToBaseForm(siteConf.getUrl()));
        site.setName(siteConf.getName());
        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        return siteRepository.save(site);
    }

    private String siteUrlToBaseForm(String url) {
        return url.replace("www.", "");
    }

    private void deleteAllSiteData(String url) {
        Site site = siteRepository.findByUrl(url);
        if (Objects.nonNull(site)) {
            pageRepository.removeBySite(site);
            siteRepository.remove(site);
        }
    }

    private void startCheckForEndingIndexing() {
        service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(() -> {
            for (ForkJoinPool pool : new HashSet<>(poolParserMap.keySet())) {
                SiteParser parser = poolParserMap.get(pool);
                if (parser.isCompletedNormally()) {
                    Site site = parser.getSite();
                    siteRepository.update(site, SiteStatus.INDEXED, site.getLastError());
                    pageRepository.clearMap(site);
                    poolParserMap.remove(pool);
                    continue;
                }
                if (parser.isCompletedAbnormally()) {
                    Site site = parser.getSite();
                    siteRepository.update(site, SiteStatus.FAILED, site.getLastError());
                    pageRepository.clearMap(site);
                    poolParserMap.remove(pool);
                }
            }
            if (poolParserMap.size() == 0) {
                indexingStarted = false;
                service.shutdown();
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);
    }

}

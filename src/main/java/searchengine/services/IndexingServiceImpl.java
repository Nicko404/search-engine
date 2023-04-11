package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.SiteParserData;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.Lemmatizer;
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
            SiteParser parser = new SiteParser("/", saveSite(siteConf), siteParserData);
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
            pool.shutdown();
            Site site = poolParserMap.get(pool).getSite();
            siteRepository.update(site, SiteStatus.FAILED, "Индексация остановлена пользователем");
            pageRepository.clearMap(site);
            poolParserMap.remove(pool);
        }
        service.shutdown();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        IndexingResponse response = new IndexingResponse();
        searchengine.config.Site siteConf = configContainsUrl(siteUrlToBaseForm(url));
        if (Objects.nonNull(siteConf)) {
            url = siteUrlToBaseForm(url);
            String path = url.substring(siteUrlToBaseForm(siteConf.getUrl()).length());
            Site site = siteRepository.findByUrl(url.substring(0, url.length() - path.length()));
            if (Objects.isNull(site)) site = saveSite(siteConf);
            Page page = pageRepository.findByPathAndSite(path, site);
            if (Objects.nonNull(page)) {
                List<Lemma> lemmas = siteParserData.getIndexRepository().removeByPage(page);
                lemmas.forEach(lemma -> siteParserData.getLemmaRepository().decrementFrequencyById(lemma.getId()));
                siteParserData.getLemmaRepository().removeIfFrequencyIsZero();
                pageRepository.removeByPathAndSite(path, site);
                pageRepository.removeFromMap(page);
            }
            SiteParser parser = new SiteParser(path, site, siteParserData);
            if (parser.indexPage()) {
                response.setResult(true);
                return response;
            }
            response.setResult(false);
            response.setError("Не удалось проиндексировать страницу");
        } else {
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле\n");
        }
        return response;
    }

    @Override
    public SearchResponse search(String site, String query) {
        Lemmatizer lemmatizer = new Lemmatizer();
        Set<String> queryLemmas = lemmatizer.lemmatize(query).keySet();
        Site siteObj = siteParserData.getSiteRepository().findByUrl(site);
        List<Lemma> lemmaList = siteParserData.getLemmaRepository().findByLemmaListAndSite(queryLemmas, siteObj);
        lemmaList.sort(Comparator.comparingInt(Lemma::getFrequency));
        List<Page> pageList;
        Set<Page> result = new HashSet<>();
        if (Objects.isNull(siteObj)) {
            pageList = siteParserData.getIndexRepository().findByLemmaStr(lemmaList.get(0)).stream()
                    .map(Index::getPage).toList();
            for (Lemma lemma : lemmaList) {
                result.clear();
                for (Page page : pageList) {
                    if (siteParserData.getIndexRepository().existsByLemmaStrAndPage(lemma.getLemma(), page)) {
                        result.add(page);
                    }
                }
                pageList = new ArrayList<>(result);
            }
        } else {
            pageList = siteParserData.getIndexRepository().findByLemmaAndSite(lemmaList.get(0), siteObj).stream()
                    .map(Index::getPage).toList();
            for (Lemma lemma : lemmaList) {
                result.clear();
                for (Page page : pageList) {
                    if (siteParserData.getIndexRepository().existsByLemmaIdAndPage(lemma, page)) {
                        result.add(page);
                    }
                }
                pageList = new ArrayList<>(result);
            }
        }
        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(result.size());
        response.setData(new ArrayList<>());
        for (Page page : result) {
            SearchData data = new SearchData();
            data.setUri(page.getPath());
            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            data.setTitle(selectTitle(page.getContent()));
            data.setSnippet(selectSnippet(page.getContent(), query.split("\\s")));
            data.setRelevance(calculateRelevance(page, lemmaList));
            response.getData().add(data);
        }
        response.getData().sort((d1, d2) -> (int) (d2.getRelevance() - d1.getRelevance()));
        return response;
    }

    private String selectTitle(String content) {
        Document document = Jsoup.parse(content);
        return document.title();
    }

    private String selectSnippet(String content, String[] lemmaList) {
        Document document = Jsoup.parse(content);
        Elements elements = document.getAllElements();
        for (String lemma : lemmaList) {
            String core = lemma.substring(0, lemma.length() / 2 + 1);
            for (Element element : elements) {
                String text = element.text();
                if (text.contains(core)) {
                    int word = text.indexOf(core);
                    int end = text.indexOf(".", word) + 1;
                    String begin = text.substring(0, word);
                    int start = begin.lastIndexOf(".") + 1;
                    return "<b>" + text.substring(start, end) + "</b>";
                }
            }
        }
       return null;
    }

    private float calculateRelevance(Page page, List<Lemma> lemmaList) {
        List<Index> indexList = siteParserData.getIndexRepository().findByPageAndLemmaList(page, lemmaList);
        float result = 0f;
        for (Index index : indexList) {
            result += index.getRank();
        }
        return result;
    }

    private searchengine.config.Site configContainsUrl(String url) {
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.startsWith(siteUrlToBaseForm(site.getUrl())))
                return site;
        }
        return null;
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
            siteParserData.getIndexRepository().removeBySite(site);
            siteParserData.getLemmaRepository().removeBySite(site);
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
package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.SiteParserData;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteParserData siteParserData;


    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (searchengine.config.Site siteConf : sites.getSites()) {
            DetailedStatisticsItem item;
            Optional<Site> siteOptional = siteParserData.getDataSaver()
                    .findSiteByUrl(IndexingServiceImpl.siteUrlToBaseForm(siteConf.getUrl()));
            item = siteOptional.map(this::createStatisticsItemFromDBSite).orElseGet(() -> createStatisticsItemFromSiteConf(siteConf));
            total.setPages(total.getPages() + item.getPages());
            total.setLemmas(total.getLemmas() + item.getLemmas());
            detailed.add(item);
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem createStatisticsItemFromDBSite(Site site) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        item.setPages(siteParserData.getDataSaver().countPageBySite(site));
        item.setLemmas(siteParserData.getDataSaver().countLemmaBySite(site));
        item.setStatus(site.getStatus().toString());
        item.setError(site.getLastError());
        item.setStatusTime(ZonedDateTime.of(site.getStatusTime(), ZoneId.systemDefault()).toInstant().toEpochMilli());
        return item;
    }

    private DetailedStatisticsItem createStatisticsItemFromSiteConf(searchengine.config.Site site) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(IndexingServiceImpl.siteUrlToBaseForm(site.getUrl()));
        item.setPages(0);
        item.setLemmas(0);
        item.setStatus("DON'T INDEXING");
        item.setError("");
        item.setStatusTime(ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault()).toInstant().toEpochMilli());
        return item;
    }
}

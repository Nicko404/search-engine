package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;

public interface IndexingService {

    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String url);

    SearchResponse search(String site, String query);
}

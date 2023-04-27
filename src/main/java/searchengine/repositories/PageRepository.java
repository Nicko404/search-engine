package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends CrudRepository<Page, Integer> {

    @Query(value = "from Page p join Index i on i.page = p join Lemma l on i.lemma = l where l.lemma = :lemma")
    Optional<List<Page>> findByLemma(String lemma);

    @Query(value = "from Page p join Index i on i.page = p join Lemma l on i.lemma = l " +
            "where l.lemma = :lemma and l.site = :site")
    Optional<List<Page>> findByLemmaAndSite(String lemma, Site site);

    Optional<Page> findByPathAndSite(String path, Site site);

    boolean existsByPathAndSite(String path, Site site);

    @Modifying
    @Transactional
    @Query(value = "delete from Page p where p.site = :site")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    void removeByPathAndSite(String path, Site site);

    int countBySite(Site site);
}

package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

public interface IndexRepository extends CrudRepository<Index, Integer> {

    @Query(value = "select i.lemma from Index i where i.page = :page")
    List<Lemma> findLemmaByPage(Page page);

    @Query(value = "from Index i where i.page = :page and i.lemma in (:lemmaList)")
    List<Index> findByPageAndLemmaList(Page page, List<Lemma> lemmaList);

    @Modifying
    @Transactional
    @Query(value = "delete from Index i where i.page in (from Page p where p.site = :site)")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    @Query(value = "delete from Index i where i.page = :page")
    void removeByPage(Page page);
}

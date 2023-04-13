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

public interface IndexRepositoryInterface extends CrudRepository<Index, Integer> {

    @Modifying
    @Transactional
    @Query(value = "delete from Index i where i.page in (from Page p where p.site = :site)")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    void removeByPage(Page page);

    @Query(value = "select i.lemma from Index i where i.page = :page")
    List<Lemma> findLemmaByPage(Page page);
}

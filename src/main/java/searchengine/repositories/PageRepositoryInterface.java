package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepositoryInterface extends CrudRepository<Page, Integer> {

    Optional<Page> findByPathAndSite(String path, Site site);

    @Modifying
    @Transactional
    @Query(value = "delete from Page p where p.site = :site")
    void removeAllBySite(Site site);

    @Modifying
    @Transactional
    void removeByPathAndSite(String path, Site site);

    int countBySite(Site site);
}

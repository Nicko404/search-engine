import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.Application;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

@SpringBootTest(classes = Application.class)
public class RepositoryTest {

    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private LemmaRepository lemmaRepository;


    @Test
    public void deleteBySiteAndPath() {
//        Site site = siteRepository.findByUrl("https://playback.ru");
//        System.out.println(site);
//        pageRepository.removeBySite(site);
    }
}

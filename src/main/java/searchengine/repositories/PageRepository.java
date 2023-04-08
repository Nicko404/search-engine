package searchengine.repositories;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;
import searchengine.model.Page;
import searchengine.model.Site;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PageRepository {

    @Getter
    private final Map<Site, Set<String>> sitePaths;
    private final SessionFactory sessionFactory;
    private static final Logger logger = LogManager.getLogger(PageRepository.class);

    public PageRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        sitePaths = new ConcurrentHashMap<>();
    }

    public Page savePage(Page page) {
        Set<String> paths;
        if (sitePaths.containsKey(page.getSite())) {
            paths = sitePaths.get(page.getSite());
        } else {
            paths = Collections.synchronizedSet(new HashSet<>());
            sitePaths.put(page.getSite(), paths);
        }
        if (paths.add(page.getPath())) {
            Session session = null;
            try {
                session = sessionFactory.openSession();
                session.beginTransaction();
                Serializable serializable = session.save(page);
                page = session.get(Page.class, serializable);
//                session.persist(page);
                session.flush();
                session.getTransaction().commit();
                return page;
            } catch (Exception e) {
                if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                    session.getTransaction().rollback();
                }
                logger.warn("Failed to save page: " + page.getSite().getUrl() + page.getPath() + " - " + e.getMessage());
            } finally {
                if (Objects.nonNull(session)) {
                    session.close();
                }
            }
        }
        return null;
    }

    public boolean isPageSaved(String path, Site site) {
        Set<String> paths = sitePaths.get(site);
        return !(Objects.isNull(paths) || !paths.contains(path));
    }

    public Page findByPathAndSite(String path, Site site) {
        Page page = null;
        String sql = "from Page p where p.path = :path and p.site = :site";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("path", path);
            query.setParameter("site", site);
            page = (Page) query.getSingleResult();
            session.getTransaction().commit();
        } catch (NoResultException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return page;
    }

    public void removeBySite(Site site) {
        String sql = "delete from Page p where p.site = :site";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("site", site);
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
            logger.warn("Failed to remove by site: " + site.getName() + " - " + e.getMessage());
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public void removeByPathAndSite(String path, Site site) {
        String sql = "delete from Page p where p.path = :path and p.site = :site";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("path", path);
            query.setParameter("site", site);
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public void clearMap(Site site) {
        Set<String> paths = sitePaths.get(site);
        if (Objects.nonNull(paths)) {
            paths.clear();
        }
        sitePaths.remove(site);
    }

    public void removeFromMap(Page page) {
        Set<String> paths = sitePaths.get(page.getSite());
        if (Objects.nonNull(paths)) {
            paths.remove(page.getPath());
        }
    }

    public int count(Site site) {
        int result = 0;
        String sql = "select count(*) from Page p where p.site = :site";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameter("site", site);
            result = ((Long) query.getSingleResult()).intValue();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result;
    }
}

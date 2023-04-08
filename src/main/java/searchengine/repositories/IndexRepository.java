package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.hibernate.PropertyValueException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import javax.persistence.Query;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class IndexRepository {

    private final SessionFactory sessionFactory;

    public void save(Index index) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.save(index);
            session.getTransaction().commit();
        } catch (Exception e) {
            if (Objects.nonNull(session) && Objects.nonNull(session.getTransaction())) {
                session.getTransaction().rollback();
            }
//            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public List<Lemma> deleteByPage(Page page) {
        List<Lemma> result = null;
        String findSql = "select i.lemma from Index i where i.page = :page";
        String sql = "delete from Index i where i.page = :page";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            Query findQuery = session.createQuery(findSql);
            findQuery.setParameter("page", page);
            result = findQuery.getResultList();
            query.setParameter("page", page);
            query.executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return result;
    }

    public void removeBySite(Site site) {
        String sql = "delete  from Index i where i.page in (from Page p where p.site = :site)";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
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
}

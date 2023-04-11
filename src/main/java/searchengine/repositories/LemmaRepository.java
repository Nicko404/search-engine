package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class LemmaRepository {

    private final SessionFactory sessionFactory;

    public void save(Lemma lemma) {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.save(lemma);
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }

    }

    public List<Lemma> findByLemmaListAndSite(Set<String> queryLemmas, Site site) {
        List<Lemma> lemmas = null;
        String sql = Objects.isNull(site) ? "from Lemma l where l.lemma in (:queryLemmas)" :
                "from Lemma l where l.site = :site and l.lemma in (:queryLemmas)";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            Query query = session.createQuery(sql);
            query.setParameterList("queryLemmas", queryLemmas);
            if (Objects.nonNull(site)) query.setParameter("site", site);
            lemmas = query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
        return lemmas;
    }

    public void decrementFrequencyById(int id) {
        String sql = "update Lemma l set l.frequency = l.frequency - 1 where l.id = :id";
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            Query query = session.createQuery(sql);
            query.setParameter("id", id);
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

    public void removeIfFrequencyIsZero() {
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.beginTransaction();
            session.createQuery("delete from Lemma l where l.frequency >= 0").executeUpdate();
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (Objects.nonNull(session)) {
                session.close();
            }
        }
    }

    public void removeBySite(Site site) {
        String sql = "delete From Lemma l where l.site = :site";
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

    public int count(Site site) {
        int result = 0;
        String sql = "select count(*) from Lemma l where l.site = :site";
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

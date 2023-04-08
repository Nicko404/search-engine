import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.util.Map;

public class MorphologyTest {


    private final String text = "Повторное появление леопарда ведь в Осетии позволяет" +
            " предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.\n";
    private final String url = "https://skillbox.ru";

    @Test
    public void morphTest() {
        Lemmatizer lemmatizer = new Lemmatizer();
        Map<String, Integer> result = lemmatizer.lemmatize(text);
        System.out.println("Result size: " + result.size());
        for (String lemma : result.keySet()) {
            System.out.println(lemma + " - " + result.get(lemma));
        }
    }

    @Test
    public void clearDocument() {
        try {
            Document document = Jsoup.connect(url).get();
            Lemmatizer lemmatizer = new Lemmatizer();
            Map<String, Integer> result = lemmatizer.lemmatize(lemmatizer.deleteHtmlTags(document));
            for (String lemma : result.keySet()) {
                System.out.println(lemma + " - " + result.get(lemma));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void printMorphInfo() {
        try {
            LuceneMorphology morphology = new RussianLuceneMorphology();
            System.out.println(morphology.getMorphInfo("недочт"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

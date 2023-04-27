package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {

    private static final LuceneMorphology morphology;
    private static final String[] PARTS_OF_SPEECH = {"СОЮЗ", "ПРЕДЛ", "МС", "КР_ПРИЛ", "МЕЖД", "ДЕЕПРИЧАСТИЕ", "L С"};

    private LemmaFinder() {}

    static {
        try {
            morphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Integer> findLemma(String text) {
        Map<String, Integer> result = new HashMap<>();
        String[] words = text.replaceAll("[^А-Яа-я\\s]", "").toLowerCase(Locale.ROOT).trim().split("\\s+");
        for (String word : words) {
            List<String> morphInfo = morphology.getMorphInfo(word);
            for (String morph : morphInfo) {
                if (morphFilter(morph)) {
                    String lemma = morph.substring(0, morph.indexOf("|"));
                    result.put(lemma, result.getOrDefault(lemma, 0) + 1);
                }
            }
        }
        return result;
    }

    private static boolean morphFilter(String word) {
        for (String s : PARTS_OF_SPEECH) {
            if (word.contains(s)) {
                return false;
            }
        }
        return true;
    }

    public static String deleteHtmlTags(Document document) {
        return document.body().text();
    }
}

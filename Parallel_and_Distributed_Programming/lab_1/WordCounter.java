package lab_1;

import java.util.*;
import java.util.regex.*;


public class WordCounter {

    private static final String[] LOREM_WORDS = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
            "adipiscing", "elit", "sed", "do", "eiusmod", "tempor",
            "incididunt", "ut", "labore", "et", "dolore", "magna",
            "aliqua", "enim", "ad", "minim", "veniam", "quis",
            "nostrud", "exercitation", "ullamco", "laboris", "nisi",
            "aliquip", "ex", "ea", "commodo", "consequat", "duis",
            "aute", "irure", "in", "reprehenderit", "voluptate",
            "velit", "esse", "cillum", "eu", "fugiat", "nulla",
            "pariatur", "excepteur", "sint", "occaecat", "cupidatat",
            "non", "proident", "sunt", "culpa", "qui", "officia",
            "deserunt", "mollit", "anim", "id", "est", "laborum"
    };

    private static final String[] END_PUNCT = {".", "!", "?", "...", ";"};
    private static final String[] MID_PUNCT = {",", ";", " -", "..."};

    private static final Random RANDOM = new Random();

    private static String generateSentence(int minLen, int maxLen) {
        int length = minLen + RANDOM.nextInt(maxLen - minLen + 1);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            String word = LOREM_WORDS[RANDOM.nextInt(LOREM_WORDS.length)];
            if (i == 0) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            sb.append(word);

            if (i != length - 1) {
                if (RANDOM.nextDouble() < 0.25) {
                    sb.append(MID_PUNCT[RANDOM.nextInt(MID_PUNCT.length)]);
                }
                sb.append(" ");
            }
        }
        sb.append(END_PUNCT[RANDOM.nextInt(END_PUNCT.length)]);
        return sb.toString();
    }

    private static String generateLoremText(int paragraphs, int minSentences, int maxSentences) {
        StringBuilder text = new StringBuilder();
        for (int p = 0; p < paragraphs; p++) {
            int sentences = minSentences + RANDOM.nextInt(maxSentences - minSentences + 1);
            List<String> sentenceList = new ArrayList<>();
            for (int s = 0; s < sentences; s++) {
                sentenceList.add(generateSentence(4, 12));
            }
            text.append(String.join(" ", sentenceList));
            if (p != paragraphs - 1) {
                text.append("\n\n");
            }
        }
        return text.toString();
    }

    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+");

    private static Map<String, Integer> countWords(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }

    private static void printWordCounts(Map<String, Integer> counts) {
        System.out.printf("%-20s%s%n", "Слово", "Количество");
        System.out.println("-".repeat(30));

        counts.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = b.getValue().compareTo(a.getValue());
                    if (cmp != 0) return cmp;
                    return a.getKey().compareTo(b.getKey());
                })
                .forEach(e -> System.out.printf("%-20s%d%n", e.getKey(), e.getValue()));

        System.out.println("-".repeat(30));
        int totalWords = counts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Всего уникальных слов: " + counts.size());
        System.out.println("Всего слов в тексте:   " + totalWords);
    }

    public static void main(String[] args) {
        String text = generateLoremText(3, 3, 6);

        System.out.println("Сгенерированный текст\n");
        System.out.println(text);
        System.out.println("\nРезультат подсчета слов\n");

        Map<String, Integer> counts = countWords(text);
        printWordCounts(counts);
    }
}
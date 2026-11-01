import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InheritanceIndexLock {

    private static final Pattern DECLARATION = Pattern.compile(
        "\\b(class|interface)\\s+(\\w+)\\s*"
        + "(?:extends\\s+([^{]+?))?\\s*"
        + "(?:implements\\s+([^{]+?))?\\s*"
        + "(?:permits\\s+[^{]+?)?\\s*\\{"
    );

    public static void main(String[] args) throws IOException, InterruptedException {
        long startNanos = System.nanoTime();

        Path root = Path.of(args.length > 0 ? args[0] : ".");

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Map<String, List<String>> index = new TreeMap<>();
        ReentrantLock lock = new ReentrantLock();

        List<Thread> threads = new ArrayList<>();
        for (Path file : javaFiles) {
            Thread thread = new Thread(() -> processFile(file, index, lock));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        int totalChildren = 0;
        for (Map.Entry<String, List<String>> entry : index.entrySet()) {
            List<String> uniqueChildren = entry.getValue().stream()
                    .distinct()
                    .sorted()
                    .toList();
            System.out.println(entry.getKey() + " <- " + String.join(", ", uniqueChildren));
            totalChildren += uniqueChildren.size();
        }
        System.out.println("Всего детей: " + totalChildren);

        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        System.err.printf(Locale.ROOT, "Время выполнения: %.3f с%n", elapsedSeconds);
    }

    private static void processFile(Path file, Map<String, List<String>> index, ReentrantLock lock) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            System.err.println("Не удалось прочитать файл: " + file);
            return;
        }

        text = removeComments(text);
        text = removeGenerics(text);

        Matcher m = DECLARATION.matcher(text);
        while (m.find()) {
            String child = m.group(2);
            addParents(index, lock, child, m.group(3));
            addParents(index, lock, child, m.group(4));
        }
    }

    private static void addParents(Map<String, List<String>> index, ReentrantLock lock,
                                   String child, String parentsText) {
        if (parentsText == null) {
            return;
        }
        Arrays.stream(parentsText.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .forEach(parent -> {
                    lock.lock();
                    try {
                        List<String> children = index.getOrDefault(parent, new ArrayList<>());
                        children.add(child);
                        index.put(parent, children);
                    } finally {
                        lock.unlock();
                    }
                });
    }

    private static String removeComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
    }

    private static String removeGenerics(String text) {
        String previous;
        do {
            previous = text;
            text = text.replaceAll("<[^<>]*>", "");
        } while (!text.equals(previous));
        return text;
    }
}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        Path root = Path.of(args.length > 0 ? args[0] : ".");

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        // Единый индекс и объект синхронизации, который защищает доступ к нему
        Map<String, List<String>> index = new TreeMap<>();
        ReentrantLock lock = new ReentrantLock();

        List<Thread> threads = new ArrayList<>();
        for (Path file : javaFiles) {
            Thread thread = new Thread(() -> processFile(file, index, lock));
            threads.add(thread);
            thread.start();
        }

        // Главный поток ждёт завершения всех потоков
        for (Thread thread : threads) {
            thread.join();
        }

        index.forEach((parent, children) -> {
            String line = children.stream()
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            System.out.println(parent + " <- " + line);
        });
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

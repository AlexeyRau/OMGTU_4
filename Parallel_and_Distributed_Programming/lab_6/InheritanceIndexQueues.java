import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InheritanceIndexQueues {

    private static final Pattern DECLARATION = Pattern.compile(
        "\\b(class|interface)\\s+(\\w+)\\s*"
        + "(?:extends\\s+([^{]+?))?\\s*"
        + "(?:implements\\s+([^{]+?))?\\s*"
        + "(?:permits\\s+[^{]+?)?\\s*\\{"
    );

    private static final Path POISON_PILL = Path.of("POISON_PILL");

    public static void main(String[] args) throws IOException, InterruptedException {
        long startNanos = System.nanoTime();

        Path root = Path.of(args.length > 0 ? args[0] : ".");
        int threadCount = args.length > 1
                ? Integer.parseInt(args[1])
                : Runtime.getRuntime().availableProcessors();
        if (threadCount < 1) {
            throw new IllegalArgumentException("Число потоков должно быть не меньше 1");
        }

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        BlockingQueue<Path> tasks = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, List<String>>> results = new LinkedBlockingQueue<>();

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(() -> runWorker(tasks, results), "worker-" + i);
            workers.add(worker);
            worker.start();
        }

        for (Path file : javaFiles) {
            tasks.put(file);
        }
        for (int i = 0; i < threadCount; i++) {
            tasks.put(POISON_PILL);
        }

        Map<String, List<String>> index = new TreeMap<>();
        for (int i = 0; i < javaFiles.size(); i++) {
            mergeInto(index, results.take());
        }

        for (Thread worker : workers) {
            worker.join();
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
        System.err.printf(Locale.ROOT, "Потоков: %d, время выполнения: %.3f с%n",
                threadCount, elapsedSeconds);
    }

    private static void runWorker(BlockingQueue<Path> tasks,
                                  BlockingQueue<Map<String, List<String>>> results) {
        try {
            while (true) {
                Path file = tasks.take();
                if (file == POISON_PILL) {
                    return;
                }

                Map<String, List<String>> localIndex;
                try {
                    localIndex = processFile(file);
                } catch (RuntimeException e) {
                    System.err.println("Ошибка обработки файла " + file + ": " + e);
                    localIndex = new HashMap<>();
                }
                results.put(localIndex);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, List<String>> processFile(Path file) {
        Map<String, List<String>> localIndex = new HashMap<>();

        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            System.err.println("Не удалось прочитать файл: " + file);
            return localIndex;
        }

        text = removeComments(text);
        text = removeGenerics(text);

        Matcher m = DECLARATION.matcher(text);
        while (m.find()) {
            String child = m.group(2);
            addParents(localIndex, child, m.group(3));
            addParents(localIndex, child, m.group(4));
        }
        return localIndex;
    }

    private static void mergeInto(Map<String, List<String>> index, Map<String, List<String>> part) {
        part.forEach((parent, children) -> {
            List<String> all = index.getOrDefault(parent, new ArrayList<>());
            all.addAll(children);
            index.put(parent, all);
        });
    }

    private static void addParents(Map<String, List<String>> index, String child, String parentsText) {
        if (parentsText == null) {
            return;
        }
        Arrays.stream(parentsText.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .forEach(parent -> {
                    List<String> children = index.getOrDefault(parent, new ArrayList<>());
                    children.add(child);
                    index.put(parent, children);
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
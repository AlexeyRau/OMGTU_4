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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InheritanceIndexExecutor {

    private static final Pattern DECLARATION = Pattern.compile(
        "\\b(class|interface)\\s+(\\w+)\\s*"
        + "(?:extends\\s+([^{]+?))?\\s*"
        + "(?:implements\\s+([^{]+?))?\\s*"
        + "(?:permits\\s+[^{]+?)?\\s*\\{"
    );

    public static void main(String[] args) throws IOException, InterruptedException {
        long startNanos = System.nanoTime();

        Path root = Path.of(args.length > 0 ? args[0] : ".");
        int threadCount = args.length > 1
                ? Integer.parseInt(args[1])
                : Runtime.getRuntime().availableProcessors();

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Map<String, List<String>> index = new TreeMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<Map<String, List<String>>>> futures = new ArrayList<>();
            for (Path file : javaFiles) {
                Callable<Map<String, List<String>>> task = () -> processFile(file);
                futures.add(executor.submit(task));
            }

            for (Future<Map<String, List<String>>> future : futures) {
                try {
                    mergeInto(index, future.get());
                } catch (ExecutionException e) {
                    System.err.println("Ошибка обработки файла: " + e.getCause());
                }
            }
        } finally {
            executor.shutdown();
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
        System.err.printf(Locale.ROOT, "Потоков: %d, время выполнения: %.3f с%n", threadCount, elapsedSeconds);
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
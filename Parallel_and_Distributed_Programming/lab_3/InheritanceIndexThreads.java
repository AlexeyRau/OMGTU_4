import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InheritanceIndexThreads {

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

        Map<String, List<String>> index = new HashMap<>();

        CountDownLatch latch = new CountDownLatch(javaFiles.size());
        for (Path file : javaFiles) {
            new Thread(() -> {
                try {
                    processFile(file, index);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();

        index.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String line = entry.getValue().stream()
                            .distinct()
                            .sorted()
                            .collect(Collectors.joining(", "));
                    System.out.println(entry.getKey() + " <- " + line);
                });
    }

    private static void processFile(Path file, Map<String, List<String>> index) {
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
            addParents(index, child, m.group(3));
            addParents(index, child, m.group(4));
        }
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

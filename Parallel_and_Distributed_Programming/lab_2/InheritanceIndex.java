import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class InheritanceIndex {

    private static final Pattern NOISE = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\"" +
            "|//[^\\n]*" +
            "|/\\*[\\s\\S]*?\\*/" +
            "|\"(?:\\\\.|[^\"\\\\\\n])*\"" +
            "|'(?:\\\\.|[^'\\\\\\n])*'");

    private static final Pattern DECLARATION = Pattern.compile(
            "(?<![.\\w])(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)([^{;]*)\\{");

    private static final Pattern EXTENDS = Pattern.compile(
            "\\bextends\\s+(.+?)(?=\\s+implements\\b|\\s+permits\\b|$)", Pattern.DOTALL);
    private static final Pattern IMPLEMENTS = Pattern.compile(
            "\\bimplements\\s+(.+?)(?=\\s+extends\\b|\\s+permits\\b|$)", Pattern.DOTALL);

    private static final Pattern GENERICS = Pattern.compile("<[^<>]*>");
    private static final Pattern PARENS = Pattern.compile("\\([^()]*\\)");

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        if (!Files.isDirectory(root)) {
            System.err.println("Папка не найдена: " + root.toAbsolutePath());
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        Map<String, List<String>> index = new TreeMap<>();
        Set<String> declared = new TreeSet<>();

        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            parseSource(source, index, declared);
        }

        printIndex(index, declared, files.size());
    }

    private static void parseSource(String source, Map<String, List<String>> index, Set<String> declared) {
        String clean = NOISE.matcher(source).replaceAll(" ");
        Matcher m = DECLARATION.matcher(clean);

        while (m.find()) {
            String child = m.group(2);
            declared.add(child);

            String header = removeAll(GENERICS, m.group(3));
            header = removeAll(PARENS, header).trim();

            Stream.concat(
                    extractParents(EXTENDS, header),
                    extractParents(IMPLEMENTS, header))
                  .forEach(parent -> {
                      List<String> children = index.getOrDefault(parent, new ArrayList<>());
                      children.add(child);
                      index.put(parent, children);
                  });
        }
    }

    private static Stream<String> extractParents(Pattern pattern, String header) {
        Matcher m = pattern.matcher(header);
        if (!m.find()) {
            return Stream.empty();
        }
        return Arrays.stream(m.group(1).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.substring(s.lastIndexOf('.') + 1))
                .map(s -> s.replaceAll("\\s+", ""));
    }

    private static String removeAll(Pattern p, String s) {
        String prev;
        do {
            prev = s;
            s = p.matcher(s).replaceAll("");
        } while (!s.equals(prev));
        return s;
    }

    private static void printIndex(Map<String, List<String>> index, Set<String> declared, int fileCount) {
        System.out.println("Обратный индекс наследования");
        System.out.println("Проанализировано файлов: " + fileCount);
        System.out.println("Найдено типов в проекте: " + declared.size());
        System.out.println("=".repeat(50));

        if (index.isEmpty()) {
            System.out.println("Отношений наследования не найдено.");
            return;
        }

        index.forEach((parent, children) -> {
            String mark = declared.contains(parent) ? "" : "  [внешний тип]";
            System.out.println(parent + mark);
            children.stream()
                    .distinct()
                    .sorted()
                    .forEach(c -> System.out.println("    <- " + c));
        });

        System.out.println("=".repeat(50));
        System.out.println("Всего родительских типов: " + index.size());
    }
}
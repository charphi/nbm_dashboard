import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

public class MakeTable {

    public static void main(String[] args) throws IOException {
        List<Item> extracted = args.length == 1 ? Item.parse(Path.of(args[0])) : Item.extract();

        Map<URI, Map<String, Map<String, Integer>>> plugins = extracted
                .stream()
                .collect(
                        groupingBy(Item::getPluginURI, TreeMap::new,
                                groupingBy(Item::plugin_version, TreeMap::new,
                                        toMap(Item::version, Item::exitcode)))
                );

        SortedSet<String> versions = extracted
                .stream()
                .map(Item::version)
                .collect(toCollection(TreeSet::new));

        List<Header> columns = versions.stream()
                .map(version -> new Header(null, new Version(version, "v")))
                .toList();

        List<Header> rows = plugins.entrySet().stream()
                .flatMap(entry -> entry.getValue().keySet().stream().map(x -> new Header(entry.getKey(), new Version(x, "v"))))
                .toList();

        int[][] body = plugins.values().stream()
                .flatMap(reports -> reports.values().stream().map(report -> versions.stream().mapToInt(report::get).toArray()))
                .toArray(int[][]::new);

        printMarkdown(rows, columns, body);
    }

    private static void printMarkdown(List<Header> rows, List<Header> columns, int[][] body) {
        int col0 = rows.stream().map(Header::uri).map(MakeTable::getShortPluginName).mapToInt(String::length).max().orElse(0);
        int col1 = rows.stream().map(Header::version).map(Version::toString).mapToInt(String::length).max().orElse(0);
        int[] sizes = IntStream.concat(
                IntStream.of(col0, col1),
                columns.stream().map(Header::version).map(Version::toString).mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        System.out.println(Stream.concat(Stream.of(" ".repeat(sizes[0]), " ".repeat(sizes[1])), columns.stream().map(Header::version).map(Version::toString)).collect(toRow));
        System.out.println(IntStream.range(0, 2 + columns.size()).mapToObj(i -> "-".repeat(sizes[i])).collect(toRow));
        AtomicReference<String> previous = new AtomicReference<>("");
        IntStream.range(0, rows.size()).forEach(i -> {
            String shortPluginName = getShortPluginName(rows.get(i).uri());
            String label = previous.getAndSet(shortPluginName).equals(shortPluginName) ? "" : shortPluginName;
            System.out.println(Stream.concat(
                    Stream.of(padRight(label, sizes[0]), padRight(rows.get(i).version().toString(), sizes[1])),
                    IntStream.range(0, body[i].length).mapToObj(j -> padRight(emoji(body[i][j]), sizes[j + 2]))
            ).collect(toRow));
        });
    }

    private static String emoji(int exitcode) {
        return switch (exitcode) {
            case 0 -> "✅";
            case 1 -> "❌";
            default -> "❓";
        };
    }

    private record Item(int exitcode, String plugin, String version, String plugin_version) {

        static Item parse(String line) {
            String[] array = line.split(",", -1);
            return new Item(Integer.parseInt(array[0]), array[1], array[2], array[3]);
        }

        URI getPluginURI() {
            try {
                return new URL("https://github.com/" + plugin).toURI();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        static List<Item> parse(Path file) throws IOException {
            try (var reader = Files.newBufferedReader(file)) {
                return reader.lines().map(Item::parse).toList();
            }
        }

        static List<Item> extract() throws IOException {
            try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(MakeTable.class.getResourceAsStream("test.csv")), UTF_8))) {
                return reader.lines().map(Item::parse).toList();
            }
        }
    }

    private record Header(URI uri, Version version) {
    }

    private record Version(String version, String prefix) {
        @Override
        public String toString() {
            return prefix + version;
        }
    }

    private static String getShortPluginName(URI plugin) {
        String text = plugin.toString();
        return text.substring(text.lastIndexOf("/") + 1).replace("jdplus-", "");
    }

    private static String toBlank(String text) {
        return text.isBlank() ? text : " ".repeat(text.length());
    }

    private static String padRight(String text, int size) {
        return text.length() >= size ? text : text + " ".repeat(size - text.length());
    }
}

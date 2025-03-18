import org.semver4j.Semver;

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
        List<Item> extracted = filter(args.length == 1 ? Item.parse(Path.of(args[0])) : Item.extract());

        Map<URI, Map<Semver, Map<Semver, Integer>>> plugins = extracted
                .stream()
                .collect(
                        groupingBy(Item::getPluginURI, TreeMap::new,
                                groupingBy(Item::plugin_version, TreeMap::new,
                                        toMap(Item::version, Item::exitcode)))
                );

        SortedSet<Semver> versions = extracted
                .stream()
                .map(Item::version)
                .collect(toCollection(TreeSet::new));

        List<Header> columns = versions.stream()
                .map(version -> new Header(null, version))
                .toList();

        List<Header> rows = plugins.entrySet().stream()
                .flatMap(entry -> entry.getValue().keySet().stream().map(x -> new Header(entry.getKey(), x)))
                .toList();

        int[][] body = plugins.values().stream()
                .flatMap(reports -> reports.values().stream().map(report -> versions.stream().map(report::get).mapToInt(value -> value != null ? value : -1).toArray()))
                .toArray(int[][]::new);

        printMarkdown(rows, columns, body);
    }

    private static void printMarkdown(List<Header> rows, List<Header> columns, int[][] body) {
        int col0 = rows.stream().map(Header::toShortPluginName).mapToInt(String::length).max().orElse(0);
        int col1 = rows.stream().map(Header::toVersionString).mapToInt(String::length).max().orElse(0);
        int[] sizes = IntStream.concat(
                IntStream.of(col0, col1),
                columns.stream().map(Header::toVersionString).mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        System.out.println(Stream.concat(Stream.of(" ".repeat(sizes[0]), " ".repeat(sizes[1])), columns.stream().map(Header::toVersionString)).collect(toRow));
        System.out.println(IntStream.range(0, 2 + columns.size()).mapToObj(i -> "-".repeat(sizes[i])).collect(toRow));
        AtomicReference<String> previous = new AtomicReference<>("");
        IntStream.range(0, rows.size()).forEach(i -> {
            String shortPluginName = rows.get(i).toShortPluginName();
            String label = previous.getAndSet(shortPluginName).equals(shortPluginName) ? "" : shortPluginName;
            System.out.println(Stream.concat(
                    Stream.of(padRight(label, sizes[0]), padRight(rows.get(i).toVersionString(), sizes[1])),
                    IntStream.range(0, body[i].length).mapToObj(j -> padRight(emoji(body[i][j]), sizes[j + 2]))
            ).collect(toRow));
        });
    }

    private static String emoji(int exitcode) {
        return switch (exitcode) {
            case 0 -> "✅";
            case 1 -> "❌";
            case -1 -> "";
            default -> "❓";
        };
    }

    private record Item(int exitcode, String plugin, Semver version, Semver plugin_version, Semver jdplus_version) {

        static Item parse(String line) {
            String[] array = line.split(",", -1);
            return new Item(Integer.parseInt(array[0]), array[1], new Semver(array[2]), new Semver(array[3]), new Semver(array[4]));
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

    private record Header(URI uri, Semver version) {

        String toVersionString() {
            return "v" + version.toString();
        }

        String toShortPluginName() {
            String text = uri().toString();
            return text.substring(text.lastIndexOf("/") + 1).replace("jdplus-", "");
        }
    }

    private static String padRight(String text, int size) {
        return text.length() >= size ? text : text + " ".repeat(size - text.length());
    }

    private static List<Item> filter(List<Item> items) {
        return items.stream().filter(MakeTable::isValid).toList();
    }

    private static boolean isValid(Item item) {
        return item.plugin_version().isStable()
                && item.version().isGreaterThanOrEqualTo(item.jdplus_version());
    }
}

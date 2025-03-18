import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class MakeTable {

    public static void main(String[] args) throws IOException {
        List<Item> extracted = args.length == 1 ? parse(Path.of(args[0])) : extract();

        Map<String, List<Item>> plugins = extracted.stream().collect(groupingBy(Item::plugin, TreeMap::new, toList()));
        Map<String, List<Item>> versions = extracted.stream().collect(groupingBy(Item::version, TreeMap::new, toList()));

        System.out.println("| | " + String.join(" | ", versions.keySet()) + " |");
        System.out.println("| " + IntStream.range(0, versions.size() + 1).mapToObj(i -> "---").collect(Collectors.joining(" | ")) + " |");
        plugins.forEach((plugin, items) -> {
            System.out.print("| " + plugin + " " + items.stream().map(Item::plugin_version).findFirst().orElse(""));
            versions.keySet().stream()
                    .map(version -> items.stream().filter(z -> z.version().equals(version)).findFirst().map(Item::exitcode).orElse(-1))
                    .map(MakeTable::emoji)
                    .forEach(exitcode -> System.out.print(" | " + exitcode));
            System.out.println(" |");
        });
    }

    private static String emoji(int exitcode) {
        return switch (exitcode) {
            case 0 -> "✅";
            case 1 -> "❌";
            default -> "❓";
        };
    }

    private static List<Item> parse(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return reader.lines().map(Item::parse).toList();
        }
    }

    private static List<Item> extract() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(MakeTable.class.getResourceAsStream("test.csv")), UTF_8))) {
            return reader.lines().map(Item::parse).toList();
        }
    }

    public record Item(int exitcode, String plugin, String version, String plugin_version) {

        static Item parse(String line) {
            String[] array = line.split(",", -1);
            return new Item(Integer.parseInt(array[0]), array[1], array[2], array[3]);
        }
    }
}

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            System.out.print("| " + plugin);
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
            return Arrays.asList(new Gson().fromJson(reader, Item[].class));
        }
    }

    private static List<Item> extract() throws IOException {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MakeTable.class.getResourceAsStream("test.json")), StandardCharsets.UTF_8)) {
            return Arrays.asList(new Gson().fromJson(reader, Item[].class));
        }
    }

    public record Item(int exitcode, String plugin, String version, String plugin_version) {
    }
}

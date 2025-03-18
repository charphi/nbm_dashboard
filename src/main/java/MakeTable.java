import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

public class MakeTable {

    public static void main(String[] args) throws IOException {
        List<Item> extracted = args.length == 1 ? parse(Path.of(args[0])) : extract();

        Map<PluginId, List<PluginReport>> plugins = extracted
                .stream()
                .collect(groupingBy(PluginId::of, TreeMap::new, mapping(PluginReport::of, toList())));

        SortedSet<String> versions = extracted
                .stream()
                .map(Item::version)
                .collect(toCollection(TreeSet::new));

        System.out.println(versions.stream().map(version -> "v" + version).collect(joining(" | ", "| | ", " |")));
        System.out.println("| " + IntStream.range(0, versions.size() + 1).mapToObj(i -> "---").collect(Collectors.joining(" | ")) + " |");
        plugins.forEach((plugin, reports) -> {
            System.out.print("| " + plugin.pluginName() + " v" + plugin.pluginVersion());
            versions.stream()
                    .map(version -> reports.stream().filter(z -> z.appVersion().equals(version)).findFirst().map(PluginReport::exitcode).orElse(-1))
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

    public record PluginId(String pluginName, String pluginVersion) implements Comparable<PluginId> {

        static PluginId of(Item item) {
            return new PluginId(item.plugin(), item.plugin_version());
        }


        @Override
        public int compareTo(PluginId o) {
            int result = pluginName.compareTo(o.pluginName());
            return result != 0 ? result : pluginVersion.compareTo(o.pluginVersion());
        }
    }

    public record PluginReport(String appVersion, int exitcode) {

        static PluginReport of(Item item) {
            return new PluginReport(item.version(), item.exitcode());
        }
    }
}
